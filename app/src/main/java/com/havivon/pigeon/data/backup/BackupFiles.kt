package com.havivon.pigeon.data.backup

import android.content.Context
import android.net.Uri
import com.havivon.pigeon.domain.backup.BackupLimits
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads and writes the settings file the user picked with the system's file picker (the Storage Access
 * Framework: no storage permission, and Pigeon never sees any other file).
 */
object BackupFiles {

    /** False when the file could not be written. */
    suspend fun write(context: Context, uri: Uri, json: String): Boolean = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) } != null
    }

    /**
     * The file's bytes, or null when it cannot be opened. A file over [BackupLimits.MAX_FILE_BYTES] comes
     * back one byte over the limit, never in full: the parser then refuses it as too large.
     */
    suspend fun read(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { readAtMost(it, BackupLimits.MAX_FILE_BYTES + 1) }
    }

    /** Up to [limit] bytes of [input]. */
    internal fun readAtMost(input: InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (out.size() < limit) {
            val n = input.read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
