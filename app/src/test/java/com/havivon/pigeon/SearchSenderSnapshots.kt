package com.havivon.pigeon

import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.havivon.pigeon.data.sample.SampleData
import com.havivon.pigeon.domain.senders.Sender
import com.havivon.pigeon.ui.search.SearchFilters
import com.havivon.pigeon.ui.search.SearchScreen
import com.havivon.pigeon.ui.search.SenderFilter
import com.havivon.pigeon.ui.search.SenderSearchContent
import com.havivon.pigeon.ui.search.SenderSuggestion
import com.havivon.pigeon.ui.theme.HebSmsTheme
import org.junit.Rule
import org.junit.Test

/** A phone set to the smallest common width: 320dp (a large display size), at the Pixel 6's density. */
private val Narrow320 = DeviceConfig.PIXEL_6.copy(screenWidth = 840)

/**
 * The sender search the "Sender" chip opens: empty (the newest senders) and with a typed text, and the chip once a sender is
 * chosen (a name, and a long number cut short), in Hebrew (right to left), English, dark, and with the font at 1.8 on a 320dp screen.
 */
abstract class SearchSenderShots(config: DeviceConfig, private val hebrew: Boolean, private val suffix: String, private val withDark: Boolean) {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = config.copy(locale = if (hebrew) "iw" else "en"),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val prefix = if (hebrew) "" else "en_"
    private val s = NewScreensSample(if (hebrew) SampleData.hebrew else SampleData.english, hebrew)

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) =
        paparazzi.snapshot(prefix + name + suffix) { HebSmsTheme(darkTheme = dark) { content() } }

    private fun suggestion(key: String, name: String?, address: String, conversations: Int, time: String, argb: Long): SenderSuggestion {
        val sender = Sender(key, address, name, (1L..conversations.toLong()).toSet(), 0)
        return SenderSuggestion(sender, name ?: address, if (name != null) address else null, conversations, time, argb)
    }

    private val dana = suggestion("525550101", if (hebrew) "דנה לוי" else "Dana Levi", "052-555-0101", 1, if (hebrew) "יום ג׳" else "Tue", 0xFF2F80EDL)
    private val danny = suggestion("525550100", s.danny.name, "+972 52-555-0100", 3, if (hebrew) "12:38" else "12:38 PM", 0xFF1E9E6AL)
    private val bank = suggestion("leumi", null, "Leumi", 1, if (hebrew) "אתמול" else "Yesterday", 0xFF5B3FD9L)
    private val harel = suggestion("harel", null, "HAREL", 2, if (hebrew) "3 אוק׳" else "Oct 3", 0xFFE8B10CL)
    private val numberOnly = suggestion("541112222", null, "054-111-2222", 1, if (hebrew) "1 ספט׳" else "Sep 1", 0xFFD9455FL)
    private val recent = listOf(danny, bank, dana, harel, numberOnly)

    @Test
    fun search() {
        shot("160_search_sender_empty") { SenderSearchContent("", recent, {}, {}, {}, autoFocus = false) }
        val typed = if (hebrew) "דנ" else "Dan"
        shot("161_search_sender_typed") { SenderSearchContent(typed, listOf(dana, danny), {}, {}, {}, autoFocus = false) }
        shot("162_search_sender_none") { SenderSearchContent("zzz", emptyList(), {}, {}, {}, autoFocus = false) }
        if (withDark) {
            shot("163_search_sender_empty_dark", dark = true) { SenderSearchContent("", recent, {}, {}, {}, autoFocus = false) }
            shot("164_search_sender_typed_dark", dark = true) { SenderSearchContent(typed, listOf(dana, danny), {}, {}, {}, autoFocus = false) }
        }
    }

    @Test
    fun chip() {
        val chosen = SearchFilters(sender = SenderFilter("525550100", s.danny.name, setOf(1L, 2L, 3L)))
        shot("165_search_sender_chip") { SearchScreen(s.search.copy(filters = chosen), autoFocus = false) }
        val longName = SearchFilters(sender = SenderFilter("x", "Dana Levi-Rosenberg +972 52-555-0101", setOf(1L)))
        shot("166_search_sender_chip_long") { SearchScreen(s.search.copy(filters = longName), autoFocus = false) }
        if (withDark) shot("167_search_sender_chip_dark", dark = true) { SearchScreen(s.search.copy(filters = chosen), autoFocus = false) }
    }
}

class SearchSenderSnapshotsHebrew : SearchSenderShots(DeviceConfig.PIXEL_6, hebrew = true, suffix = "", withDark = true)
class SearchSenderSnapshotsEnglish : SearchSenderShots(DeviceConfig.PIXEL_6, hebrew = false, suffix = "", withDark = true)
class SearchSenderSnapshotsFont180Hebrew : SearchSenderShots(Narrow320.copy(fontScale = 1.8f), hebrew = true, suffix = "_320_font180", withDark = false)
class SearchSenderSnapshotsFont180English : SearchSenderShots(Narrow320.copy(fontScale = 1.8f), hebrew = false, suffix = "_320_font180", withDark = false)
