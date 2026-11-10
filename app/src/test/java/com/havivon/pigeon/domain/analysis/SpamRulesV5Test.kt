package com.havivon.pigeon.domain.analysis

import com.havivon.pigeon.domain.model.SpamReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules of version 5 ([SpamScorer.VERSION]): drugs for sale, a number other than the sender's own, sale wording. All made up. */
class SpamRulesV5Test {

    private fun verdict(body: String, address: String = "+972549178546") = SpamScorer.score(IncomingSms(address, null, body))

    private fun assertSpam(cases: List<Pair<String, String>>, reason: SpamReason? = null) {
        for ((body, address) in cases) {
            val v = verdict(body, address)
            assertTrue("$address: $body -> ${v.score} ${v.reasons}", v.isSpam)
            if (reason != null) assertTrue("$address: $body -> ${v.reasons}", reason in v.reasons)
        }
    }

    private fun assertNotSpam(cases: List<Pair<String, String>>) {
        for ((body, address) in cases) {
            val v = verdict(body, address)
            assertFalse("$address: $body -> ${v.score} ${v.reasons}", v.isSpam)
            assertFalse("$address: $body -> ${v.reasons}", SpamReason.Drugs in v.reasons)
        }
    }

    @Test
    fun theRulesAreVersionFive() {
        assertEquals(5, SpamScorer.VERSION)
    }

    @Test
    fun theUncaughtCannabisAdIsSpam() {
        val v = verdict("קנאביס רפואי 🧑‍⚕️ במחיר מוזל 5 ג' מתנה 🎁 בכל קניה Wa.me/972503958404")
        assertTrue("${v.score} ${v.reasons}", v.isSpam)
        assertTrue(SpamReason.Drugs in v.reasons)
        assertTrue(SpamReason.RedirectNumber in v.reasons)
        assertFalse("the redirect replaces the ten points of the plain chat sign", SpamReason.ContactElsewhere in v.reasons)
        assertTrue(SpamReason.Keyword("מחיר מוזל") in v.reasons)
        // unknown 15 + drugs 40 + redirect 20 + sale wording 10
        assertEquals(85, v.score)
    }

    @Test
    fun drugAdsAreSpam() {
        assertSpam(
            listOf(
                "גראס איכותי מבצע 10 גרם ב-400 ₪ משלוחים בכל הארץ" to "0541234567",
                "וויד חדש במלאי! להזמנות בטלגרם: t.me/greenguy_il" to "0521234567",
                "חשיש מרוקאי משלוח דיסקרטי, דיסקרטיות מלאה. הזמנות בוואטסאפ 050-3958404" to "0541234567",
                "קאנביס מובחר 🌿 מחיר מיוחד לחדשים wa.me/972501112233" to "+972541234567",
                "Premium cannabis, discreet delivery all over Israel. Order now: t.me/kushdealer" to "+972541234567",
                "Fresh weed in stock, special price this week! WhatsApp +972 50 395 8404" to "0541234567",
                "טלגראס וגראס במבצע, מתנה בכל קניה 🎁 https://bit.ly/3xAbC9" to "0521234567",
                "THC vapes and edibles. Prices in the link, delivery to your door: https://lp6.me/nejcz" to "+447700900123",
                "מריחואנה רפואית בלי מרשם! הזמינו עכשיו 054-9998877" to "0521234567",
                "ויד פרימיום 5 ג' ב-100 ₪, משלוח דיסקרטי עד הבית" to "0521234567",
                "🍁 חשיש + גראס 🍁 מחיר מוזל לכל מי שמזמין היום, שליח עד הדלת" to "0551234567",
            ),
            SpamReason.Drugs,
        )
    }

    @Test
    fun legitimateLookAlikesAreNotDrugSpam() {
        assertNotSpam(
            listOf(
                "ראית את הכתבה על קנאביס רפואי?" to "0541234567",
                "הוא אמר שהוא מעשן ויד כבר שנים, חבל" to "0541234567",
                "הכתבה: בית המשפט דן בהחלפת קנאביס רפואי בהסדר חדש https://www.ynet.co.il/news/article/abc" to "0541234567",
                "הרופא שלח מרשם לקנאביס רפואי, אפשר לאסוף בבית המרקחת" to "0541234567",
                "מכבי: מרשם לקנאביס רפואי נשלח לבית המרקחת. מחיר התרופה 120 ₪" to "Maccabi",
                "סופר-פארם: קנאביס רפואי במחיר מוזל לבעלי רישיון. להסרה השיבו הסר" to "SuperPharm",
                "מרפאת קנאביס רפואי: תורך נקבע ליום ראשון בשעה 10:00, מחיר הטיפול 150 ₪" to "0521234567",
                "Ynet: קנאביס רפואי - המחירים יורדים, כך תקבלו מרשם https://ynet.co.il/n/1" to "Ynet",
                "I read the weed killer needs two days to work, order some more?" to "0541234567",
                "קוד האימות שלך הוא 482913" to "Leumi",
                "בואו ליום הולדת של נועם, מתנה בכל קניה לא צריך 🙂" to "0541234567",
            ),
        )
    }

    // --- Another number

    @Test
    fun theSameNumberInTheLinkIsNoRedirect() {
        for (address in listOf("+972503958404", "0503958404", "050-395-8404", "00972503958404")) {
            val v = verdict("לחברי המועדון שלנו, לפרטים: wa.me/972503958404", address)
            assertFalse("$address -> ${v.reasons}", SpamReason.RedirectNumber in v.reasons)
            assertTrue("$address -> ${v.reasons}", SpamReason.ContactElsewhere in v.reasons)
        }
        val inText = verdict("לתיאום תור התקשרו 050-3958404", "+972503958404")
        assertFalse(SpamReason.RedirectNumber in inText.reasons)
    }

    @Test
    fun anotherNumberIsARedirect() {
        for (body in listOf(
            "לחברי המועדון שלנו, לפרטים: wa.me/972503958404",
            "לחברי המועדון שלנו, לפרטים: https://api.whatsapp.com/send?phone=972503958404&text=hi",
            "לחברי המועדון שלנו, לפרטים: t.me/club_il",
            "לחברי המועדון שלנו, התקשרו 050-3958404",
            "לחברי המועדון שלנו, דברו איתנו בוואטסאפ +972 50 395 8404",
        )) {
            val v = verdict(body, "+972549178546")
            assertTrue("$body -> ${v.reasons}", SpamReason.RedirectNumber in v.reasons)
            assertFalse(SpamReason.ContactElsewhere in v.reasons)
            assertEquals(15 + 20, v.score)
        }
    }

    @Test
    fun aSavedContactAndAKnownBrandAreNeverRedirected() {
        val friend = SpamScorer.score(IncomingSms("0549178546", "Dana", "שלחתי לך wa.me/972503958404", isContact = true))
        assertEquals(0, friend.score)
        val wrote = SpamScorer.score(IncomingSms("0549178546", null, "התקשר 050-3958404", userWrote = true))
        assertEquals(0, wrote.score)
        val brand = SpamScorer.score(IncomingSms("SuperPharm", null, "ההטבה שלך: wa.me/972503958404"))
        assertFalse(SpamReason.RedirectNumber in brand.reasons)
        assertFalse(brand.isSpam)
        val landline = verdict("חנות הספרים נפתחת מחר, לבירורים התקשרו 03-5551234", "0549178546")
        assertFalse("a shop's landline is no redirect", SpamReason.RedirectNumber in landline.reasons)
    }

    // --- Sale wording

    @Test
    fun saleWordingIsMildAndCountedOnce() {
        val v = verdict("שלום, מחיר מוזל ומתנה בכל קניה, מבצע מיוחד", "0541234567")
        assertEquals(1, v.reasons.count { it is SpamReason.Keyword && (it as SpamReason.Keyword).word == "מחיר מוזל" })
        assertFalse(v.isSpam)
        for (text in listOf("price drop", "special price", "free with every purchase", "במבצע", "הנחה מיוחדת")) {
            assertTrue(text, verdict("Hello $text today", "0541234567").reasons.any { it is SpamReason.Keyword })
        }
    }

    @Test
    fun aKnownBrandPromoWithSaleWordingAndAnOptOutStaysBelow() {
        val promo = SpamScorer.score(IncomingSms("Shufersal", null, "פרסומת: יין במחיר מוזל וגם מתנה בכל קניה. להסרה השיבו הסר"))
        assertFalse("${promo.score} ${promo.reasons}", promo.isSpam)
    }

    // --- Existing signs are untouched

    @Test
    fun aPlainAdFromAStrangerIsStillBelowAndTheCapsAndLinkRulesStand() {
        assertFalse(verdict("שלום, מה נשמע?", "0541234567").isSpam)
        assertTrue(verdict("זכית! קבלו מתנה: https://bit.ly/3xAbC9", "0541234567").isSpam)
    }
}
