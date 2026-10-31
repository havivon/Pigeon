package com.hebsms.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextRange
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.hebsms.app.data.sample.SampleData
import com.hebsms.app.ui.conversation.ConversationScreen
import com.hebsms.app.ui.inbox.InboxScreen
import com.hebsms.app.ui.rules.RulesScreen
import com.hebsms.app.ui.spam.SpamScreen
import com.hebsms.app.ui.theme.HebSmsTheme
import org.junit.Rule
import org.junit.Test

/**
 * Renders the real screens to PNG, at phone size, so the design can be approved before the SMS
 * code exists. Run: gradlew :app:recordPaparazziDebug  → app/src/test/snapshots/images/
 */
class DesignSnapshots {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(locale = "he"),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) =
        paparazzi.snapshot(name) { HebSmsTheme(darkTheme = dark) { content() } }

    private val danny = SampleData.inbox.first { it.id == 2 }

    @Test
    fun screens() {
        shot("1_inbox") { InboxScreen(SampleData.inbox, SampleData.rules, SampleData.folderCounts) }
        shot("2_inbox_dark", dark = true) { InboxScreen(SampleData.inbox, SampleData.rules, SampleData.folderCounts) }
        shot("3_text_selection") {
            ConversationScreen(
                danny, SampleData.dannyThread, rule = null, draft = "אני בדרך, אגיע בעוד עשר דקות",
                textSelectionId = 5, previewSelection = TextRange(15, 43),
            )
        }
        shot("4_multi_select") {
            ConversationScreen(danny, SampleData.dannyThread, rule = null, draft = "", selectedIds = setOf(4, 5, 6))
        }
        shot("5_spam") { SpamScreen(SampleData.spam) }
        shot("6_color_rules") { RulesScreen(SampleData.rules) }
        shot("7_conversation_dark", dark = true) {
            ConversationScreen(danny, SampleData.dannyThread, rule = null, draft = "")
        }
    }
}
