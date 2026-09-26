package com.classicfoo.tetris.ui

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PixelPoint(val x: Int, val y: Int)

data class PixelRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(right > left) { "PixelRect must have positive width" }
        require(bottom > top) { "PixelRect must have positive height" }
    }

    val width: Int get() = right - left
    val height: Int get() = bottom - top

    fun contains(point: PixelPoint): Boolean =
        point.x >= left && point.x <= right && point.y >= top && point.y <= bottom

    fun intersects(other: PixelRect): Boolean =
        max(left, other.left) < min(right, other.right) &&
            max(top, other.top) < min(bottom, other.bottom)
}

/** A logical cell and its already pixel-snapped drawing rectangle. */
data class TengenGridCell(
    val coordinate: PixelPoint,
    val bounds: PixelRect,
)

data class PixelPolygon(val points: List<PixelPoint>) {
    init {
        require(points.size >= 3) { "PixelPolygon must have at least three points" }
    }

    val bounds: PixelRect
        get() = PixelRect(
            left = points.minOf { it.x },
            top = points.minOf { it.y },
            right = points.maxOf { it.x },
            bottom = points.maxOf { it.y },
        )
}

data class OpaqueColorRamp(
    val base: Int,
    val highlight: Int,
    val shadow: Int,
) {
    init {
        require(listOf(base, highlight, shadow).all { it ushr 24 == 0xFF }) {
            "Tengen block colors must be opaque"
        }
    }
}

object TengenBevelPalette {
    val ramps: Map<com.classicfoo.tetris.engine.Tetromino, OpaqueColorRamp> = mapOf(
        com.classicfoo.tetris.engine.Tetromino.I to OpaqueColorRamp(0xFF23AFC4.toInt(), 0xFF75DFE1.toInt(), 0xFF0F506B.toInt()),
        com.classicfoo.tetris.engine.Tetromino.O to OpaqueColorRamp(0xFFD6A62E.toInt(), 0xFFFFE07A.toInt(), 0xFF774516.toInt()),
        com.classicfoo.tetris.engine.Tetromino.T to OpaqueColorRamp(0xFFA043A8.toInt(), 0xFFD979CF.toInt(), 0xFF55225E.toInt()),
        com.classicfoo.tetris.engine.Tetromino.J to OpaqueColorRamp(0xFF3E64B7.toInt(), 0xFF7FA1E1.toInt(), 0xFF1D356F.toInt()),
        com.classicfoo.tetris.engine.Tetromino.L to OpaqueColorRamp(0xFFD96B32.toInt(), 0xFFF5A05B.toInt(), 0xFF73311F.toInt()),
        com.classicfoo.tetris.engine.Tetromino.S to OpaqueColorRamp(0xFF4EAB57.toInt(), 0xFF89D875.toInt(), 0xFF245931.toInt()),
        com.classicfoo.tetris.engine.Tetromino.Z to OpaqueColorRamp(0xFFC54646.toInt(), 0xFFF0786F.toInt(), 0xFF681F2D.toInt()),
    )
}

/**
 * Integer geometry for a Tengen-style block. The four edge polygons deliberately
 * own separate corner regions, so no highlight or shadow strip is drawn twice.
 */
data class TengenBevelParts(
    val cell: PixelRect,
    val face: PixelRect,
    val bevelPx: Int,
    val top: PixelPolygon?,
    val left: PixelPolygon?,
    val right: PixelPolygon?,
    val bottom: PixelPolygon?,
) {
    val isFlat: Boolean get() = bevelPx == 0

    val edgePolygons: List<PixelPolygon>
        get() = listOfNotNull(top, left, right, bottom)
}

object TengenBevelGeometry {
    /**
     * Tengen's blocks use a conspicuous pixel rim rather than a hairline
     * highlight.  The cap below leaves a real centre face on small preview
     * cells while allowing normal board cells to use a substantially wider
     * bevel.
     */
    private const val BEVEL_RATIO = 0.18f

    fun fromFloat(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        gutterPx: Int = 1,
    ): TengenBevelParts = fromCell(
        left = left.roundToInt(),
        top = top.roundToInt(),
        right = right.roundToInt(),
        bottom = bottom.roundToInt(),
        gutterPx = gutterPx,
    )

    fun fromCell(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        gutterPx: Int = 1,
    ): TengenBevelParts = fromCell(
        cell = PixelRect(left, top, right, bottom),
        exposed = ExposedEdges.ALL,
        gutterPx = gutterPx,
    )

    /**
     * Builds bevel parts for one contiguous tetromino. Cells that share a
     * logical edge do not get an inset, outline, or bevel on that edge. The
     * caller draws all cell silhouettes first, then all faces and bevels, so
     * shared edges are painted as one continuous shape.
     */
    fun fromCells(
        cells: Iterable<TengenGridCell>,
        gutterPx: Int = 1,
    ): List<TengenBevelParts> {
        val entries = cells.toList()
        require(entries.isNotEmpty()) { "A joined Tengen piece needs at least one cell" }
        val byCoordinate = entries.associateBy { it.coordinate }
        require(byCoordinate.size == entries.size) { "Joined Tengen piece cells must be unique" }

        return entries
            .sortedWith(compareBy<TengenGridCell> { it.coordinate.y }.thenBy { it.coordinate.x })
            .map { entry ->
                val coordinate = entry.coordinate
                fromCell(
                    cell = entry.bounds,
                    exposed = ExposedEdges(
                        top = PixelPoint(coordinate.x, coordinate.y - 1) !in byCoordinate,
                        left = PixelPoint(coordinate.x - 1, coordinate.y) !in byCoordinate,
                        right = PixelPoint(coordinate.x + 1, coordinate.y) !in byCoordinate,
                        bottom = PixelPoint(coordinate.x, coordinate.y + 1) !in byCoordinate,
                    ),
                    corners = CornerJoins(
                        topLeft = cornerJoin(
                            byCoordinate,
                            coordinate,
                            corner = Corner.TOP_LEFT,
                        ),
                        topRight = cornerJoin(
                            byCoordinate,
                            coordinate,
                            corner = Corner.TOP_RIGHT,
                        ),
                        bottomLeft = cornerJoin(
                            byCoordinate,
                            coordinate,
                            corner = Corner.BOTTOM_LEFT,
                        ),
                        bottomRight = cornerJoin(
                            byCoordinate,
                            coordinate,
                            corner = Corner.BOTTOM_RIGHT,
                        ),
                    ),
                    gutterPx = gutterPx,
                )
            }
    }

    private fun fromCell(
        cell: PixelRect,
        exposed: ExposedEdges,
        corners: CornerJoins = CornerJoins.NONE,
        gutterPx: Int,
    ): TengenBevelParts {
        val gutter = gutterPx.coerceAtLeast(0)
        val canInset = cell.width > gutter * 2 && cell.height > gutter * 2
        val face = PixelRect(
            left = cell.left + if (canInset && exposed.left) gutter else 0,
            top = cell.top + if (canInset && exposed.top) gutter else 0,
            right = cell.right - if (canInset && exposed.right) gutter else 0,
            bottom = cell.bottom - if (canInset && exposed.bottom) gutter else 0,
        )
        val maximumBevel = min(face.width / 3, face.height / 3)
        val targetBevel = max(1, (min(cell.width, cell.height) * BEVEL_RATIO).roundToInt())
        val bevel = min(targetBevel, maximumBevel)
        if (bevel < 1) {
            return TengenBevelParts(cell, face, 0, null, null, null, null)
        }

        val l = face.left
        val t = face.top
        val r = face.right
        val b = face.bottom
        val innerLeft = l + bevel
        val innerTop = t + bevel
        val innerRight = r - bevel
        val innerBottom = b - bevel

        return TengenBevelParts(
            cell = cell,
            face = face,
            bevelPx = bevel,
            top = if (exposed.top) {
                // A concave endpoint uses the inward miter too. This keeps a
                // notch's highlight on the same diagonal contour as the
                // neighboring edge instead of leaving a square step at the
                // join. Convex corners still use the top edge's shared
                // diagonal ownership.
                polygon(
                    PixelPoint(l, t),
                    PixelPoint(r, t),
                    topInnerRight(exposed, corners.topRight, innerRight, innerTop, r),
                    topInnerLeft(exposed, corners.topLeft, innerLeft, innerTop, l),
                )
            } else {
                null
            },
            left = if (exposed.left) {
                polygon(
                    PixelPoint(l, t),
                    leftInnerTop(exposed, corners.topLeft, innerLeft, innerTop, t),
                    leftInnerBottom(exposed, corners.bottomLeft, innerLeft, innerBottom, b),
                    PixelPoint(l, b),
                )
            } else {
                null
            },
            right = if (exposed.right) {
                polygon(
                    PixelPoint(r, t),
                    PixelPoint(r, b),
                    rightInnerBottom(exposed, corners.bottomRight, innerRight, innerBottom, b),
                    rightInnerTop(exposed, corners.topRight, innerRight, innerTop, t),
                )
            } else {
                null
            },
            bottom = if (exposed.bottom) {
                polygon(
                    PixelPoint(l, b),
                    PixelPoint(r, b),
                    bottomInnerRight(exposed, corners.bottomRight, innerRight, innerBottom, r),
                    bottomInnerLeft(exposed, corners.bottomLeft, innerLeft, innerBottom, l),
                )
            } else {
                null
            },
        )
    }

    private data class ExposedEdges(
        val top: Boolean,
        val left: Boolean,
        val right: Boolean,
        val bottom: Boolean,
    ) {
        companion object {
            val ALL = ExposedEdges(top = true, left = true, right = true, bottom = true)
        }
    }

    /** The four local quadrants around a cell corner. */
    private enum class Corner {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
    }

    /**
     * A three-cell corner is a concave notch in the joined silhouette.  The
     * bevel at that corner must turn inward, rather than ending in a square
     * cap.  Convex and neutral corners retain the normal edge ownership.
     */
    private enum class CornerJoin {
        NONE,
        CONCAVE,
    }

    private data class CornerJoins(
        val topLeft: CornerJoin,
        val topRight: CornerJoin,
        val bottomLeft: CornerJoin,
        val bottomRight: CornerJoin,
    ) {
        companion object {
            val NONE = CornerJoins(
                topLeft = CornerJoin.NONE,
                topRight = CornerJoin.NONE,
                bottomLeft = CornerJoin.NONE,
                bottomRight = CornerJoin.NONE,
            )
        }
    }

    private fun cornerJoin(
        occupied: Map<PixelPoint, TengenGridCell>,
        coordinate: PixelPoint,
        corner: Corner,
    ): CornerJoin {
        val quadrants = when (corner) {
            Corner.TOP_LEFT -> listOf(
                PixelPoint(coordinate.x, coordinate.y),
                PixelPoint(coordinate.x, coordinate.y - 1),
                PixelPoint(coordinate.x - 1, coordinate.y),
                PixelPoint(coordinate.x - 1, coordinate.y - 1),
            )
            Corner.TOP_RIGHT -> listOf(
                PixelPoint(coordinate.x, coordinate.y),
                PixelPoint(coordinate.x, coordinate.y - 1),
                PixelPoint(coordinate.x + 1, coordinate.y),
                PixelPoint(coordinate.x + 1, coordinate.y - 1),
            )
            Corner.BOTTOM_LEFT -> listOf(
                PixelPoint(coordinate.x, coordinate.y),
                PixelPoint(coordinate.x, coordinate.y + 1),
                PixelPoint(coordinate.x - 1, coordinate.y),
                PixelPoint(coordinate.x - 1, coordinate.y + 1),
            )
            Corner.BOTTOM_RIGHT -> listOf(
                PixelPoint(coordinate.x, coordinate.y),
                PixelPoint(coordinate.x, coordinate.y + 1),
                PixelPoint(coordinate.x + 1, coordinate.y),
                PixelPoint(coordinate.x + 1, coordinate.y + 1),
            )
        }
        return if (quadrants.count { it in occupied } == 3) CornerJoin.CONCAVE else CornerJoin.NONE
    }

    private fun topInnerLeft(
        exposed: ExposedEdges,
        corner: CornerJoin,
        innerLeft: Int,
        innerTop: Int,
        outerLeft: Int,
    ): PixelPoint = if (exposed.left || corner == CornerJoin.CONCAVE) {
        PixelPoint(innerLeft, innerTop)
    } else {
        PixelPoint(outerLeft, innerTop)
    }

    private fun topInnerRight(
        exposed: ExposedEdges,
        corner: CornerJoin,
        innerRight: Int,
        innerTop: Int,
        outerRight: Int,
    ): PixelPoint = if (exposed.right || corner == CornerJoin.CONCAVE) {
        PixelPoint(innerRight, innerTop)
    } else {
        PixelPoint(outerRight, innerTop)
    }

    private fun leftInnerTop(
        exposed: ExposedEdges,
        corner: CornerJoin,
        innerLeft: Int,
        innerTop: Int,
        outerTop: Int,
    ): PixelPoint = if (exposed.top || corner == CornerJoin.CONCAVE) {
        PixelPoint(innerLeft, innerTop)
    } else {
        PixelPoint(innerLeft, outerTop)
    }

    private fun leftInnerBottom(
        exposed: ExposedEdges,
        corner: CornerJoin,
        innerLeft: Int,
        innerBottom: Int,
        outerBottom: Int,
    ): PixelPoint = if (exposed.bottom || corner == CornerJoin.CONCAVE) {
        PixelPoint(innerLeft, innerBottom)
    } else {
        PixelPoint(innerLeft, outerBottom)
    }

    private fun rightInnerTop(
        exposed: ExposedEdges,
        corner: CornerJoin,
        innerRight: Int,
        innerTop: Int,
        outerTop: Int,
    ): PixelPoint = if (exposed.top || corner == CornerJoin.CONCAVE) {
        PixelPoint(innerRight, innerTop)
    } else {
        PixelPoint(innerRight, outerTop)
    }

    private fun rightInnerBottom(
        exposed: ExposedEdges,
        corner: CornerJoin,
        innerRight: Int,
        innerBottom: Int,
        outerBottom: Int,
    ): PixelPoint = if (exposed.bottom || corner == CornerJoin.CONCAVE) {
        PixelPoint(innerRight, innerBottom)
    } else {
        PixelPoint(innerRight, outerBottom)
    }

    private fun bottomInnerLeft(
        exposed: ExposedEdges,
        corner: CornerJoin,
        innerLeft: Int,
        innerBottom: Int,
        outerLeft: Int,
    ): PixelPoint = if (exposed.left || corner == CornerJoin.CONCAVE) {
        PixelPoint(innerLeft, innerBottom)
    } else {
        PixelPoint(outerLeft, innerBottom)
    }

    private fun bottomInnerRight(
        exposed: ExposedEdges,
        corner: CornerJoin,
        innerRight: Int,
        innerBottom: Int,
        outerRight: Int,
    ): PixelPoint = if (exposed.right || corner == CornerJoin.CONCAVE) {
        PixelPoint(innerRight, innerBottom)
    } else {
        PixelPoint(outerRight, innerBottom)
    }

    private fun polygon(vararg points: PixelPoint): PixelPolygon = PixelPolygon(points.toList())
}
