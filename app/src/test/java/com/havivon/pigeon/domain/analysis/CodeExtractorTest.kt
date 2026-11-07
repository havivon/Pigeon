package com.havivon.pigeon.domain.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodeExtractorTest {

    @Test
    fun findsTheCodeNextToItsCueWord() {
        val cases = listOf(
            "קוד האימות שלך הוא 482913" to "482913",
            "קוד האימות שלך לכניסה לחשבון הוא 582913. אל תשתף אותו." to "582913",
            "הקוד שלך: 7421 (תקף 5 דקות)" to "7421",
            "הסיסמה החד-פעמית שלך היא 904711" to "904711",
            "סיסמה חד פעמית: 55120938" to "55120938",
            "בקוד 9021 תוכל להיכנס" to "9021",
            "Your verification code is 482913. Do not share it." to "482913",
            "Your OTP is 731904, valid for 10 minutes" to "731904",
            "<#> 4821 is your code" to "4821",
            "482913 הוא קוד האימות שלך" to "482913",
            "Use 4821 as your Uber code" to "4821",
            "Your PIN is 4455" to "4455",
            "Your password is 90817263" to "90817263",
            "Use 123456 to verify your account" to "123456",
            "🔐 Your code: 918273 🔐" to "918273",
            "‏קוד: 123456‏" to "123456",
            "your code is 2026" to "2026",
        )
        for ((text, code) in cases) assertEquals(text, code, CodeExtractor.extract(text))
    }

    @Test
    fun joinedAndGroupedCodes() {
        val cases = listOf(
            "G-123456 is your Google verification code." to "123456",
            "Your code: FB-77421" to "77421",
            "קוד אימות: 482 913" to "482913",
            "Code: 123-456" to "123456",
            "Your verification code is 1234 5678" to "12345678",
        )
        for ((text, code) in cases) assertEquals(text, code, CodeExtractor.extract(text))
    }

    @Test
    fun amountsAreNotCodes() {
        val cases = listOf(
            "חיוב של 1500 ש\"ח בכרטיס המסתיים ב-4821. קוד אימות: 773421" to "773421",
            "חויבת ב-1,250.00 ₪. קוד: 6612" to "6612",
            "קוד אימות 5521 לאישור עסקה בסך 1500" to "5521",
            "קוד 4821 לאישור תשלום של 1500 ₪" to "4821",
            "קוד: 4821 סכום 2500 ש״ח" to "4821",
            "סכום העסקה 1500 קוד אימות 7788" to "7788",
            "Charged \$1500.00 on card ending in 4821. Your code is 336699" to "336699",
            "Total: USD 2500. Verification code 5523" to "5523",
            "Your code 5523 for a payment of 2500 dollars" to "5523",
            "קוד 3344 לאישור כרטיס 4821" to "3344",
        )
        for ((text, code) in cases) assertEquals(text, code, CodeExtractor.extract(text))
    }

    @Test
    fun datesAndTimesAreNotCodes() {
        val cases = listOf(
            "קוד האימות 662211 תקף עד 15/11/2026" to "662211",
            "Your code 662211 expires on 2026-11-15 at 18:30" to "662211",
            "Code 5544, appointment on Nov 15, 2026" to "5544",
            "קוד 7788 לתור ב-15 בנובמבר 2026" to "7788",
            "קוד: 1830 בשעה 18:30" to "1830",
            "תור ב-15112026 קוד: 8821" to "8821",
            "Your code is 3490 for 15th of November 2026" to "3490",
        )
        for ((text, code) in cases) assertEquals(text, code, CodeExtractor.extract(text))
    }

    @Test
    fun phoneNumbersAndOrderNumbersAreNotCodes() {
        val cases = listOf(
            "Call 054-123-4567 or enter code 8890" to "8890",
            "קוד 3321 – לבירורים 03-1234567" to "3321",
            "לבירורים +972 54 123 4567. קוד: 4410" to "4410",
            "קוד: 8890 לבירורים 1-700-500-600" to "8890",
            "מספר הזמנה 4455 קוד אימות 7788" to "7788",
            "ישראל פוסט: קוד איסוף 4455 לחבילה 7788990011" to "4455",
        )
        for ((text, code) in cases) assertEquals(text, code, CodeExtractor.extract(text))
    }

    @Test
    fun noCueWordMeansNoCode() {
        val cases = listOf(
            "הזמנה 482913 נשלחה",
            "Your order 123456 has shipped",
            "תאריך 15.11.2026 בשעה 18:30",
            "המסתיים ב-4821",
            "Card ending in 4821 was charged \$15.00",
            "חיוב של 1500 ש\"ח",
            "שלום מה נשמע",
            "",
        )
        for (text in cases) assertNull(text, CodeExtractor.extract(text))
    }

    @Test
    fun cueWordWithoutAUsableNumberIsNotACode() {
        val cases = listOf(
            "Your verification code is 12",
            "verification code 123456789",
            "הקוד שלך הוא ABCDEF",
            "קוד אימות: 15.11.2026",
            "Enter the code we sent you",
            "קוד אימות: 12:34",
        )
        for (text in cases) assertNull(text, CodeExtractor.extract(text))
    }

    @Test
    fun otherKindsOfCodeAreNotOneTimeCodes() {
        val cases = listOf(
            "קוד הנחה 2026 לקנייה הבאה",
            "קוד קופון 5555 בקופה",
            "Use promo code 5555 at checkout",
            "ZIP code 90210",
            "Area code 0212 applies",
            "מיקוד 7654321 בלבד",
            "קודם כל, תגיע ב-2026",
            "ברקוד 5012345678903",
        )
        for (text in cases) assertNull(text, CodeExtractor.extract(text))
    }

    @Test
    fun realLookingMessages() {
        val cases = listOf(
            "Your Wolt code: 5487. Order #123456" to "5487",
            "קוד:482913 תקף ל-5 דקות" to "482913",
            "OTP 123456 for txn of Rs 5000. Do not share." to "123456",
            "Ref no 99887766 code 4455" to "4455",
            "your code 483920 (valid until 18:30)" to "483920",
            "קוד האימות שלך בבנק הוא 902341. אם לא ביקשת, התקשר *2345" to "902341",
            "הקוד שלך ב-Bit: 7731\nתוקף 10 דקות" to "7731",
            "[Facebook] 123456 is your confirmation code" to "123456",
            "Dear customer, 4455 is the OTP for your login at 14:05 on 01/11/2026." to "4455",
            "אימות דו-שלבי: 662019" to "662019",
            "ביטוח לאומי: קוד הזדהות 3381, בתוקף עד 14:30" to "3381",
        )
        for ((text, code) in cases) assertEquals(text, code, CodeExtractor.extract(text))
    }

    @Test
    fun longMessagesAndNoiseDoNotConfuseIt() {
        val noise = "שלום רב, ".repeat(40)
        assertEquals("482913", CodeExtractor.extract(noise + "קוד האימות הוא 482913"))
        assertNull(CodeExtractor.extract("קוד" + " ".repeat(200) + "482913"))
    }

    // A message that is only the code, without a cue word ("HAREL" sent "66655")

    @Test
    fun aMessageThatIsOnlyANumberIsACode() {
        val cases = listOf(
            "66655" to "66655",
            "  482913 " to "482913",
            "G-123456" to "123456",
            "123 456" to "123456",
            "1234-5678" to "12345678",
            "קוד: 7781" to "7781",
            "Code - 7781" to "7781",
            "482913." to "482913",
            "(482913)" to "482913",
            "‏482913‏" to "482913",
            "<#> 482913" to "482913",
            "🔐 918273" to "918273",
        )
        for ((text, code) in cases) {
            assertEquals(text, code, CodeExtractor.extract(text))
            assertEquals("from a sender id: $text", code, CodeExtractor.extract(text, "HAREL"))
            assertEquals("from a stranger's number: $text", code, CodeExtractor.extract(text, "+972541234567"))
        }
    }

    @Test
    fun aNumberThatLooksLikeSomethingElseIsNotACode() {
        val cases = listOf(
            "2026", "1999", "15:30", "054-1234567", "0541234567", "+972541234567", "₪120", "₪1200", "1200₪", "1200 ש\"ח",
            "12/11", "12.11", "15.11.2026", "15112026", "20261115", "1200%", "#1234", "*2345", "••1234", "123", "123456789",
            "12 34", "1,250", "K7P2QD", "",
        )
        for (text in cases) {
            assertNull(text, CodeExtractor.extract(text))
            assertNull("from a sender id: $text", CodeExtractor.extract(text, "HAREL"))
        }
    }

    /** Decided: a friend's "1234" is a message (a gate code, a PIN they share), not a one-time code to file under Codes or delete after a day. */
    @Test
    fun aKnownPersonsBareNumberIsAMessage() {
        assertNull(CodeExtractor.extract("1234", "+972541234567", knownPerson = true))
        assertNull(CodeExtractor.extract("66655", "HAREL", knownPerson = true))
        assertEquals("a stranger's number may send a code", "1234", CodeExtractor.extract("1234", "+972541234567", knownPerson = false))
        assertEquals("a cue word still counts for a contact", "4590", CodeExtractor.extract("הקוד לשער 4590", "+972541234567", knownPerson = true))
    }

    @Test
    fun aShortMessageFromASenderIdWithOneNumberIsACode() {
        val cases = listOf(
            "HAREL: 66655" to "66655",
            "66655 לכניסה לאזור האישי" to "66655",
            "Your Harel login: 482913" to "482913",
            "482913 בתוקף ל-10 דקות" to "482913",
            "Use G-123456 to sign in" to "123456",
        )
        for ((text, code) in cases) {
            assertEquals(text, code, CodeExtractor.extract(text, "HAREL"))
            assertNull("only from a sender id: $text", CodeExtractor.extract(text, "+972541234567"))
            assertNull("only with the sender known: $text", CodeExtractor.extract(text))
        }
    }

    @Test
    fun aShortMessageWithAnAmountDatePhoneOrOtherNumberIsNotACode() {
        val cases = listOf(
            "Your order 123456 has shipped",
            "הזמנה 482913 נשלחה",
            "אסמכתא 556677",
            "שולם ₪120, תודה 556677",
            "תור ב-12/11 בשעה 4455",
            "חייגו 03-1234567 ונציג 4455",
            "Flight 4455 boards now",
            "4455 or 6677?",
            "Points balance: 4500",
            "Happy 2026 from Harel",
            "4455 and ref 99887766",
            "שלום! 66655 " + "ועוד מילים רבות שהופכות את ההודעה לארוכה",
        )
        for (text in cases) assertNull(text, CodeExtractor.extract(text, "HAREL"))
    }
}
