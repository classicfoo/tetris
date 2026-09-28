package com.classicfoo.tetris

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.classicfoo.tetris.engine.GameAction
import com.classicfoo.tetris.engine.GameState
import com.classicfoo.tetris.engine.GameStatus
import com.classicfoo.tetris.settings.GameSettings
import com.classicfoo.tetris.settings.ScoreEntry
import com.classicfoo.tetris.settings.ScoreStore
import com.classicfoo.tetris.settings.SettingsStore
import com.classicfoo.tetris.ui.Feedback
import com.classicfoo.tetris.ui.GameSurfaceView
import com.classicfoo.tetris.ui.TetrisTypography
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : ComponentActivity() {
    private lateinit var gameView: GameSurfaceView
    private lateinit var rootView: FrameLayout
    private lateinit var pauseButton: ImageButton
    private lateinit var settingsButton: ImageButton
    private lateinit var settingsStore: SettingsStore
    private lateinit var scoreStore: ScoreStore
    private lateinit var feedback: Feedback
    private var settings = GameSettings()
    private var gameOverShown = false
    private var menuDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        settingsStore = SettingsStore(this)
        scoreStore = ScoreStore(this)
        settings = settingsStore.load()
        feedback = Feedback(this)
        feedback.updateSettings(settings)

        rootView = FrameLayout(this).apply {
            setBackgroundColor(TENGEN_BACKGROUND)
            clipChildren = false
        }
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, bars.bottom)
            insets
        }

        gameView = GameSurfaceView(
            this,
            engine = com.classicfoo.tetris.engine.GameEngine(),
            initialSettings = settings,
            feedback = feedback,
        )
        gameView.setStateListener(::handleState)
        rootView.addView(gameView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 4.dp(), 0, 0)
            elevation = 4.dp().toFloat()
        }
        pauseButton = iconButton(R.drawable.ic_pause, R.string.pause) {
            when (gameView.state().status) {
                GameStatus.RUNNING -> {
                    gameView.dispatch(GameAction.Pause)
                    showMenu(GameStatus.PAUSED)
                }
                GameStatus.PAUSED -> {
                    menuDialog?.dismiss()
                    gameView.dispatch(GameAction.Resume)
                }
                GameStatus.GAME_OVER -> showMenu(GameStatus.GAME_OVER)
            }
        }
        actions.addView(pauseButton, iconLayoutParams())
        settingsButton = iconButton(R.drawable.ic_settings, R.string.settings) { showSettings() }
        actions.addView(settingsButton, iconLayoutParams().apply { marginStart = 8.dp() })
        rootView.addView(actions, FrameLayout.LayoutParams(
            104.dp(),
            52.dp(),
            Gravity.TOP or Gravity.END,
        ))

        setContentView(rootView)
        updateChromeColors()
        ViewCompat.requestApplyInsets(rootView)
        gameView.startTicker()
        feedback.startMusic()
    }

    override fun onResume() {
        super.onResume()
        if (::gameView.isInitialized) {
            gameView.startTicker()
            if (gameView.state().status == GameStatus.RUNNING) feedback.resumeMusic()
        }
    }

    override fun onPause() {
        if (::gameView.isInitialized) gameView.stopTicker()
        if (::feedback.isInitialized) feedback.pauseMusic()
        super.onPause()
    }

    override fun onDestroy() {
        menuDialog?.dismiss()
        if (::gameView.isInitialized) gameView.stopTicker()
        if (::feedback.isInitialized) feedback.release()
        super.onDestroy()
    }

    private fun handleState(state: GameState) {
        updatePauseIcon(state.status)
        if (state.status == GameStatus.GAME_OVER && !gameOverShown) {
            gameOverShown = true
            scoreStore.record(ScoreEntry(state.score, state.lines, state.level))
            Handler(Looper.getMainLooper()).post { showMenu(GameStatus.GAME_OVER, state) }
        }
        if (state.status != GameStatus.GAME_OVER) gameOverShown = false
    }

    private fun showMenu(status: GameStatus, state: GameState = gameView.state()) {
        if (isFinishing || menuDialog?.isShowing == true) return
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8.dp(), 0, 8.dp(), 0)
        }
        fun addAction(label: String, emphasized: Boolean = false, action: () -> Unit) {
            content.addView(actionButton(label, label, emphasized) { action() }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 4.dp(), 0, 4.dp()) })
        }

        if (status == GameStatus.PAUSED) {
            addAction(getString(R.string.resume), emphasized = true) {
                menuDialog?.dismiss()
                gameView.dispatch(GameAction.Resume)
            }
        }
        addAction(getString(R.string.new_game)) { startNewGame() }
        addAction(getString(R.string.settings)) {
            menuDialog?.dismiss()
            showSettings()
        }
        addAction(getString(R.string.high_scores)) {
            menuDialog?.dismiss()
            showScores()
        }

        val title = if (status == GameStatus.PAUSED) getString(R.string.paused) else getString(R.string.game_over)
        val message = if (status == GameStatus.GAME_OVER) {
            getString(R.string.game_over_details, state.score, state.lines, state.level)
        } else {
            getString(R.string.gesture_hint)
        }
        menuDialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setView(content)
            .setOnDismissListener { menuDialog = null }
            .show()
        styleDialog(menuDialog!!)
    }

    private fun startNewGame() {
        menuDialog?.dismiss()
        gameOverShown = false
        gameView.dispatch(GameAction.Restart)
    }

    private fun showSettings() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8.dp(), 0, 8.dp(), 0)
        }
        val scroll = ScrollView(this).apply { addView(content) }

        content.addView(sectionLabel(getString(R.string.display_settings)))
        content.addView(switchSetting(getString(R.string.show_grid), settings.showGrid) { value ->
            settings = settings.copy(showGrid = value)
            settingsStore.save(settings)
            gameView.updateSettings(settings)
        })
        content.addView(switchSetting(getString(R.string.show_ghost), settings.showGhost) { value ->
            settings = settings.copy(showGhost = value)
            settingsStore.save(settings)
            gameView.updateSettings(settings)
        })
        content.addView(sectionLabel(getString(R.string.audio_settings)))
        content.addView(switchSetting(getString(R.string.sound), settings.soundEnabled) { value ->
            settings = settings.copy(soundEnabled = value)
            settingsStore.save(settings)
            feedback.updateSettings(settings)
            gameView.updateSettings(settings)
        })
        content.addView(switchSetting(getString(R.string.music), settings.musicEnabled) { value ->
            settings = settings.copy(musicEnabled = value)
            settingsStore.save(settings)
            feedback.updateSettings(settings)
            gameView.updateSettings(settings)
        })
        content.addView(switchSetting(getString(R.string.haptics), settings.hapticsEnabled) { value ->
            settings = settings.copy(hapticsEnabled = value)
            settingsStore.save(settings)
            gameView.updateSettings(settings)
        })
        content.addView(sectionLabel(getString(R.string.controls_settings)))
        val previewButton = actionButton(previewLabel(), getString(R.string.preview_count)) {}
        previewButton.setOnClickListener {
            settings = settings.copy(previewCount = if (settings.previewCount == 5) 1 else settings.previewCount + 1)
            settingsStore.save(settings)
            gameView.updateSettings(settings)
            previewButton.text = previewLabel()
            previewButton.contentDescription = previewLabel()
        }
        content.addView(previewButton)
        val delayButton = actionButton(delayLabel(), getString(R.string.repeat_delay)) {}
        delayButton.setOnClickListener {
            settings = settings.copy(repeatDelayMs = if (settings.repeatDelayMs >= 400) 80 else settings.repeatDelayMs + 40)
            settingsStore.save(settings)
            delayButton.text = delayLabel()
            delayButton.contentDescription = delayLabel()
        }
        content.addView(delayButton)
        val rateButton = actionButton(rateLabel(), getString(R.string.repeat_rate)) {}
        rateButton.setOnClickListener {
            settings = settings.copy(repeatRateMs = if (settings.repeatRateMs >= 120) 16 else settings.repeatRateMs + 16)
            settingsStore.save(settings)
            rateButton.text = rateLabel()
            rateButton.contentDescription = rateLabel()
        }
        content.addView(rateButton)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.settings))
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(getString(R.string.high_scores)) { _, _ -> showScores() }
            .show()
        styleDialog(dialog)
    }

    private fun switchSetting(label: String, checked: Boolean, changed: (Boolean) -> Unit): SwitchMaterial {
        return SwitchMaterial(this).apply {
            text = label
            isChecked = checked
            setTextColor(dialogTextColor())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = pixelTypeface(Typeface.BOLD)
            minHeight = 52.dp()
            background = framedButtonBackground(surfaceColor(), accentColor(), 10.dp())
            thumbTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(TENGEN_ACCENT_LIGHT, TENGEN_MUTED),
            )
            trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(TENGEN_ACCENT_SURFACE, TENGEN_DIALOG),
            )
            setPadding(12.dp(), 5.dp(), 12.dp(), 5.dp())
            setOnCheckedChangeListener { _, value -> changed(value) }
            contentDescription = label
        }
    }

    private fun showScores() {
        val scores = scoreStore.load()
        val message = if (scores.isEmpty()) {
            getString(R.string.no_scores)
        } else {
            scores.mapIndexed { index, score ->
                getString(R.string.score_entry_format, index + 1, score.score, score.lines, score.level)
            }.joinToString("\n")
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.high_scores))
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        styleDialog(dialog)
    }

    private fun iconButton(icon: Int, description: Int, action: () -> Unit): ImageButton {
        return ImageButton(this).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(TENGEN_TEXT)
            background = framedButtonBackground(
                fill = surfaceColor(),
                border = accentColor(),
                radius = 10.dp(),
            )
            setPadding(11.dp(), 11.dp(), 11.dp(), 11.dp())
            minimumWidth = 48.dp()
            minimumHeight = 48.dp()
            elevation = 2.dp().toFloat()
            contentDescription = getString(description)
            setOnClickListener { action() }
        }
    }

    private fun actionButton(
        label: String,
        description: String,
        emphasized: Boolean = false,
        action: () -> Unit,
    ): MaterialButton {
        return MaterialButton(this).apply {
            text = label
            contentDescription = description
            isAllCaps = false
            minHeight = 48.dp()
            setPadding(16.dp(), 0, 16.dp(), 0)
            typeface = pixelTypeface(Typeface.BOLD)
            letterSpacing = 0.035f
            backgroundTintList = null
            background = framedButtonBackground(
                fill = if (emphasized) accentSurfaceColor() else surfaceColor(),
                border = accentColor(),
                radius = 10.dp(),
            )
            setTextColor(if (emphasized) textOnAccentColor() else dialogTextColor())
            setRippleColor(ColorStateList.valueOf(colorWithAlpha(accentColor(), 70)))
            elevation = 2.dp().toFloat()
            setOnClickListener { action() }
        }
    }

    private fun updatePauseIcon(status: GameStatus) {
        if (!::pauseButton.isInitialized) return
        pauseButton.setImageDrawable(ContextCompat.getDrawable(
            this,
            when (status) {
                GameStatus.PAUSED -> R.drawable.ic_play
                GameStatus.GAME_OVER -> R.drawable.ic_settings
                GameStatus.RUNNING -> R.drawable.ic_pause
            },
        ))
        pauseButton.imageTintList = ColorStateList.valueOf(TENGEN_TEXT)
        pauseButton.contentDescription = getString(
            when (status) {
                GameStatus.PAUSED -> R.string.resume
                GameStatus.GAME_OVER -> R.string.game_over_menu
                GameStatus.RUNNING -> R.string.pause
            },
        )
        updateActionIconTint()
    }

    private fun updateActionIconTint() {
        if (::settingsButton.isInitialized) {
            settingsButton.imageTintList = ColorStateList.valueOf(TENGEN_TEXT)
            settingsButton.background = framedButtonBackground(
                fill = surfaceColor(),
                border = accentColor(),
                radius = 10.dp(),
            )
        }
        if (::pauseButton.isInitialized) {
            pauseButton.background = framedButtonBackground(
                fill = surfaceColor(),
                border = accentColor(),
                radius = 10.dp(),
            )
        }
    }

    private fun updateChromeColors() {
        if (!::rootView.isInitialized) return
        rootView.setBackgroundColor(TENGEN_BACKGROUND)
        window.statusBarColor = TENGEN_BACKGROUND
        window.navigationBarColor = TENGEN_BACKGROUND
        WindowInsetsControllerCompat(window, rootView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    private fun previewLabel(): String = getString(R.string.preview_count_value, getString(R.string.preview_count), settings.previewCount)

    private fun delayLabel(): String = getString(R.string.repeat_delay_value, getString(R.string.repeat_delay), settings.repeatDelayMs)

    private fun rateLabel(): String = getString(R.string.repeat_rate_value, getString(R.string.repeat_rate), settings.repeatRateMs)

    private fun sectionLabel(label: String): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(accentColor())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = pixelTypeface(Typeface.BOLD)
            letterSpacing = 0.12f
            setPadding(4.dp(), 14.dp(), 4.dp(), 4.dp())
        }
    }

    private fun styleDialog(dialog: AlertDialog) {
        dialog.window?.let { window ->
            window.setBackgroundDrawable(framedButtonBackground(dialogSurfaceColor(), accentColor(), 18.dp()))
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes = window.attributes.apply { dimAmount = 0.76f }
            window.setLayout((resources.displayMetrics.widthPixels * 0.9f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
        }
        val titleId = resources.getIdentifier("alertTitle", "id", "android")
        if (titleId != 0) {
            dialog.findViewById<TextView>(titleId)?.apply {
                setTextColor(dialogTextColor())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                typeface = pixelTypeface(Typeface.BOLD)
            }
        }
        dialog.findViewById<TextView>(android.R.id.message)?.apply {
            setTextColor(dialogMutedColor())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = pixelTypeface(Typeface.NORMAL)
            setLineSpacing(2f, 1.05f)
        }
        listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL).forEach { which ->
            dialog.getButton(which)?.let { button ->
                button.setTextColor(accentColor())
                button.typeface = pixelTypeface(Typeface.BOLD)
                button.isAllCaps = true
                button.background = framedButtonBackground(
                    fill = surfaceColor(),
                    border = accentColor(),
                    radius = 10.dp(),
                )
                button.setPadding(14.dp(), 0, 14.dp(), 0)
                button.minHeight = 44.dp()
            }
        }
    }

    private fun framedButtonBackground(fill: Int, border: Int, radius: Int): Drawable = TengenBevelDrawable(
        fill = fill,
        border = border,
        highlight = TENGEN_ACCENT_LIGHT,
        shadow = TENGEN_ACCENT_DARK,
        cut = radius.toFloat(),
        borderWidth = 2.dp().toFloat(),
    )

    private fun accentColor(): Int = TENGEN_ACCENT

    private fun accentSurfaceColor(): Int = TENGEN_ACCENT_SURFACE

    private fun surfaceColor(): Int = TENGEN_PANEL

    private fun dialogSurfaceColor(): Int = TENGEN_DIALOG

    private fun dialogTextColor(): Int = TENGEN_TEXT

    private fun dialogMutedColor(): Int = TENGEN_MUTED

    private fun textOnAccentColor(): Int = TENGEN_INK

    private fun pixelTypeface(style: Int): Typeface = if (style == Typeface.BOLD) {
        TetrisTypography.bold(this)
    } else {
        TetrisTypography.regular(this)
    }

    private fun iconLayoutParams(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(48.dp(), 48.dp())

    private companion object {
        val TENGEN_BACKGROUND: Int = Color.rgb(6, 17, 38)
        val TENGEN_PANEL: Int = Color.rgb(18, 38, 74)
        val TENGEN_DIALOG: Int = Color.rgb(8, 18, 38)
        val TENGEN_ACCENT: Int = Color.rgb(255, 206, 82)
        val TENGEN_ACCENT_LIGHT: Int = Color.rgb(255, 236, 157)
        val TENGEN_ACCENT_DARK: Int = Color.rgb(111, 67, 18)
        val TENGEN_ACCENT_SURFACE: Int = Color.rgb(83, 61, 17)
        val TENGEN_TEXT: Int = Color.rgb(255, 245, 204)
        val TENGEN_MUTED: Int = Color.rgb(177, 190, 220)
        val TENGEN_INK: Int = Color.rgb(10, 14, 22)
    }

    private class TengenBevelDrawable(
        private val fill: Int,
        private val border: Int,
        private val highlight: Int,
        private val shadow: Int,
        private val cut: Float,
        private val borderWidth: Float,
    ) : Drawable() {
        private val paint = Paint().apply {
            isAntiAlias = false
            style = Paint.Style.FILL
        }
        private var alpha = 255
        private var colorFilter: ColorFilter? = null
        private var pressed = false

        override fun draw(canvas: Canvas) {
            val bounds = bounds
            if (bounds.width() <= 0 || bounds.height() <= 0) return

            val left = bounds.left.toFloat()
            val top = bounds.top.toFloat()
            val right = bounds.right.toFloat()
            val bottom = bounds.bottom.toFloat()
            val bevel = cut.coerceAtMost(minOf(right - left, bottom - top) / 3f)
            val inset = borderWidth.coerceAtMost(bevel / 2f).coerceAtLeast(1f)
            val outer = chamferedPath(left, top, right, bottom, bevel)
            val inner = chamferedPath(
                left + inset,
                top + inset,
                right - inset,
                bottom - inset,
                (bevel - inset).coerceAtLeast(1f),
            )

            paint.alpha = alpha
            paint.colorFilter = colorFilter
            paint.style = Paint.Style.FILL
            paint.color = border
            canvas.drawPath(outer, paint)
            paint.color = if (pressed) shade(fill, 0.78f) else fill
            canvas.drawPath(inner, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = inset
            paint.strokeJoin = Paint.Join.MITER
            paint.color = if (pressed) shadow else highlight
            canvas.drawPath(edgePath(left, top, right, bottom, bevel, topLeft = true), paint)
            paint.color = if (pressed) highlight else shadow
            canvas.drawPath(edgePath(left, top, right, bottom, bevel, topLeft = false), paint)
            paint.style = Paint.Style.FILL
        }

        override fun isStateful(): Boolean = true

        override fun onStateChange(stateSet: IntArray): Boolean {
            val newPressed = stateSet.contains(android.R.attr.state_pressed)
            if (newPressed == pressed) return false
            pressed = newPressed
            invalidateSelf()
            return true
        }

        override fun setAlpha(alpha: Int) {
            this.alpha = alpha.coerceIn(0, 255)
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            this.colorFilter = colorFilter
            invalidateSelf()
        }

        override fun getOpacity(): Int = PixelFormat.OPAQUE

        override fun getPadding(padding: Rect): Boolean {
            padding.set(0, 0, 0, 0)
            return false
        }

        private fun chamferedPath(left: Float, top: Float, right: Float, bottom: Float, bevel: Float): Path = Path().apply {
            moveTo(left + bevel, top)
            lineTo(right - bevel, top)
            lineTo(right, top + bevel)
            lineTo(right, bottom - bevel)
            lineTo(right - bevel, bottom)
            lineTo(left + bevel, bottom)
            lineTo(left, bottom - bevel)
            lineTo(left, top + bevel)
            close()
        }

        private fun edgePath(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            bevel: Float,
            topLeft: Boolean,
        ): Path = Path().apply {
            if (topLeft) {
                moveTo(left + bevel, top)
                lineTo(right - bevel, top)
                lineTo(right, top + bevel)
                moveTo(left, top + bevel)
                lineTo(left, bottom - bevel)
                lineTo(left + bevel, bottom)
            } else {
                moveTo(right, top + bevel)
                lineTo(right, bottom - bevel)
                lineTo(right - bevel, bottom)
                moveTo(right - bevel, bottom)
                lineTo(left + bevel, bottom)
                lineTo(left, bottom - bevel)
            }
        }

        private fun shade(color: Int, factor: Float): Int = Color.rgb(
            (Color.red(color) * factor).toInt().coerceIn(0, 255),
            (Color.green(color) * factor).toInt().coerceIn(0, 255),
            (Color.blue(color) * factor).toInt().coerceIn(0, 255),
        )
    }

    private fun colorWithAlpha(color: Int, alpha: Int): Int = Color.argb(
        alpha.coerceIn(0, 255),
        Color.red(color),
        Color.green(color),
        Color.blue(color),
    )

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
