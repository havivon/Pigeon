package com.havivon.pigeon.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.domain.analysis.LinkAnalyzer
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.SearchResults
import com.havivon.pigeon.domain.model.SmsConversation
import com.havivon.pigeon.domain.repository.MessageRepository
import com.havivon.pigeon.domain.seam.CodeFinder
import com.havivon.pigeon.ui.common.ConversationMapper
import com.havivon.pigeon.ui.common.DateLabels
import com.havivon.pigeon.ui.common.avatarColor
import com.havivon.pigeon.ui.common.ltr
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** How long typing must pause before the search runs. */
private const val DEBOUNCE_MS = 250L
private const val DAY_MS = 24 * 60 * 60 * 1000L

/** The newest messages the filters look through when nothing is typed. */
private const val BROWSE_LIMIT = 500

/** Conversations the sender chip offers before anything is typed. */
private const val MAX_SENDERS = 30

enum class DateRange(val days: Int?) { Any(null), Last7Days(7), Last30Days(30) }

data class SearchFilters(
    /** Only this conversation's messages (the "sender" chip). */
    val threadId: Long? = null,
    val hasLink: Boolean = false,
    val hasCode: Boolean = false,
    val range: DateRange = DateRange.Any,
) {
    val any: Boolean get() = threadId != null || hasLink || hasCode || range != DateRange.Any
}

/** A message that matched, as a result row. */
data class SearchHit(
    val messageId: Long,
    val threadId: Long,
    val name: String,
    val text: String,
    val time: String,
    val avatarArgb: Long,
    val outgoing: Boolean,
)

/** A sender the "sender" chip can narrow the results to. */
data class SenderOption(val threadId: Long, val name: String)

data class SearchUiState(
    val query: String = "",
    val filters: SearchFilters = SearchFilters(),
    /** True while a search for the current text is still running. */
    val searching: Boolean = false,
    val conversations: List<Conversation> = emptyList(),
    val messages: List<SearchHit> = emptyList(),
    val senders: List<SenderOption> = emptyList(),
) {
    val senderName: String? get() = senders.firstOrNull { it.threadId == filters.threadId }?.name
    val isEmpty: Boolean get() = conversations.isEmpty() && messages.isEmpty()
}

/**
 * Applies the filter chips to what the repository found. Conversations (matched by name or number)
 * only stay while no message-only filter (link, code) is on.
 */
fun filterResults(results: SearchResults, filters: SearchFilters, nowMillis: Long, codeFinder: CodeFinder): SearchResults {
    val since = filters.range.days?.let { nowMillis - it * DAY_MS }
    val messages = results.messages.filter { m ->
        (filters.threadId == null || m.threadId == filters.threadId) &&
            (since == null || m.dateMillis >= since) &&
            (!filters.hasLink || LinkAnalyzer.findLinks(m.body).isNotEmpty()) &&
            (!filters.hasCode || codeFinder.find(m.body) != null)
    }
    val conversations = if (filters.hasLink || filters.hasCode) {
        emptyList()
    } else {
        results.conversations.filter { c ->
            (filters.threadId == null || c.threadId == filters.threadId) && (since == null || c.dateMillis >= since)
        }
    }
    return SearchResults(conversations, messages)
}

/** The senders in the results, for the sender chip: each conversation once, in order of appearance. */
fun senderOptions(results: SearchResults, titleOf: (Long) -> String?): List<SenderOption> =
    (results.conversations.map { it.threadId } + results.messages.map { it.threadId })
        .distinct()
        .mapNotNull { id -> titleOf(id)?.let { SenderOption(id, it) } }

/**
 * Who the sender chip offers before anything is typed: the newest conversations, locked ones left out.
 * Once there are results, it offers the senders in them instead ([senderOptions]).
 */
fun browseSenders(conversations: List<SmsConversation>, locked: Set<Long>, limit: Int = MAX_SENDERS): List<SenderOption> =
    conversations.asSequence()
        .filter { it.threadId !in locked }
        .take(limit)
        .map { SenderOption(it.threadId, ConversationMapper.title(it)) }
        .toList()

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val messages: MessageRepository,
    private val mapper: ConversationMapper,
    private val labels: DateLabels,
    private val codeFinder: CodeFinder,
    private val now: () -> Long = System::currentTimeMillis,
    /** Left out of the sender chip; the repository already leaves them out of the results. */
    private val lockedThreads: Flow<Set<Long>> = flowOf(emptySet()),
    /** Where the results are filtered and mapped: off the main thread, since every message is read for links and codes. */
    work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val query = MutableStateFlow("")

    /** The field's text as typed, straight from here: never behind the keyboard. */
    val queryText: StateFlow<String> = query.asStateFlow()
    private val filters = MutableStateFlow(SearchFilters())

    /**
     * The repository's answer, with the text it answers. The text searches; without text, any
     * filter that is on ("has a code" alone, a sender) shows the newest messages it lets through;
     * with neither there is nothing to show.
     */
    private val results = combine(
        query.debounce(DEBOUNCE_MS),
        filters.map { it.threadId to it.any }.distinctUntilChanged(),
    ) { q, f -> q to f }.flatMapLatest { (q, f) ->
        val (threadId, anyFilter) = f
        when {
            q.isNotBlank() -> flow { emit(q to messages.search(q)) }
            anyFilter -> flow { emit(q to SearchResults(emptyList(), messages.browse(threadId, BROWSE_LIMIT))) }
            else -> flowOf(q to SearchResults.Empty)
        }
    }

    val state: StateFlow<SearchUiState> = combine(query, filters, results, messages.conversations, lockedThreads) { q, f, (searched, found), conversations, locked ->
        val byId = conversations.associateBy { it.threadId }
        val t = now()
        val filtered = filterResults(found, f, t, codeFinder)
        SearchUiState(
            query = q,
            filters = f,
            searching = q.isNotBlank() && q != searched,
            conversations = filtered.conversations.map { mapper.map(it, t) },
            messages = filtered.messages.map { m ->
                SearchHit(
                    messageId = m.id,
                    threadId = m.threadId,
                    name = byId[m.threadId]?.let(ConversationMapper::title) ?: ltr(m.address),
                    text = m.body,
                    time = labels.listTime(m.dateMillis, t),
                    avatarArgb = avatarColor(PhoneNumbers.normalize(byId[m.threadId]?.addresses?.firstOrNull() ?: m.address)),
                    outgoing = m.type.outgoing,
                )
            },
            senders = if (q.isBlank() && f.threadId == null) {
                browseSenders(conversations, locked)
            } else {
                senderOptions(found) { id -> byId[id]?.let(ConversationMapper::title) ?: titleOfMessage(found, id) }
            },
        )
    }.flowOn(work).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    private fun titleOfMessage(found: SearchResults, threadId: Long): String? =
        found.messages.firstOrNull { it.threadId == threadId }?.address?.let(::ltr)

    fun setQuery(value: String) {
        query.value = value
    }

    fun setSender(threadId: Long?) = filters.update { it.copy(threadId = threadId) }
    fun toggleHasLink() = filters.update { it.copy(hasLink = !it.hasLink) }
    fun toggleHasCode() = filters.update { it.copy(hasCode = !it.hasCode) }
    fun setRange(value: DateRange) = filters.update { it.copy(range = value) }
    fun clearFilters() {
        filters.value = SearchFilters()
    }
}
