package com.havivon.pigeon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.havivon.pigeon.data.sample.SampleData
import com.havivon.pigeon.ui.search.DateFilterContent
import com.havivon.pigeon.ui.search.DaySpan
import com.havivon.pigeon.ui.search.SearchFilters
import com.havivon.pigeon.ui.search.SearchScreen
import com.havivon.pigeon.ui.theme.HebSmsTheme
import com.havivon.pigeon.ui.theme.HebTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

/**
 * The search by date: the date chip once days are chosen (a range and one day, with the year of another year), and the
 * sheet with its quick choices above the range picker, in Hebrew (right to left), English and dark.
 */
abstract class SearchDateShots(private val hebrew: Boolean) {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(locale = if (hebrew) "iw" else "en"),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val prefix = if (hebrew) "" else "en_"
    private val s = NewScreensSample(if (hebrew) SampleData.hebrew else SampleData.english, hebrew)

    /** The day the screens count from, so the shots do not change with the date. */
    private val today = LocalDate.of(2026, 11, 10)

    private fun withDates(dates: DaySpan?) = s.search.copy(filters = SearchFilters(dates = dates))

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) =
        paparazzi.snapshot(prefix + name) { HebSmsTheme(darkTheme = dark) { content() } }

    @Test
    fun chip() {
        val range = DaySpan(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 10))
        shot("140_search_date_range") { SearchScreen(withDates(range), autoFocus = false, today = today) }
        shot("141_search_date_range_dark", dark = true) { SearchScreen(withDates(range), autoFocus = false, today = today) }
        val day = DaySpan(LocalDate.of(2025, 12, 30), LocalDate.of(2025, 12, 30))
        shot("142_search_date_day_other_year") { SearchScreen(withDates(day), autoFocus = false, today = today) }
    }

    @Test
    fun sheet() {
        // A range long past, so the picker opens on a month that is not the real one (no mark on the real today)
        val range = DaySpan(LocalDate.of(2025, 3, 4), LocalDate.of(2025, 3, 11))
        shot("143_search_date_sheet") { Sheet { DateFilterContent(range, today) {} } }
        shot("144_search_date_sheet_dark", dark = true) { Sheet { DateFilterContent(range, today) {} } }
        // A quick choice that is on is marked
        val lastMonth = DaySpan(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        shot("145_search_date_sheet_preset") { Sheet { DateFilterContent(lastMonth, today) {} } }
    }
}

class SearchDateSnapshotsHebrew : SearchDateShots(hebrew = true)

class SearchDateSnapshotsEnglish : SearchDateShots(hebrew = false)

/** A bottom sheet as the app shows it, drawn in the same composition (Paparazzi does not draw its window). */
@Composable
private fun Sheet(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.BottomCenter) {
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = HebTheme.colors.surface) {
            Box(Modifier.padding(top = 22.dp)) { content() }
        }
    }
}
