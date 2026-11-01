package com.havivon.pigeon.data.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.RemoteInput
import com.havivon.pigeon.R
import com.havivon.pigeon.data.AppContainer
import com.havivon.pigeon.data.SensitiveClipboard
import com.havivon.pigeon.data.runAsync
import com.havivon.pigeon.data.sms.MAX_SEND_LENGTH

/**
 * The buttons on a message notification. Not exported: only the app's own PendingIntents reach
 * it, so the thread id and address in the extras are the app's own.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, 0L)
        if (threadId <= 0) return
        val app = AppContainer.from(context)
        when (intent.action) {
            ACTION_REPLY -> runAsync(TAG) {
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()?.trim().orEmpty()
                val address = intent.getStringExtra(EXTRA_ADDRESS).orEmpty()
                if (text.isNotEmpty() && text.length <= MAX_SEND_LENGTH && address.isNotEmpty()) {
                    app.smsSender.send(listOf(address), text)
                    app.messages.markThreadRead(threadId)
                }
                // Always re-post: until the notification is updated, the system keeps a spinner on the reply field
                app.notifier.showReplied(threadId)
                app.widgets.updateNow()
            }
            ACTION_MARK_READ -> runAsync(TAG) {
                app.notifier.cancel(threadId)
                app.messages.markThreadRead(threadId)
                app.widgets.updateNow()
            }
            ACTION_COPY_CODE -> {
                val code = intent.getStringExtra(EXTRA_CODE)?.take(MAX_CODE_LENGTH)
                if (!code.isNullOrEmpty()) {
                    SensitiveClipboard.copy(context, context.getString(R.string.widget_code_kind), code, R.string.widget_code_copied)
                }
            }
        }
    }

    companion object {
        private const val TAG = "NotificationAction"
        private const val MAX_CODE_LENGTH = 20

        const val ACTION_REPLY = "com.havivon.pigeon.action.REPLY"
        const val ACTION_MARK_READ = "com.havivon.pigeon.action.MARK_READ"
        const val ACTION_COPY_CODE = "com.havivon.pigeon.action.COPY_CODE"
        const val KEY_REPLY = "reply_text"
        const val EXTRA_THREAD_ID = "thread_id"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_CODE = "code"

        /** One distinct intent per action and thread (the data makes it distinct), so PendingIntents never overwrite each other. */
        fun intent(context: Context, action: String, threadId: Long): Intent =
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(action)
                .setData(Uri.parse("pigeon://thread/$threadId"))
                .putExtra(EXTRA_THREAD_ID, threadId)
    }
}
