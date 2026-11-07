package com.havivon.pigeon.data.blocking

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers
import android.util.Log
import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.domain.blocking.SeenBlockedMemory
import com.havivon.pigeon.domain.blocking.SystemBlockList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext

private const val TAG = "BlockedNumbers"

/**
 * [SystemBlockList] over `BlockedNumberContract`. Pigeon may use it only as the default SMS app, and only when
 * [BlockedNumberContract.canCurrentUserBlockNumbers] says this user of the phone may block; otherwise the list
 * reads as null and nothing is written. The system throws `SecurityException` when it disagrees (the role was
 * just taken away): that is caught and treated the same. The numbers are never logged.
 *
 * It is read again whenever the system reports a change (a [ContentObserver]) and whenever [refresh] changes
 * (Pigeon became, or stopped being, the default SMS app).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidSystemBlockList(
    context: Context,
    private val canWrite: () -> Boolean,
    refresh: Flow<Int>,
    scope: CoroutineScope,
) : SystemBlockList {

    private val app = context.applicationContext
    private val resolver = app.contentResolver

    private fun allowed(): Boolean = canWrite() && try {
        BlockedNumberContract.canCurrentUserBlockNumbers(app)
    } catch (e: RuntimeException) {
        false
    }

    override val numbers: Flow<List<String>?> = refresh.flatMapLatest {
        callbackFlow {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    trySend(Unit)
                }
            }
            val registered = try {
                resolver.registerContentObserver(BlockedNumbers.CONTENT_URI, true, observer)
                true
            } catch (e: RuntimeException) {
                Log.w(TAG, "Observer failed: ${e.javaClass.name}")
                false
            }
            trySend(Unit)
            awaitClose { if (registered) resolver.unregisterContentObserver(observer) }
        }.conflate().map { read() }
    }.flowOn(Dispatchers.IO).shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private data class Entry(val id: Long, val original: String, val e164: String)

    private fun entries(): List<Entry>? {
        if (!allowed()) return null
        return try {
            resolver.query(BlockedNumbers.CONTENT_URI, arrayOf(BlockedNumbers.COLUMN_ID, BlockedNumbers.COLUMN_ORIGINAL_NUMBER, BlockedNumbers.COLUMN_E164_NUMBER), null, null, null)
                ?.use { c ->
                    val out = ArrayList<Entry>(c.count)
                    val id = c.getColumnIndexOrThrow(BlockedNumbers.COLUMN_ID)
                    val original = c.getColumnIndexOrThrow(BlockedNumbers.COLUMN_ORIGINAL_NUMBER)
                    val e164 = c.getColumnIndexOrThrow(BlockedNumbers.COLUMN_E164_NUMBER)
                    while (c.moveToNext()) out += Entry(c.getLong(id), c.getString(original).orEmpty(), c.getString(e164).orEmpty())
                    out
                }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Read failed: ${e.javaClass.name}")
            null
        }
    }

    private fun read(): List<String>? = entries()?.map { it.original.ifBlank { it.e164 } }?.filter { it.isNotBlank() }

    private fun Entry.same(number: String) = PhoneNumbers.same(original, number) || (e164.isNotBlank() && PhoneNumbers.same(e164, number))

    override suspend fun block(number: String): Boolean = withContext(Dispatchers.IO) {
        val list = entries() ?: return@withContext false
        if (list.any { it.same(number) }) return@withContext true
        try {
            resolver.insert(BlockedNumbers.CONTENT_URI, ContentValues().apply { put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number.trim()) }) != null
        } catch (e: RuntimeException) {
            Log.w(TAG, "Block failed: ${e.javaClass.name}")
            false
        }
    }

    override suspend fun unblock(number: String): Int = withContext(Dispatchers.IO) {
        val list = entries() ?: return@withContext 0
        list.filter { it.same(number) }.sumOf { e ->
            try {
                resolver.delete(ContentUris.withAppendedId(BlockedNumbers.CONTENT_URI, e.id), null, null)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Unblock failed: ${e.javaClass.name}")
                0
            }
        }
    }
}

/**
 * What [com.havivon.pigeon.domain.blocking.PhoneAwareSpamRepository.sync] saw last, in the app's private
 * preferences: the blocked numbers' lookup keys (their last digits), as the spam verdicts keep senders.
 */
class SeenBlockedPrefs(context: Context) : SeenBlockedMemory {

    private val prefs = context.applicationContext.getSharedPreferences("blocked_numbers_seen", Context.MODE_PRIVATE)

    override fun load(): Set<String>? = if (prefs.contains(KEY)) prefs.getStringSet(KEY, emptySet())?.toSet() else null

    override fun save(keys: Set<String>) {
        prefs.edit().putStringSet(KEY, keys).apply()
    }

    private companion object {
        const val KEY = "keys"
    }
}
