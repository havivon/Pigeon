package com.havivon.pigeon.domain.analysis

import com.havivon.pigeon.domain.model.Folder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FolderClassifierTest {

    private fun classify(
        body: String,
        address: String = "0541234567",
        contact: Boolean = false,
        name: String? = null,
        allowed: List<String> = emptyList(),
        blocked: List<String> = emptyList(),
        wrote: Boolean = false,
    ) = FolderClassifier.classify(IncomingSms(address, name, body, contact, allowed, blocked, userWrote = wrote))

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
                Triple("Your account balance is \$1,500.00", "0501110000", false),
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
    fun banksByTheirSenderIds() {
        val senders = listOf(
            "Leumi", "BankLeumi", "Leumi-Card", "Hapoalim", "Poalim", "BankHapoalim", "Discount", "DiscountBank", "Mizrahi-Tefahot",
            "MizrahiTfahot", "FIBI", "Beinleumi", "Yahav", "Bank Yahav", "Mercantile", "BankJerusalem", "Massad", "OtsarHahayal",
            "OneZero", "Pepper", "Isracard", "Max", "MAX-IT", "CAL", "Visa Cal", "AMEX", "American Express", "Diners", "bit",
            "PayBox", "PayPal", "Chase", "HSBC", "Bank of Somewhere",
            "בנק לאומי", "לאומי", "בנק הפועלים", "דיסקונט", "מזרחי טפחות", "הבינלאומי", "בנק יהב", "בנק ירושלים", "אוצר החייל",
            "ישראכרט", "מקס", "כאל", "ויזה כאל", "ביט",
        )
        for (sender in senders) assertEquals(sender, Folder.Banks, classify("עדכון לגבי הפעילות האחרונה", address = sender))
    }

    @Test
    fun banksByTheShapeOfATransaction() {
        check(
            Folder.Banks,
            listOf(
                Triple("חיוב בסך 89.90 ש\"ח בכרטיס המסתיים ב-1234", "0527000000", false),
                Triple("בוצעה עסקה בסך ₪250 בבית קפה", "Notify", false),
                Triple("הופקדו 1,000 ₪ לחשבונך", "Notify", false),
                Triple("נמשכו 500 ₪ מחשבונך בכספומט", "0527000000", false),
                Triple("יתרת העו״ש שלך: 4,321.50 ₪", "Notify", false),
                Triple("כרטיסך שמסתיים ב-9876 חויב ב-120 ש״ח", "Notify", false),
                Triple("Your card ending in 4821 was charged \$12.99", "Alerts", false),
                Triple("A withdrawal of \$200 was made from your account", "Alerts", false),
                Triple("\$1,250.00 was deposited to your checking account", "Alerts", false),
                Triple("Transaction: 45.00 USD at Store, card x4821", "Alerts", false),
                Triple("Your account balance is £300", "+447700900123", false),
                Triple("€30 was transferred to your account", "Alerts", false),
            ),
        )
    }

    @Test
    fun adsAreNotBanks() {
        check(
            Folder.All,
            listOf(
                // "כרטיס" is a ticket too, "אשראי" and "תשלומים" are how a shop sells
                Triple("כרטיסים להופעה במחיר מיוחד! רק 99 ₪ לכרטיס", "Tickets", false),
                Triple("מבצע! 12 תשלומים ללא ריבית בכרטיס אשראי", "SuperPharm", false),
                Triple("ללא חיוב נוסף! מנוי חודשי ב-29.90 ₪", "YesTV", false),
                Triple("יתרת הנקודות שלך: 250. ממשו עכשיו!", "Shufersal", false),
                Triple("נשארה לך יתרה של 50 ₪ בשובר המתנה", "Fox", false),
                Triple("עסקה משתלמת: 2 ב-100 ₪", "Castro", false),
                Triple("הזוכה בהגרלה יקבל 500 ₪!", "Lotto", false),
                Triple("המבצע נמשך עד מוצאי שבת, הכל ב-₪20", "Zara", false),
                Triple("יתרת החשבון לתשלום: 150 ₪", "Cellcom", false),
                Triple("חיוב החשבון החודשי שלך יבוצע בקרוב", "Partner", false),
                Triple("קחו בחשבון: המבצע ב-49 ₪ רק היום", "Dominos", false),
                Triple("Get 20% off your next purchase over \$50", "ShopNow", false),
                Triple("Credit card offer: 0% APR, apply now", "OfferCo", false),
                Triple("Your balance of points: 1,200. Redeem now", "Rewards", false),
                // Names that only look like a bank's
                Triple("הכל ב-10 ₪ בסניפים", "Max Stock", false),
                Triple("תזכורת לתור", "Leumit", false),
                Triple("מבצעי סוף שבוע", "Cal Fitness", false),
                Triple("Big sale", "Bit Shop", false),
                Triple("Hello", "Maxim Pizza", false),
            ),
        )
    }

    @Test
    fun aLoneBankWordFromAStrangersNumberIsNoBank() {
        check(
            Folder.All,
            listOf(
                Triple("יש לי כרטיס נוסף להופעה, רוצה?", "0541234567", false),
                Triple("שילמתי בכרטיס אשראי, תחזיר לי 50 ₪", "0541234567", false),
                Triple("What's your balance on the game?", "+14155550100", false),
            ),
        )
    }

    @Test
    fun contactsWhoseNamesLookLikeABankStayPersonal() {
        assertEquals(Folder.Personal, classify("מה נשמע?", address = "0541234567", contact = true, name = "Max"))
        assertEquals(Folder.Personal, classify("נתראה", address = "0541234567", contact = true, name = "Dan Mizrahi"))
        assertEquals(Folder.Personal, classify("היי", address = "0541234567", contact = true, name = "יוסי מזרחי"))
        assertEquals(Folder.Personal, classify("חויבתי ב-50 ₪ בכרטיס המסתיים ב-1234", address = "0541234567", contact = true, name = "Mom"))
        assertEquals(Folder.Banks, classify("עדכון", address = "0391234567", contact = true, name = "Isracard"))
    }

    @Test
    fun deliveriesByTheShapeOfAnUpdate() {
        check(
            Folder.Deliveries,
            listOf(
                Triple("החבילה שלך יצאה למשלוח", "Shop", false),
                Triple("ההזמנה שלך נשלחה, מספר מעקב: 5501234567", "Shop", false),
                Triple("חבילה ממתינה לך בנקודת האיסוף", "Lockers", false),
                Triple("המשלוח נמסר ללקוח", "0527000000", false),
                Triple("Your order has shipped", "ShopNow", false),
                Triple("Your parcel is out for delivery", "Couriers", false),
                Triple("Tracking number: 1Z999AA10123456784", "ShopNow", false),
                Triple("Your shipment RR123456789IL is in transit", "ShopNow", false),
                Triple("עדכון", "HFD", false),
                Triple("Your food is on its way", "Wolt", false),
            ),
        )
    }

    @Test
    fun adsAreNotDeliveries() {
        check(
            Folder.All,
            listOf(
                Triple("משלוח חינם בכל הזמנה מעל 200 ₪!", "Fox", false),
                Triple("פיצה משפחתית ב-49 ₪, עכשיו עם משלוח חינם", "Dominos", false),
                Triple("חבילת גלישה 50GB ב-29 ₪ בלבד", "Cellcom", false),
                Triple("החבילה שלך עומדת להסתיים, חדשו עכשיו", "Partner", false),
                Triple("ההזמנה תגיע עד הבית תוך 3 ימי עסקים", "Ikea", false),
                Triple("משלוחים לכל הארץ, הזמינו עכשיו", "Shop", false),
                Triple("Free shipping on all orders this weekend", "ShopNow", false),
                Triple("Track your order easily with our new app", "AppCo", false),
                Triple("New packages for your summer vacation", "Travel", false),
                Triple("Your Uber is arriving in 2 minutes", "Uber", false),
                Triple("We deliver to Groups too", "Groups", false),
            ),
        )
    }

    @Test
    fun personalIsContactsAndThePeopleWrittenTo() {
        check(
            Folder.Personal,
            listOf(
                Triple("תביא לחם בדרך הביתה ❤️", "0541234567", true),
                Triple("המשלוח הגיע, תודה!", "0541234567", true),
                Triple("The package arrived, thanks", "Mom", true),
                Triple("בדיקה של היתרה? חיוב כרטיס", "Mom", true),
            ),
        )
        // Not saved, but the user wrote to them (also all there is without the contacts permission)
        assertEquals(Folder.Personal, classify("מה קורה?", address = "+972541234567", wrote = true))
        assertEquals(Folder.Personal, classify("Are we still on for dinner?", address = "+14155550100", wrote = true))
        assertEquals(Folder.Personal, classify("המשלוח הגיע, תודה!", address = "0541234567", wrote = true))
    }

    @Test
    fun aStrangersNumberIsNotPersonal() {
        check(
            Folder.All,
            listOf(
                Triple("מבצע על פיצות היום בלבד!", "0541234567", false),
                Triple("קורס דיגיטל חדש נפתח, הירשמו עכשיו", "0541234567", false),
                Triple("מה קורה?", "+972541234567", false),
                Triple("Are we still on for dinner?", "+14155550100", false),
                Triple("שלום", "03-1234567", false),
                Triple("נתראה מחר ב-10:00", "0541234567", false),
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
        assertEquals(Folder.All, FolderClassifier.classify(sms, SpamVerdict(0, emptyList())))
        assertEquals(Folder.Personal, FolderClassifier.classify(sms.copy(isContact = true), SpamVerdict(0, emptyList())))
    }

    @Test
    fun allowedSendersAreNotSpamSoTheyFileNormally() {
        assertEquals(Folder.Codes, classify("זכית! קוד אימות 482913 bit.ly/x", address = "0551234567", allowed = listOf("0551234567")))
    }

    // --- insurance (made-up messages) ---

    @Test
    fun insuranceBySenderOrTheShapeOfAPolicy() {
        check(
            Folder.Insurance,
            listOf(
                Triple("עדכון לגבי הפוליסה שלך", "Harel", false),
                Triple("תזכורת", "HarelIns", false),
                Triple("הודעה חדשה", "מגדל", false),
                Triple("שלום", "Clal-Health", false),
                Triple("עדכון", "הפניקס", false),
                Triple("עדכון", "Menora Mivtachim", false),
                Triple("עדכון", "BituachYashir", false),
                Triple("עדכון בנוגע לפנייתך", "ביטוח לאומי", false),
                Triple("Your policy was renewed", "Lemonade", false),
                Triple("Update", "AIG", false),
                Triple("עדכון", "9000000", false),
                Triple("Update on your claim", "Allianz", false),
                Triple("פוליסה מס' 12345678 חודשה בהצלחה", "0527000000", false),
                Triple("תביעה מספר 445566 התקבלה ונמצאת בטיפול", "Notify", false),
                Triple("ביטוח הרכב שלך יפוג בעוד 14 יום", "Notify", false),
                Triple("Your car insurance policy has been renewed", "Alerts", false),
                Triple("Claim #123456 was approved", "Alerts", false),
            ),
        )
        assertEquals(Folder.Insurance, classify("עדכון", address = "0391234567", contact = true, name = "הראל ביטוח"))
    }

    @Test
    fun aLoneInsuranceWordOrAnInsuranceAdIsNotInsurance() {
        val cases = listOf(
            Triple("ביטוח רכב במחיר הכי זול! השוו עכשיו", "0541234567", false),
            Triple("הצעת מחיר לביטוח דירה תוך דקה", "Insure4U", false),
            Triple("מגדל חדש נפתח בעיר", "0541234567", false),
            Triple("כלל ראשון: לא לאחר", "0541234567", false),
            Triple("Hello", "Migdal Or", false),
            Triple("Lemonade stand opens today", "0541234567", false),
            Triple("Zurich trip photos", "+41441234567", false),
            Triple("מבצע! ביטוח נסיעות לחו\"ל ב-20% הנחה. להסרה השב הסר", "TravelCo", false),
            Triple("Get a quote for home insurance today", "+15550001111", false),
            Triple("חודש טוב!", "0541234567", false),
            Triple("Dikla's birthday party tonight", "0541234567", false),
            Triple("Our policy on returns has changed", "ShopNow", false),
            Triple("הפוליסה של הטלפון שלי נגמרה, יש לך המלצה?", "0541234567", true),
        )
        for ((body, address, contact) in cases) {
            val folder = classify(body, address, contact)
            assertNotEquals("$address: $body", Folder.Insurance, folder)
        }
        assertEquals(Folder.Personal, classify("הראל, מתי אתה מגיע?", address = "0541234567", contact = true, name = "Harel"))
    }

    @Test
    fun banksComeBeforeInsuranceAndInsuranceBeforeDeliveries() {
        assertEquals(Folder.Banks, classify("חיוב בסך 89.90 ש\"ח בכרטיס המסתיים ב-1234 עבור ביטוח הרכב שחודש", address = "Notify"))
        assertEquals(Folder.Insurance, classify("הפוליסה חודשה, המסמכים יצאו אליך במשלוח", address = "Notify"))
        assertEquals(Folder.Codes, classify("קוד האימות שלך הוא 482913", address = "Harel"))
    }
}
