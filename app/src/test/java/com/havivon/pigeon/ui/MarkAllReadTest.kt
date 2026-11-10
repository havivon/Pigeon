package com.havivon.pigeon.ui

import com.havivon.pigeon.domain.folders.FolderKeys
import com.havivon.pigeon.domain.model.CustomFolder
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.FolderChoice
import com.havivon.pigeon.domain.model.SpamReason
import com.havivon.pigeon.domain.model.SpamVerdict
import com.havivon.pigeon.fakes.FakeFolders
import com.havivon.pigeon.fakes.FakeLocks
import com.havivon.pigeon.fakes.FakeMessageRepository
import com.havivon.pigeon.fakes.FakePins
import com.havivon.pigeon.fakes.FakeReminders
import com.havivon.pigeon.fakes.FakeRules
import com.havivon.pigeon.fakes.FakeScheduler
import com.havivon.pigeon.fakes.FakeSpam
import com.havivon.pigeon.fakes.MainDispatcherRule
import com.havivon.pigeon.fakes.Now
import com.havivon.pigeon.fakes.conversation
import com.havivon.pigeon.fakes.englishLabels
import com.havivon.pigeon.fakes.millis
import com.havivon.pigeon.fakes.sms
import com.havivon.pigeon.fakes.testMapper
import com.havivon.pigeon.fakes.testMoves
import com.havivon.pigeon.ui.inbox.InboxViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * "Mark all as read": which conversations it touches in each folder, the one batched update, the cancelled
 * notifications, the number the dialog and the notice show, and the viewer who is only told why not.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MarkAllReadTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val messages = FakeMessageRepository()
    private val spam = FakeSpam()
    private val locks = FakeLocks()
    private val folders = FakeFolders(listOf(CustomFolder(5, "Work")))
    private val cancelled = mutableListOf<Long>()
    private var canWrite = true

    private fun TestScope.collect(flow: Flow<*>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { } }
    }

    private fun inbox() = InboxViewModel(
        messages, FakeRules(), FakePins(), locks, spam, folders, testMoves(folders, spam), FakeReminders(), FakeScheduler(), testMapper(englishLabels()),
        MutableStateFlow(0), { "" }, { cancelled += it }, canWrite = { canWrite }, now = { Now }, work = main.dispatcher,
    )

    /** Mom (1, 2 unread), the bank (2, 1), a colleague moved to Work (3, 1), a spammer (4, 1), a locked chat (5, 3), Dad (6, all read). */
    private fun seed() {
        messages.threads.value = listOf(
            conversation(1, "0525550100", name = "Mom", snippet = "Bring bread", dateMillis = millis(2026, 11, 1, 11, 0)),
            conversation(2, "Leumi", snippet = "Charge of 249.90 on the card ending 4821", dateMillis = millis(2026, 11, 1, 10, 0)),
            conversation(3, "+972541112222", snippet = "See you at the meeting", dateMillis = millis(2026, 11, 1, 9, 0)),
            conversation(4, "+972551234567", snippet = "You won! bit.ly/x7Kq9", dateMillis = millis(2026, 11, 1, 8, 0)),
            conversation(5, "0525550199", name = "Secret", snippet = "private", dateMillis = millis(2026, 11, 1, 7, 0)),
            conversation(6, "0525550166", name = "Dad", snippet = "ok", dateMillis = millis(2026, 11, 1, 6, 0)),
        )
        messages.allMessages.value = listOf(
            sms(10, 1, "Bring bread", millis(2026, 11, 1, 11, 0), read = false),
            sms(11, 1, "And milk", millis(2026, 11, 1, 11, 1), read = false),
            sms(20, 2, "Charge of 249.90 on the card ending 4821", millis(2026, 11, 1, 10, 0), address = "Leumi", read = false),
            sms(30, 3, "See you at the meeting", millis(2026, 11, 1, 9, 0), address = "+972541112222", read = false),
            sms(40, 4, "You won! bit.ly/x7Kq9", millis(2026, 11, 1, 8, 0), address = "+972551234567", read = false),
            sms(50, 5, "a", millis(2026, 11, 1, 7, 0), address = "0525550199", read = false),
            sms(51, 5, "b", millis(2026, 11, 1, 7, 1), address = "0525550199", read = false),
            sms(52, 5, "c", millis(2026, 11, 1, 7, 2), address = "0525550199", read = false),
            sms(60, 6, "ok", millis(2026, 11, 1, 6, 0), address = "0525550166", read = true),
        )
        locks.state.value = setOf(5)
        spam.verdictState.value = listOf(SpamVerdict("+972551234567", 4, listOf(SpamReason.ShortLink), 0))
        folders.overrideState.value = mapOf(FolderKeys.of(3, listOf("+972541112222")) to FolderChoice.Custom(5))
    }

    @Test
    fun `All marks every conversation with unread messages, spam excluded, locked chats included, in one batch`() = runTest {
        seed()
        val vm = inbox()
        collect(vm.state)
        // The dialog's number: the unread messages of All (the spammer's one is not in it)
        assertEquals(2 + 1 + 1 + 3, vm.state.value.unreadCount)
        assertEquals(7, vm.markAllRead())
        advanceUntilIdle()
        assertEquals(1, messages.markedReadBatches.size)
        assertEquals(setOf(1L, 2L, 3L, 5L), messages.markedReadBatches.single().toSet())
        assertEquals(setOf(1L, 2L, 3L, 5L), cancelled.toSet())
        assertTrue(messages.markedRead.isEmpty())
        // Spam keeps its unread message; the rest is read, so the button is dimmed now
        assertEquals(listOf(40L), messages.allMessages.value.filter { !it.read }.map { it.id })
        assertEquals(0, vm.state.value.unreadCount)
    }

    @Test
    fun `a built-in folder marks only its own conversations`() = runTest {
        seed()
        val vm = inbox()
        collect(vm.state)
        vm.selectFolder(Folder.Banks)
        assertEquals(1, vm.state.value.unreadCount)
        assertEquals(1, vm.markAllRead())
        advanceUntilIdle()
        assertEquals(listOf(listOf(2L)), messages.markedReadBatches)
        assertEquals(listOf(2L), cancelled)
        assertEquals(2, messages.allMessages.value.count { it.threadId == 1L && !it.read })
    }

    @Test
    fun `a custom category marks the conversations moved into it, and a manual move is respected`() = runTest {
        seed()
        val vm = inbox()
        collect(vm.state)
        // The colleague would be Personal by itself; the move to Work takes it out (Mom 2 and the locked chat 3 stay)
        vm.selectFolder(Folder.Personal)
        assertEquals(5, vm.state.value.unreadCount)
        vm.selectCustomFolder(5)
        assertEquals(1, vm.state.value.unreadCount)
        assertEquals(1, vm.markAllRead())
        advanceUntilIdle()
        assertEquals(listOf(listOf(3L)), messages.markedReadBatches)
        // Personal is untouched
        vm.selectFolder(Folder.Personal)
        assertEquals(5, vm.state.value.unreadCount)
    }

    @Test
    fun `it works the same with the Unread list on`() = runTest {
        seed()
        val vm = inbox()
        collect(vm.state)
        vm.selectUnreadOnly(true)
        advanceUntilIdle()
        assertEquals(7, vm.state.value.unread?.size)
        assertEquals(7, vm.markAllRead())
        advanceUntilIdle()
        assertTrue(vm.state.value.unread.orEmpty().isEmpty())
    }

    @Test
    fun `with nothing unread nothing is written and no notification is touched`() = runTest {
        seed()
        val vm = inbox()
        collect(vm.state)
        vm.selectFolder(Folder.Insurance)
        assertEquals(0, vm.state.value.unreadCount)
        assertEquals(0, vm.markAllRead())
        advanceUntilIdle()
        assertTrue(messages.markedReadBatches.isEmpty())
        assertTrue(cancelled.isEmpty())
    }

    @Test
    fun `a viewer is refused and nothing is written`() = runTest {
        seed()
        canWrite = false
        val vm = inbox()
        collect(vm.state)
        assertNull(vm.markAllRead())
        advanceUntilIdle()
        assertTrue(messages.markedReadBatches.isEmpty())
        assertTrue(cancelled.isEmpty())
        assertEquals(7, vm.state.value.unreadCount)
    }
}
