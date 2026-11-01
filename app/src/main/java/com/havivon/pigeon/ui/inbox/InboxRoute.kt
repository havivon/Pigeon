package com.havivon.pigeon.ui.inbox

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.havivon.pigeon.R
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.ui.app.LocalAuthenticator
import com.havivon.pigeon.ui.app.LocalDefaultApp
import com.havivon.pigeon.ui.app.LocalNotices
import com.havivon.pigeon.ui.app.rememberNeedsDefaultNotice
import com.havivon.pigeon.domain.model.SmsAccess
import com.havivon.pigeon.ui.onboarding.ViewerBanner
import com.havivon.pigeon.ui.common.appLocale
import com.havivon.pigeon.ui.common.conversationMapper
import com.havivon.pigeon.ui.common.dateLabels
import com.havivon.pigeon.ui.common.inboxDateLine
import com.havivon.pigeon.ui.components.Avatar
import com.havivon.pigeon.ui.components.EmptyState
import com.havivon.pigeon.ui.components.PillButton
import com.havivon.pigeon.ui.components.SheetAction
import com.havivon.pigeon.ui.components.SheetTitle
import com.havivon.pigeon.ui.components.SpamAvatar
import com.havivon.pigeon.ui.navigation.Screen
import com.havivon.pigeon.ui.navigation.pigeonViewModel
import com.havivon.pigeon.ui.reminders.ReminderSheet
import com.havivon.pigeon.ui.theme.HebTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxRoute(navigate: (Screen) -> Unit, openThread: (Long) -> Unit) {
    val context = LocalContext.current
    val locale = appLocale(context)
    val vm = pigeonViewModel { app ->
        InboxViewModel(
            app.messages, app.rules, app.pins, app.locks, app.spam, app.reminders, app.reminderScheduler,
            mapper = conversationMapper(context, app),
            accessChanges = app.accessChanges,
            dateLine = { inboxDateLine(locale) },
            cancelNotification = app.notifier::cancel,
            canWrite = app::canWrite,
            judgeSpam = app.spamOnRead::judge,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val notices = LocalNotices.current
    val auth = LocalAuthenticator.current
    var sheetFor by remember { mutableStateOf<Conversation?>(null) }
    var remindFor by remember { mutableStateOf<Conversation?>(null) }
    val scope = rememberCoroutineScope()
    val reminderCreated = stringResource(R.string.thread_reminder_created)
    val deleted = stringResource(R.string.inbox_conversation_deleted)
    val needsScreenLock = stringResource(R.string.lock_needs_screen_lock)
    val needsDefault = rememberNeedsDefaultNotice()
    val access = LocalDefaultApp.current

    InboxScreen(
        conversations = state.conversations,
        rules = state.rules,
        folderCounts = state.folderCounts,
        dateLine = state.dateLine,
        selectedFolder = state.folder,
        onFolder = { if (it == Folder.Spam) navigate(Screen.Spam) else vm.selectFolder(it) },
        onOpen = { openThread(it.id) },
        onSettings = { navigate(Screen.Settings) },
        onLongPress = { sheetFor = it },
        onSearch = { navigate(Screen.Search) },
        onNewMessage = { navigate(Screen.NewMessage()) },
        emptyState = if (state.loaded) ({ InboxEmpty(state.folder) }) else null,
        banner = if (access.showViewerBanner) ({ ViewerBanner(onMakeDefault = access.makeDefault, onHide = access.dismissViewerBanner) }) else null,
    )

    sheetFor?.let { conv ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        fun done() {
            sheetFor = null
        }
        ModalBottomSheet(onDismissRequest = ::done, sheetState = sheetState, containerColor = HebTheme.colors.surface) {
            ConversationActions(
                conv,
                onPin = { vm.togglePin(conv); done() },
                onMarkRead = {
                    done()
                    if (!vm.markRead(conv)) needsDefault()
                },
                onRemind = {
                    done()
                    remindFor = conv
                },
                onLock = {
                    done()
                    when {
                        conv.locked -> auth.authenticate { ok -> if (ok) vm.unlock(conv) }
                        auth.available() -> vm.lock(conv)
                        else -> notices.show(needsScreenLock)
                    }
                },
                onDelete = {
                    done()
                    vm.delete(conv)?.let { notices.undo(deleted, it) } ?: needsDefault()
                },
            )
        }
    }

    remindFor?.let { conv ->
        ReminderSheet(
            onChoose = { at ->
                remindFor = null
                scope.launch {
                    if (vm.remind(conv, at)) notices.show(String.format(reminderCreated, dateLabels(context).reminder(at, System.currentTimeMillis(), withTime = true)))
                }
            },
            onDismiss = { remindFor = null },
        )
    }
}

/** The long-press sheet's content: apart from its window, so the design snapshots can draw it. */
@Composable
internal fun ConversationActions(
    conv: Conversation,
    onPin: () -> Unit,
    onMarkRead: () -> Unit,
    onRemind: () -> Unit,
    onLock: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp).padding(bottom = 16.dp)) {
        SheetTitle(conv.name) { if (conv.isSpam) SpamAvatar(size = 42.dp) else Avatar(conv.name, conv.avatarArgb, size = 42.dp) }
        Spacer(Modifier.height(6.dp))
        SheetAction(Icons.Rounded.PushPin, stringResource(if (conv.pinned) R.string.action_unpin else R.string.action_pin), onClick = onPin)
        if (conv.unread > 0) SheetAction(Icons.Rounded.DoneAll, stringResource(R.string.action_mark_read), onClick = onMarkRead)
        SheetAction(Icons.Rounded.Alarm, stringResource(R.string.reminder_title), onClick = onRemind)
        SheetAction(
            if (conv.locked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
            stringResource(if (conv.locked) R.string.action_unlock_chat else R.string.action_lock_chat),
            onClick = onLock,
        )
        SheetAction(Icons.Rounded.Delete, stringResource(R.string.action_delete_conversation), danger = true, onClick = onDelete)
    }
}

/** No conversations: until Pigeon may see the messages, the reason and the two ways to fix it; then a plain "No messages yet". */
@Composable
internal fun InboxEmpty(folder: Folder) {
    val access = LocalDefaultApp.current
    when {
        access.mode == SmsAccess.None -> EmptyState(
            Icons.Rounded.Forum,
            stringResource(R.string.onboarding_reminder_title),
            stringResource(R.string.inbox_empty_not_default),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PillButton(stringResource(R.string.onboarding_make_default), filled = true, onClick = access.makeDefault)
                Spacer(Modifier.height(10.dp))
                PillButton(stringResource(R.string.onboarding_view_only), onClick = access.viewOnly)
            }
        }
        folder == Folder.All -> EmptyState(Icons.Rounded.Forum, stringResource(R.string.inbox_empty_title), stringResource(R.string.inbox_empty_body))
        else -> EmptyState(
            Icons.Rounded.Forum,
            stringResource(R.string.inbox_empty_folder_title, stringResource(folder.labelRes)),
            stringResource(R.string.inbox_empty_folder_body),
        )
    }
}
