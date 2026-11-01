package com.havivon.pigeon.data

import com.havivon.pigeon.domain.analysis.Judged
import org.junit.Assert.assertEquals
import org.junit.Test

class SpamOnReadMemoryTest {

    @Test
    fun `the judged conversations survive a round trip, and damage loses only its entry`() {
        val judged = Judged(2, mapOf(12L to 1_700_000_000_000L, 13L to 1_700_000_500_000L))
        assertEquals(judged, decodeJudged(encodeJudged(judged)))
        assertEquals(Judged(0, emptyMap()), decodeJudged(null))
        assertEquals(Judged(0, emptyMap()), decodeJudged(""))
        assertEquals(Judged(3, mapOf(13L to 5L)), decodeJudged("v3;12:x;13:5;:7;garbage"))
        assertEquals(Judged(4, emptyMap()), decodeJudged(encodeJudged(Judged(4, emptyMap()))))
    }

    @Test
    fun `what 0_3_9 stored, without a version, was judged with the first rules`() {
        assertEquals(Judged(1, mapOf(12L to 1L, 13L to 2L)), decodeJudged("12:1;13:2"))
    }
}
