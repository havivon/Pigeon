package com.havivon.pigeon.domain.model

/** The app's own records, kept in its private database (never in the system SMS database). */

/**
 * A starred message. The text, sender and date are a copy, so the favourite survives when the
 * message itself is deleted.
 */
data class Favorite(
    val id: Long,
    /** The message's id in the system database; it may no longer exist. */
    val messageId: Long,
    val threadId: Long,
    val address: String,
    /** The sender's name when it was starred, or null for a number that was not a contact. */
    val senderName: String?,
    val body: String,
    val dateMillis: Long,
    val starredAtMillis: Long,
)

/** "Remind me about this": fires as a notification at [triggerAtMillis]. */
data class Reminder(
    val id: Long,
    val threadId: Long,
    /** The message it was created from, or null for a reminder on the whole conversation. */
    val messageId: Long?,
    val text: String,
    val triggerAtMillis: Long,
    val done: Boolean,
)

/** The spam filter's (or the user's) judgement on a sender. */
data class SpamVerdict(
    /** The sender, normalised (see `PhoneNumbers.normalize`). */
    val address: String,
    val threadId: Long,
    val reasons: List<SpamReason>,
    val judgedAtMillis: Long,
)

/** A word that sends any message containing it straight to spam. */
data class BlockedKeyword(
    val id: Long,
    val word: String,
)

enum class AppLanguage(val tag: String?) {
    /** Follow the phone's language. */
    System(null),
    Hebrew("iw"),
    English("en"),
}

/** The user's settings. The defaults match Google Messages (see CLAUDE.md, section 4). */
data class AppSettings(
    val language: AppLanguage = AppLanguage.System,
    val appIcon: AppIcon = AppIcon.Envelope,
    val lockScreenText: Boolean = true,
    val widgetsShowText: Boolean = true,
    val blockScreenshots: Boolean = false,
    val appLock: Boolean = false,
    val autoDeleteCodes: Boolean = true,
    /**
     * When auto-delete started counting, or null before its first run. Only codes received after
     * it are deleted, so turning the feature on never wipes the user's existing history.
     */
    val autoDeleteCodesSinceMillis: Long? = null,
    val welcomeDone: Boolean = false,
    /** Whether the app has asked (once, after becoming the default) for notifications and contacts. */
    val permissionsAsked: Boolean = false,
)
