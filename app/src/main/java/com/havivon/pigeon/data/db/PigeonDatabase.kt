package com.havivon.pigeon.data.db

import android.content.Context
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
    ],
    version = 1,
    exportSchema = true,
)
abstract class PigeonDatabase : RoomDatabase() {
    abstract fun colorRules(): ColorRuleDao
    abstract fun favorites(): FavoriteDao
    abstract fun reminders(): ReminderDao
    abstract fun lockedThreads(): LockedThreadDao
    abstract fun spam(): SpamDao
    abstract fun blockedKeywords(): BlockedKeywordDao

    companion object {
        private const val NAME = "pigeon.db"

        fun build(context: Context): PigeonDatabase =
            Room.databaseBuilder(context.applicationContext, PigeonDatabase::class.java, NAME).build()
    }
}
