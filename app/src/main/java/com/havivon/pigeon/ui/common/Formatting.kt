package com.havivon.pigeon.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Dates and times as the screens show them, in the app's language. Pure java.time, so the labels
 * can be tested on the JVM; the words that are not dates ("Today", "Yesterday", "Tomorrow") come
 * from the string resources (see `AndroidLabels`).
 */
class DateLabels(
    private val locale: Locale,
    private val zone: ZoneId,
    is24Hour: Boolean,
    private val today: String,
    private val yesterday: String,
    private val tomorrow: String,
) {
    private val english = locale.language == "en"
    private val timeFormat = DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", locale)
    private val weekdayLong = DateTimeFormatter.ofPattern("EEEE", locale)
    private val weekdayShort = DateTimeFormatter.ofPattern("EEE", locale)
    private val shortDayMonth = DateTimeFormatter.ofPattern(if (english) "MMM d" else "d MMM", locale)
    private val numericDate = DateTimeFormatter.ofPattern(if (english) "M/d/yy" else "d.M.yy", locale)

    fun date(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /** The time of day: "14:05", or "2:05 PM" on a 12-hour phone. */
    fun time(millis: Long): String = timeFormat.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** The separator above a day's messages: Today, Yesterday, the weekday within the week, otherwise the date. */
    fun day(millis: Long, nowMillis: Long): String {
        val d = date(millis)
        val n = date(nowMillis)
        return when {
            d == n -> today
            d == n.minusDays(1) -> yesterday
            d.isBefore(n) && d.isAfter(n.minusDays(7)) -> weekdayLong.format(d)
            d.year == n.year -> longDate(d, withYear = false)
            else -> longDate(d, withYear = true)
        }
    }

    /**
     * "November 15" or, in Hebrew, "15 בנובמבר". The month's stand-alone name, with the Hebrew "ב"
     * added here: phones differ in whether their date format already has it.
     */
    private fun longDate(d: LocalDate, withYear: Boolean): String {
        val month = d.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
        val text = if (english) "$month ${d.dayOfMonth}" else "${d.dayOfMonth} ב$month"
        return if (!withYear) text else if (english) "$text, ${d.year}" else "$text ${d.year}"
    }

    /** The time in the conversation list: the time today, Yesterday, a short weekday within the week, otherwise a date. */
    fun listTime(millis: Long, nowMillis: Long): String {
        val d = date(millis)
        val n = date(nowMillis)
        return when {
            !d.isBefore(n) -> time(millis)
            d == n.minusDays(1) -> yesterday
            d.isAfter(n.minusDays(7)) -> weekdayShort.format(d)
            d.year == n.year -> shortDayMonth.format(d)
            else -> numericDate.format(d)
        }
    }

    /** When a reminder fires, for its button: "Tomorrow 10:00", "Today 18:30", "Tue 20:00", "15 Nov". */
    fun reminder(millis: Long, nowMillis: Long, withTime: Boolean): String {
        val d = date(millis)
        val n = date(nowMillis)
        val day = when {
            d == n -> today
            d == n.plusDays(1) -> tomorrow
            d.isAfter(n) && d.isBefore(n.plusDays(7)) -> weekdayShort.format(d)
            else -> shortDayMonth.format(d)
        }
        return if (withTime) "$day ${time(millis)}" else day
    }

    /** A short numeric date, "20.10.26" or "10/20/26", for the favourites widget. */
    fun shortDate(millis: Long): String = numericDate.format(date(millis))

    /** The stamp of a copied or shared message: the time, with the date when the messages span days. */
    fun stamp(millis: Long, withDate: Boolean): String =
        if (withDate) "${numericDate.format(date(millis))} ${time(millis)}" else time(millis)
}

/** The avatar colours, in the app's palette. A sender always gets the same one. */
private val AvatarPalette = longArrayOf(
    0xFFE0559B, 0xFF2F80ED, 0xFF1E9E6A, 0xFFF08A24, 0xFF7C5CE0, 0xFF14A3B8, 0xFF8D6E63, 0xFF5C6BC0,
)

/** The colour of the spam avatar and of spam everywhere. */
const val SpamArgb = 0xFFE53935

fun avatarColor(key: String): Long = AvatarPalette[Math.floorMod(key.hashCode(), AvatarPalette.size)]

/** Left-to-right isolate: keeps "+972 52-555-0123" in order inside Hebrew text. */
private const val LRI = '⁦'
private const val PDI = '⁩'

/** A phone number as display text that stays left to right in a right-to-left layout. Text with letters (a sender id, a host) is left as it is. */
fun ltr(text: String): String = if (text.any { it.isLetter() }) text else "$LRI$text$PDI"

/** The text without the direction marks [ltr] adds, for the clipboard. */
fun withoutIsolates(text: String): String = text.filter { it != LRI && it != PDI }

/**
 * What an avatar shows for [name]: its first letter (upper case) or emoji, whole even when it is
 * two UTF-16 chars. Null for a phone number, or nothing at all: the avatar then shows a person
 * icon. The direction marks of [ltr] are skipped, so a number never shows as an empty circle.
 */
fun avatarInitial(name: String): String? {
    var i = 0
    while (i < name.length) {
        val cp = name.codePointAt(i)
        val ch = String(Character.toChars(cp))
        when {
            Character.isWhitespace(cp) || ch[0] == LRI || ch[0] == PDI || Character.getType(cp) == Character.FORMAT.toInt() -> i += ch.length
            Character.isDigit(cp) || ch == "+" || ch == "(" -> return null
            else -> return ch.uppercase()
        }
    }
    return null
}
