package com.havivon.pigeon.domain.codes

import com.havivon.pigeon.data.sms.IncomingSmsHandler
import com.havivon.pigeon.data.sms.NewMessageNotifier
import com.havivon.pigeon.data.sms.PduPart
import com.havivon.pigeon.domain.analysis.AnalysisSeams
import com.havivon.pigeon.domain.seam.IncomingMessageHook
import com.havivon.pigeon.domain.seam.IncomingVerdict
import com.havivon.pigeon.domain.seam.StoredIncomingSms
import com.havivon.pigeon.fakes.FakeClipboard
import com.havivon.pigeon.fakes.FakePendingClear
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Copy codes automatically": copied on arrival only when it should be, and cleared after 3 minutes only if unchanged. */
class CodeAutoCopyTest {

    private val clipboard = FakeClipboard()
    private val pending = FakePendingClear()
    private var now = 1_000_000L
    private var enabled = true
    private val locked = mutableSetOf<Long>()
    private val blocked = mutableSetOf<String>()
    private val known = mutableSetOf<String>()
    private val scheduled = mutableListOf<Long>()

    private val copy = CodeAutoCopy(
        AnalysisSeams.codeFinder, clipboard, pending,
        enabled = { enabled },
        isLocked = { it in locked },
        isBlocked = { it in blocked },
        knownPerson = { address, _ -> address in known },
        scheduleClear = { scheduled += it },
        now = { now },
    )

    private fun sms(body: String, address: String = "HAREL", threadId: Long = 7, at: Long = now) = StoredIncomingSms(1, threadId, address, body, at)

    @Test
    fun `a code that just arrived is copied, and its clearing is scheduled 3 minutes on`() = runTest {
        assertTrue(copy.onArrived(sms("66655")))
        assertEquals("66655", clipboard.text)
        assertEquals(listOf(now + 3 * 60_000L), scheduled)
        assertEquals(now + 3 * 60_000L, pending.value?.clearAtMillis)
        assertFalse("only a hash is kept, never the code", pending.value!!.tag.contains("66655"))
    }

    @Test
    fun `a code with a cue word is copied too`() = runTest {
        assertTrue(copy.onArrived(sms("קוד האימות שלך הוא 482913", address = "+972541234567")))
        assertEquals("482913", clipboard.text)
    }

    @Test
    fun `nothing is copied with the setting off`() = runTest {
        enabled = false
        assertFalse(copy.onArrived(sms("66655")))
        assertNull(clipboard.text)
        assertTrue(scheduled.isEmpty())
    }

    @Test
    fun `not from a locked chat, nor from a number blocked on the phone`() = runTest {
        locked += 7
        assertFalse(copy.onArrived(sms("66655")))
        locked.clear()
        blocked += "+972541234567"
        assertFalse(copy.onArrived(sms("קוד 482913", address = "+972541234567")))
        assertNull(clipboard.text)
    }

    @Test
    fun `an older message read again is never copied`() = runTest {
        assertFalse(copy.onArrived(sms("66655", at = now - 60_000)))
        assertNull(clipboard.text)
    }

    @Test
    fun `a message without a code, or a friend's bare number, is not copied`() = runTest {
        assertFalse(copy.onArrived(sms("שלום, מה נשמע?")))
        known += "+972525550101"
        assertFalse(copy.onArrived(sms("1234", address = "+972525550101")))
        assertNull(clipboard.text)
    }

    @Test
    fun `the code is cleared when due if the clipboard still holds it`() = runTest {
        copy.onArrived(sms("66655"))
        now += 2 * 60_000L
        copy.clearIfDue()
        assertEquals("not due yet", "66655", clipboard.text)
        now += 60_000L
        copy.clearIfDue()
        assertNull(clipboard.text)
        assertEquals(1, clipboard.clears)
        assertNull(pending.value)
    }

    @Test
    fun `something the user copied since is never cleared`() = runTest {
        copy.onArrived(sms("66655"))
        clipboard.userCopies("my address")
        now += 3 * 60_000L
        copy.clearIfDue()
        assertEquals("my address", clipboard.text)
        assertEquals(0, clipboard.clears)
        assertNull("and nothing waits any more", pending.value)
    }

    @Test
    fun `a newer code replaces the older one's clearing`() = runTest {
        copy.onArrived(sms("66655"))
        now += 60_000L
        copy.onArrived(sms("77788"))
        now += 2 * 60_000L
        copy.clearIfDue()
        assertEquals("the newer code has a minute left", "77788", clipboard.text)
        now += 60_000L
        copy.clearIfDue()
        assertNull(clipboard.text)
    }

    @Test
    fun `while Pigeon may not look, the clearing waits for the next time it is open, then gives up`() = runTest {
        copy.onArrived(sms("66655"))
        clipboard.readable = false
        now += 3 * 60_000L
        copy.clearIfDue()
        assertEquals("66655", clipboard.text)
        assertTrue(pending.value != null)
        clipboard.readable = true
        now += 10 * 60_000L
        copy.clearIfDue()
        assertNull("cleared once Pigeon could look", clipboard.text)

        copy.onArrived(sms("77788"))
        clipboard.readable = false
        now += 2 * 60 * 60_000L
        copy.clearIfDue()
        assertNull("given up after an hour", pending.value)
        assertEquals(1, clipboard.clears)
    }

    // The receiver's order: only a message that passed the filter is copied

    private class Notifier : NewMessageNotifier {
        override suspend fun notifyThread(threadId: Long) = Unit
        override suspend fun notifyUnsaved(address: String, body: String, dateMillis: Long) = Unit
    }

    private fun handler(verdict: IncomingVerdict) = IncomingSmsHandler(
        { sms, _, at -> StoredIncomingSms(9, 7, sms.address, sms.body, at) },
        IncomingMessageHook { verdict },
        Notifier(),
        now = { now },
        afterNotify = { copy.onArrived(it) },
        log = { },
    )

    @Test
    fun `an arriving code is copied after it is notified, a spam one is not`() = runTest {
        handler(IncomingVerdict.Silent).handle(listOf(PduPart("HAREL", "66655", now)), subId = null)
        assertNull("spam or a blocked word: silent, not copied", clipboard.text)
        handler(IncomingVerdict.Notify).handle(listOf(PduPart("HAREL", "66655", now)), subId = null)
        assertEquals("66655", clipboard.text)
    }
}
