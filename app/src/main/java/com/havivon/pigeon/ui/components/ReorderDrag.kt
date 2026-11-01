package com.havivon.pigeon.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Drag-to-reorder for a list (the colour rules, the user's categories). The dragged card follows the finger
 * by its offset from where it would sit; when its middle crosses a neighbour the two swap places in the
 * order (one swap at a time, until the list has laid out again). [isItem] tells the list's reorderable
 * items by their keys from the rest (a header, a button).
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

    /** The order after moving the finger by [delta] pixels, or null when no neighbour was crossed. */
    fun <T> dragBy(delta: Float, order: List<T>, keyOf: (T) -> Any): List<T>? {
        val key = draggingKey ?: return null
        distance += delta
        val visible = list.layoutInfo.visibleItemsInfo
        val me = visible.firstOrNull { it.key == key } ?: return null
        if (awaitingLayoutFrom == me.index) return null
        awaitingLayoutFrom = null
        val middle = startOffset + distance + me.size / 2f
        val target = visible.firstOrNull { isItem(it.key) && it.key != key && middle >= it.offset && middle <= it.offset + it.size } ?: return null
        val from = order.indexOfFirst { keyOf(it) == key }
        val to = order.indexOfFirst { keyOf(it) == target.key }
        if (from < 0 || to < 0 || from == to) return null
        awaitingLayoutFrom = me.index
        return order.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun release() {
        draggingKey = null
        distance = 0f
        awaitingLayoutFrom = null
    }
}
