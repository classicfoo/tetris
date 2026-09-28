package com.classicfoo.tetris.ui

import android.graphics.Color

/** Fixed Tengen palette shared by the Canvas renderer and Android widgets.
 *
 * The original Tengen treatment used gold bevels around every surface. The
 * current UI keeps the dark blue palette and colourful pieces, but uses one
 * consistent, deliberately plain white line for component boundaries.
 */
object TengenStyle {
    val background: Int = Color.rgb(6, 17, 38)
    val panel: Int = Color.rgb(18, 38, 74)
    val board: Int = Color.rgb(11, 23, 49)
    val boardFrame: Int = Color.rgb(39, 79, 150)
    val pixelOutline: Int = Color.rgb(5, 10, 23)
    val grid: Int = Color.rgb(37, 65, 111)
    val text: Int = Color.WHITE
    val mutedText: Int = Color.rgb(177, 190, 220)
    val accent: Int = Color.WHITE
    val flash: Int = Color.WHITE
    val overlay: Int = Color.rgb(4, 8, 19)
}
