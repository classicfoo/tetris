package com.classicfoo.tetris.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TengenBevelGeometryTest {
    @Test
    fun `bevel polygons remain inside the inset face`() {
        val parts = TengenBevelGeometry.fromCell(10, 20, 110, 120)

        assertFalse(parts.isFlat)
        parts.edgePolygons
            .flatMap { it.points }
            .forEach { point -> assertTrue(parts.face.contains(point)) }
    }

    @Test
    fun `bevel polygons do not overlap one another`() {
        val polygons = TengenBevelGeometry.fromCell(10, 20, 110, 120).edgePolygons

        assertEquals(4, polygons.size)
        polygons.forEachIndexed { index, polygon ->
            polygons.drop(index + 1).forEach { other ->
                assertFalse(polygon.bounds.intersects(other.bounds))
            }
        }
    }

    @Test
    fun `floating point cell edges snap to physical pixels`() {
        val parts = TengenBevelGeometry.fromFloat(0.49f, 10.49f, 31.51f, 41.51f)

        assertEquals(PixelRect(0, 10, 32, 42), parts.cell)
    }

    @Test
    fun `tiny cells use a flat fallback without edge polygons`() {
        val parts = TengenBevelGeometry.fromCell(0, 0, 4, 4)

        assertTrue(parts.isFlat)
        assertTrue(parts.edgePolygons.isEmpty())
        assertEquals(PixelRect(1, 1, 3, 3), parts.face)
    }

    @Test
    fun `all Tengen ramps are fully opaque`() {
        TengenBevelPalette.ramps.values
            .flatMap { listOf(it.base, it.highlight, it.shadow) }
            .forEach { color -> assertEquals(0xFF, color ushr 24) }
    }

    @Test
    fun `default gutter leaves a one pixel inset around each block`() {
        val parts = TengenBevelGeometry.fromCell(10, 20, 110, 120)

        assertEquals(1, parts.face.left - parts.cell.left)
        assertEquals(1, parts.face.top - parts.cell.top)
        assertEquals(1, parts.cell.right - parts.face.right)
        assertEquals(1, parts.cell.bottom - parts.face.bottom)
    }

    @Test
    fun `joined cells remove only the internal seam`() {
        val parts = TengenBevelGeometry.fromCells(
            listOf(
                gridCell(0, 0, 0, 0, 20, 20),
                gridCell(1, 0, 20, 0, 40, 20),
            ),
        )

        assertEquals(2, parts.size)
        assertEquals(20, parts[0].face.right)
        assertEquals(20, parts[1].face.left)
        assertNull(parts[0].right)
        assertNull(parts[1].left)
        assertNotNull(parts[0].top)
        assertNotNull(parts[1].top)
        assertNotNull(parts[0].left)
        assertNotNull(parts[1].right)
    }

    @Test
    fun `separate cells retain their exposed outer gap and bevels`() {
        val left = TengenBevelGeometry.fromCells(
            listOf(gridCell(0, 0, 0, 0, 20, 20)),
        ).single()
        val right = TengenBevelGeometry.fromCells(
            listOf(gridCell(1, 0, 20, 0, 40, 20)),
        ).single()

        assertEquals(19, left.face.right)
        assertEquals(21, right.face.left)
        assertTrue(left.face.right < right.face.left)
        assertNotNull(left.right)
        assertNotNull(right.left)
    }

    private fun gridCell(
        x: Int,
        y: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): TengenGridCell = TengenGridCell(
        coordinate = PixelPoint(x, y),
        bounds = PixelRect(left, top, right, bottom),
    )
}
