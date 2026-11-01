package com.havivon.pigeon.data.reminders

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.havivon.pigeon.data.AppContainer
import com.havivon.pigeon.data.runAsync

/**
 * The reminder alarm going off, and the Done and Snooze buttons of its notification. Not exported:
 * only the app's own PendingIntents reach it, so the reminder id in the extras is the app's own.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_REMINDER_ID, 0L)
        if (id <= 0) return
        val notifier = AppContainer.from(context).reminderNotifier
        when (intent.action) {
            ACTION_FIRE -> runAsync(TAG) { notifier.fire(id) }
            ACTION_DONE -> runAsync(TAG) { notifier.done(id) }
            ACTION_SNOOZE -> runAsync(TAG) { notifier.snooze(id) }
        }
    }

    companion object {
        private const val TAG = "ReminderReceiver"

        const val ACTION_FIRE = "com.havivon.pigeon.action.REMINDER_FIRE"
        const val ACTION_DONE = "com.havivon.pigeon.action.REMINDER_DONE"
        const val ACTION_SNOOZE = "com.havivon.pigeon.action.REMINDER_SNOOZE"
        const val EXTRA_REMINDER_ID = "reminder_id"

        fun fireIntent(context: Context, reminderId: Long) = pending(context, ACTION_FIRE, "fire", reminderId)
        fun doneIntent(context: Context, reminderId: Long) = pending(context, ACTION_DONE, "done", reminderId)
        fun snoozeIntent(context: Context, reminderId: Long) = pending(context, ACTION_SNOOZE, "snooze", reminderId)

        /** One distinct PendingIntent per action and reminder (the data makes it distinct), so none replaces another. */
        private fun pending(context: Context, action: String, name: String, reminderId: Long): PendingIntent {
            val intent = Intent(context, ReminderReceiver::class.java)
                .setAction(action)
                .setData(Uri.parse("pigeon://reminder/$reminderId/$name"))
                .putExtra(EXTRA_REMINDER_ID, reminderId)
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}

/**
 * Puts the alarms back after something cleared them: a reboot, an app update, a change of the
 * clock or time zone, or the exact-alarm permission being granted. Not exported: the system still
 * delivers those broadcasts, other apps cannot. It accepts only those actions and uses nothing
 * from the intent: what to schedule comes from the app's own database, so the work is harmless to repeat.
 */
class ReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val notifier = AppContainer.from(context).reminderNotifier
        runAsync("ReminderBoot") { notifier.restoreAll() }
    }

    companion object {
        const val ACTION_EXACT_ALARM_PERMISSION_CHANGED = "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"

        private val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            ACTION_EXACT_ALARM_PERMISSION_CHANGED,
        )
    }
}
