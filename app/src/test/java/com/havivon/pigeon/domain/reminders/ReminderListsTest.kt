package com.havivon.pigeon.domain.reminders

import com.havivon.pigeon.domain.model.Reminder
import com.havivon.pigeon.fakes.Now
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderListsTest {

    private val hour = 60 * 60 * 1000L
    private val day = 24 * hour

    private fun r(id: Long, at: Long, done: Boolean = false) = Reminder(id, threadId = 1, messageId = null, text = "t$id", triggerAtMillis = at, done = done)

    @Test
    fun `upcoming is what is ahead and not done, soonest first`() {
        val lists = ReminderLists.of(listOf(r(1, Now + 3 * hour), r(2, Now + hour), r(3, Now + 2 * day)), Now)
        assertEquals(listOf(2L, 1L, 3L), lists.upcoming.map { it.id })
        assertEquals(emptyList<Long>(), lists.past.map { it.id })
    }

    @Test
    fun `past is done or missed within thirty days, latest first`() {
        val lists = ReminderLists.of(
            listOf(
                r(1, Now - 2 * day, done = true),
                r(2, Now - hour), // missed: due, not done
                r(3, Now - 29 * day, done = true),
                r(4, Now - 31 * day, done = true), // too old
            ),
            Now,
        )
        assertEquals(emptyList<Long>(), lists.upcoming.map { it.id })
        assertEquals(listOf(2L, 1L, 3L), lists.past.map { it.id })
    }
}
