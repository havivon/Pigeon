package com.havivon.pigeon.ui.conversation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.havivon.pigeon.R
import com.havivon.pigeon.data.AppContainer
import com.havivon.pigeon.data.SensitiveClipboard
import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.domain.analysis.LinkInfo
import com.havivon.pigeon.domain.folders.FolderMoves
import com.havivon.pigeon.domain.folders.FolderNames
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.FolderChoice
import com.havivon.pigeon.domain.model.SendStatus
import com.havivon.pigeon.ui.app.LocalAuthenticator
import com.havivon.pigeon.ui.app.LocalDefaultApp
import com.havivon.pigeon.ui.app.LocalNotices
import com.havivon.pigeon.ui.app.rememberNeedsDefaultNotice
import com.havivon.pigeon.ui.common.avatarColor
import com.havivon.pigeon.ui.common.conversationMapper
import com.havivon.pigeon.ui.common.dateLabels
import com.havivon.pigeon.ui.common.ltr
import com.havivon.pigeon.ui.common.reservedFolderNames
import com.havivon.pigeon.ui.folders.MoveToFolderFlow
import com.havivon.pigeon.ui.navigation.pigeonViewModel
import com.havivon.pigeon.ui.reminders.ReminderSheet
import com.havivon.pigeon.ui.theme.HebTheme
import java.time.ZoneId

/** The conversation screen on real data. [onClosed] leaves it (after delete or "mark as spam"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationRoute(threadId: Long, focusMessageId: Long?, onBack: () -> Unit, onClosed: () -> Unit) {
    val context = LocalContext.current
    val me = stringResource(R.string.thread_sender_me)
    val vm = pigeonViewModel { app ->
        val labels = dateLabels(context)
        ConversationViewModel(
            threadId, app.messages, app.smsSender, app.rules, app.favorites, app.reminders, app.reminderScheduler,
            app.spam, app.locks, app.pins, app.folders,
            FolderMoves(app.folders, app.spam, reservedFolderNames(context)),
            mapper = conversationMapper(context, app, labels),
            labels = labels,
            zone = ZoneId.systemDefault(),
            me = me,
            cancelNotification = app.notifier::cancel,
            appScope = app.appScope,
            canWrite = app::canWrite,
            focusMessageId = focusMessageId,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val notices = LocalNotices.current
    val auth = LocalAuthenticator.current
    var textSelectionId by rememberSaveable { mutableStateOf<Long?>(null) }
    var link by remember { mutableStateOf<LinkInfo?>(null) }
    var remindSheet by rememberSaveable { mutableStateOf(false) }
    var moveSheet by rememberSaveable { mutableStateOf(false) }

    // While it is on the screen its messages count as read and are not notified
    val container = remember { AppContainer.from(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, threadId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    container.visibleThreadId = threadId
                    vm.setVisible(true)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    if (container.visibleThreadId == threadId) container.visibleThreadId = null
                    vm.setVisible(false)
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (container.visibleThreadId == threadId) container.visibleThreadId = null
            vm.setVisible(false)
        }
    }

    val sendFailed = stringResource(R.string.thread_send_failed)
    val reminderRemoved = stringResource(R.string.thread_reminder_removed)
    val movedToSpam = stringResource(R.string.thread_moved_to_spam)
    val backToInbox = stringResource(R.string.spam_back_to_inbox)
    val movedTo = stringResource(R.string.move_folder_moved)
    val backToAutomatic = stringResource(R.string.move_folder_automatic_done)
    val customFolders by rememberUpdatedState(state.customFolders)
    val reminderCreatedFormat = stringResource(R.string.thread_reminder_created)
    val needsDefault = rememberNeedsDefaultNotice()
    // The events are collected once: read the access as it is when one comes
    val access by rememberUpdatedState(LocalDefaultApp.current)
    val makeDefaultLabel = stringResource(R.string.needs_default_action)
    LaunchedEffect(vm) {
        vm.eventFlow.collect { e ->
            when (e) {
                // Not the default and not allowed to send either: the same way out as everywhere else
                ConversationEvent.SendFailed ->
                    if (access.isDefault) notices.show(sendFailed) else notices.action(sendFailed, makeDefaultLabel, access.makeDefault)
                ConversationEvent.NeedsDefault -> needsDefault()
                is ConversationEvent.ReminderCreated -> notices.show(String.format(reminderCreatedFormat, e.label))
                ConversationEvent.ReminderRemoved -> notices.show(reminderRemoved)
                ConversationEvent.MovedToSpam -> {
                    notices.show(movedToSpam)
                    onClosed()
                }
                ConversationEvent.NotSpam -> notices.show(backToInbox)
                is ConversationEvent.Moved -> notices.show(
                    when (val t = e.target) {
                        null -> backToAutomatic
                        is FolderChoice.BuiltIn -> String.format(movedTo, context.getString(t.folder.labelRes))
                        is FolderChoice.Custom -> String.format(movedTo, customFolders.firstOrNull { it.id == t.id }?.name.orEmpty())
                    },
                )
                is ConversationEvent.MovedToNew -> notices.show(String.format(movedTo, FolderNames.clean(e.name)))
            }
        }
    }

    BackHandler(enabled = selected.isNotEmpty() || textSelectionId != null) {
        vm.clearSelection()
        textSelectionId = null
    }

    // Not read yet (or gone): the screen's frame at once, with an empty header, never a blank screen
    val known = state.conversation
    val conversation = known ?: remember(threadId) { placeholderConversation(threadId) }
    val dialable = known?.addresses?.singleOrNull()?.takeIf { PhoneNumbers.isPhoneNumber(it) }
    val conversationDeleted = stringResource(R.string.inbox_conversation_deleted)
    val needsScreenLock = stringResource(R.string.lock_needs_screen_lock)

    val menu = if (known == null) emptyList() else listOfNotNull(
        ThreadMenuItem(stringResource(if (conversation.pinned) R.string.action_unpin else R.string.action_pin), Icons.Rounded.PushPin) { vm.togglePin() },
        ThreadMenuItem(
            stringResource(if (conversation.locked) R.string.action_unlock_chat else R.string.action_lock_chat),
            if (conversation.locked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
        ) {
            when {
                conversation.locked -> auth.authenticate { ok -> if (ok) vm.unlock() }
                auth.available() -> vm.lock()
                else -> notices.show(needsScreenLock)
            }
        },
        ThreadMenuItem(stringResource(R.string.action_move_to_folder), Icons.AutoMirrored.Rounded.DriveFileMove) { moveSheet = true },
        if (conversation.isSpam) {
            ThreadMenuItem(stringResource(R.string.spam_not_spam), Icons.Rounded.VerifiedUser) { vm.markNotSpam() }
        } else {
            ThreadMenuItem(stringResource(R.string.action_mark_spam), Icons.Rounded.Report) { vm.markSpam() }
        },
        ThreadMenuItem(stringResource(R.string.action_delete_conversation), Icons.Rounded.Delete, danger = true) {
            vm.deleteConversation()?.let {
                notices.undo(conversationDeleted, it)
                onClosed()
            }
        },
    )

    ConversationScreen(
        conversation = conversation,
        items = state.items,
        rule = state.rule,
        draft = draft,
        textSelectionId = textSelectionId,
        selectedIds = selected,
        subtitle = conversation.addresses.joinToString(", ") { ltr(it) }.takeIf { it.isNotEmpty() && it != conversation.name },
        loading = !state.loaded,
        focusMessageId = focusMessageId,
        onNearTop = vm::loadOlder,
        onBack = onBack,
        onMessageTap = { m ->
            when {
                selected.isNotEmpty() -> vm.toggleSelected(m.id)
                m.status == SendStatus.Failed -> vm.retry(m.id)
                else -> textSelectionId = if (textSelectionId == m.id) null else m.id
            }
        },
        onMessageLongPress = { m ->
            textSelectionId = null
            vm.select(m.id)
        },
        onClearSelection = vm::clearSelection,
        onSelectAll = vm::selectAll,
        onCopySelected = {
            SensitiveClipboard.copy(context, context.getString(R.string.app_name), vm.selectedText(), R.string.thread_copied)
            vm.clearSelection()
        },
        onShareSelected = {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, vm.selectedText())
            try {
                context.startActivity(Intent.createChooser(send, null))
            } catch (_: ActivityNotFoundException) {
                // No app can take text: nothing to offer
            }
            vm.clearSelection()
        },
        onDraft = vm::setDraft,
        onSend = vm::send,
        onCall = dialable?.let { number ->
            {
                try {
                    // ACTION_DIAL only opens the dialer with the number: no call permission, the user still taps Call
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
                } catch (_: ActivityNotFoundException) {
                    // A phone without a dialer (a tablet)
                }
            }
        },
        menu = menu,
        selectedStarred = vm.allSelectedStarred(),
        onStarSelected = vm::starSelected,
        onRemindSelected = if (selected.size == 1) ({ remindSheet = true }) else null,
        onDeleteSelected = {
            vm.deleteSelected()?.let { (count, deletion) ->
                notices.undo(context.resources.getQuantityString(R.plurals.thread_messages_deleted, count, count), deletion)
            }
        },
        onReminder = vm::toggleReminder,
        onLink = { link = it },
        onNotSpam = vm::markNotSpam,
    )

    if (moveSheet && known != null) {
        MoveToFolderFlow(
            known,
            state.customFolders,
            problemOf = vm::folderNameProblem,
            onMove = vm::moveTo,
            onCreate = vm::createFolderAndMove,
            onDismiss = { moveSheet = false },
        )
    }

    if (remindSheet) {
        ReminderSheet(
            onChoose = { at ->
                remindSheet = false
                vm.remindSelected(at)
            },
            onDismiss = { remindSheet = false },
        )
    }

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
}

/** The header of a conversation not read yet: no name, no number, no actions. */
internal fun placeholderConversation(threadId: Long) = Conversation(threadId, name = "", snippet = "", time = "", avatarArgb = avatarColor(""))
