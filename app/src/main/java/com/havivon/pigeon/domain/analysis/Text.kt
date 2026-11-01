package com.havivon.pigeon.domain.analysis

/**
 * Small text helpers shared by the analysers. Pure Kotlin on purpose: the whole `analysis` package
 * has no Android imports, so every class in it runs as a plain JVM unit test.
 */
internal object Text {

    /** The one-letter Hebrew prefixes that glue onto a word: ה ו ב ל מ ש כ (the, and, in, to, from, that, as). */
    private const val HEBREW_PREFIXES = "הובלמשכ"

    /** Longest run of prefix letters in front of a word: "וכשהמשלוח" is ו + כ + ש + ה, but four covers the longest. */
    private const val MAX_PREFIX = 4

    /** Shortest keyword that may take a Hebrew prefix. Shorter ones would match inside unrelated words. */
    private const val MIN_PREFIXED_LENGTH = 3

    /** Lower-cases, drops niqqud and direction marks, and unifies the quote marks Hebrew keyboards produce. */
    fun normalize(text: String): String = buildString(text.length) {
        for (c in text) {
            when {
                c == '־' -> append(' ') // maqaf, the Hebrew hyphen
                c in '֑'..'ׇ' -> Unit // niqqud and cantillation marks
                Character.getType(c) == Character.FORMAT.toInt() -> Unit // bidi marks, zero-width characters
                c == '’' || c == '‘' || c == '׳' -> append('\'')
                c == '”' || c == '“' || c == '״' -> append('"')
                else -> append(c.lowercaseChar())
            }
        }
    }

    /** The words of [text]: runs of letters and digits, with an apostrophe or quote kept when it sits inside a word. */
    fun words(text: String): List<String> {
        val s = normalize(text)
        val out = ArrayList<String>()
        val word = StringBuilder()
        for (i in s.indices) {
            val c = s[i]
            val inner = (c == '\'' || c == '"') && word.isNotEmpty() && i + 1 < s.length && s[i + 1].isLetterOrDigit()
            if (c.isLetterOrDigit() || inner) {
                word.append(c)
            } else if (word.isNotEmpty()) {
                out += word.toString()
                word.setLength(0)
            }
        }
        if (word.isNotEmpty()) out += word.toString()
        return out
    }

    /**
     * Whether [phrase] appears in [words] as whole consecutive words. A Hebrew first word also matches
     * with ה/ו/ב/ל/מ/ש/כ glued in front, so "משלוח" matches "המשלוח" and "וכשהמשלוח" but not "משלוחים".
     */
    fun containsPhrase(words: List<String>, phrase: String): Boolean = containsWords(words, words(phrase))

    /** [containsPhrase] with the phrase already split by [words]: for keyword lists checked against many messages. */
    fun containsWords(words: List<String>, target: List<String>): Boolean {
        if (target.isEmpty() || target.size > words.size) return false
        return (0..words.size - target.size).any { matchesAt(words, it, target) }
    }

    /** Whether [target] (split by [words]) starts at [start] in [words], its Hebrew first word with a prefix allowed. */
    fun matchesAt(words: List<String>, start: Int, target: List<String>): Boolean {
        if (target.isEmpty() || start < 0 || start + target.size > words.size) return false
        if (!sameWord(words[start], target[0], prefixAllowed = true)) return false
        return (1 until target.size).all { words[start + it] == target[it] }
    }

    private fun sameWord(word: String, keyword: String, prefixAllowed: Boolean): Boolean {
        if (word == keyword) return true
        if (!prefixAllowed || !isHebrew(keyword[0]) || keyword.length < MIN_PREFIXED_LENGTH) return false
        val extra = word.length - keyword.length
        return extra in 1..MAX_PREFIX && word.endsWith(keyword) && word.take(extra).all { it in HEBREW_PREFIXES }
    }

    fun isHebrew(c: Char): Boolean = c in 'א'..'ת'

    fun hasHebrew(text: String): Boolean = text.any { isHebrew(it) }

    /**
     * A comparable key for a phone number: digits only, Israeli +972 / 00972 written as a leading 0,
     * other countries as +digits. Null when [address] is not a phone number: it has a letter, or fewer
     * than 7 digits (a short service code such as 37133).
     */
    fun phoneKey(address: String): String? {
        val trimmed = address.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isLetter() }) return null
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length !in 7..15) return null
        val plus = trimmed.startsWith("+")
        return when {
            digits.startsWith("00972") -> "0" + digits.drop(5).removePrefix("0")
            digits.startsWith("00") -> "+" + digits.drop(2)
            (plus || digits.length >= 11) && digits.startsWith("972") -> "0" + digits.drop(3).removePrefix("0")
            plus -> "+$digits"
            else -> digits
        }
    }

    /** The key two senders are compared by: the phone key, or the lower-cased text for a name or an alphanumeric sender. */
    fun senderKey(address: String): String = phoneKey(address) ?: normalize(address).trim()

    /** An alphanumeric sender id such as "Leumi" or "ISRAELPOST": it has a letter and is not a phone number. */
    fun isAlphanumericSender(address: String): Boolean = address.any { it.isLetter() } && phoneKey(address) == null
}
