package com.havivon.pigeon.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.havivon.pigeon.domain.model.Reminder
import com.havivon.pigeon.domain.reminders.ReminderLists
import com.havivon.pigeon.domain.repository.ContactsRepository
import com.havivon.pigeon.domain.repository.LocksRepository
import com.havivon.pigeon.domain.repository.MessageRepository
import com.havivon.pigeon.domain.repository.RemindersRepository
import com.havivon.pigeon.domain.seam.ReminderScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** One line of the Reminders screen. */
data class ReminderRow(
    val id: Long,
    val threadId: Long,
    /** The message it points to; the conversation opens at it. Null for a reminder on the whole conversation. */
    val messageId: Long?,
    /** The contact's name or the number; null when the conversation is gone, or [locked] (it then shows nothing of itself). */
    val sender: String?,
    /** The message the reminder is about; null for a locked chat, and for a reminder without text. */
    val text: String?,
    /** "Today 18:00", "Tomorrow 09:00", a weekday or a date, with the time. */
    val whenLabel: String,
    val atMillis: Long,
    val locked: Boolean,
)

data class RemindersUiState(
    val loaded: Boolean = false,
    /** Soonest first. */
    val upcoming: List<ReminderRow> = emptyList(),
    /** Went off or missed in the last 30 days, the latest first. */
    val past: List<ReminderRow> = emptyList(),
)

/**
 * The Reminders screen: the reminders that are still ahead and those of the last 30 days that went off. A locked
 * conversation shows neither its sender nor the text. Changing the time and cancelling go through the same
 * [ReminderScheduler] the reminders are created with, so the alarm always follows the list.
 */
class RemindersViewModel(
    private val reminders: RemindersRepository,
    private val scheduler: ReminderScheduler,
    private val messages: MessageRepository,
    private val contacts: ContactsRepository,
    locks: LocksRepository,
    /** "Today 18:00" and the like, for a moment relative to now. */
    private val whenLabel: (millis: Long, nowMillis: Long) -> String,
    private val now: () -> Long = System::currentTimeMillis,
    work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    /** The sender's name or number by conversation, read once each. */
    private val senders = HashMap<Long, String?>()

    val state: StateFlow<RemindersUiState> = combine(reminders.all, locks.lockedThreadIds) { all, locked ->
        val t = now()
        val lists = ReminderLists.of(all, t)
        suspend fun row(r: Reminder): ReminderRow {
            val isLocked = r.threadId in locked
            return ReminderRow(
                id = r.id,
                threadId = r.threadId,
                messageId = r.messageId,
                sender = if (isLocked) null else senderOf(r.threadId),
                text = if (isLocked) null else r.text.trim().ifEmpty { null },
                whenLabel = whenLabel(r.triggerAtMillis, t),
                atMillis = r.triggerAtMillis,
                locked = isLocked,
            )
        }
        RemindersUiState(loaded = true, upcoming = lists.upcoming.map { row(it) }, past = lists.past.map { row(it) })
    }.flowOn(work).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemindersUiState())

    private suspend fun senderOf(threadId: Long): String? = senders.getOrPut(threadId) {
        val address = messages.recentMessages(threadId, 1).lastOrNull()?.address ?: return@getOrPut null
        contacts.displayName(address) ?: address
    }

    /** Moves the reminder to [atMillis] and sets its alarm again; false (nothing changes) for a time that already passed. */
    suspend fun reschedule(id: Long, atMillis: Long): Boolean {
        if (atMillis <= now()) return false
        reminders.get(id) ?: return false
        reminders.reschedule(id, atMillis)
        reminders.get(id)?.let(scheduler::schedule)
        return true
    }

    /**
     * Cancels the reminder: its alarm and its row go. Returns what was removed, for [restore] (the "Undo"); null when
     * it was gone already.
     */
    suspend fun cancel(id: Long): Reminder? {
        val reminder = reminders.get(id) ?: return null
        scheduler.cancel(id)
        reminders.delete(id)
        return reminder
    }

    /** "Undo" of [cancel]: the reminder again, as it was (a new row, with its alarm). */
    suspend fun restore(reminder: Reminder) {
        val id = reminders.add(reminder.threadId, reminder.messageId, reminder.text, reminder.triggerAtMillis)
        if (reminder.done) reminders.setDone(id, true)
        reminders.get(id)?.takeIf { !it.done }?.let(scheduler::schedule)
    }
}
