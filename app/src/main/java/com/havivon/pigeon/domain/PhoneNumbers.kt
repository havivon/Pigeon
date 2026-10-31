package com.havivon.pigeon.domain

/**
 * Addresses as keys: the same sender written "052-555-0123", "+972 52 555 0123" or "0525550123"
 * must be one sender for the spam list, search and contact matching.
 *
 * Pure Kotlin on purpose. Android's `PhoneNumberUtils.normalizeNumber` turns letters into keypad
 * digits, which would make the sender ID "Leumi" the same key as the number 53864; and it cannot
 * run in a JVM test. Contact names are still looked up through the system's own matching
 * (`PhoneLookup`), so this is only for the app's own keys.
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
