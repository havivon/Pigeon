package com.havivon.pigeon.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's own data, in its private storage (`/data/data/<package>/databases`). It goes into
 * Android's encrypted backup with the rest of the app's files, as in the official apps.
 */
@Database(
    entities = [
        ColorRuleEntity::class,
        FavoriteEntity::class,
        ReminderEntity::class,
        LockedThreadEntity::class,
        SpamVerdictEntity::class,
        NotSpamEntity::class,
        BlockedKeywordEntity::class,
        PinnedThreadEntity::class,
    ],
    version = 2,
    exportSchema = true,
    // 2: pinned conversations (a new table, nothing else changes)
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class PigeonDatabase : RoomDatabase() {
    abstract fun colorRules(): ColorRuleDao
    abstract fun favorites(): FavoriteDao
    abstract fun reminders(): ReminderDao
    abstract fun lockedThreads(): LockedThreadDao
    abstract fun spam(): SpamDao
    abstract fun blockedKeywords(): BlockedKeywordDao
    abstract fun pinnedThreads(): PinnedThreadDao

    companion object {
        private const val NAME = "pigeon.db"

        fun build(context: Context): PigeonDatabase =
            Room.databaseBuilder(context.applicationContext, PigeonDatabase::class.java, NAME).build()
    }
}
