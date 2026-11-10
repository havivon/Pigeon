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
import com.havivon.pigeon.ui.inbox.ConversationActions
import com.havivon.pigeon.ui.search.MessageActions
import com.havivon.pigeon.ui.search.SearchScreen
import com.havivon.pigeon.ui.search.selectionActions
import com.havivon.pigeon.ui.theme.HebSmsTheme
import com.havivon.pigeon.ui.theme.HebTheme
import org.junit.Rule
import org.junit.Test

/** A phone set to the smallest common width: 320dp (a large display size), at the Pixel 6's density. */
private val Narrow = DeviceConfig.PIXEL_6.copy(screenWidth = 840)

/**
 * The search's long-press sheets (a message result and a conversation result), the header with its Select button, and the multi-select bars with their actions, in Hebrew
 * (right to left) and English, normal, dark, and with the font at 1.8 on a 320dp screen.
 */
abstract class SearchSheetShots(config: DeviceConfig, private val hebrew: Boolean, private val suffix: String, private val withDark: Boolean) {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = config.copy(locale = if (hebrew) "iw" else "en"),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val prefix = if (hebrew) "" else "en_"
    private val s = NewScreensSample(if (hebrew) SampleData.hebrew else SampleData.english, hebrew)

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) =
        paparazzi.snapshot(prefix + name + suffix) { HebSmsTheme(darkTheme = dark) { content() } }

    /** A message result with its conversation (one unread), so the sheet offers every action. */
    private val hit = s.searchTagged.messages[0].copy(conversation = s.danny.copy(unread = 1))
    private val conversation = s.searchTagged.conversations[0].copy(unread = 1)

    @Composable
    private fun MessageSheet() = MessageActions(hit, {}, {}, {}, {}, {}, {}, {}, onMove = {}, onMarkRead = {}, onSpam = {}, onLock = {})

    @Composable
    private fun ConversationSheet() = ConversationActions(conversation, {}, {}, {}, {}, {}, {}, onOpen = {}, onSpam = {}, onSelect = {})

    @Composable
    private fun Selecting(messages: Boolean) = SearchScreen(
        s.searchTagged,
        selectionMode = true,
        // Messages and a conversation together, or one conversation alone (then "Remind me" is offered)
        selectedIds = if (messages) setOf(5L, 41L) else emptySet(),
        selectedConversationIds = setOf(2L),
        actions = selectionActions(
            hasMessages = messages, count = if (messages) 3 else 1, allStarred = false, onCopy = {}, onShare = {}, onStar = {}, onMarkRead = {},
            onMove = {}, onSpam = {}, onDelete = {}, onRemind = if (messages) null else ({}),
        ),
        autoFocus = false,
    )

    @Test
    fun messageSheet() {
        shot("150_search_message_sheet") { SearchScreen(s.searchTagged, autoFocus = false); Sheet { MessageSheet() } }
        if (withDark) shot("151_search_message_sheet_dark", dark = true) { SearchScreen(s.searchTagged, autoFocus = false); Sheet { MessageSheet() } }
    }

    @Test
    fun conversationSheet() {
        shot("152_search_conversation_sheet") { SearchScreen(s.searchTagged, autoFocus = false); Sheet { ConversationSheet() } }
        if (withDark) shot("153_search_conversation_sheet_dark", dark = true) { SearchScreen(s.searchTagged, autoFocus = false); Sheet { ConversationSheet() } }
    }

    @Test
    fun selection() {
        shot("154_search_selection_messages") { Selecting(messages = true) }
        shot("155_search_selection_conversation") { Selecting(messages = false) }
        // Just after the header's Select button: check circles on every row, nothing chosen yet
        shot("156_search_selection_empty") { SearchScreen(s.searchTagged, selectionMode = true, autoFocus = false) }
        if (withDark) shot("157_search_selection_dark", dark = true) { Selecting(messages = true) }
    }
}

class SearchSheetSnapshotsHebrew : SearchSheetShots(DeviceConfig.PIXEL_6, hebrew = true, suffix = "", withDark = true)
class SearchSheetSnapshotsEnglish : SearchSheetShots(DeviceConfig.PIXEL_6, hebrew = false, suffix = "", withDark = true)
class SearchSheetSnapshotsFont180Hebrew : SearchSheetShots(Narrow.copy(fontScale = 1.8f), hebrew = true, suffix = "_320_font180", withDark = false)
class SearchSheetSnapshotsFont180English : SearchSheetShots(Narrow.copy(fontScale = 1.8f), hebrew = false, suffix = "_320_font180", withDark = false)

/** A bottom sheet as the app shows it, drawn in the same composition (Paparazzi does not draw its window). */
@Composable
private fun Sheet(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.BottomCenter) {
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = HebTheme.colors.surface) {
            Box(Modifier.padding(top = 22.dp)) { content() }
        }
    }
}
