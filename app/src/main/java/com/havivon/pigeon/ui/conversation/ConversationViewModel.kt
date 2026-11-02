package com.havivon.pigeon.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.domain.analysis.RuleMatcher
import com.havivon.pigeon.domain.folders.FolderMoves
import com.havivon.pigeon.domain.folders.FolderNameProblem
import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.CustomFolder
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.FolderChoice
import com.havivon.pigeon.domain.model.Message
import com.havivon.pigeon.domain.model.PendingDeletion
import com.havivon.pigeon.domain.model.Reminder
import com.havivon.pigeon.domain.model.RuleMatch
import com.havivon.pigeon.domain.model.SmsConversation
import com.havivon.pigeon.domain.model.SmsMessage
import com.havivon.pigeon.domain.model.ThreadItem
import com.havivon.pigeon.domain.repository.FavoritesRepository
import com.havivon.pigeon.domain.repository.FoldersRepository
import com.havivon.pigeon.domain.repository.LocksRepository
import com.havivon.pigeon.domain.repository.MessageRepository
import com.havivon.pigeon.domain.repository.PinsRepository
import com.havivon.pigeon.domain.repository.RemindersRepository
import com.havivon.pigeon.domain.repository.RulesRepository
import com.havivon.pigeon.domain.repository.SmsSender
import com.havivon.pigeon.domain.repository.SpamRepository
import com.havivon.pigeon.domain.seam.ReminderScheduler
import com.havivon.pigeon.ui.common.ConversationMapper
import com.havivon.pigeon.ui.common.DateLabels
import com.havivon.pigeon.ui.common.ReminderMatch
import com.havivon.pigeon.ui.common.findReminder
import com.havivon.pigeon.ui.common.formatForCopy
import com.havivon.pigeon.ui.common.overrideFor
import com.havivon.pigeon.ui.common.sendStatus
import com.havivon.pigeon.ui.common.threadItems
import com.havivon.pigeon.ui.common.verdictFor
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The longest text a reminder keeps of its message. */
private const val REMINDER_TEXT_LENGTH = 200

/** The newest messages read when a conversation opens: a screenful and more, however long it is. */
const val FIRST_PAGE = 50

/** How many older messages each scroll to the top adds. */
const val OLDER_PAGE = 100

/**
 * How many of the newest messages to read next so that message [focusId] (a tap in the Unread list)
 * is among them, or null when nothing more is needed: no message to reach, it is in [list] already,
 * or the whole conversation is read ([list] shorter than the [limit] it was asked with) and it is not
 * there (deleted). The page doubles each time, so even a message from months back takes a few reads.
 */
fun limitToReach(focusId: Long?, list: List<SmsMessage>, limit: Int): Int? {
    if (focusId == null || limit == Int.MAX_VALUE || list.size < limit || list.any { it.id == focusId }) return null
    return if (limit > Int.MAX_VALUE / 2) Int.MAX_VALUE else limit * 2
}

data class ConversationUiState(
    /** False until the first page of messages is read: the screen shows its frame and a quiet loading sign meanwhile. */
    val loaded: Boolean = false,
    /** Null until the conversation is known (it may not be in the list yet right after a first message). */
    val conversation: Conversation? = null,
    val items: List<ThreadItem> = emptyList(),
    /** The colour rule that frames the whole conversation (a sender rule). */
    val rule: ColorRule? = null,
    /** Older messages exist that are not read yet (a scroll to the top reads them). */
    val hasOlder: Boolean = false,
    /** The user's categories, for "Move to category". */
    val customFolders: List<CustomFolder> = emptyList(),
) {
    val messages: List<Message> get() = items.filterIsInstance<ThreadItem.Msg>().map { it.message }
}

sealed interface ConversationEvent {
    /** The message could not even be handed to the radio (no permission to send, no SIM). */
    data object SendFailed : ConversationEvent

    /** Deleting or retrying needs Pigeon to be the default SMS app; nothing was changed. */
    data object NeedsDefault : ConversationEvent
    data class ReminderCreated(val label: String) : ConversationEvent
    data object ReminderRemoved : ConversationEvent
    data object MovedToSpam : ConversationEvent
    data object NotSpam : ConversationEvent

    /** Moved by hand ("Move to category"): to [target], or back to the automatic sorting (null). */
    data class Moved(val target: FolderChoice?) : ConversationEvent

    /** "New category…": made, and the conversation moved into it. */
    data class MovedToNew(val name: String) : ConversationEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModel(
    val threadId: Long,
    private val messages: MessageRepository,
    private val sender: SmsSender,
    rules: RulesRepository,
    private val favorites: FavoritesRepository,
    private val reminders: RemindersRepository,
    private val scheduler: ReminderScheduler,
    private val spam: SpamRepository,
    private val locks: LocksRepository,
    private val pins: PinsRepository,
    private val folders: FoldersRepository,
    /** "Move to category" and "Mark as spam" ([FolderMoves]). */
    private val moves: FolderMoves,
    private val mapper: ConversationMapper,
    private val labels: DateLabels,
    private val zone: ZoneId,
    /** "Me", for the copied text. */
    private val me: String,
    private val cancelNotification: (Long) -> Unit,
    /** Sends and deletes outlive the screen: a message handed to the radio must be finished. */
    private val appScope: CoroutineScope,
    /**
     * Whether Pigeon may change the system database (it is the default SMS app). A viewer reads and
     * sends; marking read is then skipped quietly, and deleting or retrying says why it cannot.
     */
    private val canWrite: () -> Boolean = { true },
    private val now: () -> Long = System::currentTimeMillis,
    /**
     * Where the messages are mapped (dates, reminders and rules for each one): off the main thread,
     * since a long conversation is read again on every change.
     */
    work: CoroutineDispatcher = Dispatchers.Default,
    /** The message to scroll to (opened from the Unread list): older pages are read until it is loaded. */
    val focusMessageId: Long? = null,
) : ViewModel() {

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = _selected.asStateFlow()

    private val events = Channel<ConversationEvent>(Channel.BUFFERED)
    val eventFlow: Flow<ConversationEvent> = events.receiveAsFlow()

    /** Whether the screen is showing (resumed): only then do new messages count as read. */
    @Volatile
    private var visible = false

    /** The phrases found in each message, by id: the text of a message never changes. */
    private val reminderCache = HashMap<Long, ReminderMatch?>()

    // Written where the state is built (off the main thread), read by the screen's actions
    @Volatile
    private var latest: List<SmsMessage> = emptyList()

    @Volatile
    private var latestConversation: SmsConversation? = null

    /** How many of the newest messages are read: [FIRST_PAGE], more as the user scrolls up. */
    private val limit = MutableStateFlow(FIRST_PAGE)

    /** "Select all" while older messages are not read yet: they are read, then selected. */
    @Volatile
    private var selectAllWhenRead = false

    private val threadMessages: Flow<Pair<List<SmsMessage>, Int>> = limit.flatMapLatest { n -> messages.messages(threadId, n).map { it to n } }

    /** Null until the conversations are read (the header waits; the messages do not). */
    private val header: Flow<Pair<SmsConversation?, Conversation?>?> = combine(
        messages.conversations,
        pins.pinnedThreadIds,
        locks.lockedThreadIds,
        combine(spam.verdicts, folders.overrides, ::Pair),
        rules.rules,
    ) { list, pinned, locked, (verdicts, overrides), ruleList ->
        val c = list.firstOrNull { it.threadId == threadId }
        c to c?.let { mapper.map(it, now(), ruleList, pinned, locked, verdictFor(it, verdicts), overrideFor(it, overrides)) }
    }.onStart<Pair<SmsConversation?, Conversation?>?> { emit(null) }

    val state: StateFlow<ConversationUiState> = combine(
        threadMessages,
        header,
        rules.rules,
        favorites.favoriteMessageIds,
        combine(reminders.pending, folders.folders, ::Pair),
    ) { (list, n), known, ruleList, starred, (pending, customFolders) ->
        val sms = known?.first
        latest = list
        latestConversation = sms
        val hasOlder = n != Int.MAX_VALUE && list.size >= n
        limitToReach(focusMessageId, list, n)?.let { next -> limit.update { maxOf(it, next) } }
        if (selectAllWhenRead && !hasOlder) {
            selectAllWhenRead = false
            _selected.value = list.map { it.id }.filter { it >= 0 }.toSet()
        }
        if (visible && list.any { !it.type.outgoing && !it.read }) markRead()
        val withReminder = pending.mapNotNull { it.messageId }.toSet()
        val t = now()
        val contactName = sms?.contactName
        // While the conversations are being read the header waits, rather than show the number before the name
        val conversation = if (known == null) null else known.second ?: fallbackConversation(list, t)
        val threadRule = ruleList.firstOrNull { it.id == conversation?.ruleId }?.takeIf { it.match is RuleMatch.Sender }
        val items = threadItems(list, labels, t) { m -> toMessage(m, t, contactName, ruleList, starred, withReminder) }
        ConversationUiState(loaded = true, conversation = conversation, items = items, rule = threadRule, hasOlder = hasOlder, customFolders = customFolders)
    }.flowOn(work).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConversationUiState())

    init {
        markRead()
    }

    private fun markRead() {
        cancelNotification(threadId)
        // A viewer may not write: the default messaging app keeps its own read state
        if (!canWrite()) return
        viewModelScope.launch { messages.markThreadRead(threadId) }
    }

    fun setVisible(value: Boolean) {
        visible = value
        if (value && latest.any { !it.type.outgoing && !it.read }) markRead()
    }

    private fun fallbackConversation(list: List<SmsMessage>, t: Long): Conversation? {
        val first = list.firstOrNull() ?: return null
        val c = SmsConversation(threadId, listOf(first.address), null, list.last().body, list.last().dateMillis, 0, hasFailed = false)
        return mapper.map(c, t)
    }

    private fun toMessage(m: SmsMessage, t: Long, contactName: String?, rules: List<ColorRule>, starred: Set<Long>, withReminder: Set<Long>): Message {
        val reminder = reminderCache.getOrPut(m.id) { findReminder(m.body, m.dateMillis, t, zone) }?.takeIf { it.atMillis > t }
        return Message(
            id = m.id,
            text = m.body,
            time = labels.time(m.dateMillis),
            outgoing = m.type.outgoing,
            reminder = reminder?.let { labels.reminder(it.atMillis, t, it.hasTime) },
            reminderAtMillis = reminder?.atMillis,
            reminderSet = m.id in withReminder,
            status = sendStatus(m.type),
            starred = m.id in starred,
            ruleArgb = if (m.type.outgoing) null else RuleMatcher.firstMatch(rules, contactName, m.address, m.body)?.argb,
        )
    }

    // The composer

    fun setDraft(value: String) {
        _draft.value = value
    }

    fun send() {
        val text = _draft.value.trim()
        val recipients = state.value.conversation?.addresses?.takeIf { it.isNotEmpty() } ?: latest.firstOrNull()?.address?.let(::listOf)
        if (text.isEmpty() || recipients.isNullOrEmpty()) return
        _draft.value = ""
        appScope.launch {
            if (sender.send(recipients, text).isEmpty()) {
                _draft.compareAndSet("", text)
                events.trySend(ConversationEvent.SendFailed)
            }
        }
    }

    /** A failed message goes back to the Outbox and out again: only the default SMS app may do that. */
    fun retry(messageId: Long) {
        if (!canWrite()) {
            events.trySend(ConversationEvent.NeedsDefault)
            return
        }
        appScope.launch { sender.retry(messageId) }
    }

    // Multi-select

    // A message still on its way as a viewer (a negative id, not in the system database yet) cannot be
    // starred, reminded or deleted: it is not selectable until Android has stored it

    fun toggleSelected(id: Long) {
        if (id < 0) return
        _selected.value = _selected.value.let { if (id in it) it - id else it + id }
    }

    fun select(id: Long) {
        if (id < 0) return
        _selected.value = _selected.value + id
    }

    /** Every message of the conversation, also those not read yet: they are read first, then selected. */
    fun selectAll() {
        _selected.value = latest.map { it.id }.filter { it >= 0 }.toSet()
        if (state.value.hasOlder) {
            selectAllWhenRead = true
            limit.value = Int.MAX_VALUE
        }
    }

    /** Reads the next older messages, when there are any (the list was scrolled to its top). */
    fun loadOlder() {
        if (!state.value.hasOlder) return
        limit.update { if (it == Int.MAX_VALUE) it else it + OLDER_PAGE }
    }

    fun clearSelection() {
        _selected.value = emptySet()
    }

    private fun selectedMessages(): List<SmsMessage> = latest.filter { it.id in _selected.value }

    /** The selected messages as one text, oldest first: "[time] name: text" per line. */
    fun selectedText(): String {
        val name = state.value.conversation?.name.orEmpty()
        return formatForCopy(selectedMessages(), labels, me) { m -> latestConversation?.contactName ?: name.ifEmpty { m.address } }
    }

    /** Stars every selected message, or unstars them all when they all are starred already. */
    fun starSelected() {
        val chosen = selectedMessages()
        val starred = state.value.messages.filter { it.starred }.map { it.id }.toSet()
        val name = latestConversation?.contactName
        clearSelection()
        viewModelScope.launch {
            if (chosen.all { it.id in starred }) {
                chosen.forEach { favorites.unstar(it.id) }
            } else {
                chosen.filter { it.id !in starred }.forEach { favorites.star(it.id, it.threadId, it.address, if (it.type.outgoing) null else name, it.body, it.dateMillis) }
            }
        }
    }

    fun allSelectedStarred(): Boolean {
        val ids = _selected.value
        return ids.isNotEmpty() && state.value.messages.filter { it.id in ids }.all { it.starred }
    }

    /** Deletes the selected messages, with undo; returns how many and the pending deletion. */
    fun deleteSelected(): Pair<Int, PendingDeletion>? {
        val ids = _selected.value
        if (ids.isEmpty()) return null
        if (!canWrite()) {
            // The selection stays, so nothing seems to have happened to the messages
            events.trySend(ConversationEvent.NeedsDefault)
            return null
        }
        clearSelection()
        return ids.size to messages.deleteMessages(ids)
    }

    // Reminders

    /** Creates the reminder the message's button offers, or removes it when it was created already. */
    fun toggleReminder(message: Message) {
        val at = message.reminderAtMillis ?: return
        viewModelScope.launch {
            val existing = reminders.pending.first().filter { it.messageId == message.id }
            if (existing.isNotEmpty()) {
                existing.forEach {
                    scheduler.cancel(it.id)
                    reminders.delete(it.id)
                }
                events.trySend(ConversationEvent.ReminderRemoved)
            } else {
                createReminder(message.id, message.text, at)
                events.trySend(ConversationEvent.ReminderCreated(message.reminder.orEmpty()))
            }
        }
    }

    /**
     * "Remind me about this message": a reminder at [atMillis] on the one selected message (the button
     * is only offered for exactly one), which replaces an earlier reminder on it. Times that already
     * passed are ignored.
     */
    fun remindSelected(atMillis: Long) {
        val message = selectedMessages().singleOrNull() ?: return
        if (atMillis <= now()) return
        clearSelection()
        viewModelScope.launch {
            reminders.pending.first().filter { it.messageId == message.id }.forEach {
                scheduler.cancel(it.id)
                reminders.delete(it.id)
            }
            createReminder(message.id, message.body, atMillis)
            events.trySend(ConversationEvent.ReminderCreated(labels.reminder(atMillis, now(), withTime = true)))
        }
    }

    private suspend fun createReminder(messageId: Long, body: String, atMillis: Long) {
        val text = body.take(REMINDER_TEXT_LENGTH)
        val id = reminders.add(threadId, messageId, text, atMillis)
        scheduler.schedule(reminders.get(id) ?: Reminder(id, threadId, messageId, text, atMillis, done = false))
    }

    // The menu

    fun togglePin() {
        val pinned = state.value.conversation?.pinned ?: false
        viewModelScope.launch { if (pinned) pins.unpin(threadId) else pins.pin(threadId) }
    }

    fun lock() {
        viewModelScope.launch { locks.lock(threadId) }
    }

    /** Only after the user proved who they are (the screen asks). */
    fun unlock() {
        viewModelScope.launch { locks.unlock(threadId) }
    }

    private fun addresses(): List<String> =
        state.value.conversation?.addresses?.takeIf { it.isNotEmpty() } ?: latest.map { it.address }.distinctBy { PhoneNumbers.normalize(it) }

    /** "Mark as spam": the same as moving it to Spam (a move by hand to a folder is dropped). */
    fun markSpam() {
        val list = addresses()
        appScope.launch {
            moves.move(threadId, list, isSpam = false, FolderChoice.BuiltIn(Folder.Spam))
            events.trySend(ConversationEvent.MovedToSpam)
        }
    }

    /**
     * "Move to category" from the menu: to a built-in folder, a category of the user's, Spam (as "Mark as spam",
     * which closes the screen), or null for the automatic sorting. A spam conversation moved out is "Not spam".
     */
    fun moveTo(target: FolderChoice?) {
        if (target == FolderChoice.BuiltIn(Folder.Spam)) return markSpam()
        val list = addresses()
        val wasSpam = state.value.conversation?.isSpam == true
        appScope.launch {
            moves.move(threadId, list, wasSpam, target)
            events.trySend(ConversationEvent.Moved(target))
        }
    }

    /** Why [name] cannot be a new category's, or null when it can. */
    fun folderNameProblem(name: String): FolderNameProblem? = moves.problem(name, state.value.customFolders)

    /** "New category…": makes it and moves the conversation into it; nothing happens for a name that cannot be used. */
    fun createFolderAndMove(name: String, argb: Long? = null) {
        val list = addresses()
        val wasSpam = state.value.conversation?.isSpam == true
        appScope.launch {
            if (moves.createAndMove(name, threadId, list, wasSpam, argb) != null) events.trySend(ConversationEvent.MovedToNew(name))
        }
    }

    fun markNotSpam() {
        val list = addresses()
        appScope.launch {
            list.forEach { spam.markNotSpam(it) }
            events.trySend(ConversationEvent.NotSpam)
        }
    }

    /** Null, and nothing is deleted, while Pigeon is not the default SMS app (the screen hears [ConversationEvent.NeedsDefault]). */
    fun deleteConversation(): PendingDeletion? {
        if (!canWrite()) {
            events.trySend(ConversationEvent.NeedsDefault)
            return null
        }
        return messages.deleteThreads(setOf(threadId))
    }
}
