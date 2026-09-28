package com.classicfoo.tetris.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.max

/** Fixed Tengen palette shared by the Canvas renderer and Android widgets. */
object TengenStyle {
    val background: Int = Color.rgb(6, 17, 38)
    val panel: Int = Color.rgb(18, 38, 74)
    val board: Int = Color.rgb(11, 23, 49)
    val boardFrame: Int = Color.rgb(39, 79, 150)
    val pixelOutline: Int = Color.rgb(5, 10, 23)
    val grid: Int = Color.rgb(37, 65, 111)
    val text: Int = Color.rgb(255, 245, 204)
    val mutedText: Int = Color.rgb(177, 190, 220)
    val accent: Int = Color.rgb(255, 206, 82)
    val frameHighlight: Int = Color.rgb(255, 236, 157)
    val frameShadow: Int = Color.rgb(111, 67, 18)
    val flash: Int = Color.rgb(255, 240, 158)
    val overlay: Int = Color.rgb(4, 8, 19)

    fun bevelDrawable(
        context: Context,
        fillColor: Int,
        emphasized: Boolean = false,
        pressed: Boolean = false,
    ): Drawable {
        val density = context.resources.displayMetrics.density
        return TengenBeveledDrawable(
            fillColor = if (pressed) darken(fillColor, 0.82f) else fillColor,
            edgeColor = if (emphasized) accent else boardFrame,
            highlightColor = frameHighlight,
            shadowColor = frameShadow,
            cutCornerPx = 8f * density,
            borderWidthPx = 2f * density,
        )
    }

    private fun darken(color: Int, factor: Float): Int = Color.rgb(
        (Color.red(color) * factor).toInt().coerceIn(0, 255),
        (Color.green(color) * factor).toInt().coerceIn(0, 255),
        (Color.blue(color) * factor).toInt().coerceIn(0, 255),
    )
}

/** A reusable, state-independent cut-corner Tengen bevel. */
class TengenBeveledDrawable(
    private val fillColor: Int,
    private val edgeColor: Int,
    private val highlightColor: Int,
    private val shadowColor: Int,
    private val cutCornerPx: Float,
    private val borderWidthPx: Float,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    override fun draw(canvas: Canvas) {
        val outer = RectF(bounds)
        if (outer.width() <= 0f || outer.height() <= 0f) return
        val cut = cutCornerPx.coerceAtMost(minOf(outer.width(), outer.height()) / 3f)
        val innerInset = max(1f, borderWidthPx * 1.35f)
        val inner = RectF(
            outer.left + innerInset,
            outer.top + innerInset,
            outer.right - innerInset,
            outer.bottom - innerInset,
        )
        val innerCut = (cut - innerInset).coerceAtLeast(0f)

        paint.style = Paint.Style.FILL
        paint.color = TengenStyle.pixelOutline
        canvas.drawPath(chamfered(outer, cut), paint)

        paint.color = edgeColor
        canvas.drawPath(chamfered(RectF(outer).apply { inset(0.5f, 0.5f) }, cut), paint)

        paint.color = fillColor
        canvas.drawPath(chamfered(inner, innerCut), paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = borderWidthPx.coerceAtLeast(1f)
        paint.strokeCap = Paint.Cap.SQUARE
        paint.color = highlightColor
        canvas.drawLine(outer.left + cut, outer.top + paint.strokeWidth / 2f, outer.right - cut, outer.top + paint.strokeWidth / 2f, paint)
        canvas.drawLine(outer.left + paint.strokeWidth / 2f, outer.top + cut, outer.left + paint.strokeWidth / 2f, outer.bottom - cut, paint)
        paint.color = shadowColor
        canvas.drawLine(outer.left + cut, outer.bottom - paint.strokeWidth / 2f, outer.right - cut, outer.bottom - paint.strokeWidth / 2f, paint)
        canvas.drawLine(outer.right - paint.strokeWidth / 2f, outer.top + cut, outer.right - paint.strokeWidth / 2f, outer.bottom - cut, paint)
        paint.style = Paint.Style.FILL
    }

    private fun chamfered(rect: RectF, cut: Float): Path = Path().apply {
        moveTo(rect.left + cut, rect.top)
        lineTo(rect.right - cut, rect.top)
        lineTo(rect.right, rect.top + cut)
        lineTo(rect.right, rect.bottom - cut)
        lineTo(rect.right - cut, rect.bottom)
        lineTo(rect.left + cut, rect.bottom)
        lineTo(rect.left, rect.bottom - cut)
        lineTo(rect.left, rect.top + cut)
        close()
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
