package com.havivon.pigeon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
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
import com.havivon.pigeon.domain.folders.FolderNameProblem
import com.havivon.pigeon.domain.model.CategoryTag
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.CustomFolder
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
import com.havivon.pigeon.ui.folders.FolderNameDialogContent
import com.havivon.pigeon.ui.folders.MoveToFolderContent
import com.havivon.pigeon.ui.inbox.ConversationActions
import com.havivon.pigeon.ui.inbox.InboxEmpty
import com.havivon.pigeon.ui.inbox.InboxScreen
import com.havivon.pigeon.ui.inbox.MarkAllReadDialogContent
import com.havivon.pigeon.ui.lock.LockCover
import com.havivon.pigeon.ui.onboarding.PermissionsCard
import com.havivon.pigeon.ui.rules.RuleSwatches
import com.havivon.pigeon.ui.search.SearchFilters
import com.havivon.pigeon.ui.search.SearchHit
import com.havivon.pigeon.ui.search.SearchScreen
import com.havivon.pigeon.ui.search.SearchUiState
import com.havivon.pigeon.ui.spam.SpamMessage
import com.havivon.pigeon.ui.spam.SpamScreen
import com.havivon.pigeon.ui.spam.filterSpam
import com.havivon.pigeon.ui.spam.groupSpam
import com.havivon.pigeon.ui.bin.BinGroup
import com.havivon.pigeon.ui.bin.BinRow
import com.havivon.pigeon.ui.bin.BinScreen
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
        val bank = sample.inbox.first { it.id == 3L }.copy(addresses = listOf("Bank"))
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

    // Categories with colours (the optional colour of a category) on the inbox and the search results
    private val personal = if (hebrew) "אישי" else "Personal"
    private val banks = if (hebrew) "בנקים" else "Banks"
    private val codes = if (hebrew) "קודים" else "Codes"
    private val work = if (hebrew) "עבודה" else "Work"
    private val blue = 0xFF2F80EDL
    private val purple = 0xFF5B3FD9L
    private val amber = 0xFFE8B10CL

    /** Personal, Codes and Deliveries have a colour; one category of the user's too. */
    val builtInColors = mapOf(Folder.Personal to blue, Folder.Codes to purple, Folder.Deliveries to amber)
    val coloredCustomFolders = listOf(CustomFolder(1, work, 0xFF1E9E6AL), CustomFolder(2, if (hebrew) "ועד בית" else "Building committee"))

    /**
     * The inbox with colours: Mom keeps her pink rule frame (a rule wins) and shows her Personal tag in blue; Danny is framed in
     * Personal's blue; Verify in Codes' violet; the bank has no colour for Banks, so only its rule's frame, and no tag.
     */
    val inboxColored: List<Conversation> = sample.inbox.map {
        when (it.id) {
            1L, 2L, 6L, 7L, 8L -> it.copy(category = CategoryTag(personal, blue))
            4L -> it.copy(category = CategoryTag(if (hebrew) "משלוחים" else "Deliveries", amber))
            5L -> it.copy(category = CategoryTag(codes, purple))
            3L -> it.copy(category = CategoryTag(banks))
            else -> it
        }
    }

    /**
     * Search results with their category tags: Danny's in neutral Personal, the bank's message framed and tagged in its rule's green
     * (Banks has no colour of its own), Noa's in the user's category "Work" (blue), and a spam result tagged "Spam", neutral.
     */
    val searchTagged = SearchUiState(
        query = search.query,
        rules = sample.rules,
        conversations = listOf(
            danny.copy(category = CategoryTag(personal)),
            noa.copy(category = CategoryTag(work, blue)),
        ),
        messages = listOf(
            SearchHit(5, 2, danny.name, search.messages[0].text, danny.time, danny.avatarArgb, false, category = CategoryTag(personal)),
            SearchHit(
                6, 3, sample.inbox.first { it.id == 3L }.name,
                if (hebrew) "חיוב בכרטיס ברחוב הרצל 12 בסך 249.90 ₪" else "Card charge at 12 Maple Street for $249.90", "10:32", 0xFF1E9E6A, false,
                category = CategoryTag(banks), ruleId = 1,
            ),
            SearchHit(41, 6, noa.name, search.messages[1].text, noa.time, noa.avatarArgb, false, category = CategoryTag(work, blue)),
            SearchHit(
                42, 101, if (hebrew) "+972 55-123-4567" else "+972 55-123-4567",
                if (hebrew) "זכית בפרס ברחוב הרצל! bit.ly/x7Kq9" else "You won a prize on Maple Street! bit.ly/x7Kq9", "13:02", 0xFFE53935, false,
                category = CategoryTag(if (hebrew) "ספאם" else "Spam"),
            ),
        ),
        senders = emptyList(),
    )
    val searchSelected = setOf(6L, 41L)
    val unreadColored: List<UnreadMessage> = unread.map { m -> m.copy(category = inboxColored.first { it.id == m.threadId }.category) }

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

    /** The spam search with a word typed: the senders whose messages hold it, still one row per sender. */
    val spamQuery = if (hebrew) "פרס" else "prize"
    val spamFound = filterSpam(groupSpam(sample.spam), spamQuery)

    /** The recycle bin: a contact's two messages deleted today, a business's from last week, a locked chat about to go. */
    val bin: List<BinGroup> = listOf(
        BinGroup(
            key = "0525550123",
            name = danny.name,
            messages = listOf(
                BinRow(2, if (hebrew) "סגרנו, מחר ב-10:00" else "Done, tomorrow at 10:00", "09:21", outgoing = true, locked = false),
                BinRow(1, if (hebrew) "נפגשים מחר בבוקר?" else "Meeting tomorrow morning?", "09:12", outgoing = false, locked = false),
            ),
            deletedLabel = if (hebrew) "10:30" else "10:30 AM",
            daysLeft = 30,
            locked = false,
            ids = setOf(1, 2),
        ),
        BinGroup(
            key = "shopnow",
            name = "ShopNow",
            messages = listOf(BinRow(3, if (hebrew) "המבצע מתחיל מחר! 20% הנחה" else "The sale starts tomorrow! 20% off", if (hebrew) "יום ב׳" else "Mon", outgoing = false, locked = false)),
            deletedLabel = if (hebrew) "יום ב׳" else "Mon",
            daysLeft = 25,
            locked = false,
            ids = setOf(3),
        ),
        BinGroup(
            key = "0545550144",
            name = noa.name,
            messages = listOf(BinRow(-4, if (hebrew) "שיחה נעולה" else "Locked chat", "8.10", outgoing = false, locked = true)),
            deletedLabel = "4.10",
            daysLeft = 2,
            locked = true,
            ids = setOf(4, 5, 6),
        ),
    )

    /** The user's own categories: two, the first holding Danny and Noa (moved by hand). */
    val customFolders = listOf(CustomFolder(1, if (hebrew) "עבודה" else "Work"), CustomFolder(2, if (hebrew) "ועד בית" else "Building committee"))
    val inWork: List<Conversation> = sample.inbox.filter { it.id == 2L || it.id == 6L }
        .map { it.copy(folder = Folder.All, customFolderId = 1, folderOverridden = true, addresses = listOf("052-555-01${it.id}0"), unread = if (it.id == 6L) 2 else it.unread) }
    val customCounts = mapOf(1L to inWork.sumOf { it.unread }, 2L to 0)
    val bank = sample.inbox.first { it.id == 3L }.copy(addresses = listOf("Bank"))

    /** "New category" with a name typed, and with one already taken (a built-in folder's). */
    val newFolderName = if (hebrew) "חוג כדורסל" else "Basketball club"
    val takenFolderName = if (hebrew) "בנקים" else "banks"

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
            Sheet { ConversationActions(s.sample.inbox.first(), {}, {}, {}, {}, {}, {}) }
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
        shot("73_spam_search") { SpamScreen(s.spamFound, searching = true, query = s.spamQuery, autoFocus = false) }
        shot("74_spam_selection") { SpamScreen(s.spamGroups, selected = setOf(s.spamGroups[0].key, s.spamGroups[2].key)) }
        shot("75_recycle_bin") { BinScreen(s.bin, expanded = setOf(s.bin.first().key)) }
        shot("76_recycle_bin_dark", dark = true) { BinScreen(s.bin, expanded = setOf(s.bin.first().key)) }
        shot("77_recycle_bin_selection") { BinScreen(s.bin, selected = setOf(s.bin[0].key, s.bin[1].key)) }
        shot("78_recycle_bin_empty") { BinScreen(emptyList(), onEmpty = null) }
        shot("79_spam_search_dark", dark = true) { SpamScreen(s.spamFound, searching = true, query = s.spamQuery, autoFocus = false) }
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
        shot("84_move_to_category") {
            InboxScreen(s.sample.inbox, s.sample.rules, s.sample.folderCounts, s.sample.dateLine)
            Sheet { MoveToFolderContent(s.inWork.first(), s.customFolders, {}, {}) }
        }
        shot("85_move_to_category_dark", dark = true) {
            InboxScreen(s.sample.inbox, s.sample.rules, s.sample.folderCounts, s.sample.dateLine)
            Sheet { MoveToFolderContent(s.bank, s.customFolders, {}, {}) }
        }
        shot("86_new_category") {
            InboxScreen(s.sample.inbox, s.sample.rules, s.sample.folderCounts, s.sample.dateLine)
            Scrim { FolderNameDialogContent(stringResource(R.string.folder_new_title), stringResource(R.string.folder_create), s.newFolderName, null, {}, {}, {}, argb = RuleSwatches[1], onColor = {}) }
        }
        shot("87_new_category_taken_dark", dark = true) {
            InboxScreen(s.sample.inbox, s.sample.rules, s.sample.folderCounts, s.sample.dateLine)
            Scrim {
                FolderNameDialogContent(
                    stringResource(R.string.folder_new_title), stringResource(R.string.folder_create), s.takenFolderName, FolderNameProblem.Taken, {}, {}, {}, onColor = {},
                )
            }
        }
        shot("88_inbox_category") {
            InboxScreen(
                s.inWork, s.sample.rules, s.sample.folderCounts, s.sample.dateLine,
                customFolders = s.customFolders, customCounts = s.customCounts, selectedCustom = 1,
            )
        }
        shot("89_inbox_category_empty") {
            InboxScreen(
                emptyList(), s.sample.rules, s.sample.folderCounts, s.sample.dateLine,
                customFolders = s.customFolders, customCounts = s.customCounts, selectedCustom = 2,
                emptyState = { InboxEmpty(Folder.All, customName = s.customFolders[1].name) },
            )
        }
        shot("65_conversation_loading") {
            ConversationScreen(placeholderConversation(2), emptyList(), rule = null, draft = "", onCall = null, loading = true)
        }
        shot("104_search_tags") { SearchScreen(s.searchTagged, autoFocus = false) }
        shot("105_search_tags_dark", dark = true) { SearchScreen(s.searchTagged, autoFocus = false) }
        shot("106_search_selection") { SearchScreen(s.searchTagged, selectedIds = s.searchSelected, autoFocus = false) }
        shot("107_search_selection_dark", dark = true) { SearchScreen(s.searchTagged, selectedIds = s.searchSelected, autoFocus = false) }
        shot("108_inbox_category_colors") {
            InboxScreen(
                s.inboxColored, s.sample.rules, s.sample.folderCounts, s.sample.dateLine,
                customFolders = s.coloredCustomFolders, customCounts = mapOf(1L to 0, 2L to 0), builtInColors = s.builtInColors,
            )
        }
        shot("109_inbox_category_colors_dark", dark = true) {
            InboxScreen(
                s.inboxColored, s.sample.rules, s.sample.folderCounts, s.sample.dateLine,
                customFolders = s.coloredCustomFolders, customCounts = mapOf(1L to 0, 2L to 0), builtInColors = s.builtInColors,
            )
        }
        shot("113_inbox_unread_category_colors") {
            InboxScreen(
                emptyList(), s.sample.rules, s.unreadCounts, s.sample.dateLine, unreadOnly = true, unreadCount = s.unread.size,
                unreadMessages = s.unreadColored, builtInColors = s.builtInColors,
            )
        }
        // "Mark all read" at the other end of the All / Unread row: enabled, dark, a 360dp phone with a long count, and its question
        shot("114_inbox_mark_all_read") {
            InboxScreen(s.sample.inbox, s.sample.rules, s.sample.folderCounts, s.sample.dateLine, unreadCount = s.unread.size)
        }
        shot("115_inbox_mark_all_read_dark", dark = true) {
            InboxScreen(s.sample.inbox, s.sample.rules, s.sample.folderCounts, s.sample.dateLine, unreadCount = s.unread.size)
        }
        shot("116_inbox_mark_all_read_360dp") {
            Box(Modifier.width(360.dp)) { InboxScreen(s.sample.inbox, s.sample.rules, s.sample.folderCounts, s.sample.dateLine, unreadCount = 128) }
        }
        shot("117_mark_all_read_dialog") {
            InboxScreen(s.sample.inbox, s.sample.rules, s.sample.folderCounts, s.sample.dateLine, unreadCount = s.unread.size)
            Scrim { MarkAllReadDialogContent(s.unread.size, stringResource(R.string.common_folder_all), {}, {}) }
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
        shot("80_en_spam_search") { SpamScreen(s.spamFound, searching = true, query = s.spamQuery, autoFocus = false) }
        shot("81_en_spam_selection") { SpamScreen(s.spamGroups, selected = setOf(s.spamGroups[0].key, s.spamGroups[2].key)) }
        shot("82_en_recycle_bin") { BinScreen(s.bin, expanded = setOf(s.bin.first().key)) }
        shot("83_en_recycle_bin_selection") { BinScreen(s.bin, selected = setOf(s.bin[0].key, s.bin[1].key)) }
        shot("90_en_move_to_category") {
            InboxScreen(s.sample.inbox, s.sample.rules, s.sample.folderCounts, s.sample.dateLine)
            Sheet { MoveToFolderContent(s.inWork.first(), s.customFolders, {}, {}) }
        }
        shot("91_en_new_category") {
            InboxScreen(s.sample.inbox, s.sample.rules, s.sample.folderCounts, s.sample.dateLine)
            Scrim { FolderNameDialogContent(stringResource(R.string.folder_new_title), stringResource(R.string.folder_create), s.newFolderName, null, {}, {}, {}, argb = RuleSwatches[1], onColor = {}) }
        }
        shot("92_en_inbox_category") {
            InboxScreen(
                s.inWork, s.sample.rules, s.sample.folderCounts, s.sample.dateLine,
                customFolders = s.customFolders, customCounts = s.customCounts, selectedCustom = 1,
            )
        }
        shot("69_en_inbox_unread") {
            InboxScreen(emptyList(), s.sample.rules, s.unreadCounts, s.sample.dateLine, unreadOnly = true, unreadCount = s.unread.size, unreadMessages = s.unread)
        }
        shot("110_en_search_tags") { SearchScreen(s.searchTagged, autoFocus = false) }
        shot("111_en_search_selection") { SearchScreen(s.searchTagged, selectedIds = s.searchSelected, autoFocus = false) }
        shot("112_en_inbox_category_colors") {
            InboxScreen(
                s.inboxColored, s.sample.rules, s.sample.folderCounts, s.sample.dateLine,
                customFolders = s.coloredCustomFolders, customCounts = mapOf(1L to 0, 2L to 0), builtInColors = s.builtInColors,
            )
        }
        shot("118_en_inbox_mark_all_read") {
            InboxScreen(s.sample.inbox, s.sample.rules, s.sample.folderCounts, s.sample.dateLine, unreadCount = s.unread.size)
        }
        shot("119_en_inbox_mark_all_read_360dp") {
            Box(Modifier.width(360.dp)) { InboxScreen(s.sample.inbox, s.sample.rules, s.sample.folderCounts, s.sample.dateLine, unreadCount = 128) }
        }
        shot("120_en_mark_all_read_dialog") {
            InboxScreen(s.sample.inbox, s.sample.rules, s.sample.folderCounts, s.sample.dateLine, unreadCount = s.unread.size)
            Scrim { MarkAllReadDialogContent(s.unread.size, stringResource(R.string.common_folder_all), {}, {}) }
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
