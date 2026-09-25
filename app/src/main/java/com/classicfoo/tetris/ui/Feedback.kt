package com.classicfoo.tetris.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.classicfoo.tetris.engine.GameEvent
import com.classicfoo.tetris.settings.GameSettings

class Feedback(context: Context) {
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 65)
    private val vibrator = context.getSystemService(Vibrator::class.java)

    fun play(event: GameEvent, settings: GameSettings) {
        if (settings.soundEnabled) {
            val tone = when (event) {
                GameEvent.LINE_CLEAR -> ToneGenerator.TONE_PROP_ACK
                GameEvent.T_SPIN -> ToneGenerator.TONE_PROP_BEEP2
                GameEvent.GAME_OVER -> ToneGenerator.TONE_PROP_NACK
                GameEvent.HARD_DROP -> ToneGenerator.TONE_PROP_BEEP
                GameEvent.HOLD -> ToneGenerator.TONE_PROP_BEEP2
                else -> null
            }
            tone?.let { toneGenerator.startTone(it, 90) }
        }

        if (settings.hapticsEnabled && event != GameEvent.NONE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(22, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(22)
            }
        }
    }

    fun release() {
        toneGenerator.release()
    }
}
