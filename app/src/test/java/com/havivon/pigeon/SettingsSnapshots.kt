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
import com.havivon.pigeon.domain.backup.BackupSummary
import com.havivon.pigeon.domain.model.AppIcon
import com.havivon.pigeon.domain.model.AppLanguage
import com.havivon.pigeon.domain.model.AppSettings
import com.havivon.pigeon.domain.model.AppTheme
import com.havivon.pigeon.domain.model.BlockedKeyword
import com.havivon.pigeon.domain.model.CustomFolder
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.SmsAccess
import com.havivon.pigeon.domain.reminders.QuickReminder
import com.havivon.pigeon.ui.blocked.BlockedEntry
import com.havivon.pigeon.ui.blocked.BlockedNumbersScreen
import com.havivon.pigeon.ui.blocked.BlockedNumbersState
import com.havivon.pigeon.ui.blocked.BlockProblem
import com.havivon.pigeon.ui.blocked.PhoneUnblockContent
import com.havivon.pigeon.ui.folders.CategoryColorDialogContent
import com.havivon.pigeon.ui.folders.FoldersScreen
import com.havivon.pigeon.ui.inbox.InboxScreen
import com.havivon.pigeon.ui.keywords.BlockedKeywordsScreen
import com.havivon.pigeon.ui.reminders.ReminderChooser
import com.havivon.pigeon.ui.rules.RuleEditorSheetContent
import com.havivon.pigeon.ui.rules.RuleEditorState
import com.havivon.pigeon.ui.rules.RuleKind
import com.havivon.pigeon.ui.rules.RuleSwatches
import com.havivon.pigeon.ui.rules.RulesScreen
import com.havivon.pigeon.ui.settings.ImportConfirmContent
import com.havivon.pigeon.ui.settings.SettingsActions
import com.havivon.pigeon.ui.settings.SettingsEnvironment
import com.havivon.pigeon.ui.settings.SettingsScreen
import com.havivon.pigeon.ui.settings.SettingsUiState
import com.havivon.pigeon.ui.theme.HebSmsTheme
import com.havivon.pigeon.ui.theme.HebTheme
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** A phone tall enough to draw the whole settings list in one picture. */
private val TallPhone = DeviceConfig.PIXEL_6.copy(screenHeight = 6200)

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
        folderCount = 3,
        blockedNumberCount = 3,
    )

    /** The phone's blocked numbers: made-up 555 numbers, one saved as a contact. */
    val blocked = BlockedNumbersState(
        entries = listOf(
            BlockedEntry("+972525550188"),
            BlockedEntry("0525550142", if (hebrew) "מוקד מכירות" else "Sales line"),
            BlockedEntry("+12025550175"),
        ),
        loaded = true,
    )

    /** Notifications not allowed yet and exact alarms off, so the rows that ask for them show. */
    val environment = SettingsEnvironment(
        canLock = true,
        contactsAllowed = true,
        notificationsAllowed = false,
        exactAlarmsAllowed = false,
        smsAccess = SmsAccess.Default,
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

    val folders = listOf(
        CustomFolder(1, if (hebrew) "עבודה" else "Work"),
        CustomFolder(2, if (hebrew) "ועד בית" else "Building committee"),
        CustomFolder(3, if (hebrew) "חוג כדורסל" else "Basketball club"),
    )

    /** Categories with colours: Banks and Codes among the built-in ones, and two of the user's (one coloured, one not). */
    val builtInColors = mapOf(Folder.Personal to RuleSwatches[2], Folder.Banks to RuleSwatches[0], Folder.Deliveries to RuleSwatches[6])
    val coloredFolders = listOf(
        CustomFolder(1, if (hebrew) "עבודה" else "Work", RuleSwatches[3]),
        CustomFolder(2, if (hebrew) "ועד בית" else "Building committee", RuleSwatches[4]),
        CustomFolder(3, if (hebrew) "חוג כדורסל" else "Basketball club"),
    )

    /** What an exported file holds, for the import confirmation. */
    val importSummary = BackupSummary(
        exportedAtMillis = Instant.parse("2026-10-26T12:00:00Z").toEpochMilli(),
        appVersion = "0.4.4",
        settings = 8, rules = 4, categories = 3, moves = 12, pinned = 2, locked = 1,
        notSpam = 5, blockedWords = 3, reminders = 2, favorites = 14,
    )

    val reminderTimes = mapOf(
        QuickReminder.InOneHour to (if (hebrew) "13:41" else "1:41 PM"),
        QuickReminder.ThisEvening to (if (hebrew) "היום 20:00" else "Today 8:00 PM"),
        QuickReminder.TomorrowMorning to (if (hebrew) "מחר 09:00" else "Tomorrow 9:00 AM"),
    )
}

/** A dialog as the app shows it, drawn in the same composition (Paparazzi does not draw its window). */
@Composable
private fun DialogOver(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) { content() }
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
        shot("93_categories") { FoldersScreen(s.folders) }
        shot("94_categories_reordering_dark", dark = true) { FoldersScreen(s.folders, previewDragging = 2) }
        shot("95_categories_empty") { FoldersScreen(emptyList()) }
        shot("114_categories_colors") { FoldersScreen(s.coloredFolders, builtInColors = s.builtInColors) }
        shot("115_categories_colors_dark", dark = true) { FoldersScreen(s.coloredFolders, builtInColors = s.builtInColors) }
        shot("116_category_color_picker") {
            FoldersScreen(s.coloredFolders, builtInColors = s.builtInColors)
            DialogOver { CategoryColorDialogContent(s.coloredFolders[0].name, s.coloredFolders[0].argb, {}) }
        }
        shot("117_category_color_picker_none_dark", dark = true) {
            FoldersScreen(s.coloredFolders, builtInColors = s.builtInColors)
            DialogOver { CategoryColorDialogContent(s.coloredFolders[2].name, null, {}) }
        }
        shot("120_blocked_numbers") { BlockedNumbersScreen(s.blocked) }
        shot("121_blocked_numbers_duplicate_dark", dark = true) { BlockedNumbersScreen(s.blocked.copy(draft = "052-5550188", problem = BlockProblem.Duplicate)) }
        shot("122_blocked_numbers_viewer") { BlockedNumbersScreen(BlockedNumbersState(available = false, loaded = true)) }
        shot("123_blocked_numbers_empty") { BlockedNumbersScreen(BlockedNumbersState(loaded = true)) }
        shot("124_unblock_on_phone") {
            BlockedNumbersScreen(s.blocked)
            DialogOver { PhoneUnblockContent(listOf("052-5550188"), {}, {}) }
        }
        shot("125_unblock_on_phone_many_dark", dark = true) {
            BlockedNumbersScreen(s.blocked)
            DialogOver { PhoneUnblockContent(listOf("052-5550188", "052-5550142"), {}, {}) }
        }
        shot("97_import_dialog") {
            SettingsScreen(s.state, s.environment, SettingsActions())
            DialogOver { ImportConfirmContent(s.importSummary, {}, {}) }
        }
        shot("98_import_dialog_dark", dark = true) {
            SettingsScreen(s.state, s.environment, SettingsActions())
            DialogOver { ImportConfirmContent(s.importSummary, {}, {}) }
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
        shot("96_en_categories") { FoldersScreen(s.folders) }
        shot("118_en_categories_colors") { FoldersScreen(s.coloredFolders, builtInColors = s.builtInColors) }
        shot("126_en_blocked_numbers") { BlockedNumbersScreen(s.blocked) }
        shot("127_en_blocked_numbers_viewer") { BlockedNumbersScreen(BlockedNumbersState(available = false, loaded = true)) }
        shot("128_en_unblock_on_phone") {
            BlockedNumbersScreen(s.blocked)
            DialogOver { PhoneUnblockContent(listOf("052-5550188"), {}, {}) }
        }
        shot("119_en_category_color_picker") {
            FoldersScreen(s.coloredFolders, builtInColors = s.builtInColors)
            DialogOver { CategoryColorDialogContent(s.coloredFolders[0].name, s.coloredFolders[0].argb, {}) }
        }
        shot("99_en_import_dialog") {
            SettingsScreen(s.state, s.environment, SettingsActions())
            DialogOver { ImportConfirmContent(s.importSummary, {}, {}) }
        }
    }
}
