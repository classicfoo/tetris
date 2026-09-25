package com.classicfoo.tetris

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.ComponentActivity
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
    private var menuDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        settingsStore = SettingsStore(this)
        scoreStore = ScoreStore(this)
        settings = settingsStore.load()
        feedback = Feedback(this)
        feedback.updateSettings(settings)

        rootView = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(16, 19, 28))
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
            setPadding(0, 2.dp(), 8.dp(), 0)
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
        actions.addView(settingsButton)
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
        fun addAction(label: String, action: () -> Unit) {
            content.addView(actionButton(label, label, action), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 4.dp(), 0, 4.dp()) })
        }

        if (status == GameStatus.PAUSED) {
            addAction(getString(R.string.resume)) {
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

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.settings))
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(getString(R.string.high_scores)) { _, _ -> showScores() }
            .show()
    }

    private fun switchSetting(label: String, checked: Boolean, changed: (Boolean) -> Unit): SwitchMaterial {
        return SwitchMaterial(this).apply {
            text = label
            isChecked = checked
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
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.high_scores))
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun iconButton(icon: Int, description: Int, action: () -> Unit): ImageButton {
        return ImageButton(this).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(GameSurfaceView.iconColor(settings.theme))
            val selectable = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, selectable, true)
            setBackgroundResource(selectable.resourceId)
            setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
            minimumWidth = 48.dp()
            minimumHeight = 48.dp()
            contentDescription = getString(description)
            setOnClickListener { action() }
        }
    }

    private fun actionButton(label: String, description: String, action: () -> Unit): MaterialButton {
        return MaterialButton(this).apply {
            text = label
            contentDescription = description
            isAllCaps = false
            minHeight = 48.dp()
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

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
