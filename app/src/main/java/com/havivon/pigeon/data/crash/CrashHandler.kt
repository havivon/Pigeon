package com.havivon.pigeon.data.crash

import android.content.Context
import android.os.Build
import com.havivon.pigeon.domain.crash.CrashHeader
import com.havivon.pigeon.domain.crash.CrashReport
import com.havivon.pigeon.domain.crash.CrashStore
import java.io.File

/**
 * Crash reports without a network: an uncaught exception is written, sanitized, to the app's private storage, and
 * the user can share it by hand at the next launch. Nothing is ever sent by the app itself.
 */
object CrashHandler {
    @Volatile private var store: CrashStore? = null

    fun store(context: Context): CrashStore =
        store ?: synchronized(this) { store ?: CrashStore(File(context.applicationContext.filesDir, "crash")).also { store = it } }

    fun install(context: Context) {
        val app = context.applicationContext
        val crashes = store(app)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                crashes.save(CrashReport.build(header(app), thread.name, error))
            } catch (_: Throwable) {
                // The report is a courtesy: never let it hide the crash itself
            }
            previous?.uncaughtException(thread, error)
        }
    }

    @Suppress("DEPRECATION")
    private fun header(context: Context): CrashHeader {
        val version = try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${info.versionCode})"
        } catch (_: Exception) {
            "unknown"
        }
        return CrashHeader(
            appVersion = version,
            androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            timeMillis = System.currentTimeMillis(),
        )
    }
}
