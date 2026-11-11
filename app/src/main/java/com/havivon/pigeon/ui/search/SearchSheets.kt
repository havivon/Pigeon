package com.havivon.pigeon.ui.search

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.havivon.pigeon.R
import com.havivon.pigeon.data.SensitiveClipboard
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.CustomFolder
import com.havivon.pigeon.ui.app.LocalAuthenticator
import com.havivon.pigeon.ui.app.LocalNotices
import com.havivon.pigeon.ui.app.rememberNeedsDefaultNotice
import com.havivon.pigeon.ui.common.dateLabels
import com.havivon.pigeon.ui.components.Avatar
import com.havivon.pigeon.ui.components.CategoryTagChip
import com.havivon.pigeon.ui.components.FitOrShort
import com.havivon.pigeon.ui.components.GradientHeader
import com.havivon.pigeon.ui.components.HeaderIcon
import com.havivon.pigeon.ui.components.SheetAction
import com.havivon.pigeon.ui.components.SpamAvatar
import com.havivon.pigeon.ui.inbox.MoveWithNotices
import com.havivon.pigeon.ui.reminders.ReminderSheet
import com.havivon.pigeon.ui.theme.HebTheme
import kotlinx.coroutines.launch

/**
 * The sheet a long press on a message result opens: who sent it, its category tag and its first line, then what can be done with
 * it. An action that does not apply (no conversation to act on, nothing unread) is passed as null and left out.
 */
@Composable
internal fun MessageActions(
    hit: SearchHit,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onStar: () -> Unit,
    onRemind: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onMove: (() -> Unit)? = null,
    onMarkRead: (() -> Unit)? = null,
    onSpam: (() -> Unit)? = null,
    onLock: (() -> Unit)? = null,
    noCategory: String = stringResource(R.string.search_no_category),
) {
    val c = HebTheme.colors
    val conversation = hit.conversation
    Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 16.dp)) {
        // Sender, category tag, first line
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (conversation?.isSpam == true) SpamAvatar(size = 42.dp) else Avatar(hit.name, hit.avatarArgb, size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(hit.name, color = c.onSurface, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                CategoryTagChip(resultTag(hit.category, noCategory))
                Spacer(Modifier.height(4.dp))
                Text(firstLine(hit.text), color = c.onSurfaceMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(6.dp))
        SheetAction(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.search_action_open_message), onClick = onOpen)
        SheetAction(Icons.Rounded.ContentCopy, stringResource(R.string.search_action_copy), onClick = onCopy)
        SheetAction(Icons.Rounded.Share, stringResource(R.string.common_share), onClick = onShare)
        SheetAction(
            if (hit.starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            stringResource(if (hit.starred) R.string.thread_unstar else R.string.thread_star),
            onClick = onStar,
        )
        SheetAction(Icons.Rounded.Alarm, stringResource(R.string.reminder_title), onClick = onRemind)
        if (onMove != null) SheetAction(Icons.AutoMirrored.Rounded.DriveFileMove, stringResource(R.string.action_move_to_folder), onClick = onMove)
        if (onMarkRead != null) SheetAction(Icons.Rounded.DoneAll, stringResource(R.string.action_mark_read), onClick = onMarkRead)
        if (onSpam != null) {
            val isSpam = conversation?.isSpam == true
            SheetAction(
                if (isSpam) Icons.Rounded.VerifiedUser else Icons.Rounded.Report,
                stringResource(if (isSpam) R.string.spam_not_spam else R.string.action_mark_spam),
                onClick = onSpam,
            )
        }
        if (onLock != null) {
            val locked = conversation?.locked == true
            SheetAction(
                if (locked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                stringResource(if (locked) R.string.action_unlock_chat else R.string.action_lock_chat),
                onClick = onLock,
            )
        }
        SheetAction(Icons.Rounded.CheckCircle, stringResource(R.string.search_action_select), onClick = onSelect)
        SheetAction(Icons.Rounded.Delete, stringResource(R.string.search_action_delete_message), danger = true, onClick = onDelete)
    }
}

/** The first non-blank line of a message, for the sheet's header. */
internal fun firstLine(text: String): String =
    text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty().take(HEADER_LINE_MAX)

/** The header shows two lines at most: a message that is one enormous line (an encoded blob) is cut before it reaches the text layout. */
private const val HEADER_LINE_MAX = 240

/**
 * The long-press sheet of a message result with what follows from it (the reminder chooser, "Move to category"), using the
 * same actions as the inbox ([SearchViewModel.sheet]). [target] is the result whose sheet is open (null for none);
 * [onDone] closes it, [onOpen] opens the conversation at the message and [onSelect] starts a selection with it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessageSheet(
    target: SearchHit?,
    vm: SearchViewModel,
    customFolders: List<CustomFolder>,
    onDone: () -> Unit,
    onOpen: (SearchHit) -> Unit,
    onSelect: (SearchHit) -> Unit,
) {
    val context = LocalContext.current
    val notices = LocalNotices.current
    val auth = LocalAuthenticator.current
    val scope = rememberCoroutineScope()
    var remindFor by remember { mutableStateOf<SearchHit?>(null) }
    var moveFor by remember { mutableStateOf<SearchHit?>(null) }
    val needsDefault = rememberNeedsDefaultNotice()
    val reminderCreated = stringResource(R.string.thread_reminder_created)
    val needsScreenLock = stringResource(R.string.lock_needs_screen_lock)
    val movedToSpam = stringResource(R.string.thread_moved_to_spam)
    val notSpamDone = stringResource(R.string.search_not_spam_done)

    // One sheet per result: another long press never reuses the open sheet's state
    target?.let { hit -> key(hit.messageId) {
        val conv = hit.conversation
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = onDone, sheetState = sheetState, containerColor = HebTheme.colors.surface) {
            MessageActions(
                hit,
                onOpen = { onDone(); onOpen(hit) },
                onCopy = {
                    onDone()
                    SensitiveClipboard.copy(context, context.getString(R.string.app_name), hit.text, R.string.thread_copied)
                },
                onShare = {
                    onDone()
                    shareText(context, vm.textOf(listOf(hit)))
                },
                onStar = { onDone(); vm.toggleStar(hit) },
                onRemind = { onDone(); remindFor = hit },
                onSelect = { onDone(); onSelect(hit) },
                onDelete = {
                    onDone()
                    val messages = context.resources
                    vm.deleteMessage(hit)?.let { notices.undo(messages.getQuantityString(R.plurals.thread_messages_deleted, 1, 1), it) } ?: needsDefault()
                },
                // Each action takes the conversation as an explicit, non-null parameter: a lambda that captured a
                // smart-cast `conv` inside this sheet crashed on the phone (ClassCastException: Conversation to Void,
                // a Compose compiler bug with `if (x != null) ({ … }) else null` lambdas)
                onMove = conv?.let { c: Conversation -> { onDone(); moveFor = hit } },
                onMarkRead = conv?.takeIf { it.unread > 0 }?.let { c: Conversation ->
                    {
                        onDone()
                        if (!vm.sheet.markRead(c)) needsDefault()
                    }
                },
                onSpam = conv?.let { c: Conversation ->
                    {
                        onDone()
                        if (c.isSpam) vm.sheet.notSpam(c) else vm.sheet.markSpam(c)
                        notices.show(if (c.isSpam) notSpamDone else movedToSpam)
                    }
                },
                onLock = conv?.let { c: Conversation ->
                    {
                        onDone()
                        // Unlocking asks who is there first; locking needs a screen lock to be set
                        when {
                            c.locked -> auth.authenticate { ok -> if (ok) vm.sheet.unlock(c) }
                            auth.available() -> vm.sheet.lock(c)
                            else -> notices.show(needsScreenLock)
                        }
                    }
                },
            )
        }
    } }

    moveFor?.let { hit ->
        hit.conversation?.let { conv ->
            MoveWithNotices(
                conv, customFolders,
                problemOf = { vm.sheet.folderNameProblem(it, customFolders) },
                onMove = { vm.sheet.moveTo(conv, it) },
                onCreate = { name, argb -> vm.sheet.createFolderAndMove(conv, name, argb) },
                onDismiss = { moveFor = null },
            )
        }
    }

    remindFor?.let { hit ->
        ReminderSheet(
            onChoose = { at ->
                remindFor = null
                scope.launch {
                    if (vm.sheet.remindMessage(hit.threadId, hit.messageId, hit.text, at)) {
                        notices.show(String.format(reminderCreated, dateLabels(context).reminder(at, System.currentTimeMillis(), withTime = true)))
                    }
                }
            },
            onDismiss = { remindFor = null },
        )
    }
}

/** Offers [text] to the apps that take plain text; nothing happens when there are none. */
internal fun shareText(context: android.content.Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    try {
        context.startActivity(Intent.createChooser(send, null))
    } catch (_: ActivityNotFoundException) {
        // No app can take text: nothing to offer
    }
}

/** The move sheet for a selection of several results: the subtitle says whose conversations it moves. */
@Composable
internal fun MoveSelectionFlow(
    vm: SearchViewModel,
    customFolders: List<CustomFolder>,
    onDismiss: () -> Unit,
) {
    val list = remember { vm.selectedConversationList() }
    val first = list.firstOrNull() ?: run {
        onDismiss()
        return
    }
    MoveWithNotices(
        first, customFolders,
        problemOf = { vm.sheet.folderNameProblem(it, customFolders) },
        onMove = { vm.moveSelected(it) },
        onCreate = { name, argb -> vm.createFolderAndMoveSelected(name, argb) },
        onDismiss = onDismiss,
        subtitle = pluralStringResource(R.plurals.search_move_applies_to, list.size, list.size),
    )
}

/** One action of the selection's row. */
data class SelectionAction(val icon: ImageVector, val label: String, val danger: Boolean = false, val onClick: () -> Unit)

/** The bar at the top while selecting: leave the selection, how many results are selected, and "select all". */
@Composable
internal fun SearchSelectionBar(count: Int, onClear: () -> Unit, onSelectAll: () -> Unit) {
    GradientHeader {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderIcon(Icons.Rounded.Close, stringResource(R.string.thread_clear_selection), onClear)
            // The words, or just the number when the icon leaves too little room for them
            FitOrShort(
                Modifier.weight(1f),
                full = { Text(pluralStringResource(R.plurals.search_selected_count, count, count), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1) },
                short = { Text(count.toString(), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
            HeaderIcon(Icons.Rounded.SelectAll, stringResource(R.string.common_select_all), onSelectAll)
        }
    }
}

/** The actions on the selection, in a row at the bottom that scrolls sideways when they do not all fit. */
@Composable
internal fun SearchSelectionActions(actions: List<SelectionAction>) {
    val c = HebTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.surface)
            .navigationBarsPadding()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (a in actions) {
            val tint = if (a.danger) c.spam else c.accent
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(tint.copy(alpha = 0.12f))
                    .clickable(onClick = a.onClick)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(a.icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(a.label, color = if (a.danger) c.spam else c.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

/** The selection's actions: copy, share and star only when messages are selected ([hasMessages]); [count] results are selected. Null callbacks leave an action out. */
@Composable
internal fun selectionActions(
    hasMessages: Boolean,
    count: Int,
    allStarred: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onStar: () -> Unit,
    onMarkRead: () -> Unit,
    onMove: () -> Unit,
    onSpam: () -> Unit,
    onDelete: () -> Unit,
    onRemind: (() -> Unit)?,
): List<SelectionAction> = buildList {
    if (hasMessages) {
        add(SelectionAction(Icons.Rounded.ContentCopy, stringResource(R.string.thread_copy_together, count), onClick = onCopy))
        add(SelectionAction(Icons.Rounded.Share, stringResource(R.string.common_share), onClick = onShare))
        add(
            SelectionAction(
                if (allStarred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                stringResource(if (allStarred) R.string.thread_unstar else R.string.thread_star),
                onClick = onStar,
            ),
        )
    }
    add(SelectionAction(Icons.Rounded.DoneAll, stringResource(R.string.action_mark_read), onClick = onMarkRead))
    add(SelectionAction(Icons.AutoMirrored.Rounded.DriveFileMove, stringResource(R.string.action_move_to_folder), onClick = onMove))
    add(SelectionAction(Icons.Rounded.Report, stringResource(R.string.action_mark_spam), onClick = onSpam))
    if (onRemind != null) add(SelectionAction(Icons.Rounded.Alarm, stringResource(R.string.reminder_title), onClick = onRemind))
    add(SelectionAction(Icons.Rounded.Delete, stringResource(R.string.thread_delete_selected), danger = true, onClick = onDelete))
}
