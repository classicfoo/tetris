package com.classicfoo.tetris.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.classicfoo.tetris.audio.AudioPlayer
import com.classicfoo.tetris.audio.ChiptuneAudioPlayer
import com.classicfoo.tetris.engine.GameAction
import com.classicfoo.tetris.engine.GameEvent
import com.classicfoo.tetris.engine.GameState
import com.classicfoo.tetris.engine.GameStatus
import com.classicfoo.tetris.settings.GameSettings

class Feedback(
    context: Context,
    private val audio: AudioPlayer = ChiptuneAudioPlayer(),
) {
    private val vibrator = context.getSystemService(Vibrator::class.java)

    fun play(event: GameEvent, settings: GameSettings) {
        audio.updateSettings(settings)
        audio.playEffect(event)
        if (settings.hapticsEnabled && event != GameEvent.NONE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(22, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(22)
            }
        }
    }

    fun onStateChanged(action: GameAction, before: GameState, after: GameState, settings: GameSettings) {
        audio.updateSettings(settings)
        when {
            action == GameAction.Restart -> audio.restartMusic()
            after.status == GameStatus.PAUSED -> audio.pauseMusic()
            after.status == GameStatus.GAME_OVER -> audio.stopMusic()
            before.status == GameStatus.PAUSED && after.status == GameStatus.RUNNING -> audio.resumeMusic()
            before.status == GameStatus.GAME_OVER && after.status == GameStatus.RUNNING -> audio.startMusic()
        }
    }

    fun startMusic() = audio.startMusic()

    fun pauseMusic() = audio.pauseMusic()

    fun resumeMusic() = audio.resumeMusic()

    fun updateSettings(settings: GameSettings) = audio.updateSettings(settings)

    fun release() = audio.release()
}
