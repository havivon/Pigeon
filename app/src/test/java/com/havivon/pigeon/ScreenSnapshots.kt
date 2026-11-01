package com.havivon.pigeon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.havivon.pigeon.data.sample.SampleData
import com.havivon.pigeon.data.sample.SampleSet
import com.havivon.pigeon.domain.analysis.LinkAnalyzer
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.Message
import com.havivon.pigeon.domain.model.SmsAccess
import com.havivon.pigeon.domain.model.SendStatus
import com.havivon.pigeon.domain.model.SpamReason
import com.havivon.pigeon.domain.model.ThreadItem
import com.havivon.pigeon.domain.model.UnreadMessage
import com.havivon.pigeon.ui.app.DefaultAppAccess
import com.havivon.pigeon.ui.app.LocalDefaultApp
import com.havivon.pigeon.ui.components.EmptyState
import com.havivon.pigeon.ui.compose.NewMessageScreen
import com.havivon.pigeon.ui.compose.NewMessageUiState
import com.havivon.pigeon.ui.compose.Recipient
import com.havivon.pigeon.ui.compose.Suggestion
import com.havivon.pigeon.ui.conversation.ConversationScreen
import com.havivon.pigeon.ui.conversation.LinkDetails
import com.havivon.pigeon.ui.conversation.placeholderConversation
import com.havivon.pigeon.ui.inbox.ConversationActions
import com.havivon.pigeon.ui.inbox.InboxEmpty
import com.havivon.pigeon.ui.inbox.InboxScreen
import com.havivon.pigeon.ui.lock.LockCover
import com.havivon.pigeon.ui.onboarding.PermissionsCard
import com.havivon.pigeon.ui.search.SearchFilters
import com.havivon.pigeon.ui.search.SearchHit
import com.havivon.pigeon.ui.search.SearchScreen
import com.havivon.pigeon.ui.search.SearchUiState
import com.havivon.pigeon.ui.spam.SpamMessage
import com.havivon.pigeon.ui.spam.SpamScreen
import com.havivon.pigeon.ui.spam.groupSpam
import com.havivon.pigeon.ui.theme.HebSmsTheme
import com.havivon.pigeon.ui.theme.HebTheme
import org.junit.Rule
import org.junit.Test

/** Made-up content for the screens added with the real data (search, new message, sheets, empty states). */
private class NewScreensSample(val sample: SampleSet, hebrew: Boolean) {
    val danny = sample.inbox.first { it.id == 2L }
    val noa = sample.inbox.first { it.id == 6L }

    /** The unread list: messages, not conversations (two from Mom, two from the bank, a code, a locked chat, a long one cut at three lines). */
    val unread: List<UnreadMessage> = run {
        val mom = sample.inbox.first { it.id == 1L }
        val bank = sample.inbox.first { it.id == 3L }
        val verify = sample.inbox.first { it.id == 5L }
        listOf(
            UnreadMessage(101, mom.id, mom.name, if (hebrew) "תביא לחם בדרך הביתה ❤️" else "Pick up some bread on your way home ❤️", mom.time, mom.avatarArgb, ruleId = 3),
            UnreadMessage(
                102, mom.id, mom.name,
                if (hebrew) "ותחזור לפני שבע, דני מגיע לארוחת ערב ואני רוצה שנהיה כולם ביחד" else "And be back before seven, Danny is coming for dinner and I want us all together",
                if (hebrew) "12:38" else "12:38 PM", mom.avatarArgb, ruleId = 3,
            ),
            UnreadMessage(103, bank.id, bank.name, if (hebrew) "חיוב בכרטיס המסתיים ב-4821 בסך 249.90 ₪" else "Charge on the card ending in 4821 for $249.90", bank.time, bank.avatarArgb, ruleId = 1),
            UnreadMessage(
                107, bank.id, bank.name,
                if (hebrew) "משיכה של 320 ₪ מהחשבון. יתרה: 4,812 ₪" else "Withdrawal of $320 from your account. Balance: $4,812",
                if (hebrew) "09:48" else "9:48 AM", bank.avatarArgb, ruleId = 1,
            ),
            UnreadMessage(
                104, verify.id, verify.name,
                if (hebrew) "קוד האימות שלך הוא 482913. אל תשתף אותו עם אף אחד." else "Your verification code is 482913. Do not share it with anyone.",
                verify.time, verify.avatarArgb, code = "482913",
            ),
            UnreadMessage(105, danny.id, danny.name, if (hebrew) "שיחה נעולה" else "Locked chat", if (hebrew) "08:20" else "8:20 AM", danny.avatarArgb, locked = true),
            UnreadMessage(
                106, noa.id, noa.name,
                if (hebrew) {
                    "היי, רציתי לספר לך על הטיול שלנו בסוף השבוע. מצאתי מקום מקסים ליד הים, יש בו בריכה, ארוחת בוקר כלולה ונוף מדהים לשקיעה. אם מתאים לך, אני סוגרת את זה עוד היום"
                } else {
                    "Hi, I wanted to tell you about our weekend trip. I found a lovely place by the sea with a pool, breakfast included and an amazing sunset view. If it suits you, I will book it today"
                },
                if (hebrew) "אתמול" else "Yesterday", noa.avatarArgb,
            ),
        )
    }

    /** The unread messages of the Banks folder only (the conversations of [unread] that the sample files under Banks). */
    val bankUnread = unread.filter { m -> sample.inbox.first { it.id == m.threadId }.folder == Folder.Banks }

    /** The chips as the screen counts them when the unread list is shown. */
    val unreadCounts = sample.folderCounts + (Folder.All to unread.size) + (Folder.Banks to bankUnread.size)

    val search = SearchUiState(
        query = if (hebrew) "הרצל" else "Maple",
        filters = SearchFilters(hasLink = false),
        conversations = emptyList(),
        messages = listOf(
            SearchHit(5, 2, danny.name, if (hebrew) "מעולה. הכתובת: רחוב הרצל 12, קומה 3, דירה 7" else "Great. The address: 12 Maple Street, floor 3, apt 7", danny.time, danny.avatarArgb, false),
            SearchHit(41, 6, noa.name, if (hebrew) "נפגשים בקפה ברחוב הרצל? ב-19:00 מתאים לי" else "Meet at the cafe on Maple Street? 7 PM works for me", noa.time, noa.avatarArgb, false),
            SearchHit(42, 2, danny.name, if (hebrew) "חנית ברחוב הרצל או בחניון?" else "Did you park on Maple Street or in the lot?", if (hebrew) "יום ג׳" else "Tue", danny.avatarArgb, true),
        ),
        senders = emptyList(),
    )

    val newMessage = NewMessageUiState(
        recipients = listOf(Recipient("0525550123", danny.name)),
        query = if (hebrew) "נו" else "No",
        suggestions = listOf(
            Suggestion(noa.name, "054-555-0144"),
            Suggestion(noa.name, "03-555-0177"),
            Suggestion(if (hebrew) "נורית ברק" else "Nora Barak", "050-555-0188"),
        ),
        body = if (hebrew) "מגיעים בשבע?" else "Coming at seven?",
    )

    /** A conversation in every state the real data adds: sending, failed, starred, a link, a reminder already set. */
    val states: List<ThreadItem> = listOf(
        ThreadItem.Day(if (hebrew) "היום" else "Today"),
        ThreadItem.Msg(Message(1, if (hebrew) "החבילה שלך ממתינה, פרטים כאן: isr-post.co/pay" else "Your parcel is waiting, details here: usps-track.co/pay", "09:12", outgoing = false)),
        ThreadItem.Msg(Message(2, if (hebrew) "הכתובת: רחוב הרצל 12, קומה 3" else "The address: 12 Maple Street, floor 3", "09:20", outgoing = false, starred = true)),
        ThreadItem.Msg(
            Message(3, if (hebrew) "סגרנו, מחר ב-10:00" else "Done, tomorrow at 10:00", "09:21", outgoing = true, reminder = if (hebrew) "מחר 10:00" else "Tomorrow 10:00", reminderSet = true),
        ),
        ThreadItem.Msg(Message(4, if (hebrew) "אני בדרך" else "On my way", "09:22", outgoing = true, status = SendStatus.Failed)),
        ThreadItem.Msg(Message(5, if (hebrew) "מגיע בעוד עשר דקות" else "There in ten minutes", "09:23", outgoing = true, status = SendStatus.Sending)),
    )

    val spamConversation = Conversation(
        101, "+972 55-123-4567", "", "13:02", 0xFFE53935,
        folder = Folder.Spam,
        spamReasons = listOf(SpamReason.UnknownSender, SpamReason.ShortLink),
    )

    val spamThread: List<ThreadItem> = listOf(
        ThreadItem.Day(if (hebrew) "היום" else "Today"),
        ThreadItem.Msg(Message(1, if (hebrew) "זכית בפרס! לחץ כאן לקבלת המתנה שלך: bit.ly/x7Kq9" else "You won a prize! Tap here to claim your gift: bit.ly/x7Kq9", "13:02", outgoing = false)),
    )

    val lookalike = LinkAnalyzer.findLinks(if (hebrew) "isr-post.co/pay" else "usps-track.co/pay").first()

    /** The Spam folder by sender, the first sender opened to show its two messages. */
    val spamGroups = groupSpam(sample.spam)
    val spamOpened: Map<String, List<SpamMessage>> = run {
        val newest = sample.spam.first().snippet
        val older = if (hebrew) "עוד לא מאוחר! הפרס שלך מחכה, פרטים בהודעה הבאה" else "It's not too late! Your prize is waiting, details in the next message"
        mapOf(
            spamGroups.first().key to listOf(
                SpamMessage(1012, 101, newest, if (hebrew) "13:02" else "1:02 PM", LinkAnalyzer.findLinks(newest)),
                SpamMessage(1011, 101, older, if (hebrew) "אתמול" else "Yesterday"),
            ),
        )
    }
}

/** The screens and states added with the real data, in Hebrew, right to left. */
class NewScreensSnapshots {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(locale = "iw"),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) =
        paparazzi.snapshot(name) { HebSmsTheme(darkTheme = dark) { content() } }

    private val s = NewScreensSample(SampleData.hebrew, hebrew = true)

    @Test
    fun screens() {
        shot("30_inbox_actions_sheet") {
            InboxScreen(s.sample.inbox, s.sample.rules, s.sample.folderCounts, s.sample.dateLine)
            Sheet { ConversationActions(s.sample.inbox.first(), {}, {}, {}, {}, {}) }
        }
        shot("31_search") { SearchScreen(s.search, autoFocus = false) }
        shot("32_new_message") { NewMessageScreen(s.newMessage, autoFocus = false) }
        shot("33_link_warning") {
            ConversationScreen(s.spamConversation, s.spamThread, rule = null, draft = "", subtitle = null, onCall = null)
            Sheet { LinkDetails(s.lookalike, {}, {}) }
        }
        shot("34_inbox_empty") {
            InboxScreen(emptyList(), s.sample.rules, emptyMap(), s.sample.dateLine, emptyState = { InboxEmpty(Folder.All) })
        }
        shot("35_inbox_not_default") {
            CompositionLocalProvider(LocalDefaultApp provides DefaultAppAccess(SmsAccess.None, makeDefault = {})) {
                InboxScreen(emptyList(), s.sample.rules, emptyMap(), s.sample.dateLine, emptyState = { InboxEmpty(Folder.All) })
            }
        }
        shot("36_spam_empty") {
            SpamScreen(emptyList(), onDeleteAll = null, emptyState = {
                EmptyState(Icons.Rounded.Shield, stringResource(R.string.spam_empty_title), stringResource(R.string.spam_empty_body))
            })
        }
        shot("70_spam_expanded") { SpamScreen(s.spamGroups, expanded = s.spamOpened.keys, messages = s.spamOpened) }
        shot("72_spam_dark", dark = true) { SpamScreen(s.spamGroups) }
        shot("37_permissions") {
            InboxScreen(s.sample.inbox, s.sample.rules, s.sample.folderCounts, s.sample.dateLine)
            Scrim { PermissionsCard(askNotifications = true, askContacts = true, onContinue = {}, onNotNow = {}, modifier = Modifier.fillMaxWidth()) }
        }
        shot("38_lock_cover") { LockCover {} }
        shot("39_conversation_states") {
            ConversationScreen(s.danny, s.states, rule = null, draft = "", subtitle = "052-555-0123")
        }
        shot("40_search_dark", dark = true) { SearchScreen(s.search, autoFocus = false) }
        shot("41_new_message_dark", dark = true) { NewMessageScreen(s.newMessage, autoFocus = false) }
        shot("66_inbox_unread_empty") {
            InboxScreen(emptyList(), s.sample.rules, s.sample.folderCounts, s.sample.dateLine, unreadOnly = true, emptyState = { InboxEmpty(Folder.All, unreadOnly = true) })
        }
        shot("67_inbox_banks_unread") {
            InboxScreen(
                emptyList(), s.sample.rules, s.unreadCounts, s.sample.dateLine,
                selectedFolder = Folder.Banks, unreadOnly = true, unreadCount = s.bankUnread.size, unreadMessages = s.bankUnread,
            )
        }
        shot("68_inbox_unread_dark", dark = true) {
            InboxScreen(emptyList(), s.sample.rules, s.unreadCounts, s.sample.dateLine, unreadOnly = true, unreadCount = s.unread.size, unreadMessages = s.unread)
        }
        shot("64_inbox_loading") { InboxScreen(emptyList(), s.sample.rules, emptyMap(), s.sample.dateLine, loading = true) }
        shot("65_conversation_loading") {
            ConversationScreen(placeholderConversation(2), emptyList(), rule = null, draft = "", onCall = null, loading = true)
        }
    }
}

/** The same new screens in English, left to right. */
class NewScreensSnapshotsEnglish {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(locale = "en"),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) =
        paparazzi.snapshot(name) { HebSmsTheme(darkTheme = dark) { content() } }

    private val s = NewScreensSample(SampleData.english, hebrew = false)

    @Test
    fun screens() {
        shot("42_en_search") { SearchScreen(s.search, autoFocus = false) }
        shot("43_en_new_message") { NewMessageScreen(s.newMessage, autoFocus = false) }
        shot("44_en_link_warning") {
            ConversationScreen(s.spamConversation, s.spamThread, rule = null, draft = "", subtitle = null, onCall = null)
            Sheet { LinkDetails(s.lookalike, {}, {}) }
        }
        shot("45_en_conversation_states") {
            ConversationScreen(s.danny, s.states, rule = null, draft = "", subtitle = "555-0123")
        }
        shot("71_en_spam_expanded") { SpamScreen(s.spamGroups, expanded = s.spamOpened.keys, messages = s.spamOpened) }
        shot("69_en_inbox_unread") {
            InboxScreen(emptyList(), s.sample.rules, s.unreadCounts, s.sample.dateLine, unreadOnly = true, unreadCount = s.unread.size, unreadMessages = s.unread)
        }
    }
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

@Composable
private fun Scrim(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        content()
    }
}
