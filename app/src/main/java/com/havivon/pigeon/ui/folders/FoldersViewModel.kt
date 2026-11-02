package com.havivon.pigeon.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.havivon.pigeon.domain.folders.FolderMoves
import com.havivon.pigeon.domain.folders.FolderNameProblem
import com.havivon.pigeon.domain.model.Categories
import com.havivon.pigeon.domain.model.CustomFolder
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.FolderChoice
import com.havivon.pigeon.domain.repository.FoldersRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The Categories screen: the user's categories, renamed, deleted and reordered through [moves]. */
class FoldersViewModel(folders: FoldersRepository, private val moves: FolderMoves) : ViewModel() {

    val folders: StateFlow<List<CustomFolder>> = folders.folders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The colours the user gave to built-in folders. */
    val builtInColors: StateFlow<Map<Folder, Long>> = folders.builtInColors.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _picking = MutableStateFlow<FolderChoice?>(null)

    /** The built-in folder or category whose colour picker is open, or null for none. */
    val picking: StateFlow<FolderChoice?> = _picking.asStateFlow()

    /** The colour [choice] has now (null for none). */
    fun colorOf(choice: FolderChoice): Long? = Categories(this.folders.value, builtInColors.value).colorOf(choice)

    fun pickColorFor(choice: FolderChoice) {
        _picking.value = choice
    }

    fun dismissPicker() {
        _picking.value = null
    }

    /** A swatch (or null, "No colour") was chosen in the open picker: applies it and closes the picker. */
    fun chooseColor(argb: Long?) {
        val choice = _picking.value ?: return
        _picking.value = null
        viewModelScope.launch { moves.setColor(choice, argb) }
    }

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
