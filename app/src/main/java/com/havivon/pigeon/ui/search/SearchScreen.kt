package com.havivon.pigeon.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
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
import com.havivon.pigeon.domain.model.CategoryTag
import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.rowColor
import com.havivon.pigeon.ui.components.Avatar
import com.havivon.pigeon.ui.components.CategoryTagChip
import com.havivon.pigeon.ui.components.EmptyState
import com.havivon.pigeon.ui.components.CollapsingHeaderLayout
import com.havivon.pigeon.ui.components.CollapsingHeaderState
import com.havivon.pigeon.ui.components.NameLine
import com.havivon.pigeon.ui.components.collapsible
import com.havivon.pigeon.ui.components.rememberCollapsingHeaderState
import com.havivon.pigeon.ui.components.rememberHeaderPadding
import com.havivon.pigeon.ui.components.GradientHeader
import com.havivon.pigeon.ui.components.HeaderChip
import com.havivon.pigeon.ui.components.HeaderIcon
import com.havivon.pigeon.ui.components.SectionLabel
import com.havivon.pigeon.ui.components.SpamAvatar
import com.havivon.pigeon.ui.components.listBottomPadding
import com.havivon.pigeon.ui.conversation.SelectionActions
import com.havivon.pigeon.ui.conversation.SelectionBar
import com.havivon.pigeon.ui.theme.HebTheme
import java.time.LocalDate

/**
 * Search with filter chips: sender, has a link, has a code, starred, and a date or a range of days (the quick choices and a range picker in a sheet). Every result row carries the tag of
 * its category (in the category's colour, or a matching rule's, else neutral) and is framed by a matching colour rule,
 * else by its category's colour. A long press on a conversation opens its sheet ([onLongPressConversation]); a long
 * press on a message starts a selection ([onMessageLongClick]) that the bars at the top and bottom act on.
 */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQuery: (String) -> Unit = {},
    onBack: () -> Unit = {},
    onSender: (Long?) -> Unit = {},
    onHasLink: () -> Unit = {},
    onHasCode: () -> Unit = {},
    onStarred: () -> Unit = {},
    onDates: (DaySpan?) -> Unit = {},
    onOpenConversation: (Long) -> Unit = {},
    onLongPressConversation: (Conversation) -> Unit = {},
    onMessageClick: (SearchHit) -> Unit = {},
    onMessageLongClick: (SearchHit) -> Unit = {},
    /** The selected message results; a selection replaces the search bar with the selection bar. */
    selectedIds: Set<Long> = emptySet(),
    selectedStarred: Boolean = false,
    onClearSelection: () -> Unit = {},
    onStarSelected: () -> Unit = {},
    /** Offered only for exactly one selected message. */
    onRemindSelected: (() -> Unit)? = null,
    onCopySelected: () -> Unit = {},
    onShareSelected: () -> Unit = {},
    /** False in the design snapshots, where there is no keyboard to open. */
    autoFocus: Boolean = true,
    /** The day the date chip and its quick choices count from; fixed in the design snapshots. */
    today: LocalDate = remember { LocalDate.now() },
) {
    val c = HebTheme.colors
    val selecting = selectedIds.isNotEmpty()
    val ruleById = state.rules.filter { it.enabled }.associateBy { it.id }
    // Kept over a rotation, the picker and all
    var datesOpen by rememberSaveable { mutableStateOf(false) }
    // The filter chips scroll away with the results; the search field stays
    val header = rememberCollapsingHeaderState()
    val listState = rememberLazyListState()
    header.canCollapse = { listState.canScrollForward || listState.canScrollBackward }
    Column(Modifier.fillMaxSize().background(c.background).imePadding()) {
        CollapsingHeaderLayout(
            header,
            Modifier.weight(1f).fillMaxWidth(),
            enabled = !selecting,
            header = {
                if (selecting) {
                    SelectionBar(selectedIds.size, selectedStarred, onClearSelection, onSelectAll = null, onStar = onStarSelected, onRemind = onRemindSelected, onDelete = null)
                } else {
                    SearchHeader(header, state, onQuery, onBack, onSender, onHasLink, onHasCode, onStarred, { datesOpen = true }, onDates, today, autoFocus)
                }
            },
        ) {
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = rememberHeaderPadding(header, bottom = listBottomPadding(24.dp))) {
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
                    items(state.conversations, key = { "c" + it.id }) {
                        ConversationHit(it, ruleById[it.ruleId], state.query, onClick = { onOpenConversation(it.id) }, onLongClick = { onLongPressConversation(it) })
                    }
                }
                if (state.messages.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.search_section_messages), Icons.Rounded.Search) }
                    items(state.messages, key = { "m" + it.messageId }) {
                        MessageHit(
                            it, ruleById[it.ruleId], state.query,
                            selecting = selecting, selected = it.messageId in selectedIds,
                            onClick = { onMessageClick(it) }, onLongClick = { onMessageLongClick(it) },
                        )
                    }
                }
            }
        }
        if (selecting) SelectionActions(selectedIds.size, onCopySelected, onShareSelected)
    }
    if (datesOpen) {
        DateFilterSheet(
            current = state.filters.dates,
            today = today,
            onChoose = {
                datesOpen = false
                onDates(it)
            },
            onDismiss = { datesOpen = false },
        )
    }
}

@Composable
private fun SearchHeader(
    collapse: CollapsingHeaderState,
    state: SearchUiState,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    onSender: (Long?) -> Unit,
    onHasLink: () -> Unit,
    onHasCode: () -> Unit,
    onStarred: () -> Unit,
    onOpenDates: () -> Unit,
    onDates: (DaySpan?) -> Unit,
    today: LocalDate,
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
                            if (state.query.isEmpty()) {
                                Text(stringResource(R.string.inbox_search_hint), color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            inner()
                        },
                    )
                }
                if (state.query.isNotEmpty()) HeaderIcon(Icons.Rounded.Close, stringResource(R.string.search_clear)) { onQuery("") }
            }
        }
        // Scrolls away with the results (slides up under the search field)
        Column(Modifier.collapsible(collapse)) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SenderChip(state, onSender)
            DateChip(state.filters.dates, today, onOpenDates, onClear = { onDates(null) })
            HeaderChip(stringResource(R.string.search_filter_link), state.filters.hasLink, onHasLink)
            HeaderChip(stringResource(R.string.search_filter_code), state.filters.hasCode, onHasCode)
            HeaderChip(stringResource(R.string.search_filter_starred), state.filters.starred, onStarred)
        }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** "Date ▾": opens the date sheet; once days are chosen it shows them (the year only when not this one), with an X that clears them. */
@Composable
private fun DateChip(dates: DaySpan?, today: LocalDate, onOpen: () -> Unit, onClear: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    HeaderChip(
        if (dates == null) stringResource(R.string.search_filter_date) else spanLabel(dates, today, locale),
        selected = dates != null,
        onClick = onOpen,
        trailing = if (dates != null) Icons.Rounded.Close else Icons.Rounded.ArrowDropDown,
        onTrailingClick = if (dates != null) onClear else null,
        trailingDescription = if (dates != null) stringResource(R.string.search_date_clear) else null,
    )
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
private fun ConversationHit(conv: Conversation, rule: ColorRule?, query: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    HitCard(frame = rowColor(rule, conv.category), onClick = onClick, onLongClick = onLongClick) {
        if (conv.isSpam) SpamAvatar(size = 44.dp) else Avatar(conv.name, conv.avatarArgb, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            HitTitle(highlighted(conv.name, query), conv.time, conv.category, rule)
            Text(conv.snippet, color = HebTheme.colors.onSurfaceMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MessageHit(
    hit: SearchHit,
    rule: ColorRule?,
    query: String,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val c = HebTheme.colors
    HitCard(frame = rowColor(rule, hit.category), selected = selected, onClick = onClick, onLongClick = onLongClick) {
        if (selecting) {
            Icon(
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = stringResource(if (selected) R.string.thread_message_selected else R.string.thread_message_not_selected),
                tint = if (selected) c.accent else c.onSurfaceMuted,
            )
            Spacer(Modifier.width(10.dp))
        }
        Avatar(hit.name, hit.avatarArgb, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            HitTitle(AnnotatedString(hit.name), hit.time, hit.category, rule)
            Text(
                highlighted(excerpt(hit.text, query), query),
                color = c.onSurface,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A result row: framed in [frame] (a matching rule's colour, else the category's) when there is one. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HitCard(
    frame: Long?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val c = HebTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth()
            .shadow(if (c.isDark) 0.dp else 2.dp, shape, spotColor = c.accent.copy(alpha = 0.25f))
            .clip(shape)
            .background(c.surface)
            .background(if (selected) c.accent.copy(alpha = 0.13f) else Color.Transparent)
            .then(if (frame != null) Modifier.border(2.dp, Color(frame), shape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** The name, the category tag next to it, and the time. */
@Composable
private fun HitTitle(name: AnnotatedString, time: String, category: CategoryTag?, rule: ColorRule?) {
    val c = HebTheme.colors
    NameLine(
        name = { Text(name, color = c.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        // In the category's colour, else the matching rule's, else neutral (see [tagColor])
        tags = if (category == null) null else ({
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(6.dp))
                CategoryTagChip(category, fallbackArgb = rule?.argb)
            }
        }),
        time = { Text(time, color = c.onSurfaceMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
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
