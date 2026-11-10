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

enum class AppTheme {
    /** Follow the phone's light or dark mode. */
    System,
    Light,
    Dark,
}

/**
 * The colour skins' stored ids (`ui/theme/Skins.kt` draws them), in the order the picker shows them. The id is kept as
 * text, so a skin a newer version adds, or one removed, reads back as the default instead of failing.
 */
object SkinIds {
    const val DEFAULT = "sunset"
    val ALL = listOf(DEFAULT, "ocean", "forest", "midnight", "pomegranate", "desert", "pastel", "graphite")

    /** [id] when it names a skin, otherwise the default (Sunset). */
    fun orDefault(id: String?): String = id?.takeIf { it in ALL } ?: DEFAULT
}

/** The user's settings. The defaults match Google Messages (see CLAUDE.md, section 4). */
data class AppSettings(
    val language: AppLanguage = AppLanguage.System,
    val theme: AppTheme = AppTheme.System,
    /** The colour skin's id (see [SkinIds]); the launcher icon is a separate choice. */
    val skin: String = SkinIds.DEFAULT,
    val appIcon: AppIcon = AppIcon.Envelope,
    val lockScreenText: Boolean = true,
    val widgetsShowText: Boolean = true,
    val blockScreenshots: Boolean = false,
    val appLock: Boolean = false,
    val autoDeleteCodes: Boolean = false,
    /**
     * When auto-delete started counting, or null before its first run. Only codes received after
     * it are deleted, so turning the feature on never wipes the user's existing history.
     */
    val autoDeleteCodesSinceMillis: Long? = null,
    /** "Mark as spam" also puts the number on the phone's own blocked list, so it cannot call or text in any app. */
    val blockSpamOnPhone: Boolean = true,
    /** A one-time code that arrives is copied, so the keyboard offers it; it is cleared after a few minutes. */
    val autoCopyCodes: Boolean = true,
    val welcomeDone: Boolean = false,
    /** Whether the app has asked (once, after becoming the default) for notifications and contacts. */
    val permissionsAsked: Boolean = false,
)
