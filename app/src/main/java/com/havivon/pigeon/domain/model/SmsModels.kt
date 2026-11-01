package com.havivon.pigeon.domain.model

/**
 * The SMS platform's own records, as read from Android's system SMS database. They carry raw
 * values (millis, provider ids); the screens' models ([Conversation], [Message]) are built from
 * them together with the analysis (folder, code, spam), which is why the two sets are separate.
 */

/** Where a message sits in the system database. The values are `Telephony.Sms.MESSAGE_TYPE_*`. */
enum class SmsType(val providerValue: Int) {
    Inbox(1),
    Sent(2),
    Draft(3),
    Outbox(4),
    Failed(5),
    Queued(6);

    val outgoing: Boolean get() = this != Inbox

    companion object {
        /** Unknown values (0 is "all", vendors add their own) count as inbox: shown, never resent. */
        fun fromProvider(value: Int): SmsType = entries.firstOrNull { it.providerValue == value } ?: Inbox
    }
}

/** One conversation (a system thread) in the inbox list. */
data class SmsConversation(
    val threadId: Long,
    /** The other side, as stored by the system: one address for a normal chat, several for a group. */
    val addresses: List<String>,
    /** The contact's name, or null for a number that is not in the contacts (or without permission). */
    val contactName: String?,
    val snippet: String,
    val dateMillis: Long,
    val unreadCount: Int,
    /** Whether a message in it failed to send, so the list can show a warning. */
    val hasFailed: Boolean,
    /** How many of its messages came in (the Spam folder counts a sender's messages). */
    val incomingCount: Int = 0,
    /** The user wrote in it (sent, or tried to): the other side is someone they talk to, never spam, and Personal. */
    val userWrote: Boolean = false,
    /**
     * The ids of its unread incoming messages, newest first: [unreadCount] is their number. The inbox's Unread
     * list shows exactly these messages, so a folder's count and its list always agree.
     */
    val unreadIds: List<Long> = emptyList(),
) {
    /** What the list shows as the title: the contact's name, otherwise the address(es). */
    val title: String get() = contactName ?: addresses.joinToString(", ")
}

data class SmsMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    /** When it arrived (incoming) or was written (outgoing). */
    val dateMillis: Long,
    /** When the sender's phone sent it; 0 when unknown. */
    val dateSentMillis: Long,
    val type: SmsType,
    val read: Boolean,
    /** The SIM it came on or went out on; null when the system did not record one. */
    val subId: Int?,
)

/** A contact for the new-message picker. */
data class Contact(
    val id: Long,
    val name: String,
    val numbers: List<String>,
)

/** What a search finds: conversations by name or number, and messages by text. */
data class SearchResults(
    val conversations: List<SmsConversation>,
    val messages: List<SmsMessage>,
) {
    companion object {
        val Empty = SearchResults(emptyList(), emptyList())
    }
}

/**
 * A deletion that is not final yet: what was deleted disappears from every list at once, and is
 * only removed from the system database when the undo window ends.
 */
interface PendingDeletion {
    /** Brings it back if the window has not ended yet; false when it is already gone for good. */
    fun undo(): Boolean
}

/** A "write a message" request that came from another app (an `sms:`/`smsto:` link): a draft, never sent by itself. */
data class ComposeRequest(
    val recipients: List<String>,
    val body: String,
)
