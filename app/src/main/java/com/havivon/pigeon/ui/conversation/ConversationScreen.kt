package com.havivon.pigeon.ui.conversation

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.havivon.pigeon.R
import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.Message
import com.havivon.pigeon.domain.model.ThreadItem
import com.havivon.pigeon.ui.components.Avatar
import com.havivon.pigeon.ui.components.GradientHeader
import com.havivon.pigeon.ui.components.HeaderIcon
import com.havivon.pigeon.ui.components.PillButton
import com.havivon.pigeon.ui.theme.HebTheme

@Composable
fun ConversationScreen(
    conversation: Conversation,
    items: List<ThreadItem>,
    rule: ColorRule?,
    draft: String,
    textSelectionId: Int? = null,
    /** Static highlight for rendering the design without a finger on the screen. */
    previewSelection: TextRange? = null,
    selectedIds: Set<Int> = emptySet(),
    onBack: () -> Unit = {},
    onMessageTap: (Message) -> Unit = {},
    onMessageLongPress: (Message) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onSelectAll: () -> Unit = {},
    onCopySelected: () -> Unit = {},
    onShareSelected: () -> Unit = {},
    onDraft: (String) -> Unit = {},
) {
    val c = HebTheme.colors
    val selecting = selectedIds.isNotEmpty()
    val ruleColor = rule?.takeIf { it.enabled }?.let { Color(it.argb) }
    Column(Modifier.fillMaxSize().background(c.background)) {
        if (selecting) {
            SelectionBar(selectedIds.size, onClearSelection, onSelectAll)
        } else {
            ThreadBar(conversation, onBack)
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            items(items) { item ->
                when (item) {
                    is ThreadItem.Day -> DayChip(item.label)
                    is ThreadItem.Msg -> MessageRow(
                        msg = item.message,
                        ruleColor = ruleColor,
                        selectingText = item.message.id == textSelectionId,
                        previewSelection = previewSelection,
                        selectionMode = selecting,
                        selected = item.message.id in selectedIds,
                        onTap = { onMessageTap(item.message) },
                        onLongPress = { onMessageLongPress(item.message) },
                    )
                }
            }
        }
        if (selecting) {
            SelectionActions(selectedIds.size, onCopySelected, onShareSelected)
        } else {
            Composer(draft, onDraft)
        }
    }
}

@Composable
private fun ThreadBar(conv: Conversation, onBack: () -> Unit) {
    GradientHeader {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), onBack)
            Avatar(conv.name, conv.avatarArgb, size = 42.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(conv.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.thread_phone_subtitle), color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp)
            }
            HeaderIcon(Icons.Rounded.Call, stringResource(R.string.thread_call))
            HeaderIcon(Icons.Rounded.MoreVert, stringResource(R.string.thread_more))
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SelectionBar(count: Int, onClear: () -> Unit, onSelectAll: () -> Unit) {
    GradientHeader {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderIcon(Icons.Rounded.Close, stringResource(R.string.thread_clear_selection), onClear)
            Text(
                pluralStringResource(R.plurals.thread_selected_count, count, count),
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
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
) {
    val c = HebTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) c.accent.copy(alpha = 0.13f) else Color.Transparent)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
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
            if (selectingText) TextActions()
            Bubble(msg, if (msg.outgoing) null else ruleColor, selectingText, previewSelection)
            if (msg.reminder != null && !selectionMode) ReminderChip(msg.reminder)
        }
    }
}

@Composable
private fun Bubble(msg: Message, ruleColor: Color?, selectingText: Boolean, previewSelection: TextRange?) {
    val c = HebTheme.colors
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
                    ruleColor != null -> Modifier.border(2.dp, ruleColor, shape)
                    else -> Modifier
                },
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        val style = TextStyle(color = textColor, fontSize = 16.sp, lineHeight = 23.sp)
        when {
            selectingText && previewSelection != null -> Text(
                buildAnnotatedString {
                    append(msg.text)
                    addStyle(SpanStyle(background = c.accent.copy(alpha = 0.3f)), previewSelection.start, previewSelection.end)
                },
                style = style,
            )
            selectingText -> SelectionContainer { Text(msg.text, style = style) }
            else -> Text(msg.text, style = style)
        }
        Spacer(Modifier.height(2.dp))
        Text(msg.time, color = textColor.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.align(Alignment.End))
    }
}

/** Shown above a bubble that is open for text selection. */
@Composable
private fun TextActions() {
    val c = HebTheme.colors
    Row(
        Modifier
            .padding(bottom = 6.dp)
            .shadow(6.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(if (c.isDark) c.surfaceVariant else Color(0xFF241B3D))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(R.string.common_copy, R.string.common_share, R.string.common_select_all).forEach { label ->
            Text(
                stringResource(label),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { }.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ReminderChip(text: String) {
    val c = HebTheme.colors
    Row(
        Modifier
            .padding(top = 5.dp)
            .clip(RoundedCornerShape(50))
            .background(c.accent.copy(alpha = 0.12f))
            .clickable { }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Alarm, contentDescription = null, tint = c.accent, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(stringResource(R.string.thread_create_reminder, text), color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

@Composable
private fun Composer(draft: String, onDraft: (String) -> Unit) {
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
                    .shadow(4.dp, CircleShape, spotColor = c.accent)
                    .clip(CircleShape)
                    .background(c.header)
                    .clickable { },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(R.string.thread_send), tint = Color.White)
            }
        }
    }
}
