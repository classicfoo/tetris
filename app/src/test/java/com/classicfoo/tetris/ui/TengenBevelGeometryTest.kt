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
        assertNoPositiveAreaOverlap(polygons)
    }

    @Test
    fun `normal cells use a thick pixel bevel while small cells stay usable`() {
        val normal = TengenBevelGeometry.fromCell(0, 0, 100, 100)
        val small = TengenBevelGeometry.fromCell(0, 0, 12, 12)

        assertTrue(normal.bevelPx >= 16)
        assertTrue(normal.face.width - 2 * normal.bevelPx > 0)
        assertTrue(small.bevelPx in 1..3)
        assertTrue(small.face.width - 2 * small.bevelPx > 0)
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

    @Test
    fun `every exposed edge combination gives each corner one geometric owner`() {
        val edgeBits = listOf(
            1 to PixelPoint(0, -1), // top
            2 to PixelPoint(-1, 0), // left
            4 to PixelPoint(1, 0), // right
            8 to PixelPoint(0, 1), // bottom
        )

        (0 until 16).forEach { mask ->
            val cells = mutableListOf(gridCell(0, 0, 0, 0, 40, 40))
            edgeBits.forEach { (bit, neighbor) ->
                if (mask and bit == 0) {
                    cells += gridCell(
                        neighbor.x,
                        neighbor.y,
                        neighbor.x * 40,
                        neighbor.y * 40,
                        neighbor.x * 40 + 40,
                        neighbor.y * 40 + 40,
                    )
                }
            }

            val center = TengenBevelGeometry.fromCells(cells)
                .single { it.cell == PixelRect(0, 0, 40, 40) }

            assertFalse("mask=$mask unexpectedly flat", center.isFlat)
            assertEquals("mask=$mask has the wrong edge count", Integer.bitCount(mask), center.edgePolygons.size)
            center.edgePolygons.forEach { polygon ->
                assertEquals(4, polygon.points.toSet().size)
                polygon.points.forEach { point -> assertTrue(center.face.contains(point)) }
            }
            assertNoPositiveAreaOverlap(center.edgePolygons)
        }
    }

    @Test
    fun `joined horizontal and vertical cells keep shared seams free of bevels`() {
        val horizontal = TengenBevelGeometry.fromCells(
            listOf(
                gridCell(0, 0, 0, 0, 40, 40),
                gridCell(1, 0, 40, 0, 80, 40),
            ),
        )
        val vertical = TengenBevelGeometry.fromCells(
            listOf(
                gridCell(0, 0, 0, 0, 40, 40),
                gridCell(0, 1, 0, 40, 40, 80),
            ),
        )

        assertNull(horizontal[0].right)
        assertNull(horizontal[1].left)
        assertEquals(horizontal[0].face.right, horizontal[1].face.left)
        assertNull(vertical[0].bottom)
        assertNull(vertical[1].top)
        assertEquals(vertical[0].face.bottom, vertical[1].face.top)
        horizontal.forEach { assertNoPositiveAreaOverlap(it.edgePolygons) }
        vertical.forEach { assertNoPositiveAreaOverlap(it.edgePolygons) }
    }

    @Test
    fun `T notch gives both light corners an inward miter`() {
        val parts = TengenBevelGeometry.fromCells(
            listOf(
                gridCell(1, 0, 40, 0, 80, 40),
                gridCell(0, 1, 0, 40, 40, 80),
                gridCell(1, 1, 40, 40, 80, 80),
                gridCell(2, 1, 80, 40, 120, 80),
            ),
        )

        val upper = parts.single { it.cell == PixelRect(40, 0, 80, 40) }
        val lowerLeft = parts.single { it.cell == PixelRect(0, 40, 40, 80) }

        assertContainsPoint(upper.left, PixelPoint(48, 33))
        assertContainsPoint(lowerLeft.top, PixelPoint(33, 48))
        assertNoCrossCellPositiveAreaOverlap(parts)
    }

    @Test
    fun `L notch joins a shadow right edge to a light top edge`() {
        val parts = TengenBevelGeometry.fromCells(
            listOf(
                gridCell(0, 0, 0, 0, 40, 40),
                gridCell(0, 1, 0, 40, 40, 80),
                gridCell(0, 2, 0, 80, 40, 120),
                gridCell(1, 2, 40, 80, 80, 120),
            ),
        )

        val vertical = parts.single { it.cell == PixelRect(0, 40, 40, 80) }
        val foot = parts.single { it.cell == PixelRect(40, 80, 80, 120) }

        assertContainsPoint(vertical.right, PixelPoint(32, 73))
        assertContainsPoint(foot.top, PixelPoint(48, 88))
        assertNoCrossCellPositiveAreaOverlap(parts)
    }

    @Test
    fun `S and Z notches retain light and shadow orientation`() {
        val s = TengenBevelGeometry.fromCells(
            listOf(
                gridCell(1, 0, 40, 0, 80, 40),
                gridCell(2, 0, 80, 0, 120, 40),
                gridCell(0, 1, 0, 40, 40, 80),
                gridCell(1, 1, 40, 40, 80, 80),
            ),
        )
        val sUpper = s.single { it.cell == PixelRect(40, 0, 80, 40) }
        val sLowerLeft = s.single { it.cell == PixelRect(0, 40, 40, 80) }
        assertContainsPoint(sUpper.left, PixelPoint(48, 33))
        assertContainsPoint(sLowerLeft.top, PixelPoint(33, 48))
        assertNoCrossCellPositiveAreaOverlap(s)

        val z = TengenBevelGeometry.fromCells(
            listOf(
                gridCell(0, 0, 0, 0, 40, 40),
                gridCell(1, 0, 40, 0, 80, 40),
                gridCell(1, 1, 40, 40, 80, 80),
                gridCell(2, 1, 80, 40, 120, 80),
            ),
        )
        val zUpperLeft = z.single { it.cell == PixelRect(0, 0, 40, 40) }
        val zLowerMiddle = z.single { it.cell == PixelRect(40, 40, 80, 80) }
        assertContainsPoint(zUpperLeft.bottom, PixelPoint(33, 32))
        assertContainsPoint(zLowerMiddle.left, PixelPoint(48, 47))
        assertNoCrossCellPositiveAreaOverlap(z)
    }

    private fun assertNoPositiveAreaOverlap(polygons: List<PixelPolygon>) {
        polygons.forEachIndexed { index, polygon ->
            polygons.drop(index + 1).forEach { other ->
                assertFalse(
                    "polygons $index and ${index + 1} overlap with positive area",
                    polygonsHavePositiveAreaOverlap(polygon, other),
                )
            }
        }
    }

    private fun assertNoCrossCellPositiveAreaOverlap(parts: List<TengenBevelParts>) {
        val polygons = parts.flatMap { it.edgePolygons }
        assertNoPositiveAreaOverlap(polygons)
    }

    private fun assertContainsPoint(polygon: PixelPolygon?, expected: PixelPoint) {
        assertNotNull("expected a bevel polygon containing $expected", polygon)
        assertTrue("expected bevel polygon to contain $expected", expected in polygon!!.points)
    }

    /** Separating-axis test: shared edges/diagonals are allowed, filled overlap is not. */
    private fun polygonsHavePositiveAreaOverlap(first: PixelPolygon, second: PixelPolygon): Boolean {
        val axes = (first.points + second.points).zipWithNextCycle().map { (a, b) ->
            val dx = (b.x - a.x).toDouble()
            val dy = (b.y - a.y).toDouble()
            Pair(-dy, dx)
        }

        return axes.all { (axisX, axisY) ->
            val firstProjection = first.points.map { it.x * axisX + it.y * axisY }
            val secondProjection = second.points.map { it.x * axisX + it.y * axisY }
            val overlap = minOf(firstProjection.maxOrNull()!!, secondProjection.maxOrNull()!!) -
                maxOf(firstProjection.minOrNull()!!, secondProjection.minOrNull()!!)
            overlap > 0.000001
        }
    }

    private fun <T> List<T>.zipWithNextCycle(): List<Pair<T, T>> =
        zip(drop(1) + first())

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
