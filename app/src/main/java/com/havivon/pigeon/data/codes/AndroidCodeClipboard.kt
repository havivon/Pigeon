package com.havivon.pigeon.data.codes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.havivon.pigeon.R
import com.havivon.pigeon.data.AppContainer
import com.havivon.pigeon.data.SensitiveClipboard
import com.havivon.pigeon.domain.codes.CodeClipboard
import com.havivon.pigeon.domain.codes.PendingClear
import com.havivon.pigeon.domain.codes.PendingClearStore
import java.util.concurrent.TimeUnit

/** The extra on the clip's description that marks Pigeon's own copy of a code (a hash, see `CodeAutoCopy.tagOf`). */
private const val TAG_EXTRA = "com.havivon.pigeon.CODE_TAG"

/**
 * [CodeClipboard] on Android's clipboard. The copy is flagged sensitive (hidden from the clipboard preview) and
 * carries its tag on the description, so it is recognised later from the description alone: the text is never
 * read back.
 */
class AndroidCodeClipboard(context: Context) : CodeClipboard {

    private val app = context.applicationContext
    private val manager: ClipboardManager get() = app.getSystemService(ClipboardManager::class.java)

    override fun copy(code: String, tag: String) {
        try {
            SensitiveClipboard.copySilently(app, app.getString(R.string.widget_code_kind), code, mapOf(TAG_EXTRA to tag))
        } catch (_: RuntimeException) {
            // A clipboard that refuses: the code is still in the notification
        }
    }

    override fun currentTag(): String? = try {
        // Null while Pigeon is not on screen (Android 10+), or when the clipboard is empty
        manager.primaryClipDescription?.let { it.extras?.getString(TAG_EXTRA).orEmpty() }
    } catch (_: RuntimeException) {
        null
    }

    override fun clear() {
        try {
            if (Build.VERSION.SDK_INT >= 28) manager.clearPrimaryClip() else manager.setPrimaryClip(ClipData.newPlainText("", ""))
        } catch (_: RuntimeException) {
            // Not allowed now: Android clears it on its own later
        }
    }
}

/** [PendingClearStore] in the app's private preferences: a hash and a time, never the code. */
class PendingClearPrefs(context: Context) : PendingClearStore {

    private val prefs = context.applicationContext.getSharedPreferences("code_clipboard", Context.MODE_PRIVATE)

    override fun load(): PendingClear? {
        val tag = prefs.getString(KEY_TAG, null) ?: return null
        return PendingClear(tag, prefs.getLong(KEY_AT, 0))
    }

    override fun save(value: PendingClear?) {
        prefs.edit().apply {
            if (value == null) clear() else putString(KEY_TAG, value.tag).putLong(KEY_AT, value.clearAtMillis)
        }.apply()
    }

    private companion object {
        const val KEY_TAG = "tag"
        const val KEY_AT = "clear_at"
    }
}

/** Clears the copied code when it is due (`CodeAutoCopy.clearIfDue`). */
class CodeClipboardWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        AppContainer.from(applicationContext).codeAutoCopy.clearIfDue()
        return Result.success()
    }

    companion object {
        private const val NAME = "code-clipboard-clear"

        /** One run at about [atMillis]; a newer code replaces the run of the one before. */
        fun schedule(context: Context, atMillis: Long) {
            val delay = (atMillis - System.currentTimeMillis()).coerceAtLeast(0)
            val request = OneTimeWorkRequestBuilder<CodeClipboardWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
