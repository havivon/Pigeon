package com.hebsms.app.data.sample

import com.hebsms.app.domain.model.ColorRule
import com.hebsms.app.domain.model.Conversation
import com.hebsms.app.domain.model.Folder
import com.hebsms.app.domain.model.Message
import com.hebsms.app.domain.model.RuleMatch
import com.hebsms.app.domain.model.SpamReason
import com.hebsms.app.domain.model.ThreadItem

/** One language's worth of sample content: the same structure, ids and colours in every set. */
data class SampleSet(
    /** The inbox header's date line: the Hebrew date and the Gregorian one. */
    val dateLine: String,
    val rules: List<ColorRule>,
    val inbox: List<Conversation>,
    val spam: List<Conversation>,
    val folderCounts: Map<Folder, Int>,
    val dannyThread: List<ThreadItem>,
    /** The keyword typed into the "new rule" card. */
    val newRuleWord: String,
)

/** Made-up content for the design preview. No real people, numbers or messages. */
object SampleData {

    fun forLanguage(language: String): SampleSet = if (language == "iw" || language == "he") hebrew else english

    val hebrew: SampleSet = run {
        val spam = listOf(
            Conversation(
                101, "+972 55-123-4567", "זכית בפרס! לחץ כאן לקבלת המתנה שלך: bit.ly/x7Kq9", "13:02", 0xFFE53935,
                folder = Folder.Spam,
                spamReasons = listOf(SpamReason.UnknownSender, SpamReason.ShortLink, SpamReason.Keyword("זכית")),
            ),
            Conversation(
                102, "Delivery", "חבילה ממתינה לך. יש לשלם 3.90 ₪ דמי משלוח: isr-post.co/pay", "07:44", 0xFFE53935,
                folder = Folder.Spam,
                spamReasons = listOf(SpamReason.LookalikeDomain, SpamReason.Keyword("חבילה ממתינה")),
            ),
            Conversation(
                103, "054-000-1111", "הלוואה מיידית בלי בדיקות! להסרה השב הסר", "אתמול", 0xFFE53935,
                folder = Folder.Spam,
                spamReasons = listOf(SpamReason.UnknownSender, SpamReason.Keyword("הסר")),
            ),
        )
        SampleSet(
            dateLine = "כ׳ בחשוון תשפ״ז · 31.10.2026",
            rules = listOf(
                ColorRule(1, "בנק", RuleMatch.Sender("הבנק"), 0xFF1E9E6A),
                ColorRule(2, "משלוחים", RuleMatch.Keyword("משלוח"), 0xFFF08A24),
                ColorRule(3, "משפחה", RuleMatch.Sender("אמא"), 0xFFE0559B),
                ColorRule(4, "עבודה", RuleMatch.Keyword("פגישה"), 0xFF2F80ED, enabled = false),
            ),
            inbox = listOf(
                Conversation(1, "אמא", "תביא לחם בדרך הביתה ❤️", "12:41", 0xFFE0559B, unread = 2, pinned = true, ruleId = 3),
                Conversation(2, "דני כהן", "ותביא בבקשה את הטופס החתום 🙏", "11:05", 0xFF2F80ED, pinned = true),
                Conversation(3, "הבנק", "חיוב בכרטיס המסתיים ב-4821 בסך 249.90 ₪", "10:32", 0xFF1E9E6A, unread = 1, ruleId = 1, folder = Folder.Banks),
                Conversation(4, "שליחויות", "המשלוח שלך יגיע היום בין 14:00 ל-18:00", "09:15", 0xFFF08A24, ruleId = 2, folder = Folder.Deliveries),
                Conversation(5, "Verify", "קוד האימות שלך הוא 482913", "08:50", 0xFF7C5CE0, unread = 1, folder = Folder.Codes, code = "482913"),
                Conversation(6, "נועה לוי", "חחח בדיוק חשבתי עלייך", "אתמול", 0xFF14A3B8),
                Conversation(7, "ועד הבית", "תזכורת: אסיפת דיירים ביום שלישי ב-20:00", "אתמול", 0xFF8D6E63),
                Conversation(8, "יוסי", "הפגישה זזה ל-15:30, מתאים?", "יום ה׳", 0xFF5C6BC0),
            ),
            spam = spam,
            folderCounts = folderCounts(spam),
            dannyThread = listOf(
                ThreadItem.Day("אתמול"),
                ThreadItem.Msg(Message(1, "היי, מה קורה? יש לך זמן השבוע לעבור על ההצעה?", "18:20", outgoing = false)),
                ThreadItem.Msg(Message(2, "כן, בטח. מתי נוח לך?", "18:24", outgoing = true, delivered = true)),
                ThreadItem.Msg(Message(3, "אולי מחר בבוקר?", "18:25", outgoing = false)),
                ThreadItem.Day("היום"),
                ThreadItem.Msg(
                    Message(4, "סגרנו, מחר ב-10:00 אצלך. אני אביא את המסמכים", "10:58", outgoing = true, delivered = true, reminder = "מחר 10:00"),
                ),
                ThreadItem.Msg(Message(5, "מעולה. הכתובת: רחוב הרצל 12, קומה 3, דירה 7", "11:02", outgoing = false)),
                ThreadItem.Msg(Message(6, "ותביא בבקשה את הטופס החתום 🙏", "11:05", outgoing = false)),
            ),
            newRuleWord = "חשבונית",
        )
    }

    val english: SampleSet = run {
        val spam = listOf(
            Conversation(
                101, "+1 555-123-4567", "You won a prize! Tap here to claim your gift: bit.ly/x7Kq9", "1:02 PM", 0xFFE53935,
                folder = Folder.Spam,
                spamReasons = listOf(SpamReason.UnknownSender, SpamReason.ShortLink, SpamReason.Keyword("won")),
            ),
            Conversation(
                102, "Delivery", "A package is waiting for you. Pay a $3.90 delivery fee: usps-track.co/pay", "7:44 AM", 0xFFE53935,
                folder = Folder.Spam,
                spamReasons = listOf(SpamReason.LookalikeDomain, SpamReason.Keyword("package waiting")),
            ),
            Conversation(
                103, "555-000-1111", "Instant loan, no credit check! To opt out, reply STOP", "Yesterday", 0xFFE53935,
                folder = Folder.Spam,
                spamReasons = listOf(SpamReason.UnknownSender, SpamReason.Keyword("STOP")),
            ),
        )
        SampleSet(
            dateLine = "20 Cheshvan 5787 · Oct 31, 2026",
            rules = listOf(
                ColorRule(1, "Bank", RuleMatch.Sender("Bank"), 0xFF1E9E6A),
                ColorRule(2, "Deliveries", RuleMatch.Keyword("delivery"), 0xFFF08A24),
                ColorRule(3, "Family", RuleMatch.Sender("Mom"), 0xFFE0559B),
                ColorRule(4, "Work", RuleMatch.Keyword("meeting"), 0xFF2F80ED, enabled = false),
            ),
            inbox = listOf(
                Conversation(1, "Mom", "Pick up some bread on your way home ❤️", "12:41 PM", 0xFFE0559B, unread = 2, pinned = true, ruleId = 3),
                Conversation(2, "Danny Cole", "And please bring the signed form 🙏", "11:05 AM", 0xFF2F80ED, pinned = true),
                Conversation(3, "Bank", "Charge on the card ending in 4821 for $249.90", "10:32 AM", 0xFF1E9E6A, unread = 1, ruleId = 1, folder = Folder.Banks),
                Conversation(4, "Couriers", "Your delivery will arrive today between 2:00 PM and 6:00 PM", "9:15 AM", 0xFFF08A24, ruleId = 2, folder = Folder.Deliveries),
                Conversation(5, "Verify", "Your verification code is 482913", "8:50 AM", 0xFF7C5CE0, unread = 1, folder = Folder.Codes, code = "482913"),
                Conversation(6, "Nora Lewis", "lol I was just thinking about you", "Yesterday", 0xFF14A3B8),
                Conversation(7, "Building Committee", "Reminder: residents' meeting on Tuesday at 8:00 PM", "Yesterday", 0xFF8D6E63),
                Conversation(8, "Joe", "The meeting moved to 3:30 PM, does that work?", "Thu", 0xFF5C6BC0),
            ),
            spam = spam,
            folderCounts = folderCounts(spam),
            dannyThread = listOf(
                ThreadItem.Day("Yesterday"),
                ThreadItem.Msg(Message(1, "Hey, what's up? Do you have time this week to go over the proposal?", "6:20 PM", outgoing = false)),
                ThreadItem.Msg(Message(2, "Yes, sure. When works for you?", "6:24 PM", outgoing = true, delivered = true)),
                ThreadItem.Msg(Message(3, "Maybe tomorrow morning?", "6:25 PM", outgoing = false)),
                ThreadItem.Day("Today"),
                ThreadItem.Msg(
                    Message(4, "Done, tomorrow at 10:00 AM at your place. I'll bring the documents", "10:58 AM", outgoing = true, delivered = true, reminder = "Tomorrow 10:00 AM"),
                ),
                ThreadItem.Msg(Message(5, "Great. The address: 12 Maple Street, floor 3, apt 7", "11:02 AM", outgoing = false)),
                ThreadItem.Msg(Message(6, "And please bring the signed form 🙏", "11:05 AM", outgoing = false)),
            ),
            newRuleWord = "invoice",
        )
    }

    private fun folderCounts(spam: List<Conversation>) = mapOf(
        Folder.All to 4, Folder.Personal to 2, Folder.Banks to 1, Folder.Codes to 1,
        Folder.Deliveries to 0, Folder.Spam to spam.size,
    )
}
