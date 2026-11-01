package com.havivon.pigeon.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ColorRuleDao {
    @Query("SELECT * FROM color_rules ORDER BY position, id")
    fun observeAll(): Flow<List<ColorRuleEntity>>

    @Query("SELECT * FROM color_rules ORDER BY position, id")
    suspend fun all(): List<ColorRuleEntity>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM color_rules")
    suspend fun nextPosition(): Int

    @Insert
    suspend fun insert(rule: ColorRuleEntity): Long

    @Query("UPDATE color_rules SET label = :label, kind = :kind, value = :value, argb = :argb, enabled = :enabled WHERE id = :id")
    suspend fun updateContent(id: Int, label: String, kind: String, value: String, argb: Long, enabled: Boolean)

    @Query("UPDATE color_rules SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Int, position: Int)

    @Query("DELETE FROM color_rules WHERE id = :id")
    suspend fun delete(id: Int)

    @Transaction
    suspend fun setPositions(positions: Map<Int, Int>) {
        for ((id, position) in positions) setPosition(id, position)
    }
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY starredAtMillis DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT messageId FROM favorites")
    fun observeMessageIds(): Flow<List<Long>>

    @Query("SELECT messageId FROM favorites")
    suspend fun messageIds(): List<Long>

    @Query("SELECT * FROM favorites WHERE messageId = :messageId")
    suspend fun byMessage(messageId: Long): FavoriteEntity?

    @Insert
    suspend fun insert(favorite: FavoriteEntity): Long

    @Update
    suspend fun update(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE messageId = :messageId")
    suspend fun deleteByMessage(messageId: Long)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE done = 0 ORDER BY triggerAtMillis")
    fun observePending(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE done = 0 ORDER BY triggerAtMillis")
    suspend fun pending(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun byId(id: Long): ReminderEntity?

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Query("UPDATE reminders SET done = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface LockedThreadDao {
    @Query("SELECT threadId FROM locked_threads")
    fun observeIds(): Flow<List<Long>>

    @Query("SELECT threadId FROM locked_threads")
    suspend fun ids(): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM locked_threads WHERE threadId = :threadId)")
    suspend fun isLocked(threadId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(lock: LockedThreadEntity)

    @Query("DELETE FROM locked_threads WHERE threadId = :threadId")
    suspend fun delete(threadId: Long)
}

@Dao
interface PinnedThreadDao {
    @Query("SELECT threadId FROM pinned_threads")
    fun observeIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(pin: PinnedThreadEntity)

    @Query("DELETE FROM pinned_threads WHERE threadId = :threadId")
    suspend fun delete(threadId: Long)
}

@Dao
interface SpamDao {
    @Query("SELECT * FROM spam_verdicts ORDER BY judgedAtMillis DESC")
    fun observeVerdicts(): Flow<List<SpamVerdictEntity>>

    @Query("SELECT * FROM spam_verdicts WHERE address = :address")
    suspend fun verdict(address: String): SpamVerdictEntity?

    @Upsert
    suspend fun upsertVerdict(verdict: SpamVerdictEntity)

    @Query("DELETE FROM spam_verdicts WHERE address = :address")
    suspend fun deleteVerdict(address: String)

    @Query("SELECT address FROM not_spam")
    fun observeNotSpam(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM not_spam WHERE address = :address)")
    suspend fun isNotSpam(address: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotSpam(entry: NotSpamEntity)

    @Query("DELETE FROM not_spam WHERE address = :address")
    suspend fun deleteNotSpam(address: String)

    /** "Not spam" in one step: the verdict goes and the sender joins the allow-list. */
    @Transaction
    suspend fun markNotSpam(entry: NotSpamEntity) {
        deleteVerdict(entry.address)
        insertNotSpam(entry)
    }
}

@Dao
interface BlockedKeywordDao {
    @Query("SELECT * FROM blocked_keywords ORDER BY addedAtMillis, id")
    fun observeAll(): Flow<List<BlockedKeywordEntity>>

    /** -1 when the word is already on the list. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(keyword: BlockedKeywordEntity): Long

    @Query("DELETE FROM blocked_keywords WHERE id = :id")
    suspend fun delete(id: Long)
}
