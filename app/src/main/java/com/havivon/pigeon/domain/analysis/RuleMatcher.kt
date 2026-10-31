package com.havivon.pigeon.domain.analysis

import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.RuleMatch

/** Picks the colour rule that applies to a conversation or a message. */
object RuleMatcher {

    /**
     * The first enabled rule in [rules], in list order, that matches. A [RuleMatch.Sender] rule matches
     * the contact name or the address; a [RuleMatch.Keyword] rule matches the [body]. Null if none does.
     */
    fun firstMatch(rules: List<ColorRule>, contactName: String?, address: String, body: String): ColorRule? {
        val words = Text.words(body)
        return rules.firstOrNull { it.enabled && matches(it.match, contactName, address, words) }
    }

    /** Whether [match] applies, ignoring whether the rule is enabled. */
    fun matches(match: RuleMatch, contactName: String?, address: String, body: String): Boolean =
        matches(match, contactName, address, Text.words(body))

    private fun matches(match: RuleMatch, contactName: String?, address: String, bodyWords: List<String>): Boolean = when (match) {
        is RuleMatch.Sender -> senderMatches(match.name, contactName, address)
        is RuleMatch.Keyword -> Text.containsPhrase(bodyWords, match.word)
    }

    /**
     * Phone numbers compare by their digits, whatever the formatting: "054-123-4567", "+972 54 123 4567"
     * and "0541234567" are the same sender. Names compare ignoring case, and the rule may name a whole word
     * or words of the contact ("הפועלים" matches "בנק הפועלים"). The address is compared the same way, so an
     * alphanumeric sender such as "Leumi" works.
     */
    private fun senderMatches(rule: String, contactName: String?, address: String): Boolean {
        val wanted = rule.trim()
        if (wanted.isEmpty()) return false
        val wantedPhone = Text.phoneKey(wanted)
        if (wantedPhone != null) return wantedPhone == Text.phoneKey(address) || (contactName != null && wantedPhone == Text.phoneKey(contactName))
        val wantedWords = Text.words(wanted)
        if (wantedWords.isEmpty()) return false
        return listOfNotNull(contactName, address).any { containsWords(Text.words(it), wantedWords) }
    }

    private fun containsWords(haystack: List<String>, needle: List<String>): Boolean {
        if (needle.size > haystack.size) return false
        return (0..haystack.size - needle.size).any { start -> needle.indices.all { haystack[start + it] == needle[it] } }
    }
}
