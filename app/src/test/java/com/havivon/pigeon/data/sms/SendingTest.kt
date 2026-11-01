package com.havivon.pigeon.data.sms

import com.havivon.pigeon.domain.model.SmsType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SendingTest {

    private val transmitted = mutableListOf<Pair<String, Long>>()

    @Test
    fun `each recipient is written, then handed to the radio`() {
        var id = 10L
        val ids = sendEach(listOf("0525550100", " 0525550101 "), write = { id++ }) { a, i -> transmitted += a to i; true }
        assertEquals(listOf(10L, 11L), ids)
        assertEquals(listOf("0525550100" to 10L, "0525550101" to 11L), transmitted)
    }

    @Test
    fun `a message the system database refused is not sent at all`() {
        // Not the default SMS app: sent anyway, the screen would say "failed" and a second tap would send it twice
        val ids = sendEach(listOf("0525550100"), write = { null }) { a, i -> transmitted += a to i; true }
        assertTrue(ids.isEmpty())
        assertTrue(transmitted.isEmpty())
    }

    @Test
    fun `the same number written twice is sent once, and blank recipients not at all`() {
        var id = 1L
        sendEach(listOf("+972525550100", "+972 52-555-0100", "  ", ""), write = { id++ }) { a, i -> transmitted += a to i; true }
        assertEquals(1, transmitted.size)
    }

    @Test
    fun `a success never overwrites a failure written in the meantime`() {
        assertNull(sentResultGuard(SmsType.Failed))
        assertEquals("type != 5", sentResultGuard(SmsType.Sent))
    }
}
