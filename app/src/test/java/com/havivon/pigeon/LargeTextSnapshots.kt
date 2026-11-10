package com.havivon.pigeon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.havivon.pigeon.data.sample.SampleData
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.ui.conversation.ConversationScreen
import com.havivon.pigeon.ui.folders.FoldersScreen
import com.havivon.pigeon.ui.inbox.CategoryMenuItems
import com.havivon.pigeon.ui.inbox.InboxScreen
import com.havivon.pigeon.ui.inbox.MarkAllReadDialogContent
import com.havivon.pigeon.ui.inbox.categoryMenu
import com.havivon.pigeon.ui.search.SearchScreen
import com.havivon.pigeon.ui.settings.SettingsActions
import com.havivon.pigeon.ui.settings.SettingsScreen
import com.havivon.pigeon.ui.settings.SkinPickerScreen
import com.havivon.pigeon.ui.spam.SpamScreen
import com.havivon.pigeon.ui.theme.HebSmsTheme
import com.havivon.pigeon.ui.theme.Skin
import com.havivon.pigeon.ui.theme.HebTheme
import org.junit.Rule
import org.junit.Test

/** A phone set to the smallest common width: 320dp (a large display size), at the Pixel 6's density. */
private val Narrow320 = DeviceConfig.PIXEL_6.copy(screenWidth = 840)

/**
 * The main screens with a large font (1.3 and 1.8) and on a 320dp-wide screen (with the normal font and with 1.8), in
 * Hebrew and in English: nothing may be clipped, overlap or wrap where it should not (the search on one line, "Mark all
 * read" on the filters' line, codes whole, buttons wrapping instead of being cut).
 */
abstract class LargeTextShots(config: DeviceConfig, private val hebrew: Boolean) {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = config.copy(locale = if (hebrew) "iw" else "en"),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) =
        paparazzi.snapshot(name) { HebSmsTheme(darkTheme = dark) { content() } }

    private val sample = if (hebrew) SampleData.hebrew else SampleData.english
    private val s = NewScreensSample(sample, hebrew)
    private val st = SettingsSample(sample, hebrew)

    @Composable
    private fun Inbox(unread: Boolean = false) = InboxScreen(
        s.inboxColored, sample.rules, if (unread) s.unreadCounts else sample.folderCounts, sample.dateLine,
        unreadOnly = unread, unreadCount = s.unread.size, unreadMessages = if (unread) s.unread else emptyList(),
        customFolders = s.coloredCustomFolders, customCounts = mapOf(1L to 3, 2L to 0), builtInColors = s.builtInColors,
    )

    @Test
    fun screens() {
        shot("inbox_all") { Inbox() }
        shot("inbox_unread_code") { Inbox(unread = true) }
        shot("inbox_category_menu") {
            Inbox()
            MenuOver(categoryMenu(sample.folderCounts, s.coloredCustomFolders, mapOf(1L to 3, 2L to 0), Folder.All, null, s.builtInColors), top = 250.dp)
        }
        shot("mark_all_read_dialog") {
            Inbox()
            Scrim { MarkAllReadDialogContent(s.unread.size, stringResource(R.string.common_folder_all), {}, {}) }
        }
        shot("conversation_selection") {
            ConversationScreen(s.danny, sample.dannyThread, rule = null, draft = "", selectedIds = setOf(4L, 5L, 6L), subtitle = "052-555-0123")
        }
        shot("conversation_composer") {
            ConversationScreen(
                s.danny, s.states, rule = null, subtitle = "052-555-0123",
                draft = if (hebrew) "אני בדרך, אגיע בעוד עשר דקות בערך" else "On my way, I'll be there in about ten minutes",
            )
        }
        shot("spam_grouped") { SpamScreen(s.spamGroups, expanded = s.spamOpened.keys, messages = s.spamOpened) }
        shot("search_results") { SearchScreen(s.searchTagged, autoFocus = false) }
        shot("settings_top") { SettingsScreen(st.state, st.environment, SettingsActions()) }
        shot("categories") { FoldersScreen(st.coloredFolders, builtInColors = st.builtInColors) }
        shot("skin_picker") { SkinPickerScreen(Skin.Sunset, onPick = {}, onBack = {}) }
    }
}

class LargeTextFont130Hebrew : LargeTextShots(DeviceConfig.PIXEL_6.copy(fontScale = 1.3f), hebrew = true)
class LargeTextFont130English : LargeTextShots(DeviceConfig.PIXEL_6.copy(fontScale = 1.3f), hebrew = false)
class LargeTextFont180Hebrew : LargeTextShots(DeviceConfig.PIXEL_6.copy(fontScale = 1.8f), hebrew = true)
class LargeTextFont180English : LargeTextShots(DeviceConfig.PIXEL_6.copy(fontScale = 1.8f), hebrew = false)
class LargeTextNarrow320Hebrew : LargeTextShots(Narrow320, hebrew = true)
class LargeTextNarrow320English : LargeTextShots(Narrow320, hebrew = false)
class LargeTextNarrow320Font180Hebrew : LargeTextShots(Narrow320.copy(fontScale = 1.8f), hebrew = true)
class LargeTextNarrow320Font180English : LargeTextShots(Narrow320.copy(fontScale = 1.8f), hebrew = false)

/** The category menu open under the inbox header (normal font): Hebrew, English and dark. */
class CategoryMenuSnapshots {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(locale = "iw"),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Composable
    private fun InboxWithMenu(hebrew: Boolean) {
        val sample = if (hebrew) SampleData.hebrew else SampleData.english
        val s = NewScreensSample(sample, hebrew)
        val counts = mapOf(1L to 3, 2L to 0)
        InboxScreen(
            s.inboxColored, sample.rules, sample.folderCounts, sample.dateLine, unreadCount = s.unread.size,
            customFolders = s.coloredCustomFolders, customCounts = counts, builtInColors = s.builtInColors,
        )
        MenuOver(categoryMenu(sample.folderCounts, s.coloredCustomFolders, counts, Folder.All, null, s.builtInColors), top = 196.dp)
    }

    @Test
    fun hebrew() {
        paparazzi.snapshot("category_menu") { HebSmsTheme { InboxWithMenu(hebrew = true) } }
        paparazzi.snapshot("category_menu_dark") { HebSmsTheme(darkTheme = true) { InboxWithMenu(hebrew = true) } }
    }

    @Test
    fun english() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_6.copy(locale = "en"))
        paparazzi.snapshot("en_category_menu") { HebSmsTheme { InboxWithMenu(hebrew = false) } }
    }
}

/**
 * The category menu as its popup shows it, drawn in the same composition (Paparazzi does not draw popups): under the
 * dropdown, at the start edge, [top] from the top of the screen.
 */
@Composable
private fun MenuOver(entries: List<com.havivon.pigeon.ui.inbox.CategoryEntry>, top: Dp) {
    Box(Modifier.fillMaxSize().padding(start = 16.dp, top = top), contentAlignment = Alignment.TopStart) {
        Surface(shape = RoundedCornerShape(4.dp), color = HebTheme.colors.surface, shadowElevation = 3.dp) {
            Column(Modifier.width(IntrinsicSize.Max).padding(vertical = 8.dp)) { CategoryMenuItems(entries) {} }
        }
    }
}

@Composable
private fun Scrim(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        content()
    }
}
