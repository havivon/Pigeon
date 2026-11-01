package com.havivon.pigeon.domain.reminders

import com.havivon.pigeon.domain.model.Reminder

/** What to do with the pending reminders after the alarms were lost (reboot, app update, clock change). */
data class RestartPlan(
    /** Still in the future: set their alarms again. */
    val schedule: List<Reminder>,
    /** Their time passed while the phone was off: show them now, oldest first, rather than lose them. */
    val fireNow: List<Reminder>,
)

object ReminderPlan {

    /** Splits the pending (not done) reminders; done ones are never scheduled or shown again. */
    fun afterRestart(pending: List<Reminder>, nowMillis: Long): RestartPlan {
        val open = pending.filter { !it.done }
        return RestartPlan(
            schedule = open.filter { it.triggerAtMillis > nowMillis }.sortedBy { it.triggerAtMillis },
            fireNow = open.filter { it.triggerAtMillis <= nowMillis }.sortedBy { it.triggerAtMillis },
        )
    }

    /** Exact alarms need the user's permission on Android 12+; without it the alarm is inexact (a few minutes late at most). */
    enum class Precision { Exact, Inexact }

    fun precision(canScheduleExact: Boolean): Precision = if (canScheduleExact) Precision.Exact else Precision.Inexact
}

/** The title and text of a reminder's notification. */
data class ReminderTexts(val title: String, val text: String)

/**
 * What the notification says. A locked conversation shows nothing of itself (not the sender, not
 * the message), only that a reminder is due. Otherwise: the sender, and the message the user chose
 * to be reminded about. [generic] ("Reminder") stands in for whatever is missing.
 */
fun reminderTexts(reminderText: String, senderName: String?, locked: Boolean, lockedTitle: String, generic: String): ReminderTexts = when {
    locked -> ReminderTexts(lockedTitle, generic)
    else -> ReminderTexts(senderName?.takeIf { it.isNotBlank() } ?: generic, reminderText.trim().ifEmpty { generic })
}
