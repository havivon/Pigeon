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

    @Query("DELETE FROM color_rules")
    suspend fun deleteAll()

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

    @Query("DELETE FROM favorites")
    suspend fun deleteAll()

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

    @Query("DELETE FROM reminders WHERE done = 0")
    suspend fun deletePending()

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

    @Query("DELETE FROM locked_threads")
    suspend fun deleteAll()

    @Query("DELETE FROM locked_threads WHERE threadId = :threadId")
    suspend fun delete(threadId: Long)
}

@Dao
interface PinnedThreadDao {
    @Query("SELECT threadId FROM pinned_threads")
    fun observeIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(pin: PinnedThreadEntity)

    @Query("DELETE FROM pinned_threads")
    suspend fun deleteAll()

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

    @Query("SELECT address FROM spam_verdicts")
    suspend fun verdictAddresses(): List<String>

    @Query("SELECT address FROM not_spam")
    suspend fun notSpamAddresses(): List<String>

    @Query("SELECT address FROM not_spam")
    fun observeNotSpam(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM not_spam WHERE address = :address)")
    suspend fun isNotSpam(address: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotSpam(entry: NotSpamEntity)

    @Query("DELETE FROM not_spam")
    suspend fun deleteAllNotSpam()

    @Query("DELETE FROM not_spam WHERE address = :address")
    suspend fun deleteNotSpam(address: String)

    /**
     * "Not spam" in one step: the verdicts on this sender go (under every way its number was
     * written, see [sameSender]) and the sender joins the allow-list.
     */
    @Transaction
    suspend fun markNotSpam(entry: NotSpamEntity) {
        sameSender(verdictAddresses(), entry.address).forEach { deleteVerdict(it) }
        insertNotSpam(entry)
    }

    /** Takes the sender off the allow-list, under every way its number was written. */
    @Transaction
    suspend fun removeNotSpam(address: String) {
        sameSender(notSpamAddresses(), address).forEach { deleteNotSpam(it) }
    }
}

@Dao
interface BlockedKeywordDao {
    @Query("SELECT * FROM blocked_keywords ORDER BY addedAtMillis, id")
    fun observeAll(): Flow<List<BlockedKeywordEntity>>

    /** -1 when the word is already on the list. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(keyword: BlockedKeywordEntity): Long

    @Query("DELETE FROM blocked_keywords")
    suspend fun deleteAll()

    @Query("DELETE FROM blocked_keywords WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM custom_folders ORDER BY position, id")
    fun observeFolders(): Flow<List<CustomFolderEntity>>

    @Query("SELECT * FROM custom_folders ORDER BY position, id")
    suspend fun folders(): List<CustomFolderEntity>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM custom_folders")
    suspend fun nextPosition(): Int

    @Insert
    suspend fun insertFolder(folder: CustomFolderEntity): Long

    @Query("DELETE FROM custom_folders")
    suspend fun deleteAllFolders()

    @Query("DELETE FROM folder_overrides")
    suspend fun deleteAllOverrides()

    @Query("UPDATE custom_folders SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE custom_folders SET argb = :argb WHERE id = :id")
    suspend fun setFolderColor(id: Long, argb: Long?)

    @Query("SELECT * FROM category_colors")
    fun observeBuiltInColors(): Flow<List<CategoryColorEntity>>

    @Upsert
    suspend fun upsertBuiltInColor(entry: CategoryColorEntity)

    @Query("DELETE FROM category_colors WHERE folder = :folder")
    suspend fun deleteBuiltInColor(folder: String)

    @Query("DELETE FROM category_colors")
    suspend fun deleteAllBuiltInColors()

    @Query("UPDATE custom_folders SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    @Query("DELETE FROM custom_folders WHERE id = :id")
    suspend fun deleteFolder(id: Long)

    @Query("DELETE FROM folder_overrides WHERE customFolderId = :folderId")
    suspend fun deleteOverridesOf(folderId: Long)

    /** A category and the moves into it, together: its conversations go back to the automatic sorting. */
    @Transaction
    suspend fun deleteFolderAndOverrides(id: Long) {
        deleteOverridesOf(id)
        deleteFolder(id)
    }

    @Transaction
    suspend fun setPositions(positions: Map<Long, Int>) {
        for ((id, position) in positions) setPosition(id, position)
    }

    @Query("SELECT * FROM folder_overrides")
    fun observeOverrides(): Flow<List<FolderOverrideEntity>>

    @Query("SELECT * FROM folder_overrides WHERE `key` = :key")
    suspend fun override(key: String): FolderOverrideEntity?

    @Upsert
    suspend fun upsertOverride(entry: FolderOverrideEntity)

    @Query("DELETE FROM folder_overrides WHERE `key` = :key")
    suspend fun deleteOverride(key: String)
}
