package com.havivon.pigeon.ui.search

import com.havivon.pigeon.domain.analysis.AnalysisSeams
import com.havivon.pigeon.domain.model.SearchResults
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
import com.havivon.pigeon.fakes.Utc
import com.havivon.pigeon.fakes.conversation
import com.havivon.pigeon.fakes.englishLabels
import com.havivon.pigeon.fakes.millis
import com.havivon.pigeon.fakes.sms
import com.havivon.pigeon.fakes.testMapper
import com.havivon.pigeon.fakes.testMoves
import com.havivon.pigeon.ui.inbox.ConversationSheetActions
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The date filter in the search: both kinds of result, no typed text (browse), and together with the other chips.
 * "Now" is Sunday 1 November 2026, 12:00 UTC.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchDateFilterTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val labels = englishLabels()
    private val messages = FakeMessageRepository()
    private val locks = FakeLocks()
    private val favorites = FakeFavorites()
    private val folders = FakeFolders()
    private val spam = FakeSpam()

    private fun span(m: Int, a: Int, b: Int = a) = DaySpan(LocalDate.of(2026, m, a), LocalDate.of(2026, m, b))

    private fun TestScope.collect(flow: Flow<*>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { } }
    }

    private fun TestScope.search() = SearchViewModel(
        messages, testMapper(labels), labels, AnalysisSeams.codeFinder, FakeRules(), FakePins(), spam, folders, favorites,
        ConversationSheetActions(
            messages, FakePins(), locks, testMoves(folders, spam), FakeReminders(), FakeScheduler(),
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)), {}, { true }, { Now },
        ),
        "Me", now = { Now }, lockedThreads = locks.state, work = main.dispatcher, zone = Utc,
    )

    // Danny (thread 1) on the 3rd, 5th and 10th of October; the bank (thread 2) on the 10th (a code) and the 11th
    private val october3 = sms(1, 1, "Maple on the third, see example.com/a", millis(2026, 10, 3, 0, 0))
    private val october5 = sms(2, 1, "Maple on the fifth", millis(2026, 10, 5, 12, 0))
    private val october10 = sms(3, 1, "Maple on the tenth", millis(2026, 10, 10, 23, 59))
    private val bankOctober10 = sms(4, 2, "Maple code 482913", millis(2026, 10, 10, 8, 0), address = "Leumi")
    private val october11 = sms(5, 2, "Maple the day after", millis(2026, 10, 11, 0, 0), address = "Leumi")

    private fun seed() {
        messages.threads.value = listOf(
            conversation(1, "0525550100", name = "Danny", snippet = "Maple on the tenth", dateMillis = millis(2026, 10, 10, 23, 59)),
            conversation(2, "Leumi", snippet = "Maple the day after", dateMillis = millis(2026, 10, 11, 0, 0)),
            conversation(3, "0541112222", name = "Old Maple", snippet = "Hello", dateMillis = millis(2026, 9, 1, 9, 0)),
        )
        val all = listOf(october3, october5, october10, bankOctober10, october11)
        messages.allMessages.value = all
        messages.searchResults = SearchResults(messages.threads.value, all)
    }

    private suspend fun TestScope.typed(vm: SearchViewModel, query: String = "Maple") {
        collect(vm.state)
        vm.setQuery(query)
        advanceTimeBy(300)
    }

    private fun SearchUiState.ids() = messages.map { it.messageId }.sorted()

    @Test
    fun `a range keeps the messages and the conversations in its days, both ends included`() = runTest {
        seed()
        val vm = search()
        typed(vm)
        vm.setDates(span(10, 3, 10))
        advanceTimeBy(300)
        val s = vm.state.value
        assertEquals(listOf(1L, 2L, 3L, 4L), s.ids())
        // A conversation counts by its last message: Danny's (10 Oct 23:59) is in; the bank's (11 Oct 00:00) and the old one are out
        assertEquals(listOf(1L), s.conversations.map { it.id })
        assertTrue(s.filters.any)
    }

    @Test
    fun `one day is that day only`() = runTest {
        seed()
        val vm = search()
        typed(vm)
        vm.setDates(span(10, 10))
        advanceTimeBy(300)
        assertEquals(listOf(3L, 4L), vm.state.value.ids())
        vm.setDates(span(10, 11))
        advanceTimeBy(300)
        assertEquals(listOf(5L), vm.state.value.ids())
    }

    @Test
    fun `clearing the dates brings everything back`() = runTest {
        seed()
        val vm = search()
        typed(vm)
        vm.setDates(span(10, 5))
        advanceTimeBy(300)
        assertEquals(listOf(2L), vm.state.value.ids())
        vm.setDates(null)
        advanceTimeBy(300)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), vm.state.value.ids())
    }

    @Test
    fun `with no text the days alone browse the messages in them`() = runTest {
        seed()
        val vm = search()
        collect(vm.state)
        advanceTimeBy(300)
        assertTrue("nothing to show before a filter", vm.state.value.messages.isEmpty())
        vm.setDates(span(10, 3, 5))
        advanceTimeBy(300)
        assertEquals(listOf(1L, 2L), vm.state.value.ids())
        assertTrue("browse mode shows messages only", vm.state.value.conversations.isEmpty())
    }

    @Test
    fun `browsing asks the repository for the days, so an old range is not behind the newest messages`() = runTest {
        seed()
        val vm = search()
        collect(vm.state)
        vm.setDates(span(10, 3, 10))
        advanceTimeBy(300)
        assertEquals(span(10, 3, 10).startMillis(Utc) to span(10, 3, 10).endMillisExclusive(Utc), messages.browsedSpans.last())
    }

    @Test
    fun `the days combine with the sender, link and code chips`() = runTest {
        seed()
        val vm = search()
        typed(vm)
        vm.setDates(span(10, 3, 10))
        vm.setSender(1)
        advanceTimeBy(300)
        assertEquals(listOf(1L, 2L, 3L), vm.state.value.ids())
        vm.toggleHasLink()
        advanceTimeBy(300)
        assertEquals("a link, in those days, from Danny", listOf(1L), vm.state.value.ids())
        vm.toggleHasLink()
        vm.setSender(null)
        vm.toggleHasCode()
        advanceTimeBy(300)
        assertEquals("a code in those days", listOf(4L), vm.state.value.ids())
    }

    @Test
    fun `the days narrow the starred messages too`() = runTest {
        seed()
        favorites.star(2, 1, "0525550100", "Danny", october5.body, october5.dateMillis)
        favorites.star(5, 2, "Leumi", null, october11.body, october11.dateMillis)
        val vm = search()
        typed(vm)
        vm.toggleStarred()
        advanceTimeBy(300)
        assertEquals(listOf(2L, 5L), vm.state.value.ids())
        vm.setDates(span(10, 1, 7))
        advanceTimeBy(300)
        assertEquals(listOf(2L), vm.state.value.ids())
    }

    @Test
    fun `the days stay while the text changes, and clearing the filters clears them`() = runTest {
        seed()
        val vm = search()
        typed(vm)
        vm.setDates(span(10, 10))
        vm.setQuery("code")
        advanceTimeBy(300)
        assertEquals(span(10, 10), vm.state.value.filters.dates)
        vm.clearFilters()
        assertNull(vm.state.value.filters.dates)
    }
}
