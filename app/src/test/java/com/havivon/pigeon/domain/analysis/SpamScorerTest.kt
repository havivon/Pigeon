package com.havivon.pigeon.domain.analysis

import com.havivon.pigeon.domain.model.SpamReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpamScorerTest {

    private fun sms(
        body: String,
        address: String = "0541234567",
        contact: Boolean = false,
        allowed: List<String> = emptyList(),
        blocked: List<String> = emptyList(),
        name: String? = null,
    ) = IncomingSms(address, name, body, contact, allowed, blocked)

    private fun verdict(body: String, address: String = "0541234567") = SpamScorer.score(sms(body, address))

    private fun SpamVerdict.keywords() = reasons.filterIsInstance<SpamReason.Keyword>().map { it.word }

    // --- obvious spam ---

    @Test
    fun scamMessagesAreSpam() {
        val cases = listOf(
            "זכית בפרס! לחץ כאן לקבלת המתנה שלך: bit.ly/x7Kq9" to "+972551234567",
            "חבילה ממתינה לך. יש לשלם 3.90 ₪ דמי משלוח: isr-post.co/pay" to "Delivery",
            "הלוואה מיידית בלי בדיקות! להסרה השב הסר" to "0540001111",
            "Congratulations! You won a \$1000 gift card. Claim now: tinyurl.com/a1b2" to "+15551234567",
            "Your package is waiting. Pay the delivery fee: dhl-parcel.info/track" to "+4477009000",
            "Your account has been locked. Verify your account now: https://paypa1.com/login" to "PayPal",
            "החשבון שלך ייחסם! אמת את הפרטים: leumi-secure.com/login" to "0501112222",
            "Pre-approved loan, no credit check! Click here: cutt.ly/loanNow reply STOP to opt out" to "0521112222",
            "קזינו אונליין! בונוס 500 ₪ חינם: rb.gy/play" to "0521110000",
        )
        for ((body, address) in cases) {
            val v = verdict(body, address)
            assertTrue("$body -> ${v.score} ${v.reasons}", v.isSpam)
        }
    }

    @Test
    fun hebrewSpamReasonsAreListed() {
        val v = verdict("זכית בפרס! לחץ כאן לקבלת המתנה שלך: bit.ly/x7Kq9", "+972551234567")
        assertEquals(
            listOf(SpamReason.UnknownSender, SpamReason.ShortLink, SpamReason.Keyword("זכית"), SpamReason.Keyword("לחץ כאן")),
            v.reasons,
        )
    }

    @Test
    fun lookalikeParcelScamHasItsReasons() {
        val v = verdict("חבילה ממתינה לך. יש לשלם 3.90 ₪ דמי משלוח: isr-post.co/pay", "Delivery")
        assertTrue(v.reasons.contains(SpamReason.LookalikeDomain))
        assertTrue(v.reasons.contains(SpamReason.Keyword("חבילה ממתינה")))
        assertTrue(v.reasons.contains(SpamReason.Keyword("דמי משלוח")))
        assertFalse(v.reasons.contains(SpamReason.ShortLink))
    }

    // --- not spam ---

    @Test
    fun ordinaryMessagesFromStrangersAreNotSpam() {
        val cases = listOf(
            "היי, זה דני מהעבודה. אפשר להתקשר?" to "0541234567",
            "Hi, this is the plumber. I'll be there at 5." to "0521234567",
            "קוד האימות שלך הוא 482913" to "37133",
            "Your Uber is arriving in 2 minutes" to "Uber",
            "התור שלך ביום שלישי ב-10:00. להסרה השב הסר" to "Clinic",
            "מבצע סוף עונה! 20% הנחה בכל החנות. להסרה השב הסר" to "SuperPharm",
            "ההזמנה שלך נשלחה. מעקב: https://israelpost.co.il/track?id=RR123" to "IsraelPost",
            "חיוב של 150 ש\"ח בכרטיס המסתיים ב-4821 בוצע ב-Aroma" to "Leumi",
            "Your bank balance is 1500. Details: https://www.leumi.co.il/login" to "Leumi",
            "ראה את התמונות כאן: https://photos.example.com/album/1" to "0541234567",
            "I won the game yesterday haha" to "0541234567",
        )
        for ((body, address) in cases) {
            val v = verdict(body, address)
            assertFalse("$body -> ${v.score} ${v.reasons}", v.isSpam)
        }
    }

    @Test
    fun theOptOutWordAloneIsAModerateSignNotAVerdict() {
        for (body in listOf("הסר", "להסרה השב הסר", "Reply STOP to opt out", "לחץ כאן להסרה", "unsubscribe here")) {
            val v = verdict(body)
            assertFalse("$body -> ${v.score}", v.isSpam)
            assertTrue(body, v.score > verdict("שלום").score)
        }
        // With a bait phrase and a shortened link it does tip the balance.
        assertTrue(verdict("זכית! להסרה השב הסר bit.ly/a").isSpam)
    }

    @Test
    fun aShortenedLinkAloneIsNotEnoughButWithBaitIsSpam() {
        assertFalse(verdict("תראה את זה bit.ly/abc").isSpam)
        assertTrue(verdict("תראה את זה bit.ly/abc לחץ כאן").isSpam)
        assertTrue(verdict("click here bit.ly/abc").isSpam)
    }

    @Test
    fun lookalikeDomainAloneFromAStrangerIsSpam() {
        assertTrue(verdict("עדכון: https://leumi-secure.com", "0541234567").isSpam)
        assertTrue(verdict("see https://xn--pypal-4ve.com/x", "0541234567").isSpam)
        assertFalse(verdict("see https://leumi.co.il/x", "0541234567").isSpam)
    }

    // --- exemptions and blocklist ---

    @Test
    fun savedContactsAreNeverSpam() {
        val v = SpamScorer.score(sms("זכית! לחץ כאן bit.ly/x הלוואה", contact = true))
        assertFalse(v.isSpam)
        assertEquals(0, v.score)
        assertEquals(emptyList<SpamReason>(), v.reasons)
    }

    @Test
    fun allowedSendersAreNeverSpam() {
        val body = "זכית! לחץ כאן bit.ly/x הלוואה"
        assertFalse(SpamScorer.score(sms(body, address = "0541234567", allowed = listOf("0541234567"))).isSpam)
        assertFalse(SpamScorer.score(sms(body, address = "+972541234567", allowed = listOf("054-123-4567"))).isSpam)
        assertFalse(SpamScorer.score(sms(body, address = "Delivery", allowed = listOf("delivery"))).isSpam)
        assertTrue(SpamScorer.score(sms(body, address = "0541234568", allowed = listOf("0541234567"))).isSpam)
    }

    @Test
    fun blockedKeywordsAlwaysMakeSpam() {
        val v = SpamScorer.score(sms("שלום, יש לנו מבצע על ביטקוין", blocked = listOf("ביטקוין")))
        assertTrue(v.isSpam)
        assertEquals(SpamScorer.BLOCKED_SCORE, v.score)
        assertEquals(SpamReason.BlockedKeyword("ביטקוין"), v.reasons.first())

        assertTrue(SpamScorer.score(sms("Buy BITCOIN now", blocked = listOf("bitcoin"))).isSpam)
        assertTrue(SpamScorer.score(sms("הביטקוין עלה", blocked = listOf("ביטקוין"))).isSpam)
        assertTrue(SpamScorer.score(sms("חבילה ממתינה לך", blocked = listOf("חבילה ממתינה"))).isSpam)
        assertFalse(SpamScorer.score(sms("ביטקוינים", blocked = listOf("ביטקוין"))).isSpam)
        assertFalse(SpamScorer.score(sms("שלום", blocked = listOf("", "  "))).isSpam)
    }

    @Test
    fun contactsAndAllowedSendersBeatTheBlocklist() {
        assertFalse(SpamScorer.score(sms("ביטקוין", contact = true, blocked = listOf("ביטקוין"))).isSpam)
        assertFalse(SpamScorer.score(sms("ביטקוין", allowed = listOf("0541234567"), blocked = listOf("ביטקוין"))).isSpam)
    }

    // --- individual signals ---

    @Test
    fun unknownSenderIsTheBaseline() {
        val v = verdict("שלום")
        assertEquals(listOf<SpamReason>(SpamReason.UnknownSender), v.reasons)
        assertTrue(v.score < SpamScorer.SPAM_THRESHOLD)
    }

    @Test
    fun spamPhrasesInHebrewAndEnglish() {
        val cases = listOf(
            "זכית" to "זכית", "זכיתם בפרס" to "זכית", "וזכית" to "זכית",
            "you won" to "won", "You've won!" to "won", "claim your prize" to "claim",
            "חבילה ממתינה" to "חבילה ממתינה", "החבילה שלך ממתינה" to "חבילה ממתינה", "Your package is waiting" to "package waiting",
            "הלוואה" to "הלוואה", "בלי בדיקות" to "הלוואה", "Get a loan today" to "loan", "payday loans" to "loan",
            "לחץ כאן" to "לחץ כאן", "click here" to "click here", "Click the link" to "click here",
            "הסר" to "הסר", "להסרה" to "הסר", "reply STOP" to "STOP", "Reply Stop to opt out" to "STOP",
            "דמי משלוח" to "דמי משלוח", "delivery failed" to "delivery fee",
        )
        for ((body, label) in cases) {
            assertTrue("$body -> ${verdict(body).reasons}", label in verdict(body).keywords())
        }
    }

    @Test
    fun phrasesAreWholeWordsNotSubstrings() {
        for (body in listOf("loanword", "wonderful", "unwon", "clicking here", "הלוואתי", "זכיתי בקפה", "הסרטן", "my loans")) {
            val expected = if (body == "my loans") listOf("loan") else emptyList()
            assertEquals("$body -> ${verdict(body).reasons}", expected, verdict(body).keywords())
        }
    }

    @Test
    fun manyLinksAreASignal() {
        val three = verdict("a.com/x b.com/y c.com/z")
        assertTrue(three.reasons.contains(SpamReason.ManyLinks))
        assertFalse(verdict("a.com/x b.com/y").reasons.contains(SpamReason.ManyLinks))
    }

    @Test
    fun alphanumericSenderPretendingToBeABrandWithAForeignLink() {
        val v = SpamScorer.score(sms("החבילה שלך בדרך, מעקב כאן: https://track-it.example.com/1", address = "IsraelPost"))
        assertTrue(v.reasons.contains(SpamReason.BrandSender))

        val ok = SpamScorer.score(sms("מעקב: https://israelpost.co.il/track", address = "IsraelPost"))
        assertFalse(ok.reasons.contains(SpamReason.BrandSender))

        val noLink = SpamScorer.score(sms("החבילה שלך בדרך", address = "IsraelPost"))
        assertFalse(noLink.reasons.contains(SpamReason.BrandSender))

        val phone = SpamScorer.score(sms("מעקב: https://track-it.example.com/1", address = "0541234567"))
        assertFalse(phone.reasons.contains(SpamReason.BrandSender))

        val leumi = SpamScorer.score(sms("היכנס: https://my-account.example.com", address = "Leumi"))
        assertTrue(leumi.reasons.contains(SpamReason.BrandSender))
        assertTrue(leumi.isSpam)
    }

    @Test
    fun phraseBonusIsCapped() {
        val all = "זכית בפרס הלוואה קזינו לחץ כאן הסר מבצע חינם דחוף החבילה שלך ממתינה דמי משלוח"
        val v = verdict(all)
        assertTrue(v.isSpam)
        assertTrue(v.score <= 15 + 60)
    }

    @Test
    fun emojiAndDirectionMarksDoNotHideWords() {
        assertTrue("זכית" in verdict("🎉🎉 זכית!!! 🎉").keywords())
        assertTrue("זכית" in verdict("‏זכית‏ בפרס").keywords())
        assertTrue("זכית" in verdict("זָכִיתָ").keywords()) // niqqud are dropped before matching
    }

    @Test
    fun thresholdDocumentedValue() {
        assertEquals(50, SpamScorer.SPAM_THRESHOLD)
        assertTrue(SpamScorer.BLOCKED_SCORE >= SpamScorer.SPAM_THRESHOLD)
    }

    @Test
    fun englishPhishingWithNoLinkAndAStrangerIsNotYetSpamWithoutBait() {
        assertFalse(verdict("Hi it is Tom, can you call me back").isSpam)
        assertFalse(verdict("Your appointment is confirmed for 10:00").isSpam)
        assertTrue(verdict("URGENT: unusual activity on your account, verify your account").isSpam)
    }

    @Test
    fun bankMessagesFromTheRealSenderAreFine() {
        val v = SpamScorer.score(sms("בנק לאומי: קוד האימות שלך 482913", address = "Leumi"))
        assertFalse(v.isSpam)
        assertEquals(listOf<SpamReason>(SpamReason.UnknownSender), v.reasons)
    }
}
