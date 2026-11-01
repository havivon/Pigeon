package com.havivon.pigeon.data.sms

import android.content.ContentResolver
import android.database.ContentObserver
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.net.Uri
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/*
 * Thin helpers over ContentResolver, shared by the SMS and contacts code. Providers differ between
 * phone makers and fail when a permission is missing, so every read here returns empty instead of
 * throwing: a screen without permission shows nothing rather than crashing.
 */

/** How long a change waits before it is reported: one SMS stored is several writes, which then come as one. */
internal const val SETTLE_MILLIS = 300L

/**
 * Emits once at once, then after changes under any of [uris]. The changes of the next [settleMillis]
 * after one (a message stored, marked seen, its thread updated) are reported together, so a burst is
 * read once; and it never waits longer than that, however busy the provider is. While the collector
 * is still busy with the last one, the next ones wait as one.
 */
internal fun ContentResolver.changes(vararg uris: Uri, settleMillis: Long = SETTLE_MILLIS): Flow<Unit> = callbackFlow {
    val lock = Any()
    var pending: Job? = null
    val observer = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            synchronized(lock) {
                if (pending?.isActive == true) return
                pending = launch {
                    delay(settleMillis)
                    trySend(Unit)
                }
            }
        }
    }
    for (uri in uris) {
        try {
            registerContentObserver(uri, true, observer)
        } catch (_: SecurityException) {
            // No permission for this provider: there is nothing to watch
        }
    }
    trySend(Unit)
    awaitClose { unregisterContentObserver(observer) }
}.conflate()

/** Runs a query and maps each row; empty when the provider refuses (no permission) or does not know a column. */
internal inline fun <T> ContentResolver.queryList(
    uri: Uri,
    projection: Array<String>,
    selection: String? = null,
    args: Array<String>? = null,
    sortOrder: String? = null,
    map: (Cursor) -> T?,
): List<T> = try {
    query(uri, projection, selection, args, sortOrder)?.use { c ->
        buildList { while (c.moveToNext()) map(c)?.let(::add) }
    } ?: emptyList()
} catch (_: SecurityException) {
    emptyList()
} catch (_: SQLiteException) {
    emptyList()
} catch (_: IllegalArgumentException) {
    emptyList()
}

internal fun Cursor.long(column: String): Long = getColumnIndex(column).let { if (it < 0 || isNull(it)) 0L else getLong(it) }

internal fun Cursor.int(column: String): Int = getColumnIndex(column).let { if (it < 0 || isNull(it)) 0 else getInt(it) }

internal fun Cursor.intOrNull(column: String): Int? = getColumnIndex(column).let { if (it < 0 || isNull(it)) null else getInt(it) }

internal fun Cursor.string(column: String): String = getColumnIndex(column).let { if (it < 0 || isNull(it)) "" else getString(it) }

/** "?, ?, ?" for an IN clause with [count] arguments. */
internal fun placeholders(count: Int): String = List(count) { "?" }.joinToString(", ")
