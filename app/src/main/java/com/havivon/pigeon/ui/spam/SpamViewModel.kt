package com.havivon.pigeon.ui.spam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.PendingDeletion
import com.havivon.pigeon.domain.model.SmsConversation
import com.havivon.pigeon.domain.model.SpamVerdict
import com.havivon.pigeon.domain.repository.LocksRepository
import com.havivon.pigeon.domain.repository.MessageRepository
import com.havivon.pigeon.domain.repository.SpamRepository
import com.havivon.pigeon.ui.common.ConversationMapper
import com.havivon.pigeon.ui.common.JUDGE_AFTER_MILLIS
import com.havivon.pigeon.ui.common.SpamJudging
import com.havivon.pigeon.ui.common.verdictFor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SpamUiState(
    val loaded: Boolean = false,
    /** The spam conversations, newest first, each with its stored reasons. */
    val items: List<Conversation> = emptyList(),
    /** The same conversations as one row per sender, the newest sender first. */
    val groups: List<SpamGroup> = emptyList(),
    /** The [SpamGroup.key]s of the rows opened to show their messages. */
    val expanded: Set<String> = emptySet(),
    /** The messages of each opened row, newest first, by [SpamGroup.key]. */
    val messages: Map<String, List<SpamMessage>> = emptyMap(),
    /** The search field is open in the header; [groups] are then the senders that match [query]. */
    val searching: Boolean = false,
    val query: String = "",
    /** The [SpamGroup.key]s chosen in selection mode (a long press starts it); empty when not selecting. */
    val selected: Set<String> = emptySet(),
) {
    val selecting: Boolean get() = selected.isNotEmpty()
}

/** What the user is doing on the screen besides reading: searching, selecting. */
private data class SpamUi(val searching: Boolean = false, val query: String = "", val selected: Set<String> = emptySet())

/** How long the search waits for the typing to pause before it reads the messages. */
private const val SEARCH_PAUSE_MILLIS = 250L

/** The newest messages read for each conversation of an opened row. */
private const val OPENED_MESSAGES = 30

@OptIn(ExperimentalCoroutinesApi::class)
class SpamViewModel(
    private val messages: MessageRepository,
    private val spam: SpamRepository,
    locks: LocksRepository,
    private val mapper: ConversationMapper,
    accessChanges: Flow<Int>,
    private val appScope: CoroutineScope,
    /** Whether Pigeon may change the system database (it is the default SMS app); a viewer only reads. */
    private val canWriteNow: () -> Boolean = { true },
    /** Judges spam on the conversations already on the phone ([com.havivon.pigeon.domain.analysis.SpamOnRead]). */
    private val judgeSpam: suspend (List<SmsConversation>, List<SpamVerdict>) -> Unit = { _, _ -> },
    private val now: () -> Long = System::currentTimeMillis,
    /** Where the conversations are mapped: off the main thread. */
    private val work: CoroutineDispatcher = Dispatchers.Default,
    /** How long after the list is read the spam-on-read starts. */
    judgeDelayMillis: Long = JUDGE_AFTER_MILLIS,
) : ViewModel() {

    private val spamJudging = SpamJudging(viewModelScope, work, judgeSpam, judgeDelayMillis)

    private class Listed(val items: List<Conversation>, val groups: List<SpamGroup>)

    /** Read once for the list and for the opened rows. */
    private val listed: Flow<Listed> = combine(
        accessChanges.flatMapLatest { messages.conversations },
        spam.verdicts,
        locks.lockedThreadIds,
    ) { conversations, verdicts, locked ->
        spamJudging.offer(conversations, verdicts)
        val t = now()
        val items = conversations.mapNotNull { c -> verdictFor(c, verdicts)?.let { mapper.map(c, t, locked = locked, verdict = it) } }
        Listed(items, groupSpam(items)).also { listedNow = it }
    }.flowOn(work).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** The latest list, for the actions on the chosen senders (the whole list, not only the search's). */
    @Volatile
    private var listedNow: Listed? = null

    /** Kept in the view model: an opened row stays open through a rotation and through every new list. */
    private val expanded = MutableStateFlow<Set<String>>(emptySet())

    /** The opened rows, as they are now (a new message, a conversation gone). */
    private val opened: Flow<List<SpamGroup>> =
        combine(listed, expanded) { l, keys -> l.groups.filter { it.key in keys } }.distinctUntilChanged()

    private val openedMessages: Flow<Map<String, List<SpamMessage>>> = opened.flatMapLatest { groups ->
        if (groups.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(groups.map { g -> messagesOf(g).map { g.key to it } }) { pairs -> pairs.toMap() }
        }
    }

    private fun messagesOf(group: SpamGroup): Flow<List<SpamMessage>> {
        val readable = group.conversations.filter { !it.locked }
        val time = { millis: Long -> mapper.time(millis, now()) }
        if (readable.isEmpty()) return flowOf(spamMessages(group, emptyList(), time))
        return combine(readable.map { messages.messages(it.id, OPENED_MESSAGES) }) { pages -> spamMessages(group, pages.flatMap { it }, time) }
            .flowOn(work)
    }

    private val ui = MutableStateFlow(SpamUi())

    /** The spam conversations whose messages hold the query, by the system's message search (never locked chats). */
    private val matchingThreads: Flow<Set<Long>> = ui.map { it.query.trim() }.distinctUntilChanged().mapLatest { q ->
        if (q.isEmpty()) {
            emptySet()
        } else {
            delay(SEARCH_PAUSE_MILLIS)
            messages.search(q).messages.mapTo(HashSet()) { it.threadId }
        }
    }.onStart { emit(emptySet()) }

    val state: StateFlow<SpamUiState> = combine(listed, expanded, openedMessages, ui, matchingThreads) { l, keys, shown, u, matching ->
        val present = l.groups.mapTo(HashSet()) { it.key }
        SpamUiState(
            loaded = true,
            items = l.items,
            groups = if (u.searching) filterSpam(l.groups, u.query, matching) else l.groups,
            expanded = keys.filterTo(LinkedHashSet()) { it in present },
            messages = shown,
            searching = u.searching,
            query = u.query,
            selected = u.selected.filterTo(LinkedHashSet()) { it in present },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpamUiState())

    fun openSearch() = ui.update { it.copy(searching = true) }

    /** Closes the search field: every sender shows again. */
    fun closeSearch() = ui.update { it.copy(searching = false, query = "") }

    fun setQuery(value: String) = ui.update { it.copy(query = value) }

    /** A long press: selection mode starts with this sender chosen. */
    fun select(group: SpamGroup) = ui.update { it.copy(selected = it.selected + group.key) }

    /** A tap while selecting: adds or removes the sender. The last one removed ends selection mode. */
    fun toggleSelected(group: SpamGroup) =
        ui.update { if (group.key in it.selected) it.copy(selected = it.selected - group.key) else it.copy(selected = it.selected + group.key) }

    fun clearSelection() = ui.update { it.copy(selected = emptySet()) }

    private fun selectedGroups(): List<SpamGroup> {
        val keys = ui.value.selected
        return listedNow?.groups.orEmpty().filter { it.key in keys }
    }

    /**
     * The chosen senders' conversations, all at once with one undo: the number deleted and the undo. Null, and
     * nothing is deleted (selection mode stays), while Pigeon is not the default SMS app.
     */
    fun deleteSelected(): Pair<Int, PendingDeletion>? {
        if (!canWriteNow()) return null
        val ids = selectedGroups().flatMapTo(LinkedHashSet()) { it.threadIds }
        if (ids.isEmpty()) return null
        clearSelection()
        return ids.size to messages.deleteThreads(ids)
    }

    /** The chosen senders back to the inbox, as [notSpam] does for one. Returns how many. */
    fun notSpamSelected(): Int {
        val groups = selectedGroups()
        clearSelection()
        expanded.update { it - groups.mapTo(HashSet()) { g -> g.key } }
        appScope.launch { groups.forEach { g -> g.addresses.forEach { spam.markNotSpam(it) } } }
        return groups.size
    }

    /** Opens a row to show its sender's messages, or closes it. */
    fun toggle(group: SpamGroup) {
        expanded.update { if (group.key in it) it - group.key else it + group.key }
    }

    /**
     * Back to the inbox: every conversation with this sender, and the filter leaves the sender alone from now
     * on, under every way its number is written ("+972 52..." and "052..." are one sender to the allow-list).
     */
    fun notSpam(group: SpamGroup) {
        expanded.update { it - group.key }
        appScope.launch { group.addresses.forEach { spam.markNotSpam(it) } }
    }

    /** Deleting needs Pigeon to be the default SMS app; until then the screen says so instead. */
    val canWrite: Boolean get() = canWriteNow()

    /**
     * Every spam conversation of this sender, with one undo: the number deleted and the undo. Null, and
     * nothing is deleted, while Pigeon is not the default SMS app.
     */
    fun delete(group: SpamGroup): Pair<Int, PendingDeletion>? {
        if (!canWriteNow()) return null
        val ids = group.threadIds
        if (ids.isEmpty()) return null
        return ids.size to messages.deleteThreads(ids)
    }

    /** Every spam conversation, with one undo for all. Null when there is nothing to delete, or Pigeon may not delete. */
    fun deleteAll(): Pair<Int, PendingDeletion>? {
        val ids = state.value.items.map { it.id }.toSet()
        if (ids.isEmpty() || !canWriteNow()) return null
        return ids.size to messages.deleteThreads(ids)
    }
}
