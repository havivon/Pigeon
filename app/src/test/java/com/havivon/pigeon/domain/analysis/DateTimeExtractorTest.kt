package com.havivon.pigeon.domain.analysis

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Now" is Sunday 1 November 2026, 14:00. */
class DateTimeExtractorTest {

    private val now = LocalDateTime.of(2026, 11, 1, 14, 0)

    private fun at(month: Int, day: Int, hour: Int = 9, minute: Int = 0, year: Int = 2026) = LocalDateTime.of(year, month, day, hour, minute)

    private fun single(text: String): DateTimeMatch {
        val found = DateTimeExtractor.extract(text, now)
        assertEquals("$text -> $found", 1, found.size)
        return found.single()
    }

    private fun check(text: String, expected: LocalDateTime, label: String, hasTime: Boolean = true) {
        val m = single(text)
        assertEquals(text, expected, m.dateTime)
        assertEquals(text, label, m.label)
        assertEquals(text, hasTime, m.hasTime)
    }

    @Test
    fun hebrewDayAndTime() {
        check("נתראה מחר ב-10:00", at(11, 2, 10), "מחר 10:00")
        check("תזכיר לי היום ב-18:30", at(11, 1, 18, 30), "היום 18:30")
        check("מחר בשעה 8 בבוקר", at(11, 2, 8), "מחר 08:00")
        check("מחר ב-10", at(11, 2, 10), "מחר 10:00")
        check("ב-10:00 מחר", at(11, 2, 10), "מחר 10:00")
        check("ביום שלישי ב-20:00", at(11, 3, 20), "מחרתיים 20:00")
        check("ביום חמישי ב-18:00", at(11, 5, 18), "יום ה׳ 18:00")
        check("ביום ג' ב-20:00", at(11, 3, 20), "מחרתיים 20:00")
        check("יום רביעי הבא בשעה 9:30", at(11, 4, 9, 30), "יום ד׳ 09:30")
        check("היום ב-23:59", at(11, 1, 23, 59), "היום 23:59")
        check("מחרתיים ב-7:15", at(11, 3, 7, 15), "מחרתיים 07:15")
        check("היום ב-14:00", at(11, 1, 14, 0), "היום 14:00")
    }

    @Test
    fun hebrewDayPartsOfTheDay() {
        check("מחר ב-8 בערב", at(11, 2, 20), "מחר 20:00")
        check("מחר בשעה 9 בבוקר", at(11, 2, 9), "מחר 09:00")
        check("מחר ב-2 בצהריים", at(11, 2, 14), "מחר 14:00")
        check("מחר ב-12 בלילה", at(11, 2, 0), "מחר 00:00")
        check("מחר ב-10 בלילה", at(11, 2, 22), "מחר 22:00")
        check("מחר ב-5 אחה\"צ", at(11, 2, 17), "מחר 17:00")
    }

    @Test
    fun hebrewDates() {
        check("ב-15.11", at(11, 15), "15.11", hasTime = false)
        check("ב-15/11", at(11, 15), "15.11", hasTime = false)
        check("ב-15/11 בשעה 9", at(11, 15, 9), "15.11 09:00")
        check("ב-15 בנובמבר ב-20:30", at(11, 15, 20, 30), "15.11 20:30")
        check("ב-15 בנובמבר", at(11, 15), "15.11", hasTime = false)
        check("ב-20 לדצמבר 2026", at(12, 20), "20.12", hasTime = false)
        check("מחרתיים", at(11, 3), "מחרתיים", hasTime = false)
        check("מחר", at(11, 2), "מחר", hasTime = false)
        check("ביום שלישי", at(11, 3), "מחרתיים", hasTime = false)
        check("בשבת", at(11, 7), "יום שבת", hasTime = false)
        check("ב-1.1.2027", at(1, 1, year = 2027), "1.1.2027", hasTime = false)
        check("ב-5.3", at(3, 5, year = 2027), "5.3.2027", hasTime = false)
        check("ב-31/12/26 בשעה 23:00", at(12, 31, 23, 0), "31.12 23:00")
    }

    @Test
    fun hebrewDurations() {
        check("בעוד שעה", at(11, 1, 15), "היום 15:00")
        check("בעוד 20 דקות", at(11, 1, 14, 20), "היום 14:20")
        check("בעוד שעתיים", at(11, 1, 16), "היום 16:00")
        check("בעוד שעה וחצי", at(11, 1, 15, 30), "היום 15:30")
        check("בעוד חצי שעה", at(11, 1, 14, 30), "היום 14:30")
        check("בעוד רבע שעה", at(11, 1, 14, 15), "היום 14:15")
        check("בעוד שלוש שעות", at(11, 1, 17), "היום 17:00")
        check("בעוד 3 שעות", at(11, 1, 17), "היום 17:00")
        check("בעוד 3 ימים", at(11, 4), "יום ד׳", hasTime = false)
        check("בעוד יומיים ב-10:00", at(11, 3, 10), "מחרתיים 10:00")
        check("בעוד שבוע", at(11, 8), "8.11", hasTime = false)
        check("בעוד שבועיים", at(11, 15), "15.11", hasTime = false)
    }

    @Test
    fun hebrewBareTimeMeansTheNextOne() {
        check("ב-18:30", at(11, 1, 18, 30), "היום 18:30")
        check("נפגשים בשעה 17:45 בקניון", at(11, 1, 17, 45), "היום 17:45")
        check("ב-9:00", at(11, 2, 9), "מחר 09:00")
        check("ב-8 בערב", at(11, 1, 20), "היום 20:00")
        check("בשעה 9 בבוקר", at(11, 2, 9), "מחר 09:00")
    }

    @Test
    fun englishPhrases() {
        check("tomorrow at 10", at(11, 2, 10), "Tomorrow 10:00")
        check("Tomorrow at 10:30 am", at(11, 2, 10, 30), "Tomorrow 10:30")
        check("today 6:30 pm", at(11, 1, 18, 30), "Today 18:30")
        check("on Tuesday at 8pm", at(11, 3, 20), "Tue 20:00")
        check("on Thursday at 8:15 PM", at(11, 5, 20, 15), "Thu 20:15")
        check("in 2 hours", at(11, 1, 16), "Today 16:00")
        check("in 20 minutes", at(11, 1, 14, 20), "Today 14:20")
        check("in 20 mins", at(11, 1, 14, 20), "Today 14:20")
        check("in an hour", at(11, 1, 15), "Today 15:00")
        check("in half an hour", at(11, 1, 14, 30), "Today 14:30")
        check("in two hours", at(11, 1, 16), "Today 16:00")
        check("in 3 days", at(11, 4), "Wed", hasTime = false)
        check("in a week", at(11, 8), "8 Nov", hasTime = false)
        check("Nov 15", at(11, 15), "15 Nov", hasTime = false)
        check("November 15th", at(11, 15), "15 Nov", hasTime = false)
        check("15 Nov", at(11, 15), "15 Nov", hasTime = false)
        check("15th of November", at(11, 15), "15 Nov", hasTime = false)
        check("Dec 25, 2026", at(12, 25), "25 Dec", hasTime = false)
        check("15/11", at(11, 15), "15 Nov", hasTime = false)
        check("11/15", at(11, 15), "15 Nov", hasTime = false)
        check("15/11/2026 at 9am", at(11, 15, 9), "15 Nov 09:00")
        check("the day after tomorrow", at(11, 3), "Tue", hasTime = false)
        check("tomorrow", at(11, 2), "Tomorrow", hasTime = false)
        check("at 6pm tomorrow", at(11, 2, 18), "Tomorrow 18:00")
        check("tomorrow at 12am", at(11, 2, 0), "Tomorrow 00:00")
        check("tomorrow at 12 pm", at(11, 2, 12), "Tomorrow 12:00")
        check("see you 2 Dec at 7:45", at(12, 2, 7, 45), "2 Dec 07:45")
    }

    @Test
    fun englishWeekdaysFollowTheRuleOfTheNextOne() {
        // Today is Sunday, 14:00.
        check("next Monday at 9am", at(11, 2, 9), "Tomorrow 09:00")
        check("Sunday at 5pm", at(11, 1, 17), "Today 17:00")
        check("on Sunday at 9am", at(11, 8, 9), "8 Nov 09:00")
        check("on Sunday", at(11, 8), "8 Nov", hasTime = false)
        check("next Sunday at 5pm", at(11, 8, 17), "8 Nov 17:00")
        check("on Fri at 4pm", at(11, 6, 16), "Fri 16:00")
        check("this Saturday", at(11, 7), "Sat", hasTime = false)
    }

    @Test
    fun theMatchedRangeAndTextAreExact() {
        val text = "פגישה עם דני מחר ב-10:00 במשרד"
        val m = single(text)
        assertEquals("מחר ב-10:00", m.text)
        assertEquals("מחר ב-10:00", text.substring(m.range))

        val en = "Meeting on Nov 15th, 2026 at 6:30 pm in the office"
        val e = single(en)
        assertEquals("on Nov 15th, 2026 at 6:30 pm", e.text)
        assertEquals(at(11, 15, 18, 30), e.dateTime)
        assertEquals("15 Nov 18:30", e.label)

        val emoji = "📅 מחר ב-10:00 🙏"
        assertEquals("מחר ב-10:00", single(emoji).text)
        assertEquals("מחר ב-10:00", emoji.substring(single(emoji).range))

        val rtl = "‏נפגשים ‫מחר‬ ב-10:00"
        assertEquals(at(11, 2, 10), single(rtl).dateTime)
    }

    @Test
    fun severalPhrasesInOneMessage() {
        val text = "תזכורת: מחר ב-9:00 וגם ב-15/11 בשעה 18:00"
        val found = DateTimeExtractor.extract(text, now)
        assertEquals(2, found.size)
        assertEquals(at(11, 2, 9), found[0].dateTime)
        assertEquals(at(11, 15, 18), found[1].dateTime)
        assertTrue(found[0].range.last < found[1].range.first)

        val en = DateTimeExtractor.extract("Call me in 20 minutes or on Tuesday at 8pm", now)
        assertEquals(listOf(at(11, 1, 14, 20), at(11, 3, 20)), en.map { it.dateTime })
    }

    @Test
    fun pastTimesToday_areIgnored() {
        for (text in listOf("היום ב-9:00", "today at 9am", "today 6:30 am", "היום בשעה 8 בבוקר", "אתמול ב-10:00", "yesterday at 5pm")) {
            assertEquals(text, emptyList<DateTimeMatch>(), DateTimeExtractor.extract(text, now))
        }
    }

    @Test
    fun pastDatesAreIgnoredOrRolledForward() {
        assertEquals(emptyList<DateTimeMatch>(), DateTimeExtractor.extract("ב-15.11.2025", now))
        assertEquals(emptyList<DateTimeMatch>(), DateTimeExtractor.extract("on 3 Jan 2026", now))
        assertEquals(emptyList<DateTimeMatch>(), DateTimeExtractor.extract("1/11/2026 at 9:00", now))
        assertEquals(at(1, 3, year = 2027), single("on 3 Jan").dateTime)
        assertEquals(at(10, 31, year = 2027), single("31/10").dateTime)
        assertEquals(at(11, 1, 15), single("1/11 at 15:00").dateTime)
        assertEquals(at(2, 29, year = 2028), single("on 29 Feb").dateTime)
    }

    @Test
    fun textWithoutADateGivesNothing() {
        val cases = listOf(
            "",
            "שלום מה שלומך",
            "I'll be there soon",
            "הקוד שלך 482913",
            "היום",
            "today",
            "הוא היה שם לפני שעה",
            "open 24/7",
            "מחיר 12.50 ₪",
            "ב-3.5 ק\"מ מכאן",
            "ב-5 ק\"מ",
            "1/2 קילו",
            "Score was 3.5 in the final",
            "תפוח ב-15.11 שקל",
            "ינואר הוא חודש קר",
            "He sat on the chair",
            "I may call you",
            "הבית ברחוב הרצל 15",
        )
        for (text in cases) {
            val found = DateTimeExtractor.extract(text, now)
            assertEquals("$text -> $found", emptyList<DateTimeMatch>(), found)
        }
    }

    @Test
    fun invalidDatesAreRejected() {
        for (text in listOf("ב-32.1", "on 31/2/2027", "ב-30.2.2027", "13/13", "Nov 32", "ב-31 בנובמבר 2026")) {
            assertEquals(text, emptyList<DateTimeMatch>(), DateTimeExtractor.extract(text, now))
        }
    }

    @Test
    fun labelsUseTheLanguageOfThePhrase() {
        assertTrue(Text.hasHebrew(single("מחר ב-10:00").label))
        assertFalse(Text.hasHebrew(single("tomorrow at 10").label))
    }

    @Test
    fun weekdayBeforeADateUsesTheDate() {
        check("Dentist appointment Monday 3 Nov at 9:15am", at(11, 3, 9, 15), "Tue 09:15")
        check("Monday, Nov 9 at 6pm", at(11, 9, 18), "9 Nov 18:00")
        check("ביום שני ה-9.11 בשעה 16:30", at(11, 9, 16, 30), "9.11 16:30")
    }

    @Test
    fun realLookingHebrewMessages() {
        check("נתראה ביום ראשון בשעה 18:00 אצלי", at(11, 1, 18), "היום 18:00")
        check("נתראה ביום ראשון בשעה 10:00 אצלי", at(11, 8, 10), "8.11 10:00")
        check("הפגישה תתקיים ב-3/12 בשעה 16:30", at(12, 3, 16, 30), "3.12 16:30")
        check("תזכורת: חיסון ב-20.11 בשעה 08:15", at(11, 20, 8, 15), "20.11 08:15")
        check("התור שלך נקבע למחר ב-16:45. לביטול השב 1", at(11, 2, 16, 45), "מחר 16:45")
        check("אני מגיע בעוד 20 דקות", at(11, 1, 14, 20), "היום 14:20")
        check("תתקשר אליי היום ב-17:00", at(11, 1, 17), "היום 17:00")
    }

    @Test
    fun realLookingEnglishMessages() {
        check("Reminder: your appointment is tomorrow at 3:30 pm at the clinic.", at(11, 2, 15, 30), "Tomorrow 15:30")
        check("Your delivery arrives on Thursday between 9-12", at(11, 5), "Thu", hasTime = false)
        check("Dinner at 8pm?", at(11, 1, 20), "Today 20:00")
        check("Let's meet on 20/11 at 7", at(11, 20, 7), "20 Nov 07:00")
    }

    @Test(timeout = 5_000)
    fun daysNoMonthHasAreNoDates() {
        // These once searched the years forever for a 31 November: the conversation never opened
        for (text in listOf("המבצע בתוקף עד ה-31.11", "until 31/11", "ב-30.2", "on 31/4/2027", "ב-31 בספטמבר", "Jun 31", "31st of April")) {
            assertEquals(text, emptyList<DateTimeMatch>(), DateTimeExtractor.extract(text, now))
        }
    }

    @Test(timeout = 5_000)
    fun theTwentyNinthOfFebruaryIsTheNextLeapYears() {
        check("ב-29.2", at(2, 29, year = 2028), "29.2.2028", hasTime = false)
        assertEquals(emptyList<DateTimeMatch>(), DateTimeExtractor.extract("ב-29.2.2027", now))
    }
}
