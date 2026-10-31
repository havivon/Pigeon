package com.havivon.pigeon.domain.analysis

import com.havivon.pigeon.domain.model.Folder
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderClassifierTest {

    private fun classify(
        body: String,
        address: String = "0541234567",
        contact: Boolean = false,
        name: String? = null,
        allowed: List<String> = emptyList(),
        blocked: List<String> = emptyList(),
    ) = FolderClassifier.classify(IncomingSms(address, name, body, contact, allowed, blocked))

    private fun check(expected: Folder, cases: List<Triple<String, String, Boolean>>) {
        for ((body, address, contact) in cases) assertEquals("$address: $body", expected, classify(body, address, contact))
    }

    @Test
    fun spamComesFirst() {
        check(
            Folder.Spam,
            listOf(
                // A spam message that also has a code and bank words is still spam.
                Triple("זכית! קוד אימות 482913 לחץ כאן bit.ly/x חיוב יתרה", "0551234567", false),
                Triple("Your package is waiting. Pay the delivery fee: dhl-parcel.info/track", "+4477009000", false),
                Triple("חבילה ממתינה לך. דמי משלוח: isr-post.co/pay", "Delivery", false),
                Triple("Pre-approved loan! Click here cutt.ly/a", "0521112222", false),
            ),
        )
        assertEquals(Folder.Spam, classify("חיוב של 150 ש\"ח", address = "Leumi", blocked = listOf("חיוב")))
    }

    @Test
    fun codesBeatBanksAndDeliveries() {
        check(
            Folder.Codes,
            listOf(
                Triple("קוד האימות שלך הוא 482913", "37133", false),
                Triple("Your verification code is 482913", "Google", false),
                Triple("קוד 551122 לאישור חיוב בכרטיס המסתיים ב-4821", "Leumi", false),
                Triple("Your DHL delivery code is 7788", "DHL", false),
                Triple("קוד איסוף 4455 לחבילה שלך בלוקר", "Cheetah", false),
                Triple("G-123456 is your Google verification code.", "Google", false),
                Triple("הקוד של הדלת הוא 4821", "0541234567", true),
            ),
        )
    }

    @Test
    fun banksBySenderOrWords() {
        check(
            Folder.Banks,
            listOf(
                Triple("חיוב של 150 ש\"ח בכרטיס המסתיים ב-4821 בוצע ב-Aroma", "0527000000", false),
                Triple("יתרתך בחשבון עודכנה", "Leumi", false),
                Triple("היתרה בחשבונך היא 3,200 ₪", "0527000000", false),
                Triple("בוצעה עסקה בכרטיס שלך", "Isracard", false),
                Triple("Transaction of \$45.00 at Amazon", "VISA", false),
                Triple("Your balance is \$1,500.00", "0501110000", false),
                Triple("Your card was debited", "Chase", false),
                Triple("שלום, מעדכנים אותך שהחשבון נפתח", "Bank-Hapoalim", false),
                Triple("עדכון חשוב", "BANKLEUMI", false),
                Triple("עדכון חשוב", "בנק דיסקונט", false),
                Triple("הודעה חדשה", "Max", false),
            ),
        )
    }

    @Test
    fun deliveriesBySenderOrWords() {
        check(
            Folder.Deliveries,
            listOf(
                Triple("המשלוח שלך יוצא היום", "0527000000", false),
                Triple("השליח בדרך אליך", "0527000000", false),
                Triple("החבילה שלך הגיעה לסניף", "IsraelPost", false),
                Triple("Your package has shipped. Tracking: RR123456789IL", "0501110000", false),
                Triple("Your delivery is out for delivery today", "UPS", false),
                Triple("Your parcel arrived", "0501110000", false),
                Triple("עדכון", "דואר ישראל", false),
                Triple("update", "Cheetah", false),
                Triple("תודה", "DHL Express", false),
            ),
        )
    }

    @Test
    fun personalIsContactsAndPhoneNumbers() {
        check(
            Folder.Personal,
            listOf(
                Triple("תביא לחם בדרך הביתה ❤️", "0541234567", true),
                Triple("מה קורה?", "+972541234567", false),
                Triple("Are we still on for dinner?", "+14155550100", false),
                Triple("שלום", "03-1234567", false),
                Triple("נתראה מחר ב-10:00", "0541234567", false),
                Triple("המשלוח הגיע, תודה!", "0541234567", true),
                Triple("The package arrived, thanks", "Mom", true),
                Triple("בדיקה של היתרה? חיוב כרטיס", "Mom", true),
            ),
        )
    }

    @Test
    fun aBankSavedAsAContactIsStillABank() {
        assertEquals(Folder.Banks, classify("עדכון", address = "0391234567", contact = true, name = "בנק הפועלים"))
        assertEquals(Folder.Deliveries, classify("עדכון", address = "0391234567", contact = true, name = "דואר ישראל"))
    }

    @Test
    fun everythingElseStaysInAll() {
        check(
            Folder.All,
            listOf(
                Triple("מבצע סוף עונה! 20% הנחה בכל החנות. להסרה השב הסר", "SuperPharm", false),
                Triple("Your Uber is arriving in 2 minutes", "Uber", false),
                Triple("התור שלך ביום שלישי ב-10:00", "Clinic", false),
                Triple("עדכון מערכת", "12345", false),
                Triple("Hello", "Maxim Pizza", false),
                Triple("", "Shufersal", false),
            ),
        )
    }

    @Test
    fun theVerdictCanBePassedIn() {
        val sms = IncomingSms("0541234567", null, "שלום", false)
        assertEquals(Folder.Spam, FolderClassifier.classify(sms, SpamVerdict(100, emptyList())))
        assertEquals(Folder.Personal, FolderClassifier.classify(sms, SpamVerdict(0, emptyList())))
    }

    @Test
    fun allowedSendersAreNotSpamSoTheyFileNormally() {
        assertEquals(Folder.Codes, classify("זכית! קוד אימות 482913 bit.ly/x", address = "0551234567", allowed = listOf("0551234567")))
    }
}
