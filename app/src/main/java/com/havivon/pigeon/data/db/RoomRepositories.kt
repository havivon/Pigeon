package com.havivon.pigeon.data.db

import androidx.room.withTransaction
import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.domain.model.BlockedKeyword
import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.Favorite
import com.havivon.pigeon.domain.model.Reminder
import com.havivon.pigeon.domain.model.RuleMatch
import com.havivon.pigeon.domain.model.SpamReason
import com.havivon.pigeon.domain.model.SpamVerdict
import com.havivon.pigeon.domain.repository.BlockedKeywordsRepository
import com.havivon.pigeon.domain.repository.FavoritesRepository
import com.havivon.pigeon.domain.repository.LocksRepository
import com.havivon.pigeon.domain.repository.PinsRepository
import com.havivon.pigeon.domain.repository.RemindersRepository
import com.havivon.pigeon.domain.repository.RulesRepository
import com.havivon.pigeon.domain.repository.SpamRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// The repositories over PigeonDatabase. `now` is a parameter so the times can be fixed in a test.

class RoomRulesRepository(private val db: PigeonDatabase) : RulesRepository {
    private val dao = db.colorRules()

    override val rules: Flow<List<ColorRule>> = dao.observeAll().map { list -> list.map { it.toRule() } }

    override suspend fun add(label: String, match: RuleMatch, argb: Long, enabled: Boolean): Int = db.withTransaction {
        val rule = ColorRule(id = 0, label = label, match = match, argb = argb, enabled = enabled)
        dao.insert(rule.toEntity(position = dao.nextPosition())).toInt()
    }

    override suspend fun update(rule: ColorRule) {
        val e = rule.toEntity(position = 0)
        dao.updateContent(e.id, e.label, e.kind, e.value, e.argb, e.enabled)
    }

    override suspend fun delete(id: Int) = dao.delete(id)

    override suspend fun reorder(idsInOrder: List<Int>) = db.withTransaction {
        dao.setPositions(reorderedPositions(dao.all().map { it.id }, idsInOrder))
    }
}

class RoomFavoritesRepository(db: PigeonDatabase, private val now: () -> Long = System::currentTimeMillis) : FavoritesRepository {
    private val dao = db.favorites()

    override val favorites: Flow<List<Favorite>> = dao.observeAll().map { list ->
        list.map { Favorite(it.id, it.messageId, it.threadId, it.address, it.senderName, it.body, it.dateMillis, it.starredAtMillis) }
    }

    override val favoriteMessageIds: Flow<Set<Long>> = dao.observeMessageIds().map { it.toSet() }

    override suspend fun star(messageId: Long, threadId: Long, address: String, senderName: String?, body: String, dateMillis: Long) {
        val existing = dao.byMessage(messageId)
        val entity = FavoriteEntity(
            id = existing?.id ?: 0,
            messageId = messageId,
            threadId = threadId,
            address = address,
            senderName = senderName,
            body = body,
            dateMillis = dateMillis,
            starredAtMillis = existing?.starredAtMillis ?: now(),
        )
        if (existing == null) dao.insert(entity) else dao.update(entity)
    }

    override suspend fun unstar(messageId: Long) = dao.deleteByMessage(messageId)
}

class RoomRemindersRepository(db: PigeonDatabase, private val now: () -> Long = System::currentTimeMillis) : RemindersRepository {
    private val dao = db.reminders()

    override val pending: Flow<List<Reminder>> = dao.observePending().map { list -> list.map { it.toReminder() } }

    override suspend fun add(threadId: Long, messageId: Long?, text: String, triggerAtMillis: Long): Long =
        dao.insert(ReminderEntity(threadId = threadId, messageId = messageId, text = text, triggerAtMillis = triggerAtMillis, createdAtMillis = now()))

    override suspend fun get(id: Long): Reminder? = dao.byId(id)?.toReminder()

    override suspend fun setDone(id: Long, done: Boolean) = dao.setDone(id, done)

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun allPending(): List<Reminder> = dao.pending().map { it.toReminder() }

    private fun ReminderEntity.toReminder() = Reminder(id, threadId, messageId, text, triggerAtMillis, done)
}

class RoomSpamRepository(db: PigeonDatabase, private val now: () -> Long = System::currentTimeMillis) : SpamRepository {
    private val dao = db.spam()

    override val verdicts: Flow<List<SpamVerdict>> = dao.observeVerdicts().map { list -> list.map { it.toVerdict() } }

    override val notSpam: Flow<Set<String>> = dao.observeNotSpam().map { it.toSet() }

    override suspend fun verdictFor(address: String): SpamVerdict? = dao.verdict(PhoneNumbers.normalize(address))?.toVerdict()

    override suspend fun markSpam(address: String, threadId: Long, reasons: List<SpamReason>): Boolean {
        val key = PhoneNumbers.normalize(address)
        if (key.isEmpty() || dao.isNotSpam(key)) return false
        dao.upsertVerdict(SpamVerdictEntity(key, threadId, SpamReasonCodec.encode(reasons), now()))
        return true
    }

    override suspend fun markNotSpam(address: String) {
        val key = PhoneNumbers.normalize(address)
        if (key.isNotEmpty()) dao.markNotSpam(NotSpamEntity(key, now()))
    }

    override suspend fun isNotSpam(address: String): Boolean = dao.isNotSpam(PhoneNumbers.normalize(address))

    override suspend fun removeFromNotSpam(address: String) = dao.deleteNotSpam(PhoneNumbers.normalize(address))

    private fun SpamVerdictEntity.toVerdict() = SpamVerdict(address, threadId, SpamReasonCodec.decode(reasons), judgedAtMillis)
}

class RoomLocksRepository(db: PigeonDatabase, private val now: () -> Long = System::currentTimeMillis) : LocksRepository {
    private val dao = db.lockedThreads()

    override val lockedThreadIds: Flow<Set<Long>> = dao.observeIds().map { it.toSet() }

    override suspend fun isLocked(threadId: Long): Boolean = dao.isLocked(threadId)

    override suspend fun lockedNow(): Set<Long> = dao.ids().toSet()

    override suspend fun lock(threadId: Long) = dao.insert(LockedThreadEntity(threadId, now()))

    override suspend fun unlock(threadId: Long) = dao.delete(threadId)
}

class RoomPinsRepository(db: PigeonDatabase, private val now: () -> Long = System::currentTimeMillis) : PinsRepository {
    private val dao = db.pinnedThreads()

    override val pinnedThreadIds: Flow<Set<Long>> = dao.observeIds().map { it.toSet() }

    override suspend fun pin(threadId: Long) = dao.insert(PinnedThreadEntity(threadId, now()))

    override suspend fun unpin(threadId: Long) = dao.delete(threadId)
}

class RoomBlockedKeywordsRepository(db: PigeonDatabase, private val now: () -> Long = System::currentTimeMillis) : BlockedKeywordsRepository {
    private val dao = db.blockedKeywords()

    override val keywords: Flow<List<BlockedKeyword>> = dao.observeAll().map { list -> list.map { BlockedKeyword(it.id, it.word) } }

    override suspend fun add(word: String): Boolean {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return false
        return dao.insert(BlockedKeywordEntity(word = trimmed, normalized = trimmed.lowercase(), addedAtMillis = now())) != -1L
    }

    override suspend fun delete(id: Long) = dao.delete(id)
}
