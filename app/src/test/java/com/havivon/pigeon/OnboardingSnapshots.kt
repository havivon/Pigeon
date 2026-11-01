package com.havivon.pigeon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.havivon.pigeon.data.sample.SampleData
import com.havivon.pigeon.ui.inbox.InboxScreen
import com.havivon.pigeon.ui.onboarding.DefaultAppReminderCard
import com.havivon.pigeon.ui.onboarding.RestrictedSettingsHelpCard
import com.havivon.pigeon.ui.onboarding.DefaultAppWelcome
import com.havivon.pigeon.ui.onboarding.ViewerBanner
import com.havivon.pigeon.ui.theme.HebSmsTheme
import org.junit.Rule
import org.junit.Test

/**
 * The "make Pigeon your messaging app" screens: the welcome on first launch (make default, or just
 * view), the reminder over the inbox on later opens, and the viewer's banner at the top of the inbox. The reminder is a Dialog in the app, which Paparazzi may not draw, so
 * its card is rendered over a dimmed inbox in the same composition.
 */
class OnboardingSnapshots {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(locale = "iw"),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val sample = SampleData.hebrew

    @Test
    fun onboarding() {
        paparazzi.snapshot("26_welcome") { HebSmsTheme { DefaultAppWelcome({}, {}, {}) } }
        paparazzi.snapshot("27_reminder_over_inbox") {
            HebSmsTheme {
                InboxScreen(sample.inbox, sample.rules, sample.folderCounts, sample.dateLine)
                ReminderOverScrim()
            }
        }
        paparazzi.snapshot("29_welcome_dark") { HebSmsTheme(darkTheme = true) { DefaultAppWelcome({}, {}, {}) } }
        paparazzi.snapshot("59_restricted_help") {
            HebSmsTheme {
                DefaultAppWelcome({}, {}, {})
                OverScrim { RestrictedSettingsHelpCard({}, {}, {}, Modifier.fillMaxWidth()) }
            }
        }
        paparazzi.snapshot("61_viewer_banner") {
            HebSmsTheme {
                InboxScreen(sample.inbox, sample.rules, sample.folderCounts, sample.dateLine, banner = { ViewerBanner({}, {}) })
            }
        }
        paparazzi.snapshot("63_viewer_banner_dark") {
            HebSmsTheme(darkTheme = true) {
                InboxScreen(sample.inbox, sample.rules, sample.folderCounts, sample.dateLine, banner = { ViewerBanner({}, {}) })
            }
        }
    }
}

/** The same welcome in English, left to right. */
class OnboardingSnapshotsEnglish {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(locale = "en"),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun onboarding() {
        paparazzi.snapshot("28_en_welcome") { HebSmsTheme { DefaultAppWelcome({}, {}, {}) } }
        paparazzi.snapshot("60_en_restricted_help") {
            HebSmsTheme {
                DefaultAppWelcome({}, {}, {})
                OverScrim { RestrictedSettingsHelpCard({}, {}, {}, Modifier.fillMaxWidth()) }
            }
        }
        val sample = SampleData.english
        paparazzi.snapshot("62_en_viewer_banner") {
            HebSmsTheme {
                InboxScreen(sample.inbox, sample.rules, sample.folderCounts, sample.dateLine, banner = { ViewerBanner({}, {}) })
            }
        }
    }
}

@Composable
private fun ReminderOverScrim() = OverScrim { DefaultAppReminderCard({}, {}, {}, Modifier.fillMaxWidth()) }

/** A dialog's card drawn in the same composition, over a dimmed screen (a Dialog window does not render here). */
@Composable
private fun OverScrim(card: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        card()
    }
}
