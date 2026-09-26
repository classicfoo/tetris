package com.classicfoo.tetris.ui

import com.classicfoo.tetris.engine.Tetromino
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

    fun containsPixel(x: Int, y: Int): Boolean =
        x in left until right && y in top until bottom

    fun intersects(other: PixelRect): Boolean =
        max(left, other.left) < min(right, other.right) &&
            max(top, other.top) < min(bottom, other.bottom)
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
    val ramps: Map<Tetromino, OpaqueColorRamp> = mapOf(
        Tetromino.I to OpaqueColorRamp(0xFF23AFC4.toInt(), 0xFF75DFE1.toInt(), 0xFF0F506B.toInt()),
        Tetromino.O to OpaqueColorRamp(0xFFD6A62E.toInt(), 0xFFFFE07A.toInt(), 0xFF774516.toInt()),
        Tetromino.T to OpaqueColorRamp(0xFFA043A8.toInt(), 0xFFD979CF.toInt(), 0xFF55225E.toInt()),
        Tetromino.J to OpaqueColorRamp(0xFF3E64B7.toInt(), 0xFF7FA1E1.toInt(), 0xFF1D356F.toInt()),
        Tetromino.L to OpaqueColorRamp(0xFFD96B32.toInt(), 0xFFF5A05B.toInt(), 0xFF73311F.toInt()),
        Tetromino.S to OpaqueColorRamp(0xFF4EAB57.toInt(), 0xFF89D875.toInt(), 0xFF245931.toInt()),
        Tetromino.Z to OpaqueColorRamp(0xFFC54646.toInt(), 0xFFF0786F.toInt(), 0xFF681F2D.toInt()),
    )
}

/** A logical cell with an integer-aligned pixel rectangle and its material. */
data class TengenSurfaceCell(
    val coordinate: PixelPoint,
    val bounds: PixelRect,
    val ramp: OpaqueColorRamp,
)

/** An opaque pixel surface with transparent pixels only outside its silhouette. */
data class TengenRaster(
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

    /** Returns zero outside the surface, which is transparent in Android ARGB. */
    fun colorAt(x: Int, y: Int): Int =
        if (bounds.containsPixel(x, y)) {
            pixels[(y - bounds.top) * width + (x - bounds.left)]
        } else {
            0
        }
}

/**
 * Rasterizes a complete Tengen surface into an opaque integer pixel mask.
 *
 * The renderer deliberately does not construct adjacent polygons. Every pixel
 * belongs to exactly one logical cell and is assigned exactly one final color.
 * An edge is beveled only when its neighboring logical cell is empty, so the
 * locked board naturally becomes one continuous surface even when its cells
 * came from different tetrominoes.
 */
object TengenSurfaceRasterizer {
    private const val BEVEL_RATIO = 0.18f

    fun rasterize(
        cells: Iterable<TengenSurfaceCell>,
        outlineColor: Int,
        gutterPx: Int = 1,
    ): TengenRaster? {
        val entries = cells.toList()
        if (entries.isEmpty()) return null
        require(outlineColor ushr 24 == 0xFF) { "Tengen outline must be opaque" }

        val byCoordinate = entries.associateBy { it.coordinate }
        require(byCoordinate.size == entries.size) { "Surface cells must have unique coordinates" }
        entries.forEach { cell ->
            require(cell.ramp.base ushr 24 == 0xFF)
            require(cell.ramp.highlight ushr 24 == 0xFF)
            require(cell.ramp.shadow ushr 24 == 0xFF)
        }
        entries.forEachIndexed { index, cell ->
            entries.drop(index + 1).forEach { other ->
                require(!cell.bounds.intersects(other.bounds)) {
                    "Surface cells must not overlap in pixel space"
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
            val exposed = ExposedEdges(
                top = PixelPoint(cell.coordinate.x, cell.coordinate.y - 1) !in byCoordinate,
                left = PixelPoint(cell.coordinate.x - 1, cell.coordinate.y) !in byCoordinate,
                right = PixelPoint(cell.coordinate.x + 1, cell.coordinate.y) !in byCoordinate,
                bottom = PixelPoint(cell.coordinate.x, cell.coordinate.y + 1) !in byCoordinate,
            )
            val bevel = bevelPx(cell.bounds, gutter)
            val bevelEnd = gutter + bevel

            for (y in cell.bounds.top until cell.bounds.bottom) {
                val localY = y - cell.bounds.top
                val distanceBottom = cell.bounds.bottom - 1 - y
                for (x in cell.bounds.left until cell.bounds.right) {
                    val localX = x - cell.bounds.left
                    val distanceRight = cell.bounds.right - 1 - x
                    val index = (y - bounds.top) * bounds.width + (x - bounds.left)

                    val outline = (exposed.top && localY < gutter) ||
                        (exposed.left && localX < gutter) ||
                        (exposed.right && distanceRight < gutter) ||
                        (exposed.bottom && distanceBottom < gutter)
                    if (outline) {
                        pixels[index] = outlineColor
                        continue
                    }

                    val nearTop = exposed.top && localY in gutter until bevelEnd
                    val nearLeft = exposed.left && localX in gutter until bevelEnd
                    val nearRight = exposed.right && distanceRight in gutter until bevelEnd
                    val nearBottom = exposed.bottom && distanceBottom in gutter until bevelEnd
                    pixels[index] = when {
                        nearTop || nearLeft || nearRight || nearBottom ->
                            bevelColor(cell.ramp, localX, localY, distanceRight, distanceBottom, nearTop, nearLeft, nearRight, nearBottom)
                        else -> cell.ramp.base
                    }
                }
            }
        }

        return TengenRaster(bounds, pixels)
    }

    fun bevelPx(cell: PixelRect, gutterPx: Int = 1): Int {
        val gutter = gutterPx.coerceAtLeast(0)
        val usable = min(cell.width, cell.height) - gutter * 2
        if (usable < 3) return 0
        val target = max(1, (min(cell.width, cell.height) * BEVEL_RATIO).roundToInt())
        return min(target, usable / 3)
    }

    private fun bevelColor(
        ramp: OpaqueColorRamp,
        localX: Int,
        localY: Int,
        distanceRight: Int,
        distanceBottom: Int,
        nearTop: Boolean,
        nearLeft: Boolean,
        nearRight: Boolean,
        nearBottom: Boolean,
    ): Int {
        val lightDistance = min(
            if (nearTop) localY else Int.MAX_VALUE,
            if (nearLeft) localX else Int.MAX_VALUE,
        )
        val shadowDistance = min(
            if (nearRight) distanceRight else Int.MAX_VALUE,
            if (nearBottom) distanceBottom else Int.MAX_VALUE,
        )
        return when {
            lightDistance < Int.MAX_VALUE && shadowDistance < Int.MAX_VALUE ->
                if (lightDistance <= shadowDistance) ramp.highlight else ramp.shadow
            lightDistance < Int.MAX_VALUE -> ramp.highlight
            else -> ramp.shadow
        }
    }

    private data class ExposedEdges(
        val top: Boolean,
        val left: Boolean,
        val right: Boolean,
        val bottom: Boolean,
    )
}
