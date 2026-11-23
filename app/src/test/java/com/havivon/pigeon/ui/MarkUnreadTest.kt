package com.havivon.pigeon.ui

import com.havivon.pigeon.domain.model.SmsType
import com.havivon.pigeon.fakes.FakeFavorites
import com.havivon.pigeon.fakes.FakeFolders
import com.havivon.pigeon.fakes.testMoves
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
import com.havivon.pigeon.ui.conversation.ConversationEvent
import com.havivon.pigeon.ui.conversation.ConversationViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** "Mark as unread" for the selected messages of an open conversation. */
@OptIn(ExperimentalCoroutinesApi::class)
class MarkUnreadTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val labels = englishLabels()
    private val mapper = testMapper(labels)
    private val messages = FakeMessageRepository()
    private val locks = FakeLocks()
    private val cancelled = mutableListOf<Long>()
    private var canWrite = true

    private fun TestScope.collect(flow: Flow<*>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { } }
    }

    private fun TestScope.thread() = ConversationViewModel(
        1, messages, FakeSmsSender(messages), FakeRules(), FakeFavorites(), FakeReminders(), FakeScheduler(), FakeSpam(), locks, FakePins(),
        FakeFolders(), testMoves(FakeFolders(), FakeSpam()), mapper, labels, Utc,
        me = "Me", cancelNotification = { cancelled += it }, appScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        canWrite = { canWrite }, now = { Now }, work = main.dispatcher,
    )

    private fun seed() {
        messages.threads.value = listOf(
            conversation(1, "0525550100", name = "Danny", snippet = "On my way", dateMillis = millis(2026, 11, 1, 10, 5)),
            conversation(2, "0541112222", name = "Mom", snippet = "Call me", dateMillis = millis(2026, 11, 1, 9, 0), unread = 1),
            conversation(3, "0543334444", name = "Dana", snippet = "Sent only", dateMillis = millis(2026, 11, 1, 8, 0)),
        )
        messages.allMessages.value = listOf(
            sms(10, 1, "First", millis(2026, 11, 1, 9, 0)),
            sms(11, 1, "Bring the form", millis(2026, 11, 1, 10, 0)),
            sms(12, 1, "On my way", millis(2026, 11, 1, 10, 5), type = SmsType.Sent),
            sms(20, 2, "Call me", millis(2026, 11, 1, 9, 0), "0541112222", read = false),
            sms(30, 3, "Sent only", millis(2026, 11, 1, 8, 0), "0543334444", type = SmsType.Sent),
        )
    }

    private fun unreadIds() = messages.allMessages.value.filter { !it.read }.map { it.id }

    @Test
    fun `a message marked as unread stays unread while its conversation closes, and is read when opened again`() = runTest {
        seed()
        val vm = thread()
        collect(vm.state)
        vm.setVisible(true)
        vm.select(11)
        vm.markSelectedUnread()
        assertEquals(listOf(11L, 20L), unreadIds())
        assertEquals(ConversationEvent.MarkedUnread, vm.eventFlow.first())
        // The list is read again while the screen is still closing
        messages.allMessages.value = messages.allMessages.value.map { it.copy() } + sms(13, 1, "Seen while closing", millis(2026, 11, 1, 10, 6))
        assertTrue(11L in unreadIds())
        vm.setVisible(false)
        assertTrue(11L in unreadIds())
        vm.setVisible(true)
        assertEquals(listOf(20L), unreadIds())
    }

    @Test
    fun `selected messages are marked, the sent ones left out, and the selection ends`() = runTest {
        seed()
        val vm = thread()
        collect(vm.state)
        vm.setVisible(true)
        vm.select(10)
        vm.toggleSelected(12)
        vm.markSelectedUnread()
        assertEquals(listOf(10L, 20L), unreadIds())
        assertTrue(vm.selected.value.isEmpty())
        assertEquals(ConversationEvent.MarkedUnread, vm.eventFlow.first())
    }

    @Test
    fun `with only sent messages nothing is marked, the selection stays and new messages are still read`() = runTest {
        seed()
        val vm = thread()
        collect(vm.state)
        vm.setVisible(true)
        vm.select(12)
        vm.markSelectedUnread()
        assertEquals(ConversationEvent.NothingToMarkUnread, vm.eventFlow.first())
        assertEquals(setOf(12L), vm.selected.value)
        messages.allMessages.value += sms(13, 1, "New", millis(2026, 11, 1, 10, 6), read = false)
        assertEquals(listOf(20L), unreadIds())
    }

    @Test
    fun `a viewer changes nothing and is told why`() = runTest {
        seed()
        canWrite = false
        val vm = thread()
        collect(vm.state)
        vm.select(11)
        vm.markSelectedUnread()
        assertEquals(ConversationEvent.NeedsDefault, vm.eventFlow.first())
        assertEquals("the selection stays", setOf(11L), vm.selected.value)
        assertEquals(listOf(20L), unreadIds())
    }
}
