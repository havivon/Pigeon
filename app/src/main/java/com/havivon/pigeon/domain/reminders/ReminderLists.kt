package com.havivon.pigeon.domain.reminders

import com.havivon.pigeon.domain.model.Reminder

/** The Reminders screen's two lists: what is still ahead, and what already went off (or was missed) lately. */
data class ReminderLists(
    /** Not done and still in the future, soonest first. */
    val upcoming: List<Reminder>,
    /** Done, or due already, within [PAST_DAYS]: the most recent first. */
    val past: List<Reminder>,
) {
    companion object {
        /** How far back the "Past" section goes. */
        const val PAST_DAYS = 30
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

        fun of(all: List<Reminder>, nowMillis: Long): ReminderLists {
            val (ahead, behind) = all.partition { !it.done && it.triggerAtMillis > nowMillis }
            val since = nowMillis - PAST_DAYS * DAY_MILLIS
            return ReminderLists(
                upcoming = ahead.sortedBy { it.triggerAtMillis },
                past = behind.filter { it.triggerAtMillis >= since }.sortedByDescending { it.triggerAtMillis },
            )
        }
    }
}
