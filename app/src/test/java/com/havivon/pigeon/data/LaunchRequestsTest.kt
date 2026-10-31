package com.havivon.pigeon.data

import com.havivon.pigeon.domain.model.ComposeRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchRequestsTest {

    @Test
    fun `recipients are split on commas and semicolons`() {
        assertEquals(listOf("+15555550100", "0525550123", "555-0199"), parseRecipients("+15555550100, 0525550123;555-0199"))
    }

    @Test
    fun `the query part is not a recipient`() {
        assertEquals(listOf("5555"), parseRecipients("5555?body=Hi"))
    }

    @Test
    fun `anything that is not a phone number is dropped`() {
        assertEquals(listOf("5555"), parseRecipients("5555,<script>,Leumi,,  ,++"))
        assertTrue(parseRecipients(null).isEmpty())
    }

    @Test
    fun `recipients are capped in length and number`() {
        assertTrue(parseRecipients("1".repeat(41)).isEmpty())
        assertEquals(20, parseRecipients((1..30).joinToString(",") { "55500$it" }).size)
    }

    @Test
    fun `body parameter from the link`() {
        assertEquals("Hi there", bodyParameter("5555?body=Hi there"))
        assertEquals("x", bodyParameter("5555?subject=a&body=x"))
        assertNull(bodyParameter("5555"))
        assertNull(bodyParameter(null))
    }

    @Test
    fun `sms schemes become a draft`() {
        assertEquals(ComposeRequest(listOf("5555"), "Hello"), composeRequestOf("smsto", "5555", "Hello", null))
        assertEquals(ComposeRequest(listOf("5555"), ""), composeRequestOf("SMS", "5555", null, null))
        assertEquals(ComposeRequest(emptyList(), ""), composeRequestOf("mmsto", "", null, null))
    }

    @Test
    fun `other schemes are not a draft`() {
        assertNull(composeRequestOf("tel", "5555", "x", null))
        assertNull(composeRequestOf(null, "5555", "x", null))
    }

    @Test
    fun `the body comes from sms_body, then EXTRA_TEXT, then the link`() {
        assertEquals("a", composeRequestOf("sms", "1?body=c", "a", "b")?.body)
        assertEquals("b", composeRequestOf("sms", "1?body=c", null, "b")?.body)
        assertEquals("c", composeRequestOf("sms", "1?body=c", null, null)?.body)
    }

    @Test
    fun `a huge body is cut to the longest message the app sends`() {
        assertEquals(10_000, composeRequestOf("sms", "1", "x".repeat(50_000), null)?.body?.length)
    }
}
