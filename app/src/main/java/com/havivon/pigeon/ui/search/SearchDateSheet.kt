package com.havivon.pigeon.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.havivon.pigeon.R
import com.havivon.pigeon.ui.components.SheetTitle
import com.havivon.pigeon.ui.theme.HebTheme
import java.time.LocalDate

/** The sheet's name for a quick choice. */
private fun DatePreset.labelRes(): Int = when (this) {
    DatePreset.Today -> R.string.search_date_today
    DatePreset.Yesterday -> R.string.search_date_yesterday
    DatePreset.Last7Days -> R.string.search_filter_7_days
    DatePreset.Last30Days -> R.string.search_filter_30_days
    DatePreset.ThisMonth -> R.string.search_date_this_month
    DatePreset.LastMonth -> R.string.search_date_last_month
}

/** "Search by date" in a bottom sheet: [DateFilterContent] over the window. [onChoose] gets the days, or null to clear. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateFilterSheet(current: DaySpan?, today: LocalDate, onChoose: (DaySpan?) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = HebTheme.colors.surface,
    ) {
        DateFilterContent(current, today, onChoose)
    }
}

/**
 * The quick choices (today, yesterday, the last 7 and 30 days, this and last month; the one that is on is marked) above
 * Material's range picker. A quick choice applies at once; the picker applies with the button, one day being a range of
 * one. Kept apart from its window so the design snapshots can draw it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DateFilterContent(current: DaySpan?, today: LocalDate, onChoose: (DaySpan?) -> Unit) {
    val c = HebTheme.colors
    val picker = rememberDateRangePickerState(
        initialSelectedStartDateMillis = current?.first?.pickerMillis(),
        initialSelectedEndDateMillis = current?.last?.pickerMillis(),
        initialDisplayedMonthMillis = current?.first?.pickerMillis(),
    )
    val chosen = DaySpan.fromPicker(picker.selectedStartDateMillis, picker.selectedEndDateMillis)
    Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
        SheetTitle(stringResource(R.string.search_date_title), stringResource(R.string.search_date_subtitle))
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DatePreset.entries.forEach { preset ->
                val span = preset.span(today)
                FilterChip(
                    selected = current == span,
                    onClick = { onChoose(span) },
                    label = { Text(stringResource(preset.labelRes()), fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        labelColor = c.onSurface,
                        selectedContainerColor = c.accent.copy(alpha = 0.18f),
                        selectedLabelColor = c.accent,
                    ),
                )
            }
        }
        DateRangePicker(
            picker,
            Modifier.fillMaxWidth().height(480.dp),
            title = null,
            showModeToggle = false,
            colors = DatePickerDefaults.colors(containerColor = c.surface),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (current != null) {
                TextButton(onClick = { onChoose(null) }) { Text(stringResource(R.string.search_date_clear), color = c.accent) }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onChoose(chosen) },
                enabled = chosen != null,
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onWhite),
            ) { Text(stringResource(R.string.search_date_apply)) }
            Spacer(Modifier.width(4.dp))
        }
    }
}
