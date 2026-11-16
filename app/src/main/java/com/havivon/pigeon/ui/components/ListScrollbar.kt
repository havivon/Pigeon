package com.havivon.pigeon.ui.components

import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.havivon.pigeon.ui.theme.HebTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Where a list stands, for its scrollbar: how far down it is ([fraction], 0 at the top, 1 at the bottom) and how much of
 * it shows ([visible], the thumb's share of the track). Items differ in height, so the content is estimated from the
 * average height of the items on screen. Null when the list fits on the screen (no scrollbar then).
 */
internal data class ScrollPosition(val fraction: Float, val visible: Float)

internal fun scrollPosition(
    totalItems: Int,
    firstIndex: Int,
    firstOffset: Int,
    visibleSizes: List<Int>,
    viewport: Int,
): ScrollPosition? {
    if (totalItems == 0 || visibleSizes.isEmpty() || viewport <= 0) return null
    val average = visibleSizes.sum().toFloat() / visibleSizes.size
    val content = average * totalItems
    if (content <= viewport) return null
    val scrolled = firstIndex * average + firstOffset
    return ScrollPosition((scrolled / (content - viewport)).coerceIn(0f, 1f), (viewport / content).coerceIn(0f, 1f))
}

/**
 * The date a list row shows, for the scrollbar's label: the rows write the time for today ("14:05") and the date otherwise
 * ("26/10/2026"), so a row without a date is today's.
 */
fun scrollDate(time: String, today: String): String = DATE.find(time)?.value ?: today

private val DATE = Regex("""\d{2}/\d{2}/\d{4}""")

/** The item and offset for a scrollbar dragged to [fraction] of the list: the inverse of [scrollPosition]. */
internal fun scrollTarget(fraction: Float, totalItems: Int, averageItem: Float, viewport: Int): Pair<Int, Int> {
    val content = averageItem * totalItems
    val px = (fraction.coerceIn(0f, 1f) * (content - viewport)).coerceAtLeast(0f)
    val index = (px / averageItem).toInt().coerceIn(0, (totalItems - 1).coerceAtLeast(0))
    return index to (px - index * averageItem).roundToInt().coerceAtLeast(0)
}

/**
 * A quiet scrollbar for a list of messages or conversations, laid over the list ([modifier] should fill the list's box):
 * a thin, half-transparent thumb at the end edge (the left one in Hebrew) that turns solid and wider while a finger is on
 * it, and drags the list. Only the thumb takes touches, so the items under the edge stay tappable. Hidden while the list
 * fits on the screen. It keeps clear of the list's content padding (a header drawn over it), and of [top] / [bottom] besides.
 * While it is dragged, [label] names where the list is: the date of the top row on the screen, by the row's key (null for
 * a row with no date, a banner or a header: the next row's is taken).
 */
@Composable
fun ListScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    top: androidx.compose.ui.unit.Dp = 0.dp,
    bottom: androidx.compose.ui.unit.Dp = 0.dp,
    label: ((key: Any) -> String?)? = null,
) {
    val c = HebTheme.colors
    val scope = rememberCoroutineScope()
    val position by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            scrollPosition(
                info.totalItemsCount,
                state.firstVisibleItemIndex,
                state.firstVisibleItemScrollOffset,
                info.visibleItemsInfo.map { it.size },
                info.viewportEndOffset - info.viewportStartOffset - info.beforeContentPadding - info.afterContentPadding,
            )
        }
    }
    var pressed by remember { mutableStateOf(false) }
    // While dragging, the thumb follows the finger rather than the estimate
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val current = position ?: if (pressed) ScrollPosition(dragFraction ?: 0f, 0.1f) else return
    val width by animateDpAsState(if (pressed) 12.dp else 7.dp, label = "scrollbar width")
    val alpha by animateFloatAsState(if (pressed) 1f else 0.45f, label = "scrollbar alpha")
    val density = LocalDensity.current
    // Clear of a header or a bar the list is padded for (its content padding), and of [top] / [bottom] besides
    val padTop = with(density) { state.layoutInfo.beforeContentPadding.toDp() } + top
    val padBottom = with(density) { state.layoutInfo.afterContentPadding.toDp() } + bottom
    BoxWithConstraints(modifier.padding(top = padTop, bottom = padBottom)) {
        val track = constraints.maxHeight.toFloat()
        val minThumb = with(density) { MIN_THUMB.toPx() }
        val thumb = (track * current.visible).coerceIn(minThumb, track)
        val movable = (track - thumb).coerceAtLeast(1f)
        val fraction = dragFraction ?: current.fraction
        val latest by rememberUpdatedState(current)
        val touchHeight = with(density) { maxOf(thumb, minThumb).toDp() }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, (fraction * movable).roundToInt()) }
                .width(TOUCH_WIDTH)
                .height(touchHeight)
                // The thumb sits on the screen's edge, where gesture navigation takes a swipe for "back": without this the
                // phone kept the finger and the thumb could not be dragged
                .systemGestureExclusion()
                .pointerInput(state) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        pressed = true
                        var f = latest.fraction
                        dragFraction = f
                        try {
                            verticalDrag(down.id) { change ->
                                change.consume()
                                f = (f + change.positionChange().y / movable).coerceIn(0f, 1f)
                                dragFraction = f
                                val info = state.layoutInfo
                                val sizes = info.visibleItemsInfo.map { it.size }
                                if (sizes.isNotEmpty()) {
                                    val viewport = info.viewportEndOffset - info.viewportStartOffset - info.beforeContentPadding - info.afterContentPadding
                                    val (index, offset) = scrollTarget(f, info.totalItemsCount, sizes.sum().toFloat() / sizes.size, viewport)
                                    scope.launch { state.scrollToItem(index, offset) }
                                }
                            }
                        } finally {
                            pressed = false
                            dragFraction = null
                        }
                    }
                },
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                Modifier
                    .padding(end = 4.dp)
                    .width(width)
                    .fillMaxHeight()
                    .alpha(alpha)
                    .clip(RoundedCornerShape(50))
                    .background(if (pressed) c.accent else c.onSurfaceMuted),
            )
        }
        // The date beside the thumb while a finger drags it, toward the middle of the screen
        val shown = if (pressed && label != null) topLabel(state, label) else null
        if (shown != null) {
            Text(
                shown,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, (fraction * movable + thumb / 2).roundToInt() - with(density) { 18.dp.roundToPx() }) }
                    .padding(end = TOUCH_WIDTH + 6.dp)
                    .shadow(4.dp, RoundedCornerShape(50))
                    .clip(RoundedCornerShape(50))
                    .background(c.header)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** The label of the first row at the top of the screen that has one. */
private fun topLabel(state: LazyListState, label: (Any) -> String?): String? {
    val info = state.layoutInfo
    val top = info.viewportStartOffset + info.beforeContentPadding
    return info.visibleItemsInfo
        .filter { it.offset + it.size / 2 >= top }
        .firstNotNullOfOrNull { label(it.key) }
}

/** The thumb's shortest length, and the width of the strip a finger can take it by. */
private val MIN_THUMB = 56.dp
private val TOUCH_WIDTH = 40.dp
