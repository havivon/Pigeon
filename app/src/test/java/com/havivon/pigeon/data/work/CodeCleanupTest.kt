package com.havivon.pigeon.data.work

import com.havivon.pigeon.domain.seam.CodeFinder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeCleanupTest {

    private val day = CODE_MAX_AGE_MILLIS
    private val now = 10 * day

    /** Stands in for the real finder: a code is "123456" in the text. */
    private val finder = CodeFinder { body -> if ("123456" in body) "123456" else null }

    private fun candidate(id: Long, body: String, age: Long) = CodeCandidate(id, body, now - age)

    @Test
    fun `codes older than a day are deleted`() {
        val ids = codesToDelete(listOf(candidate(1, "Your code 123456", day + 1)), finder, now, sinceMillis = 0, starred = emptySet())
        assertEquals(listOf(1L), ids)
    }

    @Test
    fun `younger codes, messages without a code and starred ones stay`() {
        val ids = codesToDelete(
            listOf(
                candidate(1, "Your code 123456", day - 1),
                candidate(2, "See you tomorrow", 3 * day),
                candidate(3, "Your code 123456", 3 * day),
            ),
            finder, now, sinceMillis = 0, starred = setOf(3),
        )
        assertTrue(ids.isEmpty())
    }

    @Test
    fun `codes from before the feature started counting stay`() {
        val ids = codesToDelete(
            listOf(candidate(1, "Your code 123456", 5 * day), candidate(2, "Your code 123456", 2 * day)),
            finder, now, sinceMillis = now - 3 * day, starred = emptySet(),
        )
        assertEquals(listOf(2L), ids)
    }

    @Test
    fun `without a code finder nothing is deleted`() {
        assertTrue(codesToDelete(listOf(candidate(1, "Your code 123456", 3 * day)), CodeFinder.None, now, 0, emptySet()).isEmpty())
    }

    @Test
    fun `codes from the user's contacts are never deleted`() {
        val ids = codesToDelete(
            listOf(
                CodeCandidate(1, "Your code 123456", now - 2 * day, address = "Leumi"),
                CodeCandidate(2, "The gate code is 123456", now - 2 * day, address = "+972525550100"),
            ),
            finder, now, sinceMillis = 0, starred = emptySet(),
            fromContact = { it == "+972525550100" },
        )
        assertEquals(listOf(1L), ids)
    }
}
