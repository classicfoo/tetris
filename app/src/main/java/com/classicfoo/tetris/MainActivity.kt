package com.classicfoo.tetris

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
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
import com.classicfoo.tetris.settings.ThemeOption
import com.classicfoo.tetris.ui.Feedback
import com.classicfoo.tetris.ui.GameSurfaceView
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
            setBackgroundColor(GameSurfaceView.backgroundColor(settings.theme))
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
            setPadding(0, 4.dp(), 12.dp(), 0)
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
        actions.addView(pauseButton)
        settingsButton = iconButton(R.drawable.ic_settings, R.string.settings) { showSettings() }
        actions.addView(settingsButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = 8.dp() })
        rootView.addView(actions, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
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
        val themeButton = actionButton(themeLabel(), getString(R.string.theme)) {}
        themeButton.setOnClickListener {
            settings = settings.copy(theme = settings.theme.next())
            settingsStore.save(settings)
            themeButton.text = themeLabel()
            themeButton.contentDescription = themeLabel()
            gameView.updateSettings(settings)
            updateChromeColors()
            updateActionIconTint()
        }
        content.addView(themeButton)
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
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            buttonTintList = ColorStateList.valueOf(accentColor())
            setPadding(4.dp(), 5.dp(), 4.dp(), 5.dp())
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
            imageTintList = ColorStateList.valueOf(GameSurfaceView.iconColor(settings.theme))
            background = framedButtonBackground(
                fill = surfaceColor(),
                border = accentColor(),
                radius = 12.dp(),
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
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            letterSpacing = 0.035f
            cornerRadius = 10.dp()
            strokeWidth = 1.dp()
            strokeColor = ColorStateList.valueOf(accentColor())
            backgroundTintList = ColorStateList.valueOf(
                if (emphasized) accentSurfaceColor() else surfaceColor(),
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
        pauseButton.imageTintList = ColorStateList.valueOf(GameSurfaceView.iconColor(settings.theme))
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
            settingsButton.imageTintList = ColorStateList.valueOf(GameSurfaceView.iconColor(settings.theme))
            settingsButton.background = framedButtonBackground(
                fill = surfaceColor(),
                border = accentColor(),
                radius = 12.dp(),
            )
        }
        if (::pauseButton.isInitialized) {
            pauseButton.background = framedButtonBackground(
                fill = surfaceColor(),
                border = accentColor(),
                radius = 12.dp(),
            )
        }
    }

    private fun updateChromeColors() {
        if (!::rootView.isInitialized) return
        val background = GameSurfaceView.backgroundColor(settings.theme)
        rootView.setBackgroundColor(background)
        window.statusBarColor = background
        window.navigationBarColor = background
        WindowInsetsControllerCompat(window, rootView).apply {
            val lightBars = settings.theme == ThemeOption.GAME_BOY
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }
    }

    private fun themeName(theme: ThemeOption): String = when (theme) {
        ThemeOption.CLASSIC -> getString(R.string.classic_theme)
        ThemeOption.TENGEN_BEVEL -> getString(R.string.tengen_theme)
        ThemeOption.GAME_BOY -> getString(R.string.gameboy_theme)
    }

    private fun themeLabel(): String = getString(R.string.theme_value, getString(R.string.theme), themeName(settings.theme))

    private fun previewLabel(): String = getString(R.string.preview_count_value, getString(R.string.preview_count), settings.previewCount)

    private fun delayLabel(): String = getString(R.string.repeat_delay_value, getString(R.string.repeat_delay), settings.repeatDelayMs)

    private fun rateLabel(): String = getString(R.string.repeat_rate_value, getString(R.string.repeat_rate), settings.repeatRateMs)

    private fun sectionLabel(label: String): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(accentColor())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            letterSpacing = 0.12f
            setPadding(4.dp(), 14.dp(), 4.dp(), 4.dp())
        }
    }

    private fun styleDialog(dialog: AlertDialog) {
        dialog.window?.let { window ->
            window.setBackgroundDrawable(GradientDrawable().apply {
                setColor(dialogSurfaceColor())
                setStroke(1.dp(), accentColor())
                cornerRadius = 18.dp().toFloat()
            })
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes = window.attributes.apply { dimAmount = 0.76f }
            window.setLayout((resources.displayMetrics.widthPixels * 0.9f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
        }
        val titleId = resources.getIdentifier("alertTitle", "id", "android")
        if (titleId != 0) {
            dialog.findViewById<TextView>(titleId)?.apply {
                setTextColor(dialogTextColor())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }
        }
        dialog.findViewById<TextView>(android.R.id.message)?.apply {
            setTextColor(dialogMutedColor())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setLineSpacing(2f, 1.05f)
        }
        listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL).forEach { which ->
            dialog.getButton(which)?.let { button ->
                button.setTextColor(accentColor())
                button.typeface = Typeface.create("sans-serif", Typeface.BOLD)
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

    private fun framedButtonBackground(fill: Int, border: Int, radius: Int): StateListDrawable {
        val drawable = StateListDrawable()
        drawable.addState(
            intArrayOf(android.R.attr.state_pressed),
            GradientDrawable().apply {
                setColor(colorWithAlpha(border, 46))
                setStroke(1.dp(), border)
                cornerRadius = radius.toFloat()
            },
        )
        drawable.addState(
            intArrayOf(),
            GradientDrawable().apply {
                setColor(fill)
                setStroke(1.dp(), colorWithAlpha(border, 170))
                cornerRadius = radius.toFloat()
            },
        )
        return drawable
    }

    private fun accentColor(): Int = when (settings.theme) {
        ThemeOption.CLASSIC -> Color.rgb(112, 190, 255)
        ThemeOption.TENGEN_BEVEL -> Color.rgb(255, 206, 82)
        ThemeOption.GAME_BOY -> Color.rgb(15, 56, 15)
    }

    private fun accentSurfaceColor(): Int = when (settings.theme) {
        ThemeOption.CLASSIC -> Color.rgb(31, 63, 93)
        ThemeOption.TENGEN_BEVEL -> Color.rgb(83, 61, 17)
        ThemeOption.GAME_BOY -> Color.rgb(139, 172, 15)
    }

    private fun surfaceColor(): Int = when (settings.theme) {
        ThemeOption.CLASSIC -> Color.rgb(27, 32, 48)
        ThemeOption.TENGEN_BEVEL -> Color.rgb(18, 38, 74)
        ThemeOption.GAME_BOY -> Color.rgb(139, 172, 15)
    }

    private fun dialogSurfaceColor(): Int = when (settings.theme) {
        ThemeOption.CLASSIC -> Color.rgb(14, 18, 28)
        ThemeOption.TENGEN_BEVEL -> Color.rgb(8, 18, 38)
        ThemeOption.GAME_BOY -> Color.rgb(155, 188, 15)
    }

    private fun dialogTextColor(): Int = when (settings.theme) {
        ThemeOption.GAME_BOY -> Color.rgb(15, 56, 15)
        else -> Color.WHITE
    }

    private fun dialogMutedColor(): Int = when (settings.theme) {
        ThemeOption.CLASSIC -> Color.rgb(173, 182, 204)
        ThemeOption.TENGEN_BEVEL -> Color.rgb(177, 190, 220)
        ThemeOption.GAME_BOY -> Color.rgb(48, 98, 48)
    }

    private fun textOnAccentColor(): Int = when (settings.theme) {
        ThemeOption.GAME_BOY -> Color.rgb(155, 188, 15)
        else -> Color.rgb(10, 14, 22)
    }

    private fun colorWithAlpha(color: Int, alpha: Int): Int = Color.argb(
        alpha.coerceIn(0, 255),
        Color.red(color),
        Color.green(color),
        Color.blue(color),
    )

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
