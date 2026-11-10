package com.havivon.pigeon.ui.search

import com.havivon.pigeon.domain.analysis.AnalysisSeams
import com.havivon.pigeon.domain.folders.FolderKeys
import com.havivon.pigeon.domain.model.CategoryTag
import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.FolderChoice
import com.havivon.pigeon.domain.model.RuleMatch
import com.havivon.pigeon.domain.model.SearchResults
import com.havivon.pigeon.domain.model.SmsType
import com.havivon.pigeon.domain.model.SpamReason
import com.havivon.pigeon.domain.model.rowColor
import com.havivon.pigeon.fakes.FakeFavorites
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
import com.havivon.pigeon.ui.inbox.ConversationSheetActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The search results as the inbox shows its rows: the tag of each result's category (the same sorting as the inbox, a move
 * by hand first), its colour (the category's own, else a matching rule's), the long-press sheet of a conversation result
 * and the selection of message results.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchResultsTest {

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
    private val folders = FakeFolders()
    private val moves = testMoves(folders, spam)
    private val cancelled = mutableListOf<Long>()

    private val orange = 0xFF00AA11L
    private val blue = 0xFF2F80EDL
    private val green = 0xFF1E9E6AL

    private fun TestScope.collect(flow: Flow<*>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { } }
    }

    private fun TestScope.sheetActions(canWrite: Boolean = true) = ConversationSheetActions(
        messages, pins, locks, moves, reminders, scheduler, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), { cancelled += it }, { canWrite }, { Now },
    )

    private fun TestScope.search(canWrite: Boolean = true) = SearchViewModel(
        messages, mapper, labels, AnalysisSeams.codeFinder, rules, pins, spam, folders, favorites, sheetActions(canWrite), "Me",
        now = { Now }, lockedThreads = locks.state, work = main.dispatcher,
    )

    /** Danny (a contact: Personal), Leumi (a bank), a stranger with no category, and a sender judged spam. */
    private fun seed() {
        messages.threads.value = listOf(
            conversation(1, "0525550100", name = "Danny", snippet = "The address is 12 Maple", dateMillis = millis(2026, 11, 1, 11, 2)),
            conversation(2, "Leumi", snippet = "Maple card charge of 249.90", dateMillis = millis(2026, 11, 1, 10, 0), unread = 1),
            conversation(3, "0541112222", snippet = "Maple tree service", dateMillis = millis(2026, 11, 1, 9, 0)),
            conversation(4, "+972551234567", snippet = "Maple prize: bit.ly/x7Kq9", dateMillis = millis(2026, 11, 1, 8, 0)),
        )
        messages.searchResults = SearchResults(
            conversations = messages.threads.value,
            messages = listOf(
                sms(10, 1, "Did you park on Maple Street?", millis(2026, 10, 31, 18, 24), "0525550100", SmsType.Sent),
                sms(11, 1, "The address: 12 Maple Street", millis(2026, 11, 1, 11, 2), "0525550100"),
                sms(20, 2, "Maple card charge of 249.90", millis(2026, 11, 1, 10, 0), "Leumi"),
                sms(30, 3, "Maple tree service", millis(2026, 11, 1, 9, 0), "0541112222"),
                sms(40, 4, "Maple prize: bit.ly/x7Kq9", millis(2026, 11, 1, 8, 0), "+972551234567"),
            ),
        )
        spam.verdictState.value = listOf(com.havivon.pigeon.domain.model.SpamVerdict("+972551234567", 4, listOf(SpamReason.ShortLink), 0))
    }

    private suspend fun TestScope.found(vm: SearchViewModel = search()): SearchUiState {
        collect(vm.state)
        vm.setQuery("Maple")
        advanceTimeBy(300)
        return vm.state.value
    }

    private fun SearchUiState.tagOfMessage(id: Long) = messages.first { it.messageId == id }.category
    private fun SearchUiState.tagOfConversation(id: Long) = conversations.first { it.id == id }.category

    // The category tag of each result

    @Test
    fun `every result is tagged with its category, a conversation and a message alike`() = runTest {
        seed()
        val s = found()
        // Built-in, found by the same classification as the inbox
        assertEquals(CategoryTag("Personal"), s.tagOfConversation(1))
        assertEquals(CategoryTag("Banks"), s.tagOfConversation(2))
        assertEquals(CategoryTag("Personal"), s.tagOfMessage(10))
        assertEquals(CategoryTag("Personal"), s.tagOfMessage(11))
        assertEquals(CategoryTag("Banks"), s.tagOfMessage(20))
        // No category (only "All"): the state holds none, and the row shows the "No category" tag in its place
        assertNull(s.tagOfConversation(3))
        assertNull(s.tagOfMessage(30))
        assertEquals(CategoryTag("No category"), resultTag(s.tagOfConversation(3), "No category"))
        assertEquals(CategoryTag("No category"), resultTag(s.tagOfMessage(30), "No category"))
        // Spam: tagged "Spam", neutral
        assertEquals(CategoryTag("Spam", null), s.tagOfConversation(4))
        assertEquals(CategoryTag("Spam", null), s.tagOfMessage(40))
    }

    @Test
    fun `a move by hand decides the tag, to a built-in folder or to a category of the user's`() = runTest {
        seed()
        val work = folders.add("Work")
        folders.setOverride(FolderKeys.of(2, listOf("Leumi")), FolderChoice.BuiltIn(Folder.Insurance))
        folders.setOverride(FolderKeys.of(3, listOf("0541112222")), FolderChoice.Custom(work))
        val s = found()
        assertEquals(CategoryTag("Insurance"), s.tagOfConversation(2))
        assertEquals(CategoryTag("Insurance"), s.tagOfMessage(20))
        assertEquals(CategoryTag("Work"), s.tagOfConversation(3))
        assertEquals(CategoryTag("Work"), s.tagOfMessage(30))
    }

    @Test
    fun `spam stays neutral whatever colour its folder or a rule has`() = runTest {
        seed()
        rules.add("Prize", RuleMatch.Keyword("prize"), orange, true)
        folders.setColor(FolderChoice.BuiltIn(Folder.Spam), blue)
        val s = found()
        assertNull(s.tagOfMessage(40)?.argb)
        assertNull("spam is never framed by a rule", s.messages.first { it.messageId == 40L }.ruleId)
        assertNull(s.conversations.first { it.id == 4L }.ruleId)
    }

    // Colour: the category's own, else a matching rule's, else neutral

    @Test
    fun `the tag has the category's colour, otherwise a matching rule's, otherwise it is neutral`() = runTest {
        seed()
        folders.setColor(FolderChoice.BuiltIn(Folder.Banks), green)
        rules.add("Danny", RuleMatch.Sender("Danny"), orange, true)
        val s = found()
        val rule = rules.state.value.first()
        val bank = s.tagOfMessage(20)!!
        assertEquals("the category has a colour", green, bank.argb)
        assertEquals(green, tagColor(bank, null))
        val danny = s.messages.first { it.messageId == 11L }
        assertEquals("a rule matches the sender", rule.id, danny.ruleId)
        assertNull("Personal has no colour of its own", danny.category!!.argb)
        assertEquals("so the tag takes the rule's colour", orange, tagColor(danny.category!!, rule))
        val stranger = s.messages.first { it.messageId == 30L }
        assertNull("no category, no tag", stranger.category)
        assertNull(stranger.ruleId)
        assertNull("no colour anywhere: neutral", tagColor(CategoryTag("Personal"), null))
    }

    @Test
    fun `every result row has a tag, and the no-category and spam tags are neutral`() = runTest {
        seed()
        val s = found()
        val none = "No category"
        val tags = s.conversations.map { resultTag(it.category, none) } + s.messages.map { resultTag(it.category, none) }
        assertEquals(s.conversations.size + s.messages.size, tags.size)
        assertTrue(tags.all { it.label.isNotBlank() })
        assertNull("no category: neutral", tagColor(resultTag(null, none), null))
        assertEquals("a rule colours the tag of a row with no category, as it does any other", orange, tagColor(resultTag(null, none), ColorRule(1, "R", RuleMatch.Sender("x"), orange)))
        assertNull("spam: neutral", tagColor(resultTag(s.tagOfMessage(40), none), null))
        assertEquals("a category keeps its own tag", CategoryTag("Banks"), resultTag(CategoryTag("Banks"), none))
    }

    @Test
    fun `a category's own colour wins over a rule's in the tag`() {
        val rule = ColorRule(1, "R", RuleMatch.Sender("x"), orange)
        assertEquals(green, tagColor(CategoryTag("Banks", green), rule))
        assertEquals(orange, tagColor(CategoryTag("Personal"), rule))
    }

    @Test
    fun `a matching rule frames a row before its category does`() {
        val rule = ColorRule(1, "Bank", RuleMatch.Sender("Leumi"), 0xFFE53935L)
        val banks = CategoryTag("Banks", green)
        assertEquals("the rule wins over the category", rule.argb, rowColor(rule, banks))
        assertEquals("no rule: the category's colour", green, rowColor(null, banks))
        assertNull("a category without a colour does not frame", rowColor(null, CategoryTag("Personal")))
        assertNull(rowColor(null, null))
        assertEquals(rule.argb, rowColor(rule, null))
    }

    @Test
    fun `a disabled rule colours nothing`() = runTest {
        seed()
        rules.add("Leumi", RuleMatch.Sender("Leumi"), orange, false)
        val s = found()
        assertNull(s.messages.first { it.messageId == 20L }.ruleId)
    }

    // The long-press sheet of a conversation result: the inbox's own actions

    @Test
    fun `the sheet pins, marks read, locks and unlocks, and reminds like the inbox`() = runTest {
        seed()
        val vm = search()
        val s = found(vm)
        val leumi = s.conversations.first { it.id == 2L }
        vm.sheet.togglePin(leumi)
        assertEquals(setOf(2L), pins.state.value)
        assertTrue(vm.sheet.markRead(leumi))
        assertEquals(listOf(2L), messages.markedRead)
        assertEquals("the notification goes with it", listOf(2L), cancelled)
        vm.sheet.lock(leumi)
        assertEquals(setOf(2L), locks.state.value)
        vm.sheet.unlock(leumi)
        assertTrue(locks.state.value.isEmpty())
        assertTrue(vm.sheet.remind(leumi, Now + 3_600_000))
        assertEquals(1, scheduler.scheduled.size)
        assertEquals(2L, reminders.state.value.single().threadId)
        assertNull("on the conversation, not on a message", reminders.state.value.single().messageId)
        assertFalse("a time in the past is ignored", vm.sheet.remind(leumi, Now - 1))
    }

    @Test
    fun `the sheet moves to a category, to spam and back, and makes a new category with a colour`() = runTest {
        seed()
        val vm = search()
        val s = found(vm)
        val danny = s.conversations.first { it.id == 1L }
        vm.sheet.moveTo(danny, FolderChoice.BuiltIn(Folder.Codes))
        advanceTimeBy(300)
        assertEquals(CategoryTag("Codes"), vm.state.value.tagOfConversation(1))
        assertTrue(vm.sheet.createFolderAndMove(danny, "Family", green))
        advanceTimeBy(300)
        assertEquals(CategoryTag("Family", green), vm.state.value.tagOfConversation(1))
        assertEquals(CategoryTag("Family", green), vm.state.value.tagOfMessage(11))
        assertEquals("the new category has the colour", green, folders.folderState.value.single().argb)
        assertFalse("a name taken by a built-in folder makes nothing", vm.sheet.createFolderAndMove(danny, "banks"))
        vm.sheet.moveTo(vm.state.value.conversations.first { it.id == 1L }, FolderChoice.BuiltIn(Folder.Spam))
        advanceTimeBy(300)
        assertEquals(CategoryTag("Spam"), vm.state.value.tagOfConversation(1))
        vm.sheet.moveTo(vm.state.value.conversations.first { it.id == 1L }, null)
        advanceTimeBy(300)
        assertEquals("back to the automatic sorting", CategoryTag("Personal"), vm.state.value.tagOfConversation(1))
    }

    @Test
    fun `the sheet deletes with undo, and as a viewer changes nothing`() = runTest {
        seed()
        val vm = search()
        val leumi = found(vm).conversations.first { it.id == 2L }
        val pending = vm.sheet.delete(leumi)
        assertNotNull(pending)
        assertTrue(messages.conversations.first().none { it.threadId == 2L })
        assertTrue(pending!!.undo())
        assertTrue(messages.conversations.first().any { it.threadId == 2L })

        val viewer = search(canWrite = false)
        assertNull("no delete while Pigeon is not the default app", viewer.sheet.delete(leumi))
        assertFalse("nor mark read", viewer.sheet.markRead(leumi))
        assertTrue(messages.markedRead.isEmpty())
        viewer.sheet.togglePin(leumi)
        assertEquals("a pin is Pigeon's own, so it works", setOf(2L), pins.state.value)
    }

    // Multi-select of message results

    @Test
    fun `a long press starts a selection, a tap adds and removes, leaving it clears`() = runTest {
        seed()
        val vm = search()
        found(vm)
        vm.select(11)
        vm.toggleSelected(20)
        vm.toggleSelected(30)
        vm.toggleSelected(20)
        assertEquals(setOf(11L, 30L), vm.selected.value)
        vm.clearSelection()
        assertTrue(vm.selected.value.isEmpty())
        vm.select(11)
        vm.setQuery("Mapl")
        assertTrue("a new search drops the selection", vm.selected.value.isEmpty())
    }

    @Test
    fun `copy and share give the conversation's lines across senders, oldest first`() = runTest {
        seed()
        val vm = search()
        found(vm)
        // Chosen newest first, whoever sent them
        vm.select(20)
        vm.toggleSelected(11)
        vm.toggleSelected(10)
        assertEquals(
            "[10/31/26 18:24] Me: Did you park on Maple Street?\n" +
                "[11/1/26 10:00] Leumi: Maple card charge of 249.90\n" +
                "[11/1/26 11:02] Danny: The address: 12 Maple Street",
            vm.selectedText(),
        )
    }

    @Test
    fun `a day's messages are stamped with the time only`() = runTest {
        seed()
        val vm = search()
        found(vm)
        vm.select(30)
        vm.toggleSelected(20)
        assertEquals("[09:00] 0541112222: Maple tree service\n[10:00] Leumi: Maple card charge of 249.90", vm.selectedText())
    }

    @Test
    fun `star stars all the selection, or removes the stars when they all are starred`() = runTest {
        seed()
        val vm = search()
        found(vm)
        vm.select(11)
        vm.toggleSelected(20)
        assertFalse(vm.allSelectedStarred())
        vm.starSelected()
        assertEquals(setOf(11L, 20L), favorites.state.value.map { it.messageId }.toSet())
        assertTrue("starring ends the selection", vm.selected.value.isEmpty())
        assertEquals("Danny", favorites.state.value.first { it.messageId == 11L }.senderName)
        assertNull("a bare number has no name to keep", favorites.state.value.first { it.messageId == 20L }.senderName)
        vm.select(11)
        vm.toggleSelected(20)
        assertTrue(vm.allSelectedStarred())
        vm.starSelected()
        assertTrue(favorites.state.value.isEmpty())
        // One starred, one not: starring adds the missing one
        favorites.star(11, 1, "0525550100", "Danny", "The address: 12 Maple Street", millis(2026, 11, 1, 11, 2))
        vm.select(11)
        vm.toggleSelected(20)
        assertFalse(vm.allSelectedStarred())
        vm.starSelected()
        assertEquals(setOf(11L, 20L), favorites.state.value.map { it.messageId }.toSet())
    }

    @Test
    fun `remind me is for exactly one message and replaces its earlier reminder`() = runTest {
        seed()
        val vm = search()
        found(vm)
        vm.select(11)
        vm.toggleSelected(20)
        assertFalse("two selected: no reminder", vm.remindSelected(Now + 3_600_000))
        assertTrue(reminders.state.value.isEmpty())
        vm.toggleSelected(20)
        assertTrue(vm.remindSelected(Now + 3_600_000))
        val first = reminders.state.value.single()
        assertEquals(11L, first.messageId)
        assertEquals(1L, first.threadId)
        assertEquals("The address: 12 Maple Street", first.text)
        assertTrue("it ends the selection", vm.selected.value.isEmpty())
        vm.select(11)
        assertTrue(vm.remindSelected(Now + 7_200_000))
        assertEquals("the earlier one is replaced", 1, reminders.state.value.size)
        assertEquals(listOf(first.id), scheduler.cancelled)
        vm.select(11)
        assertFalse("a time in the past is ignored", vm.remindSelected(Now - 1))
    }

    @Test
    fun `a locked chat's messages are neither shown nor acted on`() = runTest {
        seed()
        val vm = search()
        found(vm)
        vm.select(20)
        vm.toggleSelected(11)
        locks.state.value = setOf(2L)
        advanceTimeBy(300)
        assertTrue(vm.state.value.messages.none { it.threadId == 2L })
        assertEquals("only the visible one is copied", "[11:02] Danny: The address: 12 Maple Street", vm.selectedText())
        vm.starSelected()
        assertEquals(listOf(11L), favorites.state.value.map { it.messageId })
    }

    // The tap sheet of a result and the selection's actions

    @Test
    fun `a message result carries its conversation, for the tap sheet's category, spam and lock actions`() = runTest {
        seed()
        val s = found()
        assertEquals(1L, s.messages.first { it.messageId == 11L }.conversation?.id)
        assertEquals(false, s.messages.first { it.messageId == 11L }.conversation?.isSpam)
        assertEquals("a spam result's conversation says so, so the sheet offers Not spam", true, s.messages.first { it.messageId == 40L }.conversation?.isSpam)
        assertEquals(CategoryTag("Banks"), s.messages.first { it.messageId == 20L }.conversation?.category)
    }

    @Test
    fun `a message result's sheet stars, unstars, deletes with undo and copies in the conversation's format`() = runTest {
        seed()
        val vm = search()
        val s = found(vm)
        val hit = s.messages.first { it.messageId == 11L }
        vm.toggleStar(hit)
        assertEquals(listOf(11L), favorites.state.value.map { it.messageId })
        advanceTimeBy(300)
        vm.toggleStar(vm.state.value.messages.first { it.messageId == 11L })
        assertTrue("the second tap removes the star", favorites.state.value.isEmpty())
        assertEquals("[11:02] Danny: The address: 12 Maple Street", vm.textOf(listOf(hit)))
        val pending = vm.deleteMessage(hit)
        assertNotNull(pending)
        assertEquals(setOf(11L), messages.hiddenMessages.value)
        assertTrue(pending!!.undo())
        assertTrue(messages.hiddenMessages.value.isEmpty())
        assertNull("a viewer deletes nothing", search(canWrite = false).deleteMessage(hit))
        assertTrue(messages.hiddenMessages.value.isEmpty())
    }

    @Test
    fun `a message found by the sender filter has a sheet even when its thread is not in the inbox list`() = runTest {
        seed()
        // Thread 9 is no longer listed (the provider lost it); its messages still browse
        messages.allMessages.value = listOf(
            sms(90, 9, "Order 55 is ready\n" + "x".repeat(5000), millis(2026, 11, 1, 7, 0), "0501110000"),
            sms(91, 9, "x".repeat(5000), millis(2026, 11, 1, 6, 0), "0501110000", SmsType.Sent),
        )
        val vm = search()
        collect(vm.state)
        vm.setSender(9)
        advanceTimeBy(300)
        val hits = vm.state.value.messages
        assertEquals(listOf(90L, 91L), hits.map { it.messageId })
        hits.forEach { hit ->
            assertNull(hit.conversation)
            assertTrue(hit.name.isNotBlank())
            assertTrue(firstLine(hit.text).length <= 240)
            vm.textOf(listOf(hit))
        }
        vm.toggleStar(hits[0])
        vm.select(91L)
        assertTrue(vm.selectedConversationList().isEmpty())
        assertNotNull(vm.deleteSelected())
    }

    @Test
    fun `the sheet's spam and not spam move the conversation of a message result`() = runTest {
        seed()
        val vm = search()
        val s = found(vm)
        vm.sheet.markSpam(s.messages.first { it.messageId == 30L }.conversation!!)
        advanceTimeBy(300)
        assertEquals(CategoryTag("Spam"), vm.state.value.tagOfMessage(30))
        vm.sheet.notSpam(vm.state.value.messages.first { it.messageId == 40L }.conversation!!)
        advanceTimeBy(300)
        assertFalse(vm.state.value.messages.first { it.messageId == 40L }.conversation!!.isSpam)
    }

    @Test
    fun `the header's select button starts selection mode with nothing chosen, taps toggle, leaving clears it`() = runTest {
        seed()
        val vm = search()
        found(vm)
        assertFalse(vm.selecting.value)
        vm.startSelecting()
        assertTrue("selection mode, nothing selected yet", vm.selecting.value && vm.selectedCount() == 0)
        vm.toggleSelected(11)
        vm.toggleSelectedConversation(2)
        assertEquals("messages and conversations can be selected together", 2, vm.selectedCount())
        vm.toggleSelected(11)
        assertTrue("an empty selection stays in the mode until it is left", vm.selecting.value)
        vm.clearSelection()
        assertFalse(vm.selecting.value)
        assertTrue(vm.selected.value.isEmpty() && vm.selectedConversations.value.isEmpty())
    }

    @Test
    fun `select in a result's sheet starts the mode with that result, and select all takes every result`() = runTest {
        seed()
        val vm = search()
        found(vm)
        vm.select(11)
        assertTrue(vm.selecting.value)
        assertEquals(setOf(11L), vm.selected.value)
        vm.selectConversation(2)
        assertEquals("both kinds at once", setOf(2L), vm.selectedConversations.value)
        vm.selectAll()
        assertEquals(setOf(1L, 2L, 3L, 4L), vm.selectedConversations.value)
        assertEquals(setOf(10L, 11L, 20L, 30L, 40L), vm.selected.value)
        vm.toggleSelectedConversation(3)
        assertEquals(setOf(1L, 2L, 4L), vm.selectedConversations.value)
        vm.setQuery("Mapl")
        assertFalse("a new search leaves the mode", vm.selecting.value)
    }

    @Test
    fun `moving the selected messages moves each sender's conversation once, to a category or a new one`() = runTest {
        seed()
        val work = folders.add("Work")
        val vm = search()
        found(vm)
        // Two messages of Danny and one of Leumi: two conversations
        vm.select(10)
        vm.toggleSelected(11)
        vm.toggleSelected(20)
        assertEquals(listOf(1L, 2L), vm.selectedConversationList().map { it.id })
        vm.moveSelected(FolderChoice.Custom(work))
        advanceTimeBy(300)
        assertEquals(CategoryTag("Work"), vm.state.value.tagOfMessage(11))
        assertEquals(CategoryTag("Work"), vm.state.value.tagOfMessage(20))
        assertNull("the unselected sender stays where it was", vm.state.value.tagOfMessage(30))
        assertTrue("the selection is done", vm.selected.value.isEmpty())

        vm.select(11)
        vm.toggleSelected(30)
        assertTrue(vm.createFolderAndMoveSelected("Family", green))
        advanceTimeBy(300)
        assertEquals(CategoryTag("Family", green), vm.state.value.tagOfMessage(11))
        assertEquals(CategoryTag("Family", green), vm.state.value.tagOfMessage(30))
        vm.select(11)
        assertFalse("a name a built-in folder has makes nothing", vm.createFolderAndMoveSelected("banks", null))
        assertEquals("and the selection stays", setOf(11L), vm.selected.value)
    }

    @Test
    fun `marking the selection as spam and as read acts on the senders of the selected messages`() = runTest {
        seed()
        val vm = search()
        found(vm)
        vm.select(20)
        vm.toggleSelected(30)
        assertEquals(1, vm.markSelectedRead())
        assertEquals(listOf(listOf(2L)), messages.markedReadBatches)
        assertTrue(vm.selected.value.isEmpty())
        vm.select(30)
        vm.markSelectedSpam()
        advanceTimeBy(300)
        assertEquals(CategoryTag("Spam"), vm.state.value.tagOfMessage(30))
        assertNull("spam stays neutral", vm.state.value.tagOfMessage(30)?.argb)
    }

    @Test
    fun `deleting the selection is one undo, for messages and for conversations`() = runTest {
        seed()
        val vm = search()
        found(vm)
        vm.select(10)
        vm.toggleSelected(30)
        val deleted = vm.deleteSelected()!!
        assertEquals(2, deleted.count)
        assertEquals(0, deleted.conversations)
        assertEquals(setOf(10L, 30L), messages.hiddenMessages.value)
        assertTrue(vm.selected.value.isEmpty())
        assertTrue("one undo brings them all back", deleted.deletion.undo())
        assertTrue(messages.hiddenMessages.value.isEmpty())

        vm.selectConversation(1)
        vm.toggleSelectedConversation(2)
        val conversations = vm.deleteSelected()!!
        assertEquals(0, conversations.messages)
        assertEquals(2, conversations.count)
        assertEquals(setOf(1L, 2L), messages.hiddenThreads.value)
        conversations.deletion.undo()
        assertTrue(messages.hiddenThreads.value.isEmpty())
        assertNull("nothing selected, nothing deleted", vm.deleteSelected())

        // Both kinds together: one undo brings both back
        vm.select(30)
        vm.selectConversation(4)
        val both = vm.deleteSelected()!!
        assertEquals(1, both.messages)
        assertEquals(1, both.conversations)
        assertEquals(setOf(30L), messages.hiddenMessages.value)
        assertEquals(setOf(4L), messages.hiddenThreads.value)
        assertTrue(both.deletion.undo())
        assertTrue(messages.hiddenMessages.value.isEmpty() && messages.hiddenThreads.value.isEmpty())
    }

    @Test
    fun `as a viewer the selection deletes and marks read nothing, and stays selected`() = runTest {
        seed()
        val vm = search(canWrite = false)
        found(vm)
        vm.select(11)
        assertNull(vm.deleteSelected())
        assertNull(vm.markSelectedRead())
        assertEquals(setOf(11L), vm.selected.value)
        assertTrue(messages.hiddenMessages.value.isEmpty())
        assertTrue(messages.markedReadBatches.isEmpty())
        vm.clearSelection()
        vm.select(40)
        vm.markSelectedSpam()
        advanceTimeBy(300)
        assertEquals("a move is Pigeon's own data, so it works", CategoryTag("Spam"), vm.state.value.tagOfMessage(40))
    }

    @Test
    fun `reminding about a single selected conversation or message is offered and saved`() = runTest {
        seed()
        val vm = search()
        found(vm)
        vm.selectConversation(2)
        assertTrue(vm.remindSelected(Now + 3_600_000))
        assertNull(reminders.state.value.single().messageId)
        vm.select(11)
        vm.toggleSelected(10)
        assertFalse("not for two", vm.remindSelected(Now + 3_600_000))
    }

    @Test
    fun `a locked chat's results are not shown, so no sheet or selection can reveal them`() = runTest {
        seed()
        val vm = search()
        found(vm)
        locks.state.value = setOf(1L)
        advanceTimeBy(300)
        assertTrue(vm.state.value.messages.none { it.threadId == 1L })
        vm.select(11)
        assertTrue("a selected result that went away is not copied", vm.selectedText().isEmpty())
        assertTrue("nor acted on", vm.selectedConversationList().isEmpty())
    }
}
