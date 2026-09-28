package com.classicfoo.tetris.ui

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.classicfoo.tetris.R

/**
 * The app's single display type family. Keeping the resource lookup here makes
 * Canvas text and Android views use the same Pixel Operator faces.
 */
object TetrisTypography {
    private var regularTypeface: Typeface? = null
    private var boldTypeface: Typeface? = null

    fun regular(context: Context): Typeface = regularTypeface ?: ResourcesCompat.getFont(
        context.applicationContext,
        R.font.pixel_operator,
    )!!.also { regularTypeface = it }

    fun bold(context: Context): Typeface = boldTypeface ?: ResourcesCompat.getFont(
        context.applicationContext,
        R.font.pixel_operator_bold,
    )!!.also { boldTypeface = it }
}
