package com.havivon.pigeon.ui.conversation

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.TextView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.ColorUtils
import com.havivon.pigeon.R
import com.havivon.pigeon.domain.analysis.LinkAnalyzer
import com.havivon.pigeon.domain.analysis.LinkInfo
import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.Message
import com.havivon.pigeon.domain.model.SendStatus
import com.havivon.pigeon.domain.model.ThreadItem
import com.havivon.pigeon.ui.components.Avatar
import com.havivon.pigeon.ui.components.GradientHeader
import com.havivon.pigeon.ui.components.HeaderIcon
import com.havivon.pigeon.ui.components.PillButton
import com.havivon.pigeon.ui.components.SpamAvatar
import com.havivon.pigeon.ui.theme.HebTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/** An entry of the conversation's ⋮ menu. */
data class ThreadMenuItem(val label: String, val icon: ImageVector, val danger: Boolean = false, val onClick: () -> Unit)

@Composable
fun ConversationScreen(
    conversation: Conversation,
    items: List<ThreadItem>,
    rule: ColorRule?,
    draft: String,
    textSelectionId: Long? = null,
    /** Static highlight for rendering the design without a finger on the screen. */
    previewSelection: TextRange? = null,
    selectedIds: Set<Long> = emptySet(),
    /** The number under the name; null shows only the name. */
    subtitle: String? = null,
    onBack: () -> Unit = {},
    onMessageTap: (Message) -> Unit = {},
    onMessageLongPress: (Message) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onSelectAll: () -> Unit = {},
    onCopySelected: () -> Unit = {},
    onShareSelected: () -> Unit = {},
    onDraft: (String) -> Unit = {},
    onSend: () -> Unit = {},
    /** Null hides the call button (a sender ID cannot be called). */
    onCall: (() -> Unit)? = {},
    menu: List<ThreadMenuItem> = emptyList(),
    /** Whether every selected message is starred already (the star then removes them). */
    selectedStarred: Boolean = false,
    onStarSelected: () -> Unit = {},
    /** "Remind me about this message"; null hides the button (it is about exactly one message). */
    onRemindSelected: (() -> Unit)? = null,
    onDeleteSelected: () -> Unit = {},
    onReminder: (Message) -> Unit = {},
    onLink: (LinkInfo) -> Unit = {},
    onNotSpam: () -> Unit = {},
) {
    val c = HebTheme.colors
    val selecting = selectedIds.isNotEmpty()
    val ruleColor = rule?.takeIf { it.enabled }?.let { Color(it.argb) }
    val spam = conversation.isSpam
    // The message whose text is being selected right now (framed in the accent colour)
    var activeSelectionId by remember { mutableStateOf(textSelectionId) }
    val listState = rememberLazyListState()
    // Open at the newest message, and follow new ones while the user is at the bottom
    var shownCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(items.size) {
        if (items.isEmpty()) return@LaunchedEffect
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        if (shownCount == 0 || lastVisible >= shownCount - 2) listState.scrollToItem(items.lastIndex)
        shownCount = items.size
    }
    Column(Modifier.fillMaxSize().background(c.background)) {
        if (selecting) {
            SelectionBar(selectedIds.size, selectedStarred, onClearSelection, onSelectAll, onStarSelected, onRemindSelected, onDeleteSelected)
        } else {
            ThreadBar(conversation, subtitle, onBack, onCall, menu)
        }
        if (spam && !selecting) SpamBanner(onNotSpam)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            items(items, key = { item -> if (item is ThreadItem.Msg) item.message.id else "day:" + (item as ThreadItem.Day).label }) { item ->
                when (item) {
                    is ThreadItem.Day -> DayChip(item.label)
                    is ThreadItem.Msg -> MessageRow(
                        msg = item.message,
                        ruleColor = if (spam) c.spam else item.message.ruleArgb?.let { Color(it) } ?: ruleColor,
                        selectingText = item.message.id == activeSelectionId,
                        previewSelection = previewSelection,
                        selectionMode = selecting,
                        selected = item.message.id in selectedIds,
                        onTap = { onMessageTap(item.message) },
                        onLongPress = { onMessageLongPress(item.message) },
                        onSelectionActive = { active ->
                            val id = item.message.id
                            activeSelectionId = if (active) id else activeSelectionId.takeUnless { it == id }
                        },
                        onReminder = { onReminder(item.message) },
                        onLink = onLink,
                    )
                }
            }
        }
        if (selecting) {
            SelectionActions(selectedIds.size, onCopySelected, onShareSelected)
        } else {
            Composer(draft, onDraft, onSend)
        }
    }
}

@Composable
private fun ThreadBar(conv: Conversation, subtitle: String?, onBack: () -> Unit, onCall: (() -> Unit)?, menu: List<ThreadMenuItem>) {
    val c = HebTheme.colors
    GradientHeader {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), onBack)
            if (conv.isSpam) SpamAvatar(size = 42.dp) else Avatar(conv.name, conv.avatarArgb, size = 42.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(conv.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp, maxLines = 1)
            }
            if (onCall != null) HeaderIcon(Icons.Rounded.Call, stringResource(R.string.thread_call), onCall)
            var open by remember { mutableStateOf(false) }
            Box {
                HeaderIcon(Icons.Rounded.MoreVert, stringResource(R.string.thread_more)) { open = menu.isNotEmpty() }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(c.surface)) {
                    menu.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.label, color = if (item.danger) c.spam else c.onSurface, fontSize = 15.sp) },
                            leadingIcon = { Icon(item.icon, contentDescription = null, tint = if (item.danger) c.spam else c.accent) },
                            onClick = {
                                open = false
                                item.onClick()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Under the header of a spam conversation: why it is here, and the way back. */
@Composable
private fun SpamBanner(onNotSpam: () -> Unit) {
    val c = HebTheme.colors
    Row(
        Modifier
            .padding(horizontal = 12.dp)
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.spamTint)
            .border(1.5.dp, c.spam, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Warning, contentDescription = null, tint = c.spam)
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.thread_spam_banner), color = c.onSurface, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        PillButton(stringResource(R.string.spam_not_spam), onClick = onNotSpam)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SelectionBar(
    count: Int,
    allStarred: Boolean,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onStar: () -> Unit,
    onRemind: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    GradientHeader {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderIcon(Icons.Rounded.Close, stringResource(R.string.thread_clear_selection), onClear)
            Text(
                pluralStringResource(R.plurals.thread_selected_count, count, count),
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            HeaderIcon(
                if (allStarred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                stringResource(if (allStarred) R.string.thread_unstar else R.string.thread_star),
                onStar,
            )
            if (onRemind != null) HeaderIcon(Icons.Rounded.Alarm, stringResource(R.string.reminder_title), onRemind)
            HeaderIcon(Icons.Rounded.Delete, stringResource(R.string.thread_delete_selected), onDelete)
            HeaderIcon(Icons.Rounded.SelectAll, stringResource(R.string.common_select_all), onSelectAll)
        }
    }
}

@Composable
private fun DayChip(label: String) {
    val c = HebTheme.colors
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            color = c.onSurfaceMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(c.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    msg: Message,
    ruleColor: Color?,
    selectingText: Boolean,
    previewSelection: TextRange?,
    selectionMode: Boolean,
    selected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onSelectionActive: (Boolean) -> Unit,
    onReminder: () -> Unit,
    onLink: (LinkInfo) -> Unit,
) {
    val c = HebTheme.colors
    // A tap on a failed message retries it, so it goes to the screen rather than to text selection
    val tapToScreen = selectionMode || msg.status == SendStatus.Failed
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) c.accent.copy(alpha = 0.13f) else Color.Transparent)
            // Taps on the text itself are the TextView's; this covers the rest of the row
            .combinedClickable(onClick = { if (tapToScreen) onTap() }, onLongClick = onLongPress)
            .padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (msg.outgoing && !selectionMode) Arrangement.End else Arrangement.Start,
    ) {
        if (selectionMode) {
            Icon(
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = stringResource(if (selected) R.string.thread_message_selected else R.string.thread_message_not_selected),
                tint = if (selected) c.accent else c.onSurfaceMuted,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(10.dp))
            if (msg.outgoing) Spacer(Modifier.weight(1f))
        }
        Column(horizontalAlignment = if (msg.outgoing) Alignment.End else Alignment.Start) {
            Bubble(msg, if (msg.outgoing) null else ruleColor, selectingText, previewSelection, tapToScreen, onTap, onLongPress, onSelectionActive, onLink)
            if (msg.reminder != null && !selectionMode) ReminderChip(msg.reminder, msg.reminderSet, onReminder)
        }
    }
}

@Composable
private fun Bubble(
    msg: Message,
    ruleColor: Color?,
    selectingText: Boolean,
    previewSelection: TextRange?,
    selectionMode: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onSelectionActive: (Boolean) -> Unit,
    onLink: (LinkInfo) -> Unit,
) {
    val c = HebTheme.colors
    val failed = msg.status == SendStatus.Failed
    val links = remember(msg.text) { LinkAnalyzer.findLinks(msg.text) }
    // The corner nearest the sender's edge is the sharp one
    val shape = if (msg.outgoing) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
    } else {
        RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
    }
    val textColor = if (msg.outgoing) Color.White else c.onSurface
    Column(
        Modifier
            .widthIn(max = 290.dp)
            .shadow(if (msg.outgoing || c.isDark) 0.dp else 1.dp, shape)
            .clip(shape)
            .then(if (msg.outgoing) Modifier.background(c.outgoing) else Modifier.background(c.incoming))
            .then(
                when {
                    selectingText -> Modifier.border(2.dp, c.accent, shape)
                    failed -> Modifier.border(2.dp, c.spam, shape)
                    ruleColor != null -> Modifier.border(2.dp, ruleColor, shape)
                    else -> Modifier
                },
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        SelectableMessageText(
            text = msg.text,
            color = textColor,
            previewSelection = previewSelection.takeIf { selectingText },
            selectionMode = selectionMode,
            onTap = onTap,
            onLongPress = onLongPress,
            onSelectionActive = onSelectionActive,
            links = links,
            linkColor = if (msg.outgoing) Color.White else c.accent,
            onLink = onLink,
        )
        Spacer(Modifier.height(2.dp))
        if (msg.status == SendStatus.Done && !msg.starred) {
            Text(msg.time, color = textColor.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.align(Alignment.End))
        } else {
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                if (msg.starred) {
                    Icon(Icons.Rounded.Star, contentDescription = stringResource(R.string.thread_starred), tint = textColor.copy(alpha = 0.85f), modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                }
                when (msg.status) {
                    SendStatus.Sending -> Text(stringResource(R.string.thread_sending), color = textColor.copy(alpha = 0.7f), fontSize = 11.sp)
                    SendStatus.Failed -> Text(stringResource(R.string.thread_failed), color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    SendStatus.Done -> Text(msg.time, color = textColor.copy(alpha = 0.7f), fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * The message text as a native selectable TextView. One tap selects the word under the finger and
 * brings up Android's own handles and Copy / Share / Select all toolbar, inside the bubble itself
 * (Compose's SelectionContainer only starts on a long press). A tap on a link shows where it
 * really leads instead. A long press stays with the row for multi-select, and in multi-select mode
 * (or on a failed message) a tap goes to the screen instead.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
private fun SelectableMessageText(
    text: String,
    color: Color,
    previewSelection: TextRange?,
    selectionMode: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onSelectionActive: (Boolean) -> Unit,
    links: List<LinkInfo>,
    linkColor: Color,
    onLink: (LinkInfo) -> Unit,
) {
    val accent = HebTheme.colors.accent.toArgb()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val lineHeightPx = with(LocalDensity.current) { 23.sp.toPx() }.roundToInt()
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    val selectionActive by rememberUpdatedState(onSelectionActive)
    val multiSelect by rememberUpdatedState(selectionMode)
    val currentLinks by rememberUpdatedState(links)
    val link by rememberUpdatedState(onLink)
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                textSize = 16f
                if (Build.VERSION.SDK_INT >= 28) lineHeight = lineHeightPx
                setTextIsSelectable(true)
                highlightColor = ColorUtils.setAlphaComponent(accent, 0x59)
                if (Build.VERSION.SDK_INT >= 29) {
                    listOfNotNull(textSelectHandle, textSelectHandleLeft, textSelectHandleRight).forEach { it.setTint(accent) }
                }
                val slop = ViewConfiguration.get(context).scaledTouchSlop
                var downX = 0f
                var downY = 0f
                // Set when a plain tap should open selection: the long click it triggers is then the editor's
                var tapSelects = false
                setOnTouchListener { _, e ->
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = e.x
                            downY = e.y
                        }
                        MotionEvent.ACTION_UP -> {
                            val isTap = abs(e.x - downX) < slop && abs(e.y - downY) < slop &&
                                e.eventTime - e.downTime < ViewConfiguration.getLongPressTimeout()
                            if (isTap && multiSelect) {
                                tap()
                                return@setOnTouchListener true
                            }
                            // A tap on a link shows where it really goes, instead of selecting text
                            val tapped = if (isTap && !hasSelection()) linkAt(this, currentLinks, e.x, e.y) else null
                            if (tapped != null) {
                                post { link(tapped) }
                                return@setOnTouchListener true
                            }
                            if (isTap && !hasSelection()) {
                                tapSelects = true
                                post { performLongClick() }
                            }
                        }
                    }
                    false
                }
                setOnLongClickListener {
                    if (tapSelects) {
                        tapSelects = false
                        false
                    } else {
                        longPress()
                        true
                    }
                }
                customSelectionActionModeCallback = object : ActionMode.Callback {
                    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                        selectionActive(true)
                        return true
                    }
                    override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
                    override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = false
                    override fun onDestroyActionMode(mode: ActionMode) = selectionActive(false)
                }
            }
        },
        update = { view ->
            view.setTextColor(color.toArgb())
            view.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            view.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            if (previewSelection != null) {
                view.text = SpannableString(text).apply {
                    setSpan(BackgroundColorSpan(view.highlightColor), previewSelection.start, previewSelection.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    styleLinks(links, linkColor.toArgb())
                }
            } else if (view.text.toString() != text || view.tag != linkColor) {
                view.tag = linkColor
                view.text = if (links.isEmpty()) text else SpannableString(text).apply { styleLinks(links, linkColor.toArgb()) }
            }
        },
    )
}

/** Underlines the links in the bubble's own colour. */
private fun SpannableString.styleLinks(links: List<LinkInfo>, color: Int) {
    for (l in links) {
        val end = (l.range.last + 1).coerceAtMost(length)
        if (l.range.first !in 0 until end) continue
        setSpan(ForegroundColorSpan(color), l.range.first, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(UnderlineSpan(), l.range.first, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(StyleSpan(Typeface.BOLD), l.range.first, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

/** The link under the finger, if any. */
private fun linkAt(view: TextView, links: List<LinkInfo>, x: Float, y: Float): LinkInfo? {
    if (links.isEmpty() || view.layout == null) return null
    val offset = view.getOffsetForPosition(x, y)
    return links.firstOrNull { offset in it.range }
}

@Composable
private fun ReminderChip(text: String, set: Boolean, onClick: () -> Unit) {
    val c = HebTheme.colors
    Row(
        Modifier
            .padding(top = 5.dp)
            .clip(RoundedCornerShape(50))
            .background(c.accent.copy(alpha = if (set) 0.22f else 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (set) Icons.Rounded.AlarmOn else Icons.Rounded.Alarm, contentDescription = null, tint = c.accent, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            stringResource(if (set) R.string.thread_reminder_set else R.string.thread_create_reminder, text),
            color = c.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SelectionActions(count: Int, onCopy: () -> Unit, onShare: () -> Unit) {
    val c = HebTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.surface)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PillButton(stringResource(R.string.thread_copy_together, count), Modifier.weight(1f), icon = Icons.Rounded.ContentCopy, filled = true, onClick = onCopy)
        PillButton(stringResource(R.string.common_share), Modifier.weight(1f), icon = Icons.Rounded.Share, onClick = onShare)
    }
}

/** The text field and send button; the button works (and looks it) only when there is text. */
@Composable
internal fun Composer(draft: String, onDraft: (String) -> Unit, onSend: () -> Unit, canSend: Boolean = draft.isNotBlank()) {
    val c = HebTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(c.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = onDraft,
                    textStyle = TextStyle(color = c.onSurface, fontSize = 16.sp),
                    cursorBrush = SolidColor(c.accent),
                    maxLines = 6,
                    decorationBox = { inner ->
                        if (draft.isEmpty()) Text(stringResource(R.string.thread_draft_hint), color = c.onSurfaceMuted, fontSize = 16.sp)
                        inner()
                    },
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(46.dp)
                    .alpha(if (canSend) 1f else 0.4f)
                    .shadow(if (canSend) 4.dp else 0.dp, CircleShape, spotColor = c.accent)
                    .clip(CircleShape)
                    .background(c.header)
                    .clickable(enabled = canSend, onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(R.string.thread_send), tint = Color.White)
            }
        }
    }
}
