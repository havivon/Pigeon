package com.havivon.pigeon.ui.spam

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.havivon.pigeon.ui.components.FixedHeaderLayout
import com.havivon.pigeon.ui.components.GradientHeader
import com.havivon.pigeon.ui.components.HeaderIcon
import com.havivon.pigeon.ui.components.PillButton
import com.havivon.pigeon.ui.components.SpamAvatar
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
) {
    val c = HebTheme.colors
    FixedHeaderLayout(
        Modifier.fillMaxSize().background(c.background),
        header = {
            GradientHeader {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeaderIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), onBack)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.spam_title), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            pluralStringResource(R.plurals.spam_subtitle, groups.size, groups.size),
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 13.sp,
                        )
                    }
                    if (onDeleteAll != null) HeaderIcon(Icons.Rounded.Delete, stringResource(R.string.spam_delete_all), onDeleteAll)
                }
            }
        },
    ) { top ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = top, bottom = 24.dp)) {
            item(key = "info") { InfoCard() }
            if (groups.isEmpty() && emptyState != null) item(key = "empty") { emptyState() }
            items(groups, key = { it.key }) { group ->
                SpamGroupCard(
                    group,
                    expanded = group.key in expanded,
                    messages = messages[group.key].orEmpty(),
                    onToggle = onToggle,
                    onNotSpam = onNotSpam,
                    onDelete = onDelete,
                    onOpen = onOpen,
                    onLink = onLink,
                )
            }
        }
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
        Text(stringResource(R.string.spam_info), color = c.onSurface, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpamGroupCard(
    group: SpamGroup,
    expanded: Boolean,
    messages: List<SpamMessage>,
    onToggle: (SpamGroup) -> Unit,
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
            .background(c.surface)
            .border(2.dp, c.spam, shape)
            .animateContentSize(),
    ) {
        val toggleLabel = stringResource(if (expanded) R.string.spam_hide_messages else R.string.spam_show_messages)
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = toggleLabel) { onToggle(group) }
                .padding(start = 14.dp, end = 10.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpamAvatar(size = 42.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        group.name,
                        color = c.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(group.time, color = c.onSurfaceMuted, fontSize = 12.sp)
                }
                Text(
                    pluralStringResource(R.plurals.spam_group_messages, group.messageCount, group.messageCount),
                    color = c.onSurfaceMuted,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = c.onSurfaceMuted)
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
        if (expanded && messages.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(c.divider))
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                messages.forEach { SpamMessageRow(it, onOpen, onLink) }
            }
        } else {
            Spacer(Modifier.height(12.dp))
        }
        Row(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(stringResource(R.string.spam_not_spam), color = c.spam, onClick = { onNotSpam(group) })
            PillButton(stringResource(R.string.spam_delete), icon = Icons.Rounded.Delete, color = c.spam, onClick = { onDelete(group) })
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
}
