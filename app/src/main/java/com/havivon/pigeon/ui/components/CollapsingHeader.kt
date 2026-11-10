package com.havivon.pigeon.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/*
 * A header whose top part scrolls away with the list and comes back as soon as the list is scrolled the other way,
 * while the rest of it stays pinned. The header's height and the list's top padding follow the same number, so the
 * first row always sits right under the header: no jump, no row hidden under it.
 */

/** The result of one scroll step on the header: how far it is now collapsed, and how much of the step it used. */
data class CollapseStep(val collapsed: Float, val consumed: Float)

/**
 * One scroll step of [delta] px (negative: the finger moves up and the list would scroll on) on a header collapsed by
 * [collapsed] of [range] px. The header moves first: it collapses on the way down and expands on the way up, and uses
 * that part of the step; the list gets the rest. With [allowCollapse] false (a list too short to scroll), it does not
 * collapse, and uses nothing.
 */
fun collapseStep(collapsed: Float, delta: Float, range: Float, allowCollapse: Boolean): CollapseStep {
    if (range <= 0f || delta == 0f || (delta < 0f && !allowCollapse)) return CollapseStep(collapsed.coerceIn(0f, maxOf(range, 0f)), 0f)
    val now = collapsed.coerceIn(0f, range)
    val next = (now - delta).coerceIn(0f, range)
    return CollapseStep(next, now - next)
}

/** Where a header left half collapsed comes to rest once the finger lets go: the nearer end. */
fun settleTarget(collapsed: Float, range: Float): Float = when {
    range <= 0f || collapsed <= 0f -> 0f
    collapsed >= range -> range
    collapsed > range / 2f -> range
    else -> 0f
}

/** How far the collapsible part of a header is scrolled away. Kept across a rotation (see [rememberCollapsingHeaderState]). */
@Stable
class CollapsingHeaderState(initialOffset: Float = 0f) {
    /** Scrolled away, in px; may be more than [range] for a moment after a rotation, so read [collapsedPx]. */
    var offset by mutableFloatStateOf(initialOffset)
        private set

    /** The height of the collapsible part, in px, as last measured; 0 while there is none. */
    var range by mutableFloatStateOf(0f)
        internal set

    /** The header's height with nothing collapsed, in px, as last measured. */
    internal var expandedHeight by mutableIntStateOf(0)

    /** Whether the list can scroll at all: a list shorter than the screen keeps its header whole. */
    var canCollapse: () -> Boolean = { true }

    /** How far the header is collapsed now, in whole px: what the header and the list's padding both use. */
    val collapsedPx: Int get() = offset.coerceIn(0f, range).roundToInt()

    /** The header is whole (nothing scrolled away). */
    val isExpanded: Boolean get() = collapsedPx == 0

    /** Back to whole, and forgets the collapsible part (a header that has none right now). */
    fun reset() {
        offset = 0f
        range = 0f
    }

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val step = collapseStep(offset, available.y, range, allowCollapse = canCollapse())
            if (step.consumed == 0f) return Offset.Zero
            offset = step.collapsed
            return Offset(0f, step.consumed)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            val from = offset.coerceIn(0f, range)
            val to = settleTarget(from, range)
            if (to != from) animate(from, to, animationSpec = tween(SETTLE_MILLIS)) { value, _ -> offset = value }
            return Velocity.Zero
        }
    }

    companion object {
        private const val SETTLE_MILLIS = 180
        val Saver: Saver<CollapsingHeaderState, Float> = Saver(save = { it.offset }, restore = { CollapsingHeaderState(it) })
    }
}

@Composable
fun rememberCollapsingHeaderState(): CollapsingHeaderState = rememberSaveable(saver = CollapsingHeaderState.Saver) { CollapsingHeaderState() }

/**
 * Marks the part of a header that scrolls away: it gets shorter by what is collapsed, its content slides up and out
 * (clipped to what is left), fading as it goes. The rest of the header (before or after it) stays.
 */
fun Modifier.collapsible(state: CollapsingHeaderState): Modifier = this
    .clipToBounds()
    .layout { measurable, constraints ->
        val p = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        state.range = p.height.toFloat()
        val off = state.offset.coerceIn(0f, p.height.toFloat()).roundToInt()
        layout(p.width, p.height - off) {
            p.placeRelativeWithLayer(0, -off) {
                alpha = if (p.height == 0) 1f else (1f - 1.6f * off / p.height).coerceIn(0f, 1f)
            }
        }
    }

/**
 * Like [FixedHeaderLayout], with a header that may hold a [collapsible] part: [header] is drawn over [content] (a
 * list that scrolls under its rounded corners), and the list's top padding, from [rememberHeaderPadding], is the
 * header's height as it is now. While [enabled] is false nothing collapses (and what was collapsed comes back).
 */
@Composable
fun CollapsingHeaderLayout(
    state: CollapsingHeaderState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    header: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    LaunchedEffect(enabled) { if (!enabled) state.reset() }
    Layout(
        content = {
            Box(Modifier.layoutId(HeaderSlot.Header)) { header() }
            Box(Modifier.layoutId(HeaderSlot.Content)) { content() }
        },
        modifier = modifier.nestedScroll(state.connection),
    ) { measurables, constraints ->
        val free = constraints.copy(minWidth = 0, minHeight = 0)
        val headerP = measurables.first { it.layoutId == HeaderSlot.Header }.measure(free)
        // Measured before the list, which reads it (with the collapse) for its top padding in this same pass
        state.expandedHeight = headerP.height + state.collapsedPx
        val contentP = measurables.first { it.layoutId == HeaderSlot.Content }.measure(constraints)
        val width = maxOf(constraints.minWidth, contentP.width, headerP.width)
        val height = maxOf(constraints.minHeight, contentP.height, headerP.height)
        layout(width, height) {
            contentP.placeRelative(0, 0)
            // Drawn last: the list scrolls under it
            headerP.placeRelative(0, 0)
        }
    }
}

private enum class HeaderSlot { Header, Content }

/**
 * The content padding of the list under a [CollapsingHeaderLayout]: on top, the header's height as it is right now
 * (read while measuring, so scrolling does not recompose the list); [bottom] at the bottom.
 */
@Composable
fun rememberHeaderPadding(state: CollapsingHeaderState, bottom: Dp, horizontal: Dp = 0.dp): PaddingValues {
    val density = LocalDensity.current
    return remember(state, bottom, horizontal, density) { HeaderPadding(state, density, bottom, horizontal) }
}

private class HeaderPadding(
    private val state: CollapsingHeaderState,
    private val density: Density,
    private val bottom: Dp,
    private val horizontal: Dp,
) : PaddingValues {
    override fun calculateTopPadding(): Dp = with(density) { (state.expandedHeight - state.collapsedPx).coerceAtLeast(0).toDp() }
    override fun calculateBottomPadding(): Dp = bottom
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = horizontal
    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = horizontal
}
