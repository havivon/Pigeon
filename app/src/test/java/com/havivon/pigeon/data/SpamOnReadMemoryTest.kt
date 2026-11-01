package com.havivon.pigeon.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SpamOnReadMemoryTest {

    @Test
    fun `the judged conversations survive a round trip, and damage loses only its entry`() {
        val judged = mapOf(12L to 1_700_000_000_000L, 13L to 1_700_000_500_000L)
        assertEquals(judged, decodeJudged(encodeJudged(judged)))
        assertEquals(emptyMap<Long, Long>(), decodeJudged(null))
        assertEquals(emptyMap<Long, Long>(), decodeJudged(""))
        assertEquals(mapOf(13L to 5L), decodeJudged("12:x;13:5;:7;garbage"))
    }
}
