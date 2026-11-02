package com.havivon.pigeon.ui.inbox

import com.havivon.pigeon.domain.folders.FolderMoves
import com.havivon.pigeon.domain.folders.FolderNameProblem
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.CustomFolder
import com.havivon.pigeon.domain.model.FolderChoice
import com.havivon.pigeon.domain.model.PendingDeletion
import com.havivon.pigeon.domain.model.Reminder
import com.havivon.pigeon.domain.repository.LocksRepository
import com.havivon.pigeon.domain.repository.MessageRepository
import com.havivon.pigeon.domain.repository.PinsRepository
import com.havivon.pigeon.domain.repository.RemindersRepository
import com.havivon.pigeon.domain.seam.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The longest text a reminder keeps of its message. */
private const val REMINDER_TEXT_LENGTH = 200

/**
 * What the long-press sheet of a conversation does (pin, mark read, remind me, lock, move to category, delete).
 * One class for every screen that offers the sheet: the inbox and the search results act through the same code,
 * with the same "default app only" rule for the actions that change Android's SMS database.
 */
class ConversationSheetActions(
    private val messages: MessageRepository,
    private val pins: PinsRepository,
    private val locks: LocksRepository,
    /** "Move to category" ([FolderMoves]): the sheet's moves and new categories. */
    private val moves: FolderMoves,
    private val reminders: RemindersRepository,
    private val scheduler: ReminderScheduler,
    private val scope: CoroutineScope,
    private val cancelNotification: (Long) -> Unit,
    /** Whether Pigeon may change the system database (it is the default SMS app); a viewer only reads. */
    private val canWrite: () -> Boolean = { true },
    private val now: () -> Long = System::currentTimeMillis,
) {

    fun togglePin(c: Conversation) {
        scope.launch { if (c.pinned) pins.unpin(c.id) else pins.pin(c.id) }
    }

    /** False, and nothing changes, while Pigeon is not the default SMS app: the screen says so. */
    fun markRead(c: Conversation): Boolean {
        if (!canWrite()) return false
        cancelNotification(c.id)
        scope.launch { messages.markThreadRead(c.id) }
        return true
    }

    fun lock(c: Conversation) {
        scope.launch { locks.lock(c.id) }
    }

    /** Only after the user proved who they are (the screen asks). */
    fun unlock(c: Conversation) {
        scope.launch { locks.unlock(c.id) }
    }

    /**
     * "Remind me" from the long-press sheet: a reminder on the conversation (not on one message) at
     * [atMillis], showing its last message. A locked conversation keeps no text in the reminder.
     * Times that already passed are ignored; returns whether the reminder was saved.
     */
    suspend fun remind(c: Conversation, atMillis: Long): Boolean {
        if (atMillis <= now()) return false
        val text = if (c.locked) "" else c.snippet.take(REMINDER_TEXT_LENGTH)
        add(c.id, null, text, atMillis)
        return true
    }

    /**
     * "Remind me about this message": a reminder at [atMillis] on one message, which replaces an earlier reminder on
     * it. Times that already passed are ignored; returns whether the reminder was saved.
     */
    suspend fun remindMessage(threadId: Long, messageId: Long, body: String, atMillis: Long): Boolean {
        if (atMillis <= now()) return false
        reminders.pending.first().filter { it.messageId == messageId }.forEach {
            scheduler.cancel(it.id)
            reminders.delete(it.id)
        }
        add(threadId, messageId, body.take(REMINDER_TEXT_LENGTH), atMillis)
        return true
    }

    private suspend fun add(threadId: Long, messageId: Long?, text: String, atMillis: Long) {
        val id = reminders.add(threadId, messageId, text, atMillis)
        scheduler.schedule(reminders.get(id) ?: Reminder(id, threadId, messageId, text, atMillis, done = false))
    }

    /** Null, and nothing is deleted, while Pigeon is not the default SMS app: the screen says so. */
    fun delete(c: Conversation): PendingDeletion? = if (canWrite()) messages.deleteThreads(setOf(c.id)) else null

    /**
     * "Move to category": to a built-in folder, one of the user's categories, Spam ("Mark as spam"), or null for
     * the automatic sorting. The sender's later messages follow it.
     */
    fun moveTo(c: Conversation, target: FolderChoice?) {
        scope.launch { moves.move(c.id, c.addresses, c.isSpam, target) }
    }

    /** Why [name] cannot be a new category's, or null when it can. */
    fun folderNameProblem(name: String, existing: List<CustomFolder>): FolderNameProblem? = moves.problem(name, existing)

    /** "New category…": makes it (coloured [argb], if given) and moves [c] into it; false (nothing made) when the name cannot be used. */
    suspend fun createFolderAndMove(c: Conversation, name: String, argb: Long? = null): Boolean =
        moves.createAndMove(name, c.id, c.addresses, c.isSpam, argb) != null
}
