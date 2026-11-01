package com.havivon.pigeon.data.sms

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DelayedDeletionTest {

    private var commits = 0
    private var releases = 0

    @Test
    fun `nothing is deleted inside the undo window`() = runTest {
        DelayedDeletion(backgroundScope, 5_000, commit = { commits++ }, release = { releases++ })
        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(0, commits)
        assertEquals(0, releases)
    }

    @Test
    fun `the delete happens once when the window ends`() = runTest {
        val deletion = DelayedDeletion(backgroundScope, 5_000, commit = { commits++ }, release = { releases++ })
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(1, commits)
        assertEquals(1, releases)
        assertFalse("too late to undo", deletion.undo())
        assertEquals(1, releases)
    }

    @Test
    fun `undo inside the window keeps the messages`() = runTest {
        val deletion = DelayedDeletion(backgroundScope, 5_000, commit = { commits++ }, release = { releases++ })
        advanceTimeBy(1_000)
        assertTrue(deletion.undo())
        assertFalse("undo works once", deletion.undo())
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(0, commits)
        assertEquals(1, releases)
    }

    @Test
    fun `a delete that fails still brings the rows back into view`() = runTest {
        // The failure goes to the scope's handler, as the app's own scope logs it
        val scope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob() + CoroutineExceptionHandler { _, _ -> })
        DelayedDeletion(scope, 5_000, commit = { throw IllegalStateException("provider broke") }, release = { releases++ })
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(1, releases)
    }
}
