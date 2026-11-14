package com.havivon.pigeon.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The arithmetic of drag-to-reorder: which neighbour a dragged card swaps with, and how fast the edges scroll. */
class ReorderTest {

    // A button ("reset", index 0), then four cards of 100px, one under the other
    private val list = listOf(
        ReorderSlot("reset", 0, 0, 60),
        ReorderSlot("a", 1, 60, 100),
        ReorderSlot("b", 2, 160, 100),
        ReorderSlot("c", 3, 260, 100),
        ReorderSlot("d", 4, 360, 100),
    )
    private val isItem: (Any) -> Boolean = { it != "reset" }
    private fun slot(key: String) = list.first { it.key == key }

    @Test
    fun `moving down swaps once the bottom passes the middle of the next card`() {
        val b = slot("b")
        assertNull(Reorder.swapTarget(b, list, top = 160f + 49f, isItem))
        assertEquals("c", Reorder.swapTarget(b, list, top = 160f + 51f, isItem))
    }

    @Test
    fun `the last place is reached like any other`() {
        // c onto d, the last card: half a card down is enough, no need to reach the bottom of the screen
        assertEquals("d", Reorder.swapTarget(slot("c"), list, top = 260f + 51f, isItem))
        // Below the last card there is nothing to swap with
        assertNull(Reorder.swapTarget(slot("d"), list, top = 360f + 300f, isItem))
    }

    @Test
    fun `moving up swaps once the top passes the middle of the previous card, never with a button`() {
        val c = slot("c")
        assertNull(Reorder.swapTarget(c, list, top = 260f - 49f, isItem))
        assertEquals("b", Reorder.swapTarget(c, list, top = 260f - 51f, isItem))
        // The first card stays first: the button above it is no place
        assertNull(Reorder.swapTarget(slot("a"), list, top = 0f, isItem))
    }

    @Test
    fun `only the neighbour swaps, one place at a time, however far the finger went`() {
        assertEquals("b", Reorder.swapTarget(slot("a"), list, top = 400f, isItem))
    }

    @Test
    fun `the list scrolls only near its edges, slowly at first and faster deeper in`() {
        // A list from 0 to 1000 with an edge zone of 100
        assertEquals(0f, Reorder.edgeSpeed(top = 400f, bottom = 500f, start = 0f, end = 1000f, zone = 100f))
        val justIn = Reorder.edgeSpeed(top = 400f, bottom = 901f, start = 0f, end = 1000f, zone = 100f)
        val deep = Reorder.edgeSpeed(top = 400f, bottom = 990f, start = 0f, end = 1000f, zone = 100f)
        assertTrue(justIn in 0.25f..0.3f)
        assertTrue(deep > justIn && deep <= 1f)
        assertEquals(1f, Reorder.edgeSpeed(top = 400f, bottom = 1200f, start = 0f, end = 1000f, zone = 100f))
        // Up is negative
        assertTrue(Reorder.edgeSpeed(top = 50f, bottom = 150f, start = 0f, end = 1000f, zone = 100f) < 0f)
    }
}
