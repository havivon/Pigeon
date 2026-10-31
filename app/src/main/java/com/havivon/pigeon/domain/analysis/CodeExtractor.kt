package com.havivon.pigeon.domain.analysis

/**
 * Finds the one-time code (verification code, OTP, password) in a message so the app can offer to
 * copy it. A code is 4 to 8 digits, written plainly, as "G-123456" or in two groups ("482 913"),
 * and it must sit near a word that says what it is: code, קוד, סיסמה, אימות, OTP, password...
 *
 * Amounts, dates, times, phone numbers and card endings are blanked out first, so
 * "חיוב 1500 ש"ח בכרטיס המסתיים ב-4821. קוד: 773421" gives 773421. When several numbers remain, the one
 * closest to the cue word wins, and a message with no cue word has no code. Letters-and-digits codes
 * ("K7P2QD") are not recognised.
 */
object CodeExtractor {

    /** The code as digits only ("G-123456" gives "123456", "482 913" gives "482913"), or null if the message has none. */
    fun extract(text: String): String? {
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

    private val MASKS = listOf(
        // Phone numbers: Israeli with or without +972, 1-700 / 1-800 lines, other countries, US style.
        Regex("(?<![\\d])(?:\\+972|00972|972)[ -]?\\(?0?\\)?[2-9]\\d?[ -]?\\d{3}[ -]?\\d{4}(?!\\d)"),
        Regex("(?<![\\d])0[2-9]\\d?[ -]?\\d{3}[ -]?\\d{4}(?!\\d)"),
        Regex("(?<![\\d])1[ -]?[78]00[ -]?\\d{3}[ -]?\\d{3,4}(?!\\d)"),
        Regex("\\+\\d{1,3}[ -]?\\(?\\d{1,4}\\)?(?:[ -]?\\d{2,4}){2,4}"),
        Regex("\\(?\\d{3}\\)?[ -]\\d{3}-\\d{4}(?!\\d)"),
        // Card and account endings: "המסתיים ב-4821", "ending in 4821", "כרטיס 4821", "****4821", "x4821".
        Regex("(?:שמסתיים|המסתיים|מסתיים|סיומת|ending|ends|last (?:4|four)(?: digits)?)\\s*(?:ב-?|in|with|:|-)*\\s*[*•x.…]*\\d{4}(?!\\d)"),
        Regex("(?:כרטיס|card|account|acct|חשבון)\\s*(?:ב-?|in|with|:|-|#|no\\.?|number|מספר)*\\s*[*•x.…]*\\d{4}(?!\\d)"),
        Regex("[*•…]+\\s*\\d{4}(?!\\d)"),
        Regex("(?<![a-z0-9])x{1,4}\\d{4}(?!\\d)"),
        // Dates and times with a month name: "15 בנובמבר 2026", "Nov 15, 2026", "15th of November".
        Regex("\\d{1,2}\\s*-?\\s*(?:ב|ל)?-?(?:$MONTHS_HE)(?:\\s+\\d{4})?"),
        Regex("(?:ב|ל)?(?:$MONTHS_HE)\\s+\\d{4}"),
        Regex("(?<![a-z])(?:$MONTHS_EN)[a-z]*\\.?\\s+\\d{1,2}(?:st|nd|rd|th)?(?:,?\\s+\\d{4})?"),
        Regex("\\d{1,2}(?:st|nd|rd|th)?\\s+(?:of\\s+)?(?:$MONTHS_EN)[a-z]*\\.?(?:,?\\s+\\d{4})?"),
        Regex("(?<![a-z])(?:$MONTHS_EN)[a-z]*\\.?\\s+\\d{4}"),
        // Amounts: a currency sign or word on either side, a word like "total" or "יתרה" before, a unit after.
        Regex("[₪$€£]\\s*\\d[\\d,.]*"),
        Regex("\\d[\\d,.]*\\s*(?:[₪$€£%]|(?:$CURRENCY_WORDS|$UNIT_WORDS)(?![\\p{L}]))"),
        Regex("(?<![a-z])(?:nis|ils|usd|eur)\\s*\\d[\\d,.]*"),
        Regex("(?:בסך|סכום|amount|total|balance|יתרה|price|מחיר|סה\"כ|charged|חויבת)(?:\\s+(?:of|is|הוא|של))?\\s*[:=-]?\\s*\\d[\\d,.]*"),
        // Numbers joined by . / : , - : 15.11.2026, 18:30, 1,250, 2026-11-15, 192.168.0.1.
        Regex("\\d+(?:[./:,]\\d+)+"),
        Regex("\\d+(?:-\\d+){2,}"),
    )

    private fun mask(lower: String): String {
        val out = StringBuilder(lower)
        for (re in MASKS) {
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
