package com.havivon.pigeon.ui.inbox

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.VerifiedUser
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
import com.havivon.pigeon.data.AppContainer
import kotlinx.coroutines.flow.map
import com.havivon.pigeon.domain.folders.FolderMoves
import com.havivon.pigeon.domain.folders.FolderNames
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.FolderChoice
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
import com.havivon.pigeon.ui.common.reservedFolderNames
import com.havivon.pigeon.ui.folders.MoveToFolderFlow
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

@Composable
fun InboxRoute(navigate: (Screen) -> Unit, openThread: (Long) -> Unit, openMessage: (threadId: Long, messageId: Long) -> Unit) {
    val context = LocalContext.current
    val locale = appLocale(context)
    val vm = pigeonViewModel { app ->
        InboxViewModel(
            app.messages, app.rules, app.pins, app.locks, app.spam, app.folders,
            FolderMoves(app.folders, app.spam, reservedFolderNames(context)),
            app.reminders, app.reminderScheduler,
            mapper = conversationMapper(context, app),
            accessChanges = app.accessChanges,
            dateLine = { inboxDateLine(locale) },
            cancelNotification = app.notifier::cancel,
            canWrite = app::canWrite,
            judgeSpam = app.spamOnRead::judge,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val hasReminders by remember { AppContainer.from(context).reminders.pending.map { it.isNotEmpty() } }.collectAsStateWithLifecycle(initialValue = false)
    var sheetFor by remember { mutableStateOf<Conversation?>(null) }
    var confirmMarkAll by remember { mutableStateOf(false) }
    val access = LocalDefaultApp.current
    val notices = LocalNotices.current
    val needsDefault = rememberNeedsDefaultNotice()

    InboxScreen(
        conversations = state.conversations,
        rules = state.rules,
        folderCounts = state.folderCounts,
        dateLine = state.dateLine,
        selectedFolder = state.folder,
        onFolder = { if (it == Folder.Spam) navigate(Screen.Spam) else vm.selectFolder(it) },
        customFolders = state.customFolders,
        customCounts = state.customFolderCounts,
        selectedCustom = state.customFolder,
        onCustomFolder = { vm.selectCustomFolder(it.id) },
        builtInColors = state.builtInColors,
        onOpen = { openThread(it.id) },
        unreadOnly = state.unreadOnly,
        unreadCount = state.unreadCount,
        onUnreadOnly = vm::selectUnreadOnly,
        // A viewer is told why at once; nothing is written, and no question is asked
        onMarkAllRead = { if (access.mode.canWrite) confirmMarkAll = true else needsDefault() },
        unreadMessages = state.unread.orEmpty(),
        onOpenMessage = { openMessage(it.threadId, it.messageId) },
        // The same sheet as for the message's conversation (gone by now: nothing to act on)
        onLongPressMessage = { m -> state.all.firstOrNull { it.id == m.threadId }?.let { sheetFor = it } },
        onSettings = { navigate(Screen.Settings) },
        onReminders = { navigate(Screen.Reminders) },
        hasReminders = hasReminders,
        onLongPress = { sheetFor = it },
        onSearch = { navigate(Screen.Search) },
        onManageCategories = { navigate(Screen.Folders) },
        onNewMessage = { navigate(Screen.NewMessage()) },
        emptyState = if (state.loaded) ({ InboxEmpty(state.folder, state.unreadOnly, state.customFolderName) }) else null,
        loading = !state.loaded || (state.unreadOnly && state.unread == null),
        banner = if (access.showViewerBanner) ({ ViewerBanner(onMakeDefault = access.makeDefault, onHide = access.dismissViewerBanner) }) else null,
    )

    if (confirmMarkAll) {
        val category = state.customFolderName ?: stringResource(state.folder.labelRes)
        val context = LocalContext.current
        MarkAllReadDialog(
            count = state.unreadCount,
            category = category,
            onConfirm = {
                confirmMarkAll = false
                val marked = vm.markAllRead()
                if (marked == null) needsDefault()
                else if (marked > 0) notices.show(context.resources.getQuantityString(R.plurals.mark_all_read_done, marked, marked))
            },
            onDismiss = { confirmMarkAll = false },
        )
    }

    ConversationSheets(sheetFor, onDone = { sheetFor = null }, actions = vm.sheet, customFolders = state.customFolders)
}

/** The long-press sheet's content: apart from its window, so the design snapshots can draw it. */
@Composable
internal fun ConversationActions(
    conv: Conversation,
    onPin: () -> Unit,
    onMarkRead: () -> Unit,
    onRemind: () -> Unit,
    onMove: () -> Unit,
    onLock: () -> Unit,
    onDelete: () -> Unit,
    /** Only the search's sheet has these: open the conversation, mark it as spam (or "Not spam"), start a selection with it. */
    onOpen: (() -> Unit)? = null,
    onSpam: (() -> Unit)? = null,
    onSelect: (() -> Unit)? = null,
) {
    Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 16.dp)) {
        SheetTitle(conv.name) { if (conv.isSpam) SpamAvatar(size = 42.dp) else Avatar(conv.name, conv.avatarArgb, size = 42.dp) }
        Spacer(Modifier.height(6.dp))
        if (onOpen != null) SheetAction(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.search_action_open), onClick = onOpen)
        SheetAction(Icons.Rounded.PushPin, stringResource(if (conv.pinned) R.string.action_unpin else R.string.action_pin), onClick = onPin)
        if (conv.unread > 0) SheetAction(Icons.Rounded.DoneAll, stringResource(R.string.action_mark_read), onClick = onMarkRead)
        SheetAction(Icons.Rounded.Alarm, stringResource(R.string.reminder_title), onClick = onRemind)
        SheetAction(Icons.AutoMirrored.Rounded.DriveFileMove, stringResource(R.string.action_move_to_folder), onClick = onMove)
        SheetAction(
            if (conv.locked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
            stringResource(if (conv.locked) R.string.action_unlock_chat else R.string.action_lock_chat),
            onClick = onLock,
        )
        if (onSpam != null) {
            SheetAction(
                if (conv.isSpam) Icons.Rounded.VerifiedUser else Icons.Rounded.Report,
                stringResource(if (conv.isSpam) R.string.spam_not_spam else R.string.action_mark_spam),
                onClick = onSpam,
            )
        }
        if (onSelect != null) SheetAction(Icons.Rounded.CheckCircle, stringResource(R.string.search_action_select), onClick = onSelect)
        SheetAction(Icons.Rounded.Delete, stringResource(R.string.action_delete_conversation), danger = true, onClick = onDelete)
    }
}

/**
 * No conversations (or, with [unreadOnly], no unread messages): until Pigeon may see the messages, the
 * reason and the two ways to fix it; then a plain "No messages yet". [customName] is the name of the chosen
 * category of the user's, which fills only by hand.
 */
@Composable
internal fun InboxEmpty(folder: Folder, unreadOnly: Boolean = false, customName: String? = null) {
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
        unreadOnly -> EmptyState(Icons.Rounded.DoneAll, stringResource(R.string.inbox_empty_unread_title), stringResource(R.string.inbox_empty_unread_body))
        customName != null -> EmptyState(
            Icons.Rounded.Forum,
            stringResource(R.string.inbox_empty_folder_title, customName),
            stringResource(R.string.inbox_empty_custom_body),
        )
        folder == Folder.All -> EmptyState(Icons.Rounded.Forum, stringResource(R.string.inbox_empty_title), stringResource(R.string.inbox_empty_body))
        else -> EmptyState(
            Icons.Rounded.Forum,
            stringResource(R.string.inbox_empty_folder_title, stringResource(folder.labelRes)),
            stringResource(R.string.inbox_empty_folder_body),
        )
    }
}
