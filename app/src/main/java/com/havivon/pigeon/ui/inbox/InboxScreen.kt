package com.havivon.pigeon.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.havivon.pigeon.R
import com.havivon.pigeon.data.SensitiveClipboard
import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.UnreadMessage
import com.havivon.pigeon.ui.components.Avatar
import com.havivon.pigeon.ui.components.CountBadge
import com.havivon.pigeon.ui.components.GradientHeader
import com.havivon.pigeon.ui.components.HeaderIcon
import com.havivon.pigeon.ui.components.RuleTag
import com.havivon.pigeon.ui.components.SectionLabel
import com.havivon.pigeon.ui.components.SpamAvatar
import com.havivon.pigeon.ui.theme.HebTheme

@Composable
fun InboxScreen(
    conversations: List<Conversation>,
    rules: List<ColorRule>,
    folderCounts: Map<Folder, Int>,
    dateLine: String,
    selectedFolder: Folder = Folder.All,
    onFolder: (Folder) -> Unit = {},
    onOpen: (Conversation) -> Unit = {},
    onSettings: () -> Unit = {},
    onLongPress: (Conversation) -> Unit = {},
    onSearch: () -> Unit = {},
    onNewMessage: () -> Unit = {},
    /** The rows of the Unread chip: the unread messages themselves, newest first. Used instead of [conversations] while it is chosen. */
    unreadMessages: List<UnreadMessage> = emptyList(),
    onOpenMessage: (UnreadMessage) -> Unit = {},
    onLongPressMessage: (UnreadMessage) -> Unit = {},
    /** Shown instead of the list when there is nothing to list; null shows nothing. */
    emptyState: (@Composable () -> Unit)? = null,
    /** A line under the header (the "viewing only" banner); null for none. */
    banner: (@Composable () -> Unit)? = null,
    /** The conversations are still being read: a quiet loading sign where the list will be. */
    loading: Boolean = false,
) {
    val c = HebTheme.colors
    val ruleById = rules.filter { it.enabled }.associateBy { it.id }
    val unreadMode = selectedFolder == Folder.Unread
    val nothing = if (unreadMode) unreadMessages.isEmpty() else conversations.isEmpty()
    val pinned = conversations.filter { it.pinned }
    Box(Modifier.fillMaxSize().background(c.background)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
            item { InboxHeader(dateLine, selectedFolder, folderCounts, onFolder, onSettings, onSearch) }
            if (banner != null) item { banner() }
            if (nothing && loading) {
                item { LoadingConversations() }
            } else if (nothing && emptyState != null) {
                item { emptyState() }
            }
            if (unreadMode) {
                // No pinned row and no sections: just the messages
                items(unreadMessages, key = { it.messageId }) { UnreadMessageCard(it, ruleById[it.ruleId], onOpenMessage, onLongPressMessage) }
                return@LazyColumn
            }
            if (pinned.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.inbox_pinned), Icons.Rounded.PushPin) }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(pinned, key = { it.id }) { PinnedTile(it, ruleById[it.ruleId], onOpen, onLongPress) }
                    }
                }
            }
            val rest = conversations.filterNot { it.pinned }
            if (rest.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.inbox_all_conversations), Icons.Rounded.Forum) }
            }
            items(rest, key = { it.id }) {
                ConversationCard(it, ruleById[it.ruleId], onOpen, onLongPress)
            }
        }
        NewMessageButton(onNewMessage, Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp))
    }
}

/** Where the list will be, while the conversations are read for the first time. */
@Composable
private fun LoadingConversations() {
    Box(Modifier.fillMaxWidth().padding(top = 72.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), color = HebTheme.colors.accent, strokeWidth = 3.dp)
    }
}

@Composable
private fun InboxHeader(
    dateLine: String,
    selected: Folder,
    counts: Map<Folder, Int>,
    onFolder: (Folder) -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
) {
    GradientHeader {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.inbox_title), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text(dateLine, color = Color.White.copy(alpha = 0.82f), fontSize = 13.sp)
            }
            HeaderIcon(Icons.Rounded.Settings, stringResource(R.string.inbox_settings_description), onSettings)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.2f))
                .clickable(onClick = onSearch)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.inbox_search_hint), color = Color.White.copy(alpha = 0.88f), fontSize = 14.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Folder.entries.forEach { folder ->
                FolderChip(folder, counts[folder] ?: 0, folder == selected) { onFolder(folder) }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun FolderChip(folder: Folder, count: Int, selected: Boolean, onClick: () -> Unit) {
    val c = HebTheme.colors
    val isSpam = folder == Folder.Spam
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(folder.labelRes),
            color = if (!selected) Color.White else if (c.isDark) Color(0xFF3A2596) else c.accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        if (count > 0) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (isSpam) c.spam else if (selected) c.accent.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$count",
                    color = if (isSpam || !selected) Color.White else c.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedTile(conv: Conversation, rule: ColorRule?, onOpen: (Conversation) -> Unit, onLongPress: (Conversation) -> Unit) {
    val c = HebTheme.colors
    val ruleColor = rule?.let { Color(it.argb) }
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .width(170.dp)
            .shadow(if (c.isDark) 0.dp else 3.dp, shape, spotColor = c.accent.copy(alpha = 0.3f))
            .clip(shape)
            .background(c.surface)
            .then(if (ruleColor != null) Modifier.border(2.dp, ruleColor, shape) else Modifier)
            .combinedClickable(onClick = { onOpen(conv) }, onLongClick = { onLongPress(conv) })
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(conv.name, conv.avatarArgb, size = 40.dp)
            Spacer(Modifier.weight(1f))
            if (conv.unread > 0) CountBadge(conv.unread)
        }
        Spacer(Modifier.height(8.dp))
        Text(conv.name, color = c.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(
            conv.snippet,
            color = c.onSurfaceMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(36.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationCard(conv: Conversation, rule: ColorRule?, onOpen: (Conversation) -> Unit, onLongPress: (Conversation) -> Unit) {
    val c = HebTheme.colors
    val ruleColor = rule?.let { Color(it.argb) }
    val unread = conv.unread > 0
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth()
            .shadow(if (c.isDark) 0.dp else 2.dp, shape, spotColor = c.accent.copy(alpha = 0.25f))
            .clip(shape)
            .background(c.surface)
            .then(if (ruleColor != null) Modifier.border(2.dp, ruleColor, shape) else Modifier)
            .combinedClickable(onClick = { onOpen(conv) }, onLongClick = { onLongPress(conv) })
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (conv.isSpam) SpamAvatar() else Avatar(conv.name, conv.avatarArgb)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conv.name,
                    color = c.onSurface,
                    fontSize = 16.sp,
                    fontWeight = if (unread) FontWeight.ExtraBold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (rule != null && ruleColor != null) {
                    Spacer(Modifier.width(6.dp))
                    RuleTag(rule.label, ruleColor)
                }
                Spacer(Modifier.weight(1f))
                Text(conv.time, color = if (unread) c.accent else c.onSurfaceMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (conv.locked) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = c.onSurfaceMuted, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    conv.snippet,
                    color = if (unread) c.onSurface else c.onSurfaceMuted,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                when {
                    conv.code != null -> {
                        Spacer(Modifier.width(8.dp))
                        CopyCodeChip(conv.code)
                    }
                    unread -> {
                        Spacer(Modifier.width(8.dp))
                        CountBadge(conv.unread)
                    }
                }
            }
        }
    }
}

/**
 * One unread message in the Unread list: who and when on the first line, the text below (three lines
 * at most), in the frame of the colour rule that matches it, with a copy button when it has a code.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UnreadMessageCard(msg: UnreadMessage, rule: ColorRule?, onOpen: (UnreadMessage) -> Unit, onLongPress: (UnreadMessage) -> Unit) {
    val c = HebTheme.colors
    val ruleColor = rule?.let { Color(it.argb) }
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth()
            .shadow(if (c.isDark) 0.dp else 2.dp, shape, spotColor = c.accent.copy(alpha = 0.25f))
            .clip(shape)
            .background(c.surface)
            .then(if (ruleColor != null) Modifier.border(2.dp, ruleColor, shape) else Modifier)
            .combinedClickable(onClick = { onOpen(msg) }, onLongClick = { onLongPress(msg) })
            .padding(12.dp),
    ) {
        Avatar(msg.name, msg.avatarArgb)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    msg.name,
                    color = c.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (rule != null && ruleColor != null) {
                    Spacer(Modifier.width(6.dp))
                    RuleTag(rule.label, ruleColor)
                }
                Spacer(Modifier.weight(1f))
                Text(msg.time, color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.Top) {
                if (msg.locked) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = c.onSurfaceMuted, modifier = Modifier.padding(top = 2.dp).size(15.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    msg.text,
                    color = if (msg.locked) c.onSurfaceMuted else c.onSurface,
                    fontSize = 14.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (msg.code != null) {
                Spacer(Modifier.height(8.dp))
                CopyCodeChip(msg.code)
            }
        }
    }
}

@Composable
private fun CopyCodeChip(code: String) {
    val context = LocalContext.current
    val c = HebTheme.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(c.accent.copy(alpha = 0.13f))
            .clickable { SensitiveClipboard.copy(context, context.getString(R.string.inbox_copy_code), code, R.string.widget_code_copied) }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.inbox_copy_code), tint = c.accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(code, color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
private fun NewMessageButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = HebTheme.colors
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .shadow(8.dp, shape, spotColor = c.accent)
            .clip(shape)
            .background(c.header)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Edit, contentDescription = null, tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.inbox_new_message), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
