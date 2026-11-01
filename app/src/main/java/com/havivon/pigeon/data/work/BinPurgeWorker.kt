package com.havivon.pigeon.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.havivon.pigeon.data.AppContainer
import java.util.concurrent.TimeUnit

/**
 * Deletes for good what has been in the recycle bin for 30 days. Runs once a day; the bin is the app's own
 * database, so it needs no default-app role.
 */
class BinPurgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        AppContainer.from(applicationContext).recycleBin.purgeExpired()
        return Result.success()
    }

    companion object {
        private const val NAME = "bin-purge"

        /** Safe to call on every app start: an existing schedule is kept. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BinPurgeWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
