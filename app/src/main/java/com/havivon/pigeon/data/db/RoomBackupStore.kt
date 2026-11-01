package com.havivon.pigeon.data.db

import androidx.room.withTransaction
import com.havivon.pigeon.domain.backup.BackupStore
import com.havivon.pigeon.domain.backup.RestorePlan
import com.havivon.pigeon.domain.model.Reminder
import java.util.Locale

/**
 * [BackupStore] over [PigeonDatabase]: an import replaces the rules, categories and moves, pins, locks, "not spam"
 * senders, blocked words, pending reminders and favorites in ONE transaction, so a failure half-way leaves the
 * data exactly as it was. Done reminders and the spam verdicts (which the filter judges again) are not touched.
 */
class RoomBackupStore(private val db: PigeonDatabase, private val now: () -> Long = System::currentTimeMillis) : BackupStore {

    override suspend fun replace(plan: RestorePlan): List<Reminder> = db.withTransaction {
        val at = now()

        val ruleDao = db.colorRules()
        ruleDao.deleteAll()
        plan.rules.forEachIndexed { position, rule -> ruleDao.insert(rule.toEntity(position)) }

        val folderDao = db.folders()
        folderDao.deleteAllOverrides()
        folderDao.deleteAllFolders()
        val folderIds = plan.categories.mapIndexed { position, name ->
            folderDao.insertFolder(CustomFolderEntity(name = name, position = position, createdAtMillis = at))
        }
        for (move in plan.moves) {
            val customId = move.categoryIndex?.let { folderIds.getOrNull(it) }
            if (move.folder == null && customId == null) continue
            folderDao.upsertOverride(FolderOverrideEntity(move.key, move.folder?.name, customId, at))
        }

        plan.pinnedThreadIds?.let { ids ->
            db.pinnedThreads().deleteAll()
            ids.forEach { db.pinnedThreads().insert(PinnedThreadEntity(it, at)) }
        }
        plan.lockedThreadIds?.let { ids ->
            db.lockedThreads().deleteAll()
            ids.forEach { db.lockedThreads().insert(LockedThreadEntity(it, at)) }
        }

        val spamDao = db.spam()
        spamDao.deleteAllNotSpam()
        plan.notSpam.forEach { spamDao.insertNotSpam(NotSpamEntity(it, at)) }

        val keywordDao = db.blockedKeywords()
        keywordDao.deleteAll()
        plan.blockedWords.forEach { keywordDao.insert(BlockedKeywordEntity(word = it, normalized = it.lowercase(Locale.ROOT), addedAtMillis = at)) }

        val inserted = plan.reminders?.let { planned ->
            val dao = db.reminders()
            dao.deletePending()
            planned.map { r ->
                val id = dao.insert(ReminderEntity(threadId = r.threadId, messageId = r.messageId, text = r.text, triggerAtMillis = r.triggerAtMillis, createdAtMillis = at))
                Reminder(id, r.threadId, r.messageId, r.text, r.triggerAtMillis, done = false)
            }
        }.orEmpty()

        plan.favorites?.let { planned ->
            val dao = db.favorites()
            dao.deleteAll()
            planned.forEach {
                dao.insert(FavoriteEntity(messageId = it.messageId, threadId = it.threadId, address = it.address, senderName = it.senderName, body = it.body, dateMillis = it.dateMillis, starredAtMillis = it.starredAtMillis))
            }
        }
        inserted
    }
}
