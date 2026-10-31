package com.havivon.pigeon.domain.repository

/**
 * Sends SMS. Only ever called after a user action (the send button, a notification reply) or by
 * the system's permission-guarded respond-via-message; nothing in the app sends by itself.
 */
interface SmsSender {

    /**
     * Sends [body] to each recipient as its own SMS (an SMS "group" is one message per number),
     * split into parts when it is long. Each message is written to the Outbox first and moves to
     * Sent or Failed when the radio reports back.
     *
     * @param subId the SIM to send from; null for the phone's default SMS SIM.
     * @return the new messages' ids in the system database, one per recipient that could be written.
     */
    suspend fun send(recipients: List<String>, body: String, subId: Int? = null): List<Long>

    /** Sends a failed message again, from the SIM it was first sent from. False if it is not a failed message. */
    suspend fun retry(messageId: Long): Boolean
}
