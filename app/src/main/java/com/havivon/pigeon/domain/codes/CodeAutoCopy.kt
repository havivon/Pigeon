package com.havivon.pigeon.domain.codes

import com.havivon.pigeon.domain.seam.CodeFinder
import com.havivon.pigeon.domain.seam.StoredIncomingSms
import java.security.MessageDigest

/** The clipboard, as [CodeAutoCopy] needs it. */
interface CodeClipboard {
    /** Puts [code] on the clipboard, flagged sensitive and marked with [tag] (so it can be recognised later without reading the text). */
    fun copy(code: String, tag: String)

    /**
     * The tag of what the clipboard holds now: [tag] of Pigeon's own copy, "" for anything else (something the user
     * copied since), or null when Android does not let Pigeon look (Android 10+ allows it only while Pigeon is on screen).
     */
    fun currentTag(): String?

    /** Empties the clipboard. */
    fun clear()
}

/** The copied code waiting to be cleared: only its [tag] (a hash, never the code itself) and when. */
data class PendingClear(val tag: String, val clearAtMillis: Long)

/** Where [PendingClear] is kept between the copy and the clearing (the process may end in between). */
interface PendingClearStore {
    fun load(): PendingClear?
    fun save(value: PendingClear?)
}

/**
 * Copies the one-time code of a message that just arrived, so the keyboard (Samsung Keyboard, Gboard) offers it as
 * a one-tap paste in the app or site the user is in, and clears it [CLEAR_AFTER_MILLIS] later.
 *
 * Copied only when the setting is on ([enabled]), for a message that arrived in the last [FRESH_MILLIS] (never for
 * old messages read again), that the spam filter let through (the caller asks only then), that is not in a locked
 * chat ([isLocked]) and whose sender is not blocked on the phone ([isBlocked]). A known person's bare number is not
 * a code (see `CodeExtractor`).
 *
 * Clearing never removes something else: the clip is recognised by its [CodeClipboard.currentTag], a hash of the code,
 * so Pigeon does not read the text (Android would then report that Pigeon read the clipboard). Android 10+ lets an
 * app look at the clipboard only while it is on screen; when it cannot look at the due time, the clearing waits for
 * the next time Pigeon is open ([clearIfDue]) and gives up after [GIVE_UP_AFTER_MILLIS]. Android 13+ also clears the
 * clipboard on its own after an hour.
 */
class CodeAutoCopy(
    private val codeFinder: CodeFinder,
    private val clipboard: CodeClipboard,
    private val pending: PendingClearStore,
    private val enabled: suspend () -> Boolean,
    private val isLocked: suspend (threadId: Long) -> Boolean,
    private val isBlocked: suspend (address: String) -> Boolean,
    /** A saved contact or someone the user wrote to. */
    private val knownPerson: suspend (address: String, threadId: Long) -> Boolean,
    /** Asks for [clearIfDue] to run at about this time (a one-off job). */
    private val scheduleClear: (atMillis: Long) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** After a new message passed the spam filter and was notified. True when a code was copied. */
    suspend fun onArrived(message: StoredIncomingSms): Boolean {
        val t = now()
        if (t - message.dateMillis !in 0..FRESH_MILLIS) return false
        if (!enabled()) return false
        if (message.threadId > 0 && isLocked(message.threadId)) return false
        if (isBlocked(message.address)) return false
        val code = codeFinder.find(message.body, message.address, knownPerson(message.address, message.threadId)) ?: return false
        val tag = tagOf(code, t)
        clipboard.copy(code, tag)
        val at = t + CLEAR_AFTER_MILLIS
        pending.save(PendingClear(tag, at))
        scheduleClear(at)
        return true
    }

    /**
     * Clears the copied code once it is due, if the clipboard still holds it. Something copied since is left alone
     * (and nothing is waiting any more); a clipboard Pigeon may not look at now keeps it waiting.
     */
    fun clearIfDue() {
        val p = pending.load() ?: return
        val t = now()
        if (t < p.clearAtMillis) return
        if (t > p.clearAtMillis + GIVE_UP_AFTER_MILLIS) {
            pending.save(null)
            return
        }
        val current = clipboard.currentTag() ?: return
        if (current == p.tag) clipboard.clear()
        pending.save(null)
    }

    companion object {
        /** How long after it arrived a message's code is still copied. */
        const val FRESH_MILLIS = 10_000L

        /** How long the code stays on the clipboard. */
        const val CLEAR_AFTER_MILLIS = 3 * 60_000L

        /** After this, a code that could not be cleared is left to Android (13+ clears the clipboard after an hour). */
        const val GIVE_UP_AFTER_MILLIS = 60 * 60_000L

        /** A short hash of the code and the time: recognises this copy, and tells nothing of the code. */
        fun tagOf(code: String, millis: Long): String {
            val digest = MessageDigest.getInstance("SHA-256").digest("$code:$millis".toByteArray())
            return digest.take(8).joinToString("") { "%02x".format(it) }
        }
    }
}
