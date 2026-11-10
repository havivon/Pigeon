package com.havivon.pigeon.ui.search

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * The search's date filter: whole days, from [first] to [last], both included, in the phone's time zone (a message at
 * 23:59 on the last day is in, one at 00:00 the day after is not). One day is [first] == [last]. Kept as dates, not
 * millis, so it means the same days whatever the time zone does meanwhile.
 */
data class DaySpan(val first: LocalDate, val last: LocalDate) {

    val isSingleDay: Boolean get() = first == last

    /** The first moment of [first] in [zone]. */
    fun startMillis(zone: ZoneId): Long = first.atStartOfDay(zone).toInstant().toEpochMilli()

    /** The first moment after [last] in [zone] (the start of the next day): the span is `[start, end)`. */
    fun endMillisExclusive(zone: ZoneId): Long = last.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun contains(millis: Long, zone: ZoneId): Boolean = millis >= startMillis(zone) && millis < endMillisExclusive(zone)

    companion object {
        /** The span between two days in either order. */
        fun of(a: LocalDate, b: LocalDate): DaySpan = if (b.isBefore(a)) DaySpan(b, a) else DaySpan(a, b)

        /**
         * What Material's date range picker chose: its millis are the days' midnights in UTC; no end is a single day.
         * Null when nothing was chosen.
         */
        fun fromPicker(startUtcMillis: Long?, endUtcMillis: Long?): DaySpan? {
            val start = startUtcMillis?.let(::utcDay) ?: return null
            return of(start, endUtcMillis?.let(::utcDay) ?: start)
        }

        private fun utcDay(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
    }
}

/** A day as Material's date picker holds it: its midnight in UTC. */
fun LocalDate.pickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** The quick choices above the date picker. "Last 7 days" is today and the six days before it. */
enum class DatePreset {
    Today, Yesterday, Last7Days, Last30Days, ThisMonth, LastMonth;

    fun span(today: LocalDate): DaySpan = when (this) {
        Today -> DaySpan(today, today)
        Yesterday -> today.minusDays(1).let { DaySpan(it, it) }
        Last7Days -> DaySpan(today.minusDays(6), today)
        Last30Days -> DaySpan(today.minusDays(29), today)
        ThisMonth -> DaySpan(today.withDayOfMonth(1), today)
        LastMonth -> today.minusMonths(1).let { DaySpan(it.withDayOfMonth(1), it.with(TemporalAdjusters.lastDayOfMonth())) }
    }
}

/**
 * The chip's label for [span]: "3.10" or "3.10–10.10" in Hebrew, "Oct 3" or "Oct 3 – Oct 10" otherwise; with the year
 * ("3.10.25", "Oct 3, 2025") when a day is not in [today]'s year.
 */
fun spanLabel(span: DaySpan, today: LocalDate, locale: Locale): String {
    val hebrew = locale.language == "iw" || locale.language == "he"
    val withYear = span.first.year != today.year || span.last.year != today.year
    val pattern = when {
        hebrew && withYear -> "d.M.yy"
        hebrew -> "d.M"
        withYear -> "MMM d, yyyy"
        else -> "MMM d"
    }
    val format = DateTimeFormatter.ofPattern(pattern, locale)
    if (span.isSingleDay) return format.format(span.first)
    return format.format(span.first) + (if (hebrew) "–" else " – ") + format.format(span.last)
}
