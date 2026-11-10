package com.havivon.pigeon.ui.bin

import com.havivon.pigeon.ui.components.ButtonsRow
import com.havivon.pigeon.ui.components.HeaderTitle
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.havivon.pigeon.R
import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.ui.common.avatarColor
import com.havivon.pigeon.ui.components.Avatar
import com.havivon.pigeon.ui.components.EmptyState
import com.havivon.pigeon.ui.components.FixedHeaderLayout
import com.havivon.pigeon.ui.components.GradientHeader
import com.havivon.pigeon.ui.components.HeaderChip
import com.havivon.pigeon.ui.components.HeaderIcon
import com.havivon.pigeon.ui.components.PillButton
import com.havivon.pigeon.ui.components.SelectionMark
import com.havivon.pigeon.ui.components.listBottomPadding
import com.havivon.pigeon.ui.theme.HebTheme

/**
 * The recycle bin, opened from the settings: one row per conversation with its deleted messages, when they were
 * deleted and the days they have left. A tap opens a row (a locked chat asks for the fingerprint first); a long
 * press starts selection mode, where the bar on top restores or deletes every chosen row.
 */
@Composable
fun BinScreen(
    groups: List<BinGroup>,
    onBack: () -> Unit = {},
    expanded: Set<String> = emptySet(),
    selected: Set<String> = emptySet(),
    /** False until the bin is read: no empty state meanwhile. */
    loaded: Boolean = true,
    onToggle: (BinGroup) -> Unit = {},
    onSelect: (BinGroup) -> Unit = {},
    onToggleSelected: (BinGroup) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onRestore: (Set<String>) -> Unit = {},
    onDeleteForever: (Set<String>) -> Unit = {},
    /** Null hides the button (nothing to empty). */
    onEmpty: (() -> Unit)? = {},
) {
    val c = HebTheme.colors
    val selecting = selected.isNotEmpty()
    FixedHeaderLayout(
        Modifier.fillMaxSize().background(c.background),
        header = {
            GradientHeader {
                if (selecting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HeaderIcon(Icons.Rounded.Close, stringResource(R.string.thread_clear_selection), onClearSelection)
                        Text(
                            pluralStringResource(R.plurals.common_selected_count, selected.size, selected.size),
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        HeaderChip(stringResource(R.string.bin_restore), selected = false, onClick = { onRestore(selected) })
                        Spacer(Modifier.width(4.dp))
                        HeaderIcon(Icons.Rounded.DeleteForever, stringResource(R.string.bin_delete_forever)) { onDeleteForever(selected) }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HeaderIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), onBack)
                        HeaderTitle(
                            stringResource(R.string.bin_title),
                            pluralStringResource(R.plurals.bin_subtitle, groups.size, groups.size),
                            Modifier.weight(1f),
                            titleSize = 26.sp,
                        )
                        if (onEmpty != null) HeaderIcon(Icons.Rounded.DeleteSweep, stringResource(R.string.bin_empty), onEmpty)
                    }
                }
            }
        },
    ) { top ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = top, bottom = listBottomPadding(24.dp))) {
            item(key = "info") { BinInfoCard() }
            if (groups.isEmpty() && loaded) {
                item(key = "empty") {
                    EmptyState(Icons.Rounded.RestoreFromTrash, stringResource(R.string.bin_empty_title), stringResource(R.string.bin_empty_body))
                }
            }
            items(groups, key = { it.key }) { group ->
                BinGroupCard(
                    group,
                    expanded = group.key in expanded && !group.locked,
                    selecting = selecting,
                    selected = group.key in selected,
                    onClick = if (selecting) onToggleSelected else onToggle,
                    onLongPress = onSelect,
                    onRestore = { onRestore(setOf(group.key)) },
                    onDeleteForever = { onDeleteForever(setOf(group.key)) },
                )
            }
        }
    }
}

@Composable
private fun BinInfoCard() {
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
        Icon(Icons.Rounded.RestoreFromTrash, contentDescription = null, tint = c.accent)
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.bin_info), color = c.onSurface, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BinGroupCard(
    group: BinGroup,
    expanded: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onClick: (BinGroup) -> Unit,
    onLongPress: (BinGroup) -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    val c = HebTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) c.accent.copy(alpha = 0.12f).compositeOver(c.surface) else c.surface)
            .then(if (selected) Modifier.border(2.dp, c.accent, shape) else Modifier)
            .animateContentSize(),
    ) {
        val label = when {
            selecting -> stringResource(if (selected) R.string.common_unselect else R.string.common_select)
            expanded -> stringResource(R.string.spam_hide_messages)
            else -> stringResource(R.string.spam_show_messages)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClickLabel = label,
                    onLongClickLabel = stringResource(R.string.common_select),
                    onLongClick = { onLongPress(group) },
                    onClick = { onClick(group) },
                )
                .padding(start = 14.dp, end = 10.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) SelectionMark(selected, 42.dp) else Avatar(group.name, avatarColor(PhoneNumbers.normalize(group.key)), 42.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(group.name, color = c.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    pluralStringResource(R.plurals.bin_messages, group.messageCount, group.messageCount) + " · " +
                        stringResource(R.string.bin_deleted_on, group.deletedLabel),
                    color = c.onSurfaceMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    pluralStringResource(R.plurals.bin_days_left, group.daysLeft, group.daysLeft),
                    color = if (group.daysLeft <= 3) c.spam else c.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(4.dp))
            if (!selecting) {
                Icon(
                    if (group.locked) Icons.Rounded.Lock else if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = c.onSurfaceMuted,
                )
            }
        }
        if (expanded && !selecting) {
            Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(c.divider))
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                group.messages.forEach { BinMessageRow(it) }
            }
        } else if (group.locked && !selecting) {
            Row(Modifier.padding(horizontal = 14.dp).padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = c.onSurfaceMuted, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.bin_locked), color = c.onSurfaceMuted, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        // While selecting, the bar on top acts on every chosen row
        if (!selecting) {
            ButtonsRow(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                PillButton(stringResource(R.string.bin_restore), icon = Icons.Rounded.Restore, onClick = onRestore)
                PillButton(stringResource(R.string.bin_delete_forever), icon = Icons.Rounded.DeleteForever, color = c.spam, onClick = onDeleteForever)
            }
        }
    }
}

/** One deleted message of an opened row: its text and its time; what the user wrote sits at the end. */
@Composable
private fun BinMessageRow(row: BinRow) {
    val c = HebTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (row.outgoing) c.accent.copy(alpha = 0.12f).compositeOver(c.surface) else c.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalAlignment = if (row.outgoing) Alignment.End else Alignment.Start,
    ) {
        if (row.locked) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = c.onSurfaceMuted, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(row.text, color = c.onSurfaceMuted, fontSize = 14.sp)
            }
        } else {
            Text(row.text, color = c.onSurface, fontSize = 14.sp, lineHeight = 20.sp)
        }
        Spacer(Modifier.height(3.dp))
        Text(row.time, color = c.onSurfaceMuted, fontSize = 11.sp)
    }
}
