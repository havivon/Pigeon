package com.havivon.pigeon.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.havivon.pigeon.domain.folders.FolderMoves
import com.havivon.pigeon.domain.folders.FolderNameProblem
import com.havivon.pigeon.domain.model.CustomFolder
import com.havivon.pigeon.domain.repository.FoldersRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The Categories screen: the user's categories, renamed, deleted and reordered through [moves]. */
class FoldersViewModel(folders: FoldersRepository, private val moves: FolderMoves) : ViewModel() {

    val folders: StateFlow<List<CustomFolder>> = folders.folders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Why [name] cannot be the new name of [folder], or null when it can (its own name is free). */
    fun renameProblem(folder: CustomFolder, name: String): FolderNameProblem? = moves.problem(name, folders.value, renaming = folder.id)

    fun rename(folder: CustomFolder, name: String) {
        viewModelScope.launch { moves.rename(folder.id, name) }
    }

    /** Its conversations go back to the automatic sorting; no message is touched. */
    fun delete(folder: CustomFolder) {
        viewModelScope.launch { moves.delete(folder.id) }
    }

    /** After a drag: the categories in this order (the chips follow it). */
    fun reorder(idsInOrder: List<Long>) {
        viewModelScope.launch { moves.reorder(idsInOrder) }
    }
}
