package com.classicfoo.tetris.ui

import com.classicfoo.tetris.engine.PieceDefinitions
import com.classicfoo.tetris.engine.Rotation
import com.classicfoo.tetris.engine.Tetromino
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TengenSurfaceRasterizerTest {
    private val outline = 0xFF050A17.toInt()
    private val blue = OpaqueColorRamp(
        base = 0xFF3E64B7.toInt(),
        highlight = 0xFF7FA1E1.toInt(),
        shadow = 0xFF1D356F.toInt(),
    )
    private val red = OpaqueColorRamp(
        base = 0xFFC54646.toInt(),
        highlight = 0xFFF0786F.toInt(),
        shadow = 0xFF681F2D.toInt(),
    )

    @Test
    fun `every rotation of every tetromino rasterizes as one opaque silhouette`() {
        Tetromino.entries.forEach { type ->
            Rotation.entries.forEach { rotation ->
                val shape = PieceDefinitions.cells(type, rotation)
                val minX = shape.minOf { it.x }
                val minY = shape.minOf { it.y }
                val cells = shape.map { cell ->
                    surfaceCell(
                        x = cell.x - minX,
                        y = cell.y - minY,
                        ramp = TengenBevelPalette.ramps.getValue(type),
                        size = 24,
                    )
                }
                val raster = rasterize(cells)

                assertEquals(shape.size * 24 * 24, raster.pixels.count { it != 0 })
                assertTrue(raster.pixels.filter { it != 0 }.all { it ushr 24 == 0xFF })
            }
        }
    }

    @Test
    fun `outer rim and bevel are opaque and deterministic`() {
        val raster = rasterize(listOf(surfaceCell(0, 0, blue, 40)))

        assertEquals(outline, raster.colorAt(20, 0))
        assertEquals(blue.highlight, raster.colorAt(20, 1))
        assertEquals(outline, raster.colorAt(39, 20))
        assertEquals(blue.shadow, raster.colorAt(38, 20))
        assertTrue(raster.pixels.all { it ushr 24 == 0xFF })
    }

    @Test
    fun `touching cells of different pieces have no internal outline or bevel`() {
        val raster = rasterize(
            listOf(
                surfaceCell(0, 0, blue, 40),
                surfaceCell(1, 0, red, 40),
            ),
        )

        assertEquals(blue.base, raster.colorAt(39, 20))
        assertEquals(red.base, raster.colorAt(40, 20))
        assertEquals(blue.base, raster.colorAt(38, 20))
        assertEquals(red.base, raster.colorAt(41, 20))
    }

    @Test
    fun `same-color pieces also merge across their shared edge`() {
        val raster = rasterize(
            listOf(
                surfaceCell(0, 0, blue, 40),
                surfaceCell(1, 0, blue, 40),
            ),
        )

        assertEquals(blue.base, raster.colorAt(39, 20))
        assertEquals(blue.base, raster.colorAt(40, 20))
        assertEquals(blue.base, raster.colorAt(39, 10))
        assertEquals(blue.base, raster.colorAt(40, 10))
    }

    @Test
    fun `disconnected surfaces keep separate outer rims`() {
        val raster = rasterize(
            listOf(
                surfaceCell(0, 0, blue, 40),
                surfaceCell(2, 0, red, 40, left = 80),
            ),
        )

        assertEquals(outline, raster.colorAt(39, 20))
        assertEquals(0, raster.colorAt(40, 20))
        assertEquals(0, raster.colorAt(79, 20))
        assertEquals(outline, raster.colorAt(80, 20))
    }

    @Test
    fun `concave silhouettes have no transparent or uncovered diagonal seam`() {
        val cells = listOf(
            surfaceCell(1, 0, blue, 40, left = 40),
            surfaceCell(0, 1, blue, 40, top = 40),
            surfaceCell(1, 1, blue, 40, left = 40, top = 40),
            surfaceCell(2, 1, blue, 40, left = 80, top = 40),
        )
        val raster = rasterize(cells)

        assertEquals(4 * 40 * 40, raster.pixels.count { it != 0 })
        cells.forEach { cell ->
            for (y in cell.bounds.top until cell.bounds.bottom) {
                for (x in cell.bounds.left until cell.bounds.right) {
                    assertTrue("transparent pixel at ($x,$y)", raster.colorAt(x, y) ushr 24 == 0xFF)
                }
            }
        }
        assertEquals(0, raster.colorAt(10, 10))
        assertEquals(blue.highlight, raster.colorAt(41, 20))
        assertEquals(blue.highlight, raster.colorAt(20, 41))
    }

    @Test
    fun `small cells remain completely filled with a flat fallback`() {
        val cell = surfaceCell(0, 0, blue, 4)
        val raster = rasterize(listOf(cell))

        assertEquals(0, TengenSurfaceRasterizer.bevelPx(cell.bounds))
        assertEquals(16, raster.pixels.count { it != 0 })
        assertTrue(raster.pixels.all { it ushr 24 == 0xFF })
    }

    private fun rasterize(cells: List<TengenSurfaceCell>): TengenRaster =
        TengenSurfaceRasterizer.rasterize(cells, outline)!!

    private fun surfaceCell(
        x: Int,
        y: Int,
        ramp: OpaqueColorRamp,
        size: Int,
        left: Int = x * size,
        top: Int = y * size,
    ): TengenSurfaceCell = TengenSurfaceCell(
        coordinate = PixelPoint(x, y),
        bounds = PixelRect(left, top, left + size, top + size),
        ramp = ramp,
    )
}
