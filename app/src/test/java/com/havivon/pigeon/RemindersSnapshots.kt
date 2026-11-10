package com.havivon.pigeon

import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.havivon.pigeon.data.sample.SampleData
import com.havivon.pigeon.domain.model.SmsType
import com.havivon.pigeon.domain.model.ThreadItem
import com.havivon.pigeon.fakes.Now
import com.havivon.pigeon.fakes.englishLabels
import com.havivon.pigeon.fakes.hebrewLabels
import com.havivon.pigeon.fakes.sms
import com.havivon.pigeon.ui.common.DateLabels
import com.havivon.pigeon.ui.common.threadItems
import com.havivon.pigeon.ui.conversation.ConversationScreen
import com.havivon.pigeon.ui.inbox.InboxScreen
import com.havivon.pigeon.ui.reminders.ReminderRow
import com.havivon.pigeon.ui.reminders.RemindersScreen
import com.havivon.pigeon.ui.theme.HebSmsTheme
import org.junit.Rule
import org.junit.Test

/** The Reminders screen, the inbox's bell and the bubble footers with their dates, in Hebrew (right to left) and in English. */
abstract class RemindersShots(private val hebrew: Boolean) {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(locale = if (hebrew) "iw" else "en"),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val prefix = if (hebrew) "" else "en_"
    private val sample = if (hebrew) SampleData.hebrew else SampleData.english
    private val s = NewScreensSample(sample, hebrew)
    private val labels: DateLabels = if (hebrew) hebrewLabels() else englishLabels()

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) =
        paparazzi.snapshot(prefix + name) { HebSmsTheme(darkTheme = dark) { content() } }

    private fun row(id: Long, sender: String?, text: String?, at: Long, locked: Boolean = false) =
        ReminderRow(id, threadId = id, messageId = id, sender = sender, text = text, whenLabel = labels.reminder(at, Now, withTime = true), atMillis = at, locked = locked)

    private val hour = 60 * 60 * 1000L
    private val day = 24 * hour

    private val upcoming = listOf(
        row(1, if (hebrew) "אמא" else "Mom", if (hebrew) "לקחת את דני מהחוג ולעצור בדרך בסופר" else "Pick Danny up from practice and stop by the store on the way", Now + 6 * hour),
        row(2, if (hebrew) "בנק הפועלים" else "First Bank", if (hebrew) "המסגרת מתחדשת מחר, לבדוק את היתרה לפני ההוראת קבע" else "The limit renews tomorrow, check the balance before the standing order", Now + day - 3 * hour),
        row(3, null, null, Now + 2 * day, locked = true),
        row(4, "+972 52-555-0199", if (hebrew) "קוד הכניסה לבניין" else "The building entry code", Now + 12 * day),
    )

    private val past = listOf(
        row(5, if (hebrew) "דני" else "Danny", if (hebrew) "תזכיר לי לשלם על הצילום" else "Remind me to pay for the photos", Now - 3 * hour),
        row(6, if (hebrew) "אמא" else "Mom", if (hebrew) "ארוחת שישי אצלנו" else "Friday dinner at our place", Now - 9 * day),
    )

    @Test
    fun reminders() {
        shot("120_reminders") { RemindersScreen(upcoming, past) }
        shot("121_reminders_dark", dark = true) { RemindersScreen(upcoming, past) }
        shot("122_reminders_empty") { RemindersScreen(emptyList(), emptyList()) }
        shot("123_reminders_empty_dark", dark = true) { RemindersScreen(emptyList(), emptyList()) }
        shot("124_reminders_past_only") { RemindersScreen(emptyList(), past) }
    }

    @Test
    fun inboxBell() {
        shot("125_inbox_bell_dot") { InboxScreen(sample.inbox, sample.rules, sample.folderCounts, sample.dateLine, hasReminders = true) }
        shot("126_inbox_bell_dot_dark", dark = true) { InboxScreen(sample.inbox, sample.rules, sample.folderCounts, sample.dateLine, hasReminders = true) }
        shot("127_inbox_bell_no_dot") { InboxScreen(sample.inbox, sample.rules, sample.folderCounts, sample.dateLine) }
    }

    @Test
    fun bubbleDates() {
        val at = { y: Int, mo: Int, dd: Int, h: Int, mi: Int -> com.havivon.pigeon.fakes.millis(y, mo, dd, h, mi) }
        val text = if (hebrew) {
            listOf("בוקר טוב", "אני מגיע בעוד חצי שעה", "נתראה ביום שלישי", "תודה על הכול", "מזל טוב!")
        } else {
            listOf("Good morning", "I'll be there in half an hour", "See you on Tuesday", "Thanks for everything", "Congratulations!")
        }
        val messages = listOf(
            sms(1, 2, text[4], at(2025, 10, 3, 13, 14), type = SmsType.Inbox),
            sms(2, 2, text[3], at(2026, 10, 3, 13, 14), type = SmsType.Sent),
            sms(3, 2, text[2], at(2026, 10, 27, 13, 14), type = SmsType.Inbox),
            sms(4, 2, text[1], at(2026, 11, 1, 9, 14), type = SmsType.Sent),
            sms(5, 2, text[0], at(2026, 11, 1, 11, 40), type = SmsType.Inbox),
        )
        val items: List<ThreadItem> = threadItems(messages, labels, Now) { m ->
            com.havivon.pigeon.domain.model.Message(m.id, m.body, labels.bubble(m.dateMillis, Now), m.type.outgoing)
        }
        shot("128_bubble_dates") { ConversationScreen(s.danny, items, rule = null, draft = "", subtitle = "052-555-0123") }
        shot("129_bubble_dates_dark", dark = true) { ConversationScreen(s.danny, items, rule = null, draft = "", subtitle = "052-555-0123") }
    }
}

class RemindersSnapshotsHebrew : RemindersShots(hebrew = true)

class RemindersSnapshotsEnglish : RemindersShots(hebrew = false)
