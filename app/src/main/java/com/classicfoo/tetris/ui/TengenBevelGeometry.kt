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
 * Integer geometry for Tengen-style blocks. Joined cells share their base face,
 * while exposed bevel polygons meet at calculated inside-miter endpoints.
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
    ).withConvexCorners()

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

        val sortedEntries = entries
            .sortedWith(compareBy<TengenGridCell> { it.coordinate.y }.thenBy { it.coordinate.x })
        val parts = sortedEntries
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
                    gutterPx = gutterPx,
                )
            }

        val partsByCoordinate = sortedEntries
            .map { it.coordinate }
            .zip(parts)
            .toMap()
        val indexByCoordinate = sortedEntries.mapIndexed { index, entry -> entry.coordinate to index }.toMap()
        val adjusted = parts.toMutableList()
        concaveMiterAdjustments(byCoordinate, partsByCoordinate).forEach { adjustment ->
            val index = indexByCoordinate[adjustment.coordinate] ?: return@forEach
            adjusted[index] = adjusted[index]
                .withEdgePoint(
                    edge = adjustment.edge,
                    pointIndex = adjustment.pointIndex,
                    point = adjustment.miter,
                )
                .withInsertedEdgePoint(
                    edge = adjustment.edge,
                    pointIndex = adjustment.insertBeforePointIndex,
                    point = adjustment.corner,
                )
        }
        return adjusted.map { it.withConvexCorners() }
    }

    private fun fromCell(
        cell: PixelRect,
        exposed: ExposedEdges,
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
                polygon(
                    PixelPoint(l, t),
                    PixelPoint(r, t),
                    PixelPoint(if (exposed.right) innerRight else r, innerTop),
                    PixelPoint(if (exposed.left) innerLeft else l, innerTop),
                )
            } else {
                null
            },
            left = if (exposed.left) {
                polygon(
                    PixelPoint(l, t),
                    PixelPoint(innerLeft, if (exposed.top) innerTop else t),
                    PixelPoint(innerLeft, if (exposed.bottom) innerBottom else b),
                    PixelPoint(l, b),
                )
            } else {
                null
            },
            right = if (exposed.right) {
                polygon(
                    PixelPoint(r, t),
                    PixelPoint(r, b),
                    PixelPoint(innerRight, if (exposed.bottom) innerBottom else b),
                    PixelPoint(innerRight, if (exposed.top) innerTop else t),
                )
            } else {
                null
            },
            bottom = if (exposed.bottom) {
                polygon(
                    PixelPoint(l, b),
                    PixelPoint(r, b),
                    PixelPoint(if (exposed.right) innerRight else r, innerBottom),
                    PixelPoint(if (exposed.left) innerLeft else l, innerBottom),
                )
            } else {
                null
            },
        )
    }

    /**
     * Extends each exposed convex corner to the snapped cell corner. This is
     * deliberately applied after inside-miter adjustments so the concave
     * point indices remain stable while they are calculated.
     */
    private fun TengenBevelParts.withConvexCorners(): TengenBevelParts {
        if (isFlat) return this

        var result = this
        if (top != null && right != null) {
            result = result
                .withInsertedPointAfter(
                    Edge.TOP,
                    PixelPoint(face.right, face.top),
                    PixelPoint(cell.right, cell.top),
                )
                .withInsertedEdgePoint(Edge.RIGHT, result.right!!.points.size, PixelPoint(cell.right, cell.top))
        }
        if (top != null && left != null) {
            result = result
                .withInsertedEdgePoint(Edge.TOP, result.top!!.points.size, PixelPoint(cell.left, cell.top))
                .withInsertedPointAfter(
                    Edge.LEFT,
                    PixelPoint(face.left, face.top),
                    PixelPoint(cell.left, cell.top),
                )
        }
        if (bottom != null && right != null) {
            result = result
                .withInsertedPointAfter(
                    Edge.BOTTOM,
                    PixelPoint(face.right, face.bottom),
                    PixelPoint(cell.right, cell.bottom),
                )
                .withInsertedPointAfter(
                    Edge.RIGHT,
                    PixelPoint(face.right, face.bottom),
                    PixelPoint(cell.right, cell.bottom),
                )
        }
        if (bottom != null && left != null) {
            result = result
                .withInsertedEdgePoint(Edge.BOTTOM, result.bottom!!.points.size, PixelPoint(cell.left, cell.bottom))
                .withInsertedPointBefore(
                    Edge.LEFT,
                    PixelPoint(face.left, face.bottom),
                    PixelPoint(cell.left, cell.bottom),
                )
        }
        return result
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

    private enum class CornerQuadrant {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
    }

    private enum class Edge {
        TOP,
        LEFT,
        RIGHT,
        BOTTOM,
    }

    private data class MiterAdjustment(
        val coordinate: PixelPoint,
        val edge: Edge,
        val pointIndex: Int,
        val miter: PixelPoint,
        val insertBeforePointIndex: Int,
        val corner: PixelPoint,
    )

    private data class MiterBoundary(
        val firstCoordinate: PixelPoint,
        val firstEdge: Edge,
        val firstPointIndex: Int,
        val firstCornerPointIndex: Int,
        val secondCoordinate: PixelPoint,
        val secondEdge: Edge,
        val secondPointIndex: Int,
        val secondCornerPointIndex: Int,
    )

    private fun concaveMiterAdjustments(
        occupied: Map<PixelPoint, TengenGridCell>,
        partsByCoordinate: Map<PixelPoint, TengenBevelParts>,
    ): List<MiterAdjustment> {
        val result = mutableListOf<MiterAdjustment>()
        val minX = occupied.keys.minOf { it.x }
        val maxX = occupied.keys.maxOf { it.x }
        val minY = occupied.keys.minOf { it.y }
        val maxY = occupied.keys.maxOf { it.y }

        for (vertexY in minY..(maxY + 1)) {
            for (vertexX in minX..(maxX + 1)) {
                val quadrants = mapOf(
                    CornerQuadrant.TOP_LEFT to occupied[PixelPoint(vertexX - 1, vertexY - 1)],
                    CornerQuadrant.TOP_RIGHT to occupied[PixelPoint(vertexX, vertexY - 1)],
                    CornerQuadrant.BOTTOM_LEFT to occupied[PixelPoint(vertexX - 1, vertexY)],
                    CornerQuadrant.BOTTOM_RIGHT to occupied[PixelPoint(vertexX, vertexY)],
                )
                if (quadrants.values.count { it != null } != 3) continue

                val missing = quadrants.entries.single { it.value == null }.key
                val diagonal = when (missing) {
                    CornerQuadrant.TOP_LEFT -> CornerQuadrant.BOTTOM_RIGHT
                    CornerQuadrant.TOP_RIGHT -> CornerQuadrant.BOTTOM_LEFT
                    CornerQuadrant.BOTTOM_LEFT -> CornerQuadrant.TOP_RIGHT
                    CornerQuadrant.BOTTOM_RIGHT -> CornerQuadrant.TOP_LEFT
                }
                quadrants.getValue(diagonal) ?: continue
                val boundary = when (missing) {
                    CornerQuadrant.TOP_LEFT -> MiterBoundary(
                        firstCoordinate = quadrants.getValue(CornerQuadrant.TOP_RIGHT)!!.coordinate,
                        firstEdge = Edge.LEFT,
                        firstPointIndex = 2,
                        firstCornerPointIndex = 3,
                        secondCoordinate = quadrants.getValue(CornerQuadrant.BOTTOM_LEFT)!!.coordinate,
                        secondEdge = Edge.TOP,
                        secondPointIndex = 2,
                        secondCornerPointIndex = 1,
                    )
                    CornerQuadrant.TOP_RIGHT -> MiterBoundary(
                        firstCoordinate = quadrants.getValue(CornerQuadrant.TOP_LEFT)!!.coordinate,
                        firstEdge = Edge.RIGHT,
                        firstPointIndex = 2,
                        firstCornerPointIndex = 1,
                        secondCoordinate = quadrants.getValue(CornerQuadrant.BOTTOM_RIGHT)!!.coordinate,
                        secondEdge = Edge.TOP,
                        secondPointIndex = 3,
                        secondCornerPointIndex = 0,
                    )
                    CornerQuadrant.BOTTOM_LEFT -> MiterBoundary(
                        firstCoordinate = quadrants.getValue(CornerQuadrant.TOP_LEFT)!!.coordinate,
                        firstEdge = Edge.BOTTOM,
                        firstPointIndex = 2,
                        firstCornerPointIndex = 1,
                        secondCoordinate = quadrants.getValue(CornerQuadrant.BOTTOM_RIGHT)!!.coordinate,
                        secondEdge = Edge.LEFT,
                        secondPointIndex = 1,
                        secondCornerPointIndex = 0,
                    )
                    CornerQuadrant.BOTTOM_RIGHT -> MiterBoundary(
                        firstCoordinate = quadrants.getValue(CornerQuadrant.TOP_RIGHT)!!.coordinate,
                        firstEdge = Edge.BOTTOM,
                        firstPointIndex = 3,
                        firstCornerPointIndex = 0,
                        secondCoordinate = quadrants.getValue(CornerQuadrant.BOTTOM_LEFT)!!.coordinate,
                        secondEdge = Edge.RIGHT,
                        secondPointIndex = 3,
                        secondCornerPointIndex = 0,
                    )
                }
                val firstPoint = endpoint(
                    partsByCoordinate[boundary.firstCoordinate]?.edge(boundary.firstEdge),
                    boundary.firstPointIndex,
                ) ?: continue
                val secondPoint = endpoint(
                    partsByCoordinate[boundary.secondCoordinate]?.edge(boundary.secondEdge),
                    boundary.secondPointIndex,
                ) ?: continue
                val miter = when (missing) {
                    CornerQuadrant.TOP_LEFT,
                    CornerQuadrant.TOP_RIGHT,
                    -> PixelPoint(firstPoint.x, secondPoint.y)
                    CornerQuadrant.BOTTOM_LEFT,
                    CornerQuadrant.BOTTOM_RIGHT,
                    -> PixelPoint(secondPoint.x, firstPoint.y)
                }
                val corner = concaveCorner(quadrants)
                result += MiterAdjustment(
                    coordinate = boundary.firstCoordinate,
                    edge = boundary.firstEdge,
                    pointIndex = boundary.firstPointIndex,
                    miter = miter,
                    insertBeforePointIndex = cornerInsertionIndex(
                        pointIndex = boundary.firstPointIndex,
                        cornerPointIndex = boundary.firstCornerPointIndex,
                    ),
                    corner = corner,
                )
                result += MiterAdjustment(
                    coordinate = boundary.secondCoordinate,
                    edge = boundary.secondEdge,
                    pointIndex = boundary.secondPointIndex,
                    miter = miter,
                    insertBeforePointIndex = cornerInsertionIndex(
                        pointIndex = boundary.secondPointIndex,
                        cornerPointIndex = boundary.secondCornerPointIndex,
                    ),
                    corner = corner,
                )
            }
        }
        return result
    }

    private fun cornerInsertionIndex(pointIndex: Int, cornerPointIndex: Int): Int =
        if (cornerPointIndex == 0 && pointIndex == 3) 0 else maxOf(pointIndex, cornerPointIndex)

    private fun concaveCorner(
        quadrants: Map<CornerQuadrant, TengenGridCell?>,
    ): PixelPoint {
        val vertexX = listOfNotNull(
            quadrants[CornerQuadrant.TOP_LEFT]?.bounds?.right,
            quadrants[CornerQuadrant.TOP_RIGHT]?.bounds?.left,
            quadrants[CornerQuadrant.BOTTOM_LEFT]?.bounds?.right,
            quadrants[CornerQuadrant.BOTTOM_RIGHT]?.bounds?.left,
        ).first()
        val vertexY = listOfNotNull(
            quadrants[CornerQuadrant.TOP_LEFT]?.bounds?.bottom,
            quadrants[CornerQuadrant.TOP_RIGHT]?.bounds?.bottom,
            quadrants[CornerQuadrant.BOTTOM_LEFT]?.bounds?.top,
            quadrants[CornerQuadrant.BOTTOM_RIGHT]?.bounds?.top,
        ).first()
        // Keep the original outer corner in each bevel polygon and insert
        // this exact shared vertex between it and the miter. That extends the
        // bevel across the one-pixel inset without introducing a non-45° edge.
        return PixelPoint(vertexX, vertexY)
    }

    private fun endpoint(polygon: PixelPolygon?, index: Int): PixelPoint? = polygon?.points?.getOrNull(index)

    private fun TengenBevelParts.edge(edge: Edge): PixelPolygon? = when (edge) {
        Edge.TOP -> top
        Edge.LEFT -> left
        Edge.RIGHT -> right
        Edge.BOTTOM -> bottom
    }

    private fun TengenBevelParts.withEdgePoint(
        edge: Edge,
        pointIndex: Int,
        point: PixelPoint,
    ): TengenBevelParts = when (edge) {
        Edge.TOP -> copy(top = top?.replacePoint(pointIndex, point))
        Edge.LEFT -> copy(left = left?.replacePoint(pointIndex, point))
        Edge.RIGHT -> copy(right = right?.replacePoint(pointIndex, point))
        Edge.BOTTOM -> copy(bottom = bottom?.replacePoint(pointIndex, point))
    }

    private fun TengenBevelParts.withInsertedEdgePoint(
        edge: Edge,
        pointIndex: Int,
        point: PixelPoint,
    ): TengenBevelParts = when (edge) {
        Edge.TOP -> copy(top = top?.insertPoint(pointIndex, point))
        Edge.LEFT -> copy(left = left?.insertPoint(pointIndex, point))
        Edge.RIGHT -> copy(right = right?.insertPoint(pointIndex, point))
        Edge.BOTTOM -> copy(bottom = bottom?.insertPoint(pointIndex, point))
    }

    private fun TengenBevelParts.withInsertedPointAfter(
        edge: Edge,
        anchor: PixelPoint,
        point: PixelPoint,
    ): TengenBevelParts {
        val index = edge(edge)?.points?.indexOf(anchor) ?: -1
        return if (index >= 0) withInsertedEdgePoint(edge, index + 1, point) else this
    }

    private fun TengenBevelParts.withInsertedPointBefore(
        edge: Edge,
        anchor: PixelPoint,
        point: PixelPoint,
    ): TengenBevelParts {
        val index = edge(edge)?.points?.indexOf(anchor) ?: -1
        return if (index >= 0) withInsertedEdgePoint(edge, index, point) else this
    }

    private fun PixelPolygon.replacePoint(index: Int, point: PixelPoint): PixelPolygon {
        if (index !in points.indices) return this
        return copy(points = points.toMutableList().also { it[index] = point })
    }

    private fun PixelPolygon.insertPoint(index: Int, point: PixelPoint): PixelPolygon {
        if (index !in 0..points.size) return this
        return copy(points = points.toMutableList().also { it.add(index, point) })
    }

    private fun polygon(vararg points: PixelPoint): PixelPolygon = PixelPolygon(points.toList())
}
