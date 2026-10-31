package com.havivon.pigeon.data.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager
import com.havivon.pigeon.data.AppContainer
import com.havivon.pigeon.data.parseRecipients
import kotlinx.coroutines.launch

/**
 * "Reply with a message" when declining a call. The phone app sends the chosen text here and
 * expects it to go out without any screen: this is the one place Pigeon sends without a tap in
 * Pigeon. Exported because the phone app calls it, and guarded by SEND_RESPOND_VIA_MESSAGE, which
 * only the system's phone app holds.
 */
class RespondViaMessageService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val recipients = intent?.takeIf { it.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE }
            ?.data?.takeIf { it.scheme?.lowercase() in setOf("sms", "smsto", "mms", "mmsto") }
            ?.let { parseRecipients(it.schemeSpecificPart) }
            .orEmpty()
        val text = try {
            intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        } catch (_: RuntimeException) {
            ""
        }
        if (recipients.isEmpty() || text.isEmpty() || text.length > MAX_SEND_LENGTH) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val app = AppContainer.from(this)
        // The app's scope, not the service's: stopping the service must not cut a send short
        app.appScope.launch {
            try {
                app.smsSender.send(recipients, text)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }
}
