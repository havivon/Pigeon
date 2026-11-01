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
        // The unknown sender, the capped phrases, and the opt-out line with an ad's words from a stranger
        assertTrue(v.score <= 15 + 60 + 20)
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

    // --- polls and political campaigns (all made up) ---

    @Test
    fun pollsAndPoliticalCampaignsAreSpam() {
        val cases = listOf(
            "סקר קצר: לאיזו מפלגה תצביע בבחירות הקרובות? השיבו 1 לליכוד, 2 ליש עתיד. להסרה השב הסר" to "0533001122",
            "השתתפו בסקר בחירות ארצי וזכו בשובר! bit.ly/seker26" to "+972533001123",
            "מי ראוי להיות ראש הממשלה? הקישו 1 עבור המועמד הראשון, 2 עבור השני" to "0721234567",
            "סקר: אם הבחירות היו היום, למי היית מצביע? ענו בקישור poll-il.com/q1 להסרה הסר" to "Seker",
            "שלום, אנו עורכים סקר בנושא הבחירות. השב 1 אם תצביע, 2 אם לא" to "0559876543",
            "הצביעו עכשיו למועמד שלנו לראשות הממשלה! www.vote-now.co.il להסרה השב הסר" to "Campaign",
            "Quick poll: who will you vote for in the election? Reply 1 for A, 2 for B" to "+15550001234",
            "Election survey: tell us your opinion at survey-now.info/e Reply STOP to opt out" to "+447700900111",
            "Vote for our candidate on election day! Reply STOP to unsubscribe" to "Campaign2026",
            "סקר טלפוני קצר על מצב המדינה. הקישו 1 להשתתפות. להסרה הקישו 9" to "0732223344",
            "בחירות 2026: בדקו מי המועמד המוביל בסקרים rb.gy/elect" to "0541112233",
            "לאיזו מפלגה תצביעו? השתתפו בסקר והשפיעו!" to "Polls",
            "הליכוד או יש עתיד? הצבעה בסקר שלנו: https://seker-online.com/v" to "0524445566",
            "תמכו במפלגה שלנו בבחירות! לפרטים: www.party-site.co.il להסרה השב הסר" to "0501239876",
            "Take our short survey about the prime minister and win a prize! tinyurl.com/pm26" to "+15550009999",
            "סקר דעת קהל: האם הקואליציה תשרוד? השיבו 1 כן, 2 לא. להסרה השיבו הסר" to "PollsIL",
            "‏סקר‏ בחירות: לחצו על הקישור והצביעו https://elections-poll.site/a" to "0587776655",
        )
        for ((body, address) in cases) {
            val v = verdict(body, address)
            assertTrue("$body -> ${v.score} ${v.reasons}", v.isSpam)
            assertTrue("$body -> ${v.reasons}", SpamReason.Poll in v.reasons)
        }
    }

    @Test
    fun aStrangersAdWithTheOptOutLineIsSpam() {
        val cases = listOf(
            "החנות החדשה שלנו נפתחה! כנסו: shop-new.co.il להסרה השב הסר" to "0547778899",
            "מבצע ענק על מזגנים! להסרה השב הסר" to "0547778800",
            "Big sale this weekend only at our store. Reply STOP to opt out" to "+15550004444",
            "Visit our new site www.best-deals.example.com unsubscribe here" to "+15550004445",
        )
        for ((body, address) in cases) {
            val v = verdict(body, address)
            assertTrue("$body -> ${v.score} ${v.reasons}", v.isSpam)
        }
    }

    @Test
    fun lookAlikesOfPollsAndAdsStayInTheInbox() {
        val cases = listOf(
            "תזכורת: התור שלך לד\"ר כהן מחר ב-09:30. לאישור השב 1, לביטול השב 2" to "Clinic",
            "שלום, נשמח לשמוע את דעתך על הביקור במרפאה. סקר קצר: השב 1 עד 5" to "Maccabi",
            "החבילה שלך יצאה למשלוח, מעקב: https://israelpost.co.il/t/RR123456789IL" to "IsraelPost",
            "חיוב בסך 120 ₪ בכרטיס המסתיים ב-4821. לפרטים: https://www.leumi.co.il" to "Leumi",
            "מחר בחירות, נלך יחד לקלפי? 🙂" to "0541239999",
            "עיריית תל אביב: ביום הבחירות יופעלו הסעות לקלפי. פרטים באתר העירייה" to "TelAviv",
            "ועד הבית: הצבעה על צביעת חדר המדרגות. השב 1 בעד, 2 נגד" to "0526667788",
            "קוד האימות שלך הוא 482913. אין למסור את הקוד לאיש" to "Verify",
            "תודה שקניתם בסופר-פארם! הקבלה נשלחה למייל. להסרה השב הסר" to "SuperPharm",
            "תזכורת לתור במרפאת השיניים ביום ה׳ ב-16:00. לביטול: https://clinic-dent.co.il/c/77 להסרה השב הסר" to "0779990000",
            "השליח בדרך אליך עם המשלוח שלך. מעקב: https://track.hfd.co.il/123 להסרה הסר" to "0501231231",
            "בוצעה עסקה בסך 250 ₪ בכרטיס המסתיים ב-1234. לפרטים https://www.bankhapoalim.co.il להסרה הסר" to "0509990001",
            "Your appointment with Dr. Smith is on Monday at 10:00. Reply 1 to confirm, 2 to cancel. Reply STOP to opt out" to "+15551230000",
            "Survey results from the school committee: school.example.com/r" to "+15553334444",
            "Did you vote yet? The polls close at 8" to "0547778888",
            "שלום, אנחנו מחפשים מועמדים לתפקיד מנהל משמרת. לפרטים התקשרו" to "0523334455",
            "פוליסת ביטוח הרכב שלך מס' 5512345 חודשה. להסרה השב הסר" to "0525556677",
            "מבצע סוף עונה! 20% הנחה. לקטלוג: https://www.super-pharm.co.il להסרה השב הסר" to "SuperPharm",
            "ההצבעה באסיפה הכללית תתקיים ביום שלישי" to "0541110000",
        )
        for ((body, address) in cases) {
            val v = verdict(body, address)
            assertFalse("$body -> ${v.score} ${v.reasons}", v.isSpam)
        }
    }

    @Test
    fun friendsAndPeopleWrittenToTalkingAboutElectionsAreNeverSpam() {
        val body = "סקר: לאיזו מפלגה תצביע בבחירות? השב 1 לליכוד. להסרה השב הסר bit.ly/x"
        assertEquals(0, SpamScorer.score(sms(body, contact = true)).score)
        assertEquals(0, SpamScorer.score(IncomingSms("0541234567", null, body, userWrote = true)).score)
        assertFalse(SpamScorer.score(sms(body, allowed = listOf("+972541234567"))).isSpam)
    }

    @Test
    fun aReplyDigitAloneIsNoPoll() {
        assertFalse(SpamReason.Poll in verdict("לאישור התור השב 1").reasons)
        assertFalse(SpamScorer.isPoll("Reply 1 to confirm"))
        assertTrue(SpamScorer.isPoll("Reply 1 in our survey"))
        assertTrue(SpamScorer.isPoll("לאיזו מפלגה תצביע?"))
        assertFalse(SpamScorer.isPoll("תפילה בבית הכנסת בשבע"))
        assertFalse(SpamScorer.isPoll("סקרן לדעת מה קורה"))
    }

    @Test
    fun theOptOutLineMakesAnAdOnlyFromAStrangersNumberOrWithAPoll() {
        val ad = "מבצע! 20% הנחה. להסרה השב הסר"
        assertTrue(verdict(ad, "0541234567").isSpam)
        assertFalse(verdict(ad, "SuperPharm").isSpam)
        assertFalse(verdict(ad, "37133").isSpam)
        assertTrue(verdict("סקר קצר בנושא המדינה. להסרה השב הסר", "Seker").isSpam)
        assertFalse(verdict("סקר קצר בנושא המדינה", "Seker").isSpam)
    }

    @Test
    fun theRulesHaveAVersion() {
        assertTrue(SpamScorer.VERSION >= 3)
    }

    // --- money bait and home-made short links (version 3, all made up) ---

    @Test
    fun theInsuranceRefundAdFromAnUnknownSenderIdIsSpam() {
        val v = verdict(BITUA_CHECK, "BituaCheck")
        assertTrue("${v.score} ${v.reasons}", v.isSpam)
        assertTrue(SpamReason.MoneyBait in v.reasons)
        assertTrue(SpamReason.ShortLink in v.reasons)
    }

    @Test
    fun moneyBaitAdsAreSpam() {
        val cases = listOf(
            "בדיקת קרן פנסיה חינם! ייתכן שמגיע לך החזר של אלפי שקלים. לבדיקה: https://pn4.me/aB3dE להסרה השב הסר" to "PensiaCheck",
            "החזר מס לשכירים! בדקו בחינם אם מגיע לכם כסף: https://tx9.ly/Qw7rT להסרה https://rm2.me/x8K2p" to "TaxBack",
            "You may be owed up to \$2,000 from your insurer. Free check: https://cl3.cc/zX9pQ Reply STOP to opt out" to "+15550007777",
            "הלוואה עד 50,000 ₪ ללא בדיקות! לבדיקה חינם: bit.ly/loan50 להסרה הסר" to "0533334444",
            "ביטוח רכב? ייתכן שמגיע לכם החזר כספי! לבדיקה: https://kx2.link/abc12 להסרה" to "Bituach4U",
            "שילמתם יותר מדי על ביטוח החיים? בדיקה חינם תוך דקה: https://www.bdika-online.co.il/l?id=88 להסרה השב הסר" to "BdikaNow",
            "Claim your refund now! Free check of your policy: https://rf4.click/9xKq unsubscribe" to "RefundsUS",
            "בדיקת קרן פנסיה: גלו כמה דמי ניהול אתם משלמים מיותר. ממוצע חיסכון של ₪12,000! https://pns.to/k3Jd9 להסרה" to "Pensia-IL",
            "החזר מס עד 8,000 ₪ לשכירים! השאירו פרטים: https://mas-back.co.il/r להסרה השב הסר" to "0541239876",
            "ייתכן שמגיע לך החזר עמלות מהבנק! לבדיקה חינם: https://bk2.me/Zz8Yq להסרה" to "BankCheck",
            "מגיע לכם כסף מביטוח לאומי? בדיקת זכאות בחינם: https://bt7.me/Ab9Cd להסרה" to "Zchuyot",
            "Pre-approved loan up to \$10,000, free check: https://ln5.ly/Xy7z reply STOP" to "+15550001212",
            "הנחה מגיעה לכם בביטוח הדירה! לבדיקה: lp3.me/kw8Tq להסרה שלחו הסר" to "DiraSave",
        )
        for ((body, address) in cases) {
            val v = verdict(body, address)
            assertTrue("$address: $body -> ${v.score} ${v.reasons}", v.isSpam)
        }
    }

    @Test
    fun legitimateNoticesAndKnownBrandPromosStayBelow() {
        val cases = listOf(
            "פוליסת ביטוח הרכב שלך מס' 5512345 חודשה בהצלחה. לפרטים: https://www.harel-group.co.il" to "Harel",
            "תביעה מספר 445566 אושרה. הכסף יועבר לחשבונך תוך 3 ימי עסקים" to "Migdal",
            "ביטוח הדירה שלך יחודש ב-01/12. לפרטים https://www.clalbit.co.il" to "Clal",
            "חיוב בסך 120 ₪ בכרטיס המסתיים ב-4821" to "Leumi",
            "הופקדו 1,000 ₪ לחשבונך" to "Hapoalim",
            "החבילה שלך יצאה למשלוח, מעקב: https://israelpost.co.il/t/RR123456789IL" to "IsraelPost",
            "מבצע סוף עונה! 20% הנחה בכל החנות: https://www.super-pharm.co.il להסרה השב הסר" to "SuperPharm",
            "מבצעי השבוע באתר https://www.shufersal.co.il להסרה השב הסר" to "Shufersal",
            "הראל: הצטרפו לביטוח נסיעות ב-20% הנחה! https://www.harel-group.co.il להסרה השב הסר" to "Harel",
            "קוד האימות שלך הוא 482913" to "Harel",
            "תזכורת: התור שלך מחר ב-10:00. לביטול השב 2" to "Maccabi",
            "עיריית חיפה: הפסקת מים מחר 08:00-12:00 ברחוב הרצל. להסרה השב הסר" to "Haifa",
            "החזר מס בסך 1,250 ₪ הועבר לחשבונך" to "Leumi",
            "שלחתי לך את הקישור לקבוצה: https://wa.me/972541234567" to "0541234567",
            "Your refund of \$40 was issued to your card ending 4821" to "Amazon",
        )
        for ((body, address) in cases) {
            val v = verdict(body, address)
            assertFalse("$address: $body -> ${v.score} ${v.reasons}", v.isSpam)
        }
    }

    @Test
    fun contactsPeopleWrittenToAndAllowedSendersStayAtZeroWhateverTheBait() {
        assertEquals(0, SpamScorer.score(sms(BITUA_CHECK, address = "BituaCheck", contact = true)).score)
        assertEquals(0, SpamScorer.score(IncomingSms("BituaCheck", null, BITUA_CHECK, userWrote = true)).score)
        assertEquals(0, SpamScorer.score(sms(BITUA_CHECK, address = "BituaCheck", allowed = listOf("bituacheck"))).score)
    }

    @Test
    fun anUnknownSenderIdNeedsALinkAndAdWordingForTheOptOutBonus() {
        // An unknown id with an opt-out line, but no link: its own ad, below the threshold
        assertFalse(verdict("מבצע! ביטוח נסיעות לחו\"ל ב-20% הנחה. להסרה השב הסר", "TravelCo").isSpam)
        // The same with a link: an unknown advertiser, now spam
        assertTrue(verdict("מבצע! ביטוח נסיעות לחו\"ל ב-20% הנחה: https://travel-co.example.com להסרה השב הסר", "TravelCo").isSpam)
        // A known brand with the same: its ordinary advertising
        assertFalse(verdict("מבצע! 20% הנחה: https://www.fox.co.il להסרה השב הסר", "Fox").isSpam)
    }
}

private const val BITUA_CHECK = "יש לך ביטוח בריאות/חיים? ייתכן שמגיע לכם כסף! בעקבות המצב כעת ניתן לבדוק בחינם כמה הנחה " +
    "מגיעה לכם - ממוצע חיסכון של ₪2,300 לכל אזרח! לבדיקה חינם: https://lp6.me/nejcz  להסרה https://l5k.me/iihzE_BP5Az"
