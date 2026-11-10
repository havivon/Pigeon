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
import com.havivon.pigeon.domain.model.PendingDeletion
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
import java.time.ZoneId

/** How long typing must pause before the search runs. */
private const val DEBOUNCE_MS = 250L

/** The newest messages the filters look through when nothing is typed. */
private const val BROWSE_LIMIT = 500

/** Conversations the sender chip offers before anything is typed. */
private const val MAX_SENDERS = 30

data class SearchFilters(
    /** Only this conversation's messages (the "sender" chip). */
    val threadId: Long? = null,
    val hasLink: Boolean = false,
    val hasCode: Boolean = false,
    /** Only these days, both ends included (the "date" chip); null for any time. */
    val dates: DaySpan? = null,
    /** Only the starred messages (the favourites), kept even when the message itself was deleted. */
    val starred: Boolean = false,
) {
    val any: Boolean get() = threadId != null || hasLink || hasCode || dates != null || starred
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
    /** Its conversation as the inbox has it (for the sheet's category, spam and lock actions); null when the system no longer lists it. */
    val conversation: Conversation? = null,
)

/** What a delete of the selection removed: [messages] messages and [conversations] conversations, all brought back by one undo of [deletion]. */
data class DeletedSelection(val messages: Int, val conversations: Int, val deletion: PendingDeletion) {
    val count: Int get() = messages + conversations
}

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

/** The tag every search result shows: its category's, else "No category" ([noCategory], with no colour of its own: neutral, muted). */
fun resultTag(category: CategoryTag?, noCategory: String): CategoryTag = category ?: CategoryTag(noCategory)

/**
 * Applies the filter chips to what the repository found. Conversations (matched by name or number)
 * only stay while no message-only filter (link, code) is on.
 */
fun filterResults(results: SearchResults, filters: SearchFilters, codeFinder: CodeFinder, zone: ZoneId = ZoneId.systemDefault()): SearchResults {
    val dates = filters.dates
    val messages = results.messages.filter { m ->
        (filters.threadId == null || m.threadId == filters.threadId) &&
            (dates == null || dates.contains(m.dateMillis, zone)) &&
            (!filters.hasLink || LinkAnalyzer.findLinks(m.body).isNotEmpty()) &&
            (!filters.hasCode || codeFinder.find(m.body, m.address, knownPerson = false) != null)
    }
    val conversations = if (filters.hasLink || filters.hasCode || filters.starred) {
        emptyList()
    } else {
        results.conversations.filter { c ->
            (filters.threadId == null || c.threadId == filters.threadId) && (dates == null || dates.contains(c.dateMillis, zone))
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
    /** The time zone the filter's days are read in. */
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    /** What decides whether the browse list is read again: the sender, whether any filter is on, the stars and the days. */
    private data class BrowseKey(val threadId: Long?, val any: Boolean, val starred: Boolean, val dates: DaySpan?)

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
        filters.map { BrowseKey(it.threadId, it.any, it.starred, it.dates) }.distinctUntilChanged(),
        favorites.favorites,
    ) { q, f, favs -> Triple(q, f, favs) }.flatMapLatest { (q, f, favs) ->
        val (threadId, anyFilter, starred, dates) = f
        when {
            starred -> flowOf(q to SearchResults(emptyList(), starredMessages(favs, q)))
            q.isNotBlank() -> flow { emit(q to messages.search(q)) }
            // The days go to the repository, so an old range is not lost behind the newest messages
            anyFilter -> flow {
                emit(q to SearchResults(emptyList(), messages.browse(threadId, BROWSE_LIMIT, dates?.startMillis(zone), dates?.endMillisExclusive(zone))))
            }
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
        val filtered = filterResults(found, f, codeFinder, zone).let { r -> r.copy(messages = r.messages.filter { it.threadId !in locked }) }
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
                    conversation = conversation,
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
    fun setDates(value: DaySpan?) = change { filters.update { it.copy(dates = value) } }
    fun clearFilters() = change { filters.value = SearchFilters() }

    /** The results change with the search, so a selection of the old ones goes. */
    private inline fun change(block: () -> Unit) {
        block()
        clearSelection()
    }

    // Multi-select. The "Select" button in the header starts selection mode (a check circle on every row, a tap toggles a result);
    // "Select" in a result's long-press sheet starts it with that result. Message and conversation results can be mixed.

    private val _selectedConversations = MutableStateFlow<Set<Long>>(emptySet())

    /** The selected conversation results, by conversation id. */
    val selectedConversations: StateFlow<Set<Long>> = _selectedConversations.asStateFlow()

    private val _selecting = MutableStateFlow(false)

    /** Whether selection mode is on, even before anything is selected; ✕ or Back leaves it. */
    val selecting: StateFlow<Boolean> = _selecting.asStateFlow()

    /** The header's "Select" button: selection mode with nothing selected yet. */
    fun startSelecting() {
        _selecting.value = true
    }

    /** Starts selecting with this message result (from its sheet). */
    fun select(messageId: Long) {
        _selecting.value = true
        _selected.value = _selected.value + messageId
    }

    fun toggleSelected(messageId: Long) {
        _selected.value = _selected.value.let { if (messageId in it) it - messageId else it + messageId }
    }

    /** Starts selecting with this conversation result (from its sheet). */
    fun selectConversation(threadId: Long) {
        _selecting.value = true
        _selectedConversations.value = _selectedConversations.value + threadId
    }

    fun toggleSelectedConversation(threadId: Long) {
        _selectedConversations.value = _selectedConversations.value.let { if (threadId in it) it - threadId else it + threadId }
    }

    /** Leaves selection mode and forgets the selection. */
    fun clearSelection() {
        if (_selected.value.isNotEmpty()) _selected.value = emptySet()
        if (_selectedConversations.value.isNotEmpty()) _selectedConversations.value = emptySet()
        _selecting.value = false
    }

    /** "Select all": every result, messages and conversations. */
    fun selectAll() {
        _selecting.value = true
        _selectedConversations.value = state.value.conversations.map { it.id }.toSet()
        _selected.value = state.value.messages.map { it.messageId }.toSet()
    }

    /** The conversations the selection acts on: the selected conversation results and the conversations the selected messages are in (each once). */
    fun selectedConversationList(): List<Conversation> =
        (state.value.conversations.filter { it.id in _selectedConversations.value } + selectedHits().mapNotNull { it.conversation }).distinctBy { it.id }

    /** How many results are selected, of both kinds. */
    fun selectedCount(): Int = selectedHits().size + state.value.conversations.count { it.id in _selectedConversations.value }

    /** "Mark as read" on the selection's conversations; null, and nothing changes, while Pigeon is not the default SMS app. */
    fun markSelectedRead(): Int? {
        val read = sheet.markAllRead(selectedConversationList()) ?: return null
        clearSelection()
        return read
    }

    /** "Mark as spam" on the selection's conversations. */
    fun markSelectedSpam() {
        sheet.markSpamAll(selectedConversationList())
        clearSelection()
    }

    /** "Move to category" for the selection's conversations (null: back to the automatic sorting). */
    fun moveSelected(target: FolderChoice?) {
        sheet.moveAll(selectedConversationList(), target)
        clearSelection()
    }

    /** A new category that the selection's conversations are moved into; false (nothing made, selection kept) when the name cannot be used. */
    suspend fun createFolderAndMoveSelected(name: String, argb: Long?): Boolean {
        val list = selectedConversationList()
        if (!sheet.createFolderAndMoveAll(list, name, argb)) return false
        clearSelection()
        return true
    }

    /**
     * Deletes the selected messages and conversations, with one undo for all. Null, and the selection stays, while Pigeon
     * is not the default SMS app (or when nothing is selected): the screen says so.
     */
    fun deleteSelected(): DeletedSelection? {
        val threadIds = state.value.conversations.filter { it.id in _selectedConversations.value }.map { it.id }.toSet()
        val messageIds = selectedHits().map { it.messageId }.toSet()
        if (threadIds.isEmpty() && messageIds.isEmpty()) return null
        val parts = listOfNotNull(
            if (messageIds.isEmpty()) null else (sheet.deleteMessages(messageIds) ?: return null),
            if (threadIds.isEmpty()) null else (sheet.deleteConversations(threadIds) ?: run { return null }),
        )
        clearSelection()
        return DeletedSelection(messageIds.size, threadIds.size, object : PendingDeletion {
            override fun undo(): Boolean = parts.map { it.undo() }.all { it }
        })
    }

    /** Deletes one message result (from its sheet), with undo; null while Pigeon is not the default SMS app. */
    fun deleteMessage(hit: SearchHit): PendingDeletion? = sheet.deleteMessages(setOf(hit.messageId))

    /** Stars a message result, or removes its star when it has one (from its sheet). */
    fun toggleStar(hit: SearchHit) = starOrUnstar(listOf(hit))

    /** One or more results as text, oldest first: "[time] name: text" per line (as in a conversation). */
    fun textOf(hits: List<SearchHit>): String {
        val names = hits.associate { it.messageId to it.name }
        return formatForCopy(hits.map { it.asMessage() }, labels, me) { names[it.id].orEmpty() }
    }

    /** The selected results still in the list (a result that left it, or a chat locked meanwhile, is not acted on). */
    private fun selectedHits(): List<SearchHit> = state.value.messages.filter { it.messageId in _selected.value }

    /** The selected messages as one text, oldest first, whoever sent them: "[time] name: text" per line (as in a conversation). */
    fun selectedText(): String = textOf(selectedHits())

    private fun SearchHit.asMessage() =
        SmsMessage(messageId, threadId, address, text, dateMillis, 0, if (outgoing) SmsType.Sent else SmsType.Inbox, read = true, subId = null)

    /** Whether every selected message is starred already (the star then removes them). */
    fun allSelectedStarred(): Boolean = selectedHits().let { hits -> hits.isNotEmpty() && hits.all { it.starred } }

    /** Stars every selected message, or unstars them all when they all are starred already. */
    fun starSelected() {
        val chosen = selectedHits()
        clearSelection()
        starOrUnstar(chosen)
    }

    private fun starOrUnstar(chosen: List<SearchHit>) {
        viewModelScope.launch {
            if (chosen.all { it.starred }) {
                chosen.forEach { favorites.unstar(it.messageId) }
            } else {
                chosen.filter { !it.starred }.forEach { favorites.star(it.messageId, it.threadId, it.address, if (it.outgoing) null else it.senderName, it.text, it.dateMillis) }
            }
        }
    }

    /**
     * "Remind me": a reminder at [atMillis] on the one selected message, or on the one selected conversation (only offered for exactly
     * one); true when it was saved. Times that already passed are ignored.
     */
    suspend fun remindSelected(atMillis: Long): Boolean {
        if (selectedCount() != 1) return false
        if (selectedHits().isEmpty()) {
            val conversation = state.value.conversations.first { it.id in _selectedConversations.value }
            clearSelection()
            return sheet.remind(conversation, atMillis)
        }
        val hit = selectedHits().single()
        clearSelection()
        return sheet.remindMessage(hit.threadId, hit.messageId, hit.text, atMillis)
    }
}
