package com.havivon.pigeon.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * The app's own tables. Message content lives in the system SMS database; these hold only what the
 * user added on top (rules, stars, reminders, locks, spam marks), keyed by the system's ids.
 */

@Entity(tableName = "color_rules")
data class ColorRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    /** The rule's place in the user's list; lower wins. */
    val position: Int,
    val label: String,
    /** [KIND_SENDER] or [KIND_KEYWORD]. */
    val kind: String,
    /** The sender's name or number, or the keyword. */
    val value: String,
    val argb: Long,
    val enabled: Boolean,
) {
    companion object {
        const val KIND_SENDER = "sender"
        const val KIND_KEYWORD = "keyword"
    }
}

@Entity(tableName = "favorites", indices = [Index(value = ["messageId"], unique = true)])
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    val threadId: Long,
    val address: String,
    val senderName: String?,
    val body: String,
    val dateMillis: Long,
    val starredAtMillis: Long,
)

@Entity(tableName = "reminders", indices = [Index("done", "triggerAtMillis")])
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: Long,
    val messageId: Long?,
    val text: String,
    val triggerAtMillis: Long,
    val done: Boolean = false,
    val createdAtMillis: Long,
)

@Entity(tableName = "locked_threads")
data class LockedThreadEntity(
    @PrimaryKey val threadId: Long,
    val lockedAtMillis: Long,
)

/** The latest verdict per sender. [reasons] is encoded by [SpamReasonCodec]. */
@Entity(tableName = "spam_verdicts", indices = [Index("threadId")])
data class SpamVerdictEntity(
    /** Normalised (see `PhoneNumbers.normalize`). */
    @PrimaryKey val address: String,
    val threadId: Long,
    val reasons: String,
    val judgedAtMillis: Long,
)

/** A sender the user marked "not spam". */
@Entity(tableName = "not_spam")
data class NotSpamEntity(
    /** Normalised (see `PhoneNumbers.normalize`). */
    @PrimaryKey val address: String,
    val addedAtMillis: Long,
)

@Entity(tableName = "blocked_keywords", indices = [Index(value = ["normalized"], unique = true)])
data class BlockedKeywordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** As the user typed it (trimmed). */
    val word: String,
    /** Lower case, for the "already on the list" check. */
    val normalized: String,
    val addedAtMillis: Long,
)
