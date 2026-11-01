package com.havivon.pigeon.ui.spam

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.havivon.pigeon.R
import com.havivon.pigeon.data.SensitiveClipboard
import com.havivon.pigeon.domain.analysis.LinkInfo
import com.havivon.pigeon.ui.conversation.LinkDetails
import com.havivon.pigeon.ui.conversation.openInBrowser
import com.havivon.pigeon.ui.app.LocalNotices
import com.havivon.pigeon.ui.app.rememberNeedsDefaultNotice
import com.havivon.pigeon.ui.common.conversationMapper
import com.havivon.pigeon.ui.components.EmptyState
import com.havivon.pigeon.ui.navigation.pigeonViewModel
import com.havivon.pigeon.ui.theme.HebTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpamRoute(onBack: () -> Unit, openThread: (threadId: Long, messageId: Long?) -> Unit) {
    val context = LocalContext.current
    val vm = pigeonViewModel { app ->
        SpamViewModel(
            app.messages, app.spam, app.locks, conversationMapper(context, app), app.accessChanges, app.appScope,
            canWriteNow = app::canWrite,
            judgeSpam = app.spamOnRead::judge,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val notices = LocalNotices.current
    var confirmAll by rememberSaveable { mutableStateOf(false) }
    var link by remember { mutableStateOf<LinkInfo?>(null) }
    val backToInbox = stringResource(R.string.spam_back_to_inbox)
    val needsDefault = rememberNeedsDefaultNotice()

    SpamScreen(
        groups = state.groups,
        onBack = onBack,
        expanded = state.expanded,
        messages = state.messages,
        onToggle = vm::toggle,
        onNotSpam = {
            vm.notSpam(it)
            notices.show(backToInbox)
        },
        onDelete = { group ->
            vm.delete(group)?.let { (n, deletion) ->
                notices.undo(context.resources.getQuantityString(R.plurals.spam_deleted_all, n, n), deletion)
            } ?: needsDefault()
        },
        onOpen = openThread,
        onLink = { link = it },
        onDeleteAll = if (state.items.isNotEmpty()) ({ if (vm.canWrite) confirmAll = true else needsDefault() }) else null,
        emptyState = if (state.loaded) ({ EmptyState(Icons.Rounded.Shield, stringResource(R.string.spam_empty_title), stringResource(R.string.spam_empty_body)) }) else null,
    )

    link?.let { l ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { link = null }, sheetState = sheetState, containerColor = HebTheme.colors.surface) {
            LinkDetails(
                l,
                onOpen = {
                    link = null
                    openInBrowser(context, l)
                },
                onCopy = {
                    link = null
                    SensitiveClipboard.copy(context, context.getString(R.string.app_name), l.text, R.string.link_copied)
                },
            )
        }
    }

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
