package com.havivon.pigeon.ui.inbox

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.havivon.pigeon.R
import com.havivon.pigeon.data.SensitiveClipboard
import com.havivon.pigeon.domain.model.CategoryTag
import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.CustomFolder
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.rowColor
import com.havivon.pigeon.domain.model.UnreadMessage
import com.havivon.pigeon.ui.components.Avatar
import com.havivon.pigeon.ui.components.CountBadge
import com.havivon.pigeon.ui.components.CollapsingHeaderLayout
import com.havivon.pigeon.ui.components.CollapsingHeaderState
import com.havivon.pigeon.ui.components.EndButtonFit
import com.havivon.pigeon.ui.components.HEADER_TITLE_MAX_SCALE
import com.havivon.pigeon.ui.components.NameLine
import com.havivon.pigeon.ui.components.TrailingOrBelow
import com.havivon.pigeon.ui.components.collapsible
import com.havivon.pigeon.ui.components.endButtonFit
import com.havivon.pigeon.ui.components.leadOnOwnRow
import com.havivon.pigeon.ui.components.rememberCollapsingHeaderState
import com.havivon.pigeon.ui.components.rememberHeaderPadding
import com.havivon.pigeon.ui.components.scaledAtMost
import com.havivon.pigeon.ui.components.GradientHeader
import com.havivon.pigeon.ui.components.HeaderIcon
import com.havivon.pigeon.ui.components.CategoryTagIfColoured
import com.havivon.pigeon.ui.components.RuleTag
import com.havivon.pigeon.ui.components.SectionLabel
import com.havivon.pigeon.ui.components.SpamAvatar
import com.havivon.pigeon.ui.components.listBottomPadding
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
    /** The "Unread" side of the All / Unread toggle under the chips is on: the list shows [unreadMessages] instead of [conversations]. */
    unreadOnly: Boolean = false,
    /** The number on the toggle's "Unread": the unread messages of [selectedFolder]. */
    unreadCount: Int = 0,
    onUnreadOnly: (Boolean) -> Unit = {},
    /** "Mark all read" at the other end of the All / Unread row: asks first; dimmed and inert while [unreadCount] is 0. */
    onMarkAllRead: () -> Unit = {},
    /** The rows of the unread list: the unread messages of [selectedFolder] themselves, newest first. Used instead of [conversations] while the toggle is on. */
    unreadMessages: List<UnreadMessage> = emptyList(),
    onOpenMessage: (UnreadMessage) -> Unit = {},
    onLongPressMessage: (UnreadMessage) -> Unit = {},
    /** Shown instead of the list when there is nothing to list; null shows nothing. */
    emptyState: (@Composable () -> Unit)? = null,
    /** A line under the header (the "viewing only" banner); null for none. */
    banner: (@Composable () -> Unit)? = null,
    /** The conversations are still being read: a quiet loading sign where the list will be. */
    loading: Boolean = false,
    /** The user's own categories: chips after the built-in ones, with [customCounts] as their numbers. */
    customFolders: List<CustomFolder> = emptyList(),
    customCounts: Map<Long, Int> = emptyMap(),
    /** The chosen category of the user's; while one is, no built-in chip is chosen. */
    selectedCustom: Long? = null,
    onCustomFolder: (CustomFolder) -> Unit = {},
    /** The colours the user gave to built-in folders: a dot on their chips. */
    builtInColors: Map<Folder, Long> = emptyMap(),
    /** "Manage categories…" at the end of the category menu. */
    onManageCategories: () -> Unit = {},
) {
    val c = HebTheme.colors
    val ruleById = rules.filter { it.enabled }.associateBy { it.id }
    val nothing = if (unreadOnly) unreadMessages.isEmpty() else conversations.isEmpty()
    val pinned = conversations.filter { it.pinned }
    val header = rememberCollapsingHeaderState()
    val listState = rememberLazyListState()
    // A list shorter than the screen keeps the header whole
    header.canCollapse = { listState.canScrollForward || listState.canScrollBackward }
    // The button is a round pencil while the list is away from its top, and says "New message" at the top
    val atTop by remember(header, listState) { derivedStateOf { header.isExpanded && !listState.canScrollBackward } }
    // Room under the last row for the button (its height grows with the font), so a code or a count is never under it
    var fabHeight by remember { mutableIntStateOf(0) }
    val fabRoom = with(LocalDensity.current) { fabHeight.toDp() } + FAB_MARGIN + 24.dp
    val padding = rememberHeaderPadding(header, bottom = listBottomPadding(maxOf(110.dp, fabRoom)))
    Box(Modifier.fillMaxSize().background(c.background)) {
        // The title, the date and the search scroll away with the list; the category menu and the All / Unread row stay
        CollapsingHeaderLayout(
            header,
            Modifier.fillMaxSize(),
            header = {
                val entries = categoryMenu(folderCounts, customFolders, customCounts, selectedFolder, selectedCustom, builtInColors)
                val onCategory: (CategoryAction) -> Unit = { it.dispatch(onFolder, onCustomFolder, onManageCategories) }
                val categories = CategoryMenuState(entries, onCategory)
                InboxHeader(header, dateLine, categories, unreadOnly, unreadCount, onUnreadOnly, onMarkAllRead, onSettings, onSearch)
            },
        ) { InboxList(listState, padding, conversations, pinned, ruleById, unreadOnly, nothing, loading, emptyState, banner, unreadMessages, onOpen, onLongPress, onOpenMessage, onLongPressMessage) }
        NewMessageButton(
            expanded = atTop,
            onClick = onNewMessage,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(FAB_MARGIN).onSizeChanged { fabHeight = it.height },
        )
    }
}

/** The new-message button's distance from the screen's edges. */
private val FAB_MARGIN = 20.dp

@Composable
private fun InboxList(
    listState: LazyListState,
    padding: PaddingValues,
    conversations: List<Conversation>,
    pinned: List<Conversation>,
    ruleById: Map<Int, ColorRule>,
    unreadMode: Boolean,
    nothing: Boolean,
    loading: Boolean,
    emptyState: (@Composable () -> Unit)?,
    banner: (@Composable () -> Unit)?,
    unreadMessages: List<UnreadMessage>,
    onOpen: (Conversation) -> Unit,
    onLongPress: (Conversation) -> Unit,
    onOpenMessage: (UnreadMessage) -> Unit,
    onLongPressMessage: (UnreadMessage) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = padding) {
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
}

/** Where the list will be, while the conversations are read for the first time. */
@Composable
private fun LoadingConversations() {
    Box(Modifier.fillMaxWidth().padding(top = 72.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), color = HebTheme.colors.accent, strokeWidth = 3.dp)
    }
}

/** The category menu's lines, and what a tap on one does. */
private class CategoryMenuState(val entries: List<CategoryEntry>, val onAction: (CategoryAction) -> Unit)

@Composable
private fun InboxHeader(
    collapse: CollapsingHeaderState,
    dateLine: String,
    categories: CategoryMenuState,
    unreadOnly: Boolean,
    unreadCount: Int,
    onUnreadOnly: (Boolean) -> Unit,
    onMarkAllRead: () -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
) {
    GradientHeader {
        // Scrolls away with the list
        Column(Modifier.collapsible(collapse)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.app_name),
                        color = Color.White,
                        fontSize = 30.sp.scaledAtMost(HEADER_TITLE_MAX_SCALE),
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(dateLine, color = Color.White.copy(alpha = 0.82f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                HeaderIcon(Icons.Rounded.Settings, stringResource(R.string.inbox_settings_description), onSettings)
            }
            Spacer(Modifier.height(12.dp))
            SearchPill(onSearch)
            Spacer(Modifier.height(12.dp))
        }
        // Stays: the chosen category (a menu of them) and the All / Unread row
        UnreadButtons(categories, unreadOnly, unreadCount, onUnreadOnly, onMarkAllRead)
        Spacer(Modifier.height(4.dp))
    }
}

/** The glassy search field that opens the search: one line, cut with "…" when the font is large; it grows in height, never in lines. */
@Composable
private fun SearchPill(onSearch: () -> Unit) {
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
        Text(
            stringResource(R.string.inbox_search_hint),
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The row under the search. At its start: the chosen category, a white pill that opens the menu of categories; then,
 * side by side with a clear gap, every message of it ("All"), or only the unread ones ("Unread", with their number
 * in a badge). At the opposite end, so it is not tapped by mistake: "Mark all read" (the full label if it fits,
 * otherwise a short one, otherwise its icon alone, with the label on a long press). When the font is too large for
 * all of it on one line, the category goes on a line of its own above the rest.
 */
@Composable
private fun UnreadButtons(categories: CategoryMenuState, unreadOnly: Boolean, unreadCount: Int, onChange: (Boolean) -> Unit, onMarkAllRead: () -> Unit) {
    FilterRow(
        lead = { CategoryDropdown(categories.entries, categories.onAction) },
        filters = {
            // Scrolls sideways only in the extreme case where even the icon leaves the two too little room
            Row(Modifier.selectableGroup().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterButton(stringResource(R.string.inbox_filter_all), count = 0, selected = !unreadOnly) { onChange(false) }
                FilterButton(stringResource(R.string.inbox_filter_unread), count = unreadCount, selected = unreadOnly) { onChange(true) }
            }
        },
        markAll = { fit -> MarkAllReadButton(fit, enabled = unreadCount > 0, onClick = onMarkAllRead) },
    )
}

/**
 * [lead] and [filters] at the start and [markAll] at the end, never overlapping: the button in the widest form that
 * fits (see [endButtonFit]); [lead] on a line of its own above the rest when even the icon would not fit beside all
 * of them (see [leadOnOwnRow]).
 */
@Composable
private fun FilterRow(lead: @Composable () -> Unit, filters: @Composable () -> Unit, markAll: @Composable (EndButtonFit) -> Unit) {
    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val width = constraints.maxWidth
        val loose = Constraints(maxWidth = width)
        val gap = 10.dp.roundToPx()
        val leadM = subcompose("lead", lead)
        val leadW = leadM.maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0
        val left = subcompose("filters", filters)
        val filtersW = left.maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0
        val ownRow = leadOnOwnRow(width, leadW, filtersW, MARK_ALL_ICON.roundToPx(), gap)
        val beforeButton = if (ownRow) filtersW else leadW + gap + filtersW
        val room = width - beforeButton - gap
        val fullW = subcompose(EndButtonFit.Full) { markAll(EndButtonFit.Full) }.maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0
        val compactW = subcompose(EndButtonFit.Compact) { markAll(EndButtonFit.Compact) }.maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0
        val fit = endButtonFit(room, fullW, compactW)
        val button = subcompose("button:$fit") { markAll(fit) }.map { it.measure(loose) }
        val buttonW = button.maxOfOrNull { it.width } ?: 0
        val buttonH = button.maxOfOrNull { it.height } ?: 0
        val leadP = leadM.map { it.measure(loose) }
        val leadPW = if (ownRow) 0 else (leadP.maxOfOrNull { it.width } ?: 0) + gap
        // The filters keep what the category and the button leave (all they need, but in the extreme case)
        val filtersP = left.map { it.measure(Constraints(maxWidth = (width - leadPW - buttonW - gap).coerceAtLeast(0))) }
        val leadH = leadP.maxOfOrNull { it.height } ?: 0
        val filtersH = filtersP.maxOfOrNull { it.height } ?: 0
        val rowGap = 8.dp.roundToPx()
        val line = maxOf(filtersH, buttonH, if (ownRow) 0 else leadH)
        val top = if (ownRow) leadH + rowGap else 0
        layout(width, top + line) {
            // placeRelative mirrors in RTL: the category and the filters sit at the start edge, the button at the end edge
            leadP.forEach { it.placeRelative(0, if (ownRow) 0 else (line - it.height) / 2) }
            filtersP.forEach { it.placeRelative(leadPW, top + (line - it.height) / 2) }
            button.forEach { it.placeRelative(width - buttonW, top + (line - buttonH) / 2) }
        }
    }
}

/** The size of the round icon-only "Mark all read". */
private val MARK_ALL_ICON = 38.dp

/**
 * "Mark all read": a glassy outlined pill with a "done all" icon and the full or short label, or (when neither fits)
 * a round button with the icon alone, which says its label on a long press and to TalkBack. Dimmed and inert when
 * there is nothing unread.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun MarkAllReadButton(fit: EndButtonFit, enabled: Boolean, onClick: () -> Unit) {
    val full = stringResource(R.string.inbox_mark_all_read)
    if (fit == EndButtonFit.IconOnly) {
        val tooltip = rememberTooltipState()
        val scope = rememberCoroutineScope()
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(full) } },
            state = tooltip,
            enableUserInput = false,
        ) {
            Box(
                Modifier
                    .alpha(if (enabled) 1f else 0.4f)
                    .size(MARK_ALL_ICON)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape)
                    .combinedClickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClickLabel = full,
                        onLongClick = { scope.launch { tooltip.show() } },
                        onClick = onClick,
                    )
                    .semantics { contentDescription = full },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.DoneAll, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        return
    }
    val shape = RoundedCornerShape(50)
    val label = if (fit == EndButtonFit.Compact) stringResource(R.string.inbox_mark_all_read_short) else full
    Row(
        Modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.55f), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.DoneAll, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun FilterButton(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val c = HebTheme.colors
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.14f))
            .then(if (selected) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = 0.55f), shape))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (!selected) Color.White else c.onWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        if (count > 0) {
            Spacer(Modifier.width(6.dp))
            CountDot(count, selected)
        }
    }
}

/** The small round number on a chip or the Unread button; with a large font (or a long number) it grows into a pill. */
@Composable
internal fun CountDot(count: Int, selected: Boolean) {
    val c = HebTheme.colors
    Box(
        Modifier
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .clip(CircleShape)
            .background(if (selected) c.accent.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.25f))
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$count",
            color = if (!selected) Color.White else c.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedTile(conv: Conversation, rule: ColorRule?, onOpen: (Conversation) -> Unit, onLongPress: (Conversation) -> Unit) {
    val c = HebTheme.colors
    // A matching colour rule frames it; without one, the colour of its category (if it has one)
    val ruleColor = rowColor(rule, conv.category)?.let { Color(it) }
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
        Text(conv.name, color = c.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // Two lines tall even for a short snippet, so the tiles line up; taller with a large font, never clipped
        Text(
            conv.snippet,
            color = c.onSurfaceMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationCard(conv: Conversation, rule: ColorRule?, onOpen: (Conversation) -> Unit, onLongPress: (Conversation) -> Unit) {
    val c = HebTheme.colors
    val ruleColor = rule?.let { Color(it.argb) }
    // The frame: a matching colour rule first, then the colour of its category
    val frame = rowColor(rule, conv.category)?.let { Color(it) }
    val unread = conv.unread > 0
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth()
            .shadow(if (c.isDark) 0.dp else 2.dp, shape, spotColor = c.accent.copy(alpha = 0.25f))
            .clip(shape)
            .background(c.surface)
            .then(if (frame != null) Modifier.border(2.dp, frame, shape) else Modifier)
            .combinedClickable(onClick = { onOpen(conv) }, onLongClick = { onLongPress(conv) })
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (conv.isSpam) SpamAvatar() else Avatar(conv.name, conv.avatarArgb)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            RowNameLine(conv.name, conv.time, unread, rule, ruleColor, conv.category)
            Spacer(Modifier.height(3.dp))
            // The code's copy button (or the unread count) at the end; under the snippet when the font is too large for both
            TrailingOrBelow(
                main = {
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
                    }
                },
                trailing = {
                    when {
                        conv.code != null -> CopyCodeChip(conv.code)
                        unread -> CountBadge(conv.unread)
                    }
                },
            )
        }
    }
}

/**
 * The first line of a row: the name, its rule and category tags, and the time at the end. The name has priority:
 * the time and the tags are shortened first (see [NameLine]).
 */
@Composable
private fun RowNameLine(name: String, time: String, unread: Boolean, rule: ColorRule?, ruleColor: Color?, category: CategoryTag?) {
    val c = HebTheme.colors
    val hasTags = (rule != null && ruleColor != null) || category?.argb != null
    NameLine(
        name = {
            Text(
                name,
                color = c.onSurface,
                fontSize = 16.sp,
                fontWeight = if (unread) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        tags = if (!hasTags) null else ({
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (rule != null && ruleColor != null) {
                    Spacer(Modifier.width(6.dp))
                    RuleTag(rule.label, ruleColor)
                }
                CategoryTagIfColoured(category)
            }
        }),
        time = {
            Text(
                time,
                color = if (unread) c.accent else c.onSurfaceMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
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
    val frame = rowColor(rule, msg.category)?.let { Color(it) }
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth()
            .shadow(if (c.isDark) 0.dp else 2.dp, shape, spotColor = c.accent.copy(alpha = 0.25f))
            .clip(shape)
            .background(c.surface)
            .then(if (frame != null) Modifier.border(2.dp, frame, shape) else Modifier)
            .combinedClickable(onClick = { onOpen(msg) }, onLongClick = { onLongPress(msg) })
            .padding(12.dp),
    ) {
        Avatar(msg.name, msg.avatarArgb)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            RowNameLine(msg.name, msg.time, unread = true, rule, ruleColor, msg.category)
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

/**
 * The code and a copy icon, in a soft pill. Its height follows the text (nothing clipped with a large font), and the
 * code never wraps; Compose widens its touch area to 48dp around it.
 */
@Composable
private fun CopyCodeChip(code: String) {
    val context = LocalContext.current
    val c = HebTheme.colors
    val label = stringResource(R.string.inbox_copy_code)
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(c.accent.copy(alpha = 0.13f))
            .clickable(onClickLabel = label, role = Role.Button) { SensitiveClipboard.copy(context, label, code, R.string.widget_code_copied) }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ContentCopy, contentDescription = label, tint = c.accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(code, color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, softWrap = false)
    }
}

/**
 * "New message": the pencil and the label at the top of the list; while the list is scrolled away from its top
 * ([expanded] false) it narrows to a round pencil, so it covers less of the rows passing under it.
 */
@Composable
private fun NewMessageButton(expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = HebTheme.colors
    val label = stringResource(R.string.inbox_new_message)
    val corner by animateDpAsState(if (expanded) 22.dp else 27.dp, label = "fabCorner")
    val side by animateDpAsState(if (expanded) 20.dp else 15.dp, label = "fabSide")
    val shape = RoundedCornerShape(corner)
    Row(
        modifier
            .shadow(8.dp, shape, spotColor = c.accent)
            .clip(shape)
            .background(c.header)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = side, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Edit, contentDescription = null, tint = Color.White)
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(8.dp))
                Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
