package com.havivon.pigeon.ui.common

import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.domain.model.SmsConversation
import com.havivon.pigeon.domain.model.SpamReason
import com.havivon.pigeon.domain.model.SpamVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The spam verdict of a conversation, looked up in a table: the same answers as comparing it with every verdict. */
class VerdictLookupTest {

    private val addresses = listOf(
        "+972525550123", "0525550123", "052-555-0123", "00972525550123", "972525550123", "525550123",
        "0525550124", "+15555550100", "5555550100", "Leumi", "LEUMI", " leumi ", "Leumi2", "4458", "*3455", "3455",
        "12345678", "123456789", "0123456789", "", "   ", "Pango IL",
    )

    @Test
    fun `the key means exactly what same means, for every pair`() {
        for (a in addresses) for (b in addresses) {
            val byKey = PhoneNumbers.sameKey(a).let { it != null && it == PhoneNumbers.sameKey(b) }
            assertEquals("'$a' / '$b'", PhoneNumbers.same(a, b), byKey)
        }
    }

    private fun verdict(address: String, thread: Long) = SpamVerdict(address, thread, listOf(SpamReason.ShortLink), 0)
    private fun conversation(vararg a: String) = SmsConversation(1, a.toList(), null, "", 0, 0, hasFailed = false)

    /** The scan the table replaced. */
    private fun scan(c: SmsConversation, verdicts: List<SpamVerdict>) =
        verdicts.firstOrNull { v -> c.addresses.any { PhoneNumbers.same(it, v.address) } }

    @Test
    fun `the lookup finds what the scan found, the earliest verdict first`() {
        val verdicts = listOf(verdict("+972525550123", 1), verdict("Leumi", 2), verdict("4458", 3), verdict("0525550123", 4), verdict("0541112222", 5))
        val cases = listOf(
            conversation("052-555-0123"),
            conversation("LEUMI"),
            conversation("0541112222", "Leumi"),
            conversation("4458"),
            conversation("0549999999"),
            conversation(),
            conversation("Leumi", "+972525550123"),
        )
        for (c in cases) assertEquals("${c.addresses}", scan(c, verdicts), verdictFor(c, verdicts))
        // A new list of verdicts is a new table
        val fewer = verdicts.drop(1)
        assertEquals(scan(conversation("052-555-0123"), fewer), verdictFor(conversation("052-555-0123"), fewer))
        assertEquals(4L, verdictFor(conversation("052-555-0123"), fewer)?.threadId)
        assertNull(verdictFor(conversation("0525550123"), emptyList()))
    }
}
