package com.havivon.pigeon.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.domain.analysis.RuleMatcher
import com.havivon.pigeon.domain.analysis.LinkAnalyzer
import com.havivon.pigeon.domain.model.Categories
import com.havivon.pigeon.domain.model.CategoryTag
import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.CustomFolder
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.Favorite
import com.havivon.pigeon.domain.model.FolderChoice
import com.havivon.pigeon.domain.model.SearchResults
import com.havivon.pigeon.domain.model.SmsConversation
import com.havivon.pigeon.domain.model.SmsMessage
import com.havivon.pigeon.domain.model.SmsType
import com.havivon.pigeon.domain.model.SpamVerdict
import com.havivon.pigeon.domain.repository.FavoritesRepository
import com.havivon.pigeon.domain.repository.FoldersRepository
import com.havivon.pigeon.domain.repository.MessageRepository
import com.havivon.pigeon.domain.repository.PinsRepository
import com.havivon.pigeon.domain.repository.RulesRepository
import com.havivon.pigeon.domain.repository.SpamRepository
import com.havivon.pigeon.domain.seam.CodeFinder
import com.havivon.pigeon.ui.common.ConversationMapper
import com.havivon.pigeon.ui.common.DateLabels
import com.havivon.pigeon.ui.common.avatarColor
import com.havivon.pigeon.ui.common.formatForCopy
import com.havivon.pigeon.ui.common.overrideFor
import com.havivon.pigeon.ui.common.verdictFor
import com.havivon.pigeon.ui.inbox.ConversationSheetActions
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
import kotlinx.coroutines.launch

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
    /** Only the starred messages (the favourites), kept even when the message itself was deleted. */
    val starred: Boolean = false,
) {
    val any: Boolean get() = threadId != null || hasLink || hasCode || range != DateRange.Any || starred
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
    /** Which category its conversation is in, with that category's colour; null when it is in none. */
    val category: CategoryTag? = null,
    /** The first colour rule that matches the sender or the text (never for spam); it frames the row. */
    val ruleId: Int? = null,
    /** What copying, starring and reminding need: the sender's number, the time as millis, the contact's name (null for a bare number) and whether it is starred. */
    val address: String = "",
    val dateMillis: Long = 0,
    val senderName: String? = null,
    val starred: Boolean = false,
)

/** A sender the "sender" chip can narrow the results to. */
data class SenderOption(val threadId: Long, val name: String)

data class SearchUiState(
    val query: String = "",
    val filters: SearchFilters = SearchFilters(),
    /** True while a search for the current text is still running. */
    val searching: Boolean = false,
    val conversations: List<Conversation> = emptyList(),
    /** The colour rules, for the frame and the tag of the rows ([ruleId]). */
    val rules: List<ColorRule> = emptyList(),
    val messages: List<SearchHit> = emptyList(),
    val senders: List<SenderOption> = emptyList(),
) {
    val senderName: String? get() = senders.firstOrNull { it.threadId == filters.threadId }?.name
    val isEmpty: Boolean get() = conversations.isEmpty() && messages.isEmpty()
}

/** The colour of a result's category tag: the category's own, else that of the colour rule that matches the row, else none (a neutral tag). */
fun tagColor(category: CategoryTag, rule: ColorRule?): Long? = category.argb ?: rule?.argb

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
            (!filters.hasCode || codeFinder.find(m.body, m.address, knownPerson = false) != null)
    }
    val conversations = if (filters.hasLink || filters.hasCode || filters.starred) {
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
 * The starred messages as search results, newest star first: those whose text or sender contains
 * [query] (all of them when nothing is typed). They are the favourites' own copies, so a starred
 * message stays findable after the message itself was deleted.
 */
fun starredMessages(favorites: List<Favorite>, query: String): List<SmsMessage> {
    val q = query.trim()
    return favorites
        .filter { f -> q.isEmpty() || f.body.contains(q, ignoreCase = true) || f.senderName?.contains(q, ignoreCase = true) == true || PhoneNumbers.matchesQuery(f.address, q) }
        .map { f -> SmsMessage(f.messageId, f.threadId, f.address, f.body, f.dateMillis, 0, SmsType.Inbox, read = true, subId = null) }
}

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
    rules: RulesRepository,
    pins: PinsRepository,
    spam: SpamRepository,
    folders: FoldersRepository,
    /** For the "Starred" chip, and to star the selected results. */
    private val favorites: FavoritesRepository,
    /** The long-press sheet of a conversation result: the inbox's own actions ([ConversationSheetActions]). */
    val sheet: ConversationSheetActions,
    /** The sender's name for the user's own messages in what is copied ("Me"). */
    private val me: String = "Me",
    private val now: () -> Long = System::currentTimeMillis,
    /** Left out of the sender chip; the repository already leaves them out of the results. */
    lockedThreads: Flow<Set<Long>> = flowOf(emptySet()),
    /** Where the results are filtered and mapped: off the main thread, since every message is read for links and codes. */
    work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val query = MutableStateFlow("")

    /** The field's text as typed, straight from here: never behind the keyboard. */
    val queryText: StateFlow<String> = query.asStateFlow()
    private val filters = MutableStateFlow(SearchFilters())

    /** The user's categories, for the sheet's "Move to category". */
    val customFolders: StateFlow<List<CustomFolder>> = folders.folders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The message results chosen with a long press, by message id (multi-select, as in a conversation). */
    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = _selected.asStateFlow()

    /**
     * The repository's answer, with the text it answers. The text searches; without text, any
     * filter that is on ("has a code" alone, a sender) shows the newest messages it lets through;
     * with neither there is nothing to show.
     */
    private val results = combine(
        query.debounce(DEBOUNCE_MS),
        filters.map { Triple(it.threadId, it.any, it.starred) }.distinctUntilChanged(),
        favorites.favorites,
    ) { q, f, favs -> Triple(q, f, favs) }.flatMapLatest { (q, f, favs) ->
        val (threadId, anyFilter, starred) = f
        when {
            starred -> flowOf(q to SearchResults(emptyList(), starredMessages(favs, q)))
            q.isNotBlank() -> flow { emit(q to messages.search(q)) }
            anyFilter -> flow { emit(q to SearchResults(emptyList(), messages.browse(threadId, BROWSE_LIMIT))) }
            else -> flowOf(q to SearchResults.Empty)
        }
    }

    /** What sorts a conversation, as the inbox has it: pins, locks, colour rules, spam verdicts, moves by hand, categories and their colours. */
    private class Sorting(
        val rules: List<ColorRule>,
        val pinned: Set<Long>,
        val locked: Set<Long>,
        val verdicts: List<SpamVerdict>,
        val overrides: Map<String, FolderChoice>,
        val categories: Categories,
        val starredIds: Set<Long>,
    )

    private val sorting: Flow<Sorting> = combine(
        combine(rules.rules, pins.pinnedThreadIds, lockedThreads, ::Triple),
        combine(spam.verdicts, folders.overrides, combine(folders.folders, folders.builtInColors, ::Categories), ::Triple),
        favorites.favoriteMessageIds,
    ) { (ruleList, pinned, locked), (verdicts, overrides, categories), starredIds ->
        Sorting(ruleList, pinned, locked, verdicts, overrides, categories, starredIds)
    }

    val state: StateFlow<SearchUiState> = combine(query, filters, results, messages.conversations, sorting) { q, f, (searched, found), conversations, sort ->
        val byId = conversations.associateBy { it.threadId }
        val locked = sort.locked
        val t = now()
        // Locked conversations are never in the results (the repository leaves them out; starred copies are checked here)
        val filtered = filterResults(found, f, t, codeFinder).let { r -> r.copy(messages = r.messages.filter { it.threadId !in locked }) }
        fun mapConversation(c: SmsConversation) =
            mapper.map(c, t, sort.rules, sort.pinned, locked, verdictFor(c, sort.verdicts), overrideFor(c, sort.overrides), sort.categories)
        val mappedByThread = HashMap<Long, Conversation>()
        SearchUiState(
            query = q,
            filters = f,
            searching = q.isNotBlank() && q != searched,
            conversations = filtered.conversations.map { mapConversation(it) },
            rules = sort.rules,
            messages = filtered.messages.map { m ->
                val source = byId[m.threadId]
                // The conversation the message is in, sorted as the inbox sorts it (a move by hand first): its category and spam verdict
                val conversation = source?.let { s -> mappedByThread.getOrPut(m.threadId) { mapConversation(s) } }
                SearchHit(
                    messageId = m.id,
                    threadId = m.threadId,
                    name = source?.let(ConversationMapper::title) ?: ltr(m.address),
                    text = m.body,
                    time = labels.listTime(m.dateMillis, t),
                    avatarArgb = avatarColor(PhoneNumbers.normalize(source?.addresses?.firstOrNull() ?: m.address)),
                    outgoing = m.type.outgoing,
                    category = conversation?.category,
                    // Spam is never framed in a rule's colour (as in the inbox)
                    ruleId = if (conversation?.isSpam == true) null else RuleMatcher.firstMatch(sort.rules, source?.contactName, m.address, m.body)?.id,
                    address = m.address,
                    dateMillis = m.dateMillis,
                    senderName = source?.contactName,
                    starred = m.id in sort.starredIds,
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
        clearSelection()
    }

    fun setSender(threadId: Long?) = change { filters.update { it.copy(threadId = threadId) } }
    fun toggleHasLink() = change { filters.update { it.copy(hasLink = !it.hasLink) } }
    fun toggleHasCode() = change { filters.update { it.copy(hasCode = !it.hasCode) } }
    fun toggleStarred() = change { filters.update { it.copy(starred = !it.starred) } }
    fun setRange(value: DateRange) = change { filters.update { it.copy(range = value) } }
    fun clearFilters() = change { filters.value = SearchFilters() }

    /** The results change with the search, so a selection of the old ones goes. */
    private inline fun change(block: () -> Unit) {
        block()
        clearSelection()
    }

    // Multi-select of message results

    /** A long press on a message result starts the selection with it. */
    fun select(messageId: Long) {
        _selected.value = _selected.value + messageId
    }

    fun toggleSelected(messageId: Long) {
        _selected.value = _selected.value.let { if (messageId in it) it - messageId else it + messageId }
    }

    fun clearSelection() {
        if (_selected.value.isNotEmpty()) _selected.value = emptySet()
    }

    /** The selected results still in the list (a result that left it, or a chat locked meanwhile, is not acted on). */
    private fun selectedHits(): List<SearchHit> = state.value.messages.filter { it.messageId in _selected.value }

    /** The selected messages as one text, oldest first, whoever sent them: "[time] name: text" per line (as in a conversation). */
    fun selectedText(): String {
        val hits = selectedHits()
        val names = hits.associate { it.messageId to it.name }
        return formatForCopy(hits.map { it.asMessage() }, labels, me) { names[it.id].orEmpty() }
    }

    private fun SearchHit.asMessage() =
        SmsMessage(messageId, threadId, address, text, dateMillis, 0, if (outgoing) SmsType.Sent else SmsType.Inbox, read = true, subId = null)

    /** Whether every selected message is starred already (the star then removes them). */
    fun allSelectedStarred(): Boolean = selectedHits().let { hits -> hits.isNotEmpty() && hits.all { it.starred } }

    /** Stars every selected message, or unstars them all when they all are starred already. */
    fun starSelected() {
        val chosen = selectedHits()
        clearSelection()
        viewModelScope.launch {
            if (chosen.all { it.starred }) {
                chosen.forEach { favorites.unstar(it.messageId) }
            } else {
                chosen.filter { !it.starred }.forEach { favorites.star(it.messageId, it.threadId, it.address, if (it.outgoing) null else it.senderName, it.text, it.dateMillis) }
            }
        }
    }

    /**
     * "Remind me about this message": a reminder at [atMillis] on the one selected message (only offered for exactly
     * one); true when it was saved. Times that already passed are ignored.
     */
    suspend fun remindSelected(atMillis: Long): Boolean {
        val hit = selectedHits().singleOrNull() ?: return false
        clearSelection()
        return sheet.remindMessage(hit.threadId, hit.messageId, hit.text, atMillis)
    }
}
