package com.havivon.pigeon.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** A burst of changes (a new message, then its read mark, then the sync) becomes one update after this quiet time. */
const val WIDGET_REFRESH_DEBOUNCE_MILLIS = 1_000L

/**
 * One signal whenever anything a widget shows may have changed (any of [sources] emits), with
 * bursts collapsed into one signal after [quietMillis] without a change.
 */
@OptIn(FlowPreview::class)
fun refreshSignals(vararg sources: Flow<*>, quietMillis: Long = WIDGET_REFRESH_DEBOUNCE_MILLIS): Flow<Unit> =
    merge(*sources.map { source -> source.map { } }.toTypedArray()).debounce(quietMillis)

/**
 * Keeps the home-screen widgets up to date: redraws all five when [signals] fire (messages and the
 * provider, read state, favourites, pins, locks, settings, rules). It only listens while at least
 * one widget is placed, so a phone without widgets pays nothing; the widget receivers start and
 * stop it as the first widget is added and the last one removed, and the app starts it on launch.
 */
class WidgetRefresher(
    private val context: Context,
    private val scope: CoroutineScope,
    private val signals: Flow<Unit>,
) {
    private var job: Job? = null

    @Synchronized
    fun ensureRunning() {
        if (job?.isActive == true || !anyPlaced()) return
        job = scope.launch { signals.collect { redraw() } }
    }

    @Synchronized
    fun stopIfNoneLeft() {
        if (anyPlaced()) return
        job?.cancel()
        job = null
    }

    /** Redraws now, without waiting for the quiet time (a new message just arrived and the process may not live long). */
    suspend fun updateNow() {
        if (anyPlaced()) redraw()
    }

    private suspend fun redraw() {
        for (widget in widgets) {
            try {
                widget.updateAll(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("WidgetRefresher", "Update failed: ${e.javaClass.name}")
            }
        }
    }

    private fun anyPlaced(): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return receivers.any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
    }

    private val widgets: List<GlanceAppWidget> = listOf(UnreadWidget(), CodeWidget(), ContactsWidget(), CounterWidget(), FavoritesWidget())

    private val receivers = listOf(
        UnreadWidgetReceiver::class.java, CodeWidgetReceiver::class.java, ContactsWidgetReceiver::class.java,
        CounterWidgetReceiver::class.java, FavoritesWidgetReceiver::class.java,
    )
}
