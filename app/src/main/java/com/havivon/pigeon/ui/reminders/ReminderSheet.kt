package com.havivon.pigeon.ui.reminders

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.havivon.pigeon.R
import com.havivon.pigeon.domain.reminders.QuickReminder
import com.havivon.pigeon.domain.reminders.ReminderTimes
import com.havivon.pigeon.ui.common.dateLabels
import com.havivon.pigeon.ui.components.SheetAction
import com.havivon.pigeon.ui.components.SheetTitle
import com.havivon.pigeon.ui.theme.HebTheme
import java.time.ZoneId

/**
 * "Remind me about this": the quick choices (in an hour, this evening, tomorrow morning) and a
 * date-and-time picker, in a bottom sheet. [onChoose] gets the moment the reminder should fire;
 * it is always in the future.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSheet(onChoose: (Long) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val now = remember { System.currentTimeMillis() }
    val labels = remember { dateLabels(context) }
    var picking by remember { mutableStateOf(false) }
    val times = QuickReminder.entries.associateWith { ReminderTimes.at(it, now, zone) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = HebTheme.colors.surface,
    ) {
        ReminderChooser(
            whenTexts = times.mapValues { (_, at) -> labels.reminder(at, now, withTime = true) },
            onQuick = { onChoose(times.getValue(it)) },
            onPickDateTime = { picking = true },
        )
    }

    if (picking) {
        DateTimePicker(now, zone, is24Hour = DateFormat.is24HourFormat(context), onPicked = onChoose, onCancel = { picking = false })
    }
}

/** The sheet's content, apart from its window, so the design snapshots can draw it. */
@Composable
fun ReminderChooser(whenTexts: Map<QuickReminder, String>, onQuick: (QuickReminder) -> Unit, onPickDateTime: () -> Unit) {
    Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp).padding(bottom = 16.dp)) {
        SheetTitle(stringResource(R.string.reminder_title), stringResource(R.string.reminder_subtitle))
        Spacer(Modifier.height(6.dp))
        SheetAction(Icons.Rounded.Schedule, stringResource(R.string.reminder_in_hour), trailing = whenTexts[QuickReminder.InOneHour]) {
            onQuick(QuickReminder.InOneHour)
        }
        SheetAction(Icons.Rounded.NightsStay, stringResource(R.string.reminder_this_evening), trailing = whenTexts[QuickReminder.ThisEvening]) {
            onQuick(QuickReminder.ThisEvening)
        }
        SheetAction(Icons.Rounded.LightMode, stringResource(R.string.reminder_tomorrow_morning), trailing = whenTexts[QuickReminder.TomorrowMorning]) {
            onQuick(QuickReminder.TomorrowMorning)
        }
        SheetAction(Icons.Rounded.CalendarMonth, stringResource(R.string.reminder_pick), onClick = onPickDateTime)
    }
}

/** First the day, then the time of day; nothing in the past can be chosen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePicker(now: Long, zone: ZoneId, is24Hour: Boolean, onPicked: (Long) -> Unit, onCancel: () -> Unit) {
    val c = HebTheme.colors
    val today = remember { ReminderTimes.todayAsPickerMillis(now, zone) }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = today,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= today
        },
    )
    var pickedDay by remember { mutableStateOf<Long?>(null) }

    val day = pickedDay
    if (day == null) {
        DatePickerDialog(
            onDismissRequest = onCancel,
            confirmButton = {
                TextButton(enabled = dateState.selectedDateMillis != null, onClick = { pickedDay = dateState.selectedDateMillis }) {
                    Text(stringResource(R.string.reminder_next))
                }
            },
            dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) } },
            colors = DatePickerDefaults.colors(containerColor = c.surface),
        ) {
            DatePicker(dateState, colors = DatePickerDefaults.colors(containerColor = c.surface))
        }
    } else {
        // The next full hour is a sensible start: always in the future today, and a round time
        val start = ReminderTimes.at(QuickReminder.InOneHour, now, zone)
        val startTime = java.time.Instant.ofEpochMilli(start).atZone(zone)
        val timeState = rememberTimePickerState(initialHour = startTime.hour, initialMinute = 0, is24Hour = is24Hour)
        val at = ReminderTimes.picked(day, timeState.hour, timeState.minute, zone)
        AlertDialog(
            onDismissRequest = onCancel,
            containerColor = c.surface,
            icon = { androidx.compose.material3.Icon(Icons.Rounded.Alarm, contentDescription = null, tint = c.accent) },
            title = { Text(stringResource(R.string.reminder_pick_time)) },
            text = { TimePicker(timeState) },
            confirmButton = {
                TextButton(enabled = at > now, onClick = { onPicked(at) }) { Text(stringResource(R.string.reminder_set)) }
            },
            dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}
