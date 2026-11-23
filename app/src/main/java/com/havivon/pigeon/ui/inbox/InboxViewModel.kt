package com.havivon.pigeon.ui.inbox

import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.havivon.pigeon.domain.folders.FolderMoves
import com.havivon.pigeon.domain.folders.FolderNameProblem
import com.havivon.pigeon.domain.model.Categories
import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.CustomFolder
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.FolderChoice
import com.havivon.pigeon.domain.model.PendingDeletion
import com.havivon.pigeon.domain.model.Reminder
import com.havivon.pigeon.domain.model.SmsConversation
import com.havivon.pigeon.domain.model.SpamVerdict
import com.havivon.pigeon.domain.model.UnreadMessage
import com.havivon.pigeon.domain.repository.FoldersRepository
import com.havivon.pigeon.domain.repository.LocksRepository
import com.havivon.pigeon.domain.repository.MessageRepository
import com.havivon.pigeon.domain.repository.PinsRepository
import com.havivon.pigeon.domain.repository.RemindersRepository
import com.havivon.pigeon.domain.repository.RulesRepository
import com.havivon.pigeon.domain.repository.SpamRepository
import com.havivon.pigeon.domain.seam.ReminderScheduler
import com.havivon.pigeon.ui.common.ConversationMapper
import com.havivon.pigeon.ui.common.JUDGE_AFTER_MILLIS
import com.havivon.pigeon.ui.common.SpamJudging
import com.havivon.pigeon.ui.common.customFolderCounts
import com.havivon.pigeon.ui.common.folderCounts
import com.havivon.pigeon.ui.common.inFolder
import com.havivon.pigeon.ui.common.mapAll
import com.havivon.pigeon.ui.common.overrideFor
import com.havivon.pigeon.ui.common.verdictFor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

data class InboxUiState(
    /** False until the conversations are read for the first time. */
    val loaded: Boolean = false,
    /** Every conversation, spam included, newest first. */
    val all: List<Conversation> = emptyList(),
    /** The chosen built-in chip; [Folder.All] while one of the user's categories ([customFolder]) is chosen. */
    val folder: Folder = Folder.All,
    /** The chosen chip of the user's own categories, or null for a built-in one ([folder]). */
    val customFolder: Long? = null,
    /** The user's categories, in their order: their chips come after the built-in ones. */
    val customFolders: List<CustomFolder> = emptyList(),
    /** The colours the user gave to built-in folders (their chips show a dot of it). */
    val builtInColors: Map<Folder, Long> = emptyMap(),
    /** The saved order of all the categories (tokens of `CategoryOrder`); empty is the default order. */
    val categoryOrder: List<String> = emptyList(),
    /** The "Unread" side of the toggle under the chips is on: the list shows [unread] instead of [conversations]. */
    val unreadOnly: Boolean = false,
    val rules: List<ColorRule> = emptyList(),
    val dateLine: String = "",
    /**
     * The unread messages of the chosen folder, newest first, one row each, while the toggle is on; empty
     * while it is off, and null while they are not read yet (a quiet loading sign meanwhile).
     */
    val unread: List<UnreadMessage>? = emptyList(),
) {
    /** The chosen chip, built-in or the user's. */
    val choice: FolderChoice get() = customFolder?.let { FolderChoice.Custom(it) } ?: FolderChoice.BuiltIn(folder)

    /** What the list shows for the chosen folder when the toggle is off. */
    val conversations: List<Conversation> get() = inFolder(all, choice)
    val folderCounts: Map<Folder, Int> get() = folderCounts(all)

    /** The numbers on the chips of the user's categories, by id. */
    val customFolderCounts: Map<Long, Int> get() = customFolderCounts(all, customFolders)

    /** The name of the chosen category of the user's, for its empty state; null for a built-in folder. */
    val customFolderName: String? get() = customFolder?.let { id -> customFolders.firstOrNull { it.id == id }?.name }

    /** The number on the toggle's "Unread": the unread messages of the chosen folder (as on its chip). */
    val unreadCount: Int get() = inFolder(all, choice).sumOf { it.unread }

    /** The number on the toggle's "All": every message of the chosen folder, incoming and sent, read and unread. */
    val totalCount: Int get() = inFolder(all, choice).sumOf { it.totalCount }
}

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModel(
    private val messages: MessageRepository,
    rules: RulesRepository,
    pins: PinsRepository,
    locks: LocksRepository,
    spam: SpamRepository,
    folders: FoldersRepository,
    /** "Move to category" ([FolderMoves]): the sheet's moves and new categories. */
    moves: FolderMoves,
    reminders: RemindersRepository,
    scheduler: ReminderScheduler,
    private val mapper: ConversationMapper,
    /** Re-reads the conversations when access changes (a permission granted, the default app changed). */
    accessChanges: Flow<Int>,
    private val dateLine: () -> String,
    cancelNotification: (Long) -> Unit,
    /** Whether Pigeon may change the system database (it is the default SMS app); a viewer only reads. */
    canWrite: () -> Boolean = { true },
    /** Judges spam on the conversations already on the phone ([com.havivon.pigeon.domain.analysis.SpamOnRead]). */
    private val judgeSpam: suspend (List<SmsConversation>, List<SpamVerdict>) -> Unit = { _, _ -> },
    private val now: () -> Long = System::currentTimeMillis,
    /** Where the conversations are mapped (rules, folders, codes for each one): off the main thread. */
    work: CoroutineDispatcher = Dispatchers.Default,
    /** How long after the list is read the spam-on-read starts. */
    judgeDelayMillis: Long = JUDGE_AFTER_MILLIS,
) : ViewModel() {

    // Both outlive a rotation with the view model, and stay as the user switches folders
    private val folder = MutableStateFlow<FolderChoice>(FolderChoice.BuiltIn(Folder.All))
    private val unreadOnly = MutableStateFlow(false)

    /** The long-press sheet's actions: shared with the search results (pin, mark read, remind me, lock, move, delete). */
    val sheet = ConversationSheetActions(messages, pins, locks, moves, reminders, scheduler, viewModelScope, cancelNotification, canWrite, now)

    // Nothing passed through Pigeon's incoming hook: judge what is read, so spam still finds its folder
    private val spamJudging = SpamJudging(viewModelScope, work, judgeSpam, judgeDelayMillis)

    /** The conversations as the screens draw them, with what the unread rows are mapped from. */
    private class Mapped(val all: List<Conversation>, val sources: List<SmsConversation>, val rules: List<ColorRule>, val locked: Set<Long>, val builtInColors: Map<Folder, Long>)

    /** What conversations are sorted by hand or by colour: the verdicts, the moves, the categories and the colours of the built-in folders. */
    private data class Sorting(val verdicts: List<SpamVerdict>, val overrides: Map<String, FolderChoice>, val categories: Categories)

    private val mapped: Flow<Mapped> = combine(
        accessChanges.flatMapLatest { messages.conversations },
        rules.rules,
        pins.pinnedThreadIds,
        locks.lockedThreadIds,
        combine(spam.verdicts, folders.overrides, folders.folders, folders.builtInColors) { v, o, f, b -> Sorting(v, o, Categories(f, b)) },
    ) { conversations, ruleList, pinned, locked, sorting ->
        val (verdicts, overrides, categories) = sorting
        spamJudging.offer(conversations, verdicts)
        val t = now()
        val all = mapAll(conversations) { mapper.map(it, t, ruleList, pinned, locked, verdictFor(it, verdicts), overrideFor(it, overrides), categories) }
        mapper.flush()
        Mapped(all, conversations, ruleList, locked, categories.builtInColors)
    }.conflate()

    /** The unread incoming messages as the system has them; null until the first read. */
    /** The folder and the rows of the Unread list last shown, or null while it is off. */
    @Volatile
    private var shown: Pair<FolderChoice, List<UnreadMessage>>? = null

    /**
     * The inbox as shown. The Unread list is read from the very conversations the count is made of (their
     * [SmsConversation.unreadIds]), never from a query of its own: a folder's number and its list always agree.
     * It is read only while shown, and while it is read again after a change the previous list stays.
     */
    val state: StateFlow<InboxUiState> = combine(mapped, folder, unreadOnly, folders.folders, folders.categoryOrder) { m, chosen, only, custom, order ->
        // A category deleted while it was chosen: back to All
        val f = chosen.takeIf { c -> c !is FolderChoice.Custom || custom.any { it.id == c.id } } ?: FolderChoice.BuiltIn(Folder.All)
        Selection(m, f, only, custom, order)
    }
        .transformLatest { (m, f, only, custom, order) ->
            fun state(unread: List<UnreadMessage>?) = InboxUiState(
                loaded = true,
                all = m.all,
                folder = (f as? FolderChoice.BuiltIn)?.folder ?: Folder.All,
                customFolder = (f as? FolderChoice.Custom)?.id,
                customFolders = custom,
                builtInColors = m.builtInColors,
                categoryOrder = order,
                unreadOnly = only,
                rules = m.rules,
                dateLine = dateLine(),
                unread = unread,
            )
            if (!only) {
                shown = null
                emit(state(emptyList()))
                return@transformLatest
            }
            // A quiet loading sign only when the list itself changes (turned on, another folder), not on every change
            val same = shown?.takeIf { it.first == f }?.second
            if (same == null) emit(state(null))
            val rows = mapUnread(m, f)
            shown = f to rows
            emit(state(rows))
        }
        .flowOn(work).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxUiState(dateLine = dateLine()))

    private data class Selection(val m: Mapped, val folder: FolderChoice, val unreadOnly: Boolean, val custom: List<CustomFolder>, val order: List<String>)

    private suspend fun mapUnread(m: Mapped, folder: FolderChoice): List<UnreadMessage> {
        val sources = m.sources.associateBy { it.threadId }
        val ids = inFolder(m.all, folder).flatMap { sources[it.id]?.unreadIds.orEmpty() }
        val unread = if (ids.isEmpty()) emptyList() else messages.messagesById(ids)
        val builtIn = (folder as? FolderChoice.BuiltIn)?.folder ?: Folder.All
        return mapper.mapUnread(unread, m.all.associateBy { it.id }, sources, now(), m.rules, m.locked, builtIn, (folder as? FolderChoice.Custom)?.id)
    }

    /** Spam opens its own screen, so it is never the chosen chip. */
    fun selectFolder(value: Folder) {
        if (value != Folder.Spam) {
            folder.value = FolderChoice.BuiltIn(value)
            clearSelection()
        }
    }

    /** The chip of one of the user's categories. */
    fun selectCustomFolder(id: Long) {
        folder.value = FolderChoice.Custom(id)
        clearSelection()
    }

    // Multi-select, as in the search's results: the "Select" button in the header starts it (nothing selected yet), and so does
    // "Select" in a conversation's long-press sheet (with that conversation); a tap adds or removes a conversation, and the bar at
    // the bottom acts on all of them. It belongs to the chosen category: another category, or the Unread list, leaves it, and so
    // do the close button and Back.

    private val _selecting = MutableStateFlow(false)

    /** Whether selection mode is on, even before anything is selected. */
    val selecting: StateFlow<Boolean> = _selecting.asStateFlow()

    /** The header's "Select" button: selection mode with nothing selected yet. */
    fun startSelecting() {
        _selecting.value = true
    }

    private val _selected = MutableStateFlow<Set<Long>>(emptySet())

    /** The selected conversations, by id (some may have left the list since: [selectedConversations] is what is acted on). */
    val selected: StateFlow<Set<Long>> = _selected.asStateFlow()

    /** Starts selecting with this conversation (from its sheet). */
    fun selectConversation(id: Long) {
        _selecting.value = true
        _selected.value = _selected.value + id
    }

    fun toggleSelected(id: Long) {
        _selected.value = _selected.value.let { if (id in it) it - id else it + id }
    }

    /** Leaves selection mode and forgets the selection. */
    fun clearSelection() {
        if (_selected.value.isNotEmpty()) _selected.value = emptySet()
        _selecting.value = false
    }

    /** "Select all": every conversation of the chosen category, the pinned ones too. */
    fun selectAll() {
        _selecting.value = true
        _selected.value = state.value.conversations.mapTo(LinkedHashSet()) { it.id }
    }

    /** The selected conversations that are still in the chosen category's list. */
    fun selectedConversations(): List<Conversation> = state.value.conversations.filter { it.id in _selected.value }

    /** "Mark as read" on the selection; null, and nothing changes (the selection stays), while Pigeon is not the default SMS app. */
    fun markSelectedRead(): Int? {
        val read = sheet.markAllRead(selectedConversations()) ?: return null
        clearSelection()
        return read
    }

    /** "Mark as unread" on the selected conversations that are read; false, and nothing changes, while Pigeon is not the default SMS app. */
    fun markSelectedUnread(onDone: (Int) -> Unit = {}): Boolean {
        val read = selectedConversations().filter { it.unread == 0 }.map { it.id }.toSet()
        if (!sheet.markUnread(read, onDone = onDone)) return false
        clearSelection()
        return true
    }

    /** "Mark as spam" on the selection (the senders' later messages follow it). */
    fun markSelectedSpam() {
        sheet.markSpamAll(selectedConversations())
        clearSelection()
    }

    /** "Move to category" for the selection (null: back to the automatic sorting). */
    fun moveSelected(target: FolderChoice?) {
        sheet.moveAll(selectedConversations(), target)
        clearSelection()
    }

    /** A new category the selection is moved into; false (nothing made, the selection kept) when the name cannot be used. */
    suspend fun createFolderAndMoveSelected(name: String, argb: Long?): Boolean {
        if (!sheet.createFolderAndMoveAll(selectedConversations(), name, argb)) return false
        clearSelection()
        return true
    }

    /**
     * Deletes the selected conversations, with one undo for all: how many and the undo. Null, and the selection stays, while
     * Pigeon is not the default SMS app (or when nothing is selected).
     */
    fun deleteSelected(): Pair<Int, PendingDeletion>? {
        val ids = selectedConversations().mapTo(LinkedHashSet()) { it.id }
        if (ids.isEmpty()) return null
        val deletion = sheet.deleteConversations(ids) ?: return null
        clearSelection()
        return ids.size to deletion
    }

    /** "Remind me" on the one selected conversation (only offered for exactly one); true when it was saved. */
    suspend fun remindSelected(atMillis: Long): Boolean {
        val one = selectedConversations().singleOrNull() ?: return false
        clearSelection()
        return sheet.remind(one, atMillis)
    }

    // The long-press sheet's actions are [sheet]'s; these keep the names the inbox's screens and tests use

    fun moveTo(c: Conversation, target: FolderChoice?) = sheet.moveTo(c, target)

    /** Why [name] cannot be a new category's, or null when it can. */
    fun folderNameProblem(name: String): FolderNameProblem? = sheet.folderNameProblem(name, state.value.customFolders)

    /** "New category…": makes it (coloured [argb], if given) and moves [c] into it; false (nothing made) when the name cannot be used. */
    suspend fun createFolderAndMove(c: Conversation, name: String, argb: Long? = null): Boolean = sheet.createFolderAndMove(c, name, argb)

    /** The toggle under the chips: every message of the folder (false) or only the unread ones (true). It applies to every folder. */
    fun selectUnreadOnly(value: Boolean) {
        unreadOnly.value = value
        if (value) clearSelection()
    }

    fun togglePin(c: Conversation) = sheet.togglePin(c)

    /** False, and nothing changes, while Pigeon is not the default SMS app: the screen says so. */
    fun markRead(c: Conversation): Boolean = sheet.markRead(c)

    /**
     * "Mark all as read" in the chosen folder (a built-in one or one of the user's categories; spam never): returns
     * how many messages were marked, or null, and nothing changes, while Pigeon is not the default SMS app.
     */
    fun markAllRead(): Int? = sheet.markAllRead(state.value.conversations)

    fun lock(c: Conversation) = sheet.lock(c)

    /** Only after the user proved who they are (the screen asks). */
    fun unlock(c: Conversation) = sheet.unlock(c)

    suspend fun remind(c: Conversation, atMillis: Long): Boolean = sheet.remind(c, atMillis)

    /** Null, and nothing is deleted, while Pigeon is not the default SMS app: the screen says so. */
    fun delete(c: Conversation): PendingDeletion? = sheet.delete(c)
}
