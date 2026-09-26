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

enum class TengenBevelShade {
    HIGHLIGHT,
    SHADOW,
}

data class TengenCornerPolygon(
    val polygon: PixelPolygon,
    val shade: TengenBevelShade,
)

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
    val cornerPolygons: List<TengenCornerPolygon> = emptyList(),
) {
    val isFlat: Boolean get() = bevelPx == 0

    val edgePolygons: List<PixelPolygon>
        get() = listOfNotNull(top, left, right, bottom)

    val bevelPolygons: List<PixelPolygon>
        get() = edgePolygons + cornerPolygons.map { it.polygon }
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
        val joins = concaveCornerJoins(byCoordinate, partsByCoordinate)
        return sortedEntries.mapIndexed { index, entry ->
            parts[index].copy(
                cornerPolygons = joins
                    .filter { it.targetCoordinate == entry.coordinate }
                    .flatMap { it.polygons },
            )
        }
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

    private data class ConcaveCornerJoin(
        val vertex: PixelPoint,
        val missing: CornerQuadrant,
        val targetCoordinate: PixelPoint,
        val firstPoint: PixelPoint,
        val secondPoint: PixelPoint,
        val polygons: List<TengenCornerPolygon>,
    )

    private fun concaveCornerJoins(
        occupied: Map<PixelPoint, TengenGridCell>,
        partsByCoordinate: Map<PixelPoint, TengenBevelParts>,
    ): List<ConcaveCornerJoin> {
        val result = mutableListOf<ConcaveCornerJoin>()
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
                val targetCell = quadrants.getValue(diagonal) ?: continue
                val targetCoordinate = targetCell.coordinate
                val vertex = PixelPoint(
                    x = if (quadrants[CornerQuadrant.TOP_RIGHT] != null || quadrants[CornerQuadrant.BOTTOM_RIGHT] != null) {
                        targetCell.bounds.right.takeIf { targetCell.coordinate.x < vertexX } ?:
                            quadrants.values.filterNotNull().first { it.coordinate.x >= vertexX }.bounds.left
                    } else {
                        quadrants.values.filterNotNull().first { it.coordinate.x < vertexX }.bounds.right
                    },
                    y = if (quadrants[CornerQuadrant.BOTTOM_LEFT] != null || quadrants[CornerQuadrant.BOTTOM_RIGHT] != null) {
                        quadrants.values.filterNotNull().first { it.coordinate.y >= vertexY }.bounds.top
                    } else {
                        quadrants.values.filterNotNull().first { it.coordinate.y < vertexY }.bounds.bottom
                    },
                )
                val (firstPoint, secondPoint, firstShade, secondShade) = when (missing) {
                    CornerQuadrant.TOP_LEFT -> QuadJoin(
                        endpoint(partsByCoordinate[quadrants.getValue(CornerQuadrant.TOP_RIGHT)!!.coordinate]?.left, 2) ?: continue,
                        endpoint(partsByCoordinate[quadrants.getValue(CornerQuadrant.BOTTOM_LEFT)!!.coordinate]?.top, 2) ?: continue,
                        TengenBevelShade.HIGHLIGHT,
                        TengenBevelShade.HIGHLIGHT,
                    )
                    CornerQuadrant.TOP_RIGHT -> QuadJoin(
                        endpoint(partsByCoordinate[quadrants.getValue(CornerQuadrant.TOP_LEFT)!!.coordinate]?.right, 2) ?: continue,
                        endpoint(partsByCoordinate[quadrants.getValue(CornerQuadrant.BOTTOM_RIGHT)!!.coordinate]?.top, 3) ?: continue,
                        TengenBevelShade.SHADOW,
                        TengenBevelShade.HIGHLIGHT,
                    )
                    CornerQuadrant.BOTTOM_LEFT -> QuadJoin(
                        endpoint(partsByCoordinate[quadrants.getValue(CornerQuadrant.TOP_LEFT)!!.coordinate]?.bottom, 2) ?: continue,
                        endpoint(partsByCoordinate[quadrants.getValue(CornerQuadrant.BOTTOM_RIGHT)!!.coordinate]?.left, 1) ?: continue,
                        TengenBevelShade.SHADOW,
                        TengenBevelShade.HIGHLIGHT,
                    )
                    CornerQuadrant.BOTTOM_RIGHT -> QuadJoin(
                        endpoint(partsByCoordinate[quadrants.getValue(CornerQuadrant.TOP_RIGHT)!!.coordinate]?.bottom, 3) ?: continue,
                        endpoint(partsByCoordinate[quadrants.getValue(CornerQuadrant.BOTTOM_LEFT)!!.coordinate]?.right, 3) ?: continue,
                        TengenBevelShade.SHADOW,
                        TengenBevelShade.SHADOW,
                    )
                }
                val joins = if (firstShade == secondShade) {
                    listOf(TengenCornerPolygon(polygon(firstPoint, vertex, secondPoint), firstShade))
                } else {
                    val midpoint = PixelPoint(
                        (firstPoint.x + secondPoint.x) / 2,
                        (firstPoint.y + secondPoint.y) / 2,
                    )
                    if (midpoint == firstPoint || midpoint == secondPoint) {
                        listOf(TengenCornerPolygon(polygon(firstPoint, vertex, secondPoint), firstShade))
                    } else {
                        listOf(
                            TengenCornerPolygon(polygon(firstPoint, vertex, midpoint), firstShade),
                            TengenCornerPolygon(polygon(midpoint, vertex, secondPoint), secondShade),
                        )
                    }
                }
                result += ConcaveCornerJoin(
                    vertex = vertex,
                    missing = missing,
                    targetCoordinate = targetCoordinate,
                    firstPoint = firstPoint,
                    secondPoint = secondPoint,
                    polygons = joins,
                )
            }
        }
        return result
    }

    private fun endpoint(polygon: PixelPolygon?, index: Int): PixelPoint? = polygon?.points?.getOrNull(index)

    private data class QuadJoin(
        val firstPoint: PixelPoint,
        val secondPoint: PixelPoint,
        val firstShade: TengenBevelShade,
        val secondShade: TengenBevelShade,
    )

    private fun polygon(vararg points: PixelPoint): PixelPolygon = PixelPolygon(points.toList())
}
