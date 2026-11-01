package com.havivon.pigeon.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SameSenderTest {

    @Test
    fun `one number written in several ways is one sender`() {
        // A verdict stored from the incoming SMS, "Not spam" pressed on the conversation's own spelling
        val keys = listOf("+972525550123", "0525550999", "leumi")
        assertEquals(listOf("+972525550123"), sameSender(keys, "052-555-0123"))
        assertEquals(listOf("+972525550123"), sameSender(keys, "0525550123"))
    }

    @Test
    fun `sender ids match only themselves`() {
        val keys = listOf("leumi", "+972525550123")
        assertEquals(listOf("leumi"), sameSender(keys, "Leumi"))
        assertTrue(sameSender(keys, "Leumi2").isEmpty())
    }
}
