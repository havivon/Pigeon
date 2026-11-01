package com.havivon.pigeon.ui

import com.havivon.pigeon.domain.model.SmsAccess
import com.havivon.pigeon.domain.model.SmsType
import com.havivon.pigeon.domain.model.SpamReason
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
import com.havivon.pigeon.ui.spam.SpamViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Viewer mode: another app is the default, so nothing may change the system database. Every
 * control that would (delete, mark read, retry) changes nothing and tells the screen why, except
 * the quiet mark-read on opening a conversation; reading and sending work as usual.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewerModeTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val labels = englishLabels()
    private val mapper = testMapper(labels)
    private val messages = FakeMessageRepository()
    private val spam = FakeSpam()
    private val locks = FakeLocks()
    private val sender = FakeSmsSender(messages)
    private val cancelled = mutableListOf<Long>()
    private val judged = mutableListOf<Int>()
    private var canWrite = false
    private val folders = FakeFolders()

    private fun TestScope.collect(flow: kotlinx.coroutines.flow.Flow<*>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { } }
    }

    private fun inbox() = InboxViewModel(
        messages, FakeRules(), FakePins(), locks, spam, folders, testMoves(folders, spam), FakeReminders(), FakeScheduler(), mapper, MutableStateFlow(0), { "date line" }, { cancelled += it },
        canWrite = { canWrite }, judgeSpam = { list, _ -> judged += list.size }, now = { Now }, work = main.dispatcher,
    )

    private fun TestScope.spamScreen() = SpamViewModel(
        messages, spam, locks, mapper, MutableStateFlow(0), CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        canWriteNow = { canWrite }, judgeSpam = { list, _ -> judged += list.size }, now = { Now }, work = main.dispatcher,
    )

    private fun TestScope.thread() = ConversationViewModel(
        1, messages, sender, FakeRules(), FakeFavorites(), FakeReminders(), FakeScheduler(), spam, locks, FakePins(),
        folders, testMoves(folders, spam), mapper, labels, Utc,
        me = "Me", cancelNotification = { cancelled += it }, appScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        canWrite = { canWrite }, now = { Now }, work = main.dispatcher,
    )

    private fun seed() {
        messages.threads.value = listOf(
            conversation(1, "0525550100", name = "Mom", snippet = "Bring bread", dateMillis = millis(2026, 11, 1, 11, 0), unread = 1),
            conversation(4, "+972551234567", snippet = "You won! bit.ly/x7Kq9", dateMillis = millis(2026, 11, 1, 8, 0), unread = 1),
        )
        messages.allMessages.value = listOf(
            sms(10, 1, "Where are you?", millis(2026, 11, 1, 10, 0), read = true),
            sms(11, 1, "Bring bread", millis(2026, 11, 1, 11, 0), read = false),
            sms(12, 1, "On my way", millis(2026, 11, 1, 11, 5), type = SmsType.Failed),
        )
    }

    @Test
    fun `the access follows the role and the read permission`() {
        assertEquals(SmsAccess.Default, SmsAccess.of(isDefault = true, canReadSms = false))
        assertEquals(SmsAccess.Default, SmsAccess.of(isDefault = true, canReadSms = true))
        assertEquals(SmsAccess.Viewer, SmsAccess.of(isDefault = false, canReadSms = true))
        assertEquals(SmsAccess.None, SmsAccess.of(isDefault = false, canReadSms = false))
        assertTrue(SmsAccess.Viewer.canRead)
        assertFalse(SmsAccess.Viewer.canWrite)
        assertFalse(SmsAccess.None.canRead)
        assertTrue(SmsAccess.Default.canWrite)
    }

    @Test
    fun `a viewer's inbox reads everything, and mark read and delete only say why not`() = runTest {
        seed()
        val vm = inbox()
        collect(vm.state)
        val mom = vm.state.value.all.first { it.id == 1L }
        assertFalse(vm.markRead(mom))
        assertTrue(messages.markedRead.isEmpty())
        assertNull(vm.delete(mom))
        assertTrue(vm.state.value.all.any { it.id == 1L })
    }

    @Test
    fun `the default app's inbox marks read and deletes as before`() = runTest {
        seed()
        canWrite = true
        val vm = inbox()
        collect(vm.state)
        val mom = vm.state.value.all.first { it.id == 1L }
        assertTrue(vm.markRead(mom))
        assertEquals(listOf(1L), messages.markedRead)
        assertTrue(vm.delete(mom) != null)
    }

    @Test
    fun `spam is judged on read as a viewer and as the default app, after the list is shown`() = runTest {
        seed()
        collect(inbox().state)
        // After the list is on the screen, not with it
        assertTrue(judged.isEmpty())
        advanceUntilIdle()
        assertEquals(listOf(2), judged)
        judged.clear()
        // The default app too: new rules reach the messages already on the phone (what was judged is remembered)
        canWrite = true
        collect(inbox().state)
        advanceUntilIdle()
        assertEquals(listOf(2), judged)
    }

    @Test
    fun `spam is judged once per new list, not again for the verdicts it stores`() = runTest {
        seed()
        collect(inbox().state)
        advanceUntilIdle()
        spam.markSpam("+972551234567", 4, listOf(SpamReason.ShortLink))
        advanceUntilIdle()
        assertEquals(listOf(2), judged)
        messages.threads.value = messages.threads.value + conversation(7, "+972557654321", snippet = "Hi", dateMillis = millis(2026, 11, 1, 9, 0))
        advanceUntilIdle()
        assertEquals(listOf(2, 3), judged)
    }

    @Test
    fun `a viewer's spam folder shows the verdicts, keeps Not spam, and refuses deleting`() = runTest {
        seed()
        spam.markSpam("+972551234567", 4, listOf(SpamReason.ShortLink))
        val vm = spamScreen()
        collect(vm.state)
        val group = vm.state.value.groups.single()
        assertFalse(vm.canWrite)
        // Nothing deleted: the screen shows "only when Pigeon is your messaging app" instead
        assertNull(vm.delete(group))
        assertNull(vm.deleteAll())
        assertEquals(1, vm.state.value.items.size)
        vm.notSpam(group)
        assertTrue(vm.state.value.items.isEmpty())
    }

    @Test
    fun `opening a conversation as a viewer does not mark it read, quietly`() = runTest {
        seed()
        val vm = thread()
        val events = mutableListOf<ConversationEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventFlow.toList(events) }
        collect(vm.state)
        vm.setVisible(true)
        assertTrue(messages.markedRead.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun `a viewer's delete, delete conversation and retry each say it needs the default app`() = runTest {
        seed()
        val vm = thread()
        val events = mutableListOf<ConversationEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventFlow.toList(events) }
        collect(vm.state)
        vm.select(10)
        assertNull(vm.deleteSelected())
        // The selection stays and the message is still there
        assertEquals(setOf(10L), vm.selected.value)
        assertEquals(3, vm.state.value.messages.size)
        assertNull(vm.deleteConversation())
        vm.retry(12)
        assertTrue(sender.retried.isEmpty())
        assertEquals(List(3) { ConversationEvent.NeedsDefault }, events)
    }

    @Test
    fun `a viewer still sends, once`() = runTest {
        seed()
        val vm = thread()
        collect(vm.state)
        vm.setDraft("Coming")
        vm.send()
        vm.send()
        assertEquals(1, sender.sent.size)
        assertEquals("", vm.draft.value)
    }
}
