package com.havivon.pigeon.domain.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules of version 6 ([SpamScorer.VERSION]): ads with an opt-out line, phishing, lures, slang, campaigns. All made up. */
class SpamRulesV6Test {

    private fun verdict(body: String, address: String = "0541234567", contact: Boolean = false, wrote: Boolean = false) =
        SpamScorer.score(IncomingSms(address, if (contact) "Dana" else null, body, isContact = contact, userWrote = wrote))

    private fun assertSpam(vararg cases: Pair<String, String>) {
        for ((body, address) in cases) {
            val v = verdict(body, address)
            assertTrue("$address: $body -> ${v.score} ${v.reasons}", v.isSpam)
        }
    }

    private fun assertNotSpam(vararg cases: Pair<String, String>) {
        for ((body, address) in cases) {
            val v = verdict(body, address)
            assertFalse("$address: $body -> ${v.score} ${v.reasons}", v.isSpam)
        }
    }

    @Test
    fun theRulesAreVersionSix() {
        assertEquals(6, SpamScorer.VERSION)
    }

    @Test
    fun anAdWithTheOptOutLineFromAnUnknownSenderIsSpam() {
        assertSpam(
            "מהפך בשוק הסלולר! חברת הסלולר שלכם גובה מכם יותר מידי כסף? בדקו עכשיו אצלנו. להסרה השב הסר" to "0525556677",
            "סוף עונה אצלנו בחנות, כל הנעליים ב-40 אחוז הנחה עד יום שישי. להסרה הקש 9" to "+972541234567",
            "קיץ בסטייל? ביגוד ונעליים בחנויות ובאתר שלנו, אל תפספסו את הקולקציה החדשה. להסרה: x" to "ShopX",
            "מגוון מבצעים על יינות ומשקאות בסניפי הרשת, מגיעים ונהנים מקוקטייל על חשבון הבית. להסרה השב הסר" to "WineBar",
        )
    }

    @Test
    fun serviceNoticesAndKnownSendersWithTheOptOutLineStayInTheInbox() {
        assertNotSpam(
            "תזכורת לתור במרפאה ביום חמישי בשעה 16:00 אצל ד\"ר כהן, נא להגיע 10 דקות לפני. להסרה השב הסר" to "0779990000",
            "תושבים יקרים, מתחם החיסונים ברחוב הרצל יפעל מחר בין השעות 9 עד 15, נתראה שם. להסרה השב הסר" to "0559709535",
            "הודעת עירייה: עבודות ריבוד בכבישים בשכונה ביום שלישי, נא לא להחנות רכב ברחוב. להסרה השב הסר" to "RamatCity",
            "פוליסת ביטוח הרכב שלך מס' 5512345 חודשה בהצלחה לשנה נוספת, תודה שבחרת בנו. להסרה השב הסר" to "0525556677",
            "מבצע סוף עונה! 20 אחוז הנחה בכל החנויות עד סוף השבוע, מחכים לכם. להסרה השב הסר" to "SuperPharm",
            "להסרה השב הסר" to "0525556677",
        )
        assertEquals(0, verdict("מבצע סוף עונה אצלנו בחנות, הכל בהנחה עד סוף השבוע. להסרה השב הסר", contact = true).score)
        assertEquals(0, verdict("מבצע סוף עונה אצלנו בחנות, הכל בהנחה עד סוף השבוע. להסרה השב הסר", wrote = true).score)
    }

    @Test
    fun phishingAboutLockedAccountsAndWrongParcelAddresses() {
        assertSpam(
            "חשבון Bit שלך ננעל מסיבות אבטחה. אנו מזהים כניסה ממכשיר לא ידוע, אמת את זהותך: https://bit.ly/x1" to "0551110000",
            "החשבון שלך ננעל עבור אבטחה, אנא אמת זהותך במהלך היום https://is.gd/Ab12" to "0551110001",
            "הכתובת של החבילה שלך שגויה אנא אשר את הכתובת: https://shorturl.at/abc12" to "+972552220000",
            "מספר רחוב חסר בחבילה, בבקשה תעדכן: https://t.ly/Post12" to "+972552220001",
            "המשלוח שלך נעצר - צפה בפרטים bit.ly/SIif9yO" to "0587654321",
            "המשלוח שלך נעצר - צפה בפרטים otucay.link/SIif9yO" to "0587654322",
            "חשבון Bit שלך ננעל מסיבות אבטחה. אנו מזהים כניסה ממכשיר לא ידוע, אמת זהותך עוד היום" to "0551110009",
            "שלום יקירי, אנא עדכן את הנתונים שלך באופן מיידי כדי להבטיח המשכיות השירות דרך הקישור https://bit.ly/aB3" to "033069889",
            "תמיכת PayPal: הגבלנו זמנית את מה שאתה יכול לעשות עם החשבון שלך, לחץ כאן https://rebrand.ly/xy1" to "0557295395",
        )
        // The real thing stays: a courier's address question, a bank's notice
        assertNotSpam(
            "שלום דני, הכתובת לא נמצאה במערכת. נא להשיב עם רחוב ומספר בית ונמסור היום" to "0501231231",
            "חשבונך ננעל לאחר ניסיונות כניסה שגויים, יש לפנות לסניף עם תעודה מזהה" to "Leumi",
        )
    }

    @Test
    fun pensionWithdrawalAndClassActionLures() {
        assertSpam(
            "ניתן למשוך את כספי הפנסיה והפיצויים בלי להתפטר! לבדיקה bit.ly/pens7" to "PensiaNow",
            "בעקבות המצב נפתחה זכאות למשיכת קופות פנסיה ללא התפטרות מהעבודה תוך ימים בודדים. להסרה השב הסר" to "0532805024",
            "במסגרת הסכם פשרה בתביעה ייצוגית הינך זכאי לשדרוג חינם. לפרטים התקשרו לנציג שלנו" to "0537037907",
        )
        assertNotSpam(
            "הדוח השנתי של קרן הפנסיה שלך זמין באזור האישי באתר הקרן" to "PensionFund",
            "תודה על הפנייה אלינו, נציג יחזור אליך בהקדם בנושא ההתפטרות" to "0521112222",
        )
    }

    @Test
    fun cannabisAndPotencyAdsInSellersSlang() {
        assertSpam(
            "5 ג' מתנה 🎁 בקניה !!! שקיות וקופסאות רפואי wa.me/972545744495" to "+972549173299",
            "חממה פרימיום איכותית 25ג'=500 50ג'=800 https://wa.me/972534619532" to "+972548365992",
            "מגוון רפואי / חממה חדש בלונים בתפריט https://wa.me/972544992312" to "+972542976285",
            "שוברים את השוק 10g רפואי = 280 ש\"ח https://wa.me/972508148508" to "+972548380696",
            "שקיות רפואי ללא צורך במרשם רופא https://wa.me/message/AB12CD34" to "+972544319295",
            "ויאגרה וסיאליס במחירים מיוחדים, להזמנות משלוח עד הבית https://wa.me/972501112233" to "+972552221111",
        )
        assertNotSpam(
            "הגינה שלנו מחכה: בנינו חממה חדשה בחצר ונשמח לעזרה בסופ\"ש" to "0521112222",
            "מרשם לתרופה נשלח לבית המרקחת. ניתן לאסוף היום" to "Maccabi",
            "הציוד הרפואי שביקשת מוכן לאיסוף, אפשר לבוא עד שש בערב" to "0521113333",
        )
    }

    @Test
    fun gamblingAds() {
        assertSpam(
            "ליגת האלופות הערב! בונוס הפקדה של 100 אחוז ומשיכות מהירות https://bit.ly/aB3" to "0503284087",
            "הרולטה חוזרת! סיבובים חינם לכל מי שמצטרף היום, בונוס הפקדה ראשונה https://casino.example/x" to "0524444444",
            "פוקר אונליין עם טורנירים בחינם ובונוסים בפתיחת חשבון https://bit.ly/pk1" to "0597778888",
            "Roulette and free spins tonight with a deposit bonus: https://bit.ly/xx12" to "+447700900111",
        )
        assertNotSpam(
            "ערב פוקר אצל דני ביום שישי, מי מביא שתייה" to "0521112222",
        )
    }

    @Test
    fun investmentTipsInEnglish() {
        assertSpam("Goldman: We will invest in a skyrocketing stock with returns of more than 70%. Join us and start for free" to "Goldman")
        assertNotSpam("Your monthly investment report is ready in the app" to "0521112222")
    }

    @Test
    fun politicalCampaignsByTheNamesOfPoliticiansAndParties() {
        assertSpam(
            "חברים, נתניהו או גנץ? ההכרעה בידיים שלכם, צאו והביאו את כל המשפחה" to "0559709469",
            "שלום, כאן לפיד. ביחד עם בנט נחליף את השלטון, מצטרפים אלינו" to "BennettX",
            "הליכוד או כחול לבן, יום שלישי הקרוב מכריעים בקלפי" to "0529993536",
            "בלי בן גביר אין ממשלת ימין, תצביעו עוצמה יהודית ביום שלישי" to "BenX",
        )
        // A single name in a plain sentence, a neighbour's talk about elections, a municipality
        assertNotSpam(
            "אז מי ניצח בדיון אתמול, נתניהו לא הגיע בסוף" to "0521112222",
            "מחר בחירות, נלך יחד לקלפי?" to "0541239999",
            "עיריית תל אביב: ביום הבחירות יופעלו הסעות לקלפי. פרטים באתר העירייה" to "TelAviv",
        )
        assertEquals(0, verdict("נתניהו וגנץ ולפיד, מה דעתך על כולם", contact = true).score)
        assertEquals(0, verdict("נתניהו וגנץ ולפיד, מה דעתך על כולם", wrote = true).score)
    }

    @Test
    fun religiousChainMessagesInBulk() {
        assertSpam(
            "מה המתנה שלך לחיילים? 1. נר שבת 2. תפילין 3. שמע ישראל" to "0549162510",
            "כדי שלא יפלו חיילים מה המצווה שלך? 1 נר שבת 2תפילין 3שמע ישראל 4שבת" to "0549162511",
            "השמאל והערבים נוהרים לקלפיות. תצביעו ב' לבן גביר" to "BengX",
            "שניות אחרונות מחב\"ד סמסי אור לתזכורת נר שבת סמס כח לתזכורת תפילין חינם" to "+972537620880",
        )
        assertNotSpam(
            "שבת שלום לכולם, אל תשכחו להדליק נר שבת לפני השקיעה" to "0521112222",
        )
        assertEquals(0, verdict("מה תעשו? 1 נר שבת 2 תפילין 3 שמע ישראל", contact = true).score)
    }
}
