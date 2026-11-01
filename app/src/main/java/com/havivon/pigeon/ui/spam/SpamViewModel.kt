package com.havivon.pigeon.ui.spam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.PendingDeletion
import com.havivon.pigeon.domain.repository.LocksRepository
import com.havivon.pigeon.domain.repository.MessageRepository
import com.havivon.pigeon.domain.repository.SpamRepository
import com.havivon.pigeon.ui.common.ConversationMapper
import com.havivon.pigeon.ui.common.verdictFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SpamUiState(
    val loaded: Boolean = false,
    /** The spam conversations, newest first, each with its stored reasons. */
    val items: List<Conversation> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class SpamViewModel(
    private val messages: MessageRepository,
    private val spam: SpamRepository,
    locks: LocksRepository,
    private val mapper: ConversationMapper,
    accessChanges: Flow<Int>,
    private val appScope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    val state: StateFlow<SpamUiState> = combine(
        accessChanges.flatMapLatest { messages.conversations },
        spam.verdicts,
        locks.lockedThreadIds,
    ) { conversations, verdicts, locked ->
        val t = now()
        SpamUiState(
            loaded = true,
            items = conversations.mapNotNull { c -> verdictFor(c, verdicts)?.let { mapper.map(c, t, locked = locked, verdict = it) } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpamUiState())

    /** Back to the inbox, and the filter leaves the sender alone from now on. */
    fun notSpam(c: Conversation) {
        appScope.launch { c.addresses.forEach { spam.markNotSpam(it) } }
    }

    fun delete(c: Conversation): PendingDeletion = messages.deleteThreads(setOf(c.id))

    /** Every spam conversation, with one undo for all. Null when there is nothing to delete. */
    fun deleteAll(): Pair<Int, PendingDeletion>? {
        val ids = state.value.items.map { it.id }.toSet()
        if (ids.isEmpty()) return null
        return ids.size to messages.deleteThreads(ids)
    }
}
