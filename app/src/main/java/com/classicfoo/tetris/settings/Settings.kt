package com.classicfoo.tetris.settings

import android.content.Context
import com.classicfoo.tetris.engine.Tetromino

enum class ThemeOption {
    CLASSIC, NEON, MONOCHROME;

    fun next(): ThemeOption = entries[(ordinal + 1) % entries.size]
}

data class GameSettings(
    val theme: ThemeOption = ThemeOption.CLASSIC,
    val showGrid: Boolean = true,
    val showGhost: Boolean = true,
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val leftHanded: Boolean = false,
    val previewCount: Int = 5,
    val repeatDelayMs: Int = 167,
    val repeatRateMs: Int = 33,
)

data class ScoreEntry(
    val score: Long,
    val lines: Int,
    val level: Int,
)

class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): GameSettings = GameSettings(
        theme = runCatching {
            ThemeOption.valueOf(preferences.getString(KEY_THEME, ThemeOption.CLASSIC.name).orEmpty())
        }.getOrDefault(ThemeOption.CLASSIC),
        showGrid = preferences.getBoolean(KEY_GRID, true),
        showGhost = preferences.getBoolean(KEY_GHOST, true),
        soundEnabled = preferences.getBoolean(KEY_SOUND, true),
        hapticsEnabled = preferences.getBoolean(KEY_HAPTICS, true),
        leftHanded = preferences.getBoolean(KEY_LEFT_HANDED, false),
        previewCount = preferences.getInt(KEY_PREVIEW, 5).coerceIn(1, 5),
        repeatDelayMs = preferences.getInt(KEY_REPEAT_DELAY, 167).coerceIn(80, 400),
        repeatRateMs = preferences.getInt(KEY_REPEAT_RATE, 33).coerceIn(16, 120),
    )

    fun save(settings: GameSettings) {
        preferences.edit()
            .putString(KEY_THEME, settings.theme.name)
            .putBoolean(KEY_GRID, settings.showGrid)
            .putBoolean(KEY_GHOST, settings.showGhost)
            .putBoolean(KEY_SOUND, settings.soundEnabled)
            .putBoolean(KEY_HAPTICS, settings.hapticsEnabled)
            .putBoolean(KEY_LEFT_HANDED, settings.leftHanded)
            .putInt(KEY_PREVIEW, settings.previewCount.coerceIn(1, 5))
            .putInt(KEY_REPEAT_DELAY, settings.repeatDelayMs.coerceIn(80, 400))
            .putInt(KEY_REPEAT_RATE, settings.repeatRateMs.coerceIn(16, 120))
            .apply()
    }

    private companion object {
        const val PREFERENCES = "tetris_settings"
        const val KEY_THEME = "theme"
        const val KEY_GRID = "grid"
        const val KEY_GHOST = "ghost"
        const val KEY_SOUND = "sound"
        const val KEY_HAPTICS = "haptics"
        const val KEY_LEFT_HANDED = "left_handed"
        const val KEY_PREVIEW = "preview_count"
        const val KEY_REPEAT_DELAY = "repeat_delay"
        const val KEY_REPEAT_RATE = "repeat_rate"
    }
}

class ScoreStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): List<ScoreEntry> {
        return preferences.getString(KEY_SCORES, "")
            .orEmpty()
            .split(';')
            .filter { it.isNotBlank() }
            .mapNotNull { encoded ->
                val values = encoded.split(',')
                if (values.size != 3) return@mapNotNull null
                runCatching {
                    ScoreEntry(values[0].toLong(), values[1].toInt(), values[2].toInt())
                }.getOrNull()
            }
            .sortedWith(compareByDescending<ScoreEntry> { it.score }.thenByDescending { it.lines })
            .take(MAX_SCORES)
    }

    fun record(entry: ScoreEntry): List<ScoreEntry> {
        val updated = (load() + entry)
            .sortedWith(compareByDescending<ScoreEntry> { it.score }.thenByDescending { it.lines })
            .take(MAX_SCORES)
        val encoded = updated.joinToString(";") { "${it.score},${it.lines},${it.level}" }
        preferences.edit().putString(KEY_SCORES, encoded).apply()
        return updated
    }

    private companion object {
        const val PREFERENCES = "tetris_scores"
        const val KEY_SCORES = "scores"
        const val MAX_SCORES = 10
    }
}

@Suppress("UNUSED_PARAMETER")
private fun Tetromino?.asSaveValue(): String = this?.name.orEmpty()
