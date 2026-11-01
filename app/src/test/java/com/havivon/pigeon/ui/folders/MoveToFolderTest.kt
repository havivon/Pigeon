package com.havivon.pigeon.ui.folders

import com.havivon.pigeon.domain.folders.FolderKeys
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.CustomFolder
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.FolderChoice
import com.havivon.pigeon.domain.model.SpamReason
import com.havivon.pigeon.domain.model.SpamVerdict
import com.havivon.pigeon.fakes.FakeFavorites
import com.havivon.pigeon.fakes.FakeFolders
import com.havivon.pigeon.fakes.FakeLocks
import com.havivon.pigeon.fakes.FakeMessageRepository
import com.havivon.pigeon.fakes.FakePins
import com.havivon.pigeon.fakes.FakeReminders
import com.havivon.pigeon.fakes.FakeRules
import com.havivon.pigeon.fakes.FakeScheduler
import com.havivon.pigeon.fakes.FakeSmsSender
import com.havivon.pigeon.fakes.FakeSpam
import com.havivon.pigeon.fakes.MainDispatcherRule
import com.havivon.pigeon.fakes.Now
import com.havivon.pigeon.fakes.Utc
import com.havivon.pigeon.fakes.conversation
import com.havivon.pigeon.fakes.englishLabels
import com.havivon.pigeon.fakes.millis
import com.havivon.pigeon.fakes.sms
import com.havivon.pigeon.fakes.testMapper
import com.havivon.pigeon.fakes.testMoves
import com.havivon.pigeon.ui.common.currentFolder
import com.havivon.pigeon.ui.common.customFolderCounts
import com.havivon.pigeon.ui.common.inFolder
import com.havivon.pigeon.ui.common.overrideFor
import com.havivon.pigeon.ui.conversation.ConversationViewModel
import com.havivon.pigeon.ui.inbox.InboxViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** "Move to category": the move wins over the automatic sorting, the chips of the user's categories, and spam both ways. */
@OptIn(ExperimentalCoroutinesApi::class)
class MoveToFolderTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val labels = englishLabels()
    private val mapper = testMapper(labels)
    private val messages = FakeMessageRepository()
    private val spam = FakeSpam()
    private val folders = FakeFolders()
    private val moves = testMoves(folders, spam)

    private fun TestScope.collect(flow: Flow<*>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { } }
    }

    private fun inbox() = InboxViewModel(
        messages, FakeRules(), FakePins(), FakeLocks(), spam, folders, moves, FakeReminders(), FakeScheduler(), mapper,
        MutableStateFlow(0), { "" }, { }, now = { Now }, work = main.dispatcher,
    )

    private fun TestScope.thread(threadId: Long) = ConversationViewModel(
        threadId, messages, FakeSmsSender(messages), FakeRules(), FakeFavorites(), FakeReminders(), FakeScheduler(), spam, FakeLocks(), FakePins(),
        folders, moves, mapper, labels, Utc,
        me = "Me", cancelNotification = { }, appScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)), now = { Now }, work = main.dispatcher,
    )

    private fun seed() {
        messages.threads.value = listOf(
            conversation(1, "0525550100", name = "Mom", snippet = "Bring bread", dateMillis = millis(2026, 11, 1, 11, 0), unread = 2),
            conversation(2, "Leumi", snippet = "Charge of 249.90 on the card ending 4821", dateMillis = millis(2026, 11, 1, 10, 0), unread = 1),
            conversation(3, "+972541112222", snippet = "See you at the meeting", dateMillis = millis(2026, 11, 1, 9, 0), unread = 1),
            conversation(4, "+972551234567", snippet = "You won! bit.ly/x7Kq9", dateMillis = millis(2026, 11, 1, 8, 0), unread = 1),
        )
        messages.allMessages.value = listOf(
            sms(10, 1, "Bring bread", millis(2026, 11, 1, 11, 0), read = false),
            sms(11, 1, "And milk", millis(2026, 11, 1, 11, 1), read = false),
            sms(20, 2, "Charge of 249.90 on the card ending 4821", millis(2026, 11, 1, 10, 0), address = "Leumi", read = false),
            sms(30, 3, "See you at the meeting", millis(2026, 11, 1, 9, 0), address = "+972541112222", read = false),
            sms(40, 4, "You won! bit.ly/x7Kq9", millis(2026, 11, 1, 8, 0), address = "+972551234567", read = false),
        )
    }

    // The mapping

    @Test
    fun `a move wins over the automatic folder, for every message to come`() {
        val leumi = conversation(2, "Leumi", snippet = "Charge of 249.90 on the card ending 4821")
        assertEquals(Folder.Banks, mapper.map(leumi, Now).folder)
        val moved = mapper.map(leumi, Now, override = FolderChoice.BuiltIn(Folder.Personal))
        assertEquals(Folder.Personal, moved.folder)
        assertTrue(moved.folderOverridden)
        // A new message that would be a code stays where the user put it
        val code = conversation(2, "Leumi", snippet = "Your code is 482913")
        assertEquals(Folder.Personal, mapper.map(code, Now, override = FolderChoice.BuiltIn(Folder.Personal)).folder)
        val custom = mapper.map(leumi, Now, override = FolderChoice.Custom(5))
        assertEquals(Folder.All, custom.folder)
        assertEquals(5L, custom.customFolderId)
        assertEquals(FolderChoice.Custom(5), currentFolder(custom))
    }

    @Test
    fun `a spam verdict still wins over a move`() {
        val verdict = SpamVerdict("+972551234567", 4, listOf(SpamReason.MarkedByUser), 0)
        val c = mapper.map(conversation(4, "+972551234567"), Now, verdict = verdict, override = FolderChoice.Custom(5))
        assertTrue(c.isSpam)
        assertNull(c.customFolderId)
        assertFalse(c.folderOverridden)
        assertEquals(FolderChoice.BuiltIn(Folder.Spam), currentFolder(c))
    }

    @Test
    fun `a move made on 05 finds the conversation stored as +972`() {
        val overrides = mapOf(FolderKeys.sender("054-111-2222")!! to FolderChoice.BuiltIn(Folder.Banks))
        assertEquals(FolderChoice.BuiltIn(Folder.Banks), overrideFor(conversation(3, "+972541112222"), overrides))
        assertNull(overrideFor(conversation(3, "+972541112223"), overrides))
    }

    @Test
    fun `a category's chip lists and counts its conversations only, never spam`() {
        val all = listOf(
            Conversation(1, "Mom", "", "", 0, unread = 2, folder = Folder.Personal),
            Conversation(2, "Club", "", "", 0, unread = 3, folder = Folder.All, customFolderId = 7, folderOverridden = true),
            Conversation(3, "Coach", "", "", 0, unread = 1, folder = Folder.All, customFolderId = 7, folderOverridden = true),
            Conversation(4, "Spam", "", "", 0, unread = 1, folder = Folder.Spam, spamReasons = listOf(SpamReason.ShortLink)),
        )
        assertEquals(listOf(2L, 3L), inFolder(all, FolderChoice.Custom(7)).map { it.id })
        assertEquals(mapOf(7L to 4, 8L to 0), customFolderCounts(all, listOf(CustomFolder(7, "Club"), CustomFolder(8, "Empty"))))
        // Under All, not under any built-in folder
        assertEquals(listOf(1L, 2L, 3L), inFolder(all, Folder.All).map { it.id })
        assertEquals(listOf(1L), inFolder(all, Folder.Personal).map { it.id })
    }

    // The inbox

    @Test
    fun `moving from the inbox moves the chips and their numbers`() = runTest {
        seed()
        val vm = inbox()
        collect(vm.state)
        val leumi = vm.state.value.all.first { it.id == 2L }
        assertEquals(Folder.Banks, leumi.folder)
        vm.moveTo(leumi, FolderChoice.BuiltIn(Folder.Personal))
        assertEquals(Folder.Personal, vm.state.value.all.first { it.id == 2L }.folder)
        assertEquals(3, vm.state.value.folderCounts[Folder.Personal])
        assertEquals(0, vm.state.value.folderCounts[Folder.Banks])
        vm.moveTo(vm.state.value.all.first { it.id == 2L }, null)
        assertEquals(Folder.Banks, vm.state.value.all.first { it.id == 2L }.folder)
        assertFalse(vm.state.value.all.first { it.id == 2L }.folderOverridden)
    }

    @Test
    fun `a new category gets a chip, its count, its list and its unread rows`() = runTest {
        seed()
        val vm = inbox()
        collect(vm.state)
        val stranger = vm.state.value.all.first { it.id == 3L }
        assertTrue(vm.createFolderAndMove(stranger, "Work"))
        val work = vm.state.value.customFolders.single()
        assertEquals("Work", work.name)
        assertEquals(1, vm.state.value.customFolderCounts[work.id])
        vm.selectCustomFolder(work.id)
        assertEquals(work.id, vm.state.value.customFolder)
        assertEquals("Work", vm.state.value.customFolderName)
        assertEquals(listOf(3L), vm.state.value.conversations.map { it.id })
        assertEquals(1, vm.state.value.unreadCount)
        vm.selectUnreadOnly(true)
        assertEquals(listOf(30L), vm.state.value.unread?.map { it.messageId })
        // The same name again is refused, in any case
        assertFalse(vm.createFolderAndMove(vm.state.value.all.first { it.id == 1L }, "work"))
        assertEquals(1, vm.state.value.customFolders.size)
    }

    @Test
    fun `an empty category shows nothing, and deleting the chosen one goes back to All`() = runTest {
        seed()
        val vm = inbox()
        collect(vm.state)
        val id = moves.create("Family")!!
        vm.selectCustomFolder(id)
        assertTrue(vm.state.value.conversations.isEmpty())
        assertEquals("Family", vm.state.value.customFolderName)
        moves.move(3, listOf("+972541112222"), isSpam = false, FolderChoice.Custom(id))
        assertEquals(listOf(3L), vm.state.value.conversations.map { it.id })
        moves.delete(id)
        assertNull(vm.state.value.customFolder)
        assertEquals(Folder.All, vm.state.value.folder)
        // Its conversation is sorted automatically again
        assertNull(vm.state.value.all.first { it.id == 3L }.customFolderId)
    }

    @Test
    fun `moving to Spam from the inbox marks it spam`() = runTest {
        seed()
        val vm = inbox()
        collect(vm.state)
        vm.moveTo(vm.state.value.all.first { it.id == 3L }, FolderChoice.BuiltIn(Folder.Spam))
        assertTrue(vm.state.value.all.first { it.id == 3L }.isSpam)
        assertFalse(vm.state.value.conversations.any { it.id == 3L })
    }

    // The conversation's menu

    @Test
    fun `a spam conversation moved to a folder is Not spam and lands there`() = runTest {
        seed()
        spam.markSpam("+972551234567", 4, listOf(SpamReason.ShortLink))
        val vm = thread(4)
        collect(vm.state)
        assertTrue(vm.state.value.conversation!!.isSpam)
        vm.moveTo(FolderChoice.BuiltIn(Folder.Deliveries))
        val c = vm.state.value.conversation!!
        assertFalse(c.isSpam)
        assertEquals(Folder.Deliveries, c.folder)
        assertTrue(spam.isNotSpam("+972551234567"))
    }

    @Test
    fun `Mark as spam drops the move, so Not spam brings back the automatic folder`() = runTest {
        seed()
        val vm = thread(2)
        collect(vm.state)
        vm.moveTo(FolderChoice.BuiltIn(Folder.Personal))
        assertEquals(Folder.Personal, vm.state.value.conversation!!.folder)
        vm.markSpam()
        assertTrue(vm.state.value.conversation!!.isSpam)
        assertTrue(folders.overrideState.value.isEmpty())
        vm.markNotSpam()
        assertEquals(Folder.Banks, vm.state.value.conversation!!.folder)
    }

    @Test
    fun `the menu's new category takes the conversation in`() = runTest {
        seed()
        val vm = thread(1)
        collect(vm.state)
        assertEquals(com.havivon.pigeon.domain.folders.FolderNameProblem.Taken, vm.folderNameProblem("Personal"))
        vm.createFolderAndMove("Family")
        val family = vm.state.value.customFolders.single()
        assertEquals(family.id, vm.state.value.conversation!!.customFolderId)
        assertEquals(com.havivon.pigeon.domain.folders.FolderNameProblem.Taken, vm.folderNameProblem("family"))
    }
}
