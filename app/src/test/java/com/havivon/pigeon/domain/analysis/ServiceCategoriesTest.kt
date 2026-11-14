package com.havivon.pigeon.domain.analysis

import com.havivon.pigeon.domain.model.Folder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The six service categories of 0.7.0 (Telecom, Health, Bills, Transport, Travel, Shopping): for each, messages that must be in it
 * and messages that must not, among them that brand's own ads. Every message is made up.
 */
class ServiceCategoriesTest {

    private fun classify(
        body: String,
        address: String = "0541234567",
        contact: Boolean = false,
        name: String? = null,
        wrote: Boolean = false,
    ) = FolderClassifier.classify(IncomingSms(address, name ?: if (contact) "Dana" else null, body, contact, userWrote = wrote))

    private fun mustBe(expected: Folder, vararg cases: Pair<String, String>) {
        for ((body, address) in cases) assertEquals("$address: $body", expected, classify(body, address))
    }

    private fun mustNotBe(folder: Folder, vararg cases: Pair<String, String>) {
        for ((body, address) in cases) assertNotEquals("$address: $body", folder, classify(body, address))
    }

    private fun mustBeAll(vararg cases: Pair<String, String>) = mustBe(Folder.All, *cases)

    // --- ads ---

    /** A known company's ad stays in the company's category (its conversation does not leave it whenever the latest is an ad). */
    @Test
    fun aKnownSendersAdStaysInItsCategory() {
        mustBe(Folder.Telecom, "מבצע סלולר! 50GB ב-29 ₪ בלבד. להסרה השב הסר" to "Cellcom", "פרסומת: חבילת סיבים במחיר מיוחד" to "Bezeq", "פרסומת: עברו ל-Partner והרוויחו חודשיים חינם" to "Partner")
        mustBe(Folder.Bills, "מבצע גז! 20% הנחה בהחלפת בלון. להסרה השב הסר" to "Pazgas", "פרסומת: הנחה על חשבון החשמל עם חבילת חיסכון" to "IEC")
        mustBe(Folder.Transport, "מבצע נסיעות! להסרה השב הסר" to "Gett", "פרסומת: הנחה בנסיעה הראשונה" to "Yango")
        mustBe(Folder.Travel, "טיסה לאתונה ב-199$! להסרה השב הסר" to "Israir", "פרסומת: חבילות נופש באילת" to "Isrotel")
        mustBe(Folder.Shopping, "מבצע סוף עונה! 20% הנחה בכל החנות. להסרה השב הסר" to "Shufersal", "פרסומת: 1+1 על כל הפיצות" to "PizzaHut")
        // The Phoenix's ad, as it comes: an opt-out link and "no reply"
        assertEquals(Folder.Insurance, classify("סידרנו לך מגוון הטבות מיוחדות במוצרי הביטוח שלנו. להסרה מקבלת הודעות שיווקיות: https://y.fnx.co.il/x1 אין להשיב להודעה זו.", "Phoenix"))
        // Its text never decides: the same ad from an unknown sender id is no service's
        mustNotBe(Folder.Telecom, "מבצע סלולר! 50GB ב-29 ₪ בלבד. להסרה השב הסר" to "Sale4U")
        mustNotBe(Folder.Travel, "פרסומת: חבילות נופש באילת" to "0541234567")
    }

    // --- telecom ---

    @Test
    fun telecomBySenderOrTheNoticeOfALine() {
        mustBe(
            Folder.Telecom,
            "לקוח יקר, חשבונך טרם שולם. לתשלום היכנס לאזור האישי" to "Pelephone",
            "עודכן זיכוי בחשבונך, תודה שבחרת בנו" to "Cellcom",
            "הגדרות הגלישה נשלחו למכשירך, יש להפעיל אותו מחדש" to "Partner",
            "הועבר אליך דואר דיגיטלי באזור האישי" to "HOT",
            "תודה שפנית לבזק, פנייתך התקבלה" to "Bezeq",
            "הטכנאי יגיע לתיאום ביקור ביום ראשון בין 8 ל-12" to "Golan",
            "שלום, הודעה מהמוקד" to "4458",
            "הודעה לגבי הקו שלך" to "199",
            "עדכון על החשבון" to "HOT Mobile",
            "עדכון על החשבון" to "012",
            "עדכון על החשבון" to "019 Mobile",
            "עדכון על החשבון" to "We4G",
            "עדכון על החשבון" to "Rami Levy Communications",
            "עדכון על החשבון" to "Xfone",
            "עדכון על החשבון" to "yes",
            // From a number the sender is not named by, the shape of the text names a telecom company or a line
            "תודה שפנית לשירות הלקוחות של פלאפון, פנייתך בטיפול" to "0521234567",
            "הגדרות הגלישה שלך עודכנו" to "0541234567",
            "טכנאי אינטרנט יגיע אליך, תיאום ביקור במספר 03-1234567" to "0771234567",
        )
    }

    @Test
    fun notTelecom() {
        mustBeAll(
            "Business partner meeting at ten" to "0541234567",
            "כל הכרטיסים בהנחה" to "Yes Planet",
            "Hot dogs for lunch" to "Hot Dogs",
            "לקוח יקר, חשבונך טרם שולם" to "0541234567",
            "הטכנאי יגיע לתיאום ביקור מחר" to "0541234567",
            "Winery tour tomorrow" to "Golan Heights Winery",
        )
        // A saved contact named like a company is a person
        assertEquals(Folder.Personal, classify("מה נשמע", "0541234567", name = "Partner", contact = true))
        assertEquals(Folder.Personal, classify("הגדרות הגלישה", "0541234567", contact = true))
        // A bank's charge that names the company is the bank's
        assertEquals(Folder.Banks, classify("חיוב של 99 ש\"ח בכרטיס המסתיים ב-4821 בוצע ב-Partner", "Leumi"))
    }

    // --- health ---

    @Test
    fun healthBySenderOrAnAppointment() {
        mustBe(
            Folder.Health,
            "נקבע לך תור לד\"ר כהן ביום שלישי בשעה 10:30" to "Clalit",
            "תזכורת לתור מחר בשעה 09:15 במרפאה" to "Maccabi",
            "אישור מחלה נשלח אליך במייל" to "Meuhedet",
            "ביקור מרחוק עם הרופא יתחיל בעוד עשר דקות" to "Leumit",
            "תודה על תרומתך" to "MADA",
            "תוצאות הבדיקה זמינות באפליקציה" to "TIPATHALAV",
            "עדכון" to "Sheba",
            "עדכון" to "ShebaBeyond",
            "עדכון" to "MUSHLAM",
            "עדכון" to "Shila",
            "עדכון" to "MOR",
            "עדכון" to "Smile",
            "עדכון" to "odoro",
            "עדכון" to "Hadassah",
            "עדכון" to "Rambam Hospital",
            "המרשם שלך מוכן לאיסוף בבית המרקחת" to "SuperPharm",
            "נקבע לך תור לבדיקת דם ביום רביעי" to "0541234567",
            "תזכורת לתור אצל ד\"ר לוי מחר ב-16:00" to "0779990000",
        )
    }

    @Test
    fun notHealth() {
        mustBeAll(
            "שאלון קצר על ביקורך האחרון במרפאה" to "Clalit",
            "משוב: איך היה הביקור שלך?" to "Meuhedet",
            "הטבה מיוחדת לחברי מכבי: 20% הנחה בחנויות. להסרה השב הסר" to "Maccabi",
            "פרסומת: ד\"ר שיניים, תור לבדיקה ב-99 ₪" to "0541234567",
            "ד\"ר שפיר מזמין לסדנה על שינה" to "0541234567",
            "Latest accessories" to "Smile Mobile Accessories",
            "מבצעי סוף שבוע" to "Mor Shop",
        )
        mustNotBe(Folder.Health, "מריחואנה רפואית בלי מרשם! הזמינו עכשיו 054-9998877" to "0521234567")
        assertEquals(Folder.Personal, classify("אתה בא?", "0541234567", name = "Mor", contact = true))
        assertEquals(Folder.Personal, classify("נקבע לי תור למרפאה ביום חמישי", "0541234567", contact = true))
    }

    // --- bills and services ---

    @Test
    fun billsBySenderOrTheShapeOfABill() {
        mustBe(
            Folder.Bills,
            "מס' צרכן 1234567, חש' לתשלום 245 ₪" to "PAZGAS",
            "יתרת חוב: 120 ₪" to "IEC",
            "תשלום בסך 300 ₪ בוצע בהצלחה" to "GovPay",
            "תושבים יקרים, מחר תתבצע הפסקת מים בשכונה" to "RamatGan",
            "הודעה" to "עיריית חיפה",
            "הודעה" to "Mei Avivim",
            "הודעה" to "מי רמת גן",
            "הודעה" to "Tami4",
            "הודעה" to "Amisragas",
            "הודעה" to "Supergas",
            "הודעה" to "Moked 106",
            "הודעה" to "MySender",
            "הודעה" to "חברת החשמל",
            "הודעה" to "מועצה אזורית גזר",
            "הודעה" to "Moked109",
            "חש' לתשלום מס' 5512 עד 15.11" to "0771111111",
            "הארנונה לרבעון הבא נשלחה אליך" to "0541234567",
            "הודעה לתושבות יקרות: הפסקת חשמל מתוכננת" to "0541234567",
        )
    }

    @Test
    fun notBills() {
        mustBeAll(
            "Electric scooters on sale" to "ElectricCo",
            "פיצה במבצע" to "Water Park Deals",
            "מי זה שם בדלת" to "0541234567",
        )
        assertEquals(Folder.Personal, classify("מה שלומך", "0541234567", name = "מי כהן", contact = true))
        assertEquals(Folder.Personal, classify("יתרת חוב של 50 שקל, תחזיר לי", "0541234567", contact = true))
        // The vehicle's fee is the vehicle's
        assertEquals(Folder.Transport, classify("הודעה על אגרת רישוי שנתית לרכב שלך", "0541234567"))
        assertEquals(Folder.Transport, classify("עדכון", "mot"))
    }

    // --- transport ---

    @Test
    fun transportBySenderOrAParkingRideOrTestNotice() {
        mustBe(
            Folder.Transport,
            "להפעלת חניה בעיר השיבו 1" to "Pango",
            "החניה הסתיימה. סה\"כ 12.5 ₪" to "Cellopark",
            "הנהג בדרך אליך, יגיע בעוד 4 דקות" to "Gett",
            "Your Uber is arriving in 2 minutes" to "Uber",
            "טעינת רב קו בוצעה" to "Rav-Kav",
            "חשבונית נסיעה בכביש 6" to "Kvish6",
            "הודעה" to "Yango",
            "הודעה" to "Egged",
            "הודעה" to "ITURAN",
            "הודעה" to "Pointer",
            "הודעה" to "Israel Railways",
            "הודעה" to "RavKav",
            "הודעה" to "Fastlane",
            "תזכורת לטסט שנתי לרכב שלך" to "0541234567",
            "מועד הטסט שלך נקבע ליום חמישי" to "0541234567",
            "מבחן רישוי נקבע לרכבך" to "0541234567",
            "להפעלת חניה באזור 1 השיבו 1" to "0541234567",
        )
    }

    @Test
    fun notTransport() {
        mustBeAll(
            "חניה פנויה להשכרה ברמת גן, 500 ₪ לחודש" to "0541234567",
            "טסט למוצר החדש שלנו נשלח אליך" to "0541234567",
            "Coffee at the corner" to "Gett Coffee",
            "Fan club news" to "Pointer Sisters Fan Club",
            "The test results are in" to "0541234567",
        )
        assertEquals(Folder.Personal, classify("נסיעה טובה!", "0541234567", contact = true))
        assertEquals(Folder.Personal, classify("שמרתי לך חניה", "0541234567", contact = true))
    }

    // --- travel ---

    @Test
    fun travelBySenderOrAFlightOrAHotelBooking() {
        mustBe(
            Folder.Travel,
            "הטיסה שלך LY001 ביום שלישי, צ'ק-אין נפתח 24 שעות לפני" to "ELAL",
            "הזמנת מלון אושרה, צ'ק-אין ב-15.12" to "Fattal",
            "עדכון" to "ELAL MATMID",
            "עדכון" to "Israir",
            "עדכון" to "Arkia",
            "עדכון" to "Isrotel",
            "עדכון" to "Dan",
            "עדכון" to "Dan Hotels",
            "עדכון" to "Booking.com",
            "עדכון" to "Airbnb",
            "Your boarding pass for flight LY315 is ready, gate B4" to "0541234567",
            "הזמנת מלון בברצלונה אושרה" to "0541234567",
            "הטיסה שלך בעוד יומיים, צ'ק אין אונליין זמין" to "0541234567",
        )
    }

    @Test
    fun notTravel() {
        mustBeAll(
            "טיסה מוצלחת!" to "0541234567",
            "Hello there" to "Dan Cohen",
            "Hotel California tonight" to "Hotels Deals Ltd",
            "Flight simulator sale" to "Flight Games",
        )
        assertEquals(Folder.Personal, classify("אנחנו בטיסה, נתראה בלילה", "0541234567", contact = true))
        assertEquals(Folder.Personal, classify("מה נשמע", "0541234567", name = "Dan", contact = true))
    }

    // --- shopping ---

    @Test
    fun shoppingBySenderOrAReceiptOrAnOrderConfirmation() {
        mustBe(
            Folder.Shopping,
            "הגיעה אליך קבלה על סך 145 ₪: wee.ai/a1b2" to "ACE",
            "החשבונית שלך מוכנה לצפייה" to "Payngo",
            "אישור הזמנה מספר 55123 התקבל" to "Shufersal",
            "בחירתך נקלטה, תודה!" to "PizzaHut",
            "קישור לסיכום הזמנה: payper.co.il/x9" to "BINO EAT",
            "שובר מתנה בתוקף עד 31.12" to "buyme",
            "עדכון" to "MShuk",
            "עדכון" to "Correct",
            "עדכון" to "MODAN",
            "עדכון" to "NEHALIM",
            "עדכון" to "Htzone",
            "עדכון" to "HtzoneGift",
            "עדכון" to "Bee+gift",
            "עדכון" to "MyOrder",
            "עדכון" to "PaisPlus",
            "עדכון" to "Pais Plus",
            "עדכון" to "ManuiPais",
            "עדכון" to "LogisTeam",
            "עדכון" to "HTZ",
            "הגיעה אליך קבלה על רכישתך, תודה" to "0541234567",
            "Thank you for your purchase. Your receipt: wee.ai/x" to "0771234567",
        )
    }

    @Test
    fun notShopping() {
        mustBeAll(
            "Correct is the right answer" to "0541234567",
            "Hello" to "Correct Solutions Ltd",
            "חשבונית" to "0541234567",
            "קבלת פנים לאורחים ביום שישי" to "0541234567",
        )
        // A shop's own shipment update is a delivery
        assertEquals(Folder.Deliveries, classify("ההזמנה שלך נשלחה ויצאה למשלוח, מספר מעקב 8123456", "Shufersal"))
        assertEquals(Folder.Personal, classify("שלחתי לך את הקבלה", "0541234567", contact = true))
    }

    // --- the order when a message fits several ---

    @Test
    fun theMoreSpecificInstitutionWins() {
        // A code is a code, whoever sends it
        assertEquals(Folder.Codes, classify("קוד האימות שלך הוא 482913", "Pelephone"))
        // A bank's charge that names a shop is the bank's
        assertEquals(Folder.Banks, classify("חיוב של 150 ש\"ח בכרטיס המסתיים ב-4821 בוצע ב-Shufersal", "Leumi"))
        // A pharmacy is a shop by its sender, a prescription is health
        assertEquals(Folder.Health, classify("המרשם מוכן באיסוף", "SuperPharm"))
        assertEquals(Folder.Shopping, classify("הקבלה שלך מוכנה", "SuperPharm"))
        // Rami Levy the supermarket and Rami Levy Communications
        assertEquals(Folder.Shopping, classify("אישור הזמנה מספר 4455", "RamiLevy"))
        assertEquals(Folder.Telecom, classify("עדכון", "RamiLevyCom"))
        // A courier's update stays a delivery
        assertEquals(Folder.Deliveries, classify("החבילה שלך הגיעה לנקודת האיסוף", "Cheetah"))
    }

    @Test
    fun spamStillComesFirst() {
        val scam = "זכית! לחץ כאן bit.ly/x אישור הזמנה מספר 12345"
        assertEquals(Folder.Spam, classify(scam, "0551234567"))
    }

    @Test
    fun theSixHaveIconsLabelsAndCountAsMoveTargets() {
        val six = listOf(Folder.Telecom, Folder.Health, Folder.Bills, Folder.Transport, Folder.Travel, Folder.Shopping)
        for (f in six) {
            assertEquals(f.name, true, f.isMoveTarget)
            assertEquals(f.name, true, f.colourable)
        }
        // After Personal, before the older ones, in this order
        assertEquals(six, Folder.entries.dropWhile { it != Folder.Personal }.drop(1).take(6))
    }
}
