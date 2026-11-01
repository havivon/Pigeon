package com.havivon.pigeon.data.work

import android.content.Context
import android.provider.Telephony
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.havivon.pigeon.data.AppContainer
import com.havivon.pigeon.data.DefaultSmsRole
import com.havivon.pigeon.data.sms.long
import com.havivon.pigeon.data.sms.placeholders
import com.havivon.pigeon.data.sms.queryList
import com.havivon.pigeon.data.sms.string
import com.havivon.pigeon.domain.model.SmsType
import com.havivon.pigeon.domain.seam.CodeFinder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/** Codes are deleted once they are this old. */
const val CODE_MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L

/** A candidate row for the cleanup: only what is needed to decide. */
data class CodeCandidate(val id: Long, val body: String, val dateMillis: Long, val address: String = "")

/**
 * Which messages the cleanup deletes: incoming ones received after the feature started counting
 * ([sinceMillis]), at least [CODE_MAX_AGE_MILLIS] old, that hold a code, that the user has not
 * starred, and that do not come from one of the user's contacts ([fromContact]). Codes come from
 * services; a friend's "the gate code is 4590" is a personal message and is never deleted.
 */
fun codesToDelete(
    candidates: List<CodeCandidate>,
    codeFinder: CodeFinder,
    nowMillis: Long,
    sinceMillis: Long,
    starred: Set<Long>,
    fromContact: (String) -> Boolean = { false },
): List<Long> =
    candidates.filter { c ->
        c.dateMillis >= sinceMillis && c.dateMillis <= nowMillis - CODE_MAX_AGE_MILLIS && c.id !in starred &&
            codeFinder.find(c.body) != null && !fromContact(c.address)
    }.map { it.id }

/**
 * Deletes one-time codes 24 hours after they arrive, when the setting is on (the default), except
 * those from the user's contacts and starred ones. It
 * only counts from when the feature first ran (or was turned on again), so the user's older
 * history is never wiped in one go. Runs about once an hour; does nothing unless Pigeon is the
 * default SMS app, the only app allowed to delete.
 */
class CodeCleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = AppContainer.from(applicationContext)
        val settings = app.settings.settings.first()
        if (!settings.autoDeleteCodes || !DefaultSmsRole.isDefault(applicationContext)) return Result.success()
        val now = System.currentTimeMillis()
        val since = settings.autoDeleteCodesSinceMillis ?: run {
            app.settings.setAutoDeleteCodesSince(now)
            return Result.success()
        }
        val resolver = applicationContext.contentResolver
        val candidates = resolver.queryList(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.ADDRESS),
            "${Telephony.Sms.TYPE} = ${SmsType.Inbox.providerValue} AND ${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} <= ?",
            arrayOf(since.toString(), (now - CODE_MAX_AGE_MILLIS).toString()),
        ) { CodeCandidate(it.long(Telephony.Sms._ID), it.string(Telephony.Sms.BODY), it.long(Telephony.Sms.DATE), it.string(Telephony.Sms.ADDRESS)) }
        val contacts = candidates.map { it.address }.distinct().filter { app.contacts.displayName(it) != null }.toSet()
        val ids = codesToDelete(candidates, app.codeFinder, now, since, app.favorites.favoriteMessageIds.first()) { it in contacts }
        try {
            ids.chunked(500).forEach { chunk ->
                resolver.delete(Telephony.Sms.CONTENT_URI, "${Telephony.Sms._ID} IN (${placeholders(chunk.size)})", chunk.map { it.toString() }.toTypedArray())
            }
        } catch (_: SecurityException) {
            // The default SMS app changed while it ran
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "code-cleanup"

        /** Safe to call on every app start: an existing schedule is kept. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CodeCleanupWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
