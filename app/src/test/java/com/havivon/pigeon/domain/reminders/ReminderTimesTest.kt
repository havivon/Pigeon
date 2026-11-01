package com.havivon.pigeon.domain.reminders

import com.havivon.pigeon.domain.model.Reminder
import com.havivon.pigeon.fakes.Now
import com.havivon.pigeon.fakes.Utc
import com.havivon.pigeon.fakes.millis
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Now" in these tests is Sunday 1 November 2026, 12:00 (see `Now`). */
class ReminderTimesTest {

    private val jerusalem = ZoneId.of("Asia/Jerusalem")
    private val newYork = ZoneId.of("America/New_York")

    private fun local(zone: ZoneId, y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `in an hour is exactly an hour from now`() {
        assertEquals(millis(2026, 11, 1, 13, 0), ReminderTimes.at(QuickReminder.InOneHour, Now, Utc))
    }

    @Test
    fun `this evening is 20 00 today`() {
        assertEquals(millis(2026, 11, 1, 20, 0), ReminderTimes.at(QuickReminder.ThisEvening, Now, Utc))
        // Even in the small hours: that is still "this evening", later the same day
        assertEquals(millis(2026, 11, 1, 20, 0), ReminderTimes.at(QuickReminder.ThisEvening, millis(2026, 11, 1, 0, 30), Utc))
    }

    @Test
    fun `this evening after 20 00 rolls over to tomorrow evening`() {
        assertEquals(millis(2026, 11, 2, 20, 0), ReminderTimes.at(QuickReminder.ThisEvening, millis(2026, 11, 1, 21, 30), Utc))
        assertEquals(millis(2026, 11, 2, 20, 0), ReminderTimes.at(QuickReminder.ThisEvening, millis(2026, 11, 1, 23, 59), Utc))
    }

    @Test
    fun `this evening at exactly 20 00 is tomorrow, never a time that is already now`() {
        assertEquals(millis(2026, 11, 2, 20, 0), ReminderTimes.at(QuickReminder.ThisEvening, millis(2026, 11, 1, 20, 0), Utc))
        assertEquals(millis(2026, 11, 1, 20, 0), ReminderTimes.at(QuickReminder.ThisEvening, millis(2026, 11, 1, 19, 59), Utc))
    }

    @Test
    fun `tomorrow morning is 09 00 on the next calendar day`() {
        assertEquals(millis(2026, 11, 2, 9, 0), ReminderTimes.at(QuickReminder.TomorrowMorning, Now, Utc))
        // At 00:30 "tomorrow" is still the next calendar day, not this morning
        assertEquals(millis(2026, 11, 2, 9, 0), ReminderTimes.at(QuickReminder.TomorrowMorning, millis(2026, 11, 1, 0, 30), Utc))
        assertEquals(millis(2026, 12, 1, 9, 0), ReminderTimes.at(QuickReminder.TomorrowMorning, millis(2026, 11, 30, 23, 0), Utc))
    }

    @Test
    fun `the evening and morning are the phone's local time`() {
        // 12:00 UTC is 14:00 in Jerusalem in November
        assertEquals(local(jerusalem, 2026, 11, 1, 20, 0), ReminderTimes.at(QuickReminder.ThisEvening, Now, jerusalem))
        // 18:30 UTC is 20:30 there: already past 20:00 locally
        assertEquals(local(jerusalem, 2026, 11, 2, 20, 0), ReminderTimes.at(QuickReminder.ThisEvening, millis(2026, 11, 1, 18, 30), jerusalem))
        assertEquals(local(jerusalem, 2026, 11, 2, 9, 0), ReminderTimes.at(QuickReminder.TomorrowMorning, Now, jerusalem))
    }

    @Test
    fun `tomorrow morning stays 09 00 across the end of daylight saving time`() {
        // New York turns its clocks back at 2 AM on 1 November 2026: the night is 25 hours long
        val saturdayNight = local(newYork, 2026, 10, 31, 22, 0)
        val tomorrow = ReminderTimes.at(QuickReminder.TomorrowMorning, saturdayNight, newYork)
        assertEquals(local(newYork, 2026, 11, 1, 9, 0), tomorrow)
        // 22:00 to 09:00 is 11 hours on the wall clock, 12 on the real one, because of the repeated hour
        assertEquals(12L, (tomorrow - saturdayNight) / ReminderTimes.SNOOZE_MILLIS)
    }

    @Test
    fun `a picked date and time is read in the phone's zone`() {
        // The date picker reports the day as midnight UTC, whatever the zone
        val day = millis(2026, 11, 5, 0, 0)
        assertEquals(local(jerusalem, 2026, 11, 5, 14, 30), ReminderTimes.picked(day, 14, 30, jerusalem))
        assertEquals(local(newYork, 2026, 11, 5, 14, 30), ReminderTimes.picked(day, 14, 30, newYork))
        assertEquals(millis(2026, 11, 5, 8, 5), ReminderTimes.picked(day, 8, 5, Utc))
    }

    @Test
    fun `the first day the date picker offers is today in the phone's zone`() {
        // 23:30 UTC on the 31st is already the 1st in Jerusalem
        val late = millis(2026, 10, 31, 23, 30)
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), ReminderTimes.todayAsPickerMillis(late, jerusalem))
        assertEquals(LocalDateTime.of(2026, 10, 31, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), ReminderTimes.todayAsPickerMillis(late, Utc))
    }

    @Test
    fun `snoozing is an hour from the tap`() {
        assertEquals(Now + 3_600_000L, ReminderTimes.snoozedUntil(Now))
    }
}

class ReminderPlanTest {

    private fun reminder(id: Long, at: Long, done: Boolean = false) = Reminder(id, 1, null, "text", at, done)

    @Test
    fun `after a restart the future ones are scheduled, soonest first`() {
        val plan = ReminderPlan.afterRestart(listOf(reminder(1, Now + 5_000), reminder(2, Now + 1_000)), Now)
        assertEquals(listOf(2L, 1L), plan.schedule.map { it.id })
        assertTrue(plan.fireNow.isEmpty())
    }

    @Test
    fun `the ones missed while the phone was off are shown now, oldest first`() {
        val plan = ReminderPlan.afterRestart(listOf(reminder(1, Now - 1_000), reminder(2, Now - 9_000), reminder(3, Now + 1)), Now)
        assertEquals(listOf(2L, 1L), plan.fireNow.map { it.id })
        assertEquals(listOf(3L), plan.schedule.map { it.id })
    }

    @Test
    fun `a reminder due exactly now is shown, not scheduled`() {
        val plan = ReminderPlan.afterRestart(listOf(reminder(1, Now)), Now)
        assertEquals(listOf(1L), plan.fireNow.map { it.id })
        assertTrue(plan.schedule.isEmpty())
    }

    @Test
    fun `done reminders are never scheduled or shown again`() {
        val plan = ReminderPlan.afterRestart(listOf(reminder(1, Now + 1_000, done = true), reminder(2, Now - 1_000, done = true)), Now)
        assertTrue(plan.schedule.isEmpty())
        assertTrue(plan.fireNow.isEmpty())
    }

    @Test
    fun `nothing pending means nothing to do`() {
        val plan = ReminderPlan.afterRestart(emptyList(), Now)
        assertTrue(plan.schedule.isEmpty() && plan.fireNow.isEmpty())
    }

    @Test
    fun `exact alarms need the permission, otherwise the alarm is inexact`() {
        assertEquals(ReminderPlan.Precision.Exact, ReminderPlan.precision(canScheduleExact = true))
        assertEquals(ReminderPlan.Precision.Inexact, ReminderPlan.precision(canScheduleExact = false))
    }

    @Test
    fun `the notification shows the sender and the message`() {
        val t = reminderTexts("Dinner at 20:00", "Danny", locked = false, lockedTitle = "Locked", generic = "Reminder")
        assertEquals(ReminderTexts("Danny", "Dinner at 20:00"), t)
    }

    @Test
    fun `a locked conversation shows nothing of itself`() {
        val t = reminderTexts("Secret plans", "Danny", locked = true, lockedTitle = "Locked conversation", generic = "Reminder")
        assertEquals(ReminderTexts("Locked conversation", "Reminder"), t)
        assertTrue("Secret" !in t.text && "Danny" !in t.title)
    }

    @Test
    fun `a missing sender or text falls back to the generic word`() {
        assertEquals(ReminderTexts("Reminder", "Reminder"), reminderTexts("  ", null, locked = false, lockedTitle = "Locked", generic = "Reminder"))
        assertEquals(ReminderTexts("Reminder", "Call back"), reminderTexts("Call back", " ", locked = false, lockedTitle = "Locked", generic = "Reminder"))
    }
}
