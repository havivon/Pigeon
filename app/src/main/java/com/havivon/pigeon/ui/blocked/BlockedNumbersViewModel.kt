package com.havivon.pigeon.ui.blocked

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.havivon.pigeon.domain.blocking.BlockedOnPhone
import com.havivon.pigeon.domain.blocking.SystemBlockList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The longest number that can be typed. */
const val MAX_BLOCKED_NUMBER_LENGTH = 20

/** A number on the phone's blocked list, with the contact's name when it is a saved one. */
data class BlockedEntry(val number: String, val name: String? = null)

/** What is wrong with the typed number. */
enum class BlockProblem { Invalid, Duplicate, Failed }

data class BlockedNumbersState(
    /** False while Pigeon may not read the phone's list (not the default SMS app): the screen says so instead. */
    val available: Boolean = true,
    val loaded: Boolean = false,
    val entries: List<BlockedEntry> = emptyList(),
    val draft: String = "",
    val problem: BlockProblem? = null,
)

/**
 * Settings → Messages → Blocked numbers: the phone's own blocked list ([SystemBlockList]), the same one Google
 * Messages, Samsung Messages and the Phone app use. Unblocking and adding change it for the whole phone.
 */
class BlockedNumbersViewModel(
    private val system: SystemBlockList,
    /** The contact's name for a number, or null. */
    private val nameOf: suspend (String) -> String?,
) : ViewModel() {

    private val ui = MutableStateFlow(BlockedNumbersState())

    val state: StateFlow<BlockedNumbersState> = combine(
        system.numbers.map { list -> list?.map { BlockedEntry(it, nameOf(it)) } },
        ui,
    ) { entries, u ->
        u.copy(available = entries != null, loaded = true, entries = entries.orEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlockedNumbersState(loaded = false))

    fun setDraft(value: String) {
        val kept = value.filter { it.isDigit() || it in "+-() " }.take(MAX_BLOCKED_NUMBER_LENGTH)
        ui.update { it.copy(draft = kept, problem = null) }
    }

    /** Blocks the typed number on the phone; [done] hears true when it was added. A blank field does nothing. */
    fun add(done: (Boolean) -> Unit = {}) {
        val number = ui.value.draft.trim()
        if (number.isEmpty()) return
        if (!BlockedOnPhone.blockable(number)) {
            ui.update { it.copy(problem = BlockProblem.Invalid) }
            return
        }
        if (BlockedOnPhone.isOn(state.value.entries.map { it.number }, number)) {
            ui.update { it.copy(problem = BlockProblem.Duplicate) }
            return
        }
        viewModelScope.launch {
            val ok = system.block(number)
            ui.update { if (ok) it.copy(draft = "", problem = null) else it.copy(problem = BlockProblem.Failed) }
            done(ok)
        }
    }

    /** Takes [entry] off the phone's list (the screen asks first). */
    fun unblock(entry: BlockedEntry) {
        viewModelScope.launch { system.unblock(entry.number) }
    }
}
