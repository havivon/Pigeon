package com.havivon.pigeon

import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.havivon.pigeon.data.sample.SampleData
import com.havivon.pigeon.ui.inbox.InboxScreen
import com.havivon.pigeon.ui.settings.SettingsActions
import com.havivon.pigeon.ui.settings.SettingsScreen
import com.havivon.pigeon.ui.settings.SkinPickerScreen
import com.havivon.pigeon.ui.theme.HebSmsTheme
import com.havivon.pigeon.ui.theme.Skin
import org.junit.Rule
import org.junit.Test

/**
 * The skin picker (Settings, Appearance, Skin) and the settings row that opens it, in Hebrew (right to left) and in
 * English, drawn as the app draws them: in the chosen skin.
 */
abstract class SkinPickerShots(private val hebrew: Boolean) {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(locale = if (hebrew) "iw" else "en"),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val prefix = if (hebrew) "" else "en_"
    private val st = SettingsSample(if (hebrew) SampleData.hebrew else SampleData.english, hebrew)

    private fun shot(name: String, skin: Skin, dark: Boolean = false, content: @Composable () -> Unit) =
        paparazzi.snapshot(prefix + name) { HebSmsTheme(darkTheme = dark, skin = skin) { content() } }

    @Test
    fun picker() {
        shot("130_skin_picker", Skin.Ocean) { SkinPickerScreen(Skin.Ocean, onPick = {}, onBack = {}) }
        shot("131_skin_picker_dark", Skin.Pomegranate, dark = true) { SkinPickerScreen(Skin.Pomegranate, onPick = {}, onBack = {}) }
        shot("132_settings_skin_row", Skin.Ocean) {
            SettingsScreen(st.state.copy(settings = st.state.settings.copy(skin = Skin.Ocean.id)), st.environment, SettingsActions())
        }
    }
}

class SkinPickerSnapshotsHebrew : SkinPickerShots(hebrew = true)

class SkinPickerSnapshotsEnglish : SkinPickerShots(hebrew = false)

/** The inbox in three skins besides Sunset, light and dark: a regression check on every skinned part of the header and list. */
class SkinInboxSnapshots {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(locale = "iw"),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val sample = SampleData.hebrew
    private val s = NewScreensSample(sample, hebrew = true)

    @Composable
    private fun Inbox() = InboxScreen(
        s.inboxColored, sample.rules, sample.folderCounts, sample.dateLine, hasReminders = true, unreadCount = s.unread.size,
        customFolders = s.coloredCustomFolders, customCounts = mapOf(1L to 3, 2L to 0), builtInColors = s.builtInColors,
    )

    @Test
    fun inbox() {
        listOf(Skin.Ocean, Skin.Pomegranate, Skin.Graphite).forEachIndexed { i, skin ->
            paparazzi.snapshot("${133 + 2 * i}_inbox_${skin.id}") { HebSmsTheme(darkTheme = false, skin = skin) { Inbox() } }
            paparazzi.snapshot("${134 + 2 * i}_inbox_${skin.id}_dark") { HebSmsTheme(darkTheme = true, skin = skin) { Inbox() } }
        }
    }
}
