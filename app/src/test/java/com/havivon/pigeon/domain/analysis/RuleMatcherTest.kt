package com.havivon.pigeon.domain.analysis

import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.RuleMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleMatcherTest {

    private fun sender(id: Int, name: String, enabled: Boolean = true) = ColorRule(id, "s$id", RuleMatch.Sender(name), 0xFF000000, enabled)

    private fun keyword(id: Int, word: String, enabled: Boolean = true) = ColorRule(id, "k$id", RuleMatch.Keyword(word), 0xFF000000, enabled)

    private fun firstId(rules: List<ColorRule>, name: String?, address: String, body: String) =
        RuleMatcher.firstMatch(rules, name, address, body)?.id

    @Test
    fun firstEnabledRuleInListOrderWins() {
        val rules = listOf(keyword(1, "משלוח"), sender(2, "אמא"), keyword(3, "תביא"))
        assertEquals(1, firstId(rules, "אמא", "0541234567", "המשלוח הגיע, תביא לחם"))
        assertEquals(2, firstId(rules, "אמא", "0541234567", "תביא לחם"))
        assertEquals(3, firstId(rules, "דני", "0541234567", "תביא לחם"))
        assertNull(firstId(rules, "דני", "0541234567", "שלום"))
    }

    @Test
    fun disabledRulesAreSkipped() {
        val rules = listOf(keyword(1, "פגישה", enabled = false), keyword(2, "פגישה"))
        assertEquals(2, firstId(rules, null, "0541234567", "פגישה מחר"))
        assertNull(firstId(listOf(keyword(1, "פגישה", enabled = false)), null, "0541234567", "פגישה מחר"))
        assertNull(firstId(emptyList(), "אמא", "x", "y"))
    }

    @Test
    fun hebrewPrefixesBeforeTheKeywordStillMatch() {
        val rule = listOf(keyword(1, "משלוח"))
        val matching = listOf(
            "משלוח", "המשלוח", "ומשלוח", "במשלוח", "למשלוח", "שמשלוח", "כמשלוח", "והמשלוח", "כשהמשלוח", "וכשהמשלוח",
            "המשלוח הגיע", "תודה על המשלוח!", "משלוח.", "(המשלוח)", "המשלוח‏", "מַשְׁלוֹחַ",
        )
        for (body in matching) assertEquals(body, 1, firstId(rule, null, "x", body))
        val notMatching = listOf("משלוחים", "המשלוחים", "שלוח", "משלו", "אמשלוח", "פרמשלוח", "")
        for (body in notMatching) assertNull(body, firstId(rule, null, "x", body))
    }

    @Test
    fun keywordsMatchWholeWordsCaseInsensitively() {
        val rule = listOf(keyword(1, "Delivery"))
        for (body in listOf("delivery today", "Your DELIVERY is here", "delivery, tomorrow", "the delivery.", "[delivery]")) {
            assertEquals(body, 1, firstId(rule, null, "x", body))
        }
        for (body in listOf("deliveryman", "redelivery", "deliveries", "deli very")) assertNull(body, firstId(rule, null, "x", body))
    }

    @Test
    fun phraseKeywordsNeedTheWordsInOrder() {
        val rule = listOf(keyword(1, "חבילה ממתינה"))
        assertEquals(1, firstId(rule, null, "x", "החבילה ממתינה לך"))
        assertEquals(1, firstId(rule, null, "x", "יש חבילה  ממתינה"))
        assertNull(firstId(rule, null, "x", "ממתינה חבילה"))
        assertNull(firstId(rule, null, "x", "חבילה היא ממתינה"))
        assertNull(firstId(listOf(keyword(1, "")), null, "x", "anything"))
        assertNull(firstId(listOf(keyword(1, "   ")), null, "x", "anything"))
    }

    @Test
    fun keywordsWithPunctuationAndMixedScripts() {
        assertEquals(1, firstId(listOf(keyword(1, "OTP")), null, "x", "Your OTP: 1234"))
        assertEquals(1, firstId(listOf(keyword(1, "ש\"ח")), null, "x", "חיוב 150 ש\"ח"))
        assertEquals(1, firstId(listOf(keyword(1, "PayPal")), null, "x", "הועבר דרך PayPal בהצלחה"))
        assertEquals(1, firstId(listOf(keyword(1, "לא")), null, "x", "לא"))
        assertNull(firstId(listOf(keyword(1, "לא")), null, "x", "ולא")) // two-letter keywords take no prefix
    }

    @Test
    fun senderRulesMatchContactNameIgnoringCase() {
        val cases = listOf(
            Triple("אמא", "אמא", true),
            Triple("דני כהן", "דני כהן", true),
            Triple("הפועלים", "בנק הפועלים", true),
            Triple("בנק הפועלים", "בנק הפועלים", true),
            Triple("john", "John Smith", true),
            Triple("JOHN SMITH", "john smith", true),
            Triple("דני", "דניאל", false),
            Triple("אמא", "אמא של דני", true),
            Triple("דני כהן", "כהן דני", false),
            Triple("יוסי", "דני", false),
        )
        for ((rule, contact, expected) in cases) {
            assertEquals("$rule vs $contact", expected, firstId(listOf(sender(1, rule)), contact, "0541234567", "x") == 1)
        }
    }

    @Test
    fun senderRulesMatchAlphanumericAddress() {
        val rules = listOf(sender(1, "Leumi"))
        assertEquals(1, firstId(rules, null, "Leumi", "x"))
        assertEquals(1, firstId(rules, null, "LEUMI", "x"))
        assertEquals(1, firstId(rules, null, "Bank-Leumi", "x"))
        assertNull(firstId(rules, null, "Leumicard", "x"))
        assertNull(firstId(rules, null, "Hapoalim", "x"))
    }

    @Test
    fun phoneNumbersCompareByTheirDigits() {
        val rule = listOf(sender(1, "054-123-4567"))
        val same = listOf("0541234567", "054-123-4567", "+972541234567", "+972 54-123-4567", "00972541234567", "972541234567", "(054) 123 4567")
        for (address in same) assertEquals(address, 1, firstId(rule, null, address, "x"))
        for (address in listOf("0541234568", "0521234567", "123456", "Leumi")) assertNull(address, firstId(rule, null, address, "x"))

        val international = listOf(sender(1, "+972541234567"))
        assertEquals(1, firstId(international, null, "0541234567", "x"))
        assertEquals(1, firstId(international, null, "054 123 4567", "x"))

        // A saved number stored as the contact's name still matches.
        assertEquals(1, firstId(rule, "054-123-4567", "unknown", "x"))
    }

    @Test
    fun otherCountriesPhoneNumbers() {
        val rule = listOf(sender(1, "+1 415 555 0100"))
        assertEquals(1, firstId(rule, null, "+14155550100", "x"))
        assertEquals(1, firstId(rule, null, "001-415-555-0100", "x"))
        assertNull(firstId(rule, null, "4155550100", "x"))
    }

    @Test
    fun blankSenderRuleNeverMatches() {
        assertNull(firstId(listOf(sender(1, "")), "אמא", "054", "x"))
        assertNull(firstId(listOf(sender(1, "   ")), "אמא", "054", "x"))
    }

    @Test
    fun ruleMatchesIgnoresEnabledFlag() {
        assertTrue(RuleMatcher.matches(RuleMatch.Keyword("פגישה"), null, "x", "הפגישה מחר"))
        assertFalse(RuleMatcher.matches(RuleMatch.Sender("אמא"), "דני", "x", "הפגישה מחר"))
    }

    @Test
    fun realLookingRuleSet() {
        val rules = listOf(sender(1, "הפועלים"), keyword(2, "משלוח"), sender(3, "אמא"), keyword(4, "פגישה", enabled = false))
        assertEquals(1, firstId(rules, "בנק הפועלים", "0391234567", "יתרה: 1,200 ש\"ח"))
        assertEquals(2, firstId(rules, null, "Cheetah", "המשלוח שלך יגיע היום"))
        assertEquals(3, firstId(rules, "אמא", "0541234567", "מה קורה"))
        assertNull(firstId(rules, "דני", "0541234567", "פגישה מחר"))
    }
}
