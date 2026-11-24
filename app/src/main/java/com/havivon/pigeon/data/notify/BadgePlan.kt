package com.havivon.pigeon.data.notify

/**
 * What every notification's number should be, so that they add up to all the unread messages. A launcher shows a number
 * only while a notification of the app is there, so there must always be one to carry it.
 *
 * [numbers] is by notification key; [quiet] is the number of the quiet "unread messages" notification, 0 for none.
 */
data class BadgePlan(val numbers: Map<String, Int>, val quiet: Int) {

    /** A message notification: its own unread [count] and when its message came. */
    data class Shown(val key: String, val count: Int, val time: Long)

    companion object {
        /**
         * The newest of the [shown] notifications also carries the unread messages that have none; with nothing shown the
         * quiet notification carries them all. With [badge] off each carries its own count, and there is no quiet one.
         */
        fun of(shown: List<Shown>, total: Int, badge: Boolean): BadgePlan {
            val own = shown.associate { it.key to it.count }
            if (!badge) return BadgePlan(own, quiet = 0)
            val rest = (total - shown.sumOf { it.count }).coerceAtLeast(0)
            val carrier = shown.maxByOrNull { it.time } ?: return BadgePlan(own, quiet = rest)
            return BadgePlan(own + (carrier.key to carrier.count + rest), quiet = 0)
        }
    }
}
