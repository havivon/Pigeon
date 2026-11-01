package com.havivon.pigeon.ui.common

import com.havivon.pigeon.domain.model.SmsConversation
import com.havivon.pigeon.domain.model.SpamVerdict
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long after the list is shown the judging starts: the first frame and a conversation opened at once go first. */
const val JUDGE_AFTER_MILLIS = 1_500L

/**
 * Runs a viewer's spam-on-read ([com.havivon.pigeon.domain.analysis.SpamOnRead]) for a screen: once per
 * new list of conversations (a new message, a new conversation), not on every emission of the screen's
 * state, of which its own verdicts are one; off the main thread and after [delayMillis]. A newer list
 * replaces a run that has not finished: what was judged is remembered there, so nothing is judged twice.
 */
class SpamJudging(
    private val scope: CoroutineScope,
    private val work: CoroutineDispatcher,
    private val judge: suspend (List<SmsConversation>, List<SpamVerdict>) -> Unit,
    private val delayMillis: Long = JUDGE_AFTER_MILLIS,
) {
    private var lastKey: List<Long>? = null
    private var job: Job? = null

    @Synchronized
    fun offer(conversations: List<SmsConversation>, verdicts: List<SpamVerdict>) {
        val key = ArrayList<Long>(conversations.size * 2)
        for (c in conversations) {
            key += c.threadId
            key += c.dateMillis
        }
        if (key == lastKey) return
        lastKey = key
        job?.cancel()
        job = scope.launch(work) {
            delay(delayMillis)
            judge(conversations, verdicts)
        }
    }
}
