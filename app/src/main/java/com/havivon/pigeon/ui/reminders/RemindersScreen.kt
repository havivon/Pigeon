package com.havivon.pigeon.ui.reminders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.havivon.pigeon.R
import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.ui.common.avatarColor
import com.havivon.pigeon.ui.common.ltr
import com.havivon.pigeon.ui.components.Avatar
import com.havivon.pigeon.ui.components.ButtonsRow
import com.havivon.pigeon.ui.components.EmptyState
import com.havivon.pigeon.ui.components.FixedHeaderLayout
import com.havivon.pigeon.ui.components.GradientHeader
import com.havivon.pigeon.ui.components.HeaderIcon
import com.havivon.pigeon.ui.components.HeaderTitle
import com.havivon.pigeon.ui.components.PillButton
import com.havivon.pigeon.ui.components.SectionLabel
import com.havivon.pigeon.ui.components.listBottomPadding
import com.havivon.pigeon.ui.theme.HebTheme

/**
 * The reminders: the ones still ahead (soonest first, each with "Change time" and "Cancel reminder") and, under
 * "Past", those of the last 30 days that already went off. A tap on a row opens the conversation at its message.
 */
@Composable
fun RemindersScreen(
    upcoming: List<ReminderRow>,
    past: List<ReminderRow>,
    onBack: () -> Unit = {},
    /** False until the reminders are read: no empty state meanwhile. */
    loaded: Boolean = true,
    onOpen: (ReminderRow) -> Unit = {},
    onChangeTime: (ReminderRow) -> Unit = {},
    onCancel: (ReminderRow) -> Unit = {},
) {
    val c = HebTheme.colors
    FixedHeaderLayout(
        Modifier.fillMaxSize().background(c.background),
        header = {
            GradientHeader {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeaderIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), onBack)
                    HeaderTitle(
                        stringResource(R.string.reminders_title),
                        if (upcoming.isEmpty()) stringResource(R.string.reminders_none_upcoming)
                        else pluralStringResource(R.plurals.reminders_upcoming_count, upcoming.size, upcoming.size),
                        Modifier.weight(1f),
                        titleSize = 26.sp,
                    )
                }
            }
        },
    ) { top ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = top, bottom = listBottomPadding(24.dp))) {
            if (upcoming.isEmpty() && past.isEmpty() && loaded) {
                item(key = "empty") {
                    EmptyState(Icons.Rounded.Alarm, stringResource(R.string.reminders_empty_title), stringResource(R.string.reminders_empty_body))
                }
            }
            if (upcoming.isNotEmpty()) {
                item(key = "upcoming") { SectionLabel(stringResource(R.string.reminders_section_upcoming), Icons.Rounded.Schedule) }
                items(upcoming, key = { it.id }) { row ->
                    ReminderCard(row, past = false, onOpen = { onOpen(row) }, onChangeTime = { onChangeTime(row) }, onCancel = { onCancel(row) })
                }
            }
            if (past.isNotEmpty()) {
                item(key = "past") { SectionLabel(stringResource(R.string.reminders_section_past), Icons.Rounded.History) }
                items(past, key = { "past-${it.id}" }) { row ->
                    ReminderCard(row, past = true, onOpen = { onOpen(row) }, onChangeTime = {}, onCancel = {})
                }
            }
        }
    }
}

@Composable
private fun ReminderCard(row: ReminderRow, past: Boolean, onOpen: () -> Unit, onChangeTime: () -> Unit, onCancel: () -> Unit) {
    val c = HebTheme.colors
    val shape = RoundedCornerShape(20.dp)
    val sender = row.sender
    val title = when {
        row.locked -> stringResource(R.string.reminders_locked)
        sender != null -> ltr(sender)
        else -> stringResource(R.string.reminder_notification_title)
    }
    val description = stringResource(R.string.reminders_open_description, title, row.whenLabel)
    Column(
        Modifier
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .semantics { contentDescription = description }
                .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = if (past) 14.dp else 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            when {
                row.locked -> IconCircle(Icons.Rounded.Lock)
                sender != null -> Avatar(sender, avatarColor(PhoneNumbers.normalize(sender)), 42.dp)
                else -> IconCircle(Icons.Rounded.Alarm)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = c.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (row.text != null) {
                    Text(row.text, color = c.onSurfaceMuted, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (past) Icons.Rounded.History else Icons.Rounded.Alarm,
                        contentDescription = null,
                        tint = if (past) c.onSurfaceMuted else c.accent,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        row.whenLabel,
                        color = if (past) c.onSurfaceMuted else c.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (!past) {
            ButtonsRow(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                PillButton(stringResource(R.string.reminders_change_time), icon = Icons.Rounded.Schedule, onClick = onChangeTime)
                PillButton(stringResource(R.string.reminders_cancel), icon = Icons.Rounded.Close, color = c.spam, onClick = onCancel)
            }
        }
    }
}

/** A soft round icon standing in for an avatar (a locked chat, a conversation that is gone). */
@Composable
private fun IconCircle(icon: ImageVector) {
    val c = HebTheme.colors
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(22.dp))
    }
}
