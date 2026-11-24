package com.havivon.pigeon.data.notify

import org.junit.Assert.assertEquals
import org.junit.Test

class BadgePlanTest {

    private fun shown(key: String, count: Int, time: Long) = BadgePlan.Shown(key, count, time)

    @Test
    fun `the newest notification carries the messages that have none`() {
        val plan = BadgePlan.of(listOf(shown("a", 2, 10), shown("b", 1, 20)), total = 9, badge = true)
        assertEquals(mapOf("a" to 2, "b" to 7), plan.numbers)
        assertEquals("no second carrier", 0, plan.quiet)
    }

    @Test
    fun `nine unread, the new one read, its notification gone - eight stay on the icon`() {
        assertEquals(9, BadgePlan.of(listOf(shown("a", 1, 10)), total = 9, badge = true).numbers["a"])
        assertEquals(BadgePlan(emptyMap(), quiet = 8), BadgePlan.of(emptyList(), total = 8, badge = true))
    }

    @Test
    fun `nothing unread - no number and no quiet notification`() {
        assertEquals(BadgePlan(emptyMap(), quiet = 0), BadgePlan.of(emptyList(), total = 0, badge = true))
    }

    @Test
    fun `a count larger than the total is not taken off`() {
        assertEquals(BadgePlan(mapOf("a" to 3), quiet = 0), BadgePlan.of(listOf(shown("a", 3, 10)), total = 2, badge = true))
    }

    @Test
    fun `with the setting off each has its own count and there is no quiet notification`() {
        assertEquals(BadgePlan(mapOf("a" to 2), quiet = 0), BadgePlan.of(listOf(shown("a", 2, 10)), total = 9, badge = false))
        assertEquals(BadgePlan(emptyMap(), quiet = 0), BadgePlan.of(emptyList(), total = 9, badge = false))
    }
}
