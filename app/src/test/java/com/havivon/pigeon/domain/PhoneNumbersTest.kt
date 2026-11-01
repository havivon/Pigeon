package com.havivon.pigeon.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Fictional numbers only (555 range): the repo is public
class PhoneNumbersTest {

    @Test
    fun `normalize keeps plus and digits`() {
        assertEquals("+972525550123", PhoneNumbers.normalize(" +972 52-555-0123 "))
        assertEquals("0525550123", PhoneNumbers.normalize("052-555-0123"))
        assertEquals("0525550123", PhoneNumbers.normalize("(052) 555.0123"))
    }

    @Test
    fun `normalize keeps sender IDs as text, not keypad digits`() {
        assertEquals("leumi", PhoneNumbers.normalize(" Leumi "))
        assertEquals("isra-post", PhoneNumbers.normalize("Isra-Post"))
    }

    @Test
    fun `normalize of blank is blank`() {
        assertEquals("", PhoneNumbers.normalize("   "))
        assertEquals("", PhoneNumbers.normalize("+"))
    }

    @Test
    fun `local and international forms are the same sender`() {
        assertTrue(PhoneNumbers.same("+972525550123", "052-555-0123"))
        assertTrue(PhoneNumbers.same("0525550123", "+972 52 555 0123"))
    }

    @Test
    fun `different numbers are different senders`() {
        assertFalse(PhoneNumbers.same("+972525550123", "0525550124"))
    }

    @Test
    fun `short codes must match exactly`() {
        assertTrue(PhoneNumbers.same("5555", "5555"))
        assertFalse(PhoneNumbers.same("5555", "15555"))
    }

    @Test
    fun `sender IDs match by text, ignoring case`() {
        assertTrue(PhoneNumbers.same("LEUMI", "leumi"))
        assertFalse(PhoneNumbers.same("Leumi", "53864"))
    }

    @Test
    fun `blank never matches`() {
        assertFalse(PhoneNumbers.same("", ""))
    }

    @Test
    fun `isPhoneNumber tells numbers from sender IDs`() {
        assertTrue(PhoneNumbers.isPhoneNumber("+972 52-555-0123"))
        assertFalse(PhoneNumbers.isPhoneNumber("Leumi"))
        assertFalse(PhoneNumbers.isPhoneNumber(""))
    }

    @Test
    fun `query by digits finds formatted and international numbers`() {
        assertTrue(PhoneNumbers.matchesQuery("+972 52-555-0123", "5550123"))
        assertTrue(PhoneNumbers.matchesQuery("+972525550123", "0525"))
        assertFalse(PhoneNumbers.matchesQuery("+972525550123", "0599"))
    }

    @Test
    fun `query by text finds sender IDs`() {
        assertTrue(PhoneNumbers.matchesQuery("Leumi", "leu"))
        assertFalse(PhoneNumbers.matchesQuery("Leumi", ""))
        assertFalse(PhoneNumbers.matchesQuery("0525550123", "--"))
    }

    @Test
    fun `contact lookup keys meet across the ways a number is written`() {
        val mobile = listOf("052-555-0123", "0525550123", "+972 52-555-0123", "00972525550123", "972525550123", "+9720525550123")
        assertEquals(setOf("525550123"), mobile.map { PhoneNumbers.lookupKey(it) }.toSet())
        val landline = listOf("03-1234567", "+972 3-123-4567", "0097231234567")
        assertEquals(setOf("031234567"), landline.map { PhoneNumbers.lookupKey(it) }.toSet())
        assertEquals(PhoneNumbers.lookupKey("+1 555-555-0100"), PhoneNumbers.lookupKey("5555550100"))
        assertEquals("37133", PhoneNumbers.lookupKey("37133"))
        assertEquals("leumi", PhoneNumbers.lookupKey(" Leumi "))
        assertEquals(null, PhoneNumbers.lookupKey("  "))
        assertNotEquals(PhoneNumbers.lookupKey("0525550123"), PhoneNumbers.lookupKey("0525550124"))
    }
}
