package com.havivon.pigeon

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextRange
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.havivon.pigeon.data.sample.SampleData
import com.havivon.pigeon.domain.model.ThreadItem
import com.havivon.pigeon.ui.conversation.ConversationScreen
import com.havivon.pigeon.ui.inbox.InboxScreen
import com.havivon.pigeon.ui.rules.RulesScreen
import com.havivon.pigeon.ui.spam.SpamScreen
import com.havivon.pigeon.ui.spam.groupSpam
import com.havivon.pigeon.ui.theme.HebSmsTheme
import org.junit.Rule
import org.junit.Test

/** The made-up number under Danny's name. */
private const val PHONE = "052-555-0123"

/** The range of [part] inside the text of message [id], for the static selection highlight. */
private fun highlight(thread: List<ThreadItem>, id: Long, part: String): TextRange {
    val text = thread.filterIsInstance<ThreadItem.Msg>().first { it.message.id == id }.message.text
    val start = text.indexOf(part)
    check(start >= 0) { "\"$part\" is not in message $id" }
    return TextRange(start, start + part.length)
}

/**
 * Renders the real screens to PNG, at phone size, so the design can be approved before the SMS
 * code exists. Run: gradlew :app:recordPaparazziDebug  → app/src/test/snapshots/images/
 */
class DesignSnapshots {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(locale = "iw"),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) =
        paparazzi.snapshot(name) { HebSmsTheme(darkTheme = dark) { content() } }

    private val sample = SampleData.hebrew
    private val danny = sample.inbox.first { it.id == 2L }

    @Test
    fun screens() {
        shot("1_inbox") { InboxScreen(sample.inbox, sample.rules, sample.folderCounts, sample.dateLine) }
        shot("2_inbox_dark", dark = true) { InboxScreen(sample.inbox, sample.rules, sample.folderCounts, sample.dateLine) }
        shot("3_text_selection") {
            ConversationScreen(
                danny, sample.dannyThread, rule = null, draft = "אני בדרך, אגיע בעוד עשר דקות", subtitle = PHONE,
                textSelectionId = 5, previewSelection = highlight(sample.dannyThread, 5, "רחוב הרצל 12, קומה 3, דירה 7"),
            )
        }
        shot("4_multi_select") {
            ConversationScreen(danny, sample.dannyThread, rule = null, draft = "", selectedIds = setOf(4L, 5L, 6L), subtitle = PHONE)
        }
        shot("5_spam") { SpamScreen(groupSpam(sample.spam)) }
        shot("6_color_rules") { RulesScreen(sample.rules) }
        shot("7_conversation_dark", dark = true) {
            ConversationScreen(danny, sample.dannyThread, rule = null, draft = "", subtitle = PHONE)
        }
    }
}

/** The same design in English, left to right. */
class DesignSnapshotsEnglish {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(locale = "en"),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) =
        paparazzi.snapshot(name) { HebSmsTheme(darkTheme = dark) { content() } }

    private val sample = SampleData.english
    private val danny = sample.inbox.first { it.id == 2L }

    @Test
    fun screens() {
        shot("8_en_inbox") { InboxScreen(sample.inbox, sample.rules, sample.folderCounts, sample.dateLine) }
        shot("9_en_text_selection") {
            ConversationScreen(
                danny, sample.dannyThread, rule = null, draft = "On my way, I'll be there in ten minutes", subtitle = PHONE,
                textSelectionId = 5, previewSelection = highlight(sample.dannyThread, 5, "12 Maple Street, floor 3, apt 7"),
            )
        }
        shot("10_en_spam") { SpamScreen(groupSpam(sample.spam)) }
        shot("11_en_color_rules") { RulesScreen(sample.rules) }
        shot("12_en_inbox_dark", dark = true) {
            InboxScreen(sample.inbox, sample.rules, sample.folderCounts, sample.dateLine)
        }
    }
}
