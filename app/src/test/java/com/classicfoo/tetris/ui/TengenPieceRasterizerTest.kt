package com.classicfoo.tetris.ui

import com.classicfoo.tetris.engine.PieceDefinitions
import com.classicfoo.tetris.engine.Rotation
import com.classicfoo.tetris.engine.Tetromino
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TengenPieceRasterizerTest {
    private val outline = 0xFF050A17.toInt()
    private val blue = TengenBevelPalette.ramps.getValue(Tetromino.J)
    private val red = TengenBevelPalette.ramps.getValue(Tetromino.Z)

    @Test
    fun `every rotation is opaque and joined only within its tetromino`() {
        Tetromino.entries.forEach { type ->
            Rotation.entries.forEach { rotation ->
                val shape = PieceDefinitions.cells(type, rotation)
                val minX = shape.minOf { it.x }
                val minY = shape.minOf { it.y }
                val ramp = TengenBevelPalette.ramps.getValue(type)
                val cells = shape.map { block ->
                    cell(block.x - minX, block.y - minY, owner = 7L, ramp = ramp, size = 24)
                }
                val raster = rasterize(cells)

                assertEquals(shape.size * 24 * 24, raster.pixels.count { it != 0 })
                assertTrue(raster.pixels.filter { it != 0 }.all { it ushr 24 == 0xFF })
                cells.forEach { first ->
                    cells.filter { it.coordinate == PixelPoint(first.coordinate.x + 1, first.coordinate.y) }
                        .forEach { second ->
                            val y = (maxOf(first.bounds.top, second.bounds.top) + minOf(first.bounds.bottom, second.bounds.bottom)) / 2
                            assertEquals(ramp.base, raster.colorAt(first.bounds.right - 1, y))
                            assertEquals(ramp.base, raster.colorAt(second.bounds.left, y))
                        }
                    cells.filter { it.coordinate == PixelPoint(first.coordinate.x, first.coordinate.y + 1) }
                        .forEach { second ->
                            val x = (maxOf(first.bounds.left, second.bounds.left) + minOf(first.bounds.right, second.bounds.right)) / 2
                            assertEquals(ramp.base, raster.colorAt(x, first.bounds.bottom - 1))
                            assertEquals(ramp.base, raster.colorAt(x, second.bounds.top))
                        }
                }
            }
        }
    }

    @Test
    fun `different tetrominoes share exactly one crisp separator`() {
        val raster = rasterize(
            listOf(
                cell(0, 0, owner = 1L, ramp = blue),
                cell(1, 0, owner = 2L, ramp = red),
            ),
        )

        assertEquals(blue.shadow, raster.colorAt(38, 20))
        assertEquals(outline, raster.colorAt(39, 20))
        assertEquals(red.highlight, raster.colorAt(40, 20))
        assertEquals(red.highlight, raster.colorAt(41, 20))
        assertEquals(1, listOf(raster.colorAt(39, 20), raster.colorAt(40, 20)).count { it == outline })
    }

    @Test
    fun `same-color tetrominoes remain visually separate by owner`() {
        val raster = rasterize(
            listOf(
                cell(0, 0, owner = 10L, ramp = blue),
                cell(1, 0, owner = 11L, ramp = blue),
            ),
        )

        assertEquals(outline, raster.colorAt(39, 20))
        assertEquals(blue.highlight, raster.colorAt(40, 20))
        assertFalse(raster.colorAt(39, 20) == blue.base && raster.colorAt(40, 20) == blue.base)
    }

    @Test
    fun `mixed concave miter is fully covered by joined highlight and shadow`() {
        val cells = listOf(
            cell(0, 0, owner = 3L, ramp = blue),
            cell(0, 1, owner = 3L, ramp = blue),
            cell(1, 1, owner = 3L, ramp = blue),
        )
        val raster = rasterize(cells)
        val bevel = TengenPieceRasterizer.bevelPx(cells.first().bounds)
        val colors = buildSet {
            for (y in 40 until 40 + bevel) {
                for (x in 40 - bevel until 40) add(raster.colorAt(x, y))
            }
        }

        assertEquals(setOf(blue.highlight, blue.shadow), colors)
        assertEquals(blue.highlight, raster.colorAt(40 - bevel, 40))
        assertEquals(blue.shadow, raster.colorAt(39, 40))
        assertEquals(blue.shadow, raster.colorAt(39, 40 + bevel - 1))
        assertFalse(colors.contains(0))
        assertFalse(colors.contains(outline))
        assertFalse(colors.contains(blue.base))
    }

    @Test
    fun `same-shade concave miter contains no base-colored diagonal`() {
        val cells = listOf(
            cell(1, 0, owner = 4L, ramp = blue),
            cell(0, 1, owner = 4L, ramp = blue),
            cell(1, 1, owner = 4L, ramp = blue),
        )
        val raster = rasterize(cells)
        val bevel = TengenPieceRasterizer.bevelPx(cells.first().bounds)

        for (y in 40 until 40 + bevel) {
            for (x in 40 until 40 + bevel) {
                assertEquals(blue.highlight, raster.colorAt(x, y))
            }
        }
    }

    @Test
    fun `dense multi-owner stack keeps all occupied pixels opaque`() {
        val cells = listOf(
            cell(0, 1, owner = 1L, ramp = blue),
            cell(1, 1, owner = 1L, ramp = blue),
            cell(0, 2, owner = 1L, ramp = blue),
            cell(1, 2, owner = 1L, ramp = blue),
            cell(2, 1, owner = 2L, ramp = red),
            cell(2, 2, owner = 2L, ramp = red),
            cell(3, 2, owner = 2L, ramp = red),
            cell(3, 3, owner = 2L, ramp = red),
        )
        val raster = rasterize(cells)

        assertEquals(cells.size * 40 * 40, raster.pixels.count { it != 0 })
        cells.forEach { cell ->
            for (y in cell.bounds.top until cell.bounds.bottom) {
                for (x in cell.bounds.left until cell.bounds.right) {
                    assertEquals(0xFF, raster.colorAt(x, y) ushr 24)
                }
            }
        }
    }

    @Test
    fun `tiny preview cells use a complete flat fallback`() {
        val cell = cell(0, 0, owner = 1L, ramp = blue, size = 4)
        val raster = rasterize(listOf(cell))

        assertEquals(0, TengenPieceRasterizer.bevelPx(cell.bounds))
        assertEquals(16, raster.pixels.count { it != 0 })
        assertTrue(raster.pixels.all { it ushr 24 == 0xFF })
    }

    private fun rasterize(cells: List<TengenRasterCell>): TengenRasterImage =
        TengenPieceRasterizer.rasterize(cells, outline)!!

    private fun cell(
        x: Int,
        y: Int,
        owner: Long,
        ramp: OpaqueColorRamp,
        size: Int = 40,
    ): TengenRasterCell = TengenRasterCell(
        coordinate = PixelPoint(x, y),
        bounds = PixelRect(x * size, y * size, (x + 1) * size, (y + 1) * size),
        ramp = ramp,
        ownerId = owner,
    )
}
