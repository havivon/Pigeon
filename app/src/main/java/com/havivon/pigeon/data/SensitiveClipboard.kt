package com.havivon.pigeon.data

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.widget.Toast
import androidx.annotation.StringRes

/** Copies text flagged as sensitive, so Android keeps it out of the clipboard preview, then confirms with a toast. */
object SensitiveClipboard {

    fun copy(context: Context, label: String, text: String, @StringRes confirmation: Int) {
        val clip = ClipData.newPlainText(label, text)
        clip.description.extras = PersistableBundle().apply {
            putBoolean(if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE", true)
        }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        // Android 13+ shows its own "copied" confirmation
        if (Build.VERSION.SDK_INT < 33) {
            Handler(Looper.getMainLooper()).post { Toast.makeText(context, confirmation, Toast.LENGTH_SHORT).show() }
        }
    }
}
