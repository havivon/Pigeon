package com.havivon.pigeon.data.reminders

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import com.havivon.pigeon.domain.model.Reminder
import com.havivon.pigeon.domain.reminders.ReminderPlan
import com.havivon.pigeon.domain.seam.ReminderScheduler

/**
 * [ReminderScheduler] with AlarmManager: one alarm per reminder, whose PendingIntent (an explicit,
 * not exported receiver) is the same for the same reminder, so scheduling again replaces it and
 * [cancel] finds it.
 *
 * The alarm is exact (`setExactAndAllowWhileIdle`) when the user allows exact alarms. Android 12+
 * asks for that in the special-access settings (SCHEDULE_EXACT_ALARM; USE_EXACT_ALARM is for alarm
 * clock and calendar apps, which a messaging app is not), and Android 14 leaves it off by default.
 * Without it the alarm is `setAndAllowWhileIdle`: still fires in doze, up to a few minutes late.
 */
class AlarmReminderScheduler(private val context: Context) : ReminderScheduler {

    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun schedule(reminder: Reminder) {
        val pending = ReminderReceiver.fireIntent(context, reminder.id)
        val at = reminder.triggerAtMillis
        when (ReminderPlan.precision(canScheduleExact(context))) {
            ReminderPlan.Precision.Exact -> try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } catch (_: SecurityException) {
                // The permission was withdrawn after the check
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
            ReminderPlan.Precision.Inexact -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    override fun cancel(reminderId: Long) {
        alarms.cancel(ReminderReceiver.fireIntent(context, reminderId))
    }

    companion object {
        /** Below Android 12 exact alarms need no permission. */
        fun canScheduleExact(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }
}
