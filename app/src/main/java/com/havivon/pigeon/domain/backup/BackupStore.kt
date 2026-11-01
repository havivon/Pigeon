package com.havivon.pigeon.domain.backup

import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.Reminder

/** One move by hand, in this phone's terms: [key] as `FolderKeys` keeps it; exactly one of [folder] and [categoryIndex] (into [RestorePlan.categories]). */
data class PlannedMove(val key: String, val folder: Folder?, val categoryIndex: Int?)

data class PlannedReminder(val threadId: Long, val messageId: Long?, val text: String, val triggerAtMillis: Long)

data class PlannedFavorite(
    val messageId: Long,
    val threadId: Long,
    val address: String,
    val senderName: String?,
    val body: String,
    val dateMillis: Long,
    val starredAtMillis: Long,
)

/**
 * What an import writes, already turned into this phone's ids. A null list or set means "leave what is
 * there as it is" (the data needs the phone's messages and Pigeon cannot read them now).
 */
data class RestorePlan(
    val rules: List<ColorRule>,
    val categories: List<String>,
    val moves: List<PlannedMove>,
    val pinnedThreadIds: Set<Long>?,
    val lockedThreadIds: Set<Long>?,
    val notSpam: List<String>,
    val blockedWords: List<String>,
    val reminders: List<PlannedReminder>?,
    val favorites: List<PlannedFavorite>?,
)

/** Replaces the app's own data with a [RestorePlan]. The Room version does it in a single transaction: all of it or none. */
interface BackupStore {
    /** Returns the reminders now pending, with their new ids, so the caller can set their alarms. */
    suspend fun replace(plan: RestorePlan): List<Reminder>
}
