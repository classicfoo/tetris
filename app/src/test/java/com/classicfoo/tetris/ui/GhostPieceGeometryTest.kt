package com.classicfoo.tetris.ui

import com.classicfoo.tetris.engine.Cell
import com.classicfoo.tetris.engine.Rotation
import com.classicfoo.tetris.engine.Tetromino
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GhostPieceGeometryTest {
    @Test
    fun `every tetromino rotation has four connected occupied cells`() {
        Tetromino.entries.forEach { type ->
            Rotation.entries.forEach { rotation ->
                val geometry = GhostPieceGeometry.from(type, rotation)

                assertEquals("$type $rotation", 4, geometry.occupiedCells.size)
                assertEquals("$type $rotation", 4, geometry.occupiedCells.toSet().size)
                assertEquals(
                    "$type $rotation",
                    geometry.occupiedCells.toSet(),
                    geometry.solidRegions.flatMap { it.cells() }.toSet(),
                )
                assertTrue("$type $rotation", isFourConnected(geometry.occupiedCells.toSet()))
            }
        }
    }

    @Test
    fun `fill regions never cover a cell outside the silhouette`() {
        val geometry = GhostPieceGeometry.from(
            type = Tetromino.T,
            rotation = Rotation.RIGHT,
            originX = 4,
            originY = 12,
        )
        val occupied = geometry.occupiedCells.toSet()

        geometry.solidRegions.forEach { region ->
            assertTrue(region.width > 0)
            assertTrue(region.height > 0)
            region.cells().forEach { cell ->
                assertTrue("Unexpected fill cell $cell", cell in occupied)
            }
        }

        assertEquals(4, geometry.solidRegions.sumOf { it.cellArea })
        assertEquals(Cell(5, 12), geometry.occupiedCells.first())
        assertFalse(geometry.occupies(Cell(0, 0)))
    }

    @Test
    fun `world positioned geometry translates every region without changing shape`() {
        val local = GhostPieceGeometry.from(Tetromino.L, Rotation.LEFT)
        val world = GhostPieceGeometry.from(
            Tetromino.L,
            Rotation.LEFT,
            originX = 7,
            originY = 9,
        )

        val translated = local.occupiedCells
            .map { Cell(it.x + 7, it.y + 9) }
            .toSet()

        assertEquals(translated, world.occupiedCells.toSet())
        assertEquals(4, world.solidRegions.sumOf { it.cellArea })
        assertEquals(7, world.minX)
        assertEquals(9, world.minY)
    }

    @Test
    fun `long horizontal runs are represented as fill regions rather than cell outlines`() {
        val geometry = GhostPieceGeometry.from(Tetromino.I, Rotation.SPAWN)

        assertEquals(
            listOf(GhostFillRegion(left = 0, top = 1, right = 4, bottom = 2)),
            geometry.solidRegions,
        )
        assertEquals(4, geometry.solidRegions.single().cellArea)
    }

    private fun isFourConnected(cells: Set<Cell>): Boolean {
        val visited = mutableSetOf(cells.first())
        val pending = ArrayDeque<Cell>().apply { add(cells.first()) }
        while (pending.isNotEmpty()) {
            val cell = pending.removeFirst()
            listOf(
                Cell(cell.x - 1, cell.y),
                Cell(cell.x + 1, cell.y),
                Cell(cell.x, cell.y - 1),
                Cell(cell.x, cell.y + 1),
            ).filter { it in cells && visited.add(it) }.forEach(pending::addLast)
        }
        return visited.size == cells.size
    }
}
