package com.havivon.pigeon.ui.folders

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.havivon.pigeon.R
import com.havivon.pigeon.domain.folders.FolderMoves
import com.havivon.pigeon.domain.model.CustomFolder
import com.havivon.pigeon.ui.app.LocalNotices
import com.havivon.pigeon.ui.common.reservedFolderNames
import com.havivon.pigeon.ui.navigation.pigeonViewModel
import com.havivon.pigeon.ui.theme.HebTheme

/** The Categories screen on real data: rename (a dialog), delete (after a confirmation) and reorder. */
@Composable
fun FoldersRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm = pigeonViewModel { app -> FoldersViewModel(app.folders, FolderMoves(app.folders, app.spam, reservedFolderNames(context))) }
    val folders by vm.folders.collectAsStateWithLifecycle()
    val notices = LocalNotices.current
    var renaming by remember { mutableStateOf<CustomFolder?>(null) }
    var deleting by remember { mutableStateOf<CustomFolder?>(null) }

    FoldersScreen(
        folders = folders,
        onBack = onBack,
        onRename = { renaming = it },
        onDelete = { deleting = it },
        onReorder = vm::reorder,
    )

    renaming?.let { folder ->
        FolderNameDialog(
            title = stringResource(R.string.folder_rename_title),
            confirm = stringResource(R.string.folder_save),
            initial = folder.name,
            problemOf = { vm.renameProblem(folder, it) },
            onConfirm = { name ->
                renaming = null
                vm.rename(folder, name)
            },
            onDismiss = { renaming = null },
        )
    }

    val c = HebTheme.colors
    val deleted = stringResource(R.string.folders_deleted)
    deleting?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = c.surface,
            title = { Text(stringResource(R.string.folders_delete_title, folder.name), color = c.onSurface, fontWeight = FontWeight.ExtraBold) },
            text = { Text(stringResource(R.string.folders_delete_body), color = c.onSurfaceMuted) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    vm.delete(folder)
                    notices.show(deleted)
                }) { Text(stringResource(R.string.folders_delete_confirm), color = c.spam, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.common_cancel), color = c.accent) } },
        )
    }
}
