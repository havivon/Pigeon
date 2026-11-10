package com.havivon.pigeon.ui.inbox

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.havivon.pigeon.R
import com.havivon.pigeon.domain.folders.FolderNames
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.CustomFolder
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.FolderChoice
import com.havivon.pigeon.ui.app.LocalAuthenticator
import com.havivon.pigeon.ui.app.LocalNotices
import com.havivon.pigeon.ui.app.rememberNeedsDefaultNotice
import com.havivon.pigeon.ui.common.dateLabels
import com.havivon.pigeon.ui.folders.MoveToFolderFlow
import com.havivon.pigeon.ui.reminders.ReminderSheet
import com.havivon.pigeon.ui.theme.HebTheme
import kotlinx.coroutines.launch

/**
 * The long-press sheet of a conversation and what follows from it (the reminder time chooser, "Move to
 * category"), with the notices of the inbox: the same for every screen that lists conversations. [target] is the
 * conversation whose sheet is open (null for none); [onDone] closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationSheets(
    target: Conversation?,
    onDone: () -> Unit,
    actions: ConversationSheetActions,
    customFolders: List<CustomFolder>,
    /** The search's extras ([ConversationActions]): open the conversation, "Mark as spam" / "Not spam", start a selection with it. */
    onOpen: ((Conversation) -> Unit)? = null,
    onSelect: ((Conversation) -> Unit)? = null,
    withSpam: Boolean = false,
) {
    val context = LocalContext.current
    val notices = LocalNotices.current
    val auth = LocalAuthenticator.current
    var remindFor by remember { mutableStateOf<Conversation?>(null) }
    var moveFor by remember { mutableStateOf<Conversation?>(null) }
    val scope = rememberCoroutineScope()
    val reminderCreated = stringResource(R.string.thread_reminder_created)
    val deleted = stringResource(R.string.inbox_conversation_deleted)
    val needsScreenLock = stringResource(R.string.lock_needs_screen_lock)
    val movedToSpam = stringResource(R.string.thread_moved_to_spam)
    val notSpamDone = stringResource(R.string.search_not_spam_done)
    val needsDefault = rememberNeedsDefaultNotice()

    target?.let { conv ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = onDone, sheetState = sheetState, containerColor = HebTheme.colors.surface) {
            ConversationActions(
                conv,
                onPin = { actions.togglePin(conv); onDone() },
                onMarkRead = {
                    onDone()
                    if (!actions.markRead(conv)) needsDefault()
                },
                onRemind = {
                    onDone()
                    remindFor = conv
                },
                onMove = {
                    onDone()
                    moveFor = conv
                },
                onLock = {
                    onDone()
                    when {
                        conv.locked -> auth.authenticate { ok -> if (ok) actions.unlock(conv) }
                        auth.available() -> actions.lock(conv)
                        else -> notices.show(needsScreenLock)
                    }
                },
                onDelete = {
                    onDone()
                    actions.delete(conv)?.let { notices.undo(deleted, it) } ?: needsDefault()
                },
                onOpen = onOpen?.let { open -> { onDone(); open(conv) } },
                onSpam = if (withSpam) ({
                    onDone()
                    if (conv.isSpam) actions.notSpam(conv) else actions.markSpam(conv)
                    notices.show(if (conv.isSpam) notSpamDone else movedToSpam)
                }) else null,
                onSelect = onSelect?.let { select -> { onDone(); select(conv) } },
            )
        }
    }

    moveFor?.let { conv ->
        MoveWithNotices(
            conv, customFolders,
            problemOf = { actions.folderNameProblem(it, customFolders) },
            onMove = { actions.moveTo(conv, it) },
            onCreate = { name, argb -> actions.createFolderAndMove(conv, name, argb) },
            onDismiss = { moveFor = null },
        )
    }

    remindFor?.let { conv ->
        ReminderSheet(
            onChoose = { at ->
                remindFor = null
                scope.launch {
                    if (actions.remind(conv, at)) notices.show(String.format(reminderCreated, dateLabels(context).reminder(at, System.currentTimeMillis(), withTime = true)))
                }
            },
            onDismiss = { remindFor = null },
        )
    }
}

/**
 * "Move to category" with the notice the inbox shows after it: [MoveToFolderFlow] for [conv] (or, with [subtitle], for
 * several conversations at once: [conv] is only who the sheet is drawn for). [onCreate] returns false when the name was not usable.
 */
@Composable
fun MoveWithNotices(
    conv: Conversation,
    customFolders: List<CustomFolder>,
    problemOf: (String) -> com.havivon.pigeon.domain.folders.FolderNameProblem?,
    onMove: (FolderChoice?) -> Unit,
    onCreate: suspend (name: String, argb: Long?) -> Boolean,
    onDismiss: () -> Unit,
    subtitle: String? = null,
) {
    val context = LocalContext.current
    val notices = LocalNotices.current
    val scope = rememberCoroutineScope()
    val movedToSpam = stringResource(R.string.thread_moved_to_spam)
    val movedTo = stringResource(R.string.move_folder_moved)
    val backToAutomatic = stringResource(R.string.move_folder_automatic_done)
    MoveToFolderFlow(
        conv,
        customFolders,
        problemOf = problemOf,
        onMove = { choice ->
            onMove(choice)
            notices.show(
                when (choice) {
                    null -> backToAutomatic
                    FolderChoice.BuiltIn(Folder.Spam) -> movedToSpam
                    is FolderChoice.BuiltIn -> String.format(movedTo, context.getString(choice.folder.labelRes))
                    is FolderChoice.Custom -> String.format(movedTo, customFolders.firstOrNull { it.id == choice.id }?.name.orEmpty())
                },
            )
        },
        onCreate = { name, argb -> scope.launch { if (onCreate(name, argb)) notices.show(String.format(movedTo, FolderNames.clean(name))) } },
        onDismiss = onDismiss,
        subtitle = subtitle,
    )
}
