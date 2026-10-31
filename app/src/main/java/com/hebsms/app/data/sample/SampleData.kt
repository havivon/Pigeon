package com.hebsms.app.data.sample

import com.hebsms.app.domain.model.ColorRule
import com.hebsms.app.domain.model.Conversation
import com.hebsms.app.domain.model.Folder
import com.hebsms.app.domain.model.Message
import com.hebsms.app.domain.model.RuleMatch
import com.hebsms.app.domain.model.ThreadItem

/** Made-up content for the design preview. No real people, numbers or messages. */
object SampleData {

    val rules = listOf(
        ColorRule(1, "בנק", RuleMatch.Sender("הבנק"), 0xFF1E9E6A),
        ColorRule(2, "משלוחים", RuleMatch.Keyword("משלוח"), 0xFFF08A24),
        ColorRule(3, "משפחה", RuleMatch.Sender("אמא"), 0xFFE0559B),
        ColorRule(4, "עבודה", RuleMatch.Keyword("פגישה"), 0xFF2F80ED, enabled = false),
    )

    val inbox = listOf(
        Conversation(1, "אמא", "תביא לחם בדרך הביתה ❤️", "12:41", 0xFFE0559B, unread = 2, pinned = true, ruleId = 3),
        Conversation(2, "דני כהן", "ותביא בבקשה את הטופס החתום 🙏", "11:05", 0xFF2F80ED, pinned = true),
        Conversation(3, "הבנק", "חיוב בכרטיס המסתיים ב-4821 בסך 249.90 ₪", "10:32", 0xFF1E9E6A, unread = 1, ruleId = 1, folder = Folder.Banks),
        Conversation(4, "שליחויות", "המשלוח שלך יגיע היום בין 14:00 ל-18:00", "09:15", 0xFFF08A24, ruleId = 2, folder = Folder.Deliveries),
        Conversation(5, "Verify", "קוד האימות שלך הוא 482913", "08:50", 0xFF7C5CE0, unread = 1, folder = Folder.Codes, code = "482913"),
        Conversation(6, "נועה לוי", "חחח בדיוק חשבתי עלייך", "אתמול", 0xFF14A3B8),
        Conversation(7, "ועד הבית", "תזכורת: אסיפת דיירים ביום שלישי ב-20:00", "אתמול", 0xFF8D6E63),
        Conversation(8, "יוסי", "הפגישה זזה ל-15:30, מתאים?", "יום ה׳", 0xFF5C6BC0),
    )

    val spam = listOf(
        Conversation(
            101, "+972 55-123-4567", "זכית בפרס! לחץ כאן לקבלת המתנה שלך: bit.ly/x7Kq9", "13:02", 0xFFE53935,
            folder = Folder.Spam, spamReasons = listOf("שולח לא מוכר", "קישור מקוצר", "זכית"),
        ),
        Conversation(
            102, "Delivery", "חבילה ממתינה לך. יש לשלם 3.90 ₪ דמי משלוח: isr-post.co/pay", "07:44", 0xFFE53935,
            folder = Folder.Spam, spamReasons = listOf("דומיין מתחזה", "חבילה ממתינה"),
        ),
        Conversation(
            103, "054-000-1111", "הלוואה מיידית בלי בדיקות! להסרה השב הסר", "אתמול", 0xFFE53935,
            folder = Folder.Spam, spamReasons = listOf("שולח לא מוכר", "הסר"),
        ),
    )

    val folderCounts = mapOf(
        Folder.All to 4, Folder.Personal to 2, Folder.Banks to 1, Folder.Codes to 1,
        Folder.Deliveries to 0, Folder.Spam to spam.size,
    )

    val dannyThread = listOf(
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
    )
}
