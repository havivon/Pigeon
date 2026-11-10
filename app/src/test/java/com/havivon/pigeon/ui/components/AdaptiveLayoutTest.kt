package com.havivon.pigeon.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The choices behind the layouts that keep working with a large font and a narrow screen, checked without drawing:
 * the widths are what the real composables measure, in px at the Pixel 6's density (2.625 px per dp).
 */
class AdaptiveLayoutTest {

    private fun dp(v: Int) = (v * 2.625f).toInt()

    // "Mark all read" at the end of the All / Unread row

    @Test
    fun `the full label when it fits beside the filters`() {
        assertEquals(EndButtonFit.Full, endButtonFit(room = dp(150), fullWidth = dp(140), compactWidth = dp(90)))
    }

    @Test
    fun `the short label when only it fits`() {
        assertEquals(EndButtonFit.Compact, endButtonFit(room = dp(100), fullWidth = dp(140), compactWidth = dp(90)))
    }

    @Test
    fun `the icon alone when neither label fits, never a line of its own`() {
        // 1.8x font on a 320dp screen: the filters leave ~60dp, the short label needs ~150dp
        assertEquals(EndButtonFit.IconOnly, endButtonFit(room = dp(60), fullWidth = dp(250), compactWidth = dp(150)))
        // Even with no room at all (the filters scroll sideways then), still the icon on the same line
        assertEquals(EndButtonFit.IconOnly, endButtonFit(room = -dp(20), fullWidth = dp(250), compactWidth = dp(150)))
    }

    @Test
    fun `the label scales out of the row as the font grows`() {
        // A 411dp phone: 16dp header padding each side, a 10dp gap; filters 170dp, labels 120dp / 80dp at 1.0
        fun fitAt(scale: Float, screen: Int): EndButtonFit {
            val width = dp(screen - 32)
            val filters = (dp(170) * scale).toInt()
            val room = width - filters - dp(10)
            return endButtonFit(room, (dp(120) * scale).toInt(), (dp(80) * scale).toInt())
        }
        assertEquals(EndButtonFit.Full, fitAt(1.0f, 411))
        assertEquals(EndButtonFit.Compact, fitAt(1.3f, 411))
        assertEquals(EndButtonFit.IconOnly, fitAt(1.8f, 411))
        assertEquals(EndButtonFit.IconOnly, fitAt(1.3f, 320))
    }

    // The category menu's button beside All / Unread, or on a line of its own

    @Test
    fun `the category stays on the filters' line while everything fits`() {
        assertFalse(leadOnOwnRow(width = dp(379), leadWidth = dp(110), filtersWidth = dp(170), iconWidth = dp(38), gap = dp(10)))
    }

    @Test
    fun `the category gets a line of its own when even the icon would not fit`() {
        // "All categories" with a large font: 200dp, the filters 260dp, on a 288dp row
        assertTrue(leadOnOwnRow(width = dp(288), leadWidth = dp(200), filtersWidth = dp(260), iconWidth = dp(38), gap = dp(10)))
    }

    // A trailing part (a code's copy button, a settings button) beside its text or under it

    @Test
    fun `the copy button stays beside the snippet when the snippet keeps half the row`() {
        assertTrue(trailingFitsBeside(width = dp(300), trailingWidth = dp(100), gap = dp(8), minMainWidth = dp(150)))
    }

    @Test
    fun `the copy button goes under the snippet when the font makes it too wide`() {
        assertFalse(trailingFitsBeside(width = dp(240), trailingWidth = dp(150), gap = dp(8), minMainWidth = dp(120)))
    }

    @Test
    fun `nothing at the end always fits`() {
        assertTrue(trailingFitsBeside(width = dp(100), trailingWidth = 0, gap = dp(8), minMainWidth = dp(200)))
    }

    // The name line: the name first, then the tags, the time capped

    @Test
    fun `name and tags whole when they fit`() {
        assertEquals(NameLineWidths(100, 60), nameLineWidths(room = 200, nameWidth = 100, tagsWidth = 60, minTagWidth = 28))
    }

    @Test
    fun `a long name keeps most of the room and the tags are shortened`() {
        // Before: the tag took its 150 and the name got 100 ("0505050…"); now the name keeps 60%
        assertEquals(NameLineWidths(150, 100), nameLineWidths(room = 250, nameWidth = 300, tagsWidth = 150, minTagWidth = 28))
    }

    @Test
    fun `a short tag is not shortened for a long name`() {
        assertEquals(NameLineWidths(170, 80), nameLineWidths(room = 250, nameWidth = 300, tagsWidth = 80, minTagWidth = 28))
    }

    @Test
    fun `tags too narrow to read are left out`() {
        assertEquals(NameLineWidths(100, 0), nameLineWidths(room = 100, nameWidth = 300, tagsWidth = 80, minTagWidth = 50))
    }

    @Test
    fun `no room gives nothing`() {
        assertEquals(NameLineWidths(0, 0), nameLineWidths(room = 0, nameWidth = 300, tagsWidth = 80, minTagWidth = 28))
    }

    @Test
    fun `the time takes at most its share of the line`() {
        assertTrue(TIME_MAX_FRACTION in 0.3f..0.4f)
    }

    // The collapsing header

    @Test
    fun `scrolling down collapses the header first and the list gets the rest`() {
        assertEquals(CollapseStep(collapsed = 30f, consumed = -30f), collapseStep(collapsed = 0f, delta = -30f, range = 100f, allowCollapse = true))
        // Only the 20px left to collapse are used; the list scrolls by the other 30
        assertEquals(CollapseStep(collapsed = 100f, consumed = -20f), collapseStep(collapsed = 80f, delta = -50f, range = 100f, allowCollapse = true))
    }

    @Test
    fun `scrolling up expands the header before the list moves`() {
        assertEquals(CollapseStep(collapsed = 60f, consumed = 40f), collapseStep(collapsed = 100f, delta = 40f, range = 100f, allowCollapse = true))
        assertEquals(CollapseStep(collapsed = 0f, consumed = 10f), collapseStep(collapsed = 10f, delta = 40f, range = 100f, allowCollapse = true))
    }

    @Test
    fun `a list too short to scroll keeps its header`() {
        assertEquals(CollapseStep(collapsed = 0f, consumed = 0f), collapseStep(collapsed = 0f, delta = -30f, range = 100f, allowCollapse = false))
    }

    @Test
    fun `no collapsible part, nothing used`() {
        assertEquals(0f, collapseStep(collapsed = 0f, delta = -30f, range = 0f, allowCollapse = true).consumed)
    }

    @Test
    fun `after a rotation made the part shorter, a step never uses more than it was given`() {
        val step = collapseStep(collapsed = 400f, delta = 10f, range = 100f, allowCollapse = true)
        assertEquals(10f, step.consumed)
        assertEquals(90f, step.collapsed)
    }

    @Test
    fun `a half collapsed header settles at the nearer end`() {
        assertEquals(0f, settleTarget(30f, 100f))
        assertEquals(100f, settleTarget(70f, 100f))
        assertEquals(0f, settleTarget(0f, 100f))
        assertEquals(100f, settleTarget(100f, 100f))
        assertEquals(0f, settleTarget(50f, 0f))
    }

    @Test
    fun `the state follows the scroll and reports when it is whole`() {
        val state = CollapsingHeaderState()
        state.range = 120f
        val used = state.connection.onPreScroll(Offset(0f, -50f), NestedScrollSource.Drag)
        assertEquals(-50f, used.y)
        assertEquals(50, state.collapsedPx)
        assertFalse(state.isExpanded)
        state.connection.onPreScroll(Offset(0f, 80f), NestedScrollSource.Drag)
        assertTrue(state.isExpanded)
    }

    @Test
    fun `the state keeps the header whole while the list cannot scroll`() {
        val state = CollapsingHeaderState()
        state.range = 120f
        state.canCollapse = { false }
        assertEquals(Offset.Zero, state.connection.onPreScroll(Offset(0f, -50f), NestedScrollSource.Drag))
        assertTrue(state.isExpanded)
    }

    @Test
    fun `the collapse survives a rotation`() {
        val state = CollapsingHeaderState()
        state.range = 120f
        state.connection.onPreScroll(Offset(0f, -70f), NestedScrollSource.Drag)
        val saved = with(CollapsingHeaderState.Saver) { androidx.compose.runtime.saveable.SaverScope { true }.save(state) }!!
        val restored = CollapsingHeaderState.Saver.restore(saved)!!
        restored.range = 120f
        assertEquals(70, restored.collapsedPx)
        // A shorter header after the rotation: no more than all of it is collapsed
        restored.range = 50f
        assertEquals(50, restored.collapsedPx)
    }

    @Test
    fun `reset brings the header back whole`() {
        val state = CollapsingHeaderState(60f)
        state.range = 120f
        state.reset()
        assertTrue(state.isExpanded)
        assertEquals(0f, state.range)
    }
}
