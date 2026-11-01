package com.havivon.pigeon.domain.reminders

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** The quick choices offered when the user asks to be reminded about a message. */
enum class QuickReminder { InOneHour, ThisEvening, TomorrowMorning }

/**
 * When a reminder fires, for each way of choosing it. Pure functions of the current time and zone,
 * so a test can fix the clock. Wall-clock times (evening, morning) are built from the local date,
 * which keeps them right across a change to or from daylight saving time.
 */
object ReminderTimes {

    const val EVENING_HOUR = 20
    const val MORNING_HOUR = 9

    /** "Snooze 1 hour" on the notification. */
    const val SNOOZE_MILLIS = 60 * 60 * 1000L

    /**
     * "In 1 hour" is an hour from now; "this evening" is 20:00 today, or tomorrow's 20:00 when it
     * is 20:00 or later already (a time in the past would fire at once); "tomorrow" is 09:00 on the
     * next calendar day.
     */
    fun at(choice: QuickReminder, nowMillis: Long, zone: ZoneId): Long {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return when (choice) {
            QuickReminder.InOneHour -> nowMillis + SNOOZE_MILLIS
            QuickReminder.ThisEvening -> {
                val tonight = atLocal(today, EVENING_HOUR, 0, zone)
                if (tonight > nowMillis) tonight else atLocal(today.plusDays(1), EVENING_HOUR, 0, zone)
            }
            QuickReminder.TomorrowMorning -> atLocal(today.plusDays(1), MORNING_HOUR, 0, zone)
        }
    }

    /** Snoozing again: an hour from the moment the button was tapped. */
    fun snoozedUntil(nowMillis: Long): Long = nowMillis + SNOOZE_MILLIS

    /**
     * The moment picked in the date and time pickers. The date picker reports the chosen day as
     * midnight UTC of that day, whatever the time zone; the time is the phone's local time.
     */
    fun picked(datePickerUtcMillis: Long, hour: Int, minute: Int, zone: ZoneId): Long {
        val day = Instant.ofEpochMilli(datePickerUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        return atLocal(day, hour, minute, zone)
    }

    /** The first day the date picker may offer: today, as the date picker's midnight-UTC millis. */
    fun todayAsPickerMillis(nowMillis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun atLocal(day: LocalDate, hour: Int, minute: Int, zone: ZoneId): Long =
        day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
}
