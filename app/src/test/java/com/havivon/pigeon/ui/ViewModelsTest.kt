package com.havivon.pigeon.ui

import com.havivon.pigeon.domain.analysis.AnalysisSeams
import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.Contact
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.RuleMatch
import com.havivon.pigeon.domain.model.SearchResults
import com.havivon.pigeon.domain.model.SendStatus
import com.havivon.pigeon.domain.model.SmsType
import com.havivon.pigeon.domain.model.SpamReason
import com.havivon.pigeon.domain.model.ThreadItem
import com.havivon.pigeon.fakes.FakeContacts
import com.havivon.pigeon.fakes.FakeFavorites
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
import com.havivon.pigeon.ui.inbox.InboxViewModel
import com.havivon.pigeon.ui.search.DateRange
import com.havivon.pigeon.ui.search.SearchFilters
import com.havivon.pigeon.ui.search.SearchViewModel
import com.havivon.pigeon.ui.search.filterResults
import com.havivon.pigeon.ui.search.senderOptions
import com.havivon.pigeon.ui.spam.SpamViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val cancelled = mutableListOf<Long>()

    /** Keeps a StateFlow subscribed for the test, as a screen would. */
    private fun TestScope.collect(flow: kotlinx.coroutines.flow.Flow<*>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { } }
    }

    private fun inbox() = InboxViewModel(messages, rules, pins, locks, spam, reminders, scheduler, mapper, MutableStateFlow(0), { "date line" }, { cancelled += it }, now = { Now }, work = main.dispatcher)

    private fun TestScope.thread(threadId: Long = 1) = ConversationViewModel(
        threadId, messages, sender, rules, favorites, reminders, scheduler, spam, locks, pins, mapper, labels, Utc,
        me = "Me", cancelNotification = { cancelled += it }, appScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)), now = { Now }, work = main.dispatcher,
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
        val deletion = vm.delete(mom)
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

    @Test
    fun `the spam screen lists spam with its reasons, not spam goes back`() = runTest {
        seedInbox()
        spam.markSpam("+972551234567", 4, listOf(SpamReason.ShortLink, SpamReason.Keyword("won")))
        val vm = SpamViewModel(messages, spam, locks, mapper, MutableStateFlow(0), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), now = { Now }, work = main.dispatcher)
        collect(vm.state)
        val item = vm.state.value.items.single()
        assertEquals(4L, item.id)
        assertEquals(listOf(SpamReason.ShortLink, SpamReason.Keyword("won")), item.spamReasons)
        vm.notSpam(item)
        assertTrue(vm.state.value.items.isEmpty())
        assertTrue(spam.isNotSpam("0551234567"))
    }

    @Test
    fun `delete all spam is one undo`() = runTest {
        seedInbox()
        spam.markSpam("+972551234567", 4, listOf(SpamReason.ShortLink))
        spam.markSpam("Leumi", 2, listOf(SpamReason.BrandSender))
        val vm = SpamViewModel(messages, spam, locks, mapper, MutableStateFlow(0), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), now = { Now }, work = main.dispatcher)
        collect(vm.state)
        val (count, deletion) = vm.deleteAll()!!
        assertEquals(2, count)
        assertTrue(vm.state.value.items.isEmpty())
        deletion.undo()
        assertEquals(2, vm.state.value.items.size)
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
        assertEquals(listOf(1L), filterResults(found, SearchFilters(hasLink = true), Now, finder).messages.map { it.id })
        assertTrue(filterResults(found, SearchFilters(hasLink = true), Now, finder).conversations.isEmpty())
        assertEquals(listOf(2L), filterResults(found, SearchFilters(hasCode = true), Now, finder).messages.map { it.id })
        assertEquals(listOf(1L), filterResults(found, SearchFilters(range = DateRange.Last7Days), Now, finder).messages.map { it.id })
        assertEquals(listOf(1L, 2L), filterResults(found, SearchFilters(range = DateRange.Last30Days), Now, finder).messages.map { it.id })
        assertEquals(listOf(1L, 3L), filterResults(found, SearchFilters(threadId = 1), Now, finder).messages.map { it.id })
        assertEquals(listOf(1L, 2L), senderOptions(found) { mapOf(1L to "Danny", 2L to "Verify")[it] }.map { it.threadId })
    }

    @Test
    fun `search waits for typing to pause, then shows names and messages`() = runTest {
        messages.threads.value = listOf(conversation(1, "0525550100", name = "Danny"), conversation(2, "Verify"))
        messages.searchResults = found
        val vm = SearchViewModel(messages, mapper, labels, AnalysisSeams.codeFinder, now = { Now }, work = main.dispatcher)
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
        vm.setSender(2)
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

    private fun TestScope.search() = SearchViewModel(
        messages, mapper, labels, AnalysisSeams.codeFinder, now = { Now }, lockedThreads = locks.state, work = main.dispatcher,
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
    fun `the sender chip offers the conversations before anything is typed, never a locked one`() = runTest {
        messages.threads.value = listOf(conversation(1, "0525550100", name = "Danny"), conversation(2, "Verify"), conversation(3, "0535550100", name = "Secret"))
        messages.allMessages.value = listOf(sms(1, 1, "Hello", millis(2026, 10, 31, 9, 0)), sms(2, 2, "Code 1234", millis(2026, 10, 31, 10, 0), address = "Verify"))
        locks.state.value = setOf(3L)
        val vm = search()
        collect(vm.state)
        advanceTimeBy(300)
        assertEquals(listOf("Danny", "Verify"), vm.state.value.senders.map { it.name })
        vm.setSender(1)
        advanceTimeBy(300)
        assertEquals(listOf(1L), messages.browsed)
        assertEquals("Danny", vm.state.value.senderName)
        assertEquals(listOf(1L), vm.state.value.messages.map { it.messageId })
    }
}
