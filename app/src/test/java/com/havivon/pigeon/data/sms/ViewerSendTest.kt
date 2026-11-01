package com.havivon.pigeon.data.sms

import com.havivon.pigeon.domain.model.SmsType
import com.havivon.pigeon.fakes.sms
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sending while another app is the default: Android stores the sent message itself, so Pigeon must
 * write nothing (an Outbox row would show the message twice) and hand each message to the radio
 * exactly once. The default app's way (Outbox first) stays as it was.
 */
class ViewerSendTest {

    private class FakeOutbox : OutboxStore {
        val inserted = mutableListOf<String>()
        val failed = mutableListOf<Long>()
        val requeued = mutableListOf<Long>()
        var nextId = 100L
        override fun insert(address: String, body: String, subId: Int?): Long = nextId++.also { inserted += address }
        override fun markFailed(messageId: Long) {
            failed += messageId
        }
        override fun requeue(messageId: Long): Boolean = requeued.add(messageId)
    }

    private val outbox = FakeOutbox()
    private val transmitted = mutableListOf<Pair<String, Long>>()
    private var radioAccepts = true
    private val radio = Radio { address, _, _, id -> transmitted += address to id; radioAccepts }
    private var clock = 1_000_000L
    private val pending = PendingSends { clock }

    private fun flow(isDefault: Boolean) = SendFlow({ isDefault }, outbox, radio, pending) { 7L }

    @Test
    fun `a viewer sends each recipient exactly once and writes nothing`() = runTest {
        val ids = flow(isDefault = false).send(listOf("0525550100", "052-555-0100", "0525550101"), "On my way", null)
        assertTrue(outbox.inserted.isEmpty())
        assertEquals(listOf("0525550100", "0525550101"), transmitted.map { it.first })
        assertEquals(2, ids.size)
        assertTrue(ids.all { PendingSends.isPending(it) })
        // The radio got the same ids the screen waits on
        assertEquals(ids, transmitted.map { it.second })
        assertEquals(listOf(7L, 7L), pending.pending.value.map { it.threadId })
    }

    @Test
    fun `a viewer send the radio refuses at once is gone, and reported as not sent`() = runTest {
        radioAccepts = false
        val ids = flow(isDefault = false).send(listOf("0525550100"), "Hi", null)
        assertTrue(ids.isEmpty())
        assertTrue(pending.pending.value.isEmpty())
        assertTrue(outbox.inserted.isEmpty())
        assertTrue(outbox.failed.isEmpty())
        assertEquals(1, transmitted.size)
    }

    @Test
    fun `the default app still writes the Outbox first, then sends once`() = runTest {
        val ids = flow(isDefault = true).send(listOf("0525550100"), "Hi", null)
        assertEquals(listOf(100L), ids)
        assertEquals(listOf("0525550100"), outbox.inserted)
        assertEquals(listOf("0525550100" to 100L), transmitted)
        assertTrue(pending.pending.value.isEmpty())
    }

    @Test
    fun `the default app marks a refused send failed and keeps its row`() = runTest {
        radioAccepts = false
        val ids = flow(isDefault = true).send(listOf("0525550100"), "Hi", null)
        assertEquals(listOf(100L), ids)
        assertEquals(listOf(100L), outbox.failed)
    }

    @Test
    fun `a viewer cannot retry, since the failed row would need a write`() {
        val failed = sms(55, 7, "Hi", 1, type = SmsType.Failed)
        assertFalse(flow(isDefault = false).retry(failed))
        assertTrue(outbox.requeued.isEmpty())
        assertTrue(transmitted.isEmpty())
        assertTrue(flow(isDefault = true).retry(failed))
        assertEquals(listOf(55L), outbox.requeued)
        assertEquals(1, transmitted.size)
    }

    @Test
    fun `the wait ends on a failure or on the last part`() {
        val id = pending.add(7, "0525550100", "A long message", null)
        pending.onResult(id, success = true, isLastPart = false)
        assertEquals(1, pending.pending.value.size)
        pending.onResult(id, success = true, isLastPart = true)
        assertTrue(pending.pending.value.isEmpty())
        val other = pending.add(7, "0525550100", "Again", null)
        pending.onResult(other, success = false, isLastPart = false)
        assertTrue(pending.pending.value.isEmpty())
    }

    @Test
    fun `a pending send shows as sending until Android's row is there, then once`() {
        clock = 10_000
        val id = pending.add(7, "0525550100", "On my way", null)
        val before = listOf(sms(1, 7, "Where are you?", 5_000))
        val waiting = withPendingSends(before, pending.pending.value)
        assertEquals(listOf(1L, id), waiting.map { it.id })
        assertEquals(SmsType.Outbox, waiting.last().type)

        // Android writes the sent message (a little earlier on the clock is still the same one)
        val after = before + sms(2, 7, "On my way", 9_500, address = "+972525550100", type = SmsType.Sent)
        assertEquals(listOf(1L, 2L), withPendingSends(after, pending.pending.value).map { it.id })
    }

    @Test
    fun `an older message with the same text never hides a new send, and two sends need two rows`() {
        clock = 100_000
        pending.add(7, "0525550100", "ok", null)
        pending.add(7, "0525550100", "ok", null)
        val old = sms(1, 7, "ok", 10_000, type = SmsType.Sent)
        assertEquals(3, withPendingSends(listOf(old), pending.pending.value).size)
        val one = listOf(old, sms(2, 7, "ok", 100_100, type = SmsType.Sent))
        val shown = withPendingSends(one, pending.pending.value)
        assertEquals(3, shown.size)
        assertEquals(1, shown.count { PendingSends.isPending(it.id) })
    }
}
