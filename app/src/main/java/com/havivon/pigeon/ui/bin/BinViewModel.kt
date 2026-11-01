package com.havivon.pigeon.ui.bin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.havivon.pigeon.domain.bin.BIN_RETENTION_MILLIS
import com.havivon.pigeon.domain.bin.BinMessage
import com.havivon.pigeon.domain.bin.RecycleBin
import com.havivon.pigeon.domain.repository.ContactsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BinUiState(
    val loaded: Boolean = false,
    /** One row per conversation, the newest deletion first. */
    val groups: List<BinGroup> = emptyList(),
    /** The [BinGroup.key]s opened to show their messages. */
    val expanded: Set<String> = emptySet(),
    /** The [BinGroup.key]s chosen in selection mode (a long press starts it); empty when not selecting. */
    val selected: Set<String> = emptySet(),
) {
    val selecting: Boolean get() = selected.isNotEmpty()
}

/** What the user is doing on the screen: rows opened, rows chosen, locked chats shown after the fingerprint. */
private data class BinUi(val expanded: Set<String> = emptySet(), val selected: Set<String> = emptySet(), val unlocked: Set<String> = emptySet())

/**
 * The recycle bin's screen. Restoring writes to the system database, so it needs Pigeon to be the default SMS
 * app ([canWriteNow]); deleting for good and emptying only touch the bin, Pigeon's own database. Locked chats
 * show nothing of their messages until [unlock] (after the user proved who they are), for this screen only.
 */
class BinViewModel(
    private val bin: RecycleBin,
    private val contacts: ContactsRepository,
    private val appScope: CoroutineScope,
    /** The text a locked chat shows instead of its messages. */
    private val lockedText: String,
    private val time: (millis: Long, nowMillis: Long) -> String,
    private val canWriteNow: () -> Boolean = { true },
    /** The system's conversation for these addresses, created if needed; 0 when it has none to give. */
    private val threadFor: suspend (List<String>) -> Long = { 0L },
    /** Puts one message back into the system database; false when it was refused. */
    private val insert: suspend (BinMessage, Long) -> Boolean = { _, _ -> false },
    private val now: () -> Long = System::currentTimeMillis,
    work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val ui = MutableStateFlow(BinUi())

    /** Contacts' names by address, read once each. */
    private val names = HashMap<String, String?>()

    /** The bin as it is now, without what is past its 30 days (the daily purge may not have run yet). */
    private val current = bin.messages.map { list ->
        val cutoff = now() - BIN_RETENTION_MILLIS
        list.filter { it.deletedAtMillis >= cutoff }.also { latest = it }
    }

    @Volatile
    private var latest: List<BinMessage> = emptyList()

    val state: StateFlow<BinUiState> = combine(current, ui) { list, u ->
        for (address in list.flatMap { it.threadAddresses.ifEmpty { listOf(it.address) } }.distinct()) {
            if (address !in names) names[address] = contacts.displayName(address)
        }
        val t = now()
        val groups = groupBin(list, t, names, u.unlocked, lockedText) { time(it, t) }
        val present = groups.mapTo(HashSet()) { it.key }
        BinUiState(
            loaded = true,
            groups = groups,
            expanded = u.expanded.filterTo(LinkedHashSet()) { it in present },
            selected = u.selected.filterTo(LinkedHashSet()) { it in present },
        )
    }.flowOn(work).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BinUiState())

    /** Opens a row to show its messages, or closes it. A locked chat is unlocked by the screen first ([unlock]). */
    fun toggle(group: BinGroup) = ui.update { if (group.key in it.expanded) it.copy(expanded = it.expanded - group.key) else it.copy(expanded = it.expanded + group.key) }

    /** Only after the user proved who they are: the chat's messages show (and its row opens), on this screen only. */
    fun unlock(group: BinGroup) = ui.update { it.copy(unlocked = it.unlocked + group.key, expanded = it.expanded + group.key) }

    /** A long press: selection mode starts with this row chosen. */
    fun select(group: BinGroup) = ui.update { it.copy(selected = it.selected + group.key) }

    fun toggleSelected(group: BinGroup) =
        ui.update { if (group.key in it.selected) it.copy(selected = it.selected - group.key) else it.copy(selected = it.selected + group.key) }

    fun clearSelection() = ui.update { it.copy(selected = emptySet()) }

    /** Whether restoring is possible now: only as the default SMS app. */
    val canRestore: Boolean get() = canWriteNow()

    private fun messagesOf(keys: Set<String>): List<BinMessage> = latest.filter { RecycleBin.conversationKey(it) in keys }

    /**
     * Puts these rows' messages back into their conversations, in the background (it outlives the screen);
     * [done] gets the number restored. False, and nothing happens, while Pigeon is not the default SMS app.
     */
    fun restore(keys: Set<String>, done: (Int) -> Unit = {}): Boolean {
        if (!canWriteNow()) return false
        val items = messagesOf(keys)
        if (items.isEmpty()) return true
        ui.update { it.copy(selected = it.selected - keys) }
        appScope.launch { done(bin.restore(items, threadFor, insert)) }
        return true
    }

    /** These rows' messages, deleted for good. */
    fun deleteForever(keys: Set<String>) {
        val ids = messagesOf(keys).map { it.id }
        ui.update { it.copy(selected = it.selected - keys) }
        if (ids.isNotEmpty()) appScope.launch { bin.deleteForever(ids) }
    }

    /** Everything in the bin, deleted for good. */
    fun empty() {
        clearSelection()
        appScope.launch { bin.empty() }
    }
}
