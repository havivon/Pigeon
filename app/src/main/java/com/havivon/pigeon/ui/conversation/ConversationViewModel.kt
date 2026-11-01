package com.havivon.pigeon.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.domain.analysis.RuleMatcher
import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.Message
import com.havivon.pigeon.domain.model.PendingDeletion
import com.havivon.pigeon.domain.model.Reminder
import com.havivon.pigeon.domain.model.RuleMatch
import com.havivon.pigeon.domain.model.SmsConversation
import com.havivon.pigeon.domain.model.SmsMessage
import com.havivon.pigeon.domain.model.SpamReason
import com.havivon.pigeon.domain.model.ThreadItem
import com.havivon.pigeon.domain.repository.FavoritesRepository
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
import com.havivon.pigeon.ui.common.sendStatus
import com.havivon.pigeon.ui.common.threadItems
import com.havivon.pigeon.ui.common.verdictFor
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The longest text a reminder keeps of its message. */
private const val REMINDER_TEXT_LENGTH = 200

data class ConversationUiState(
    val loaded: Boolean = false,
    /** Null until the conversation is known (it may not be in the list yet right after a first message). */
    val conversation: Conversation? = null,
    val items: List<ThreadItem> = emptyList(),
    /** The colour rule that frames the whole conversation (a sender rule). */
    val rule: ColorRule? = null,
) {
    val messages: List<Message> get() = items.filterIsInstance<ThreadItem.Msg>().map { it.message }
}

sealed interface ConversationEvent {
    /** The message could not even be handed to the radio (Pigeon is not the default SMS app). */
    data object SendFailed : ConversationEvent
    data class ReminderCreated(val label: String) : ConversationEvent
    data object ReminderRemoved : ConversationEvent
    data object MovedToSpam : ConversationEvent
    data object NotSpam : ConversationEvent
}

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
    private val mapper: ConversationMapper,
    private val labels: DateLabels,
    private val zone: ZoneId,
    /** "Me", for the copied text. */
    private val me: String,
    private val cancelNotification: (Long) -> Unit,
    /** Sends and deletes outlive the screen: a message handed to the radio must be finished. */
    private val appScope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
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

    private var latest: List<SmsMessage> = emptyList()
    private var latestConversation: SmsConversation? = null

    private val threadMessages = messages.messages(threadId)

    private val header: Flow<Pair<SmsConversation?, Conversation?>> = combine(
        messages.conversations,
        pins.pinnedThreadIds,
        locks.lockedThreadIds,
        spam.verdicts,
        rules.rules,
    ) { list, pinned, locked, verdicts, ruleList ->
        val c = list.firstOrNull { it.threadId == threadId }
        c to c?.let { mapper.map(it, now(), ruleList, pinned, locked, verdictFor(it, verdicts)) }
    }

    val state: StateFlow<ConversationUiState> = combine(
        threadMessages,
        header,
        rules.rules,
        favorites.favoriteMessageIds,
        reminders.pending,
    ) { list, (sms, conv), ruleList, starred, pending ->
        latest = list
        latestConversation = sms
        if (visible && list.any { !it.type.outgoing && !it.read }) markRead()
        val withReminder = pending.mapNotNull { it.messageId }.toSet()
        val t = now()
        val contactName = sms?.contactName
        val conversation = conv ?: fallbackConversation(list, t)
        val threadRule = ruleList.firstOrNull { it.id == conversation?.ruleId }?.takeIf { it.match is RuleMatch.Sender }
        val items = threadItems(list, labels, t) { m -> toMessage(m, t, contactName, ruleList, starred, withReminder) }
        ConversationUiState(loaded = true, conversation = conversation, items = items, rule = threadRule)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConversationUiState())

    init {
        markRead()
    }

    private fun markRead() {
        cancelNotification(threadId)
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

    fun retry(messageId: Long) {
        appScope.launch { sender.retry(messageId) }
    }

    // Multi-select

    fun toggleSelected(id: Long) {
        _selected.value = _selected.value.let { if (id in it) it - id else it + id }
    }

    fun select(id: Long) {
        _selected.value = _selected.value + id
    }

    fun selectAll() {
        _selected.value = latest.map { it.id }.toSet()
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
                val id = reminders.add(threadId, message.id, message.text.take(REMINDER_TEXT_LENGTH), at)
                val reminder = reminders.get(id) ?: Reminder(id, threadId, message.id, message.text.take(REMINDER_TEXT_LENGTH), at, done = false)
                scheduler.schedule(reminder)
                events.trySend(ConversationEvent.ReminderCreated(message.reminder.orEmpty()))
            }
        }
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

    fun markSpam() {
        val list = addresses()
        appScope.launch {
            for (a in list) {
                // The user's own mark overrides an earlier "not spam"
                spam.removeFromNotSpam(a)
                spam.markSpam(a, threadId, listOf(SpamReason.MarkedByUser))
            }
            events.trySend(ConversationEvent.MovedToSpam)
        }
    }

    fun markNotSpam() {
        val list = addresses()
        appScope.launch {
            list.forEach { spam.markNotSpam(it) }
            events.trySend(ConversationEvent.NotSpam)
        }
    }

    fun deleteConversation(): PendingDeletion = messages.deleteThreads(setOf(threadId))
}
