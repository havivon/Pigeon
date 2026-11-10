package com.havivon.pigeon.domain.backup

import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.domain.model.AppIcon
import com.havivon.pigeon.domain.model.AppLanguage
import com.havivon.pigeon.domain.model.AppTheme
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.RuleMatch
import java.security.MessageDigest

/*
 * Pigeon's settings as a portable file ("Export settings" / "Import settings").
 *
 * PRIVACY: this model, and the JSON made from it, hold the user's SETTINGS only. They never hold a
 * message body or a reminder's text (a reminder's text is a copy of a message, so it is rebuilt from
 * the phone's own messages on import). A starred message or a message with a reminder is written as a
 * reference: the sender's key, the message's date and, for a starred message, a short hash of the text
 * used only to recognise the same message again. Nothing here is ever logged.
 */

/** The settings the file carries; null means "not in the file" (or unusable), and import then keeps the current value. */
data class BackupSettings(
    val language: AppLanguage? = null,
    val theme: AppTheme? = null,
    val appIcon: AppIcon? = null,
    val lockScreenText: Boolean? = null,
    val widgetsShowText: Boolean? = null,
    val blockScreenshots: Boolean? = null,
    val appLock: Boolean? = null,
    val autoDeleteCodes: Boolean? = null,
    val blockSpamOnPhone: Boolean? = null,
    val autoCopyCodes: Boolean? = null,
    /** A skin's id (see `SkinIds`); a file from before skins (0.6.0) has none. */
    val skin: String? = null,
) {
    /** How many settings the file holds. */
    val count: Int
        get() = listOf<Any?>(language, theme, appIcon, lockScreenText, widgetsShowText, blockScreenshots, appLock, autoDeleteCodes, blockSpamOnPhone, autoCopyCodes, skin).count { it != null }
}

/** A colour rule. [argb] is a full ARGB value, as in `ColorRule`. */
data class BackupRule(val label: String, val match: RuleMatch, val argb: Long, val enabled: Boolean)

/**
 * A conversation moved by hand. [key] is a chat key (see [ChatKeys]); exactly one of [folder] (a built-in
 * folder, never All or Spam) and [category] (the name of one of the file's [SettingsBackup.categories]) is set.
 */
data class BackupMove(val key: String, val folder: Folder?, val category: String?)

/** A reminder not done yet: when it fires, the chat it is in and, if made on one message, that message's date. No text. */
data class BackupReminder(val triggerAtMillis: Long, val chat: String, val messageDateMillis: Long?)

/** A starred message by reference: the chat, the message's date and [bodyHash] ([BackupHash]) to recognise it. No text. */
data class BackupFavorite(val chat: String, val dateMillis: Long, val bodyHash: String, val starredAtMillis: Long?)

data class SettingsBackup(
    /** 0 when the file does not say. */
    val exportedAtMillis: Long,
    val appVersion: String,
    val settings: BackupSettings,
    val rules: List<BackupRule>,
    /** The user's own categories, in their order. */
    val categories: List<String>,
    /**
     * The colours of the categories, by name (only those that have one). Version 2 of the file; a version 1 file has none.
     */
    val categoryColors: Map<String, Long> = emptyMap(),
    /**
     * The colours of the built-in folders. Null when the file does not carry them (version 1): an import then leaves
     * the phone's as they are.
     */
    val folderColors: Map<Folder, Long>? = null,
    val moves: List<BackupMove>,
    /** Chat keys. */
    val pinned: List<String>,
    val locked: List<String>,
    /** The "not spam" senders, as the app keeps them (normalised). */
    val notSpam: List<String>,
    val blockedWords: List<String>,
    val reminders: List<BackupReminder>,
    val favorites: List<BackupFavorite>,
) {
    fun summary() = BackupSummary(
        exportedAtMillis, appVersion, settings.count, rules.size, categories.size, moves.size,
        pinned.size, locked.size, notSpam.size, blockedWords.size, reminders.size, favorites.size,
    )
}

/** What a file holds, for the confirmation before an import: a count per kind. */
data class BackupSummary(
    val exportedAtMillis: Long,
    val appVersion: String,
    val settings: Int,
    val rules: Int,
    val categories: Int,
    val moves: Int,
    val pinned: Int,
    val locked: Int,
    val notSpam: Int,
    val blockedWords: Int,
    val reminders: Int,
    val favorites: Int,
)

/** Why a file was refused. */
enum class BackupError { Unreadable, NotJson, WrongFormat, TooNew, TooLarge }

sealed interface BackupParse {
    data class Ok(val backup: SettingsBackup) : BackupParse
    data class Failed(val error: BackupError) : BackupParse
}

/** The limits a file is held to: it comes from outside, so it is never trusted to be small or sane. */
object BackupLimits {
    const val FORMAT = "pigeon-settings"
    const val VERSION = 2
    const val MAX_FILE_BYTES = 2 * 1024 * 1024
    const val MAX_LABEL = 100
    const val MAX_VALUE = 200
    const val MAX_KEY = 400
    const val MAX_VERSION_NAME = 40
    const val MAX_RULES = 200
    const val MAX_CATEGORIES = 100
    const val MAX_MOVES = 5_000
    const val MAX_CHATS = 5_000
    const val MAX_NOT_SPAM = 5_000
    const val MAX_WORDS = 1_000
    const val MAX_REMINDERS = 1_000
    const val MAX_FAVORITES = 5_000
    const val MAX_CATEGORY_NAME = 24

    /** The latest date a time may have (year 2100): anything later is a mistake. */
    const val MAX_MILLIS = 4_102_444_800_000L
}

/**
 * The key a conversation travels under, from one phone to another (thread ids are the phone's own): the
 * sender's lookup key for one sender, `group:` and the members' sorted keys for a group.
 */
object ChatKeys {
    const val GROUP = "group:"

    fun of(addresses: List<String>): String? {
        val keys = addresses.mapNotNull { PhoneNumbers.lookupKey(it) }.distinct()
        return when (keys.size) {
            0 -> null
            1 -> keys[0]
            else -> GROUP + keys.sorted().joinToString(",")
        }
    }
}

/** A short fingerprint of a message's text, to tell whether it is the same message: 8 hex digits, not enough to read anything back. */
object BackupHash {
    fun of(body: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(body.trim().toByteArray(Charsets.UTF_8))
        return digest.take(4).joinToString("") { "%02x".format(it) }
    }
}
