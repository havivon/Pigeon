package com.havivon.pigeon.domain.analysis

import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.SpamReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules of version 8 ([SpamScorer.VERSION]): known senders (brands, authorities, municipalities) are judged by their
 * links and the scam signs only; new scam shapes. All made up.
 */
class SpamRulesV8Test {

    private fun verdict(body: String, address: String = "0541234567") = SpamScorer.score(IncomingSms(address, null, body))

    private fun assertNotSpam(body: String, address: String) {
        val v = verdict(body, address)
        assertFalse("$address -> ${v.score} ${v.reasons}", v.isSpam)
    }

    private fun assertSpam(body: String, address: String = "0541234567") {
        val v = verdict(body, address)
        assertTrue("$address -> ${v.score} ${v.reasons}", v.isSpam)
    }

    @Test
    fun theRulesAreVersionEight() {
        assertTrue(SpamScorer.VERSION >= 8)
    }

    // Known senders: their own mail

    @Test
    fun nationalInsuranceIsNotTakenForBankLeumi() {
        val body = "שלום רב, הביטוח הלאומי מזכיר: ניתן להגדיל את החיסכון לכל ילד באתר https://www.btl.gov.il/benefits"
        assertNotSpam(body, "BituahLeumi")
        assertFalse(verdict(body, "BituahLeumi").reasons.contains(SpamReason.BrandSender))
        // A fake one still is: the authority's name with a payment link off its sites
        assertSpam("ביטוח לאומי: נותר לך חוב לתשלום, יש להסדיר כאן https://btl-pay.top/x7Kq9 לפני העברה לאכיפה", "BituahLeumi")
    }

    @Test
    fun aHealthFundsSurveyWithAnOptOutLineIsItsOwnMail() {
        val body = "שלום, נשמח למענה על שאלון קצר על הביקור במרפאה. לסקר: https://survey.example-maccabi.co.il/q1 להסרה השב הסר"
        for (address in listOf("Maccabi", "MaccabiSKR", "Clalit")) assertNotSpam(body, address)
    }

    @Test
    fun aMunicipalitysRoadWorksNoticeIsNotAnAccountThreat() {
        val body = "תושבים יקרים, בשל עבודות ריבוד הצומת ייחסם לתנועה מחר בין 22:00 ל-05:00. למפה: https://bit.ly/RoadWorks22 להסרה השב הסר"
        for (address in listOf("RamatGan", "Or Yehuda", "TLV-Muni")) assertNotSpam(body, address)
        assertFalse(verdict(body, "0541234567").reasons.contains(SpamReason.Keyword("החשבון ייחסם")))
        // Next to an account it still is one
        assertTrue(verdict("חשבונך ייחסם היום, אמתו את הפרטים כאן https://bit.ly/Zx81q", "0541234567").isSpam)
    }

    @Test
    fun aKnownSendersLinksThroughTheSmsPlatformsAreItsOwn() {
        val body = "להזכירך: משלוח RS000000000IL ממתין לך ביחידת הדואר עד יום ה'. למעקב: https://sop1.biz/t/AbC12xYz/"
        assertNotSpam(body, "Israel_Post")
        assertFalse(verdict(body, "Israel_Post").reasons.contains(SpamReason.BrandSender))
        // From a stranger it is still a short link
        assertTrue(verdict("זכית! לקבלת הפרס: https://l5k.me/Ab12Cd", "0541234567").reasons.contains(SpamReason.ShortLink))
    }

    @Test
    fun aBrandsAdKeepsToItsCategory() {
        val body = "היי, החודש מצטרפים ונהנים מ-20% הנחה בחשבון. לפרטים לחצו כאן https://bit.ly/Summer26x להסרה השב הסר"
        assertNotSpam(body, "Bezeq")
        assertNotSpam(body, "HarelSummer")
        assertEquals(Folder.Telecom, FolderClassifier.classify(IncomingSms("Bezeq", null, body)))
    }

    @Test
    fun aBanksLoanOfferAndTheLotterysRaffleAreTheirBusiness() {
        assertNotSpam("הלוואה בתנאים מיוחדים ללקוחות הבנק, לפרטים: https://www.leumi.co.il/loan26 להסרה השב הסר", "Leumi")
        assertNotSpam("הגרלה ענקית החודש! מתנה חינם לכל משתתף: https://bit.ly/Draw26x להסרה השב הסר", "ManuiPais")
        // A stranger's loan offer with a short link still is spam, and so is a known name with "you won"
        assertSpam("הלוואה בלי בדיקות, אישור מיידי: https://bit.ly/Loan26x")
        assertSpam("זכית בהגרלה! לקבלת הפרס: https://bit.ly/Prize26x", "ManuiPais")
    }

    @Test
    fun anOfficialElectionNoticeIsNoCampaign() {
        assertNotSpam("שלום, הבחירות לכנסת יתקיימו ביום שלישי. הנך מצביע/ה בקלפי מספר 12. פרטים: https://www.gov.il/elections", "PibaGovIL")
    }

    // New scam shapes

    @Test
    fun aFakeEmergencyAlertWithALinkIsSpam() {
        assertSpam("התרעת חירום! יש להיכנס מייד למרחב המוגן ולהתקין את העדכון https://version-update.xyz", "OrefAlert")
        assertSpam("התרעת חירום! יש להיכנס מייד למרחב המוגן ולהתקין את העדכון https://version-update.xyz")
        // The real alert has no link
        assertNotSpam("התרעת חירום: יש להיכנס למרחב המוגן ולשהות בו 10 דקות", "PikudHaoref")
    }

    @Test
    fun aStoppedOrUnpaidParcelIsSpam() {
        assertSpam("סטטוס משלוח: IL0000000000 נעצר - ראה עוד https://parcel-info.com/abc")
        assertSpam("ISRAIL POST : Hello, your shipping number IL-0000000000 Pending: Please pay https://post-fees.com/il/")
        assertSpam("קונה יקר, עליך לאשר את פרטי המשלוח בהקדם. אם לא תאשר את המידע, המשלוח יוחזר. לחץ כאן https://ship-confirm.com/a")
        // A pickup reminder that says the parcel goes back if not collected is not
        assertNotSpam("שלום, משלוח שלך ממתין לאיסוף בחנות. אם לא ייאסף תוך 7 ימים המשלוח יוחזר לשולח. פרטים: https://www.epost.co.il/track", "0541234567")
    }

    @Test
    fun escortAdsLoginLuresAndPlanEndsAreSpam() {
        assertSpam("היי מאמי, אני דוגמנית בת 22, מארחת אותך בדירה דיסקרטית. לפרטים בוואטסאפ")
        assertSpam("קישור לגבי הכניסה האחרונה לחשבונך: https://bit.ly/3uAbCdE")
        assertSpam("לקוח יקר, חבילת הסלולר שלך מסתיימת היום. לחידוש במבצע של 2.50 ש\"ח לחודש: https://renew-now.com")
    }

    @Test
    fun drugsInAPriceListOrALinksNameAreSpam() {
        assertSpam("בוטיק10=400 פרימיום20=550 https://t.me/kanabis_example")
        assertSpam("וודינג קייק 450₪ עד שעה https://wa.me/message/ABCDEF")
    }

    @Test
    fun gamblingBonusesAndPoliticiansAsSendersAreSpam() {
        assertSpam("פלייאוף הערב! Spin&Win כל סיבוב זוכה, בונוס הצטרפות 100% בעמוד הבונוסים", "LuckyBet")
        assertSpam("ההקלטה שנחשפה הערב: https://bit.ly/Leak2026", "BBNetanyahu")
        // A politician's name in an ordinary sentence from a stranger stays below
        assertNotSpam("ראית מה גנץ אמר אתמול בחדשות?", "0541234567")
    }
}
