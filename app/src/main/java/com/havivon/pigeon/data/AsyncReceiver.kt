package com.havivon.pigeon.data

import android.content.BroadcastReceiver
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Work a broadcast receiver hands off, so it can finish its database writes after onReceive returns. */
private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Runs [block] off the main thread and keeps the broadcast alive until it ends (goAsync). A failure
 * is logged by its type only: the exception message could quote a message's text.
 */
internal fun BroadcastReceiver.runAsync(tag: String, block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            block()
        } catch (e: Exception) {
            Log.w(tag, "Failed: ${e.javaClass.name}")
        } finally {
            pending.finish()
        }
    }
}
