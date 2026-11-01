package com.havivon.pigeon.domain.model

/**
 * How far Pigeon may go with the phone's SMS. Only the default SMS app may change Android's SMS
 * database (store, delete, mark read) and receive new messages; any app the user allows to read
 * SMS can show them, and with the send permission send them (Android then stores the sent message).
 */
enum class SmsAccess {
    /** Pigeon is the default SMS app: it receives, reads, sends, deletes and marks read. */
    Default,

    /** Another app is the default, but Pigeon may read SMS: it shows and sends, and changes nothing in the database. */
    Viewer,

    /** Neither: Pigeon has nothing to show. */
    None;

    /** Conversations, messages, search and widgets can be read. */
    val canRead: Boolean get() = this != None

    /** Android's SMS database may be changed: deleting, marking read, the Outbox before a send, a retry. */
    val canWrite: Boolean get() = this == Default

    companion object {
        /** The access from the two facts that decide it: the default-app role and the READ_SMS permission. */
        fun of(isDefault: Boolean, canReadSms: Boolean): SmsAccess = when {
            isDefault -> Default
            canReadSms -> Viewer
            else -> None
        }
    }
}
