package com.classicfoo.tetris

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : ComponentActivity() {
    private lateinit var gameView: GameSurfaceView
    private lateinit var controls: LinearLayout
    private lateinit var pauseButton: MaterialButton
    private lateinit var settingsStore: SettingsStore
    private lateinit var scoreStore: ScoreStore
    private lateinit var feedback: Feedback
    private var settings = GameSettings()
    private var gameOverShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        settingsStore = SettingsStore(this)
        scoreStore = ScoreStore(this)
        settings = settingsStore.load()
        feedback = Feedback(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.rgb(16, 19, 28))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, bars.bottom)
            insets
        }

        val gameContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(8, 4, 8, 4)
        }
        pauseButton = actionButton(getString(com.classicfoo.tetris.R.string.pause), getString(com.classicfoo.tetris.R.string.pause)) {
            if (gameView.state().status == GameStatus.PAUSED) gameView.dispatch(GameAction.Resume)
            else gameView.dispatch(GameAction.Pause)
            updatePauseLabel()
        }
        toolbar.addView(pauseButton, LinearLayout.LayoutParams(0, 52.dp(), 1f))
        val settingsButton = actionButton(getString(com.classicfoo.tetris.R.string.settings), getString(com.classicfoo.tetris.R.string.settings)) {
            showSettings()
        }
        toolbar.addView(settingsButton, LinearLayout.LayoutParams(0, 52.dp(), 1f))
        gameContainer.addView(toolbar)

        gameView = GameSurfaceView(this, engine = com.classicfoo.tetris.engine.GameEngine(), initialSettings = settings, feedback = feedback)
        gameView.setStateListener { state -> handleState(state) }
        gameContainer.addView(gameView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(gameContainer)

        controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 4, 8, 4)
        }
        root.addView(controls, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        rebuildControls()

        setContentView(root)
        gameView.startTicker()
    }

    override fun onResume() {
        super.onResume()
        if (::gameView.isInitialized) gameView.startTicker()
    }

    override fun onPause() {
        if (::gameView.isInitialized) gameView.stopTicker()
        super.onPause()
    }

    override fun onDestroy() {
        if (::gameView.isInitialized) gameView.stopTicker()
        if (::feedback.isInitialized) feedback.release()
        super.onDestroy()
    }

    private fun handleState(state: GameState) {
        updatePauseLabel()
        if (state.status == GameStatus.GAME_OVER && !gameOverShown) {
            gameOverShown = true
            scoreStore.record(ScoreEntry(state.score, state.lines, state.level))
            Handler(Looper.getMainLooper()).post { showGameOver(state) }
        }
        if (state.status != GameStatus.GAME_OVER) gameOverShown = false
    }

    private fun rebuildControls() {
        controls.removeAllViews()
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val hold = actionButton(getString(R.string.hold), getString(R.string.hold)) { gameView.dispatch(GameAction.Hold) }
        val rotateLeft = actionButton("↺", getString(R.string.rotate_left)) { gameView.dispatch(GameAction.RotateCounterClockwise) }
        val rotateRight = actionButton("↻", getString(R.string.rotate_right)) { gameView.dispatch(GameAction.RotateClockwise) }
        val drop = actionButton(getString(R.string.drop), getString(R.string.drop)) { gameView.dispatch(GameAction.HardDrop) }
        listOf(hold, rotateLeft, rotateRight, drop).forEach { top.addView(it, weightedButtonParams()) }

        val left = repeatButton("←", R.string.move_left, GameAction.MoveLeft)
        val down = repeatButton("↓", R.string.soft_drop, GameAction.SoftDrop)
        val right = repeatButton("→", R.string.move_right, GameAction.MoveRight)
        val newGame = actionButton(getString(R.string.new_game), getString(R.string.new_game)) {
            gameView.dispatch(GameAction.Restart)
            gameOverShown = false
        }
        val movement = if (settings.leftHanded) listOf(right, down, left, newGame) else listOf(left, down, right, newGame)
        movement.forEach { bottom.addView(it, weightedButtonParams()) }

        controls.addView(top)
        controls.addView(bottom)
    }

    private fun repeatButton(label: String, description: Int, action: GameAction): MaterialButton {
        val button = actionButton(label, getString(description)) {}
        button.setOnTouchListener(RepeatTouchListener(
            delay = { settings.repeatDelayMs.toLong() },
            rate = { settings.repeatRateMs.toLong() },
            action = { gameView.dispatch(action) },
        ))
        return button
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

    private fun weightedButtonParams(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, 52.dp(), 1f).apply {
        setMargins(3.dp(), 2.dp(), 3.dp(), 2.dp())
    }

    private fun showSettings() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8.dp(), 0, 8.dp(), 0)
        }
        val scroll = ScrollView(this).apply { addView(content) }

        val themeButton = actionButton("${getString(R.string.theme)}: ${themeName(settings.theme)}", getString(R.string.theme)) {}
        themeButton.setOnClickListener {
            settings = settings.copy(theme = settings.theme.next())
            settingsStore.save(settings)
            themeButton.text = "${getString(R.string.theme)}: ${themeName(settings.theme)}"
            gameView.updateSettings(settings)
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
            gameView.updateSettings(settings)
        })
        content.addView(switchSetting(getString(R.string.haptics), settings.hapticsEnabled) { value ->
            settings = settings.copy(hapticsEnabled = value)
            settingsStore.save(settings)
            gameView.updateSettings(settings)
        })
        content.addView(switchSetting(getString(R.string.left_handed), settings.leftHanded) { value ->
            settings = settings.copy(leftHanded = value)
            settingsStore.save(settings)
            gameView.updateSettings(settings)
            rebuildControls()
        })
        val previewButton = actionButton("${getString(R.string.preview_count)}: ${settings.previewCount}", getString(R.string.preview_count)) {}
        previewButton.setOnClickListener {
            settings = settings.copy(previewCount = if (settings.previewCount == 5) 1 else settings.previewCount + 1)
            settingsStore.save(settings)
            gameView.updateSettings(settings)
            previewButton.text = "${getString(R.string.preview_count)}: ${settings.previewCount}"
        }
        content.addView(previewButton)
        val delayButton = actionButton("${getString(R.string.repeat_delay)}: ${settings.repeatDelayMs} ms", getString(R.string.repeat_delay)) {}
        delayButton.setOnClickListener {
            settings = settings.copy(repeatDelayMs = if (settings.repeatDelayMs >= 400) 80 else settings.repeatDelayMs + 40)
            settingsStore.save(settings)
            delayButton.text = "${getString(R.string.repeat_delay)}: ${settings.repeatDelayMs} ms"
        }
        content.addView(delayButton)
        val rateButton = actionButton("${getString(R.string.repeat_rate)}: ${settings.repeatRateMs} ms", getString(R.string.repeat_rate)) {}
        rateButton.setOnClickListener {
            settings = settings.copy(repeatRateMs = if (settings.repeatRateMs >= 120) 16 else settings.repeatRateMs + 16)
            settingsStore.save(settings)
            rateButton.text = "${getString(R.string.repeat_rate)}: ${settings.repeatRateMs} ms"
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

    private fun showGameOver(state: GameState) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.game_over))
            .setMessage("Score ${state.score}\nLines ${state.lines}\nLevel ${state.level}")
            .setPositiveButton(getString(R.string.new_game)) { _, _ ->
                gameView.dispatch(GameAction.Restart)
                gameOverShown = false
            }
            .setNegativeButton(getString(R.string.high_scores)) { _, _ -> showScores() }
            .setOnDismissListener { gameOverShown = gameView.state().status == GameStatus.GAME_OVER }
            .show()
    }

    private fun showScores() {
        val scores = scoreStore.load()
        val message = if (scores.isEmpty()) {
            getString(R.string.no_scores)
        } else {
            scores.mapIndexed { index, score ->
                "${index + 1}. ${score.score} points · ${score.lines} lines · level ${score.level}"
            }.joinToString("\n")
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.high_scores))
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun updatePauseLabel() {
        if (!::pauseButton.isInitialized || !::gameView.isInitialized) return
        pauseButton.text = if (gameView.state().status == GameStatus.PAUSED) getString(R.string.resume) else getString(R.string.pause)
    }

    private fun themeName(theme: ThemeOption): String = when (theme) {
        ThemeOption.CLASSIC -> getString(R.string.classic_theme)
        ThemeOption.NEON -> getString(R.string.neon_theme)
        ThemeOption.MONOCHROME -> getString(R.string.mono_theme)
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private class RepeatTouchListener(
        private val delay: () -> Long,
        private val rate: () -> Long,
        private val action: () -> Unit,
    ) : View.OnTouchListener {
        private val handler = Handler(Looper.getMainLooper())
        private var pressed = false
        private val repeat = object : Runnable {
            override fun run() {
                if (!pressed) return
                action()
                handler.postDelayed(this, rate().coerceAtLeast(16L))
            }
        }

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pressed = true
                    action()
                    handler.postDelayed(repeat, delay().coerceAtLeast(0L))
                    view.isPressed = true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    pressed = false
                    handler.removeCallbacks(repeat)
                    view.isPressed = false
                    if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                }
            }
            return true
        }
    }
}
