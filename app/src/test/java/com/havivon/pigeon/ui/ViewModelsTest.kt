package com.havivon.pigeon.ui

import com.havivon.pigeon.domain.analysis.AnalysisSeams
import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.Contact
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.RuleMatch
import com.havivon.pigeon.domain.model.SearchResults
import com.havivon.pigeon.domain.model.SendStatus
import com.havivon.pigeon.domain.model.SmsMessage
import com.havivon.pigeon.domain.model.SmsType
import com.havivon.pigeon.domain.model.SpamReason
import com.havivon.pigeon.domain.model.ThreadItem
import com.havivon.pigeon.domain.repository.MessageRepository
import com.havivon.pigeon.fakes.FakeContacts
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
import com.havivon.pigeon.ui.compose.NewMessageEvent
import com.havivon.pigeon.ui.compose.NewMessageViewModel
import com.havivon.pigeon.ui.compose.Suggestion
import com.havivon.pigeon.ui.conversation.ConversationEvent
import com.havivon.pigeon.ui.conversation.ConversationViewModel
import com.havivon.pigeon.ui.conversation.FIRST_PAGE
import com.havivon.pigeon.ui.conversation.limitToReach
import com.havivon.pigeon.ui.inbox.ConversationSheetActions
import com.havivon.pigeon.ui.inbox.InboxViewModel
import com.havivon.pigeon.ui.search.DaySpan
import com.havivon.pigeon.ui.search.SearchFilters
import com.havivon.pigeon.ui.search.SearchViewModel
import com.havivon.pigeon.ui.search.filterResults
import com.havivon.pigeon.ui.search.SenderFilter
import com.havivon.pigeon.ui.search.toFilter
import com.havivon.pigeon.ui.spam.SpamViewModel
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.ui.spam.groupSpam
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelsTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val labels = englishLabels()
    private val mapper = testMapper(labels)
    private val messages = FakeMessageRepository()
    private val rules = FakeRules()
    private val pins = FakePins()
    private val locks = FakeLocks()
    private val spam = FakeSpam()
    private val favorites = FakeFavorites()
    private val reminders = FakeReminders()
    private val scheduler = FakeScheduler()
    private val sender = FakeSmsSender(messages)
    private val folders = FakeFolders()
    private val moves = testMoves(folders, spam)
    private val cancelled = mutableListOf<Long>()

    /** Keeps a StateFlow subscribed for the test, as a screen would. */
    private fun TestScope.collect(flow: kotlinx.coroutines.flow.Flow<*>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { } }
    }

    private fun inbox() = InboxViewModel(messages, rules, pins, locks, spam, folders, moves, reminders, scheduler, mapper, MutableStateFlow(0), { "date line" }, { cancelled += it }, now = { Now }, work = main.dispatcher)

    private fun TestScope.thread(threadId: Long = 1, focusMessageId: Long? = null) = ConversationViewModel(
        threadId, messages, sender, rules, favorites, reminders, scheduler, spam, locks, pins, folders, moves, mapper, labels, Utc,
        me = "Me", cancelNotification = { cancelled += it }, appScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)), now = { Now }, work = main.dispatcher,
        focusMessageId = focusMessageId,
    )

    private fun seedInbox() {
        messages.threads.value = listOf(
            conversation(1, "0525550100", name = "Mom", snippet = "Bring bread", dateMillis = millis(2026, 11, 1, 11, 0), unread = 2),
            conversation(2, "Leumi", snippet = "Charge of 249.90 on the card ending 4821", dateMillis = millis(2026, 11, 1, 10, 0), unread = 1),
            conversation(3, "Verify", snippet = "Your verification code is 482913", dateMillis = millis(2026, 11, 1, 9, 0), unread = 1),
            conversation(4, "+972551234567", snippet = "You won! bit.ly/x7Kq9", dateMillis = millis(2026, 11, 1, 8, 0), unread = 1),
        )
    }

    // Inbox

    @Test
    fun `the inbox maps conversations, leaves spam out and counts folders`() = runTest {
        seedInbox()
        spam.markSpam("+972551234567", 4, listOf(SpamReason.ShortLink))
        val vm = inbox()
        collect(vm.state)
        val s = vm.state.value
        assertTrue(s.loaded)
        assertEquals(listOf(1L, 2L, 3L), s.conversations.map { it.id })
        assertEquals("482913", s.conversations.first { it.id == 3L }.code)
        assertEquals(4, s.folderCounts[Folder.All])
        assertEquals(2, s.folderCounts[Folder.Personal])
        assertEquals(1, s.folderCounts[Folder.Codes])
        assertEquals(1, s.folderCounts[Folder.Spam])
        assertEquals("date line", s.dateLine)
    }

    @Test
    fun `folder chips filter, and spam never becomes the chosen chip`() = runTest {
        seedInbox()
        val vm = inbox()
        collect(vm.state)
        vm.selectFolder(Folder.Codes)
        assertEquals(listOf(3L), vm.state.value.conversations.map { it.id })
        vm.selectFolder(Folder.Spam)
        assertEquals(Folder.Codes, vm.state.value.folder)
        vm.selectFolder(Folder.All)
        assertEquals(4, vm.state.value.conversations.size)
    }

    /** The unread messages behind [seedInbox]'s counts (Mom 2, Leumi 1, Verify 1, the spam sender 1), and a read one and a sent one. */
    private fun seedUnreadMessages() {
        messages.allMessages.value = listOf(
            sms(10, 1, "Bring bread", millis(2026, 11, 1, 10, 30), "0525550100", read = false),
            sms(11, 1, "and a bottle of milk", millis(2026, 11, 1, 11, 0), "0525550100", read = false),
            sms(12, 2, "Charge of 249.90 on the card ending 4821", millis(2026, 11, 1, 10, 0), "Leumi", read = false),
            sms(13, 3, "Your verification code is 482913", millis(2026, 11, 1, 9, 0), "Verify", read = false),
            sms(14, 4, "You won! bit.ly/x7Kq9", millis(2026, 11, 1, 8, 0), "+972551234567", read = false),
            sms(15, 1, "An old one", millis(2026, 10, 31, 8, 0), "0525550100", read = true),
            sms(16, 1, "On my way", millis(2026, 11, 1, 11, 5), "0525550100", type = SmsType.Sent, read = false),
        )
    }

    @Test
    fun `the unread toggle lists the unread messages, newest first, and lets go of a conversation's once it is read`() = runTest {
        seedInbox()
        seedUnreadMessages()
        spam.markSpam("+972551234567", 4, listOf(SpamReason.ShortLink))
        val vm = inbox()
        collect(vm.state)
        // Mapped only while the toggle is on
        assertFalse(vm.state.value.unreadOnly)
        assertEquals(emptyList<Any>(), vm.state.value.unread)
        vm.selectUnreadOnly(true)
        assertEquals(listOf(11L, 10L, 12L, 13L), vm.state.value.unread!!.map { it.messageId })
        assertEquals(listOf("Mom", "Mom", "Leumi", "Verify"), vm.state.value.unread!!.map { it.name })
        assertEquals("482913", vm.state.value.unread!!.last().code)
        // The toggle counts messages, spam's left out: as many as the rows
        assertEquals(4, vm.state.value.unreadCount)
        vm.markRead(vm.state.value.all.first { it.id == 1L })
        assertEquals(listOf(12L, 13L), vm.state.value.unread!!.map { it.messageId })
        assertEquals(2, vm.state.value.unreadCount)
        vm.selectUnreadOnly(false)
        assertEquals(emptyList<Any>(), vm.state.value.unread)
    }

    @Test
    fun `a folder's unread count and its unread list always agree, however many unread messages there are elsewhere`() = runTest {
        // A bank message, older than many unread messages of another conversation: the count said 1, the list was empty
        val flood = (1..520).map { i ->
            sms(1000L + i, 5, "Hello $i", millis(2026, 11, 1, 12, 0) + i, "0541112222", read = false)
        }
        messages.threads.value = listOf(
            conversation(5, "0541112222", snippet = "Hello", dateMillis = millis(2026, 11, 1, 13, 0), unread = flood.size),
            conversation(2, "Leumi", snippet = "Charge of 249.90 on the card ending 4821", dateMillis = millis(2026, 11, 1, 10, 0), unread = 1),
        )
        messages.allMessages.value = flood + sms(12, 2, "Charge of 249.90 on the card ending 4821", millis(2026, 11, 1, 10, 0), "Leumi", read = false)
        val vm = inbox()
        collect(vm.state)
        vm.selectUnreadOnly(true)
        vm.selectFolder(Folder.Banks)
        assertEquals(1, vm.state.value.unreadCount)
        assertEquals(1, vm.state.value.folderCounts[Folder.Banks])
        assertEquals(listOf(12L), vm.state.value.unread!!.map { it.messageId })
        // Every folder: as many rows as the count says
        vm.selectFolder(Folder.All)
        assertEquals(vm.state.value.unreadCount, vm.state.value.unread!!.size)
    }

    @Test
    fun `the unread toggle applies to the chosen folder and stays on as the folder changes`() = runTest {
        seedInbox()
        seedUnreadMessages()
        spam.markSpam("+972551234567", 4, listOf(SpamReason.ShortLink))
        val vm = inbox()
        collect(vm.state)
        vm.selectUnreadOnly(true)
        vm.selectFolder(Folder.Banks)
        assertTrue(vm.state.value.unreadOnly)
        assertEquals(listOf(12L), vm.state.value.unread!!.map { it.messageId })
        assertEquals(1, vm.state.value.unreadCount)
        // The chip keeps its own count, the same number
        assertEquals(1, vm.state.value.folderCounts[Folder.Banks])
        vm.selectFolder(Folder.Codes)
        assertTrue(vm.state.value.unreadOnly)
        assertEquals(listOf(13L), vm.state.value.unread!!.map { it.messageId })
        vm.selectFolder(Folder.Personal)
        assertEquals(listOf(11L, 10L), vm.state.value.unread!!.map { it.messageId })
        assertEquals(2, vm.state.value.unreadCount)
        // A folder with nothing unread: an empty list, not a loading one
        vm.selectFolder(Folder.Deliveries)
        assertEquals(emptyList<Any>(), vm.state.value.unread)
        assertEquals(0, vm.state.value.unreadCount)
        // Back to every message: the folder's conversations again
        vm.selectUnreadOnly(false)
        vm.selectFolder(Folder.Banks)
        assertFalse(vm.state.value.unreadOnly)
        assertEquals(listOf(2L), vm.state.value.conversations.map { it.id })
        vm.selectFolder(Folder.All)
        assertEquals(3, vm.state.value.conversations.size)
        // Turned on again, it lists the unread messages of every folder
        vm.selectUnreadOnly(true)
        assertEquals(listOf(11L, 10L, 12L, 13L), vm.state.value.unread!!.map { it.messageId })
        assertEquals(4, vm.state.value.unreadCount)
    }

    @Test
    fun `the unread messages of a locked chat stay in its folder, and spam is in none`() = runTest {
        seedInbox()
        seedUnreadMessages()
        spam.markSpam("+972551234567", 4, listOf(SpamReason.ShortLink))
        val vm = inbox()
        collect(vm.state)
        vm.selectUnreadOnly(true)
        vm.lock(vm.state.value.all.first { it.id == 2L })
        vm.selectFolder(Folder.Banks)
        val row = vm.state.value.unread!!.single()
        assertEquals(12L, row.messageId)
        assertTrue(row.locked)
        assertEquals("Locked chat", row.text)
        for (folder in Folder.entries.filter { it != Folder.Spam }) {
            vm.selectFolder(folder)
            assertTrue("$folder", vm.state.value.unread!!.none { it.messageId == 14L })
        }
    }

    @Test
    fun `a new message arrives in the unread list, and a deleted conversation's leave it with undo`() = runTest {
        seedInbox()
        seedUnreadMessages()
        val vm = inbox()
        collect(vm.state)
        vm.selectUnreadOnly(true)
        messages.allMessages.value += sms(20, 2, "Another charge of 15.00", millis(2026, 11, 1, 11, 30), "Leumi", read = false)
        assertEquals(20L, vm.state.value.unread!!.first().messageId)
        val deletion = vm.delete(vm.state.value.all.first { it.id == 2L })!!
        assertEquals(listOf(11L, 10L, 13L, 14L), vm.state.value.unread!!.map { it.messageId })
        assertTrue(deletion.undo())
        assertEquals(listOf(20L, 11L, 10L, 12L, 13L, 14L), vm.state.value.unread!!.map { it.messageId })
    }

    @Test
    fun `a locked conversation's unread messages show the locked line`() = runTest {
        seedInbox()
        seedUnreadMessages()
        val vm = inbox()
        collect(vm.state)
        vm.selectUnreadOnly(true)
        vm.lock(vm.state.value.all.first { it.id == 3L })
        val verify = vm.state.value.unread!!.first { it.messageId == 13L }
        assertTrue(verify.locked)
        assertEquals("Locked chat", verify.text)
        assertNull(verify.code)
    }

    @Test
    fun `the unread list waits for its first read, not showing an empty list meanwhile`() = runTest {
        seedInbox()
        seedUnreadMessages()
        val vm = InboxViewModel(
            object : MessageRepository by messages {
                override suspend fun messagesById(ids: Collection<Long>): List<SmsMessage> = kotlinx.coroutines.awaitCancellation()
            },
            rules, pins, locks, spam, folders, moves, reminders, scheduler, mapper, MutableStateFlow(0), { "date line" }, { cancelled += it }, now = { Now }, work = main.dispatcher,
        )
        collect(vm.state)
        vm.selectUnreadOnly(true)
        assertTrue(vm.state.value.loaded)
        assertNull(vm.state.value.unread)
    }

    @Test
    fun `pin, mark read, lock and delete with undo`() = runTest {
        seedInbox()
        val vm = inbox()
        collect(vm.state)
        val mom = vm.state.value.conversations.first()
        vm.togglePin(mom)
        assertTrue(vm.state.value.conversations.first { it.id == 1L }.pinned)
        vm.markRead(mom)
        assertEquals(listOf(1L), cancelled)
        assertEquals(0, vm.state.value.conversations.first { it.id == 1L }.unread)
        vm.lock(mom)
        assertEquals("Locked chat", vm.state.value.conversations.first { it.id == 1L }.snippet)
        val deletion = vm.delete(mom)!!
        assertFalse(vm.state.value.conversations.any { it.id == 1L })
        assertTrue(deletion.undo())
        assertTrue(vm.state.value.conversations.any { it.id == 1L })
    }

    @Test
    fun `colour rules frame the matching conversation`() = runTest {
        seedInbox()
        rules.state.value = listOf(ColorRule(3, "Family", RuleMatch.Sender("Mom"), 0xFFE0559B))
        val vm = inbox()
        collect(vm.state)
        assertEquals(3, vm.state.value.conversations.first { it.id == 1L }.ruleId)
        assertNull(vm.state.value.conversations.first { it.id == 2L }.ruleId)
    }

    // Conversation

    private fun seedThread() {
        messages.threads.value = listOf(conversation(1, "0525550100", name = "Danny", snippet = "and the form"))
        messages.allMessages.value = listOf(
            sms(10, 1, "Shall we meet tomorrow at 10:00?", millis(2026, 10, 31, 18, 20), read = false),
            sms(11, 1, "Yes, sure", millis(2026, 10, 31, 18, 24), type = SmsType.Sent),
            sms(12, 1, "The address: 12 Maple Street", millis(2026, 11, 1, 11, 2), read = false),
            sms(13, 1, "On my way", millis(2026, 11, 1, 11, 3), type = SmsType.Failed),
        )
    }

    @Test
    fun `opening a conversation marks it read and cancels its notification`() = runTest {
        seedThread()
        val vm = thread()
        collect(vm.state)
        assertTrue(1L in messages.markedRead)
        assertTrue(1L in cancelled)
        val items = vm.state.value.items
        assertEquals(ThreadItem.Day("Yesterday"), items.first())
        assertEquals(ThreadItem.Day("Today"), items[3])
        assertEquals("Danny", vm.state.value.conversation?.name)
    }

    @Test
    fun `a long conversation opens on its newest page, and older pages come on scrolling up`() = runTest {
        messages.threads.value = listOf(conversation(1, "Shop", snippet = "Sale 120", dateMillis = millis(2026, 11, 1, 11, 0)))
        messages.allMessages.value = (1..120).map { i -> sms(i.toLong(), 1, "Sale $i", millis(2026, 10, 1, 8, 0) + i * 60_000L, address = "Shop") }
        val vm = thread()
        collect(vm.state)
        val first = vm.state.value.messages
        assertEquals(FIRST_PAGE, first.size)
        assertEquals((71L..120L).toList(), first.map { it.id })
        assertTrue(vm.state.value.hasOlder)
        vm.loadOlder()
        assertEquals(120, vm.state.value.messages.size)
        assertFalse(vm.state.value.hasOlder)
        // Oldest first, and nothing more to read
        assertEquals(1L, vm.state.value.messages.first().id)
        vm.loadOlder()
        assertEquals(120, vm.state.value.messages.size)
    }

    private fun seedLongThread(count: Int = 400) {
        messages.threads.value = listOf(conversation(1, "Shop", snippet = "Sale $count", dateMillis = millis(2026, 11, 1, 11, 0)))
        messages.allMessages.value = (1..count).map { i -> sms(i.toLong(), 1, "Sale $i", millis(2026, 10, 1, 8, 0) + i * 60_000L, address = "Shop") }
    }

    @Test
    fun `a message on the first page is there at once, with no more reading`() = runTest {
        seedLongThread()
        val vm = thread(focusMessageId = 380)
        collect(vm.state)
        assertEquals(FIRST_PAGE, vm.state.value.messages.size)
        assertTrue(vm.state.value.messages.any { it.id == 380L })
    }

    @Test
    fun `a message older than the first page is paged back to until it is loaded`() = runTest {
        seedLongThread()
        val vm = thread(focusMessageId = 250)
        collect(vm.state)
        // 50, then 100, 200: the first page that holds it
        assertEquals(200, vm.state.value.messages.size)
        assertTrue(vm.state.value.messages.any { it.id == 250L })
        assertTrue(vm.state.value.hasOlder)
        assertTrue(vm.state.value.loaded)
    }

    @Test
    fun `a message that is gone stops the paging at the start of the conversation`() = runTest {
        seedLongThread(count = 130)
        val vm = thread(focusMessageId = 9_999)
        collect(vm.state)
        assertEquals(130, vm.state.value.messages.size)
        assertFalse(vm.state.value.hasOlder)
    }

    @Test
    fun `without a message to reach, a conversation reads only its first page`() = runTest {
        seedLongThread()
        val vm = thread()
        collect(vm.state)
        assertEquals(FIRST_PAGE, vm.state.value.messages.size)
    }

    @Test
    fun `the next page to read doubles until the message is in it`() {
        val page = (51L..100L).map { sms(it, 1, "x", it) }
        assertNull(limitToReach(null, page, 50))
        assertNull(limitToReach(60, page, 50))
        assertEquals(100, limitToReach(5, page, 50))
        assertEquals(400, limitToReach(5, (1L..200L).map { sms(it + 1000, 1, "x", it) }, 200))
        // Everything is read and it is not there: stop
        assertNull(limitToReach(5, page.take(30), 50))
        assertNull(limitToReach(5, page, Int.MAX_VALUE))
    }

    @Test
    fun `select all takes the messages not read yet too`() = runTest {
        messages.threads.value = listOf(conversation(1, "Shop", snippet = "Sale 80", dateMillis = millis(2026, 11, 1, 11, 0)))
        messages.allMessages.value = (1..80).map { i -> sms(i.toLong(), 1, "Sale $i", millis(2026, 10, 1, 8, 0) + i * 60_000L, address = "Shop") }
        val vm = thread()
        collect(vm.state)
        vm.selectAll()
        assertEquals((1L..80L).toSet(), vm.selected.value)
        assertEquals(80, vm.state.value.messages.size)
    }

    @Test
    fun `failed messages show as failed, and a reminder is offered from the phrase`() = runTest {
        seedThread()
        val vm = thread()
        collect(vm.state)
        val byId = vm.state.value.messages.associateBy { it.id }
        assertEquals(SendStatus.Failed, byId.getValue(13).status)
        // "tomorrow at 10:00" written yesterday is today at 10:00, already past at noon
        assertNull(byId.getValue(10).reminder)
        vm.retry(13)
        assertEquals(listOf(13L), sender.retried)
    }

    @Test
    fun `a reminder chip creates the reminder and schedules it, a second tap removes it`() = runTest {
        messages.threads.value = listOf(conversation(1, "0525550100", name = "Danny"))
        messages.allMessages.value = listOf(sms(20, 1, "Dinner on Friday at 20:00", millis(2026, 11, 1, 10, 0)))
        val vm = thread()
        collect(vm.state)
        val msg = vm.state.value.messages.single()
        assertEquals("Fri 20:00", msg.reminder)
        vm.toggleReminder(msg)
        assertEquals(millis(2026, 11, 6, 20, 0), scheduler.scheduled.single().triggerAtMillis)
        assertTrue(vm.state.value.messages.single().reminderSet)
        vm.toggleReminder(vm.state.value.messages.single())
        assertEquals(listOf(scheduler.scheduled.single().id), scheduler.cancelled)
        assertTrue(reminders.state.value.isEmpty())
    }

    @Test
    fun `send goes to the conversation's number and clears the draft`() = runTest {
        seedThread()
        val vm = thread()
        collect(vm.state)
        vm.setDraft("  See you soon ")
        vm.send()
        assertEquals(listOf(listOf("0525550100") to "See you soon"), sender.sent)
        assertEquals("", vm.draft.value)
        assertEquals(SendStatus.Sending, vm.state.value.messages.last().status)
    }

    @Test
    fun `a blank draft is never sent, and a refused send keeps the text`() = runTest {
        seedThread()
        val vm = thread()
        collect(vm.state)
        vm.setDraft("   ")
        vm.send()
        assertTrue(sender.sent.isEmpty())
        sender.fail = true
        val events = mutableListOf<ConversationEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventFlow.collect { events += it } }
        vm.setDraft("Hello")
        vm.send()
        assertEquals("Hello", vm.draft.value)
        assertEquals(listOf<ConversationEvent>(ConversationEvent.SendFailed), events)
    }

    @Test
    fun `multi-select copies oldest first, stars and deletes with undo`() = runTest {
        seedThread()
        val vm = thread()
        collect(vm.state)
        vm.select(12)
        vm.select(11)
        assertEquals("[10/31/26 18:24] Me: Yes, sure\n[11/1/26 11:02] Danny: The address: 12 Maple Street", vm.selectedText())
        vm.starSelected()
        assertTrue(vm.selected.value.isEmpty())
        assertEquals(setOf(11L, 12L), favorites.state.value.map { it.messageId }.toSet())
        vm.select(11)
        vm.select(12)
        assertTrue(vm.allSelectedStarred())
        vm.starSelected()
        assertTrue(favorites.state.value.isEmpty())
        vm.select(10)
        val (count, deletion) = vm.deleteSelected()!!
        assertEquals(1, count)
        assertFalse(vm.state.value.messages.any { it.id == 10L })
        deletion.undo()
        assertTrue(vm.state.value.messages.any { it.id == 10L })
    }

    @Test
    fun `mark as spam is the user's own mark, and not spam brings it back`() = runTest {
        seedThread()
        spam.markNotSpam("0525550100")
        val vm = thread()
        collect(vm.state)
        vm.markSpam()
        assertEquals(listOf(SpamReason.MarkedByUser), spam.verdictState.value.single().reasons)
        assertTrue(vm.state.value.conversation!!.isSpam)
        vm.markNotSpam()
        assertTrue(spam.verdictState.value.isEmpty())
        assertFalse(vm.state.value.conversation!!.isSpam)
    }

    // Spam

    private fun TestScope.spamScreen(canWrite: Boolean = true) =
        SpamViewModel(
            messages, spam, locks, mapper, MutableStateFlow(0), CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            canWriteNow = { canWrite }, now = { Now }, work = main.dispatcher,
        )

    @Test
    fun `the spam search finds senders by number, by name and by message text, still one row per sender`() = runTest {
        seedSpam()
        markSeededSpam()
        spam.markSpam("0551234567", 6, listOf(SpamReason.UnknownSender))
        val vm = spamScreen()
        collect(vm.state)
        vm.openSearch()
        assertTrue(vm.state.value.searching)
        assertEquals(2, vm.state.value.groups.size)

        vm.setQuery("polls")
        advanceTimeBy(300)
        assertEquals(listOf(listOf(5L)), vm.state.value.groups.map { g -> g.conversations.map { it.id } })

        // The system's message search finds an older message of the number's first conversation
        messages.searchResults = SearchResults(emptyList(), listOf(sms(40, 4, "You won a prize", millis(2026, 11, 1, 9, 30), address = "+972551234567")))
        vm.setQuery("won a prize")
        advanceTimeBy(300)
        assertEquals(listOf(listOf(4L, 6L)), vm.state.value.groups.map { g -> g.conversations.map { it.id } })

        messages.searchResults = SearchResults.Empty
        vm.setQuery("055-123")
        advanceTimeBy(300)
        assertEquals(1, vm.state.value.groups.size)
        assertEquals(listOf(4L, 6L), vm.state.value.groups.single().conversations.map { it.id })

        vm.setQuery("nothing like this")
        advanceTimeBy(300)
        assertTrue(vm.state.value.groups.isEmpty())
        // Deleting everything still means every spam conversation, not only the search's
        assertEquals(3, vm.state.value.items.size)

        vm.closeSearch()
        assertFalse(vm.state.value.searching)
        assertEquals("", vm.state.value.query)
        assertEquals(2, vm.state.value.groups.size)
    }

    @Test
    fun `a long press selects senders, then delete and not spam act on the chosen ones at once`() = runTest {
        seedSpam()
        markSeededSpam()
        spam.markSpam("0551234567", 6, listOf(SpamReason.UnknownSender))
        val vm = spamScreen()
        collect(vm.state)
        val (number, polls) = vm.state.value.groups
        vm.select(number)
        assertTrue(vm.state.value.selecting)
        vm.toggleSelected(polls)
        assertEquals(setOf(number.key, polls.key), vm.state.value.selected)
        vm.toggleSelected(polls)
        assertEquals(setOf(number.key), vm.state.value.selected)

        val (n, deletion) = vm.deleteSelected()!!
        // Both conversations of the number, with one undo; the other sender stays
        assertEquals(2, n)
        assertEquals(listOf(5L), vm.state.value.items.map { it.id })
        assertFalse(vm.state.value.selecting)
        assertTrue(deletion.undo())
        assertEquals(3, vm.state.value.items.size)

        vm.select(vm.state.value.groups.first { it.key == polls.key })
        vm.clearSelection()
        assertFalse(vm.state.value.selecting)

        vm.select(vm.state.value.groups.first { it.key == polls.key })
        assertEquals(1, vm.notSpamSelected())
        assertTrue(spam.isNotSpam("Polls"))
        assertFalse(vm.state.value.selecting)
        assertEquals(listOf(4L, 6L), vm.state.value.items.map { it.id })
    }

    @Test
    fun `as a viewer the chosen senders are not deleted and selection mode stays`() = runTest {
        seedSpam()
        markSeededSpam()
        val vm = spamScreen(canWrite = false)
        collect(vm.state)
        vm.select(vm.state.value.groups.first())
        assertNull(vm.deleteSelected())
        assertTrue(vm.state.value.selecting)
        assertEquals(3, vm.state.value.items.size)
    }

    /** One sender in two conversations (the system wrote its number two ways), and another sender. */
    private fun seedSpam() {
        messages.threads.value = listOf(
            conversation(1, "0525550100", name = "Mom", snippet = "Bring bread", dateMillis = millis(2026, 11, 1, 11, 0)),
            conversation(4, "+972551234567", snippet = "You won! bit.ly/x7Kq9", dateMillis = millis(2026, 11, 1, 10, 0)).copy(incomingCount = 2),
            conversation(5, "Polls", snippet = "Quick poll: who will you vote for?", dateMillis = millis(2026, 11, 1, 9, 0)).copy(incomingCount = 1),
            conversation(6, "055-123-4567", snippet = "Claim your prize now", dateMillis = millis(2026, 10, 30, 9, 0)).copy(incomingCount = 3),
        )
        messages.allMessages.value = listOf(
            sms(40, 4, "You won a prize", millis(2026, 11, 1, 9, 30), address = "+972551234567"),
            sms(41, 4, "You won! bit.ly/x7Kq9", millis(2026, 11, 1, 10, 0), address = "+972551234567"),
            sms(50, 5, "Quick poll: who will you vote for?", millis(2026, 11, 1, 9, 0), address = "Polls"),
            sms(60, 6, "Claim your prize now", millis(2026, 10, 30, 9, 0), address = "0551234567"),
            sms(61, 6, "Stop it", millis(2026, 10, 30, 9, 5), address = "0551234567", type = SmsType.Sent),
        )
    }

    private suspend fun markSeededSpam() {
        spam.markSpam("+972551234567", 4, listOf(SpamReason.ShortLink, SpamReason.Keyword("won")))
        spam.markSpam("Polls", 5, listOf(SpamReason.UnknownSender, SpamReason.Poll))
    }

    @Test
    fun `the spam screen groups by sender, newest sender first, with counts and every reason once`() = runTest {
        seedSpam()
        markSeededSpam()
        // A second verdict on the same number written another way: still the same sender
        spam.markSpam("0551234567", 6, listOf(SpamReason.UnknownSender, SpamReason.ShortLink))
        val vm = spamScreen()
        collect(vm.state)
        val groups = vm.state.value.groups
        assertEquals(2, groups.size)
        val number = groups[0]
        assertEquals(listOf(4L, 6L), number.conversations.map { it.id })
        assertEquals(5, number.messageCount)
        // One verdict per sender: the conversations of one number share its reasons
        assertEquals(listOf(SpamReason.ShortLink, SpamReason.Keyword("won")), number.reasons)
        assertEquals(number.reasons.size, number.reasons.distinct().size)
        assertEquals(listOf(5L), groups[1].conversations.map { it.id })
        assertEquals(1, groups[1].messageCount)
        assertEquals(3, vm.state.value.items.size)
    }

    @Test
    fun `an opened row shows the sender's incoming messages, newest first, and stays open`() = runTest {
        seedSpam()
        markSeededSpam()
        spam.markSpam("0551234567", 6, listOf(SpamReason.UnknownSender))
        val vm = spamScreen()
        collect(vm.state)
        val number = vm.state.value.groups.first()
        vm.toggle(number)
        assertEquals(setOf(number.key), vm.state.value.expanded)
        val shown = vm.state.value.messages.getValue(number.key)
        assertEquals(listOf(41L, 40L, 60L), shown.map { it.id })
        assertEquals("bit.ly", shown.first().links.single().host)
        // A new message: the row stays open and shows it
        messages.allMessages.value += sms(42, 4, "Last chance", millis(2026, 11, 1, 10, 30), address = "+972551234567")
        messages.threads.value = messages.threads.value.map { if (it.threadId == 4L) it.copy(dateMillis = millis(2026, 11, 1, 10, 30)) else it }
        assertEquals(setOf(number.key), vm.state.value.expanded)
        assertEquals(42L, vm.state.value.messages.getValue(number.key).first().id)
        vm.toggle(vm.state.value.groups.first())
        assertTrue(vm.state.value.expanded.isEmpty())
        assertTrue(vm.state.value.messages.isEmpty())
    }

    @Test
    fun `a locked conversation's messages are never read into an opened row`() = runTest {
        seedSpam()
        markSeededSpam()
        locks.lock(4)
        val vm = spamScreen()
        collect(vm.state)
        val number = vm.state.value.groups.first()
        vm.toggle(number)
        val shown = vm.state.value.messages.getValue(number.key)
        // The other conversation of the number is read; of the locked one, only the "locked chat" line
        assertEquals(listOf(60L), shown.filter { !it.locked }.map { it.id })
        assertEquals(1, shown.count { it.locked })
        assertTrue(shown.none { it.text.contains("won") })
    }

    @Test
    fun `not spam brings back every conversation of the sender, under every way its number is written`() = runTest {
        seedSpam()
        markSeededSpam()
        spam.markSpam("0551234567", 6, listOf(SpamReason.UnknownSender))
        val vm = spamScreen()
        collect(vm.state)
        vm.notSpam(vm.state.value.groups.first())
        assertEquals(listOf(5L), vm.state.value.items.map { it.id })
        assertTrue(spam.isNotSpam("0551234567"))
        assertTrue(spam.isNotSpam("+972 55-123-4567"))
        // The filter leaves the sender alone from now on
        assertFalse(spam.markSpam("055-1234567", 6, listOf(SpamReason.ShortLink)))
    }

    @Test
    fun `delete removes all of a sender's spam with one undo, and the other senders stay`() = runTest {
        seedSpam()
        markSeededSpam()
        spam.markSpam("0551234567", 6, listOf(SpamReason.UnknownSender))
        val vm = spamScreen()
        collect(vm.state)
        val (count, deletion) = vm.delete(vm.state.value.groups.first())!!
        assertEquals(2, count)
        assertEquals(listOf(5L), vm.state.value.items.map { it.id })
        assertEquals(1, vm.state.value.groups.size)
        assertTrue(deletion.undo())
        assertEquals(listOf(4L, 5L, 6L), vm.state.value.items.map { it.id })
    }

    @Test
    fun `delete all spam is one undo`() = runTest {
        seedInbox()
        spam.markSpam("+972551234567", 4, listOf(SpamReason.ShortLink))
        spam.markSpam("Leumi", 2, listOf(SpamReason.BrandSender))
        val vm = spamScreen()
        collect(vm.state)
        val (count, deletion) = vm.deleteAll()!!
        assertEquals(2, count)
        assertTrue(vm.state.value.items.isEmpty())
        deletion.undo()
        assertEquals(2, vm.state.value.items.size)
    }

    @Test
    fun `spam is grouped by the normalised sender`() {
        val a = Conversation(1, "+972 55-123-4567", "", "", 0, addresses = listOf("+972551234567"), messageCount = 2)
        val b = Conversation(2, "055-123-4567", "", "", 0, addresses = listOf("0551234567"))
        val c = Conversation(3, "Polls", "", "", 0, addresses = listOf("POLLS"), messageCount = 4)
        val d = Conversation(4, "polls", "", "", 0, addresses = listOf("polls"), messageCount = 1)
        val groups = groupSpam(listOf(a, c, b, d))
        assertEquals(listOf(listOf(1L, 2L), listOf(3L, 4L)), groups.map { g -> g.conversations.map { it.id } })
        // A conversation never counted still holds its last message
        assertEquals(listOf(3, 5), groups.map { it.messageCount })
        assertEquals("+972 55-123-4567", groups[0].name)
    }

    // Search

    private val found = SearchResults(
        conversations = listOf(conversation(1, "0525550100", name = "Danny", dateMillis = millis(2026, 10, 1, 9, 0))),
        messages = listOf(
            sms(1, 1, "Danny's link: example.com/a", millis(2026, 10, 30, 9, 0)),
            sms(2, 2, "Your verification code is 482913, Danny", millis(2026, 10, 20, 9, 0), address = "Verify"),
            sms(3, 1, "Danny here", millis(2026, 9, 1, 9, 0)),
        ),
    )

    @Test
    fun `search filters by link, code, date and sender`() {
        val finder = AnalysisSeams.codeFinder
        assertEquals(listOf(1L), filterResults(found, SearchFilters(hasLink = true), finder, Utc).messages.map { it.id })
        assertTrue(filterResults(found, SearchFilters(hasLink = true), finder, Utc).conversations.isEmpty())
        assertEquals(listOf(2L), filterResults(found, SearchFilters(hasCode = true), finder, Utc).messages.map { it.id })
        val today = java.time.LocalDate.of(2026, 11, 1)
        val last7 = SearchFilters(dates = DaySpan(today.minusDays(6), today))
        val last30 = SearchFilters(dates = DaySpan(today.minusDays(29), today))
        assertEquals(listOf(1L), filterResults(found, last7, finder, Utc).messages.map { it.id })
        assertEquals(listOf(1L, 2L), filterResults(found, last30, finder, Utc).messages.map { it.id })
        assertEquals(listOf(1L, 3L), filterResults(found, SearchFilters(sender = SenderFilter("525550100", "Danny", setOf(1L))), finder, Utc).messages.map { it.id })
        assertEquals("by address when the thread is not known", listOf(1L, 3L), filterResults(found, SearchFilters(sender = SenderFilter("525550100", "Danny", emptySet())), finder, Utc).messages.map { it.id })
    }

    @Test
    fun `search waits for typing to pause, then shows names and messages`() = runTest {
        messages.threads.value = listOf(conversation(1, "0525550100", name = "Danny"), conversation(2, "Verify"))
        messages.searchResults = found
        val vm = search(locked = null)
        collect(vm.state)
        vm.setQuery("Dan")
        vm.setQuery("Danny")
        advanceTimeBy(300)
        assertEquals(listOf("Danny"), messages.searched)
        val s = vm.state.value
        assertFalse(s.searching)
        assertEquals(listOf("Danny"), s.conversations.map { it.name })
        assertEquals(listOf("Danny", "Verify", "Danny"), s.messages.map { it.name })
        vm.toggleHasCode()
        assertEquals(listOf(2L), vm.state.value.messages.map { it.messageId })
        vm.setSender(SenderFilter("verify", "Verify", setOf(2L)))
        assertEquals("Verify", vm.state.value.senderName)
    }

    // New message

    private val contacts = FakeContacts(listOf(Contact(1, "Dana Levi", listOf("0525550111", "0535550111")), Contact(2, "Noa", listOf("0545550122"))))

    private fun TestScope.newMessage(to: List<String> = emptyList(), body: String = "") =
        NewMessageViewModel(contacts, messages, sender, to, body, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

    @Test
    fun `contacts are suggested by name, a typed number is offered first`() = runTest {
        val vm = newMessage()
        collect(vm.state)
        vm.setQuery("dana")
        advanceTimeBy(200)
        assertEquals(listOf("0525550111", "0535550111"), vm.state.value.suggestions.map { it.number })
        vm.setQuery("0587")
        advanceTimeBy(200)
        assertEquals(listOf(Suggestion(null, "0587")), vm.state.value.suggestions)
        vm.add(Suggestion("Noa", "0545550122"))
        assertEquals("Noa", vm.state.value.recipients.single().name)
        assertEquals("", vm.state.value.query)
    }

    @Test
    fun `a comma ends a typed number, letters never become a recipient`() = runTest {
        val vm = newMessage()
        collect(vm.state)
        vm.setQuery("052-555-0199,")
        assertEquals(listOf("052-555-0199"), vm.state.value.recipients.map { it.address })
        vm.setQuery("hello")
        assertFalse(vm.commitTyped())
        assertEquals(1, vm.state.value.recipients.size)
    }

    @Test
    fun `a draft from a link is only prefilled, sending opens the conversation`() = runTest {
        messages.threads.value = listOf(conversation(7, "0525550111", name = "Dana Levi"))
        val vm = newMessage(listOf("0525550111"), "Hi from the link")
        collect(vm.state)
        assertTrue(sender.sent.isEmpty())
        assertEquals("Dana Levi", vm.state.value.recipients.single().name)
        assertTrue(vm.state.value.canSend)
        val events = mutableListOf<NewMessageEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventFlow.collect { events += it } }
        vm.send()
        assertEquals(listOf(listOf("0525550111") to "Hi from the link"), sender.sent)
        assertEquals(listOf<NewMessageEvent>(NewMessageEvent.OpenThread(7)), events)
    }

    @Test
    fun `several recipients each get the message, a refusal keeps the draft`() = runTest {
        val vm = newMessage(listOf("0525550111", "0545550122"), "Meeting moved")
        collect(vm.state)
        val events = mutableListOf<NewMessageEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventFlow.collect { events += it } }
        sender.fail = true
        vm.send()
        assertEquals("Meeting moved", vm.bodyText.value)
        sender.fail = false
        vm.send()
        assertEquals(listOf(NewMessageEvent.Failed, NewMessageEvent.SentToMany(2)), events)
        assertEquals("", vm.bodyText.first())
    }

    @Test
    fun `a person saved twice is suggested once, so the list never has two rows with one key`() = runTest {
        val twice = FakeContacts(listOf(Contact(1, "Mom", listOf("052-555-0111")), Contact(2, "Mom", listOf("+972525550111"))))
        val vm = NewMessageViewModel(twice, messages, sender, emptyList(), "", CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        collect(vm.state)
        vm.setQuery("mom")
        advanceTimeBy(200)
        assertEquals(1, vm.state.value.suggestions.size)
    }

    @Test
    fun `sent to one number without a conversation to open, it says so instead of opening an empty screen`() = runTest {
        messages.nextThreadId = 0
        val vm = newMessage(listOf("0587770000"), "Hi")
        collect(vm.state)
        val events = mutableListOf<NewMessageEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventFlow.collect { events += it } }
        vm.send()
        assertEquals(listOf<NewMessageEvent>(NewMessageEvent.SentToMany(1)), events)
    }

    // Search filters without typing

    /** The long-press sheet's actions, as the inbox and the search both build them. */
    private fun TestScope.sheetActions(canWrite: Boolean = true) = ConversationSheetActions(
        messages, pins, locks, moves, reminders, scheduler, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), { cancelled += it }, { canWrite }, { Now },
    )

    private fun TestScope.search(locked: Flow<Set<Long>>? = locks.state, canWrite: Boolean = true) = SearchViewModel(
        messages, mapper, labels, AnalysisSeams.codeFinder, rules, pins, spam, folders, favorites, sheetActions(canWrite), "Me",
        now = { Now }, lockedThreads = locked ?: flowOf(emptySet()), work = main.dispatcher,
    )

    @Test
    fun `a filter works without typing, has a code lists the messages with a code`() = runTest {
        messages.threads.value = listOf(conversation(1, "0525550100", name = "Danny"), conversation(2, "Verify"))
        messages.allMessages.value = listOf(
            sms(1, 1, "See you at 8", millis(2026, 10, 31, 9, 0)),
            sms(2, 2, "Your verification code is 482913", millis(2026, 10, 31, 10, 0), address = "Verify"),
        )
        val vm = search()
        collect(vm.state)
        assertTrue("nothing typed and no filter, nothing is read", messages.browsed.isEmpty())
        vm.toggleHasCode()
        advanceTimeBy(300)
        assertEquals(listOf(2L), vm.state.value.messages.map { it.messageId })
        assertTrue(messages.searched.isEmpty())
    }

    @Test
    fun `the sender search offers everyone, locked chats by name too, and the chosen sender browses all their conversations`() = runTest {
        messages.threads.value = listOf(
            conversation(1, "0525550100", name = "Danny"), conversation(2, "Verify"),
            conversation(3, "0535550100", name = "Secret"), conversation(4, "+972525550100", name = "Danny"),
        )
        messages.allMessages.value = listOf(
            sms(1, 1, "Hello", millis(2026, 10, 31, 9, 0)),
            sms(2, 2, "Code 1234", millis(2026, 10, 31, 10, 0), address = "Verify"),
            sms(3, 3, "Hidden", millis(2026, 10, 30, 10, 0), address = "0535550100"),
            sms(4, 4, "Another number format", millis(2026, 10, 29, 10, 0), address = "+972525550100"),
        )
        locks.state.value = setOf(3L)
        val vm = search()
        collect(vm.senderSuggestions)
        collect(vm.state)
        advanceTimeBy(300)
        assertEquals(listOf("Verify", "Danny", "Secret"), vm.senderSuggestions.value.map { it.title })
        assertEquals("Danny's two threads are one sender", 2, vm.senderSuggestions.value[1].conversations)
        vm.setSenderQuery("secret")
        advanceTimeBy(300)
        assertEquals(listOf("Secret"), vm.senderSuggestions.value.map { it.title })
        vm.setSenderQuery("+97252555")
        advanceTimeBy(300)
        val danny = vm.senderSuggestions.value.single()
        vm.setSender(danny.sender.toFilter())
        advanceTimeBy(300)
        assertEquals(listOf(setOf(1L, 4L)), messages.browsed)
        assertEquals("Danny", vm.state.value.senderName)
        assertEquals(listOf(1L, 4L), vm.state.value.messages.map { it.messageId }.sorted())
        vm.setSender(null)
        advanceTimeBy(300)
        assertTrue(vm.state.value.messages.isEmpty())
    }

    @Test
    fun `the starred chip lists the starred messages, also by text, never from a locked chat`() = runTest {
        messages.threads.value = listOf(conversation(1, "0525550100", name = "Danny"), conversation(3, "0535550100", name = "Secret"))
        favorites.star(10, 1, "0525550100", "Danny", "The address: 12 Maple Street", millis(2026, 10, 30, 9, 0))
        favorites.star(11, 1, "0525550100", "Danny", "Gate code 4590", millis(2026, 10, 30, 10, 0))
        favorites.star(12, 3, "0535550100", "Secret", "Hidden", millis(2026, 10, 30, 11, 0))
        locks.state.value = setOf(3L)
        val vm = search()
        collect(vm.state)
        vm.toggleStarred()
        advanceTimeBy(300)
        assertEquals(setOf(10L, 11L), vm.state.value.messages.map { it.messageId }.toSet())
        assertEquals("Danny", vm.state.value.messages.first().name)
        vm.setQuery("maple")
        advanceTimeBy(300)
        assertEquals(listOf(10L), vm.state.value.messages.map { it.messageId })
        assertTrue("starred results are messages only", vm.state.value.conversations.isEmpty())
    }
}
