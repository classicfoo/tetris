package com.classicfoo.tetris.ui

import com.classicfoo.tetris.engine.PieceDefinitions
import com.classicfoo.tetris.engine.Rotation
import com.classicfoo.tetris.engine.Tetromino
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TengenPieceSurfaceGeometryTest {
    @Test
    fun `every tetromino rotation uses continuous shade bands`() {
        Tetromino.entries.forEach { type ->
            Rotation.entries.forEach { rotation ->
                val cells = cells(type, rotation)
                val surface = TengenPieceSurfaceGeometry.fromCells(cells)

                assertTrue("$type $rotation should have highlight and shadow bands", surface.bands.map { it.shade }.toSet().containsAll(TengenSurfaceShade.entries.toSet()))
                assertEquals(
                    setOf(TengenSurfaceShade.HIGHLIGHT, TengenSurfaceShade.SHADOW),
                    surface.bands.map { it.shade }.toSet(),
                )
                assertTrue(surface.bands.all { it.polygon.points.size >= 4 })
                assertTrue("$type $rotation should contain a multi-edge bevel band", surface.bands.any { it.polygon.points.size > 4 })
            }
        }
    }

    @Test
    fun `S and T concave turns stay inside one polygon per shade`() {
        listOf(Tetromino.S, Tetromino.T).forEach { type ->
            Rotation.entries.forEach { rotation ->
                val surface = TengenPieceSurfaceGeometry.fromCells(cells(type, rotation))
                assertTrue(surface.bands.any { it.shade == TengenSurfaceShade.HIGHLIGHT && it.polygon.points.size > 4 })
                assertTrue(surface.bands.any { it.shade == TengenSurfaceShade.SHADOW && it.polygon.points.size > 4 })
            }
        }
    }

    private fun cells(type: Tetromino, rotation: Rotation): List<TengenGridCell> {
        val blocks = PieceDefinitions.cells(type, rotation)
        val minX = blocks.minOf { it.x }
        val minY = blocks.minOf { it.y }
        val size = 40
        return blocks.map { block ->
            val x = block.x - minX
            val y = block.y - minY
            TengenGridCell(
                coordinate = PixelPoint(x, y),
                bounds = PixelRect(x * size, y * size, (x + 1) * size, (y + 1) * size),
            )
        }
    }
}
