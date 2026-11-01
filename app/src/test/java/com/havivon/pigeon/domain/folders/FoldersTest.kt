package com.havivon.pigeon.domain.folders

import com.havivon.pigeon.data.db.FolderChoiceCodec
import com.havivon.pigeon.domain.analysis.SpamFilterHook
import com.havivon.pigeon.domain.model.CustomFolder
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.FolderChoice
import com.havivon.pigeon.domain.model.SpamReason
import com.havivon.pigeon.domain.seam.IncomingVerdict
import com.havivon.pigeon.domain.seam.StoredIncomingSms
import com.havivon.pigeon.fakes.FakeBlockedKeywords
import com.havivon.pigeon.fakes.FakeContacts
import com.havivon.pigeon.fakes.FakeFolders
import com.havivon.pigeon.fakes.FakeSpam
import com.havivon.pigeon.fakes.ReservedFolderNames
import com.havivon.pigeon.fakes.testMoves
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldersTest {

    // The key a move is kept under

    @Test
    fun `one sender written as +972 or as 05 shares one key`() {
        val local = FolderKeys.of(1, listOf("052-555-0123"))
        assertEquals(local, FolderKeys.of(2, listOf("+972 52-555-0123")))
        assertEquals(local, FolderKeys.of(3, listOf("+972525550123")))
        assertEquals(local, FolderKeys.sender("0525550123"))
        assertNotEquals(local, FolderKeys.of(1, listOf("0525550124")))
    }

    @Test
    fun `a sender id is its own key, a group is keyed by its thread`() {
        assertEquals(FolderKeys.of(1, listOf("Leumi")), FolderKeys.of(9, listOf("LEUMI")))
        assertEquals("thread:7", FolderKeys.of(7, listOf("0525550123", "0545550144")))
        assertEquals("thread:7", FolderKeys.of(7, emptyList()))
        // The same person listed twice (two ways of writing the number) is still one sender
        assertEquals(FolderKeys.sender("0525550123"), FolderKeys.of(7, listOf("0525550123", "+972525550123")))
        assertTrue(FolderKeys.bySender(listOf("0525550123", "+972525550123")))
        assertFalse(FolderKeys.bySender(listOf("0525550123", "0545550144")))
    }

    // Names

    private val work = CustomFolder(1, "Work")

    @Test
    fun `a name must be there and short enough for a chip`() {
        assertEquals(FolderNameProblem.Blank, FolderNames.problem("   ", ReservedFolderNames, emptyList()))
        assertEquals(FolderNameProblem.TooLong, FolderNames.problem("x".repeat(FolderNames.MAX_LENGTH + 1), ReservedFolderNames, emptyList()))
        assertNull(FolderNames.problem("x".repeat(FolderNames.MAX_LENGTH), ReservedFolderNames, emptyList()))
        assertEquals("Building committee", FolderNames.clean("  Building   committee "))
    }

    @Test
    fun `no name of a built-in folder, in either language, whatever the case`() {
        for (name in listOf("Banks", "banks", " BANKS ", "בנקים", "הכל", "All", "spam", "ספאם", "Deliveries")) {
            assertEquals(name, FolderNameProblem.Taken, FolderNames.problem(name, ReservedFolderNames, emptyList()))
        }
        assertNull(FolderNames.problem("Bank stuff", ReservedFolderNames, emptyList()))
    }

    @Test
    fun `no two categories of the same name, but a category keeps its own`() {
        assertEquals(FolderNameProblem.Taken, FolderNames.problem("work", ReservedFolderNames, listOf(work)))
        assertEquals(FolderNameProblem.Taken, FolderNames.problem(" WORK", ReservedFolderNames, listOf(work)))
        assertNull(FolderNames.problem("Work", ReservedFolderNames, listOf(work), renaming = work.id))
        assertNull(FolderNames.problem("work", ReservedFolderNames, listOf(work), renaming = work.id))
        assertNull(FolderNames.problem("Family", ReservedFolderNames, listOf(work)))
    }

    // How a move is stored

    @Test
    fun `a move is stored as a folder name or a category id, never All or Spam`() {
        assertEquals("Banks" to null, FolderChoiceCodec.encode(FolderChoice.BuiltIn(Folder.Banks)))
        assertEquals(null to 4L, FolderChoiceCodec.encode(FolderChoice.Custom(4)))
        assertNull(FolderChoiceCodec.encode(FolderChoice.BuiltIn(Folder.All)))
        assertNull(FolderChoiceCodec.encode(FolderChoice.BuiltIn(Folder.Spam)))
        assertEquals(FolderChoice.BuiltIn(Folder.Banks), FolderChoiceCodec.decode("Banks", null))
        assertEquals(FolderChoice.Custom(4), FolderChoiceCodec.decode(null, 4))
        // A folder of a later version, or a value that was never allowed: no move
        assertNull(FolderChoiceCodec.decode("Travel", null))
        assertNull(FolderChoiceCodec.decode("Spam", null))
        assertNull(FolderChoiceCodec.decode(null, null))
    }

    // The moves

    private val folders = FakeFolders()
    private val spam = FakeSpam()
    private val moves = testMoves(folders, spam)
    private val danny = listOf("0525550123")
    private val dannyKey = FolderKeys.of(1, danny)

    @Test
    fun `moving to a folder keeps it by the sender, and Automatic drops it`() = runTest {
        moves.move(1, danny, isSpam = false, FolderChoice.BuiltIn(Folder.Banks))
        assertEquals(FolderChoice.BuiltIn(Folder.Banks), folders.overrideFor(dannyKey))
        // Another thread of the same sender, written the international way, has it too
        assertEquals(FolderChoice.BuiltIn(Folder.Banks), folders.overrideFor(FolderKeys.of(5, listOf("+972525550123"))))
        moves.move(1, danny, isSpam = false, null)
        assertNull(folders.overrideFor(dannyKey))
        assertTrue(spam.notSpamState.value.isEmpty())
    }

    @Test
    fun `moving to Spam is Mark as spam and drops the move`() = runTest {
        spam.markNotSpam("0525550123")
        moves.move(1, danny, isSpam = false, FolderChoice.BuiltIn(Folder.Personal))
        moves.move(1, danny, isSpam = false, FolderChoice.BuiltIn(Folder.Spam))
        assertNull(folders.overrideFor(dannyKey))
        assertEquals(listOf(SpamReason.MarkedByUser), spam.verdictFor("0525550123")?.reasons)
        // The user's own mark beats the earlier "Not spam"
        assertFalse(spam.isNotSpam("0525550123"))
    }

    @Test
    fun `moving a spam conversation to a folder is Not spam and the move`() = runTest {
        spam.markSpam("0525550123", 1, listOf(SpamReason.ShortLink))
        moves.move(1, danny, isSpam = true, FolderChoice.BuiltIn(Folder.Deliveries))
        assertNull(spam.verdictFor("0525550123"))
        assertTrue(spam.isNotSpam("0525550123"))
        assertEquals(FolderChoice.BuiltIn(Folder.Deliveries), folders.overrideFor(dannyKey))
    }

    @Test
    fun `a new category is made only with a usable name, and takes the conversation`() = runTest {
        assertNull(moves.createAndMove("Banks", 1, danny, isSpam = false))
        assertNull(moves.createAndMove(" ", 1, danny, isSpam = false))
        val id = moves.createAndMove("  Building   committee ", 1, danny, isSpam = false)!!
        assertEquals(listOf(CustomFolder(id, "Building committee")), folders.folders.first())
        assertEquals(FolderChoice.Custom(id), folders.overrideFor(dannyKey))
        assertNull(moves.createAndMove("building committee", 2, listOf("0545550144"), isSpam = false))
        assertEquals(1, folders.folders.first().size)
    }

    @Test
    fun `deleting a category sends its conversations back to automatic, the others stay`() = runTest {
        val work = moves.create("Work")!!
        val family = moves.create("Family")!!
        moves.move(1, danny, isSpam = false, FolderChoice.Custom(work))
        moves.move(2, listOf("0545550144"), isSpam = false, FolderChoice.Custom(family))
        moves.move(3, listOf("Leumi"), isSpam = false, FolderChoice.BuiltIn(Folder.Personal))
        moves.delete(work)
        assertNull(folders.overrideFor(dannyKey))
        assertEquals(FolderChoice.Custom(family), folders.overrideFor(FolderKeys.sender("0545550144")!!))
        assertEquals(FolderChoice.BuiltIn(Folder.Personal), folders.overrideFor(FolderKeys.sender("Leumi")!!))
        assertEquals(listOf("Family"), folders.folders.first().map { it.name })
    }

    @Test
    fun `categories are renamed with the same checks, and reordered`() = runTest {
        val work = moves.create("Work")!!
        val family = moves.create("Family")!!
        val club = moves.create("Club")!!
        assertFalse(moves.rename(work, "family"))
        assertFalse(moves.rename(work, "קודים"))
        assertTrue(moves.rename(work, "work"))
        moves.reorder(listOf(club, work))
        assertEquals(listOf(club, work, family), folders.folders.first().map { it.id })
        assertEquals("work", folders.folders.first().first { it.id == work }.name)
    }

    // Spam on arrival

    private fun incoming(address: String) = StoredIncomingSms(1, 1, address, "זכית בפרס! לחץ כאן לקבלת המתנה: bit.ly/x7Kq9", 0)

    @Test
    fun `a sender the user filed is never spam, as if marked Not spam`() = runTest {
        val hook = SpamFilterHook(FakeContacts(), spam, FakeBlockedKeywords(), filedByUser = { a -> FolderKeys.sender(a)?.let { folders.overrideFor(it) } != null })
        moves.move(1, listOf("0551234567"), isSpam = false, FolderChoice.BuiltIn(Folder.Personal))
        assertEquals(IncomingVerdict.Notify, hook.onStored(incoming("+972551234567")))
        assertTrue(spam.verdictState.value.isEmpty())
        // An unfiled stranger with the same text still is
        assertEquals(IncomingVerdict.Silent, hook.onStored(incoming("+972559876543")))
    }
}
