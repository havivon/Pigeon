package com.havivon.pigeon.domain.folders

import com.havivon.pigeon.data.backup.BackupJson
import com.havivon.pigeon.domain.backup.BackupParse
import com.havivon.pigeon.domain.backup.SettingsBackup
import com.havivon.pigeon.domain.backup.BackupSettings
import com.havivon.pigeon.domain.model.CustomFolder
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.fakes.FakeFolders
import com.havivon.pigeon.fakes.FakeSpam
import com.havivon.pigeon.fakes.testMoves
import com.havivon.pigeon.ui.inbox.CategoryEntry
import com.havivon.pigeon.ui.inbox.categoryMenu
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The order of all the categories, built-in and the user's together: resolving it, saving it, the dropdown, and the backup file. */
class CategoryOrderTest {

    private val work = CustomFolder(1, "Work")
    private val club = CustomFolder(2, "Club")
    private val defaults = CategoryOrder.defaultBuiltIns.map { CategoryOrder.token(it) }

    private fun tokens(order: List<String>, custom: List<CustomFolder> = listOf(work, club)) = CategoryOrder.resolve(order, custom).map { it.token }

    @Test
    fun `the default order is the built-in categories as declared, then the user's`() {
        val expected = listOf(
            Folder.Uncategorized, Folder.Personal, Folder.Telecom, Folder.Health, Folder.Bills, Folder.Transport, Folder.Travel,
            Folder.Shopping, Folder.Banks, Folder.Insurance, Folder.Codes, Folder.Deliveries,
        )
        assertEquals(expected, CategoryOrder.defaultBuiltIns)
        assertEquals(defaults + listOf("c:1", "c:2"), tokens(emptyList()))
        assertEquals(tokens(emptyList()), CategoryOrder.default(listOf(work, club)))
        assertFalse("All and Spam are never in the list", defaults.any { it == "b:All" || it == "b:Spam" })
    }

    @Test
    fun `a saved order puts the user's categories among the built-in ones`() {
        val saved = listOf("c:2", "b:Banks", "c:1", "b:Personal")
        val resolved = tokens(saved)
        assertEquals(saved, resolved.take(4))
        // What the saved order does not list follows in the default order
        assertEquals(defaults.filter { it !in saved }, resolved.drop(4))
    }

    @Test
    fun `a stale token, a repeated one and a category added later are handled`() {
        // c:9 was deleted, b:Cruises is a later version's, b:Banks is listed twice
        val resolved = tokens(listOf("b:Banks", "c:9", "b:Cruises", "b:Banks", "c:1"))
        assertEquals(listOf("b:Banks", "c:1"), resolved.take(2))
        assertEquals(1, resolved.count { it == "b:Banks" })
        assertTrue("c:2 was made after the order was saved: it goes to the end", resolved.last() == "c:2")
        assertFalse("c:9" in resolved)
        assertEquals(CategoryOrder.defaultBuiltIns.size + 2, resolved.size)
    }

    @Test
    fun `the dropdown follows the order, with All first and Spam and Manage last`() {
        val counts = mapOf(Folder.All to 9, Folder.Banks to 2, Folder.Spam to 4)
        val m = categoryMenu(counts, listOf(work, club), mapOf(1L to 3), Folder.All, null, order = listOf("c:2", "b:Banks", "c:1", "b:Personal"))
        assertEquals(Folder.All, (m.first() as CategoryEntry.BuiltIn).folder)
        assertEquals(CategoryEntry.Spam(4), m[m.size - 2])
        assertEquals(CategoryEntry.Manage, m.last())
        val middle = m.subList(1, m.size - 2)
        assertEquals(
            listOf("Club", "Banks", "Work", "Personal"),
            middle.take(4).map { if (it is CategoryEntry.Custom) it.folder.name else (it as CategoryEntry.BuiltIn).folder.name },
        )
        assertEquals(3, (middle[2] as CategoryEntry.Custom).count)
        assertEquals(2, (middle[1] as CategoryEntry.BuiltIn).count)
        // The default order when none is saved
        val plain = categoryMenu(counts, listOf(work, club), emptyMap(), Folder.All, null)
        assertEquals(
            defaults.map { it.removePrefix("b:") } + listOf("Work", "Club"),
            plain.subList(1, plain.size - 2).map { if (it is CategoryEntry.Custom) it.folder.name else (it as CategoryEntry.BuiltIn).folder.name },
        )
    }

    // --- saved through FolderMoves ---

    private val folders = FakeFolders()
    private val moves = testMoves(folders, FakeSpam())

    @Test
    fun `a drag saves the whole order, a stale token is dropped and what is missing follows`() = runTest {
        val a = moves.create("Work")!!
        val b = moves.create("Club")!!
        moves.reorderAll(listOf("b:Banks", CategoryOrder.customToken(b), "b:Gone", CategoryOrder.customToken(a)))
        val saved = folders.categoryOrder.first()
        assertEquals(listOf("b:Banks", "c:$b", "c:$a"), saved.take(3))
        assertEquals(CategoryOrder.defaultBuiltIns.size + 2, saved.size)
        assertFalse("b:Gone" in saved)
        // The user's own categories take their places among themselves too
        assertEquals(listOf(b, a), folders.folders.first().map { it.id })
    }

    @Test
    fun `resetting returns to the default order`() = runTest {
        moves.create("Work")
        moves.reorderAll(listOf("b:Banks"))
        assertTrue(folders.categoryOrder.first().isNotEmpty())
        moves.resetOrder()
        assertEquals(emptyList<String>(), folders.categoryOrder.first())
    }

    @Test
    fun `deleting a category drops its place`() = runTest {
        val a = moves.create("Work")!!
        moves.reorderAll(listOf("c:$a", "b:Banks"))
        moves.delete(a)
        assertFalse("c:$a" in folders.categoryOrder.first())
    }

    @Test
    fun `reordering only the user's categories leaves the built-in ones where they are`() = runTest {
        val a = moves.create("Work")!!
        val b = moves.create("Club")!!
        moves.reorderAll(listOf("c:$a", "b:Banks", "c:$b"))
        moves.reorder(listOf(b, a))
        assertEquals(listOf("c:$b", "b:Banks", "c:$a"), folders.categoryOrder.first().take(3))
    }

    // --- the backup file (version 3) ---

    private fun backup(order: List<String>?) = SettingsBackup(
        exportedAtMillis = 0, appVersion = "0.7.0", settings = BackupSettings(), rules = emptyList(),
        categories = listOf("Work", "Club"), categoryOrder = order, moves = emptyList(), pinned = emptyList(), locked = emptyList(),
        notSpam = emptyList(), blockedWords = emptyList(), reminders = emptyList(), favorites = emptyList(),
    )

    private fun parse(text: String) = (BackupJson.parse(text) as BackupParse.Ok).backup

    @Test
    fun `the order is written and read back, by the name of the user's categories`() {
        val order = listOf("c:Club", "b:Banks", "c:Work", "b:Telecom")
        val json = BackupJson.encode(backup(order))
        assertTrue(json.replace(" ", "").contains("\"version\":3"))
        assertEquals(order, parse(json).categoryOrder)
        // An empty list is the default order, which is not the same as no order in the file
        assertEquals(emptyList<String>(), parse(BackupJson.encode(backup(emptyList()))).categoryOrder)
        assertNull(parse(BackupJson.encode(backup(null))).categoryOrder)
    }

    @Test
    fun `a file before version 3 has no order, and what a file names that is unknown is dropped`() {
        val v2 = """{"format":"pigeon-settings","version":2,"categories":["Work"],"categoryOrder":["b:Banks","c:Work"]}"""
        assertNull("an older file has no order, whatever it holds", parse(v2).categoryOrder)
        val v3 = """{"format":"pigeon-settings","version":3,"categories":["Work"],"categoryOrder":["b:Banks","c:work","c:Gone","b:Cruises","b:All","b:Spam",7,"c:Work","b:Banks","x"]}"""
        assertEquals(listOf("b:Banks", "c:Work"), parse(v3).categoryOrder)
        // A version 3 file with no order says nothing
        assertNull(parse("""{"format":"pigeon-settings","version":3,"categories":["Work"]}""").categoryOrder)
        // A newer one is refused
        assertTrue(BackupJson.parse("""{"format":"pigeon-settings","version":4}""") is BackupParse.Failed)
    }
}
