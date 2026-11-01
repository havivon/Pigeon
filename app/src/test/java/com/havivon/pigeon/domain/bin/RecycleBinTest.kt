package com.havivon.pigeon.domain.bin

import com.havivon.pigeon.domain.model.SmsType
import com.havivon.pigeon.fakes.FakeBinStore
import com.havivon.pigeon.fakes.Now
import com.havivon.pigeon.fakes.millis
import com.havivon.pigeon.fakes.sms
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// Made-up messages only.
class RecycleBinTest {

    private val store = FakeBinStore()
    private var clock = Now
    private val bin = RecycleBin(store) { clock }

    private val hello = sms(10, 1, "Hello there", millis(2026, 10, 31, 9, 0), "0525550100")
    private val reply = sms(11, 1, "See you soon", millis(2026, 10, 31, 9, 5), "0525550100", type = SmsType.Sent)
    private val secret = sms(20, 2, "The gate code is 4590", millis(2026, 10, 31, 10, 0), "0525550200")

    @Test
    fun `messages are copied into the bin before they are deleted`() = runTest {
        var deleted = false
        bin.moveToBin(listOf(BinCopy(hello, listOf("0525550100")), BinCopy(reply, listOf("0525550100"))), lockedThreads = emptySet()) {
            // At the moment of the delete, the copies are already stored
            assertEquals(listOf(10L, 11L), store.rows.value.map { it.originalId })
            deleted = true
        }
        assertTrue(deleted)
        val copy = store.rows.value.first()
        assertEquals("Hello there", copy.body)
        assertEquals(1L, copy.threadId)
        assertEquals(listOf("0525550100"), copy.threadAddresses)
        assertEquals(Now, copy.deletedAtMillis)
        assertEquals(SmsType.Sent, store.rows.value[1].type)
    }

    @Test
    fun `when the copy fails nothing is deleted`() = runTest {
        store.fail = true
        var deleted = false
        try {
            bin.moveToBin(listOf(BinCopy(hello, listOf("0525550100")))) { deleted = true }
            fail("the failed copy must stop the deletion")
        } catch (_: IOException) {
            // expected
        }
        assertFalse(deleted)
        assertTrue(store.rows.value.isEmpty())
    }

    private suspend fun RecycleBin.moveToBin(copies: List<BinCopy>, delete: suspend () -> Unit) = moveToBin(copies, emptySet(), delete)

    @Test
    fun `a locked chat's messages are marked locked in the bin`() = runTest {
        bin.moveToBin(listOf(BinCopy(hello, listOf("0525550100")), BinCopy(secret, listOf("0525550200"))), lockedThreads = setOf(2L)) {}
        assertEquals(listOf(false, true), store.rows.value.map { it.locked })
    }

    @Test
    fun `the daily purge deletes for good only what has been in the bin for 30 days`() = runTest {
        clock = Now
        bin.moveToBin(listOf(BinCopy(hello, listOf("0525550100")))) {}
        clock = Now + 3 * DAY_MILLIS
        bin.moveToBin(listOf(BinCopy(secret, listOf("0525550200")))) {}

        clock = Now + 29 * DAY_MILLIS
        assertEquals(0, bin.purgeExpired())
        assertEquals(2, store.rows.value.size)

        clock = Now + 30 * DAY_MILLIS + 1
        assertEquals(1, bin.purgeExpired())
        assertEquals(listOf(20L), store.rows.value.map { it.originalId })

        clock = Now + 34 * DAY_MILLIS
        assertEquals(1, bin.purgeExpired())
        assertTrue(store.rows.value.isEmpty())
    }

    @Test
    fun `days left count down from 30 and never go below 0`() {
        assertEquals(30, RecycleBin.daysLeft(Now, Now))
        assertEquals(30, RecycleBin.daysLeft(Now, Now + 1))
        assertEquals(1, RecycleBin.daysLeft(Now, Now + 29 * DAY_MILLIS + 60_000))
        assertEquals(0, RecycleBin.daysLeft(Now, Now + 30 * DAY_MILLIS))
        assertEquals(0, RecycleBin.daysLeft(Now, Now + 40 * DAY_MILLIS))
    }

    @Test
    fun `restore puts each conversation back into the thread the system gives for its addresses, oldest first`() = runTest {
        val groupMessage = sms(30, 3, "Dinner at 8?", millis(2026, 10, 30, 18, 0), "0525550300", type = SmsType.Sent)
        bin.moveToBin(
            listOf(
                BinCopy(reply, listOf("0525550100")),
                BinCopy(hello, listOf("0525550100")),
                BinCopy(groupMessage, listOf("0525550300", "0525550400")),
                BinCopy(secret, listOf("0525550200")),
            ),
        ) {}
        val asked = mutableListOf<List<String>>()
        val inserted = mutableListOf<Pair<Long, Long>>()
        val threads = mapOf(listOf("0525550100") to 101L, listOf("0525550300", "0525550400") to 103L, listOf("0525550200") to 102L)
        val restoring = store.rows.value.filter { it.originalId != 20L }
        val n = bin.restore(
            restoring,
            threadFor = { addresses -> asked += addresses; threads.getValue(addresses) },
            insert = { m, threadId -> inserted += m.originalId to threadId; true },
        )
        assertEquals(3, n)
        // Once per conversation, with all of a group's addresses
        assertEquals(listOf(listOf("0525550100"), listOf("0525550300", "0525550400")), asked)
        // Back in the old order: the older message first
        assertEquals(listOf(10L to 101L, 11L to 101L, 30L to 103L), inserted)
        // What was restored left the bin; the rest stays
        assertEquals(listOf(20L), store.rows.value.map { it.originalId })
    }

    @Test
    fun `a message the system refused stays in the bin`() = runTest {
        bin.moveToBin(listOf(BinCopy(hello, listOf("0525550100")), BinCopy(reply, listOf("0525550100")))) {}
        val n = bin.restore(store.rows.value, threadFor = { 101L }, insert = { m, _ -> m.originalId == 10L })
        assertEquals(1, n)
        assertEquals(listOf(11L), store.rows.value.map { it.originalId })
        // No thread to give: nothing is inserted, nothing leaves the bin
        assertEquals(0, bin.restore(store.rows.value, threadFor = { 0L }, insert = { _, _ -> true }))
        assertEquals(1, store.rows.value.size)
    }

    @Test
    fun `delete forever and empty touch only the bin`() = runTest {
        bin.moveToBin(listOf(BinCopy(hello, listOf("0525550100")), BinCopy(secret, listOf("0525550200")))) {}
        bin.deleteForever(listOf(store.rows.value.first { it.originalId == 10L }.id))
        assertEquals(listOf(20L), store.rows.value.map { it.originalId })
        bin.empty()
        assertTrue(store.rows.value.isEmpty())
    }
}
