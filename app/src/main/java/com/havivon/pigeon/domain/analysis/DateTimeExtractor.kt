package com.havivon.pigeon.domain.analysis

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.Locale

/** A date or time phrase found in a message, resolved to a moment, ready to become a reminder. */
data class DateTimeMatch(
    /** Where the phrase sits in the message. */
    val range: IntRange,
    /** The phrase as written. */
    val text: String,
    /** When it means. [LocalTime] is 09:00 when the phrase names only a day ([hasTime] is false). */
    val dateTime: LocalDateTime,
    /** A short label for the "create a reminder" button: "מחר 10:00", "Tomorrow 10:00", "15.11", "Tue 20:00". */
    val label: String,
    val hasTime: Boolean,
)

/**
 * Finds the dates and times a message mentions, in Hebrew and English, relative to [extract]'s `now`.
 *
 * Understood, with a day, a time, or both in either order:
 * - days: היום, מחר, מחרתיים, "ביום שלישי", "יום ג'", "בשבת", today, tomorrow, "on Tuesday", "next Friday";
 * - dates: "ב-15.11", "ב-15/11", "15/11/2026", "ב-15 בנובמבר", "Nov 15", "15th of November", "15/11";
 * - times: "ב-10:00", "בשעה 9", "ב-8 בערב", "at 10", "6:30 pm", "8pm";
 * - durations: "בעוד שעה", "בעוד 20 דקות", "בעוד 3 ימים", "in 2 hours", "in half an hour", "in a week".
 *
 * Only the future is returned. "היום ב-9:00" at 14:00 is not a match; a bare time ("ב-18:30") that has
 * passed today means tomorrow; a date without a year that has passed means next year; a weekday means
 * the next one. A bare hour with no am/pm is read as written: "at 8" is 08:00. Slash dates are day/month,
 * unless the second number cannot be a month ("11/15" is 15 November). Months of the Hebrew calendar
 * are not recognised.
 */
object DateTimeExtractor {

    /** The matches in [text], in order and not overlapping. An empty list if there are none. */
    fun extract(text: String, now: LocalDateTime): List<DateTimeMatch> {
        val folded = fold(text)
        val nowMinute = now.truncatedTo(ChronoUnit.MINUTES)

        val dayParts = withWeekdaysAbsorbed(overlapFree(findDays(folded) + findDurations(folded)), folded)
        val timeParts = ArrayList<TimePart>()
        for (t in findTimes(folded)) {
            if (dayParts.none { overlaps(it, t) } && timeParts.none { overlaps(it, t) }) timeParts += t
        }
        val all = (dayParts + timeParts).sortedBy { it.start }

        val used = HashSet<Part>()
        val out = ArrayList<DateTimeMatch>()
        fun pairable(day: Part, time: TimePart) =
            time !in used && folded.substring(minOf(day.end, time.end), maxOf(day.start, time.start)).isBlankOrCommas()

        for ((i, part) in all.withIndex()) {
            if (part in used) continue
            when (part) {
                is TimePart -> Unit
                is DurationPart -> {
                    used += part
                    out += build(text, part.start, part.end, nowMinute.plusMinutes(part.minutes), true, now)
                }
                is DayPart -> {
                    used += part
                    // The time that follows the day, or else the one that precedes it: "מחר ב-10:00", "at 6pm tomorrow".
                    val time = listOf(i + 1, i - 1).mapNotNull { all.getOrNull(it) as? TimePart }.firstOrNull { pairable(part, it) }
                    time?.let { used += it }
                    val start = minOf(part.start, time?.start ?: part.start)
                    val end = maxOf(part.end, time?.end ?: part.end)
                    val date = resolveDay(part.day, time?.time, now) ?: continue
                    out += build(text, start, end, LocalDateTime.of(date, time?.time ?: DEFAULT_TIME), time != null, now)
                }
            }
        }
        // A time with no day: the next time it is that time. Only when it is clearly a time, not "ב-5".
        for (t in timeParts) {
            if (t in used || !t.strong) continue
            if (YESTERDAY.containsMatchIn(folded.substring(maxOf(0, t.start - 12), t.start))) continue
            var at = LocalDateTime.of(now.toLocalDate(), t.time)
            if (at.isBefore(nowMinute)) at = at.plusDays(1)
            out += build(text, t.start, t.end, at, true, now)
        }
        return out.sortedBy { it.range.first }
    }

    private val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)

    /** "אתמול ב-10:00" is a time in the past, not "the next 10:00". */
    private val YESTERDAY = Regex("(?:אתמול|yesterday|last night)\\s*$")

    // --- parts found in the text ---

    private sealed class Part(val start: Int, val end: Int) // end is exclusive
    private class DayPart(start: Int, end: Int, val day: Day) : Part(start, end)
    private class DurationPart(start: Int, end: Int, val minutes: Long) : Part(start, end)
    private class TimePart(start: Int, end: Int, val time: LocalTime, val strong: Boolean) : Part(start, end)

    private sealed interface Day {
        data object Today : Day
        data class Offset(val days: Long) : Day
        data class Weekday(val dow: DayOfWeek, val forceNext: Boolean) : Day
        data class Absolute(val day: Int, val month: Int, val year: Int?) : Day
    }

    private fun overlaps(a: Part, b: Part) = a.start < b.end && b.start < a.end

    /** "Monday 3 Nov" and "ביום ב' ב-3.11" are one date: the weekday only names the day, the date decides. */
    private fun withWeekdaysAbsorbed(parts: List<Part>, s: String): List<Part> {
        val sorted = parts.sortedBy { it.start }
        val out = ArrayList<Part>()
        var i = 0
        while (i < sorted.size) {
            val p = sorted[i]
            val next = sorted.getOrNull(i + 1)
            if (p is DayPart && p.day is Day.Weekday && next is DayPart && next.day is Day.Absolute &&
                s.substring(p.end, next.start).isBlankOrCommas()
            ) {
                out += DayPart(p.start, next.end, next.day)
                i += 2
            } else {
                out += p
                i++
            }
        }
        return out
    }

    /** Keeps the earliest, then the longest, of any parts that overlap. */
    private fun overlapFree(parts: List<Part>): List<Part> {
        val kept = ArrayList<Part>()
        for (p in parts.sortedWith(compareBy<Part> { it.start }.thenByDescending { it.end - it.start })) {
            if (kept.none { overlaps(it, p) }) kept += p
        }
        return kept
    }

    private fun String.isBlankOrCommas() = all { it.isWhitespace() || it == ',' }

    /** Same length as the input, so positions carry over: lower case, one kind of dash and quote, no direction marks. */
    private fun fold(text: String): String = String(
        CharArray(text.length) {
            val c = text[it]
            when {
                c == '־' || c == '–' || c == '—' || c == '‑' -> '-'
                c == '’' || c == '‘' || c == '׳' -> '\''
                c == '”' || c == '“' || c == '״' -> '"'
                Character.getType(c) == Character.FORMAT.toInt() -> ' '
                else -> c.lowercaseChar()
            }
        },
    )

    // --- days ---

    private val HE_MONTHS = mapOf(
        "ינואר" to 1, "פברואר" to 2, "מרץ" to 3, "מרס" to 3, "אפריל" to 4, "מאי" to 5, "יוני" to 6,
        "יולי" to 7, "אוגוסט" to 8, "ספטמבר" to 9, "אוקטובר" to 10, "נובמבר" to 11, "דצמבר" to 12,
    )
    private val EN_MONTHS = mapOf(
        "january" to 1, "february" to 2, "march" to 3, "april" to 4, "may" to 5, "june" to 6, "july" to 7,
        "august" to 8, "september" to 9, "october" to 10, "november" to 11, "december" to 12,
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "jun" to 6, "jul" to 7, "aug" to 8,
        "sept" to 9, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )
    private val HE_WEEKDAYS = mapOf(
        "ראשון" to DayOfWeek.SUNDAY, "שני" to DayOfWeek.MONDAY, "שלישי" to DayOfWeek.TUESDAY, "רביעי" to DayOfWeek.WEDNESDAY,
        "חמישי" to DayOfWeek.THURSDAY, "שישי" to DayOfWeek.FRIDAY, "שבת" to DayOfWeek.SATURDAY,
        "א'" to DayOfWeek.SUNDAY, "ב'" to DayOfWeek.MONDAY, "ג'" to DayOfWeek.TUESDAY, "ד'" to DayOfWeek.WEDNESDAY,
        "ה'" to DayOfWeek.THURSDAY, "ו'" to DayOfWeek.FRIDAY, "ש'" to DayOfWeek.SATURDAY,
    )
    private val EN_WEEKDAYS = mapOf(
        "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY,
        "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY, "fri" to DayOfWeek.FRIDAY,
        "sat" to DayOfWeek.SATURDAY, "sun" to DayOfWeek.SUNDAY,
    )

    private fun alt(words: Collection<String>) = words.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }

    private const val NOT_AFTER = "(?<![\\p{L}\\d])"
    private const val NOT_BEFORE = "(?![\\p{L}])"

    private val HE_RELATIVE = Regex("$NOT_AFTER(?:ל)?(מחרתיים|מחר|היום)$NOT_BEFORE")
    private val EN_RELATIVE = Regex("$NOT_AFTER(the day after tomorrow|day after tomorrow|tomorrow|tmrw|today)$NOT_BEFORE")
    private val HE_WEEKDAY = Regex("$NOT_AFTER[בל]?(?:יום\\s+(${alt(HE_WEEKDAYS.keys)})|שבת)(?:\\s+(הבא|הקרוב))?$NOT_BEFORE")
    private val EN_WEEKDAY = Regex("$NOT_AFTER(?:(on|next|this|coming)\\s+)?(${alt(EN_WEEKDAYS.keys.filter { it.length >= 6 })})$NOT_BEFORE")
    private val EN_WEEKDAY_SHORT = Regex("$NOT_AFTER(on|next)\\s+(${alt(EN_WEEKDAYS.keys.filter { it.length < 6 })})\\.?$NOT_BEFORE")

    private const val NOT_A_DATE_UNIT = "(?!\\s*(?:%|₪|\\$|€|ש\"?ח|שקל|דולר|אחוז|אלף|מיליון|nis\\b|usd\\b|km|ק\"מ|מטר|kg|ml))"

    /** Numeric dates: "ב-15.11", "on 15/11", "15/11/2026", "15.11.26". */
    private val NUM_DATE = Regex(
        "(?<![\\d./:\\-])((?:(?:בתאריך|ב|ל)-?\\s*|ה-\\s*|(?:on|by|until|till|before)\\s+)?)(\\d{1,2})([./])(\\d{1,2})(?:\\3(\\d{2,4}))?(?![\\d])(?![./]\\d)$NOT_A_DATE_UNIT",
    )
    private val HE_MONTH_DATE = Regex(
        "(?<![\\p{L}\\d./:])(?:[בלה]-?)?(\\d{1,2})\\s*(?:[בל]-?)?(${alt(HE_MONTHS.keys)})$NOT_BEFORE(?:\\s+(\\d{4})(?!\\d))?",
    )
    private val EN_MONTH_DAY = Regex(
        "(?<![\\p{L}\\d])(?:(?:on|by|until|till|before)\\s+)?(${alt(EN_MONTHS.keys)})\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?(?![\\d:])(?:,?\\s+(\\d{4})(?!\\d))?",
    )
    private val EN_DAY_MONTH = Regex(
        "(?<![\\p{L}\\d./:])(?:(?:on|by|until|till|before)\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?(${alt(EN_MONTHS.keys)})$NOT_BEFORE\\.?(?:,?\\s+(\\d{4})(?!\\d))?",
    )

    /** Slash and dot pairs that are almost never dates. */
    private val NOT_DATES = setOf("24/7", "24/6", "1/2", "1/3", "2/3", "1/4", "3/4")

    private fun findDays(s: String): List<Part> {
        val out = ArrayList<Part>()
        for (m in HE_RELATIVE.findAll(s)) out += DayPart(m.range.first, m.range.last + 1, relative(m.groupValues[1]))
        for (m in EN_RELATIVE.findAll(s)) out += DayPart(m.range.first, m.range.last + 1, relative(m.groupValues[1]))
        for (m in HE_WEEKDAY.findAll(s)) {
            val dow = HE_WEEKDAYS[m.groupValues[1]] ?: DayOfWeek.SATURDAY
            out += DayPart(m.range.first, m.range.last + 1, Day.Weekday(dow, m.groupValues[2] == "הבא"))
        }
        for (m in EN_WEEKDAY.findAll(s)) {
            out += DayPart(m.range.first, m.range.last + 1, Day.Weekday(EN_WEEKDAYS.getValue(m.groupValues[2]), m.groupValues[1] == "next"))
        }
        for (m in EN_WEEKDAY_SHORT.findAll(s)) {
            out += DayPart(m.range.first, m.range.last + 1, Day.Weekday(EN_WEEKDAYS.getValue(m.groupValues[2]), m.groupValues[1] == "next"))
        }
        for (m in NUM_DATE.findAll(s)) numericDate(m)?.let { out += DayPart(m.range.first, m.range.last + 1, it) }
        for (m in HE_MONTH_DATE.findAll(s)) {
            absolute(m.groupValues[1].toInt(), HE_MONTHS.getValue(m.groupValues[2]), m.groupValues[3])?.let { out += DayPart(m.range.first, m.range.last + 1, it) }
        }
        for (m in EN_MONTH_DAY.findAll(s)) {
            absolute(m.groupValues[2].toInt(), EN_MONTHS.getValue(m.groupValues[1]), m.groupValues[3])?.let { out += DayPart(m.range.first, m.range.last + 1, it) }
        }
        for (m in EN_DAY_MONTH.findAll(s)) {
            absolute(m.groupValues[1].toInt(), EN_MONTHS.getValue(m.groupValues[2]), m.groupValues[3])?.let { out += DayPart(m.range.first, m.range.last + 1, it) }
        }
        return out
    }

    private fun relative(word: String): Day = when (word) {
        "היום", "today" -> Day.Today
        "מחר", "tomorrow", "tmrw" -> Day.Offset(1)
        else -> Day.Offset(2) // מחרתיים, day after tomorrow
    }

    private fun numericDate(m: MatchResult): Day? {
        val prefix = m.groupValues[1]
        val a = m.groupValues[2].toInt()
        val b = m.groupValues[4].toInt()
        val sep = m.groupValues[3]
        val yearText = m.groupValues[5]
        val bare = prefix.isBlank()
        if (yearText.isEmpty() && "$a/$b" in NOT_DATES && sep == "/") return null
        // "3.5" is a decimal, not 3 May: a dot date needs a word in front of it, or a year.
        if (sep == "." && bare && yearText.isEmpty()) return null
        val (day, month) = when {
            a in 1..31 && b in 1..12 -> a to b
            sep == "/" && a in 1..12 && b in 13..31 -> b to a // 11/15
            else -> return null
        }
        return absolute(day, month, yearText)
    }

    private fun absolute(day: Int, month: Int, yearText: String): Day.Absolute? {
        val year = when (yearText.length) {
            0 -> null
            2 -> 2000 + yearText.toInt()
            4 -> yearText.toInt().takeIf { it in 2000..2100 } ?: return null
            else -> return null
        }
        if (month !in 1..12 || day !in 1..java.time.Month.of(month).maxLength()) return null // "31.11" is no date, in any year
        if (year != null && !validDate(year, month, day)) return null
        return Day.Absolute(day, month, year)
    }

    private fun validDate(year: Int, month: Int, day: Int) = day <= java.time.YearMonth.of(year, month).lengthOfMonth()

    // --- durations ---

    private val HE_NUMBERS = mapOf(
        "שתי" to 2, "שני" to 2, "שלוש" to 3, "שלושה" to 3, "ארבע" to 4, "ארבעה" to 4, "חמש" to 5, "חמישה" to 5, "שש" to 6, "שישה" to 6,
        "שבע" to 7, "שבעה" to 7, "שמונה" to 8, "תשע" to 9, "תשעה" to 9, "עשר" to 10, "עשרה" to 10, "עשרים" to 20, "שלושים" to 30,
        "ארבעים" to 40, "חמישים" to 50,
    )
    private val EN_NUMBERS = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
        "ten" to 10, "fifteen" to 15, "twenty" to 20, "thirty" to 30, "forty" to 40, "forty-five" to 45, "sixty" to 60,
        "a" to 1, "an" to 1,
    )

    private val HE_DURATION = Regex(
        "$NOT_AFTER(?:ב)?עוד\\s+(?:(\\d{1,3}|${alt(HE_NUMBERS.keys)})\\s*(דקות|דקה|שעות|שעה|ימים|יום|שבועות|שבוע)" +
            "|(חצי\\s+שעה|רבע\\s+שעה|שעה\\s+וחצי|שעה\\s+ורבע|שעתיים|יומיים|שבועיים|שעה|דקה|יום|שבוע))$NOT_BEFORE",
    )
    private val EN_DURATION = Regex(
        "$NOT_AFTER(?:in|within)\\s+(?:(\\d{1,3}|${alt(EN_NUMBERS.keys)}|half an?)\\s*(minutes?|mins?|hours?|hrs?|days?|weeks?))$NOT_BEFORE",
    )

    private fun findDurations(s: String): List<Part> {
        val out = ArrayList<Part>()
        for (m in HE_DURATION.findAll(s)) {
            val fixed = m.groupValues[3]
            val found: Part? = if (fixed.isNotEmpty()) {
                val key = fixed.replace(Regex("\\s+"), " ")
                when (key) {
                    "חצי שעה" -> DurationPart(m.range.first, m.range.last + 1, 30)
                    "רבע שעה" -> DurationPart(m.range.first, m.range.last + 1, 15)
                    "שעה וחצי" -> DurationPart(m.range.first, m.range.last + 1, 90)
                    "שעה ורבע" -> DurationPart(m.range.first, m.range.last + 1, 75)
                    "שעתיים" -> DurationPart(m.range.first, m.range.last + 1, 120)
                    "שעה" -> DurationPart(m.range.first, m.range.last + 1, 60)
                    "דקה" -> DurationPart(m.range.first, m.range.last + 1, 1)
                    "יומיים" -> DayPart(m.range.first, m.range.last + 1, Day.Offset(2))
                    "יום" -> DayPart(m.range.first, m.range.last + 1, Day.Offset(1))
                    "שבועיים" -> DayPart(m.range.first, m.range.last + 1, Day.Offset(14))
                    else -> DayPart(m.range.first, m.range.last + 1, Day.Offset(7)) // שבוע
                }
            } else {
                val n = m.groupValues[1].toIntOrNull() ?: HE_NUMBERS.getValue(m.groupValues[1])
                duration(m.range, n, m.groupValues[2])
            }
            if (found != null) out += found
        }
        for (m in EN_DURATION.findAll(s)) {
            val amount = m.groupValues[1].replace(Regex("\\s+"), " ")
            val unit = m.groupValues[2]
            if (amount.startsWith("half")) {
                // "half an hour"; "half a day" and the like are not worth a reminder phrase.
                if (unit.startsWith("hour") || unit.startsWith("hr")) out += DurationPart(m.range.first, m.range.last + 1, 30)
                continue
            }
            val n = amount.toIntOrNull() ?: EN_NUMBERS.getValue(amount)
            duration(m.range, n, unit)?.let { out += it }
        }
        return out
    }

    private fun duration(range: IntRange, n: Int, unit: String): Part? {
        if (n <= 0) return null
        val start = range.first
        val end = range.last + 1
        val u = unit.lowercase()
        return when {
            u.startsWith("min") || u.startsWith("דק") -> DurationPart(start, end, n.toLong())
            u.startsWith("hour") || u.startsWith("hr") || u.startsWith("שע") -> DurationPart(start, end, n * 60L)
            u.startsWith("week") || u.startsWith("שבוע") -> DayPart(start, end, Day.Offset(n * 7L))
            u.startsWith("day") || u.startsWith("ימ") || u.startsWith("יום") -> DayPart(start, end, Day.Offset(n.toLong()))
            else -> null
        }
    }

    // --- times ---

    private const val HE_DAYPART = "(?:\\s+(בבוקר|בצהריים|בצהרים|אחה\"צ|אחר\\s+הצהריים|אחרי\\s+הצהריים|בערב|בלילה|לפנה\"צ|לפנות\\s+בוקר)$NOT_BEFORE)?"
    private const val AMPM = "(?:\\s*(am|pm|a\\.m\\.|p\\.m\\.)(?![\\p{L}]))?"
    private const val TIME_AFTER = "(?<![\\p{L}\\d:.])"

    private val T_COLON = Regex("$TIME_AFTER(?:(?:בשעה|לשעה|בסביבות|ב)-?\\s*|(?:at|@|around|by)\\s*)?(\\d{1,2}):(\\d{2})(?![\\d:])$AMPM$HE_DAYPART")
    private val T_AMPM = Regex("$TIME_AFTER(?:(?:at|@|around|by)\\s*)?(\\d{1,2})\\s*(am|pm|a\\.m\\.|p\\.m\\.)(?![\\p{L}])$HE_DAYPART")
    private val T_HE_HOUR = Regex("$TIME_AFTER(?:בשעה|לשעה|בסביבות)\\s*(\\d{1,2})(?![\\d:.]\\d)(?!\\d)$HE_DAYPART")
    private val T_EN_AT = Regex("$TIME_AFTER(?:at|@)\\s*(\\d{1,2})(?!\\d)(?![:./]\\d)(?!\\s*(?:min|hour|hr|sec|day|week|year|kg|km|%))")
    private val T_HE_WEAK = Regex("${TIME_AFTER}ב-?(\\d{1,2})(?!\\d)(?![:./]\\d)$HE_DAYPART")

    private fun findTimes(s: String): List<TimePart> {
        val out = ArrayList<TimePart>()
        fun add(m: MatchResult, hour: Int, minute: Int, ampm: String?, daypart: String?, strong: Boolean) {
            val h = when {
                ampm != null -> twelveHour(hour, ampm.startsWith("p"))
                daypart != null -> dayPartHour(hour, daypart)
                else -> hour
            } ?: return
            if (h !in 0..23 || minute !in 0..59) return
            out += TimePart(m.range.first, m.range.last + 1, LocalTime.of(h, minute), strong || daypart != null)
        }
        for (m in T_COLON.findAll(s)) {
            add(m, m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].ifEmpty { null }, m.groupValues[4].ifEmpty { null }, true)
        }
        for (m in T_AMPM.findAll(s)) {
            add(m, m.groupValues[1].toInt(), 0, m.groupValues[2], m.groupValues[3].ifEmpty { null }, true)
        }
        for (m in T_HE_HOUR.findAll(s)) {
            add(m, m.groupValues[1].toInt(), 0, null, m.groupValues[2].ifEmpty { null }, true)
        }
        for (m in T_EN_AT.findAll(s)) {
            add(m, m.groupValues[1].toInt(), 0, null, null, true)
        }
        for (m in T_HE_WEAK.findAll(s)) {
            add(m, m.groupValues[1].toInt(), 0, null, m.groupValues[2].ifEmpty { null }, false)
        }
        return out.sortedWith(compareBy<TimePart> { it.start }.thenByDescending { it.end - it.start }).fold(ArrayList()) { kept, t ->
            if (kept.none { overlaps(it, t) }) kept += t
            kept
        }
    }

    private fun twelveHour(hour: Int, pm: Boolean): Int? = when {
        hour !in 1..12 -> null
        pm -> if (hour == 12) 12 else hour + 12
        else -> if (hour == 12) 0 else hour
    }

    /** "ב-8 בערב" is 20:00, "ב-12 בלילה" is 00:00, "ב-2 בצהריים" is 14:00. */
    private fun dayPartHour(hour: Int, word: String): Int? {
        if (hour !in 0..23) return null
        return when {
            word.startsWith("בערב") || word.startsWith("אחה") || word.startsWith("אחר") || word.startsWith("אחרי") ->
                if (hour in 1..11) hour + 12 else hour
            word.startsWith("בצהר") -> if (hour in 1..5) hour + 12 else hour
            word.startsWith("בלילה") -> when (hour) {
                12 -> 0
                in 6..11 -> hour + 12
                else -> hour
            }
            else -> if (hour == 12) 0 else hour // בבוקר, לפנה"צ, לפנות בוקר
        }
    }

    // --- resolving ---

    /** The date a phrase means, or null if it is in the past. */
    private fun resolveDay(day: Day, time: LocalTime?, now: LocalDateTime): LocalDate? {
        val today = now.toLocalDate()
        val nowTime = now.toLocalTime().truncatedTo(ChronoUnit.MINUTES)
        return when (day) {
            // "today" alone is no reminder, and a time that has passed today is not one either.
            Day.Today -> if (time != null && !time.isBefore(nowTime)) today else null
            is Day.Offset -> today.plusDays(day.days)
            is Day.Weekday -> {
                var delta = (day.dow.value - today.dayOfWeek.value + 7) % 7
                if (delta == 0 && (day.forceNext || time == null || time.isBefore(nowTime))) delta = 7
                today.plusDays(delta.toLong())
            }
            is Day.Absolute -> {
                val date = if (day.year != null) {
                    LocalDate.of(day.year, day.month, day.day)
                } else {
                    val thisYear = nextValid(today.year, day.month, day.day)
                    if (thisYear.isBefore(today)) nextValid(today.year + 1, day.month, day.day) else thisYear
                }
                when {
                    date.isBefore(today) -> null
                    date == today && time != null && time.isBefore(nowTime) -> null
                    else -> date
                }
            }
        }
    }

    /**
     * [day] of [month] in [year], or in the next year where it exists (29 February: within a leap cycle).
     * [absolute] lets no day through that no year has, and the search is bounded all the same.
     */
    private fun nextValid(year: Int, month: Int, day: Int): LocalDate {
        val y = (year until year + LEAP_CYCLE).firstOrNull { validDate(it, month, day) } ?: year
        return LocalDate.of(y, month, minOf(day, java.time.YearMonth.of(y, month).lengthOfMonth()))
    }

    /** No day of the year is missing for longer than this many years in a row (29 February across 2100). */
    private const val LEAP_CYCLE = 9

    private fun build(text: String, start: Int, end: Int, at: LocalDateTime, hasTime: Boolean, now: LocalDateTime): DateTimeMatch {
        val phrase = text.substring(start, end)
        return DateTimeMatch(start until end, phrase, at, label(at, hasTime, Text.hasHebrew(phrase), now.toLocalDate()), hasTime)
    }

    // --- labels ---

    private val HE_SHORT_DAYS = mapOf(
        DayOfWeek.SUNDAY to "א׳", DayOfWeek.MONDAY to "ב׳", DayOfWeek.TUESDAY to "ג׳", DayOfWeek.WEDNESDAY to "ד׳",
        DayOfWeek.THURSDAY to "ה׳", DayOfWeek.FRIDAY to "ו׳", DayOfWeek.SATURDAY to "שבת",
    )
    private val EN_SHORT_DAYS = mapOf(
        DayOfWeek.SUNDAY to "Sun", DayOfWeek.MONDAY to "Mon", DayOfWeek.TUESDAY to "Tue", DayOfWeek.WEDNESDAY to "Wed",
        DayOfWeek.THURSDAY to "Thu", DayOfWeek.FRIDAY to "Fri", DayOfWeek.SATURDAY to "Sat",
    )
    private val EN_SHORT_MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /**
     * Today and tomorrow by name, the next days by weekday, anything later by date (with the year only
     * when it is not this one), then the time as HH:mm when the phrase had one.
     */
    private fun label(at: LocalDateTime, hasTime: Boolean, hebrew: Boolean, today: LocalDate): String {
        val date = at.toLocalDate()
        val days = ChronoUnit.DAYS.between(today, date)
        val day = when {
            days == 0L -> if (hebrew) "היום" else "Today"
            days == 1L -> if (hebrew) "מחר" else "Tomorrow"
            days == 2L && hebrew -> "מחרתיים"
            days in 2..6 -> if (hebrew) "יום ${HE_SHORT_DAYS.getValue(date.dayOfWeek)}" else EN_SHORT_DAYS.getValue(date.dayOfWeek)
            hebrew -> "${date.dayOfMonth}.${date.monthValue}" + if (date.year != today.year) ".${date.year}" else ""
            else -> "${date.dayOfMonth} ${EN_SHORT_MONTHS[date.monthValue - 1]}" + if (date.year != today.year) " ${date.year}" else ""
        }
        return if (hasTime) "$day %02d:%02d".format(Locale.ROOT, at.hour, at.minute) else day
    }
}
