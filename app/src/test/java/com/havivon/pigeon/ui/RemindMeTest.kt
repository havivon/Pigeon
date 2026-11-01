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
import com.havivon.pigeon.ui.inbox.InboxViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** "Remind me about this message": from the conversation's selection bar and from the inbox's long-press sheet. */
@OptIn(ExperimentalCoroutinesApi::class)
class RemindMeTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val labels = englishLabels()
    private val mapper = testMapper(labels)
    private val messages = FakeMessageRepository()
    private val reminders = FakeReminders()
    private val scheduler = FakeScheduler()
    private val locks = FakeLocks()

    private fun TestScope.collect(flow: Flow<*>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { } }
    }

    private fun TestScope.thread() = ConversationViewModel(
        1, messages, FakeSmsSender(messages), FakeRules(), FakeFavorites(), reminders, scheduler, FakeSpam(), locks, FakePins(),
        FakeFolders(), testMoves(FakeFolders(), FakeSpam()), mapper, labels, Utc,
        me = "Me", cancelNotification = { }, appScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)), now = { Now }, work = main.dispatcher,
    )

    private fun inbox() = InboxViewModel(
        messages, FakeRules(), FakePins(), locks, FakeSpam(), FakeFolders(), testMoves(FakeFolders(), FakeSpam()), reminders, scheduler, mapper, MutableStateFlow(0), { "" }, { }, now = { Now }, work = main.dispatcher,
    )

    private fun seedThread() {
        messages.threads.value = listOf(conversation(1, "0525550100", name = "Danny", snippet = "Bring the form"))
        messages.allMessages.value = listOf(
            sms(10, 1, "Bring the form", millis(2026, 11, 1, 10, 0)),
            sms(11, 1, "On my way", millis(2026, 11, 1, 10, 5), type = SmsType.Sent),
        )
    }

    // From the conversation

    @Test
    fun `a reminder on the selected message is saved and scheduled`() = runTest {
        seedThread()
        val vm = thread()
        collect(vm.state)
        vm.select(10)
        val at = millis(2026, 11, 1, 20, 0)
        vm.remindSelected(at)

        val saved = reminders.state.value.single()
        assertEquals(1L, saved.threadId)
        assertEquals(10L, saved.messageId)
        assertEquals("Bring the form", saved.text)
        assertEquals(at, saved.triggerAtMillis)
        assertEquals(listOf(saved), scheduler.scheduled)
        // The selection is over, and the message shows its reminder
        assertTrue(vm.selected.value.isEmpty())
        assertTrue(vm.state.value.messages.first { it.id == 10L }.reminderSet)
    }

    @Test
    fun `the user is told when it will fire`() = runTest {
        seedThread()
        val vm = thread()
        collect(vm.state)
        vm.select(10)
        vm.remindSelected(millis(2026, 11, 2, 9, 0))
        val event = vm.eventFlow.first()
        assertEquals(ConversationEvent.ReminderCreated("Tomorrow 09:00"), event)
    }

    @Test
    fun `a second reminder on the same message replaces the first`() = runTest {
        seedThread()
        val vm = thread()
        collect(vm.state)
        vm.select(10)
        vm.remindSelected(millis(2026, 11, 1, 20, 0))
        val first = reminders.state.value.single()
        vm.select(10)
        vm.remindSelected(millis(2026, 11, 2, 9, 0))

        assertEquals(listOf(millis(2026, 11, 2, 9, 0)), reminders.state.value.map { it.triggerAtMillis })
        assertEquals(listOf(first.id), scheduler.cancelled)
        assertEquals(2, scheduler.scheduled.size)
    }

    @Test
    fun `a time that already passed is ignored`() = runTest {
        seedThread()
        val vm = thread()
        collect(vm.state)
        vm.select(10)
        vm.remindSelected(Now)
        assertTrue(reminders.state.value.isEmpty())
        assertTrue(scheduler.scheduled.isEmpty())
        // The selection is kept, so the user can pick another time
        assertEquals(setOf(10L), vm.selected.value)
    }

    @Test
    fun `it is about one message, so several selected messages get no reminder`() = runTest {
        seedThread()
        val vm = thread()
        collect(vm.state)
        vm.select(10)
        vm.select(11)
        vm.remindSelected(millis(2026, 11, 1, 20, 0))
        assertTrue(reminders.state.value.isEmpty())
    }

    // From the inbox

    @Test
    fun `the inbox sheet saves a reminder on the whole conversation`() = runTest {
        messages.threads.value = listOf(conversation(1, "0525550100", name = "Danny", snippet = "Bring the form", unread = 1))
        val vm = inbox()
        collect(vm.state)
        val conv = vm.state.value.conversations.single()
        val at = millis(2026, 11, 1, 20, 0)
        assertTrue(vm.remind(conv, at))

        val saved = reminders.state.value.single()
        assertEquals(1L, saved.threadId)
        assertEquals(null, saved.messageId)
        assertEquals("Bring the form", saved.text)
        assertEquals(listOf(saved), scheduler.scheduled)
    }

    @Test
    fun `a locked conversation keeps no text in its reminder`() = runTest {
        messages.threads.value = listOf(conversation(1, "0525550100", name = "Danny", snippet = "Secret plans"))
        locks.lock(1)
        val vm = inbox()
        collect(vm.state)
        val conv = vm.state.value.conversations.single()
        assertTrue(conv.locked)
        assertTrue(vm.remind(conv, millis(2026, 11, 1, 20, 0)))
        assertEquals("", reminders.state.value.single().text)
        assertFalse("Secret" in reminders.state.value.single().text)
    }

    @Test
    fun `the inbox ignores a time that already passed`() = runTest {
        messages.threads.value = listOf(conversation(1, "0525550100", name = "Danny", snippet = "x"))
        val vm = inbox()
        collect(vm.state)
        assertFalse(vm.remind(vm.state.value.conversations.single(), Now - 1))
        assertTrue(reminders.state.value.isEmpty())
    }
}
