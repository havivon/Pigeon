package com.havivon.pigeon.domain.blocking

import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.FolderChoice
import com.havivon.pigeon.domain.model.SpamReason
import com.havivon.pigeon.domain.model.SpamVerdict
import com.havivon.pigeon.fakes.FakeFolders
import com.havivon.pigeon.fakes.FakeSeenBlocked
import com.havivon.pigeon.fakes.FakeSpam
import com.havivon.pigeon.fakes.FakeSystemBlockList
import com.havivon.pigeon.fakes.conversation
import com.havivon.pigeon.fakes.testMoves
import com.havivon.pigeon.ui.common.verdictFor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The phone's own blocked list as spam: read, kept in step with "Mark as spam" and "Not spam", and left alone by a viewer. */
class PhoneBlockingTest {

    private val stored = FakeSpam()
    private val folders = FakeFolders()
    private var blockSetting = true

    private fun repo(system: FakeSystemBlockList, seen: FakeSeenBlocked = FakeSeenBlocked()) =
        PhoneAwareSpamRepository(stored, system, { blockSetting }, seen)

    // Blocked numbers are spam

    @Test
    fun `a blocked number is spam under every way it is written`() = runTest {
        val spam = repo(FakeSystemBlockList(listOf("+972541234567")))
        val verdicts = spam.verdicts.first()
        for (address in listOf("054-1234567", "0541234567", "+972 54-123-4567", "00972541234567")) {
            val v = verdictFor(conversation(1, address), verdicts)
            assertNotNull(address, v)
            assertEquals(address, listOf(SpamReason.BlockedOnPhone), v!!.reasons)
            assertEquals(listOf(SpamReason.BlockedOnPhone), spam.verdictFor(address)?.reasons)
        }
        assertNull("another number is not", verdictFor(conversation(2, "0541234568"), verdicts))
        assertNull(spam.verdictFor("0541234568"))
    }

    @Test
    fun `a local number on the list matches the international sender`() = runTest {
        val verdicts = repo(FakeSystemBlockList(listOf("054-1234567"))).verdicts.first()
        assertNotNull(verdictFor(conversation(1, "+972541234567"), verdicts))
    }

    @Test
    fun `a stored verdict on a blocked number gets the reason once, and no second verdict is made`() = runTest {
        stored.markSpam("+972541234567", 4, listOf(SpamReason.ShortLink))
        val verdicts = repo(FakeSystemBlockList(listOf("054-1234567", "0541234567"))).verdicts.first()
        assertEquals(1, verdicts.size)
        assertEquals(listOf(SpamReason.ShortLink, SpamReason.BlockedOnPhone), verdicts.single().reasons)
        assertEquals(4, verdicts.single().threadId)
    }

    @Test
    fun `entries that are not phone numbers are ignored`() {
        val merged = BlockedOnPhone.merge(emptyList(), listOf("Leumi", "", "12"), emptySet())
        assertTrue(merged.isEmpty())
    }

    // The user's "Not spam" in Pigeon comes first

    @Test
    fun `not spam keeps a blocked number out of spam`() = runTest {
        val system = FakeSystemBlockList(listOf("+972541234567"))
        val spam = repo(system)
        spam.markNotSpam("054-1234567")
        assertNull(verdictFor(conversation(1, "054-1234567"), spam.verdicts.first()))
        assertNull(spam.verdictFor("054-1234567"))
    }

    @Test
    fun `not spam on a blocked number offers to unblock it, and Unblock takes it off the phone's list`() = runTest {
        val system = FakeSystemBlockList(listOf("+972541234567", "0529999999"))
        val spam = repo(system)
        spam.markNotSpam("054-1234567")
        spam.markNotSpam("+972 54 123 4567")
        assertEquals("one offer per number", listOf("054-1234567"), spam.unblockOffers.value)
        spam.confirmUnblock()
        assertTrue(spam.unblockOffers.value.isEmpty())
        assertEquals(listOf("0529999999"), system.state.value)
    }

    @Test
    fun `keep blocked leaves the phone's list alone, and the number is still not spam in Pigeon`() = runTest {
        val system = FakeSystemBlockList(listOf("+972541234567"))
        val spam = repo(system)
        spam.markNotSpam("054-1234567")
        spam.keepBlocked()
        assertTrue(spam.unblockOffers.value.isEmpty())
        assertEquals(listOf("+972541234567"), system.state.value)
        assertTrue(system.unblocked.isEmpty())
        assertNull(verdictFor(conversation(1, "054-1234567"), spam.verdicts.first()))
    }

    @Test
    fun `not spam on a number that is not blocked offers nothing`() = runTest {
        val spam = repo(FakeSystemBlockList(listOf("0529999999")))
        spam.markNotSpam("054-1234567")
        spam.markNotSpam("Leumi")
        assertTrue(spam.unblockOffers.value.isEmpty())
    }

    @Test
    fun `a number blocked after the user's not spam is spam again`() = runTest {
        val system = FakeSystemBlockList(emptyList())
        val seen = FakeSeenBlocked()
        val spam = repo(system, seen)
        spam.sync(system.state.value)
        spam.markNotSpam("054-1234567")
        // Blocked later in the Phone app
        system.state.value = listOf("+972541234567")
        spam.sync(system.state.value)
        assertFalse(stored.isNotSpam("054-1234567"))
        assertNotNull(verdictFor(conversation(1, "054-1234567"), spam.verdicts.first()))
    }

    @Test
    fun `the first reading only remembers, so an older not spam stays`() = runTest {
        stored.markNotSpam("054-1234567")
        val system = FakeSystemBlockList(listOf("+972541234567"))
        val seen = FakeSeenBlocked()
        val spam = repo(system, seen)
        spam.sync(system.state.value)
        assertTrue(stored.isNotSpam("054-1234567"))
        assertEquals(setOf("541234567"), seen.keys)
        assertNull(verdictFor(conversation(1, "054-1234567"), spam.verdicts.first()))
    }

    @Test
    fun `an unreadable list changes nothing`() = runTest {
        val seen = FakeSeenBlocked(setOf("541234567"))
        stored.markNotSpam("0529999999")
        val spam = repo(FakeSystemBlockList(available = false), seen)
        spam.sync(null)
        assertEquals(setOf("541234567"), seen.keys)
        assertTrue(stored.isNotSpam("0529999999"))
    }

    // Mark as spam blocks on the phone (the setting)

    @Test
    fun `mark as spam blocks the number on the phone when the setting is on`() = runTest {
        val system = FakeSystemBlockList()
        val spam = repo(system)
        testMoves(folders, spam).move(3, listOf("054-1234567"), isSpam = false, FolderChoice.BuiltIn(Folder.Spam))
        assertEquals(listOf("054-1234567"), system.state.value)
        assertEquals(listOf(SpamReason.MarkedByUser, SpamReason.BlockedOnPhone), verdictFor(conversation(3, "054-1234567"), spam.verdicts.first())?.reasons)
    }

    @Test
    fun `with the setting off, mark as spam stays inside Pigeon`() = runTest {
        blockSetting = false
        val system = FakeSystemBlockList()
        val spam = repo(system)
        testMoves(folders, spam).move(3, listOf("054-1234567"), isSpam = false, FolderChoice.BuiltIn(Folder.Spam))
        assertEquals(emptyList<String>(), system.state.value)
        assertEquals(listOf(SpamReason.MarkedByUser), verdictFor(conversation(3, "054-1234567"), spam.verdicts.first())?.reasons)
    }

    @Test
    fun `only phone numbers are blocked, and the filter's own verdicts never block`() = runTest {
        val system = FakeSystemBlockList()
        val spam = repo(system)
        testMoves(folders, spam).move(3, listOf("Leumi"), isSpam = false, FolderChoice.BuiltIn(Folder.Spam))
        spam.markSpam("0541234567", 4, listOf(SpamReason.UnknownSender, SpamReason.ShortLink))
        assertTrue(system.blocked.isEmpty())
        assertEquals(emptyList<String>(), system.state.value)
    }

    @Test
    fun `a number already blocked is not added twice`() = runTest {
        val system = FakeSystemBlockList(listOf("+972541234567"))
        repo(system).markSpam("054-1234567", 3, listOf(SpamReason.MarkedByUser))
        assertTrue(system.blocked.isEmpty())
    }

    // A viewer: Android does not show the list

    @Test
    fun `as a viewer nothing is read, blocked or offered`() = runTest {
        val system = FakeSystemBlockList(available = false)
        val spam = repo(system)
        stored.markSpam("0529999999", 1, listOf(SpamReason.ShortLink))
        assertEquals(stored.verdictState.value, spam.verdicts.first())
        testMoves(folders, spam).move(3, listOf("054-1234567"), isSpam = false, FolderChoice.BuiltIn(Folder.Spam))
        assertTrue(system.blocked.isEmpty())
        spam.markNotSpam("054-1234567")
        assertTrue(spam.unblockOffers.value.isEmpty())
        assertNull(system.state.value)
    }

    @Test
    fun `merge keeps the stored verdicts as they are without a list`() {
        val v = listOf(SpamVerdict("+972541234567", 1, listOf(SpamReason.Poll), 5))
        assertEquals(v, BlockedOnPhone.merge(v, emptyList(), emptySet()))
    }
}
