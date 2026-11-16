package com.havivon.pigeon.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Where the scrollbar's thumb stands, and where dragging it takes the list. */
class ListScrollbarTest {

    @Test
    fun `no scrollbar while the list fits on the screen`() {
        assertNull(scrollPosition(5, 0, 0, List(5) { 100 }, viewport = 800))
        assertNull(scrollPosition(0, 0, 0, emptyList(), viewport = 800))
    }

    @Test
    fun `the thumb is at the top, the middle and the bottom with the list`() {
        val sizes = List(8) { 100 }
        // 100 items of 100 px, 800 px on screen: 10 000 px of content, 9 200 px to scroll
        val top = scrollPosition(100, 0, 0, sizes, 800)!!
        assertEquals(0f, top.fraction, 0.001f)
        assertEquals(0.08f, top.visible, 0.001f)
        assertEquals(0.5f, scrollPosition(100, 46, 0, sizes, 800)!!.fraction, 0.001f)
        assertEquals(1f, scrollPosition(100, 92, 0, sizes, 800)!!.fraction, 0.001f)
    }

    @Test
    fun `dragging the thumb is the inverse of where it stands`() {
        for (f in listOf(0f, 0.25f, 0.5f, 0.9f, 1f)) {
            val (index, offset) = scrollTarget(f, totalItems = 100, averageItem = 100f, viewport = 800)
            val back = assertNotNull(scrollPosition(100, index, offset, List(8) { 100 }, 800))
            assertEquals("fraction $f", f, back!!.fraction, 0.01f)
        }
        // Never past the last item, never above the first
        assertEquals(0 to 0, scrollTarget(-1f, 100, 100f, 800))
        assertEquals(92, scrollTarget(2f, 100, 100f, 800).first)
    }

    private fun <T> assertNotNull(value: T?): T? {
        org.junit.Assert.assertNotNull(value)
        return value
    }
}
