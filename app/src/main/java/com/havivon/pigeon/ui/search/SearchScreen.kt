package com.havivon.pigeon.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.havivon.pigeon.R
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.ui.components.Avatar
import com.havivon.pigeon.ui.components.EmptyState
import com.havivon.pigeon.ui.components.FixedHeaderLayout
import com.havivon.pigeon.ui.components.GradientHeader
import com.havivon.pigeon.ui.components.HeaderChip
import com.havivon.pigeon.ui.components.HeaderIcon
import com.havivon.pigeon.ui.components.SectionLabel
import com.havivon.pigeon.ui.components.SpamAvatar
import com.havivon.pigeon.ui.theme.HebTheme

/** Search with filter chips: sender, has a link, has a code, the last 7 or 30 days. */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQuery: (String) -> Unit = {},
    onBack: () -> Unit = {},
    onSender: (Long?) -> Unit = {},
    onHasLink: () -> Unit = {},
    onHasCode: () -> Unit = {},
    onStarred: () -> Unit = {},
    onRange: (DateRange) -> Unit = {},
    onOpenConversation: (Long) -> Unit = {},
    /** False in the design snapshots, where there is no keyboard to open. */
    autoFocus: Boolean = true,
) {
    val c = HebTheme.colors
    FixedHeaderLayout(
        Modifier.fillMaxSize().background(c.background).imePadding(),
        header = { SearchHeader(state, onQuery, onBack, onSender, onHasLink, onHasCode, onStarred, onRange, autoFocus) },
    ) { top ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = top, bottom = 24.dp)) {
            when {
                state.query.isBlank() && !state.filters.any -> item {
                    EmptyState(Icons.Rounded.Search, stringResource(R.string.search_start_title), stringResource(R.string.search_start_body))
                }
                state.isEmpty && !state.searching -> item {
                    EmptyState(
                        Icons.Rounded.SearchOff,
                        // Nothing typed: the filters alone found nothing
                        if (state.query.isBlank()) stringResource(R.string.search_none_filtered_title) else stringResource(R.string.search_none_title, state.query.trim()),
                        stringResource(if (state.filters.any) R.string.search_none_filtered else R.string.search_none_body),
                    )
                }
            }
            if (state.conversations.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.search_section_conversations), Icons.Rounded.Forum) }
                items(state.conversations, key = { "c" + it.id }) { ConversationHit(it, state.query) { onOpenConversation(it.id) } }
            }
            if (state.messages.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.search_section_messages), Icons.Rounded.Search) }
                items(state.messages, key = { "m" + it.messageId }) { MessageHit(it, state.query) { onOpenConversation(it.threadId) } }
            }
        }
    }
}

@Composable
private fun SearchHeader(
    state: SearchUiState,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    onSender: (Long?) -> Unit,
    onHasLink: () -> Unit,
    onHasCode: () -> Unit,
    onStarred: () -> Unit,
    onRange: (DateRange) -> Unit,
    autoFocus: Boolean,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
    GradientHeader {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), onBack)
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.2f))
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                    BasicTextField(
                        value = state.query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        decorationBox = { inner ->
                            if (state.query.isEmpty()) Text(stringResource(R.string.inbox_search_hint), color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, maxLines = 1)
                            inner()
                        },
                    )
                }
                if (state.query.isNotEmpty()) HeaderIcon(Icons.Rounded.Close, stringResource(R.string.search_clear)) { onQuery("") }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SenderChip(state, onSender)
            HeaderChip(stringResource(R.string.search_filter_link), state.filters.hasLink, onHasLink)
            HeaderChip(stringResource(R.string.search_filter_code), state.filters.hasCode, onHasCode)
            HeaderChip(stringResource(R.string.search_filter_starred), state.filters.starred, onStarred)
            HeaderChip(stringResource(R.string.search_filter_7_days), state.filters.range == DateRange.Last7Days, {
                onRange(if (state.filters.range == DateRange.Last7Days) DateRange.Any else DateRange.Last7Days)
            })
            HeaderChip(stringResource(R.string.search_filter_30_days), state.filters.range == DateRange.Last30Days, {
                onRange(if (state.filters.range == DateRange.Last30Days) DateRange.Any else DateRange.Last30Days)
            })
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** "Sender ▾": picks one of the senders in the results; when one is picked, a tap clears it. */
@Composable
private fun SenderChip(state: SearchUiState, onSender: (Long?) -> Unit) {
    val c = HebTheme.colors
    var open by remember { mutableStateOf(false) }
    val chosen = state.senderName
    Box {
        HeaderChip(
            chosen ?: stringResource(R.string.search_filter_sender),
            selected = chosen != null,
            onClick = { if (chosen != null) onSender(null) else open = state.senders.isNotEmpty() },
            trailing = if (chosen != null) Icons.Rounded.Close else Icons.Rounded.ArrowDropDown,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(c.surface)) {
            state.senders.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.name, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        open = false
                        onSender(s.threadId)
                    },
                )
            }
        }
    }
}

@Composable
private fun ConversationHit(conv: Conversation, query: String, onClick: () -> Unit) {
    HitCard(onClick) {
        if (conv.isSpam) SpamAvatar(size = 44.dp) else Avatar(conv.name, conv.avatarArgb, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            HitTitle(highlighted(conv.name, query), conv.time)
            Text(conv.snippet, color = HebTheme.colors.onSurfaceMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MessageHit(hit: SearchHit, query: String, onClick: () -> Unit) {
    HitCard(onClick) {
        Avatar(hit.name, hit.avatarArgb, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            HitTitle(AnnotatedString(hit.name), hit.time)
            Text(
                highlighted(excerpt(hit.text, query), query),
                color = HebTheme.colors.onSurface,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HitCard(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val c = HebTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth()
            .shadow(if (c.isDark) 0.dp else 2.dp, shape, spotColor = c.accent.copy(alpha = 0.25f))
            .clip(shape)
            .background(c.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun HitTitle(name: AnnotatedString, time: String) {
    val c = HebTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = c.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(time, color = c.onSurfaceMuted, fontSize = 12.sp)
    }
}

/** The part of a long message around the first match, so the match is in view. */
internal fun excerpt(text: String, query: String, before: Int = 30): String {
    val at = text.indexOf(query.trim(), ignoreCase = true)
    return if (at <= before) text else "…" + text.substring(at - before).trimStart()
}

@Composable
private fun highlighted(text: String, query: String): AnnotatedString {
    val c = HebTheme.colors
    val q = query.trim()
    return buildAnnotatedString {
        append(text)
        if (q.isEmpty()) return@buildAnnotatedString
        var from = 0
        while (true) {
            val at = text.indexOf(q, from, ignoreCase = true)
            if (at < 0) break
            addStyle(SpanStyle(color = c.accent, fontWeight = FontWeight.ExtraBold, background = c.accent.copy(alpha = 0.12f)), at, at + q.length)
            from = at + q.length
        }
    }
}
