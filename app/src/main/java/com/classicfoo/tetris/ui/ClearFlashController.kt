package com.classicfoo.tetris.ui

import com.classicfoo.tetris.engine.GameState
import com.classicfoo.tetris.engine.GameStatus
import kotlin.math.roundToInt

data class ClearFlashFrame(
    val rows: List<Int> = emptyList(),
    val alpha: Int = 0,
) {
    val active: Boolean
        get() = rows.isNotEmpty() && alpha > 0
}

/**
 * Converts immutable clear metadata into one deterministic, short-lived row pulse.
 * The controller has no Android clock or drawing dependencies; callers provide uptime.
 */
class ClearFlashController(
    private val durationMs: Long = 140L,
    private val maxAlpha: Int = 56,
) {
    private var seenSequence: Long? = null
    private var pulseStartedAtMs: Long? = null
    private var pulseRows: List<Int> = emptyList()

    init {
        require(durationMs in 120L..160L) { "Clear pulse must last 120-160 ms" }
        require(maxAlpha in 48..64) { "Clear pulse alpha must be 48-64" }
    }

    /**
     * Observes a state snapshot. A new clear sequence starts one pulse; repeated Tick
     * snapshots with the same sequence only advance the existing pulse.
     */
    fun observe(state: GameState, nowMs: Long): ClearFlashFrame {
        val previousSequence = seenSequence
        when {
            previousSequence == null -> seenSequence = state.clearSequence
            state.clearSequence < previousSequence -> {
                // A newly constructed engine or restored state may start a lower epoch.
                seenSequence = state.clearSequence
                cancel()
            }
            state.clearSequence > previousSequence -> {
                seenSequence = state.clearSequence
                if (state.status == GameStatus.RUNNING && state.lastClearedRows.isNotEmpty()) {
                    pulseStartedAtMs = nowMs
                    pulseRows = state.lastClearedRows.toList()
                } else {
                    cancel()
                }
            }
        }

        if (state.status != GameStatus.RUNNING) cancel()
        return frame(nowMs)
    }

    /** Cancels the visible pulse without forgetting the sequence already consumed. */
    fun cancel(consumedSequence: Long? = null) {
        if (consumedSequence != null) seenSequence = consumedSequence
        pulseStartedAtMs = null
        pulseRows = emptyList()
    }

    fun frame(nowMs: Long): ClearFlashFrame {
        val startedAt = pulseStartedAtMs ?: return ClearFlashFrame()
        val elapsed = (nowMs - startedAt).coerceAtLeast(0L)
        if (elapsed >= durationMs) {
            cancel()
            return ClearFlashFrame()
        }

        val remaining = durationMs - elapsed
        val alpha = (maxAlpha * remaining.toFloat() / durationMs).roundToInt()
            .coerceIn(1, maxAlpha)
        return ClearFlashFrame(pulseRows, alpha)
    }
}
