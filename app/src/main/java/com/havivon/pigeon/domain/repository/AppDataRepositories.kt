package com.havivon.pigeon.domain.repository

import com.havivon.pigeon.domain.model.BlockedKeyword
import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.Favorite
import com.havivon.pigeon.domain.model.Reminder
import com.havivon.pigeon.domain.model.RuleMatch
import com.havivon.pigeon.domain.model.SpamReason
import com.havivon.pigeon.domain.model.SpamVerdict
import kotlinx.coroutines.flow.Flow

// The app's own data, in its private database. Each Flow emits again on every change.

/** The colour rules, in the user's order: the first matching rule wins. */
interface RulesRepository {
    val rules: Flow<List<ColorRule>>

    /** Adds a rule at the end of the list and returns its id. */
    suspend fun add(label: String, match: RuleMatch, argb: Long, enabled: Boolean = true): Int

    /** Saves the rule's label, match, colour and enabled flag; its place in the list stays. */
    suspend fun update(rule: ColorRule)

    suspend fun delete(id: Int)

    /** Puts the rules in this order (after a drag). Rules not listed keep their relative order, after the listed ones. */
    suspend fun reorder(idsInOrder: List<Int>)
}

interface FavoritesRepository {
    /** Newest star first. */
    val favorites: Flow<List<Favorite>>

    /** The starred messages' ids, for the star on each bubble. */
    val favoriteMessageIds: Flow<Set<Long>>

    /** Stars a message, keeping a copy of it. Starring it again only refreshes the copy. */
    suspend fun star(messageId: Long, threadId: Long, address: String, senderName: String?, body: String, dateMillis: Long)

    suspend fun unstar(messageId: Long)
}

interface RemindersRepository {
    /** Reminders not done yet, soonest first. */
    val pending: Flow<List<Reminder>>

    suspend fun add(threadId: Long, messageId: Long?, text: String, triggerAtMillis: Long): Long
    suspend fun get(id: Long): Reminder?
    suspend fun setDone(id: Long, done: Boolean = true)
    suspend fun delete(id: Long)

    /** Every pending reminder, for scheduling the alarms again after a reboot. */
    suspend fun allPending(): List<Reminder>
}

/**
 * Spam marks per sender, and the "not spam" senders the filter must leave alone. Addresses are
 * normalised inside, so callers pass them as the system stores them.
 */
interface SpamRepository {
    val verdicts: Flow<List<SpamVerdict>>

    /** The senders the user marked "not spam", normalised. */
    val notSpam: Flow<Set<String>>

    suspend fun verdictFor(address: String): SpamVerdict?

    /** Records a verdict; ignored (returns false) for a sender on the "not spam" list. */
    suspend fun markSpam(address: String, threadId: Long, reasons: List<SpamReason>): Boolean

    /** "Not spam": drops the verdict and keeps the filter away from this sender from now on. */
    suspend fun markNotSpam(address: String)

    suspend fun isNotSpam(address: String): Boolean

    /** Lets the filter judge this sender again. */
    suspend fun removeFromNotSpam(address: String)
}

/** Conversations behind fingerprint or screen lock. Their content never shows in notifications, widgets or search. */
interface LocksRepository {
    val lockedThreadIds: Flow<Set<Long>>

    suspend fun isLocked(threadId: Long): Boolean
    suspend fun lockedNow(): Set<Long>
    suspend fun lock(threadId: Long)
    suspend fun unlock(threadId: Long)
}

/** Conversations pinned to the top of the inbox. */
interface PinsRepository {
    val pinnedThreadIds: Flow<Set<Long>>

    suspend fun pin(threadId: Long)
    suspend fun unpin(threadId: Long)
}

interface BlockedKeywordsRepository {
    val keywords: Flow<List<BlockedKeyword>>

    /** Adds a word (trimmed); false for a blank word or one already on the list (case does not matter). */
    suspend fun add(word: String): Boolean

    suspend fun delete(id: Long)
}
