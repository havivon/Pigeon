package com.havivon.pigeon.ui.search

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The date filter's pure logic: the days a range covers, the quick choices and the chip's label. */
class SearchDatesTest {

    private val utc = ZoneOffset.UTC
    private val jerusalem = ZoneId.of("Asia/Jerusalem")
    private val iw = Locale("iw")

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d)
    private fun span(y: Int, m: Int, a: Int, b: Int = a) = DaySpan(day(y, m, a), day(y, m, b))
    private fun at(date: LocalDate, hour: Int, minute: Int, zone: ZoneId) = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    // The days a range covers

    @Test
    fun `a range includes its first and last day whole and nothing around them`() {
        val s = span(2026, 10, 3, 10)
        assertTrue(s.contains(at(day(2026, 10, 3), 0, 0, utc), utc))
        assertTrue(s.contains(at(day(2026, 10, 10), 23, 59, utc), utc))
        assertFalse(s.contains(at(day(2026, 10, 2), 23, 59, utc), utc))
        assertFalse(s.contains(at(day(2026, 10, 11), 0, 0, utc), utc))
    }

    @Test
    fun `one day is a range of that day`() {
        val s = span(2026, 10, 5)
        assertTrue(s.isSingleDay)
        assertTrue(s.contains(at(day(2026, 10, 5), 12, 0, utc), utc))
        assertFalse(s.contains(at(day(2026, 10, 4), 23, 59, utc), utc))
        assertFalse(s.contains(at(day(2026, 10, 6), 0, 0, utc), utc))
        assertEquals(24 * 60 * 60 * 1000L, s.endMillisExclusive(utc) - s.startMillis(utc))
    }

    @Test
    fun `the days are read in the time zone of the phone`() {
        val s = span(2026, 10, 5)
        // 23:30 on the 4th in Jerusalem (UTC+3 in October) is the 4th: out; 01:00 on the 5th there is still the 4th in UTC
        val lateOnThe4th = at(day(2026, 10, 4), 23, 30, jerusalem)
        val earlyOnThe5th = at(day(2026, 10, 5), 1, 0, jerusalem)
        assertFalse(s.contains(lateOnThe4th, jerusalem))
        assertTrue(s.contains(earlyOnThe5th, jerusalem))
        assertFalse("the same moment is the 4th in UTC", s.contains(earlyOnThe5th, ZoneOffset.UTC))
    }

    @Test
    fun `a day with a clock change still ends at the next midnight`() {
        // Israel's clocks go back on 25 October 2026: that day has 25 hours
        val s = span(2026, 10, 25)
        assertEquals(25 * 60 * 60 * 1000L, s.endMillisExclusive(jerusalem) - s.startMillis(jerusalem))
        assertTrue(s.contains(at(day(2026, 10, 25), 23, 59, jerusalem), jerusalem))
        assertFalse(s.contains(at(day(2026, 10, 26), 0, 0, jerusalem), jerusalem))
    }

    @Test
    fun `two days in either order make the same range`() {
        assertEquals(span(2026, 10, 3, 10), DaySpan.of(day(2026, 10, 10), day(2026, 10, 3)))
        assertEquals(span(2026, 10, 3, 10), DaySpan.of(day(2026, 10, 3), day(2026, 10, 10)))
    }

    @Test
    fun `the days of the picker are UTC midnights, whatever the zone of the phone is`() {
        val start = day(2026, 10, 3).pickerMillis()
        val end = day(2026, 10, 10).pickerMillis()
        assertEquals(span(2026, 10, 3, 10), DaySpan.fromPicker(start, end))
        assertEquals("no end is that one day", span(2026, 10, 3), DaySpan.fromPicker(start, null))
        assertNull("nothing chosen", DaySpan.fromPicker(null, null))
        assertNull(DaySpan.fromPicker(null, end))
        assertEquals("an end before the start is swapped", span(2026, 10, 3, 10), DaySpan.fromPicker(end, start))
    }

    // The quick choices

    @Test
    fun `today and yesterday are one day each`() {
        val today = day(2026, 11, 10)
        assertEquals(DaySpan(today, today), DatePreset.Today.span(today))
        assertEquals(DaySpan(day(2026, 11, 9), day(2026, 11, 9)), DatePreset.Yesterday.span(today))
    }

    @Test
    fun `the last 7 and 30 days count today as one of them`() {
        val today = day(2026, 11, 10)
        assertEquals(DaySpan(day(2026, 11, 4), today), DatePreset.Last7Days.span(today))
        assertEquals(DaySpan(day(2026, 10, 12), today), DatePreset.Last30Days.span(today))
        assertEquals(7L, ChronoUnit.DAYS.between(DatePreset.Last7Days.span(today).first, today) + 1)
        assertEquals(30L, ChronoUnit.DAYS.between(DatePreset.Last30Days.span(today).first, today) + 1)
    }

    @Test
    fun `this month runs from the 1st to today and last month is the whole month before`() {
        assertEquals(DaySpan(day(2026, 11, 1), day(2026, 11, 10)), DatePreset.ThisMonth.span(day(2026, 11, 10)))
        assertEquals(DaySpan(day(2026, 10, 1), day(2026, 10, 31)), DatePreset.LastMonth.span(day(2026, 11, 10)))
    }

    @Test
    fun `month edges, a short month and the year boundary`() {
        // On the 1st, this month is that single day
        assertEquals(DaySpan(day(2026, 11, 1), day(2026, 11, 1)), DatePreset.ThisMonth.span(day(2026, 11, 1)))
        // Last month from the 31st of a long month into a short one is still the whole of it
        assertEquals(DaySpan(day(2026, 2, 1), day(2026, 2, 28)), DatePreset.LastMonth.span(day(2026, 3, 31)))
        assertEquals(DaySpan(day(2024, 2, 1), day(2024, 2, 29)), DatePreset.LastMonth.span(day(2024, 3, 31)))
        // January looks back into December of the year before
        assertEquals(DaySpan(day(2025, 12, 1), day(2025, 12, 31)), DatePreset.LastMonth.span(day(2026, 1, 15)))
        // Early January: the last 7 days and yesterday cross the new year
        assertEquals(DaySpan(day(2025, 12, 30), day(2026, 1, 5)), DatePreset.Last7Days.span(day(2026, 1, 5)))
        assertEquals(DaySpan(day(2025, 12, 31), day(2025, 12, 31)), DatePreset.Yesterday.span(day(2026, 1, 1)))
    }

    // The label of the chip

    @Test
    fun `the label in Hebrew is day dot month, a range with an en dash`() {
        val today = day(2026, 11, 10)
        assertEquals("3.10–10.10", spanLabel(span(2026, 10, 3, 10), today, iw))
        assertEquals("5.10", spanLabel(span(2026, 10, 5), today, iw))
    }

    @Test
    fun `the label in English is month and day`() {
        val today = day(2026, 11, 10)
        assertEquals("Oct 3 – Oct 10", spanLabel(span(2026, 10, 3, 10), today, Locale.ENGLISH))
        assertEquals("Oct 5", spanLabel(span(2026, 10, 5), today, Locale.ENGLISH))
    }

    @Test
    fun `the year shows only when a day is not in this year`() {
        val today = day(2026, 11, 10)
        assertEquals("3.10.25", spanLabel(span(2025, 10, 3), today, iw))
        assertEquals("30.12.25–5.1.26", spanLabel(DaySpan(day(2025, 12, 30), day(2026, 1, 5)), today, iw))
        assertEquals("Oct 3, 2025", spanLabel(span(2025, 10, 3), today, Locale.ENGLISH))
        assertEquals("Dec 30, 2025 – Jan 5, 2026", spanLabel(DaySpan(day(2025, 12, 30), day(2026, 1, 5)), today, Locale.ENGLISH))
    }
}
