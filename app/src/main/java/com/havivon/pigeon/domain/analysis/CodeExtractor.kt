package com.havivon.pigeon.domain.analysis

/**
 * Finds the one-time code (verification code, OTP, password) in a message so the app can offer to
 * copy it. A code is 4 to 8 digits, written plainly, as "G-123456" or in two groups ("482 913").
 * There are three ways a message holds one, tried in this order:
 *
 * 1. **Next to a cue word**: code, קוד, סיסמה, אימות, OTP, password... Amounts, dates, times, phone numbers and
 *    card endings are blanked out first, so "חיוב 1500 ש"ח בכרטיס המסתיים ב-4821. קוד: 773421" gives 773421. When
 *    several numbers remain, the one closest to the cue word wins.
 * 2. **The whole message is the code** ([bareCode]): "66655", " 482913 ", "G-123456", "123 456", "קוד: 7781",
 *    with surrounding spaces, punctuation and direction marks ignored. Not a year ("2026") nor an 8-digit date
 *    ("15112026"); anything with a currency sign, a colon, a slash or more digits (a time, a date, a phone
 *    number, an amount: "15:30", "12/11", "054-1234567", "₪120") is not a whole-number message. Never from a
 *    known person (a saved contact, someone the user wrote to): a friend's "1234" is a message, maybe a gate
 *    code, but it must not move the conversation to Codes nor be deleted with the codes. A stranger's number
 *    sending only "1234" does count: services send codes from plain numbers too.
 * 3. **A short message from a sender id** ([shortServiceCode]), such as "HAREL: 66655 לכניסה": at most
 *    [SHORT_BODY] characters, exactly one 4 to 8 digit number, and nothing that looks like an amount, a date, a
 *    time or a phone number, nor a word that names the number as something else (an order, a reference, a
 *    coupon, a flight...).
 *
 * Without a cue word, anything else has no code. Letters-and-digits codes ("K7P2QD") are not recognised.
 */
object CodeExtractor {

    /**
     * The code as digits only ("G-123456" gives "123456", "482 913" gives "482913"), or null if the message has none.
     * [address] (as the system stores it) and [knownPerson] (a saved contact, or someone the user wrote to) say who
     * sent it; without them a whole-number message still counts, a short message from a sender id does not.
     */
    fun extract(text: String, address: String? = null, knownPerson: Boolean = false): String? {
        cueCode(text)?.let { return it }
        if (knownPerson) return null
        bareCode(text)?.let { return it }
        if (address != null && Text.isAlphanumericSender(address)) return shortServiceCode(text)
        return null
    }

    /** Rule 1: the number closest to a cue word, or null. */
    private fun cueCode(text: String): String? {
        val lower = lowerKeepingLength(text)
        val cues = findCues(lower)
        if (cues.isEmpty()) return null
        val masked = mask(lower)
        var best: String? = null
        var bestScore = Int.MAX_VALUE
        for (candidate in candidates(masked)) {
            val score = cues.minOf { distance(candidate.range, it) }
            if (score <= MAX_DISTANCE && score < bestScore) {
                best = candidate.digits
                bestScore = score
            }
        }
        return best
    }

    // --- rule 2: the whole message is the code ---

    /** An optional label before a whole-message code: "קוד: 7781", "Code - 7781". */
    private val LABEL = Regex("^(?:code|otp|pin|קוד|סיסמה)\\s{0,3}[:\\-]?\\s{0,3}")

    /** "66655", "G-123456" (up to 4 letters and a dash), "123 456", "1234-5678". */
    private val WHOLE = Regex("(?:[a-z]{1,4}-)?\\d{4,8}|\\d{3}[ -]\\d{3}|\\d{4}[ -]\\d{4}")

    /**
     * Punctuation that may surround a whole-message code. Not "#", "*", "%", "+", "•" or a currency sign:
     * "#1234", "*2345", "1200%", "+1234", "••1234" and "₪1200" are not codes.
     */
    private const val EDGE_PUNCTUATION = ".,;:!?\"'()[]{}<>«»“”„‘’״׳-–—~_|"

    /** The message is only a code (rule 2), or null. */
    internal fun bareCode(text: String): String? {
        var s = trimEdges(lowerKeepingLength(text).trim().removePrefix("<#>"))
        // Right after the label: "קוד" and the number far apart are not one code
        LABEL.find(s)?.let { s = s.substring(it.range.last + 1) }
        if (!WHOLE.matches(s)) return null
        val digits = s.filter { it.isDigit() }
        return digits.takeUnless { looksLikeYear(it) || looksLikeDate(it) }
    }

    private fun trimEdges(s: String): String {
        var start = 0
        var end = s.length
        while (start < end && isEdge(s[start])) start++
        while (end > start && isEdge(s[end - 1])) end--
        return s.substring(start, end)
    }

    /** Space, direction marks and joiners, emoji and their variation selectors, and the punctuation of [EDGE_PUNCTUATION]. */
    private fun isEdge(ch: Char): Boolean {
        if (ch.isWhitespace() || ch in EDGE_PUNCTUATION) return true
        return when (Character.getType(ch).toByte()) {
            Character.FORMAT, Character.NON_SPACING_MARK, Character.OTHER_SYMBOL, Character.SURROGATE -> true
            else -> false
        }
    }

    /** "1999", "2026": a year, more likely than a 4-digit code that happens to look like one. */
    private fun looksLikeYear(digits: String): Boolean = digits.length == 4 && (digits.startsWith("19") || digits.startsWith("20"))

    /** "15112026" or "20261115": a date written without separators. */
    private fun looksLikeDate(digits: String): Boolean {
        if (digits.length != 8) return false
        fun num(from: Int, len: Int) = digits.substring(from, from + len).toInt()
        fun valid(day: Int, month: Int, year: Int) = day in 1..31 && month in 1..12 && year in 1900..2099
        return valid(num(0, 2), num(2, 2), num(4, 4)) || valid(num(6, 2), num(4, 2), num(0, 4))
    }

    // --- rule 3: a short message from a sender id ---

    /** The longest message rule 3 reads: a code and a few words ("HAREL: 66655 לכניסה לאזור האישי"). */
    const val SHORT_BODY = 40

    /** Words that name the number as something else than a code. */
    private val NOT_A_CODE_NUMBER = Regex(
        "(?<![a-z])(?:order|ref|reference|invoice|receipt|tracking|parcel|package|flight|room|seat|table|branch|ticket|" +
            "account|acct|customer|member|promo|coupon|voucher|discount|points?|no\\.|number|id)(?![a-z])" +
            "|הזמנ|אסמכתא|חשבונית|קבלה|מספר|מס'|מעקב|משלוח|חבילה|טיסה|חדר|מושב|שולחן|סניף|תור|כרטיס|חשבון|לקוח|מנוי|" +
            "קופון|הנחה|שובר|נקודות|ת\"ז|#",
    )

    /** Any run of 4 or more digits. */
    private val DIGIT_RUN = Regex("\\d{4,}")

    /** Rule 3, or null. */
    internal fun shortServiceCode(text: String): String? {
        val lower = lowerKeepingLength(text)
        if (lower.trim().length > SHORT_BODY || NOT_A_CODE_NUMBER.containsMatchIn(lower)) return null
        // An amount, a date, a time, a phone number or a card ending anywhere: the number may be any of them
        if (SUSPICIOUS.any { it.containsMatchIn(lower) }) return null
        val masked = maskWith(lower, UNITS)
        val only = candidates(masked).singleOrNull() ?: return null
        // Another long run of digits (a 9-digit reference) makes it unclear which number is meant
        if (DIGIT_RUN.findAll(masked).any { it.range.first !in only.range }) return null
        return only.digits.takeUnless { looksLikeYear(it) || looksLikeDate(it) }
    }

    /** How far, in characters, a code may be from its cue word. "קוד האימות שלך לכניסה לחשבון הוא 582913" needs about 30. */
    private const val MAX_DISTANCE = 45

    /** A code written before its cue ("123456 is your code") counts as slightly farther than one after it. */
    private const val BEFORE_CUE_PENALTY = 5

    private class Candidate(val range: IntRange, val digits: String)

    private fun distance(code: IntRange, cue: IntRange): Int =
        if (code.first > cue.last) code.first - cue.last - 1 else cue.first - code.last - 1 + BEFORE_CUE_PENALTY

    private fun lowerKeepingLength(text: String): String = String(CharArray(text.length) { text[it].lowercaseChar() })

    // --- cue words ---

    private val CUE = Regex(
        "(?<![a-z])(?:code|otp|pin|passcode|password|verification|verify|2fa|one-time|authentication)s?(?![a-z])" +
            "|קוד(?!ם)|סיסמ|אימות|הזדהות|מאמת",
    )

    /** Words just before "code" that make it something else: a promo code, a zip code, a bar code. */
    private val NOT_A_CODE_BEFORE = Regex("(?:promo|coupon|discount|zip|postal|area|country|bar|qr|tracking|gift|voucher|referral|invite|dial)\\s*-?\\s*$")
    private val NOT_A_CODE_AFTER = Regex("^\\s*(?:הנחה|קופון|מבצע|הטבה|שובר|מתנה|discount|coupon|promo)")

    private fun findCues(lower: String): List<IntRange> = CUE.findAll(lower).filter { m ->
        val before = lower.substring(maxOf(0, m.range.first - 12), m.range.first)
        val after = lower.substring(m.range.last + 1, minOf(lower.length, m.range.last + 12))
        !NOT_A_CODE_BEFORE.containsMatchIn(before) && !NOT_A_CODE_AFTER.containsMatchIn(after) &&
            !before.endsWith("מי") && !before.endsWith("בר") // מיקוד (zip code), ברקוד (bar code)
    }.map { it.range }.toList()

    // --- things that look like codes but are not ---

    private const val MONTHS_HE = "ינואר|פברואר|מרץ|מרס|אפריל|מאי|יוני|יולי|אוגוסט|ספטמבר|אוקטובר|נובמבר|דצמבר"
    private const val MONTHS_EN = "jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec"
    private const val CURRENCY_WORDS = "ש[\"״'׳]{0,2}ח|שקלים|שקל|דולר|אירו|יורו|nis|ils|usd|eur|dollars?|euros?|bucks"
    private const val UNIT_WORDS = "אחוז|percent|points?|נקודות|דקות|דקה|שעות|שעה|ימים|יום|שניות|שנים|שנה|minutes?|mins?|hours?|hrs?|days?|seconds?|secs?|years?|km|ק\"מ|kg|ml|mb|gb|kb"

    private val PHONES = listOf(
        // Phone numbers: Israeli with or without +972, 1-700 / 1-800 lines, other countries, US style.
        Regex("(?<![\\d])(?:\\+972|00972|972)[ -]?\\(?0?\\)?[2-9]\\d?[ -]?\\d{3}[ -]?\\d{4}(?!\\d)"),
        Regex("(?<![\\d])0[2-9]\\d?[ -]?\\d{3}[ -]?\\d{4}(?!\\d)"),
        Regex("(?<![\\d])1[ -]?[78]00[ -]?\\d{3}[ -]?\\d{3,4}(?!\\d)"),
        Regex("\\+\\d{1,3}[ -]?\\(?\\d{1,4}\\)?(?:[ -]?\\d{2,4}){2,4}"),
        Regex("\\(?\\d{3}\\)?[ -]\\d{3}-\\d{4}(?!\\d)"),
    )

    private val CARDS = listOf(
        // Card and account endings: "המסתיים ב-4821", "ending in 4821", "כרטיס 4821", "****4821", "x4821".
        Regex("(?:שמסתיים|המסתיים|מסתיים|סיומת|ending|ends|last (?:4|four)(?: digits)?)\\s*(?:ב-?|in|with|:|-)*\\s*[*•x.…]*\\d{4}(?!\\d)"),
        Regex("(?:כרטיס|card|account|acct|חשבון)\\s*(?:ב-?|in|with|:|-|#|no\\.?|number|מספר)*\\s*[*•x.…]*\\d{4}(?!\\d)"),
        Regex("[*•…]+\\s*\\d{4}(?!\\d)"),
        Regex("(?<![a-z0-9])x{1,4}\\d{4}(?!\\d)"),
    )

    private val DATES = listOf(
        // Dates and times with a month name: "15 בנובמבר 2026", "Nov 15, 2026", "15th of November".
        Regex("\\d{1,2}\\s*-?\\s*(?:ב|ל)?-?(?:$MONTHS_HE)(?:\\s+\\d{4})?"),
        Regex("(?:ב|ל)?(?:$MONTHS_HE)\\s+\\d{4}"),
        Regex("(?<![a-z])(?:$MONTHS_EN)[a-z]*\\.?\\s+\\d{1,2}(?:st|nd|rd|th)?(?:,?\\s+\\d{4})?"),
        Regex("\\d{1,2}(?:st|nd|rd|th)?\\s+(?:of\\s+)?(?:$MONTHS_EN)[a-z]*\\.?(?:,?\\s+\\d{4})?"),
        Regex("(?<![a-z])(?:$MONTHS_EN)[a-z]*\\.?\\s+\\d{4}"),
    )

    private val AMOUNTS = listOf(
        // Amounts: a currency sign or word on either side, a word like "total" or "יתרה" before.
        Regex("[₪$€£]\\s*\\d[\\d,.]*"),
        Regex("\\d[\\d,.]*\\s*(?:[₪$€£%]|(?:$CURRENCY_WORDS)(?![\\p{L}]))"),
        Regex("(?<![a-z])(?:nis|ils|usd|eur)\\s*\\d[\\d,.]*"),
        Regex("(?:בסך|סכום|amount|total|balance|יתרה|price|מחיר|סה\"כ|charged|חויבת)(?:\\s+(?:of|is|הוא|של))?\\s*[:=-]?\\s*\\d[\\d,.]*"),
    )

    /** A number with a unit after it: "5 דקות", "10 minutes". */
    private val UNITS = listOf(Regex("\\d[\\d,.]*\\s*(?:$UNIT_WORDS)(?![\\p{L}])"))

    private val JOINED = listOf(
        // Numbers joined by . / : , - : 15.11.2026, 18:30, 1,250, 2026-11-15, 192.168.0.1.
        Regex("\\d+(?:[./:,]\\d+)+"),
        Regex("\\d+(?:-\\d+){2,}"),
    )

    private val MASKS = PHONES + CARDS + DATES + AMOUNTS + UNITS + JOINED

    /** What rule 3 refuses outright: a short message with any of these is not a code without a cue word. */
    private val SUSPICIOUS = PHONES + CARDS + DATES + AMOUNTS + JOINED

    private fun mask(lower: String): String = maskWith(lower, MASKS)

    private fun maskWith(lower: String, masks: List<Regex>): String {
        val out = StringBuilder(lower)
        for (re in masks) {
            // Matches are found on the text as it stands, so a blanked-out span cannot match twice.
            for (m in re.findAll(out.toString())) for (i in m.range) out.setCharAt(i, ' ')
        }
        return out.toString()
    }

    // --- candidates ---

    private val GROUPED = Regex("(?<![0-9a-z-])(\\d{3})[ -](\\d{3})(?![0-9a-z])|(?<![0-9a-z-])(\\d{4})[ -](\\d{4})(?![0-9a-z])")
    private val PLAIN = Regex("(?<![0-9a-z])(\\d{4,8})(?![0-9a-z])")

    private fun candidates(masked: String): List<Candidate> {
        val out = ArrayList<Candidate>()
        val taken = BooleanArray(masked.length)
        for (m in GROUPED.findAll(masked)) {
            out += Candidate(m.range, m.value.filter { it.isDigit() })
            for (i in m.range) taken[i] = true
        }
        for (m in PLAIN.findAll(masked)) {
            if (m.range.any { taken[it] } || !plausible(masked, m.range)) continue
            out += Candidate(m.range, m.value)
        }
        return out
    }

    /** Rules out a number by what touches it: a decimal, a date or phone fragment, an order number, a percentage. */
    private fun plausible(s: String, r: IntRange): Boolean {
        val prev = s.getOrNull(r.first - 1)
        val prev2 = s.getOrNull(r.first - 2)
        val next = s.getOrNull(r.last + 1)
        val next2 = s.getOrNull(r.last + 2)
        if (prev != null && prev in "./:,-" && prev2 != null && prev2.isDigit()) return false
        if (next != null && next in "./:,-" && next2 != null && next2.isDigit()) return false
        if (prev != null && prev in "#+$₪€£") return false
        if (next == '%') return false
        return true
    }
}
