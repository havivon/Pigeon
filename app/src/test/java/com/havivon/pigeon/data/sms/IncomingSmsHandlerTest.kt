package com.havivon.pigeon.data.sms

import com.havivon.pigeon.domain.seam.IncomingMessageHook
import com.havivon.pigeon.domain.seam.IncomingVerdict
import com.havivon.pigeon.domain.seam.StoredIncomingSms
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The order that never loses a new message: stored first, then judged, then notified. */
class IncomingSmsHandlerTest {

    private val steps = mutableListOf<String>()
    private val logged = mutableListOf<String>()
    private var nextId = 100L

    /** Stores in memory, in thread 7, unless told to refuse or to fail. */
    private inner class Store(val refuse: Boolean = false, val fail: Boolean = false, val threadId: Long = 7) : IncomingSmsStore {
        val stored = mutableListOf<IncomingSms>()
        override fun store(sms: IncomingSms, subId: Int?, receivedAt: Long): StoredIncomingSms? {
            steps += "store:${sms.body}"
            if (fail) throw IllegalStateException("provider broke")
            if (refuse) return null
            stored += sms
            return StoredIncomingSms(nextId++, threadId, sms.address, sms.body, receivedAt)
        }
    }

    private inner class Notifier(val fail: Boolean = false) : NewMessageNotifier {
        override suspend fun notifyThread(threadId: Long) {
            steps += "notify:$threadId"
            if (fail) throw IllegalStateException("notifications broke")
        }

        override suspend fun notifyUnsaved(address: String, body: String, dateMillis: Long) {
            steps += "unsaved:$body"
        }
    }

    private fun hook(verdict: IncomingVerdict) = IncomingMessageHook {
        steps += "hook:${it.body}"
        verdict
    }

    private fun handler(store: IncomingSmsStore, hook: IncomingMessageHook, notifier: NewMessageNotifier, now: Long = 1_000) =
        IncomingSmsHandler(store, hook, notifier, now = { now }, log = { logged += it })

    private fun part(body: String, address: String = "+972525550100", at: Long = 1_000) = PduPart(address, body, at)

    @Test
    fun `a message is stored before it is judged and notified`() = runTest {
        handler(Store(), hook(IncomingVerdict.Notify), Notifier(), now = 5_000).handle(listOf(part("Hi")), subId = 1)
        assertEquals(listOf("store:Hi", "hook:Hi", "notify:7"), steps)
    }

    @Test
    fun `the parts of a long message are stored as one message`() = runTest {
        val store = Store()
        handler(store, hook(IncomingVerdict.Notify), Notifier()).handle(listOf(part("שלום "), part("עולם 👋")), subId = null)
        assertEquals(listOf("שלום עולם 👋"), store.stored.map { it.body })
    }

    @Test
    fun `spam is stored but not notified`() = runTest {
        val store = Store()
        handler(store, hook(IncomingVerdict.Silent), Notifier()).handle(listOf(part("You won")), subId = null)
        assertEquals(1, store.stored.size)
        assertEquals(listOf("store:You won", "hook:You won"), steps)
    }

    @Test
    fun `a hook that throws still lets the message be notified`() = runTest {
        val broken = IncomingMessageHook { throw IllegalStateException("analysis broke") }
        handler(Store(), broken, Notifier()).handle(listOf(part("Hi")), subId = null)
        assertEquals(listOf("store:Hi", "notify:7"), steps)
    }

    @Test
    fun `a refused message is shown with its text, since the notification is its only copy`() = runTest {
        handler(Store(refuse = true), hook(IncomingVerdict.Notify), Notifier()).handle(listOf(part("Code 1234")), subId = null)
        assertEquals(listOf("store:Code 1234", "unsaved:Code 1234"), steps)
    }

    @Test
    fun `a provider that throws does not lose the message either`() = runTest {
        handler(Store(fail = true), hook(IncomingVerdict.Notify), Notifier()).handle(listOf(part("Hi")), subId = null)
        assertEquals(listOf("store:Hi", "unsaved:Hi"), steps)
        // The log names the failure, never the message
        assertEquals(listOf("Store failed: java.lang.IllegalStateException"), logged)
    }

    @Test
    fun `a stored message whose conversation is unknown is still notified`() = runTest {
        handler(Store(threadId = 0), hook(IncomingVerdict.Notify), Notifier()).handle(listOf(part("Hi")), subId = null)
        assertEquals(listOf("store:Hi", "hook:Hi", "unsaved:Hi"), steps)
    }

    @Test
    fun `a failing notification does not stop the next message`() = runTest {
        val store = Store()
        handler(store, hook(IncomingVerdict.Notify), Notifier(fail = true))
            .handle(listOf(part("A", address = "111"), part("B", address = "222")), subId = null)
        assertEquals(listOf("A", "B"), store.stored.map { it.body })
    }

    @Test
    fun `an empty broadcast stores nothing`() = runTest {
        val store = Store()
        handler(store, hook(IncomingVerdict.Notify), Notifier()).handle(listOf(part("", address = "")), subId = null)
        assertTrue(store.stored.isEmpty())
        assertTrue(steps.isEmpty())
    }
}
