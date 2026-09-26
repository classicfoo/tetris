package com.classicfoo.tetris.ui

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One board or preview cell, including the tetromino that owns it. */
data class TengenRasterCell(
    val coordinate: PixelPoint,
    val bounds: PixelRect,
    val ramp: OpaqueColorRamp,
    val ownerId: Long,
)

/** Integer ARGB pixels positioned in the view's already-snapped coordinate space. */
data class TengenRasterImage(
    val bounds: PixelRect,
    val pixels: IntArray,
) {
    init {
        require(pixels.size == bounds.width * bounds.height) {
            "Raster pixel count must match its bounds"
        }
    }

    val width: Int get() = bounds.width
    val height: Int get() = bounds.height

    fun colorAt(x: Int, y: Int): Int =
        if (x in bounds.left until bounds.right && y in bounds.top until bounds.bottom) {
            pixels[(y - bounds.top) * width + (x - bounds.left)]
        } else {
            0
        }
}

/**
 * Produces joined, opaque Tengen tetrominoes without shared Canvas paths.
 *
 * Cells with the same owner have no boundary between them. Different owners
 * retain separate bevels, but exactly one side owns the shared one-pixel
 * separator. Every occupied pixel is written once, and concave miters are
 * filled explicitly so independently rasterized polygon edges cannot crack.
 */
object TengenPieceRasterizer {
    private const val BEVEL_RATIO = 0.18f

    fun rasterize(
        cells: Iterable<TengenRasterCell>,
        outlineColor: Int,
        gutterPx: Int = 1,
    ): TengenRasterImage? {
        val entries = cells.toList()
        if (entries.isEmpty()) return null
        require(outlineColor ushr 24 == 0xFF) { "Tengen outline must be opaque" }

        val byCoordinate = entries.associateBy { it.coordinate }
        require(byCoordinate.size == entries.size) { "Raster cells must have unique coordinates" }
        entries.groupBy { it.ownerId }.values.forEach { ownedCells ->
            require(ownedCells.map { it.ramp }.distinct().size == 1) {
                "Every tetromino owner must use one color ramp"
            }
        }
        entries.forEachIndexed { index, cell ->
            entries.drop(index + 1).forEach { other ->
                require(!cell.bounds.intersects(other.bounds)) {
                    "Raster cells must not overlap in pixel space"
                }
            }
        }

        val bounds = PixelRect(
            left = entries.minOf { it.bounds.left },
            top = entries.minOf { it.bounds.top },
            right = entries.maxOf { it.bounds.right },
            bottom = entries.maxOf { it.bounds.bottom },
        )
        val pixels = IntArray(bounds.width * bounds.height)
        val gutter = gutterPx.coerceAtLeast(0)

        entries.forEach { cell ->
            val boundaries = Edge.entries.associateWith { edge ->
                boundaryFor(cell, byCoordinate[edge.neighbor(cell.coordinate)], edge)
            }
            val bevel = bevelPx(cell.bounds, gutter)
            for (y in cell.bounds.top until cell.bounds.bottom) {
                val distances = intArrayOf(
                    y - cell.bounds.top,
                    0,
                    0,
                    cell.bounds.bottom - 1 - y,
                )
                for (x in cell.bounds.left until cell.bounds.right) {
                    distances[Edge.RIGHT.ordinal] = cell.bounds.right - 1 - x
                    distances[Edge.LEFT.ordinal] = x - cell.bounds.left
                    val pixelIndex = (y - bounds.top) * bounds.width + (x - bounds.left)
                    val outline = Edge.entries.any { edge ->
                        val boundary = boundaries.getValue(edge)
                        boundary != null && boundary.ownsOutline && distances[edge.ordinal] < gutter
                    }
                    if (outline) {
                        pixels[pixelIndex] = outlineColor
                        continue
                    }

                    val bevelCandidate = Edge.entries.mapNotNull { edge ->
                        val boundary = boundaries.getValue(edge) ?: return@mapNotNull null
                        val start = if (boundary.ownsOutline) gutter else 0
                        val distance = distances[edge.ordinal]
                        if (distance in start until (start + bevel)) {
                            BevelCandidate(
                                distance = distance - start,
                                edge = edge,
                                color = edge.color(cell.ramp),
                            )
                        } else {
                            null
                        }
                    }.minWithOrNull(compareBy<BevelCandidate> { it.distance }.thenBy { it.edge.priority })
                    pixels[pixelIndex] = bevelCandidate?.color ?: cell.ramp.base
                }
            }
        }

        fillConcaveMiters(entries, bounds, pixels, gutter)
        return TengenRasterImage(bounds, pixels)
    }

    fun bevelPx(cell: PixelRect, gutterPx: Int = 1): Int {
        val gutter = gutterPx.coerceAtLeast(0)
        val usable = min(cell.width, cell.height) - gutter * 2
        if (usable < 3) return 0
        val target = max(1, (min(cell.width, cell.height) * BEVEL_RATIO).roundToInt())
        return min(target, usable / 3)
    }

    private fun boundaryFor(
        cell: TengenRasterCell,
        neighbor: TengenRasterCell?,
        edge: Edge,
    ): Boundary? = when {
        neighbor?.ownerId == cell.ownerId -> null
        neighbor == null -> Boundary(ownsOutline = true)
        else -> Boundary(ownsOutline = edge == Edge.RIGHT || edge == Edge.BOTTOM)
    }

    private fun fillConcaveMiters(
        entries: List<TengenRasterCell>,
        rasterBounds: PixelRect,
        pixels: IntArray,
        gutter: Int,
    ) {
        entries.groupBy { it.ownerId }.values.forEach { ownerCells ->
            val occupied = ownerCells.associateBy { it.coordinate }
            val minX = occupied.keys.minOf { it.x }
            val maxX = occupied.keys.maxOf { it.x }
            val minY = occupied.keys.minOf { it.y }
            val maxY = occupied.keys.maxOf { it.y }

            for (vertexY in minY..(maxY + 1)) {
                for (vertexX in minX..(maxX + 1)) {
                    val quadrants = mapOf(
                        Quadrant.TOP_LEFT to occupied[PixelPoint(vertexX - 1, vertexY - 1)],
                        Quadrant.TOP_RIGHT to occupied[PixelPoint(vertexX, vertexY - 1)],
                        Quadrant.BOTTOM_LEFT to occupied[PixelPoint(vertexX - 1, vertexY)],
                        Quadrant.BOTTOM_RIGHT to occupied[PixelPoint(vertexX, vertexY)],
                    )
                    if (quadrants.values.count { it != null } != 3) continue
                    val missing = quadrants.entries.single { it.value == null }.key
                    val diagonalQuadrant = missing.opposite()
                    val diagonal = quadrants.getValue(diagonalQuadrant) ?: continue
                    val bevel = bevelPx(diagonal.bounds, gutter)
                    if (bevel < 1) continue

                    val vertex = pixelVertex(quadrants)
                    val patch = miterPatch(diagonal.bounds, diagonalQuadrant, bevel)
                    val verticalColor = missing.verticalEdgeColor(diagonal.ramp)
                    val horizontalColor = missing.horizontalEdgeColor(diagonal.ramp)
                    for (y in patch.top until patch.bottom) {
                        for (x in patch.left until patch.right) {
                            val horizontalDistance = if (diagonalQuadrant.isRight) x - vertex.x else vertex.x - 1 - x
                            val verticalDistance = if (diagonalQuadrant.isBottom) y - vertex.y else vertex.y - 1 - y
                            val color = if (horizontalDistance <= verticalDistance) verticalColor else horizontalColor
                            val index = (y - rasterBounds.top) * rasterBounds.width + (x - rasterBounds.left)
                            pixels[index] = color
                        }
                    }
                }
            }
        }
    }

    private fun pixelVertex(quadrants: Map<Quadrant, TengenRasterCell?>): PixelPoint = PixelPoint(
        x = quadrants[Quadrant.TOP_RIGHT]?.bounds?.left
            ?: quadrants[Quadrant.BOTTOM_RIGHT]?.bounds?.left
            ?: quadrants[Quadrant.TOP_LEFT]!!.bounds.right,
        y = quadrants[Quadrant.BOTTOM_LEFT]?.bounds?.top
            ?: quadrants[Quadrant.BOTTOM_RIGHT]?.bounds?.top
            ?: quadrants[Quadrant.TOP_LEFT]!!.bounds.bottom,
    )

    private fun miterPatch(cell: PixelRect, quadrant: Quadrant, bevel: Int): PixelRect = when (quadrant) {
        Quadrant.TOP_LEFT -> PixelRect(cell.right - bevel, cell.bottom - bevel, cell.right, cell.bottom)
        Quadrant.TOP_RIGHT -> PixelRect(cell.left, cell.bottom - bevel, cell.left + bevel, cell.bottom)
        Quadrant.BOTTOM_LEFT -> PixelRect(cell.right - bevel, cell.top, cell.right, cell.top + bevel)
        Quadrant.BOTTOM_RIGHT -> PixelRect(cell.left, cell.top, cell.left + bevel, cell.top + bevel)
    }

    private data class Boundary(val ownsOutline: Boolean)

    private data class BevelCandidate(
        val distance: Int,
        val edge: Edge,
        val color: Int,
    )

    private enum class Edge(val priority: Int) {
        TOP(0),
        LEFT(1),
        RIGHT(2),
        BOTTOM(3);

        fun neighbor(point: PixelPoint): PixelPoint = when (this) {
            TOP -> PixelPoint(point.x, point.y - 1)
            LEFT -> PixelPoint(point.x - 1, point.y)
            RIGHT -> PixelPoint(point.x + 1, point.y)
            BOTTOM -> PixelPoint(point.x, point.y + 1)
        }

        fun color(ramp: OpaqueColorRamp): Int = when (this) {
            TOP, LEFT -> ramp.highlight
            RIGHT, BOTTOM -> ramp.shadow
        }
    }

    private enum class Quadrant(val isRight: Boolean, val isBottom: Boolean) {
        TOP_LEFT(isRight = false, isBottom = false),
        TOP_RIGHT(isRight = true, isBottom = false),
        BOTTOM_LEFT(isRight = false, isBottom = true),
        BOTTOM_RIGHT(isRight = true, isBottom = true);

        fun opposite(): Quadrant = when (this) {
            TOP_LEFT -> BOTTOM_RIGHT
            TOP_RIGHT -> BOTTOM_LEFT
            BOTTOM_LEFT -> TOP_RIGHT
            BOTTOM_RIGHT -> TOP_LEFT
        }

        fun verticalEdgeColor(ramp: OpaqueColorRamp): Int = when (this) {
            TOP_LEFT, BOTTOM_LEFT -> ramp.highlight
            TOP_RIGHT, BOTTOM_RIGHT -> ramp.shadow
        }

        fun horizontalEdgeColor(ramp: OpaqueColorRamp): Int = when (this) {
            TOP_LEFT, TOP_RIGHT -> ramp.highlight
            BOTTOM_LEFT, BOTTOM_RIGHT -> ramp.shadow
        }
    }
}
