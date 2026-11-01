package com.havivon.pigeon.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.RuleMatch
import com.havivon.pigeon.domain.repository.RulesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What a colour rule looks at. */
enum class RuleKind { Sender, Keyword }

/** The longest sender or word a rule keeps, and the longest tag text. */
const val MAX_RULE_TEXT = 60
const val MAX_RULE_LABEL = 24

/** The rule being added or edited, as the editor sheet shows it. */
data class RuleEditorState(
    /** The rule being edited, or null for a new one. */
    val editingId: Int? = null,
    val kind: RuleKind = RuleKind.Sender,
    /** The sender's name or number, or the word. */
    val text: String = "",
    /** The tag on the conversation; blank follows [text]. */
    val label: String = "",
    val argb: Long = RuleSwatches.first(),
    /** Kept as it was when editing, so saving never switches a rule on or off. */
    val enabled: Boolean = true,
) {
    val canSave: Boolean get() = text.isNotBlank()

    /** The tag the rule will show. */
    val tag: String get() = label.trim().ifEmpty { text.trim() }.take(MAX_RULE_LABEL)
}

/**
 * The colours-and-rules screen: the rules on/off, and adding, editing, deleting and reordering them.
 * The first matching rule wins, so the order is part of what the user sets.
 */
class RulesViewModel(private val repository: RulesRepository) : ViewModel() {

    val rules: StateFlow<List<ColorRule>> = repository.rules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _editor = MutableStateFlow<RuleEditorState?>(null)

    /** The open editor, or null when none is. */
    val editor: StateFlow<RuleEditorState?> = _editor.asStateFlow()

    fun setEnabled(rule: ColorRule, enabled: Boolean) {
        viewModelScope.launch { repository.update(rule.copy(enabled = enabled)) }
    }

    /** Opens the editor for a new rule, with the first colour no rule uses yet. */
    fun startNew() {
        val used = rules.value.map { it.argb }.toSet()
        _editor.value = RuleEditorState(argb = RuleSwatches.firstOrNull { it !in used } ?: RuleSwatches.first())
    }

    fun startEdit(rule: ColorRule) {
        val (kind, text) = when (val m = rule.match) {
            is RuleMatch.Sender -> RuleKind.Sender to m.name
            is RuleMatch.Keyword -> RuleKind.Keyword to m.word
        }
        _editor.value = RuleEditorState(
            editingId = rule.id,
            kind = kind,
            text = text,
            // A tag that is just the word keeps following it
            label = rule.label.takeIf { it != text } ?: "",
            argb = rule.argb,
            enabled = rule.enabled,
        )
    }

    fun setKind(kind: RuleKind) = edit { it.copy(kind = kind) }
    fun setText(text: String) = edit { it.copy(text = text.take(MAX_RULE_TEXT)) }
    fun setLabel(label: String) = edit { it.copy(label = label.take(MAX_RULE_LABEL)) }
    fun setColor(argb: Long) = edit { it.copy(argb = argb) }

    fun dismissEditor() {
        _editor.value = null
    }

    /** Saves the open editor (a new rule goes to the end of the list) and closes it. Nothing happens for a blank word. */
    fun save() {
        val e = _editor.value?.takeIf { it.canSave } ?: return
        _editor.value = null
        val text = e.text.trim()
        val match = if (e.kind == RuleKind.Sender) RuleMatch.Sender(text) else RuleMatch.Keyword(text)
        viewModelScope.launch {
            if (e.editingId == null) {
                repository.add(e.tag, match, e.argb, e.enabled)
            } else {
                repository.update(ColorRule(e.editingId, e.tag, match, e.argb, e.enabled))
            }
        }
    }

    /** Deletes the rule being edited and closes the editor. */
    fun deleteEditing() {
        val id = _editor.value?.editingId ?: return
        _editor.value = null
        viewModelScope.launch { repository.delete(id) }
    }

    /** After a drag: the rules in this order (the top one wins). */
    fun reorder(idsInOrder: List<Int>) {
        viewModelScope.launch { repository.reorder(idsInOrder) }
    }

    private fun edit(change: (RuleEditorState) -> RuleEditorState) = _editor.update { it?.let(change) }
}
