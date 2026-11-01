package com.havivon.pigeon.data.sms

import com.havivon.pigeon.domain.model.PendingDeletion
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A deletion that waits out the undo window. The rows are already hidden from every list; when
 * [windowMillis] ends without [undo], [commit] deletes them for good. Either way [release] then
 * takes them off the hidden list, exactly once, even when the commit fails (so a refused delete
 * brings the messages back into view instead of hiding them until the app restarts).
 *
 * Runs in [scope], which outlives the screens: leaving the conversation does not stop the delete.
 * If the process dies inside the window nothing is deleted, which errs on the side of keeping
 * the user's messages.
 */
class DelayedDeletion(
    scope: CoroutineScope,
    windowMillis: Long,
    private val commit: suspend () -> Unit,
    private val release: () -> Unit,
) : PendingDeletion {

    private val settled = AtomicBoolean(false)

    private val job = scope.launch {
        delay(windowMillis)
        if (settled.compareAndSet(false, true)) {
            withContext(NonCancellable) {
                try {
                    commit()
                } finally {
                    release()
                }
            }
        }
    }

    override fun undo(): Boolean {
        if (!settled.compareAndSet(false, true)) return false
        job.cancel()
        release()
        return true
    }
}
