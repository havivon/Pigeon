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
import com.havivon.pigeon.data.sample.SampleSet
import com.havivon.pigeon.domain.model.AppIcon
import com.havivon.pigeon.domain.model.AppLanguage
import com.havivon.pigeon.domain.model.AppSettings
import com.havivon.pigeon.domain.model.AppTheme
import com.havivon.pigeon.domain.model.BlockedKeyword
import com.havivon.pigeon.domain.reminders.QuickReminder
import com.havivon.pigeon.ui.inbox.InboxScreen
import com.havivon.pigeon.ui.keywords.BlockedKeywordsScreen
import com.havivon.pigeon.ui.reminders.ReminderChooser
import com.havivon.pigeon.ui.rules.RuleEditorSheetContent
import com.havivon.pigeon.ui.rules.RuleEditorState
import com.havivon.pigeon.ui.rules.RuleKind
import com.havivon.pigeon.ui.rules.RuleSwatches
import com.havivon.pigeon.ui.rules.RulesScreen
import com.havivon.pigeon.ui.settings.SettingsActions
import com.havivon.pigeon.ui.settings.SettingsEnvironment
import com.havivon.pigeon.ui.settings.SettingsScreen
import com.havivon.pigeon.ui.settings.SettingsUiState
import com.havivon.pigeon.ui.theme.HebSmsTheme
import com.havivon.pigeon.ui.theme.HebTheme
import org.junit.Rule
import org.junit.Test

/** A phone tall enough to draw the whole settings list in one picture. */
private val TallPhone = DeviceConfig.PIXEL_6.copy(screenHeight = 5600)

/** Made-up settings content; nothing here is a real person, number or message. */
private class SettingsSample(val sample: SampleSet, val hebrew: Boolean) {

    val state = SettingsUiState(
        settings = AppSettings(
            language = if (hebrew) AppLanguage.Hebrew else AppLanguage.English,
            theme = AppTheme.System,
            blockScreenshots = true,
            widgetsShowText = false,
        ),
        appIcon = AppIcon.Envelope,
        ruleCount = sample.rules.size,
        keywordCount = 3,
    )

    /** Notifications not allowed yet and exact alarms off, so the rows that ask for them show. */
    val environment = SettingsEnvironment(
        canLock = true,
        contactsAllowed = true,
        notificationsAllowed = false,
        exactAlarmsAllowed = false,
        isDefaultSmsApp = true,
        versionName = "0.1.0",
    )

    val keywords = listOf(
        BlockedKeyword(1, if (hebrew) "הלוואה" else "loan"),
        BlockedKeyword(2, if (hebrew) "זכית" else "prize"),
        BlockedKeyword(3, if (hebrew) "קזינו" else "casino"),
    )

    val newRule = RuleEditorState(
        kind = RuleKind.Keyword,
        text = sample.newRuleWord,
        argb = RuleSwatches[3],
    )

    val editRule = RuleEditorState(
        editingId = 1,
        kind = RuleKind.Sender,
        text = if (hebrew) "הבנק" else "Bank",
        label = if (hebrew) "בנק" else "Bank",
        argb = RuleSwatches[0],
    )

    val reminderTimes = mapOf(
        QuickReminder.InOneHour to (if (hebrew) "13:41" else "1:41 PM"),
        QuickReminder.ThisEvening to (if (hebrew) "היום 20:00" else "Today 8:00 PM"),
        QuickReminder.TomorrowMorning to (if (hebrew) "מחר 09:00" else "Tomorrow 9:00 AM"),
    )
}

/** A bottom sheet as the app shows it, drawn in the same composition (Paparazzi does not draw its window). */
@Composable
private fun Sheet(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.BottomCenter) {
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = HebTheme.colors.surface) {
            Box(Modifier.padding(top = 22.dp)) { content() }
        }
    }
}

/** The settings, the rules editing and the reminder chooser, in Hebrew, right to left. */
class SettingsSnapshots {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = TallPhone.copy(locale = "iw"),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val s = SettingsSample(SampleData.hebrew, hebrew = true)

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) =
        paparazzi.snapshot(name) { HebSmsTheme(darkTheme = dark) { content() } }

    @Test
    fun screens() {
        shot("46_settings") { SettingsScreen(s.state, s.environment, SettingsActions()) }
        shot("47_settings_dark", dark = true) { SettingsScreen(s.state, s.environment, SettingsActions()) }
    }
}

/** The screens of one phone height: rules editing, blocked words and the reminder chooser. */
class EditingSnapshots {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(locale = "iw"),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val s = SettingsSample(SampleData.hebrew, hebrew = true)

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) =
        paparazzi.snapshot(name) { HebSmsTheme(darkTheme = dark) { content() } }

    @Test
    fun screens() {
        shot("49_rules_add_sheet") {
            RulesScreen(s.sample.rules)
            Sheet { RuleEditorSheetContent(s.newRule, {}, {}, {}, {}, {}, onDelete = null) }
        }
        shot("50_rules_reordering") { RulesScreen(s.sample.rules, previewDragging = 2) }
        shot("51_rules_edit_sheet_dark", dark = true) {
            RulesScreen(s.sample.rules)
            Sheet { RuleEditorSheetContent(s.editRule, {}, {}, {}, {}, {}, onDelete = {}) }
        }
        shot("52_blocked_keywords") { BlockedKeywordsScreen(s.keywords, draft = "", duplicate = false) }
        shot("53_blocked_keywords_duplicate", dark = true) { BlockedKeywordsScreen(s.keywords, draft = s.keywords[0].word, duplicate = true) }
        shot("54_blocked_keywords_empty") { BlockedKeywordsScreen(emptyList(), draft = "", duplicate = false) }
        shot("55_reminder_chooser") {
            InboxScreen(s.sample.inbox, s.sample.rules, s.sample.folderCounts, s.sample.dateLine)
            Sheet { ReminderChooser(s.reminderTimes, {}, {}) }
        }
    }
}

/** The same screens in English, left to right. */
class SettingsSnapshotsEnglish {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = TallPhone.copy(locale = "en"),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val s = SettingsSample(SampleData.english, hebrew = false)

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) =
        paparazzi.snapshot(name) { HebSmsTheme(darkTheme = dark) { content() } }

    @Test
    fun screens() {
        shot("48_en_settings") { SettingsScreen(s.state, s.environment, SettingsActions()) }
    }
}

/** The editing screens in English. */
class EditingSnapshotsEnglish {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(locale = "en"),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val s = SettingsSample(SampleData.english, hebrew = false)

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) =
        paparazzi.snapshot(name) { HebSmsTheme(darkTheme = dark) { content() } }

    @Test
    fun screens() {
        shot("56_en_rules_add_sheet") {
            RulesScreen(s.sample.rules)
            Sheet { RuleEditorSheetContent(s.newRule, {}, {}, {}, {}, {}, onDelete = null) }
        }
        shot("57_en_blocked_keywords") { BlockedKeywordsScreen(s.keywords, draft = "", duplicate = false) }
        shot("58_en_reminder_chooser") {
            InboxScreen(s.sample.inbox, s.sample.rules, s.sample.folderCounts, s.sample.dateLine)
            Sheet { ReminderChooser(s.reminderTimes, {}, {}) }
        }
    }
}
