package com.havivon.pigeon.domain.seam

import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.SmsConversation

/**
 * The points where the message analysis (spam, codes, folders) plugs into the SMS platform.
 * The platform only knows these interfaces; each default does nothing, so the platform works on
 * its own. The real implementations are passed to `AppContainer` (see `PigeonApp`).
 */

/** Finds the one-time code in a message, for the "copy code" button and the 24-hour auto-delete. */
fun interface CodeFinder {
    /** The code, or null when the message has none. Must be fast and pure: it runs on every notification. */
    fun find(body: String): String?

    companion object {
        val None = CodeFinder { null }
    }
}

/** What to do with a new message once it is safely stored. */
enum class IncomingVerdict {
    /** Notify as usual. */
    Notify,

    /** Spam or a blocked keyword: the message stays stored (it shows in the Spam folder), but no notification. */
    Silent,
}

/** A new incoming message, as handed to [IncomingMessageHook]. */
data class StoredIncomingSms(
    val messageId: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val dateMillis: Long,
)

/**
 * Called by the SMS receiver after a new message is stored and before it is notified. This is
 * where spam scoring and blocked keywords record their verdict. A hook that throws is treated as
 * [IncomingVerdict.Notify]: a failure in the analysis must never hide a real message.
 */
fun interface IncomingMessageHook {
    suspend fun onStored(message: StoredIncomingSms): IncomingVerdict

    companion object {
        val NotifyAll = IncomingMessageHook { IncomingVerdict.Notify }
    }
}

/** Puts a conversation in its inbox folder (personal, banks, codes, deliveries, spam). */
fun interface FolderClassifier {
    fun classify(conversation: SmsConversation): Folder

    companion object {
        val AllPersonal = FolderClassifier { Folder.Personal }
    }
}
