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
    private val durationMs: Long = DEFAULT_DURATION_MS,
    private val maxAlpha: Int = DEFAULT_MAX_ALPHA,
) {
    companion object {
        /** A full second gives the player time to register the clear and accolade. */
        const val DEFAULT_DURATION_MS: Long = 1_000L
        const val DEFAULT_MAX_ALPHA: Int = 160

        private const val PULSE_COUNT = 3
        /** Each flash fades out before the next one begins, leaving a clear dark beat. */
        private const val ACTIVE_FRACTION = 0.56f
    }

    private var seenSequence: Long? = null
    private var pulseStartedAtMs: Long? = null
    private var pulseRows: List<Int> = emptyList()

    init {
        require(durationMs in 800L..1_200L) { "Clear pulse must last 800-1200 ms" }
        require(maxAlpha in 96..192) { "Clear pulse alpha must be 96-192" }
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

        val alpha = pulseAlpha(elapsed)
        return ClearFlashFrame(pulseRows, alpha)
    }

    /**
     * Three identical, deterministic flashes: a bright leading edge, a quick fade, a
     * dark beat, then the next flash. The final pulse also fades to zero before expiry.
     */
    private fun pulseAlpha(elapsedMs: Long): Int {
        val periodMs = durationMs.toFloat() / PULSE_COUNT
        val phase = (elapsedMs.toFloat() % periodMs) / periodMs
        if (phase >= ACTIVE_FRACTION) return 0

        return (maxAlpha * (1f - phase / ACTIVE_FRACTION))
            .roundToInt()
            .coerceIn(0, maxAlpha)
    }
}
