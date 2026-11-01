package com.havivon.pigeon.ui.spam

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
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
import com.havivon.pigeon.ui.app.LocalNotices
import com.havivon.pigeon.ui.common.conversationMapper
import com.havivon.pigeon.ui.components.EmptyState
import com.havivon.pigeon.ui.navigation.pigeonViewModel
import com.havivon.pigeon.ui.theme.HebTheme

@Composable
fun SpamRoute(onBack: () -> Unit, openThread: (Long) -> Unit) {
    val context = LocalContext.current
    val vm = pigeonViewModel { app -> SpamViewModel(app.messages, app.spam, app.locks, conversationMapper(context, app), app.accessChanges, app.appScope) }
    val state by vm.state.collectAsStateWithLifecycle()
    val notices = LocalNotices.current
    var confirmAll by rememberSaveable { mutableStateOf(false) }
    val deleted = stringResource(R.string.inbox_conversation_deleted)
    val backToInbox = stringResource(R.string.spam_back_to_inbox)

    SpamScreen(
        items = state.items,
        onBack = onBack,
        onNotSpam = {
            vm.notSpam(it)
            notices.show(backToInbox)
        },
        onDelete = { notices.undo(deleted, vm.delete(it)) },
        onOpen = { openThread(it.id) },
        onDeleteAll = if (state.items.isNotEmpty()) ({ confirmAll = true }) else null,
        emptyState = if (state.loaded) ({ EmptyState(Icons.Rounded.Shield, stringResource(R.string.spam_empty_title), stringResource(R.string.spam_empty_body)) }) else null,
    )

    if (confirmAll) {
        val c = HebTheme.colors
        val count = state.items.size
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            containerColor = c.surface,
            title = { Text(pluralStringResource(R.plurals.spam_delete_all_title, count, count), color = c.onSurface, fontWeight = FontWeight.ExtraBold) },
            text = { Text(stringResource(R.string.spam_delete_all_body), color = c.onSurfaceMuted) },
            confirmButton = {
                TextButton(onClick = {
                    confirmAll = false
                    vm.deleteAll()?.let { (n, deletion) ->
                        notices.undo(context.resources.getQuantityString(R.plurals.spam_deleted_all, n, n), deletion)
                    }
                }) { Text(stringResource(R.string.spam_delete_all), color = c.spam, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmAll = false }) { Text(stringResource(R.string.common_cancel), color = c.accent) }
            },
        )
    }
}
