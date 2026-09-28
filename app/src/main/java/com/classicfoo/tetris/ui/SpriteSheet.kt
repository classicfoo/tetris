package com.classicfoo.tetris.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import kotlin.math.roundToInt

/**
 * Loader and fixed-region catalog for the Tengen UI sprite sheet.
 *
 * The atlas is an opaque 1536x1024 RGB image. Coordinates below use the
 * Android convention of a half-open rectangle: left/top are inclusive and
 * right/bottom are exclusive. The regions are deliberately cropped to the
 * visible sprite plus its soft outer edge; the black gaps around sprites are
 * not part of any region.
 *
 * The source artwork has already-rendered chamfered corners. The drawable
 * below keeps each corner and border edge at source scale while stretching
 * only the nine-slice centre and edge spans.
 */
class SpriteSheet private constructor(private val bitmap: Bitmap) {

    /** Returns a scalable drawable for one of the fixed panel sprites. */
    fun panel(
        region: Region,
        filterBitmap: Boolean = true,
    ): Drawable = NineSlicePanelDrawable(
        bitmap = bitmap,
        source = region.source.toRect(),
        insets = region.insets,
        filterBitmap = filterBitmap,
    )

    companion object {
        const val ASSET_PATH: String = "ui/tengen_ui_sprite_sheet.png"

        /** Loads the atlas without density-based bitmap scaling. */
        fun load(context: Context): SpriteSheet {
            val options = BitmapFactory.Options().apply { inScaled = false }
            val loaded = context.assets.open(ASSET_PATH).use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: error("Unable to decode Tengen sprite sheet: $ASSET_PATH")
            check(loaded.width == 1536 && loaded.height == 1024) {
                "Unexpected Tengen sprite sheet size: ${loaded.width}x${loaded.height}"
            }
            return SpriteSheet(loaded)
        }
    }

    /** Source-space inset values for a nine-slice region. */
    data class Insets(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        init {
            require(left >= 0 && top >= 0 && right >= 0 && bottom >= 0) {
                "Nine-slice insets must be non-negative"
            }
        }
    }

    /** An immutable, half-open source rectangle in atlas pixels. */
    data class SourceRect(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        init {
            require(left >= 0 && top >= 0 && right > left && bottom > top) {
                "Sprite source rectangle must be positive and inside the atlas"
            }
            require(right <= 1536 && bottom <= 1024) {
                "Sprite source rectangle exceeds the 1536x1024 atlas"
            }
        }

        fun toRect(): Rect = Rect(left, top, right, bottom)
    }

    /** A fixed atlas region and its preserved chamfer/border insets. */
    data class Region(
        val source: SourceRect,
        val insets: Insets,
        val description: String,
    ) {
        init {
            require(insets.left + insets.right < source.right - source.left) {
                "Horizontal insets consume the entire sprite region"
            }
            require(insets.top + insets.bottom < source.bottom - source.top) {
                "Vertical insets consume the entire sprite region"
            }
        }
    }

    /**
     * Fixed atlas coordinates inspected from the supplied 1536x1024 sheet.
     * The inset values include the gold frame and chamfered corner geometry;
     * the central dark texture is the stretchable area.
     */
    object Regions {
        // Unlabelled frames from the top row: safe for generic dialogs/buttons.
        val LARGE_PANEL = Region(
            source = SourceRect(12, 15, 382, 325),
            insets = Insets(left = 22, top = 22, right = 22, bottom = 22),
            description = "Large unlabelled chamfered panel",
        )
        val TALL_PANEL = Region(
            source = SourceRect(400, 15, 622, 325),
            insets = Insets(left = 22, top = 22, right = 22, bottom = 22),
            description = "Tall unlabelled chamfered panel",
        )
        val WIDE_PANEL = Region(
            source = SourceRect(640, 15, 914, 108),
            insets = Insets(left = 22, top = 22, right = 22, bottom = 22),
            description = "Wide unlabelled chamfered panel/button frame",
        )
        val MEDIUM_PANEL = Region(
            source = SourceRect(640, 126, 864, 215),
            insets = Insets(left = 22, top = 20, right = 22, bottom = 20),
            description = "Medium unlabelled chamfered panel",
        )
        val SMALL_PANEL = Region(
            source = SourceRect(640, 232, 864, 323),
            insets = Insets(left = 22, top = 20, right = 22, bottom = 20),
            description = "Small unlabelled chamfered panel",
        )
        val RED_BUTTON = Region(
            // Blank red button rail from the lower atlas row; unlike the
            // Resume sample above, it contains no baked-in label.
            source = SourceRect(76, 796, 367, 828),
            insets = Insets(left = 18, top = 12, right = 18, bottom = 12),
            description = "Blank red emphasized button rail",
        )

        // Gameplay UI frames from the middle row.
        val SCORE_HUD = Region(
            source = SourceRect(801, 355, 1228, 458),
            insets = Insets(left = 23, top = 19, right = 23, bottom = 19),
            description = "Score/level/lines HUD frame",
        )
        val PAUSE_HINTS = Region(
            source = SourceRect(433, 458, 796, 626),
            insets = Insets(left = 24, top = 22, right = 24, bottom = 22),
            description = "Pause instructions panel",
        )
        val HOLD = Region(
            source = SourceRect(804, 467, 912, 617),
            insets = Insets(left = 21, top = 20, right = 21, bottom = 20),
            description = "Hold preview frame",
        )
        val NEXT = Region(
            source = SourceRect(917, 467, 1231, 617),
            insets = Insets(left = 21, top = 20, right = 21, bottom = 20),
            description = "Next queue frame",
        )

        // This is the red labelled Resume sample; use it only when the baked
        // label is wanted. WIDE_PANEL is the reusable unlabelled button frame.
        val RESUME_BUTTON_SAMPLE = Region(
            source = SourceRect(18, 341, 417, 428),
            insets = Insets(left = 25, top = 20, right = 25, bottom = 20),
            description = "Red Resume button sample with baked label",
        )
    }
}

/**
 * Nine-slice drawable for an atlas region whose corners contain chamfers.
 * The source edge strips remain proportional to the supplied insets, so the
 * gold bevel does not become stretched when a panel grows on a phone.
 */
class NineSlicePanelDrawable(
    private val bitmap: Bitmap,
    source: Rect,
    private val insets: SpriteSheet.Insets,
    filterBitmap: Boolean = true,
) : Drawable() {
    private val source = Rect(source)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = filterBitmap
        isDither = true
    }

    init {
        require(source.left >= 0 && source.top >= 0) { "Source rectangle must be inside the bitmap" }
        require(source.right <= bitmap.width && source.bottom <= bitmap.height) {
            "Source rectangle exceeds the bitmap"
        }
        require(insets.left + insets.right < source.width()) {
            "Horizontal insets consume the entire source rectangle"
        }
        require(insets.top + insets.bottom < source.height()) {
            "Vertical insets consume the entire source rectangle"
        }
    }

    override fun draw(canvas: Canvas) {
        val target = bounds
        if (target.width() <= 0 || target.height() <= 0) return

        val (destinationLeft, destinationRight) = fitEdges(
            total = target.width(),
            leading = insets.left,
            trailing = insets.right,
        )
        val (destinationTop, destinationBottom) = fitEdges(
            total = target.height(),
            leading = insets.top,
            trailing = insets.bottom,
        )

        val sourceX = intArrayOf(
            source.left,
            source.left + insets.left,
            source.right - insets.right,
            source.right,
        )
        val sourceY = intArrayOf(
            source.top,
            source.top + insets.top,
            source.bottom - insets.bottom,
            source.bottom,
        )
        val destinationX = intArrayOf(
            target.left,
            target.left + destinationLeft,
            target.right - destinationRight,
            target.right,
        )
        val destinationY = intArrayOf(
            target.top,
            target.top + destinationTop,
            target.bottom - destinationBottom,
            target.bottom,
        )

        for (row in 0 until 3) {
            for (column in 0 until 3) {
                val sourcePatch = Rect(
                    sourceX[column],
                    sourceY[row],
                    sourceX[column + 1],
                    sourceY[row + 1],
                )
                val destinationPatch = Rect(
                    destinationX[column],
                    destinationY[row],
                    destinationX[column + 1],
                    destinationY[row + 1],
                )
                if (!sourcePatch.isEmpty && !destinationPatch.isEmpty) {
                    canvas.drawBitmap(bitmap, sourcePatch, destinationPatch, paint)
                }
            }
        }
    }

    private fun fitEdges(total: Int, leading: Int, trailing: Int): Pair<Int, Int> {
        val requested = leading + trailing
        if (requested <= total) return leading to trailing

        val fittedLeading = (leading * total.toFloat() / requested).roundToInt()
            .coerceIn(0, total)
        return fittedLeading to (total - fittedLeading)
    }

    override fun getIntrinsicWidth(): Int = source.width()

    override fun getIntrinsicHeight(): Int = source.height()

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha.coerceIn(0, 255)
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
