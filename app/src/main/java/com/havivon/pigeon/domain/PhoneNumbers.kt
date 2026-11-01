package com.havivon.pigeon.domain

/**
 * Addresses as keys: the same sender written "052-555-0123", "+972 52 555 0123" or "0525550123"
 * must be one sender for the spam list, search and contact matching.
 *
 * Pure Kotlin on purpose. Android's `PhoneNumberUtils.normalizeNumber` turns letters into keypad
 * digits, which would make the sender ID "Leumi" the same key as the number 53864; and it cannot
 * run in a JVM test.
 */
object PhoneNumbers {

    /** Digits compared from the end: enough for a mobile number without its country or trunk prefix. */
    private const val MATCH_DIGITS = 9

    /**
     * A phone number as "+" and digits, or digits only; a sender ID (letters) trimmed and in lower case.
     * Blank stays blank.
     */
    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.any { it.isLetter() }) return trimmed.lowercase()
        val digits = trimmed.filter { it.isDigit() }
        return if (trimmed.startsWith("+") && digits.isNotEmpty()) "+$digits" else digits
    }

    /** Whether this is a phone number (as opposed to a sender ID such as "Leumi"). */
    fun isPhoneNumber(raw: String): Boolean {
        val n = normalize(raw)
        return n.isNotEmpty() && n.removePrefix("+").all { it.isDigit() }
    }

    /**
     * Whether two addresses are the same sender: sender IDs and short codes must be equal; longer
     * numbers are the same when their last [MATCH_DIGITS] digits are ("+972525550123" and
     * "0525550123").
     */
    fun same(a: String, b: String): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        if (!isPhoneNumber(na) || !isPhoneNumber(nb)) return na == nb
        val da = na.removePrefix("+")
        val db = nb.removePrefix("+")
        if (da.length < MATCH_DIGITS || db.length < MATCH_DIGITS) return da == db
        return da.takeLast(MATCH_DIGITS) == db.takeLast(MATCH_DIGITS)
    }

    /**
     * The key a contact's number and a sender's address meet on, so that every contact can be read in
     * one query and looked up in a map: a number's last [MATCH_DIGITS] digits, with an Israeli +972 or
     * 00972 written as the local 0 first ("+972 3-123-4567" and "03-1234567" are one key); a shorter
     * number or short code as its digits; a sender ID in lower case. Null for a blank address.
     */
    fun lookupKey(raw: String): String? {
        val n = normalize(raw)
        if (n.isEmpty()) return null
        if (!isPhoneNumber(n)) return n
        var digits = n.removePrefix("+")
        if (digits.startsWith("00")) digits = digits.drop(2)
        val international = n.startsWith("+") || n.startsWith("00") || digits.length >= 11
        if (international && digits.startsWith("972")) digits = "0" + digits.drop(3).removePrefix("0")
        return if (digits.length > MATCH_DIGITS) digits.takeLast(MATCH_DIGITS) else digits
    }

    /** Whether [address] contains what the user typed: digits by digits ("0525" finds "+972 52-5..."), text by text. */
    fun matchesQuery(address: String, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return false
        if (q.any { it.isLetter() }) return address.contains(q, ignoreCase = true)
        val qDigits = q.filter { it.isDigit() }
        if (qDigits.isEmpty()) return false
        val digits = address.filter { it.isDigit() }
        // A local "05..." also finds the international "+9725...": drop the trunk zero
        return digits.contains(qDigits) || (qDigits.startsWith("0") && qDigits.length > 1 && digits.contains(qDigits.drop(1)))
    }
}
