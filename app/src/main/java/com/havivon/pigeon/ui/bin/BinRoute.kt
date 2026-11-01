package com.havivon.pigeon.ui.bin

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.havivon.pigeon.R
import com.havivon.pigeon.ui.app.LocalAuthenticator
import com.havivon.pigeon.ui.app.LocalNotices
import com.havivon.pigeon.ui.app.rememberNeedsDefaultNotice
import com.havivon.pigeon.ui.common.dateLabels
import com.havivon.pigeon.ui.navigation.pigeonViewModel
import com.havivon.pigeon.ui.theme.HebTheme

/** The recycle bin on real data, opened from the settings. */
@Composable
fun BinRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm = pigeonViewModel { app ->
        val labels = dateLabels(context)
        BinViewModel(
            app.recycleBin, app.contacts, app.appScope,
            lockedText = context.getString(R.string.inbox_locked_snippet),
            time = labels::listTime,
            canWriteNow = app::canWrite,
            threadFor = app.binRestorer::threadFor,
            insert = app.binRestorer::insert,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val notices = LocalNotices.current
    val auth = LocalAuthenticator.current
    val needsDefault = rememberNeedsDefaultNotice()
    // What a confirmation dialog is for: the rows to delete for good, or null; emptying the whole bin
    var confirmDelete by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var confirmEmpty by rememberSaveable { mutableStateOf(false) }
    val failed = stringResource(R.string.bin_restore_failed)

    BinScreen(
        groups = state.groups,
        onBack = onBack,
        expanded = state.expanded,
        selected = state.selected,
        loaded = state.loaded,
        onToggle = { group ->
            // A locked chat shows its messages only after the fingerprint or screen lock
            if (group.locked) auth.authenticate { ok -> if (ok) vm.unlock(group) } else vm.toggle(group)
        },
        onSelect = vm::select,
        onToggleSelected = vm::toggleSelected,
        onClearSelection = vm::clearSelection,
        onRestore = { keys ->
            val started = vm.restore(keys) { n ->
                notices.show(if (n > 0) context.resources.getQuantityString(R.plurals.bin_restored, n, n) else failed)
            }
            if (!started) needsDefault()
        },
        onDeleteForever = { keys -> confirmDelete = ArrayList(keys) },
        onEmpty = if (state.groups.isNotEmpty()) ({ confirmEmpty = true }) else null,
    )

    BackHandler(enabled = state.selecting) { vm.clearSelection() }

    val c = HebTheme.colors
    confirmDelete?.let { keys ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = c.surface,
            title = { Text(pluralStringResource(R.plurals.bin_delete_forever_title, keys.size, keys.size), color = c.onSurface, fontWeight = FontWeight.ExtraBold) },
            text = { Text(stringResource(R.string.bin_delete_forever_body), color = c.onSurfaceMuted) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    vm.deleteForever(keys.toSet())
                    notices.show(context.getString(R.string.bin_deleted_forever))
                }) { Text(stringResource(R.string.bin_delete_forever), color = c.spam, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.common_cancel), color = c.accent) } },
        )
    }
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            containerColor = c.surface,
            title = { Text(stringResource(R.string.bin_empty_confirm_title), color = c.onSurface, fontWeight = FontWeight.ExtraBold) },
            text = { Text(stringResource(R.string.bin_empty_confirm_body), color = c.onSurfaceMuted) },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmpty = false
                    vm.empty()
                    notices.show(context.getString(R.string.bin_deleted_forever))
                }) { Text(stringResource(R.string.bin_empty), color = c.spam, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text(stringResource(R.string.common_cancel), color = c.accent) } },
        )
    }
}
