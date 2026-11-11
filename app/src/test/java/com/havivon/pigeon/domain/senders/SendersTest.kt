package com.havivon.pigeon.domain.senders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SendersTest {

    private fun sender(address: String, name: String? = null, last: Long = 0, vararg threads: Long) =
        distinctSenders(listOf(AddressRow(threads.firstOrNull() ?: 1L, address, last))) { name }.single()

    private fun find(all: List<Sender>, q: String) = SenderMatcher.suggest(all, q).map { it.label }

    private val people = listOf(
        sender("0525550100", "Danny Cohen", 50),
        sender("0541112222", "דנה לוי", 40),
        sender("+972503334444", "Dana Levi", 30),
        sender("Leumi", null, 60),
        sender("HAREL", null, 20),
        sender("Paisplus", null, 10),
        sender("0521230000", "Abby Dan", 70),
        sender("+15551234567", null, 5),
    )

    // distinct senders

    @Test
    fun `incoming and outgoing addresses of every thread are one entry per normalised address`() {
        val rows = listOf(
            AddressRow(1, "0525550100", 100),
            AddressRow(1, "0525550100", 300),
            AddressRow(2, "+972 52-555-0100", 200),
            AddressRow(3, "Leumi", 150),
            AddressRow(4, "leumi", 120),
            AddressRow(5, "  ", 999),
            AddressRow(6, "", 999),
        )
        val all = distinctSenders(rows) { if (it.endsWith("0100")) "Danny" else null }
        assertEquals(listOf("Danny", "Leumi"), all.map { it.label })
        val danny = all[0]
        assertEquals(setOf(1L, 2L), danny.threadIds)
        assertEquals(2, danny.conversationCount)
        assertEquals(300, danny.lastMillis)
        assertEquals("the way it was last written", "0525550100", danny.address)
        assertEquals(setOf(3L, 4L), all[1].threadIds)
        assertEquals("a sender ID in its first spelling when equally recent", "Leumi", all[1].address)
    }

    @Test
    fun `senders are newest first and a number without a name is labelled by its address`() {
        val all = distinctSenders(listOf(AddressRow(1, "0501", 10), AddressRow(2, "0502222222", 20)))
        assertEquals(listOf("0502222222", "0501"), all.map { it.label })
        assertEquals(null, all[0].name)
    }

    // matching

    @Test
    fun `an empty field gives the 20 newest senders`() {
        val many = (1..30).map { sender("050${1000000 + it}", null, it.toLong()) }
        val recent = SenderMatcher.suggest(many, "  ")
        assertEquals(20, recent.size)
        assertEquals(30L, recent.first().lastMillis)
        assertEquals(11L, recent.last().lastMillis)
    }

    @Test
    fun `names match in any case, Latin and Hebrew, anywhere in the name`() {
        assertEquals(setOf("Danny Cohen", "Dana Levi", "Abby Dan"), find(people, "dan").toSet())
        assertEquals(listOf("דנה לוי"), find(people, "דנה"))
        assertEquals(listOf("דנה לוי"), find(people, "לוי"))
        assertEquals(listOf("Danny Cohen"), find(people, "COHEN"))
    }

    @Test
    fun `a phone number matches however it is written`() {
        val danny = listOf("Danny Cohen")
        assertEquals(danny, find(people, "052-555"))
        assertEquals(danny, find(people, "0525550100"))
        assertEquals(danny, find(people, "+972 52 555 0100"))
        assertEquals(danny, find(people, "+972525550100"))
        assertEquals(danny, find(people, "525550100"))
        assertEquals(danny, find(people, "5255"))
        assertEquals(danny, find(people, "(052) 555-01"))
        assertEquals(listOf("Dana Levi"), find(people, "050 333"))
        assertEquals(listOf("Dana Levi"), find(people, "+97250333"))
        assertEquals("a foreign number by its digits", listOf("+15551234567"), find(people, "+1 555"))
    }

    @Test
    fun `alphanumeric senders match by their letters`() {
        assertEquals(listOf("HAREL"), find(people, "harel"))
        assertEquals(listOf("Paisplus"), find(people, "pais"))
        assertEquals(listOf("Leumi"), find(people, "EUM"))
    }

    @Test
    fun `nothing matches nothing`() {
        assertTrue(find(people, "zzz").isEmpty())
        assertTrue(find(people, "999999").isEmpty())
    }

    @Test
    fun `a prefix beats the start of a word beats a substring, then the newest wins`() {
        val all = listOf(
            sender("0501", "Xdan Smith", last = 100), // substring
            sender("0502", "Sam Dan", last = 90), // start of a word
            sender("0503", "Dan Old", last = 10), // prefix, old
            sender("0504", "Dana New", last = 80), // prefix, newer
        )
        assertEquals(listOf("Dana New", "Dan Old", "Sam Dan", "Xdan Smith"), find(all, "dan"))
    }

    @Test
    fun `at most 50 suggestions`() {
        val many = (1..80).map { sender("052${1000000 + it}", "Dana $it", it.toLong()) }
        assertEquals(50, SenderMatcher.suggest(many, "dana").size)
        assertEquals(80L, SenderMatcher.suggest(many, "dana").first().lastMillis)
        assertEquals(5, SenderMatcher.suggest(many, "dana", limit = 5).size)
    }
}
