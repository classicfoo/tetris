package com.classicfoo.tetris.ui

import com.classicfoo.tetris.engine.ActivePiece
import com.classicfoo.tetris.engine.Cell
import com.classicfoo.tetris.engine.PieceDefinitions
import com.classicfoo.tetris.engine.Rotation
import com.classicfoo.tetris.engine.Tetromino

/**
 * A half-open rectangle in board-cell coordinates.
 *
 * The rectangle represents every cell with
 * `left <= x < right` and `top <= y < bottom`. It is intentionally a grid
 * primitive rather than an Android drawing type so the renderer can scale it
 * to pixels and fill it without adding per-cell strokes, bevels, or gutters.
 */
data class GhostFillRegion(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(right > left) { "Ghost fill region must have positive width" }
        require(bottom > top) { "Ghost fill region must have positive height" }
    }

    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val cellArea: Int get() = width * height

    fun contains(cell: Cell): Boolean =
        cell.x in left until right && cell.y in top until bottom

    /** Enumerates the occupied board cells covered by this fill region. */
    fun cells(): List<Cell> = buildList {
        for (y in top until bottom) {
            for (x in left until right) add(Cell(x, y))
        }
    }
}

/**
 * Pure grid geometry for Tengen's solid translucent ghost fill.
 *
 * [occupiedCells] is the exact four-cell silhouette. [solidRegions] is a
 * lossless fill-only decomposition of that silhouette into maximal horizontal
 * runs. Adjacent regions share an edge, so a renderer can draw them with one
 * opaque/translucent fill and no per-cell outline or inset. The decomposition
 * never includes a cell outside [occupiedCells].
 */
data class GhostPieceGeometry(
    val occupiedCells: List<Cell>,
    val solidRegions: List<GhostFillRegion>,
) {
    init {
        require(occupiedCells.size == OCCUPIED_CELL_COUNT) {
            "A tetromino ghost must contain exactly four cells"
        }
        require(occupiedCells.distinct().size == OCCUPIED_CELL_COUNT) {
            "A tetromino ghost must not contain duplicate cells"
        }
        val coveredCells = solidRegions.flatMap(GhostFillRegion::cells)
        require(coveredCells.size == OCCUPIED_CELL_COUNT) {
            "Ghost fill regions must cover exactly four cells"
        }
        require(coveredCells.toSet() == occupiedCells.toSet()) {
            "Ghost fill regions must match the occupied cells exactly"
        }
    }

    val minX: Int get() = occupiedCells.minOf { it.x }
    val minY: Int get() = occupiedCells.minOf { it.y }
    val maxX: Int get() = occupiedCells.maxOf { it.x }
    val maxY: Int get() = occupiedCells.maxOf { it.y }
    val width: Int get() = maxX - minX + 1
    val height: Int get() = maxY - minY + 1

    fun occupies(cell: Cell): Boolean = cell in occupiedCells

    companion object {
        private const val OCCUPIED_CELL_COUNT = 4

        /** Builds a world-positioned silhouette for the active/ghost piece. */
        fun from(piece: ActivePiece): GhostPieceGeometry =
            from(piece.type, piece.rotation, piece.x, piece.y)

        /**
         * Builds a silhouette at [originX], [originY] in board-cell
         * coordinates. Defaults to the local origin, which is useful for
         * previews and deterministic geometry tests.
         */
        fun from(
            type: Tetromino,
            rotation: Rotation,
            originX: Int = 0,
            originY: Int = 0,
        ): GhostPieceGeometry {
            val cells = PieceDefinitions.cells(type, rotation)
                .map { Cell(originX + it.x, originY + it.y) }
                .sortedWith(compareBy<Cell> { it.y }.thenBy { it.x })

            return GhostPieceGeometry(
                occupiedCells = cells,
                solidRegions = horizontalRuns(cells),
            )
        }

        private fun horizontalRuns(cells: List<Cell>): List<GhostFillRegion> {
            return cells
                .groupBy { it.y }
                .toSortedMap()
                .flatMap { (y, row) ->
                    val xs = row.map { it.x }.sorted()
                    buildList {
                        var runStart = xs.first()
                        var previous = runStart
                        xs.drop(1).forEach { x ->
                            if (x != previous + 1) {
                                add(GhostFillRegion(runStart, y, previous + 1, y + 1))
                                runStart = x
                            }
                            previous = x
                        }
                        add(GhostFillRegion(runStart, y, previous + 1, y + 1))
                    }
                }
        }
    }
}
