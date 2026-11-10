package com.havivon.pigeon.ui.common

import com.havivon.pigeon.fakes.Now
import com.havivon.pigeon.fakes.englishLabels
import com.havivon.pigeon.fakes.hebrewLabels
import com.havivon.pigeon.fakes.millis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattingTest {

    private val en = englishLabels()
    private val he = hebrewLabels()

    @Test
    fun `day separators say today, yesterday, the weekday, then the date`() {
        assertEquals("Today", en.day(millis(2026, 11, 1, 8, 0), Now))
        assertEquals("Yesterday", en.day(millis(2026, 10, 31, 23, 59), Now))
        assertEquals("Tuesday", en.day(millis(2026, 10, 27, 9, 0), Now))
        assertEquals("October 25", en.day(millis(2026, 10, 25, 9, 0), Now))
        assertEquals("December 31, 2025", en.day(millis(2025, 12, 31, 9, 0), Now))
    }

    @Test
    fun `day separators in Hebrew`() {
        assertEquals("היום", he.day(millis(2026, 11, 1, 8, 0), Now))
        assertEquals("אתמול", he.day(millis(2026, 10, 31, 9, 0), Now))
        assertEquals("יום שלישי", he.day(millis(2026, 10, 27, 9, 0), Now))
        assertEquals("25 באוקטובר", he.day(millis(2026, 10, 25, 9, 0), Now))
    }

    @Test
    fun `the list shows the time today and shorter labels before`() {
        assertEquals("09:05", en.listTime(millis(2026, 11, 1, 9, 5), Now))
        assertEquals("Yesterday", en.listTime(millis(2026, 10, 31, 9, 5), Now))
        assertEquals("Thu", en.listTime(millis(2026, 10, 29, 9, 5), Now))
        assertEquals("Oct 2", en.listTime(millis(2026, 10, 2, 9, 5), Now))
        assertEquals("3/4/25", en.listTime(millis(2025, 3, 4, 9, 5), Now))
    }

    private val en12 = com.havivon.pigeon.ui.common.DateLabels(java.util.Locale.ENGLISH, com.havivon.pigeon.fakes.Utc, false, "Today", "Yesterday", "Tomorrow")
    private val he12 = com.havivon.pigeon.ui.common.DateLabels(java.util.Locale("iw"), com.havivon.pigeon.fakes.Utc, false, "היום", "אתמול", "מחר")

    @Test
    fun `a bubble footer says today with the time`() {
        assertEquals("Today 13:14", en.bubble(millis(2026, 11, 1, 13, 14), Now))
        assertEquals("היום 13:14", he.bubble(millis(2026, 11, 1, 13, 14), Now))
        assertEquals("Today 1:14 PM", en12.bubble(millis(2026, 11, 1, 13, 14), Now))
        assertEquals("Today 9:05 AM", en12.bubble(millis(2026, 11, 1, 9, 5), Now))
    }

    @Test
    fun `a bubble footer within the last week names the weekday`() {
        assertEquals("Sat 13:14", en.bubble(millis(2026, 10, 31, 13, 14), Now))
        assertEquals("Tue 13:14", en.bubble(millis(2026, 10, 27, 13, 14), Now))
        assertEquals("Mon 13:14", en.bubble(millis(2026, 10, 26, 13, 14), Now))
        assertEquals("Tue 1:14 PM", en12.bubble(millis(2026, 10, 27, 13, 14), Now))
        assertEquals("יום ג׳ 13:14", he.bubble(millis(2026, 10, 27, 13, 14), Now))
    }

    @Test
    fun `a bubble footer a week or more back has the date, with the year only when it is not this year`() {
        assertEquals("Oct 25 13:14", en.bubble(millis(2026, 10, 25, 13, 14), Now))
        assertEquals("Oct 3 13:14", en.bubble(millis(2026, 10, 3, 13, 14), Now))
        assertEquals("3.10 13:14", he.bubble(millis(2026, 10, 3, 13, 14), Now))
        assertEquals("Oct 3, 2025 13:14", en.bubble(millis(2025, 10, 3, 13, 14), Now))
        assertEquals("3.10.2025 13:14", he.bubble(millis(2025, 10, 3, 13, 14), Now))
        assertEquals("Oct 3, 2025 1:14 PM", en12.bubble(millis(2025, 10, 3, 13, 14), Now))
        assertEquals("3.10.2025 1:14 אחה״צ", he12.bubble(millis(2025, 10, 3, 13, 14), Now))
    }

    @Test
    fun `a bubble footer from the future is today's`() {
        assertEquals("Today 13:14", en.bubble(millis(2026, 11, 2, 13, 14), Now))
    }

    @Test
    fun `a twelve-hour phone gets am and pm`() {
        val labels = com.havivon.pigeon.ui.common.DateLabels(java.util.Locale.ENGLISH, com.havivon.pigeon.fakes.Utc, false, "Today", "Yesterday", "Tomorrow")
        assertEquals("2:05 PM", labels.time(millis(2026, 11, 1, 14, 5)))
    }

    @Test
    fun `reminder labels name the day and the time`() {
        assertEquals("Tomorrow 10:00", en.reminder(millis(2026, 11, 2, 10, 0), Now, withTime = true))
        assertEquals("Today 18:30", en.reminder(millis(2026, 11, 1, 18, 30), Now, withTime = true))
        assertEquals("Wed", en.reminder(millis(2026, 11, 4, 9, 0), Now, withTime = false))
        assertEquals("Nov 15", en.reminder(millis(2026, 11, 15, 9, 0), Now, withTime = false))
    }

    @Test
    fun `numbers are kept left to right, Hebrew names are not wrapped`() {
        val wrapped = ltr("+972 52-555-0123")
        assertTrue(wrapped.first() == '⁦' && wrapped.last() == '⁩')
        assertEquals("+972 52-555-0123", withoutIsolates(wrapped))
        assertEquals("דני", ltr("דני"))
        assertEquals("Leumi", ltr("Leumi"))
    }

    @Test
    fun `a sender always gets the same avatar colour`() {
        assertEquals(avatarColor("0525550100"), avatarColor("0525550100"))
        assertFalse(avatarColor("0525550100") == SpamArgb)
    }

    @Test
    fun `an avatar shows the first letter or emoji, and a person for a bare number`() {
        assertEquals("D", avatarInitial("danny"))
        assertEquals("ד", avatarInitial("  דני"))
        assertEquals("😀", avatarInitial("😀 Mom"))
        // A number is shown left to right with direction marks around it: never an empty circle
        assertEquals(null, avatarInitial(ltr("+972 52-555-0123")))
        assertEquals(null, avatarInitial("0525550123"))
        assertEquals(null, avatarInitial(""))
        assertEquals("L", avatarInitial(ltr("Leumi")))
    }
}
