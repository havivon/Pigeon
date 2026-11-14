package com.havivon.pigeon.ui.components

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive

/**
 * Drag-to-reorder for a list (the colour rules, the categories). The dragged card follows the finger by its offset from
 * where it would sit; when its leading edge passes the middle of the neighbour it moves towards, the two swap places in the
 * order (one swap at a time, until the list has laid out again). [isItem] tells the list's reorderable items by their keys
 * from the rest (a header, a button), which never swap. Near the top or bottom edge of the list the screen scrolls by
 * itself, slowly ([ReorderAutoScroll]).
 */
@Stable
class ReorderDragState(private val list: LazyListState, private val isItem: (Any) -> Boolean) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set
    private var distance by mutableFloatStateOf(0f)
    private var startOffset = 0f
    private var awaitingLayoutFrom: Int? = null

    fun begin(key: Any) {
        val info = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        draggingKey = key
        startOffset = info.offset.toFloat()
        distance = 0f
        awaitingLayoutFrom = null
    }

    /** How far the dragged card is from its place in the list right now. */
    fun translationOf(key: Any): Float {
        val info = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return 0f
        return startOffset + distance - info.offset
    }

    /**
     * The order after moving the finger by [delta] pixels (0 after the list scrolled under a still finger), or null when no
     * neighbour was passed.
     */
    fun <T> dragBy(delta: Float, order: List<T>, keyOf: (T) -> Any): List<T>? {
        val key = draggingKey ?: return null
        distance += delta
        val visible = list.layoutInfo.visibleItemsInfo
        val me = visible.firstOrNull { it.key == key } ?: return null
        if (awaitingLayoutFrom == me.index) return null
        awaitingLayoutFrom = null
        val slots = visible.map { ReorderSlot(it.key, it.index, it.offset, it.size) }
        val targetKey = Reorder.swapTarget(ReorderSlot(me.key, me.index, me.offset, me.size), slots, startOffset + distance, isItem) ?: return null
        val from = order.indexOfFirst { keyOf(it) == key }
        val to = order.indexOfFirst { keyOf(it) == targetKey }
        if (from < 0 || to < 0 || from == to) return null
        awaitingLayoutFrom = me.index
        return order.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** How fast the list should scroll by itself right now, from -1 (up, full speed) to 1 (down); 0 away from the edges. */
    fun edgeSpeed(zone: Float): Float {
        val key = draggingKey ?: return 0f
        val info = list.layoutInfo
        val me = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return 0f
        val top = startOffset + distance
        // Item offsets start after the top content padding (under the fixed header), so 0 is the first visible content line
        return Reorder.edgeSpeed(top, top + me.size, start = 0f, end = (info.viewportEndOffset - info.afterContentPadding).toFloat(), zone = zone)
    }

    fun release() {
        draggingKey = null
        distance = 0f
        awaitingLayoutFrom = null
    }
}

/** One laid-out item of the list, for [Reorder.swapTarget]: its key, its index in the list and its place in pixels. */
internal data class ReorderSlot(val key: Any, val index: Int, val offset: Int, val size: Int)

/** The pure arithmetic of a drag, apart from the list so it can be tested. */
internal object Reorder {
    /**
     * The key of the neighbour the dragged card ([me], now drawn with its top at [top]) should swap with, or null. Moving
     * down, its bottom must pass the middle of the next item; moving up, its top must pass the middle of the previous one.
     * Only the neighbour, and only a reorderable one ([isItem]): the last place is reached like any other.
     */
    fun swapTarget(me: ReorderSlot, visible: List<ReorderSlot>, top: Float, isItem: (Any) -> Boolean): Any? {
        val bottom = top + me.size
        val target = when {
            top > me.offset -> visible.firstOrNull { it.index == me.index + 1 }?.takeIf { bottom > it.offset + it.size / 2f }
            top < me.offset -> visible.firstOrNull { it.index == me.index - 1 }?.takeIf { top < it.offset + it.size / 2f }
            else -> null
        }
        return target?.key?.takeIf(isItem)
    }

    /**
     * From -1 to 1: how deep the dragged card (from [top] to [bottom]) is in the [zone] at the [start] or the [end] of the
     * visible list; negative scrolls up. At least a quarter of the speed as soon as it is in the zone, so it never crawls.
     */
    fun edgeSpeed(top: Float, bottom: Float, start: Float, end: Float, zone: Float): Float {
        fun depth(d: Float) = if (d <= 0f) 0f else (0.25f + 0.75f * (d / zone)).coerceAtMost(1f)
        val up = depth(start + zone - top)
        val down = depth(bottom - (end - zone))
        return when {
            up > 0f && down > 0f -> 0f // a card taller than the list: no side to scroll to
            up > 0f -> -up
            else -> down
        }
    }
}

/** At most this many dp a second: a few cards a second, slow enough to stop where the user wants. */
private const val AUTO_SCROLL_DP_PER_SECOND = 260f

/** The edge zone where the scrolling starts. */
private const val AUTO_SCROLL_ZONE_DP = 72f

/**
 * While a card is dragged near the top or bottom of the list, scrolls the list slowly (by time, so the same on a 60Hz and a
 * 120Hz screen) and lets the card keep swapping places under the still finger: [order] is the order on screen and [onOrder]
 * gets the new one, as the drag's own [ReorderDragState.dragBy] does.
 */
@Composable
fun <T> ReorderAutoScroll(drag: ReorderDragState, list: LazyListState, order: List<T>, keyOf: (T) -> Any, onOrder: (List<T>) -> Unit) {
    val density = LocalDensity.current
    val zone = with(density) { AUTO_SCROLL_ZONE_DP.dp.toPx() }
    val pxPerSecond = with(density) { AUTO_SCROLL_DP_PER_SECOND.dp.toPx() }
    val currentOrder by rememberUpdatedState(order)
    val currentOnOrder by rememberUpdatedState(onOrder)
    val dragging = drag.draggingKey
    LaunchedEffect(dragging) {
        if (dragging == null) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (isActive && drag.draggingKey == dragging) {
            val now = withFrameNanos { it }
            val seconds = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            val speed = drag.edgeSpeed(zone)
            if (speed != 0f && list.scrollBy(speed * pxPerSecond * seconds) != 0f) {
                drag.dragBy(0f, currentOrder, keyOf)?.let { currentOnOrder(it) }
            }
        }
    }
}

/**
 * The order a reorderable list draws: the user's while a drag lasts and until the saved order arrives, otherwise [source].
 * One state for the screen's whole life: the drag gestures, made once per item, always write the order the list draws (a
 * state remembered by [source] was replaced when the first drop was saved, and the gestures went on writing the old one, so
 * nothing could be dragged after it).
 */
@Composable
fun <T> rememberDraggedOrder(source: List<T>, drag: ReorderDragState): MutableState<List<T>?> {
    val dragged = remember { mutableStateOf<List<T>?>(null) }
    LaunchedEffect(source) { if (drag.draggingKey == null) dragged.value = null }
    return dragged
}
