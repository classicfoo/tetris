package com.classicfoo.tetris.settings

import android.content.Context
import androidx.core.content.edit

data class GameSettings(
    val showGrid: Boolean = true,
    val showGhost: Boolean = true,
    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
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

internal const val CURRENT_LEVEL_BASE = 0
internal const val LEGACY_LEVEL_BASE = 1

internal fun migrateScoreLevel(level: Int, storedLevelBase: Int): Int =
    (level + CURRENT_LEVEL_BASE - storedLevelBase).coerceAtLeast(CURRENT_LEVEL_BASE)

class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): GameSettings {
        // Discard the old selectable-theme value while leaving every gameplay
        // preference untouched. Tengen Bevel is now the only supported style.
        preferences.edit { remove(KEY_THEME) }
        return GameSettings(
            showGrid = preferences.getBoolean(KEY_GRID, true),
            showGhost = preferences.getBoolean(KEY_GHOST, true),
            soundEnabled = preferences.getBoolean(KEY_SOUND, true),
            musicEnabled = preferences.getBoolean(KEY_MUSIC, true),
            hapticsEnabled = preferences.getBoolean(KEY_HAPTICS, true),
            leftHanded = preferences.getBoolean(KEY_LEFT_HANDED, false),
            previewCount = preferences.getInt(KEY_PREVIEW, 5).coerceIn(1, 5),
            repeatDelayMs = preferences.getInt(KEY_REPEAT_DELAY, 167).coerceIn(80, 400),
            repeatRateMs = preferences.getInt(KEY_REPEAT_RATE, 33).coerceIn(16, 120),
        )
    }

    fun save(settings: GameSettings) {
        preferences.edit {
            // Do not persist theme selection. Remove the legacy key so an
            // existing install cannot retain an obsolete alternate style.
            remove(KEY_THEME)
            .putBoolean(KEY_GRID, settings.showGrid)
            .putBoolean(KEY_GHOST, settings.showGhost)
            .putBoolean(KEY_SOUND, settings.soundEnabled)
            .putBoolean(KEY_MUSIC, settings.musicEnabled)
            .putBoolean(KEY_HAPTICS, settings.hapticsEnabled)
            .putBoolean(KEY_LEFT_HANDED, settings.leftHanded)
            .putInt(KEY_PREVIEW, settings.previewCount.coerceIn(1, 5))
            .putInt(KEY_REPEAT_DELAY, settings.repeatDelayMs.coerceIn(80, 400))
            .putInt(KEY_REPEAT_RATE, settings.repeatRateMs.coerceIn(16, 120))
        }
    }

    private companion object {
        const val PREFERENCES = "tetris_settings"
        // Legacy key removed on load/save; no theme is part of GameSettings.
        const val KEY_THEME = "theme"
        const val KEY_GRID = "grid"
        const val KEY_GHOST = "ghost"
        const val KEY_SOUND = "sound"
        const val KEY_MUSIC = "music"
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
        val storedLevelBase = preferences.getInt(KEY_LEVEL_BASE, LEGACY_LEVEL_BASE)
        val entries = preferences.getString(KEY_SCORES, "")
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
            .map { entry ->
                entry.copy(level = migrateScoreLevel(entry.level, storedLevelBase))
            }
            .sortedWith(compareByDescending<ScoreEntry> { it.score }.thenByDescending { it.lines })
            .take(MAX_SCORES)
        if (storedLevelBase != CURRENT_LEVEL_BASE) persist(entries)
        return entries
    }

    fun record(entry: ScoreEntry): List<ScoreEntry> {
        val updated = (load() + entry)
            .sortedWith(compareByDescending<ScoreEntry> { it.score }.thenByDescending { it.lines })
            .take(MAX_SCORES)
        persist(updated)
        return updated
    }

    private fun persist(entries: List<ScoreEntry>) {
        val encoded = entries.joinToString(";") { "${it.score},${it.lines},${it.level}" }
        preferences.edit {
            putString(KEY_SCORES, encoded)
            putInt(KEY_LEVEL_BASE, CURRENT_LEVEL_BASE)
        }
    }

    private companion object {
        const val PREFERENCES = "tetris_scores"
        const val KEY_SCORES = "scores"
        const val KEY_LEVEL_BASE = "level_base"
        const val MAX_SCORES = 10
    }
}
