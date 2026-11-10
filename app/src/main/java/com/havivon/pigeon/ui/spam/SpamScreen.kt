package com.havivon.pigeon.ui.spam

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.havivon.pigeon.R
import com.havivon.pigeon.domain.analysis.LinkInfo
import com.havivon.pigeon.domain.model.SpamReason
import com.havivon.pigeon.ui.components.EmptyState
import com.havivon.pigeon.ui.components.ButtonsRow
import com.havivon.pigeon.ui.components.CollapsingHeaderLayout
import com.havivon.pigeon.ui.components.HeaderTitle
import com.havivon.pigeon.ui.components.NameLine
import com.havivon.pigeon.ui.components.collapsible
import com.havivon.pigeon.ui.components.rememberCollapsingHeaderState
import com.havivon.pigeon.ui.components.rememberHeaderPadding
import com.havivon.pigeon.ui.components.GradientHeader
import com.havivon.pigeon.ui.components.HeaderChip
import com.havivon.pigeon.ui.components.HeaderIcon
import com.havivon.pigeon.ui.components.HeaderSearchField
import com.havivon.pigeon.ui.components.PillButton
import com.havivon.pigeon.ui.components.SelectionMark
import com.havivon.pigeon.ui.components.SpamAvatar
import com.havivon.pigeon.ui.components.listBottomPadding
import com.havivon.pigeon.ui.theme.HebTheme

/**
 * The Spam folder: one row per sender, framed in red, with the red warning sign and red buttons; everything
 * else in the ordinary colours. A tap opens the row to show the sender's messages, newest first, with their
 * links (a tap on one shows where it goes, as in a conversation).
 */
@Composable
fun SpamScreen(
    groups: List<SpamGroup>,
    onBack: () -> Unit = {},
    /** The rows opened to show their messages, by [SpamGroup.key]. */
    expanded: Set<String> = emptySet(),
    /** The messages of each opened row, newest first. */
    messages: Map<String, List<SpamMessage>> = emptyMap(),
    onToggle: (SpamGroup) -> Unit = {},
    onNotSpam: (SpamGroup) -> Unit = {},
    onDelete: (SpamGroup) -> Unit = {},
    /** A message tapped: its conversation, at that message (null for a locked chat's line). */
    onOpen: (threadId: Long, messageId: Long?) -> Unit = { _, _ -> },
    onLink: (LinkInfo) -> Unit = {},
    /** Null hides the button (nothing to delete). */
    onDeleteAll: (() -> Unit)? = {},
    /** Shown under the info card when there is no spam; null shows nothing. */
    emptyState: (@Composable () -> Unit)? = null,
    /** The search field is open: [groups] are the senders matching [query]. */
    searching: Boolean = false,
    query: String = "",
    onSearch: () -> Unit = {},
    onQuery: (String) -> Unit = {},
    onCloseSearch: () -> Unit = {},
    /** False in the design snapshots, where there is no keyboard to open. */
    autoFocus: Boolean = true,
    /** The senders chosen in selection mode, by [SpamGroup.key]; empty when not selecting. */
    selected: Set<String> = emptySet(),
    /** A long press on a sender: selection mode starts with it. */
    onSelect: (SpamGroup) -> Unit = {},
    /** A tap on a sender while selecting. */
    onToggleSelected: (SpamGroup) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onNotSpamSelected: () -> Unit = {},
) {
    val c = HebTheme.colors
    val selecting = selected.isNotEmpty()
    // While searching, the title scrolls away with the results and the search field stays
    val collapsing = searching && !selecting
    val header = rememberCollapsingHeaderState()
    val listState = rememberLazyListState()
    header.canCollapse = { listState.canScrollForward || listState.canScrollBackward }
    CollapsingHeaderLayout(
        header,
        Modifier.fillMaxSize().background(c.background),
        enabled = collapsing,
        header = {
            GradientHeader {
                if (selecting) {
                    SpamSelectionBar(selected.size, onClearSelection, onNotSpamSelected, onDeleteSelected)
                } else {
                    Column(if (collapsing) Modifier.collapsible(header) else Modifier) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HeaderIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), onBack)
                            HeaderTitle(
                                stringResource(R.string.spam_title),
                                pluralStringResource(R.plurals.spam_subtitle, groups.size, groups.size),
                                Modifier.weight(1f),
                                titleSize = 26.sp,
                            )
                            if (!searching) HeaderIcon(Icons.Rounded.Search, stringResource(R.string.spam_search), onSearch)
                            if (onDeleteAll != null) HeaderIcon(Icons.Rounded.Delete, stringResource(R.string.spam_delete_all), onDeleteAll)
                        }
                        if (searching) Spacer(Modifier.height(10.dp))
                    }
                    if (searching) HeaderSearchField(query, stringResource(R.string.spam_search_hint), onQuery, onCloseSearch, autoFocus)
                }
            }
        },
    ) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = rememberHeaderPadding(header, bottom = listBottomPadding(24.dp))) {
            if (searching) {
                item(key = "top-space") { Spacer(Modifier.height(8.dp)) }
            } else {
                item(key = "info") { InfoCard() }
            }
            if (groups.isEmpty() && searching && query.isNotBlank()) {
                item(key = "no-results") {
                    EmptyState(Icons.Rounded.SearchOff, stringResource(R.string.spam_search_empty_title), stringResource(R.string.spam_search_empty_body))
                }
            } else if (groups.isEmpty() && emptyState != null && !searching) {
                item(key = "empty") { emptyState() }
            }
            items(groups, key = { it.key }) { group ->
                SpamGroupCard(
                    group,
                    expanded = group.key in expanded,
                    messages = messages[group.key].orEmpty(),
                    onToggle = if (selecting) onToggleSelected else onToggle,
                    onLongPress = onSelect,
                    selecting = selecting,
                    selected = group.key in selected,
                    onNotSpam = onNotSpam,
                    onDelete = onDelete,
                    onOpen = onOpen,
                    onLink = onLink,
                )
            }
        }
    }
}

/** The header in selection mode: X to leave it, the number chosen, and the actions on all of them. */
@Composable
private fun SpamSelectionBar(count: Int, onClose: () -> Unit, onNotSpam: () -> Unit, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HeaderIcon(Icons.Rounded.Close, stringResource(R.string.thread_clear_selection), onClose)
        Text(
            pluralStringResource(R.plurals.common_selected_count, count, count),
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        HeaderChip(stringResource(R.string.spam_not_spam), selected = false, onClick = onNotSpam)
        Spacer(Modifier.width(4.dp))
        HeaderIcon(Icons.Rounded.Delete, stringResource(R.string.spam_delete), onDelete)
    }
}

@Composable
private fun InfoCard() {
    val c = HebTheme.colors
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Shield, contentDescription = null, tint = c.accent)
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.spam_info), color = c.onSurface, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun SpamGroupCard(
    group: SpamGroup,
    expanded: Boolean,
    messages: List<SpamMessage>,
    onToggle: (SpamGroup) -> Unit,
    onLongPress: (SpamGroup) -> Unit,
    selecting: Boolean,
    selected: Boolean,
    onNotSpam: (SpamGroup) -> Unit,
    onDelete: (SpamGroup) -> Unit,
    onOpen: (Long, Long?) -> Unit,
    onLink: (LinkInfo) -> Unit,
) {
    val c = HebTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) c.accent.copy(alpha = 0.12f).compositeOver(c.surface) else c.surface)
            .border(if (selected) 3.dp else 2.dp, if (selected) c.accent else c.spam, shape)
            .animateContentSize(),
    ) {
        val toggleLabel = when {
            selecting -> stringResource(if (selected) R.string.common_unselect else R.string.common_select)
            expanded -> stringResource(R.string.spam_hide_messages)
            else -> stringResource(R.string.spam_show_messages)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClickLabel = toggleLabel,
                    onLongClickLabel = stringResource(R.string.common_select),
                    onLongClick = { onLongPress(group) },
                    onClick = { onToggle(group) },
                )
                .padding(start = 14.dp, end = 10.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) SelectionMark(selected, 42.dp) else SpamAvatar(size = 42.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                NameLine(
                    name = { Text(group.name, color = c.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    time = { Text(group.time, color = c.onSurfaceMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
                Text(
                    pluralStringResource(R.plurals.spam_group_messages, group.messageCount, group.messageCount),
                    color = c.onSurfaceMuted,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.width(4.dp))
            if (!selecting) Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = c.onSurfaceMuted)
        }
        if (group.reasons.isNotEmpty()) {
            FlowRow(
                Modifier.padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                group.reasons.forEach { reason ->
                    Text(
                        reason.label(),
                        color = c.onSurfaceMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(c.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
        }
        if (expanded && messages.isNotEmpty() && !selecting) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(c.divider))
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                messages.forEach { SpamMessageRow(it, onOpen, onLink) }
            }
        } else {
            Spacer(Modifier.height(12.dp))
        }
        // While selecting, the bar on top acts on every chosen sender
        if (!selecting) {
            ButtonsRow(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                PillButton(stringResource(R.string.spam_not_spam), color = c.spam, onClick = { onNotSpam(group) })
                PillButton(stringResource(R.string.spam_delete), icon = Icons.Rounded.Delete, color = c.spam, onClick = { onDelete(group) })
            }
        }
    }
}

/** One message of an opened row: its text, links underlined in the text's own colour, and its time. */
@Composable
private fun SpamMessageRow(msg: SpamMessage, onOpen: (Long, Long?) -> Unit, onLink: (LinkInfo) -> Unit) {
    val c = HebTheme.colors
    val openLabel = stringResource(R.string.spam_open_conversation)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surfaceVariant)
            .then(if (msg.links.isEmpty()) Modifier.clickable(onClickLabel = openLabel) { onOpen(msg.threadId, msg.id.takeUnless { msg.locked }) } else Modifier)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        if (msg.locked) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = c.onSurfaceMuted, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(msg.text, color = c.onSurfaceMuted, fontSize = 14.sp)
            }
        } else if (msg.links.isEmpty()) {
            Text(msg.text, color = c.onSurface, fontSize = 14.sp, lineHeight = 20.sp)
        } else {
            val text = remember(msg.text, msg.links, c.onSurface) {
                buildAnnotatedString {
                    append(msg.text)
                    msg.links.forEachIndexed { i, link ->
                        addStyle(SpanStyle(fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline), link.range.first, link.range.last + 1)
                        addStringAnnotation(LINK_TAG, i.toString(), link.range.first, link.range.last + 1)
                    }
                }
            }
            ClickableText(
                text,
                style = TextStyle(color = c.onSurface, fontSize = 14.sp, lineHeight = 20.sp),
                onClick = { offset ->
                    val hit = text.getStringAnnotations(LINK_TAG, offset, offset).firstOrNull()?.item?.toIntOrNull()
                    if (hit != null) onLink(msg.links[hit]) else onOpen(msg.threadId, msg.id)
                },
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(msg.time, color = c.onSurfaceMuted, fontSize = 11.sp)
    }
}

private const val LINK_TAG = "link"

@Composable
private fun SpamReason.label(): String = when (this) {
    SpamReason.UnknownSender -> stringResource(R.string.spam_reason_unknown_sender)
    SpamReason.ShortLink -> stringResource(R.string.spam_reason_short_link)
    SpamReason.LookalikeDomain -> stringResource(R.string.spam_reason_lookalike_domain)
    is SpamReason.Keyword -> word
    is SpamReason.BlockedKeyword -> word
    SpamReason.ManyLinks -> stringResource(R.string.spam_reason_many_links)
    SpamReason.BrandSender -> stringResource(R.string.spam_reason_brand_sender)
    SpamReason.MarkedByUser -> stringResource(R.string.spam_reason_marked_by_user)
    SpamReason.Poll -> stringResource(R.string.spam_reason_poll)
    SpamReason.MoneyBait -> stringResource(R.string.spam_reason_money_bait)
    SpamReason.GovImpersonation -> stringResource(R.string.spam_reason_gov_impersonation)
    SpamReason.Investment -> stringResource(R.string.spam_reason_investment)
    SpamReason.AsksForDetails -> stringResource(R.string.spam_reason_asks_for_details)
    SpamReason.ForeignNumber -> stringResource(R.string.spam_reason_foreign_number)
    SpamReason.ContactElsewhere -> stringResource(R.string.spam_reason_contact_elsewhere)
    SpamReason.MoneyAmount -> stringResource(R.string.spam_reason_money_amount)
    SpamReason.AllCaps -> stringResource(R.string.spam_reason_all_caps)
    SpamReason.BlockedOnPhone -> stringResource(R.string.spam_reason_blocked_on_phone)
}
