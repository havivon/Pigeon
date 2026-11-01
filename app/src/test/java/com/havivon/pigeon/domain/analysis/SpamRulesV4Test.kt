package com.havivon.pigeon.domain.analysis

import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.SpamReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules of version 4 ([SpamScorer.VERSION]). Every message here is made up. */
class SpamRulesV4Test {

    private fun verdict(body: String, address: String = "0541234567") = SpamScorer.score(IncomingSms(address, null, body))

    private fun assertSpam(cases: List<Pair<String, String>>, reason: SpamReason? = null) {
        for ((body, address) in cases) {
            val v = verdict(body, address)
            assertTrue("$address: $body -> ${v.score} ${v.reasons}", v.isSpam)
            if (reason != null) assertTrue("$address: $body -> ${v.reasons}", reason in v.reasons)
        }
    }

    private fun assertNotSpam(cases: List<Pair<String, String>>, reason: SpamReason? = null) {
        for ((body, address) in cases) {
            val v = verdict(body, address)
            assertFalse("$address: $body -> ${v.score} ${v.reasons}", v.isSpam)
            if (reason != null) assertFalse("$address: $body -> ${v.reasons}", reason in v.reasons)
        }
    }

    @Test
    fun theRulesAreVersionFour() {
        assertEquals(4, SpamScorer.VERSION)
    }

    // --- 1. common Israeli scams: an authority asking to click or pay ---

    @Test
    fun anAuthorityAskingToClickOrPayIsSpam() {
        assertSpam(
            listOf(
                "פיקוד העורף: הורידו את האפליקציה החדשה להתרעות בזמן אמת: https://oref-alerts.app/download" to "PikudHaoref",
                "כביש 6: נמצא חוב אגרה בסך 38.90 ₪. להסדרת החוב: https://kvish6-pay.com/p" to "Kvish6",
                "רשות המסים: מגיע לך החזר של 1,240 ₪. לקבלת ההחזר עדכנו פרטים: https://taxes-refund.co/r" to "0533334455",
                "ביטוח לאומי: יש לשלם את החוב עד מחר, אחרת ייצא לאכיפה. לתשלום: btl-pay.net/x" to "+972501112233",
                "Kvish 6 toll: unpaid toll of 52 NIS. Pay now: https://kvish6.toll-pay.info" to "+447700900555",
                "נתיב מהיר: לא שולם תשלום הנסיעה. יש לשלם תוך 24 שעות: https://fastlane-il.top/pay" to "FastLane",
                "דוח חניה לא שולם בסך 250 ₪. לתשלום מיידי: https://parking-fines.site/d" to "0559998877",
                "הוצאה לפועל: נפתח נגדך תיק. להסדרת החוב: https://eca-gov.co/case" to "Hotzlap",
                "משטרת ישראל: יש לך קנס שלא שולם. יש לשלם היום בהעברה לחשבון 12-345-678" to "0549990000",
                "Israel Tax Authority: you are eligible for a refund of 1,240 NIS. Verify your details: https://tax-il-refund.com" to "TaxIL",
            ),
            SpamReason.GovImpersonation,
        )
    }

    @Test
    fun genuineAuthoritiesAndTheirLookAlikesStayBelow() {
        assertNotSpam(
            listOf(
                // A real Kvish 6 invoice from its own id, with its own domain
                "כביש 6: חשבונית לתשלום בסך 23.50 ₪ לחודש אוקטובר. לצפייה ותשלום: https://www.kvish6.co.il/invoice" to "Kvish6",
                // A real Home Front Command alert, without a link
                "פיקוד העורף: התרעה באזור עוטף עזה. היכנסו למרחב המוגן" to "PikudHaoref",
                "פיקוד העורף: הנחיות מעודכנות באתר https://www.oref.org.il" to "Oref",
                "ביטוח לאומי: קצבת הילדים הופקדה לחשבונך. לפרטים: https://www.btl.gov.il" to "BTL",
                "עיריית תל אביב: נרשם דוח חניה לרכבך. לתשלום: https://www.tel-aviv.muni.il/pay" to "TelAviv",
                "עיריית חיפה: דוח חניה מס' 12345 ממתין לתשלום. לתשלום: https://pay.city-pay.co.il/haifa" to "Haifa",
                "ראית שרשות המסים הודיעה על מועד הגשת הדוחות?" to "0541234567",
                "Leumi: החזר מס הכנסה בסך 900 ₪ הופקד לחשבונך. לפרטים: https://www.leumi.co.il" to "Leumi",
                "ynet: פיקוד העורף עדכן את ההנחיות באזור הצפון. לכתבה: https://www.ynet.co.il/news/1" to "ynet",
                "gov.il: קוד האימות שלך הוא 482913" to "Gov.il",
                "דוח הנסיעות החודשי שלך מוכן: https://reports.example.com/r" to "TripLog",
            ),
            SpamReason.GovImpersonation,
        )
    }

    // --- 1. common Israeli scams: investment and crypto ---

    @Test
    fun investmentAndCryptoSchemesAreSpam() {
        assertSpam(
            listOf(
                "הצטרפו לקבוצת ההשקעות שלנו בביטקוין ותרוויחו 3,000 ₪ ביום! https://wa.me/447700900123" to "+447700900123",
                "רובוט מסחר מבוסס בינה מלאכותית למסחר: רווח יומי של 5%! להצטרפות: https://t.me/ai_profit_il" to "0533221100",
                "תשואה מובטחת של 20% בחודש לזמן מוגבל! פרטים בוואטסאפ 054-1112233" to "0541112233",
                "Guaranteed returns of 30% a month with our AI trading bot. Join our group: https://t.me/cryptowin" to "+12025550123",
                "Crypto daily profit \$500! Join the group now: https://chat.whatsapp.com/AbC123" to "+447700900999",
                "קריפטו: הכנסה פסיבית של אלפי שקלים בחודש. הצטרף לקבוצה: https://bit.ly/crp99" to "CryptoIL",
                "הזדמנות השקעה חד פעמית בפורקס! רווח מובטח. היום בלבד: https://fx-il.top/j" to "0527771122",
            ),
            SpamReason.Investment,
        )
    }

    @Test
    fun talkAboutMoneyIsNoInvestmentScheme() {
        assertEquals(0, SpamScorer.score(IncomingSms("0541234567", "Yossi", "קניתי קצת ביטקוין אתמול 😅", isContact = true)).score)
        assertEquals(0, SpamScorer.score(IncomingSms("+15551234567", null, "Bitcoin is up again, should we invest?", userWrote = true)).score)
        assertNotSpam(
            listOf(
                "ביטקוין עלה היום ב-5%, ראית?" to "0541234567",
                "Your investment account statement is ready at https://www.leumi.co.il" to "Leumi",
                "תודה על ההשקעה בפרויקט, היה מעולה" to "0521234567",
                "ועד הבית: הצטרפו לקבוצת הווטסאפ של הבניין https://chat.whatsapp.com/Xyz12" to "0524445566",
                "Leumi: דוח ההשקעות הרבעוני שלך זמין באתר https://www.leumi.co.il" to "Leumi",
                "הצטרפו לקבוצת הריצה של השכונה, נפגשים בשבת ב-7:00" to "0503334455",
            ),
        )
        assertFalse(SpamReason.Investment in verdict("ועד הבית: הצטרפו לקבוצת הווטסאפ של הבניין https://chat.whatsapp.com/Xyz12").reasons)
        assertFalse(SpamReason.Investment in verdict("תודה על ההשקעה בפרויקט").reasons)
    }

    // --- 1. common Israeli scams: a request for card details or a code ---

    @Test
    fun aRequestForCardDetailsOrACodeIsSpam() {
        assertSpam(
            listOf(
                "חשבונך הוקפא. הזינו את פרטי הכרטיס כדי לשחרר: https://secure-pay-il.com/c" to "0501234000",
                "שלום, שלחתי לך קוד בטעות, שלחו לי את הקוד שקיבלתם" to "+447700900321",
                "אמתו את פרטי האשראי שלכם בקישור: http://card-verify.top/a" to "IsraCard",
                "Your payment failed. Enter your card details here: https://netfl1x-billing.com" to "Netflix",
                "Hi, I sent you a code by mistake. Please send me the code" to "+15550001234",
                "לקבלת ההחזר מסור את הקוד שנשלח אליך לנציג" to "0521230000",
                "Confirm your card to avoid suspension: https://pay-secure.site/x" to "Billing",
                "מסרו את פרטי החשבון לנציג שלנו לקבלת הזיכוי" to "0531112222",
            ),
            SpamReason.AsksForDetails,
        )
    }

    @Test
    fun warningsNotToShareTheCodeAndOwnRequestsAreNotAsks() {
        assertNotSpam(
            listOf(
                "קוד האימות שלך הוא 482913. אין למסור את הקוד לאף אחד" to "Leumi",
                "קוד האימות שלך הוא 482913. אין למסור את הקוד לאיש" to "0541230000",
                "Your code is 482913. Never send this code to anyone." to "Google",
                "Your verification code is 123456. Do not share the code with anyone" to "Verify",
                "קוד לאיסוף החבילה: 4821. מסרו את הקוד לשליח" to "0501231231",
                "סלקום: כרטיס האשראי שלך פג תוקף. יש לעדכן את פרטי הכרטיס באזור האישי" to "Cellcom",
                "We will send the code to your phone shortly" to "Verify",
                "לעולם אל תמסרו את הקוד שקיבלתם, גם לא לנציג שלנו" to "Pepper",
                "Enter the code 482913 to verify your phone number" to "Verify",
                "הזינו את הקוד 482913 באתר כדי להשלים את ההרשמה" to "Info",
                "קוד האימות: 482913. הקלד את הקוד באפליקציה" to "0541230001",
            ),
            SpamReason.AsksForDetails,
        )
    }

    // --- 2. a number from abroad ---

    @Test
    fun aForeignNumberTipsAMildMessageOver() {
        val cases = listOf(
            "Congratulations, claim your reward" to "+2348012345678",
            "Hi, check this out: bit.ly/abc" to "+15551234567",
            "הלוואה עד 50 אלף, בלי ערבים" to "+79161234567",
            "מבצע ענק! 70% על כל המוצרים, היום בלבד" to "+8613800138000",
            "Hello dear, I have a business offer worth \$2,500,000 for you, reply urgent" to "00447911123456",
            "עבודה מהבית, 3,000 ₪ בשבוע. כתבו לי: wa.me/62812345678" to "+62812345678",
        )
        assertSpam(cases, SpamReason.ForeignNumber)
        // The same messages from an Israeli number stay below: the country is what tipped them
        assertNotSpam(cases.map { (body, _) -> body to "0541234567" }, SpamReason.ForeignNumber)
    }

    @Test
    fun israeliNumbersShortCodesAndPeopleKnownAreNotForeign() {
        assertEquals(0, SpamScorer.score(IncomingSms("+15551234567", "Sarah", "check this out bit.ly/abc", isContact = true)).score)
        assertEquals(0, SpamScorer.score(IncomingSms("+447700900123", null, "claim your seat at the dinner", userWrote = true)).score)
        assertEquals(0, SpamScorer.score(IncomingSms("+15551234567", null, "claim your prize", allowedSenders = listOf("+1 555 123 4567"))).score)
        assertNotSpam(
            listOf(
                "קוד האימות שלך הוא 482913" to "37133",
                "Your order has shipped" to "Amazon",
                "שלום, מה שלומך?" to "+972541234567",
                "שלום, מה שלומך?" to "00972541234567",
                "שלום, מה שלומך?" to "972541234567",
                "Hi, it's Sarah from the conference, nice to meet you" to "+15551234567",
            ),
        )
        for (address in listOf("37133", "Amazon", "+972541234567", "00972541234567", "972541234567", "0541234567")) {
            assertFalse(address, SpamReason.ForeignNumber in verdict("שלום", address).reasons)
        }
        assertTrue(SpamReason.ForeignNumber in verdict("Hi, it's Sarah", "+15551234567").reasons)
    }

    // --- 3. the shape of the message, from a stranger ---

    @Test
    fun aSumAPhoneNumberAndCapitalsTipAStrangersAdOver() {
        assertSpam(
            listOf(
                "מבצע היום בלבד: מזגן ב-999 ₪ בלבד! להזמנות חייגו 03-5551234" to "0523334444",
                "FREE IPHONE FOR THE FIRST 100 CUSTOMERS TODAY ONLY PAY \$1.99 SHIPPING TEXT US AT 054-9998887" to "0549998887",
                "הנחה מטורפת של 50% לזמן מוגבל! רק 199 ש\"ח. הזמינו בוואטסאפ: https://wa.me/972541112233" to "0541112233",
                "עבודה מהבית דחוף! בונוס הצטרפות, הכנסה של 800 ₪ ביום. שלחו הודעה בוואטסאפ 052-7778899" to "0527778899",
                "SALE SALE SALE ALL ITEMS HALF PRICE THIS WEEKEND LIMITED TIME AT OUR STORE CALL 03-5556677" to "0527776655",
                "Special offer: only \$49 instead of \$199! Order now: wa.me/15550001111 Reply STOP to opt out" to "0541230000",
            ),
        )
        val caps = verdict("SALE SALE SALE ALL ITEMS HALF PRICE THIS WEEKEND LIMITED TIME AT OUR STORE CALL 03-5556677", "0527776655")
        assertTrue(caps.reasons.containsAll(listOf(SpamReason.AllCaps, SpamReason.ContactElsewhere)))
        assertTrue(SpamReason.MoneyAmount in verdict("מזגן ב-999 ₪ בלבד", "0523334444").reasons)
    }

    @Test
    fun theShapeAloneNeverDecidesAndNeverCountsForServicesOrPeopleKnown() {
        // All three shape signs together are capped: still below the threshold
        val shape = verdict("PAY \$120 TO THE DRIVER WHEN THE VAN ARRIVES TOMORROW MORNING, CALL 054-5556677", "0545556677")
        assertFalse("${shape.score} ${shape.reasons}", shape.isSpam)
        assertNotSpam(
            listOf(
                "תזכורת: התור שלך במרפאה מחר ב-10:00. השתתפות עצמית 25 ₪. לביטול התקשרו 03-1234567" to "0779990000",
                "היי, זה האינסטלטור. העבודה תעלה 350 ₪, תתקשר אליי 052-1234567" to "0521234567",
                "שופרסל: מבצע! 2 ב-20 ₪. להזמנות חייגו *6000" to "Shufersal",
                "חיוב בסך 120 ₪ בכרטיס המסתיים ב-4821" to "Leumi",
                "Hi, it's Dan from the bike shop, your repair is \$120, call me at 054-5556677" to "0545556677",
                "ההזמנה שלך לשולחן אושרה. לשינויים חייגו 03-6667788" to "0536667788",
                "OK THANKS SEE YOU" to "0541234567",
            ),
        )
        assertFalse(SpamReason.MoneyAmount in verdict("תזכורת: התור שלך במרפאה מחר. השתתפות עצמית 25 ₪", "Clinic").reasons)
        assertFalse(SpamReason.AllCaps in verdict("OK THANKS SEE YOU").reasons)
        val body = "SALE SALE SALE ALL ITEMS HALF PRICE THIS WEEKEND LIMITED TIME AT OUR STORE CALL 03-5556677 \$5"
        assertEquals(0, SpamScorer.score(IncomingSms("0527776655", "Shop", body, isContact = true)).score)
        assertEquals(0, SpamScorer.score(IncomingSms("0527776655", null, body, userWrote = true)).score)
    }

    // --- 4. "פרסומת", the ad marker ---

    @Test
    fun theAdMarkerFromAStrangerIsAnAdLikeTheOptOutLine() {
        assertSpam(
            listOf(
                "פרסומת: מבצע ענק על מזגנים!" to "0547778800",
                "פרסומת: החנות החדשה שלנו נפתחה: shop-new.co.il" to "0547778899",
                "(פרסום) הלוואות לכל מטרה, לפרטים: https://loans-now.co.il" to "0541110000",
                "Ad: Big sale this weekend at our store" to "0501234567",
                "שופרסל דילים - פרסומת: 50% הנחה על הכל https://deals-il.example.com/x" to "DealsIL",
                "פרסומת | סקר קצר על מוצרי חלב, השתתפו וזכו" to "Seker",
                "פרסומת: ייתכן שמגיע לכם החזר מס! בדיקה חינם https://tx1.me/aB9x" to "TaxBack",
            ),
        )
        // The marker and the opt-out line are one sign: counted once
        assertEquals(
            verdict("מבצע! 20% הנחה. להסרה השב הסר", "SuperPharm").score,
            verdict("פרסומת: מבצע! 20% הנחה. להסרה השב הסר", "SuperPharm").score,
        )
        assertEquals(verdict("מבצע! 20% הנחה. להסרה השב הסר").score, verdict("פרסומת: מבצע! 20% הנחה").score)
    }

    @Test
    fun aBrandsAdWithTheMarkerIsNotSpamAndNotAServiceFolder() {
        val cases = listOf(
            Triple("פרסומת: מבצע סוף עונה בסופר-פארם! 20% הנחה. להסרה השב הסר", "SuperPharm", Folder.All),
            Triple("פרסומת: כרטיס אשראי חדש עם הטבות! להסרה השב הסר", "Max", Folder.All),
            Triple("פרסומת: כרטיס אשראי חדש עם הטבות!", "Max", Folder.All),
            Triple("פרסומת: ביטוח נסיעות ב-20% הנחה", "Harel", Folder.All),
            Triple("פרסומת: משלוח חינם על כל הזמנה", "IsraelPost", Folder.All),
            Triple("Fox: פרסומת - קולקציה חדשה באתר https://www.fox.co.il", "Fox", Folder.All),
            Triple("Ad: your next order ships free | Reply STOP to opt out", "Amazon", Folder.All),
        )
        for ((body, address, folder) in cases) {
            val sms = IncomingSms(address, null, body)
            val v = SpamScorer.score(sms)
            assertFalse("$address: $body -> ${v.score} ${v.reasons}", v.isSpam)
            assertEquals("$address: $body", folder, FolderClassifier.classify(sms, v))
        }
        // Without the marker the same bank, insurer and courier are their folders
        assertEquals(Folder.Banks, FolderClassifier.classify(IncomingSms("Max", null, "חיוב בסך 120 ₪ בכרטיס המסתיים ב-4821")))
        assertNotEquals(Folder.All, FolderClassifier.classify(IncomingSms("Harel", null, "פוליסה מס' 5512345 חודשה")))
    }

    @Test
    fun theWordElsewhereIsJustAWord() {
        for (body in listOf(
            "אתמול בערב בטלוויזיה ראיתי פרסומת מצחיקה",
            "פרסום תוצאות הבחירות לוועד יתקיים מחר",
            "Adam here, call me",
            "Ad hoc meeting at 5",
            "הפרסומות בטלוויזיה ארוכות מדי",
        )) {
            assertFalse(body, FolderClassifier.hasAdMarker(body))
        }
        for (body in listOf("פרסומת: מבצע", "שופרסל - פרסומת: מבצע", "(פרסום) מבצע", "פרסום: מבצע", "AD: sale", "[Ad] sale", "‏פרסומת‏ מבצע")) {
            assertTrue(body, FolderClassifier.hasAdMarker(body))
        }
        assertFalse(verdict("פרסום תוצאות הבחירות לוועד יתקיים מחר").isSpam)
    }

    // --- 5. pressure next to a link ---

    @Test
    fun pressureNextToALinkWeighsMore() {
        val withLink = verdict("דחוף לחצו כאן https://example.com/x")
        val without = verdict("דחוף לחצו כאן")
        assertEquals(5, withLink.score - without.score)
        assertSpam(
            listOf(
                "Final notice: verify your account now. Click here: https://acct-review.site/a" to "0501112233",
                "היום בלבד! 90% הנחה על כל האתר, רק 9.90 ₪: https://mega-sale.top/s" to "0547770000",
                "URGENT: Your parcel is on hold. Act now: https://parcel-hold.info/p" to "0531110000",
                "לזמן מוגבל: זכית בשובר! לחצו כאן https://gift-il.site/g" to "0521119999",
                "דחוף - הודעה אחרונה לפני ניתוק החשמל בגלל חוב של 212 ₪. לחצו כאן לתשלום: https://iec-pay.site/b" to "0541113333",
                "Last chance! Claim your free gift today only: https://free-gift.click/x9" to "0551112222",
            ),
        )
    }

    @Test
    fun pressureInGenuineNoticesStaysBelow() {
        assertNotSpam(
            listOf(
                "דחוף, תתקשר אליי כשאתה יכול" to "0541234567",
                "Urgent: the meeting moved to 3pm" to "0521234567",
                "עיריית חיפה - דחוף: הפסקת מים מחר 08:00-12:00. פרטים: https://www.haifa.muni.il" to "Haifa",
                "Leumi: דחוף - נא לאשר את העסקה באפליקציה https://www.leumi.co.il" to "Leumi",
                "תזכורת דחוף: התור שלך מחר ב-08:00. לביטול: https://clinic.example.com/c" to "Clinic",
                "SuperPharm: מבצע היום בלבד! https://www.super-pharm.co.il" to "SuperPharm",
                "קוד האימות שלך 482913, אל תמסור את הקוד. לביטול מיידי: https://www.leumi.co.il" to "Leumi",
                "Act now if you want tickets to the show: https://tickets.example.com" to "0509998877",
            ),
        )
    }

    // --- guard rails ---

    @Test
    fun peopleKnownAndAllowedSendersStayAtZeroWhateverTheNewSigns() {
        val scams = listOf(
            "כביש 6: חוב אגרה. לתשלום: https://kvish6-pay.com/p",
            "הצטרפו לקבוצת ההשקעות בביטקוין: https://t.me/x",
            "שלחו לי את הקוד שקיבלתם",
            "פרסומת: מבצע! 999 ₪ חייגו 03-5551234",
        )
        for (body in scams) {
            assertEquals(body, 0, SpamScorer.score(IncomingSms("+447700900123", "Friend", body, isContact = true)).score)
            assertEquals(body, 0, SpamScorer.score(IncomingSms("+447700900123", null, body, userWrote = true)).score)
            assertEquals(body, 0, SpamScorer.score(IncomingSms("+447700900123", null, body, allowedSenders = listOf("00447700900123"))).score)
        }
    }
}
