package com.havivon.pigeon.ui.keywords

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.havivon.pigeon.domain.model.BlockedKeyword
import com.havivon.pigeon.domain.repository.BlockedKeywordsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The longest blocked word. */
const val MAX_KEYWORD_LENGTH = 40

/**
 * The blocked words: any message containing one goes straight to Spam, without a notification (the
 * spam filter reads this list for every incoming message).
 */
class BlockedKeywordsViewModel(private val repository: BlockedKeywordsRepository) : ViewModel() {

    val keywords: StateFlow<List<BlockedKeyword>> =
        repository.keywords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _draft = MutableStateFlow("")

    /** The word being typed. */
    val draft: StateFlow<String> = _draft.asStateFlow()

    private val _duplicate = MutableStateFlow(false)

    /** True after trying to add a word that is on the list already (case does not matter). */
    val duplicate: StateFlow<Boolean> = _duplicate.asStateFlow()

    fun setDraft(value: String) {
        _draft.value = value.take(MAX_KEYWORD_LENGTH)
        _duplicate.value = false
    }

    /** Adds the typed word. A blank one is ignored; a word already on the list shows [duplicate] and stays in the field. */
    fun add() {
        val word = _draft.value.trim()
        if (word.isEmpty()) return
        viewModelScope.launch {
            if (repository.add(word)) {
                _draft.value = ""
                _duplicate.value = false
            } else {
                _duplicate.value = true
            }
        }
    }

    fun delete(keyword: BlockedKeyword) {
        viewModelScope.launch { repository.delete(keyword.id) }
    }
}
