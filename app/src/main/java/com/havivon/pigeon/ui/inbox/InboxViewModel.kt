package com.havivon.pigeon.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.havivon.pigeon.domain.folders.FolderMoves
import com.havivon.pigeon.domain.folders.FolderNameProblem
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

/** The longest text a reminder keeps of its message. */
private const val REMINDER_TEXT_LENGTH = 200

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
}

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModel(
    private val messages: MessageRepository,
    rules: RulesRepository,
    private val pins: PinsRepository,
    private val locks: LocksRepository,
    spam: SpamRepository,
    folders: FoldersRepository,
    /** "Move to category" ([FolderMoves]): the sheet's moves and new categories. */
    private val moves: FolderMoves,
    private val reminders: RemindersRepository,
    private val scheduler: ReminderScheduler,
    private val mapper: ConversationMapper,
    /** Re-reads the conversations when access changes (a permission granted, the default app changed). */
    accessChanges: Flow<Int>,
    private val dateLine: () -> String,
    private val cancelNotification: (Long) -> Unit,
    /** Whether Pigeon may change the system database (it is the default SMS app); a viewer only reads. */
    private val canWrite: () -> Boolean = { true },
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

    // Nothing passed through Pigeon's incoming hook: judge what is read, so spam still finds its folder
    private val spamJudging = SpamJudging(viewModelScope, work, judgeSpam, judgeDelayMillis)

    /** The conversations as the screens draw them, with what the unread rows are mapped from. */
    private class Mapped(val all: List<Conversation>, val sources: List<SmsConversation>, val rules: List<ColorRule>, val locked: Set<Long>)

    private val mapped: Flow<Mapped> = combine(
        accessChanges.flatMapLatest { messages.conversations },
        rules.rules,
        pins.pinnedThreadIds,
        locks.lockedThreadIds,
        combine(spam.verdicts, folders.overrides, ::Pair),
    ) { conversations, ruleList, pinned, locked, (verdicts, overrides) ->
        spamJudging.offer(conversations, verdicts)
        val t = now()
        val all = mapAll(conversations) { mapper.map(it, t, ruleList, pinned, locked, verdictFor(it, verdicts), overrideFor(it, overrides)) }
        Mapped(all, conversations, ruleList, locked)
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
    val state: StateFlow<InboxUiState> = combine(mapped, folder, unreadOnly, folders.folders) { m, chosen, only, custom ->
        // A category deleted while it was chosen: back to All
        val f = chosen.takeIf { c -> c !is FolderChoice.Custom || custom.any { it.id == c.id } } ?: FolderChoice.BuiltIn(Folder.All)
        Selection(m, f, only, custom)
    }
        .transformLatest { (m, f, only, custom) ->
            fun state(unread: List<UnreadMessage>?) = InboxUiState(
                loaded = true,
                all = m.all,
                folder = (f as? FolderChoice.BuiltIn)?.folder ?: Folder.All,
                customFolder = (f as? FolderChoice.Custom)?.id,
                customFolders = custom,
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

    private data class Selection(val m: Mapped, val folder: FolderChoice, val unreadOnly: Boolean, val custom: List<CustomFolder>)

    private suspend fun mapUnread(m: Mapped, folder: FolderChoice): List<UnreadMessage> {
        val sources = m.sources.associateBy { it.threadId }
        val ids = inFolder(m.all, folder).flatMap { sources[it.id]?.unreadIds.orEmpty() }
        val unread = if (ids.isEmpty()) emptyList() else messages.messagesById(ids)
        val builtIn = (folder as? FolderChoice.BuiltIn)?.folder ?: Folder.All
        return mapper.mapUnread(unread, m.all.associateBy { it.id }, sources, now(), m.rules, m.locked, builtIn, (folder as? FolderChoice.Custom)?.id)
    }

    /** Spam opens its own screen, so it is never the chosen chip. */
    fun selectFolder(value: Folder) {
        if (value != Folder.Spam) folder.value = FolderChoice.BuiltIn(value)
    }

    /** The chip of one of the user's categories. */
    fun selectCustomFolder(id: Long) {
        folder.value = FolderChoice.Custom(id)
    }

    /**
     * "Move to category" from the long-press sheet: to a built-in folder, one of the user's categories, Spam
     * ("Mark as spam"), or null for the automatic sorting. The sender's later messages follow it.
     */
    fun moveTo(c: Conversation, target: FolderChoice?) {
        viewModelScope.launch { moves.move(c.id, c.addresses, c.isSpam, target) }
    }

    /** Why [name] cannot be a new category's, or null when it can. */
    fun folderNameProblem(name: String): FolderNameProblem? = moves.problem(name, state.value.customFolders)

    /** "New category…": makes it and moves [c] into it; false (nothing made) when the name cannot be used. */
    suspend fun createFolderAndMove(c: Conversation, name: String): Boolean =
        moves.createAndMove(name, c.id, c.addresses, c.isSpam) != null

    /** The toggle under the chips: every message of the folder (false) or only the unread ones (true). It applies to every folder. */
    fun selectUnreadOnly(value: Boolean) {
        unreadOnly.value = value
    }

    fun togglePin(c: Conversation) {
        viewModelScope.launch { if (c.pinned) pins.unpin(c.id) else pins.pin(c.id) }
    }

    /** False, and nothing changes, while Pigeon is not the default SMS app: the screen says so. */
    fun markRead(c: Conversation): Boolean {
        if (!canWrite()) return false
        cancelNotification(c.id)
        viewModelScope.launch { messages.markThreadRead(c.id) }
        return true
    }

    fun lock(c: Conversation) {
        viewModelScope.launch { locks.lock(c.id) }
    }

    /** Only after the user proved who they are (the screen asks). */
    fun unlock(c: Conversation) {
        viewModelScope.launch { locks.unlock(c.id) }
    }

    /**
     * "Remind me" from the long-press sheet: a reminder on the conversation (not on one message) at
     * [atMillis], showing its last message. A locked conversation keeps no text in the reminder.
     * Times that already passed are ignored; returns whether the reminder was saved.
     */
    suspend fun remind(c: Conversation, atMillis: Long): Boolean {
        if (atMillis <= now()) return false
        val text = if (c.locked) "" else c.snippet.take(REMINDER_TEXT_LENGTH)
        val id = reminders.add(c.id, null, text, atMillis)
        scheduler.schedule(reminders.get(id) ?: Reminder(id, c.id, null, text, atMillis, done = false))
        return true
    }

    /** Null, and nothing is deleted, while Pigeon is not the default SMS app: the screen says so. */
    fun delete(c: Conversation): PendingDeletion? = if (canWrite()) messages.deleteThreads(setOf(c.id)) else null
}
