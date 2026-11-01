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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
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
    /** Whether Pigeon may change the system database (it is the default SMS app); a viewer only reads. */
    private val canWriteNow: () -> Boolean = { true },
    /** Judges spam on the conversations a viewer reads ([com.havivon.pigeon.domain.analysis.SpamOnRead]). */
    private val judgeSpam: suspend (List<SmsConversation>, List<SpamVerdict>) -> Unit = { _, _ -> },
    private val now: () -> Long = System::currentTimeMillis,
    /** Where the conversations are mapped: off the main thread. */
    work: CoroutineDispatcher = Dispatchers.Default,
    /** How long after the list is read the spam-on-read starts. */
    judgeDelayMillis: Long = JUDGE_AFTER_MILLIS,
) : ViewModel() {

    private val spamJudging = SpamJudging(viewModelScope, work, judgeSpam, judgeDelayMillis)

    val state: StateFlow<SpamUiState> = combine(
        accessChanges.flatMapLatest { messages.conversations },
        spam.verdicts,
        locks.lockedThreadIds,
    ) { conversations, verdicts, locked ->
        if (!canWriteNow()) spamJudging.offer(conversations, verdicts)
        val t = now()
        SpamUiState(
            loaded = true,
            items = conversations.mapNotNull { c -> verdictFor(c, verdicts)?.let { mapper.map(c, t, locked = locked, verdict = it) } },
        )
    }.flowOn(work).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpamUiState())

    /** Back to the inbox, and the filter leaves the sender alone from now on. */
    fun notSpam(c: Conversation) {
        appScope.launch { c.addresses.forEach { spam.markNotSpam(it) } }
    }

    /** Deleting needs Pigeon to be the default SMS app; until then the screen says so instead. */
    val canWrite: Boolean get() = canWriteNow()

    /** Null, and nothing is deleted, while Pigeon is not the default SMS app. */
    fun delete(c: Conversation): PendingDeletion? = if (canWriteNow()) messages.deleteThreads(setOf(c.id)) else null

    /** Every spam conversation, with one undo for all. Null when there is nothing to delete, or Pigeon may not delete. */
    fun deleteAll(): Pair<Int, PendingDeletion>? {
        val ids = state.value.items.map { it.id }.toSet()
        if (ids.isEmpty() || !canWriteNow()) return null
        return ids.size to messages.deleteThreads(ids)
    }
}
