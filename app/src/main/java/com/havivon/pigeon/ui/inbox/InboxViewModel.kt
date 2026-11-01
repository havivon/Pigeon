package com.havivon.pigeon.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.PendingDeletion
import com.havivon.pigeon.domain.model.Reminder
import com.havivon.pigeon.domain.repository.LocksRepository
import com.havivon.pigeon.domain.repository.MessageRepository
import com.havivon.pigeon.domain.repository.PinsRepository
import com.havivon.pigeon.domain.repository.RemindersRepository
import com.havivon.pigeon.domain.repository.RulesRepository
import com.havivon.pigeon.domain.repository.SpamRepository
import com.havivon.pigeon.domain.seam.ReminderScheduler
import com.havivon.pigeon.ui.common.ConversationMapper
import com.havivon.pigeon.ui.common.folderCounts
import com.havivon.pigeon.ui.common.inFolder
import com.havivon.pigeon.ui.common.verdictFor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The longest text a reminder keeps of its message. */
private const val REMINDER_TEXT_LENGTH = 200

data class InboxUiState(
    /** False until the conversations are read for the first time. */
    val loaded: Boolean = false,
    /** Every conversation, spam included, newest first. */
    val all: List<Conversation> = emptyList(),
    val folder: Folder = Folder.All,
    val rules: List<ColorRule> = emptyList(),
    val dateLine: String = "",
) {
    /** What the list shows for the chosen folder. */
    val conversations: List<Conversation> get() = inFolder(all, folder)
    val folderCounts: Map<Folder, Int> get() = folderCounts(all)
}

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModel(
    private val messages: MessageRepository,
    rules: RulesRepository,
    private val pins: PinsRepository,
    private val locks: LocksRepository,
    spam: SpamRepository,
    private val reminders: RemindersRepository,
    private val scheduler: ReminderScheduler,
    private val mapper: ConversationMapper,
    /** Re-reads the conversations when access changes (a permission granted, the default app changed). */
    accessChanges: Flow<Int>,
    private val dateLine: () -> String,
    private val cancelNotification: (Long) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    /** Where the conversations are mapped (rules, folders, codes for each one): off the main thread. */
    work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val folder = MutableStateFlow(Folder.All)

    private val mapped: Flow<Pair<List<Conversation>, List<ColorRule>>> = combine(
        accessChanges.flatMapLatest { messages.conversations },
        rules.rules,
        pins.pinnedThreadIds,
        locks.lockedThreadIds,
        spam.verdicts,
    ) { conversations, ruleList, pinned, locked, verdicts ->
        val t = now()
        conversations.map { mapper.map(it, t, ruleList, pinned, locked, verdictFor(it, verdicts)) } to ruleList
    }

    val state: StateFlow<InboxUiState> = combine(mapped, folder) { (all, ruleList), f ->
        InboxUiState(loaded = true, all = all, folder = f, rules = ruleList, dateLine = dateLine())
    }.flowOn(work).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxUiState(dateLine = dateLine()))

    /** Spam opens its own screen, so it is never the chosen chip. */
    fun selectFolder(value: Folder) {
        if (value != Folder.Spam) folder.value = value
    }

    fun togglePin(c: Conversation) {
        viewModelScope.launch { if (c.pinned) pins.unpin(c.id) else pins.pin(c.id) }
    }

    fun markRead(c: Conversation) {
        cancelNotification(c.id)
        viewModelScope.launch { messages.markThreadRead(c.id) }
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

    fun delete(c: Conversation): PendingDeletion = messages.deleteThreads(setOf(c.id))
}
