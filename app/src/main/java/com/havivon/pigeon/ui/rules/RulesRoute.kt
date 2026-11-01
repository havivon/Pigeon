package com.havivon.pigeon.ui.rules

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.havivon.pigeon.ui.navigation.pigeonViewModel
import com.havivon.pigeon.ui.theme.HebTheme

/** The colours-and-rules screen on real data: add, edit, delete, switch on or off, and reorder rules. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesRoute(onBack: () -> Unit) {
    val vm = pigeonViewModel { app -> RulesViewModel(app.rules) }
    val rules by vm.rules.collectAsStateWithLifecycle()
    val editor by vm.editor.collectAsStateWithLifecycle()

    RulesScreen(
        rules = rules,
        onBack = onBack,
        onToggle = vm::setEnabled,
        onEdit = vm::startEdit,
        onAdd = vm::startNew,
        onReorder = vm::reorder,
    )

    editor?.let { state ->
        ModalBottomSheet(
            onDismissRequest = vm::dismissEditor,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = HebTheme.colors.surface,
        ) {
            RuleEditorSheetContent(
                state = state,
                onKind = vm::setKind,
                onText = vm::setText,
                onLabel = vm::setLabel,
                onColor = vm::setColor,
                onSave = vm::save,
                onDelete = if (state.editingId != null) vm::deleteEditing else null,
            )
        }
    }
}
