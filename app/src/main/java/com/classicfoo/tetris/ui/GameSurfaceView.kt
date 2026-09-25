package com.classicfoo.tetris.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.classicfoo.tetris.engine.ActivePiece
import com.classicfoo.tetris.engine.BOARD_HEIGHT
import com.classicfoo.tetris.engine.BOARD_WIDTH
import com.classicfoo.tetris.engine.BoardRules
import com.classicfoo.tetris.engine.GameAction
import com.classicfoo.tetris.engine.GameEngine
import com.classicfoo.tetris.engine.GameEvent
import com.classicfoo.tetris.engine.GameState
import com.classicfoo.tetris.engine.Tetromino
import com.classicfoo.tetris.settings.GameSettings
import com.classicfoo.tetris.settings.ThemeOption
import kotlin.math.abs
import kotlin.math.min

class GameSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    private val engine: GameEngine = GameEngine(),
    initialSettings: GameSettings = GameSettings(),
    private val feedback: Feedback? = null,
) : View(context, attrs) {
    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD)
    }
    private var settings = initialSettings
    private var running = false
    private var lastFrameAt = 0L
    private var lastFeedbackEvent = GameEvent.NONE
    private var flashUntil = 0L
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDownAt = 0L
    private var stateListener: ((GameState) -> Unit)? = null

    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            val delta = if (lastFrameAt == 0L) 16L else (now - lastFrameAt).coerceAtMost(100L)
            lastFrameAt = now
            dispatch(GameAction.Tick(delta))
            postOnAnimationDelayed(this, 16L)
        }
    }

    init {
        isFocusable = true
        contentDescription = "Tetris game board"
        gridPaint.style = Paint.Style.STROKE
        gridPaint.strokeWidth = 1f
    }

    fun setStateListener(listener: (GameState) -> Unit) {
        stateListener = listener
        listener(engine.state)
    }

    fun updateSettings(newSettings: GameSettings) {
        settings = newSettings
        invalidate()
    }

    fun currentSettings(): GameSettings = settings

    fun state(): GameState = engine.state

    fun dispatch(action: GameAction) {
        val before = engine.state
        val after = engine.dispatch(action)
        if (after.lastEvent != GameEvent.NONE && after.lastEvent != lastFeedbackEvent) {
            feedback?.play(after.lastEvent, settings)
            lastFeedbackEvent = after.lastEvent
        }
        if (after.lastLines > 0) flashUntil = SystemClock.uptimeMillis() + 220L
        if (before.status != after.status || action !is GameAction.Tick) {
            stateListener?.invoke(after)
        } else {
            stateListener?.invoke(after)
        }
        invalidate()
    }

    fun startTicker() {
        if (running) return
        running = true
        lastFrameAt = 0L
        removeCallbacks(frame)
        post(frame)
    }

    fun stopTicker() {
        running = false
        removeCallbacks(frame)
        lastFrameAt = 0L
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val state = engine.state
        val palette = Palette.forTheme(settings.theme)
        canvas.drawColor(palette.background)

        val topSpace = 82f
        val cellSize = min(width * 0.78f / BOARD_WIDTH, (height - topSpace - 20f) / BOARD_HEIGHT)
            .coerceAtLeast(8f)
        val boardWidth = cellSize * BOARD_WIDTH
        val boardHeight = cellSize * BOARD_HEIGHT
        val boardLeft = (width - boardWidth) / 2f
        val boardTop = topSpace + ((height - topSpace - boardHeight).coerceAtLeast(0f) / 2f)

        drawHud(canvas, state, palette)
        drawPreview(canvas, state, palette, boardLeft, boardTop, cellSize)
        drawBoard(canvas, state, palette, boardLeft, boardTop, cellSize)

        if (state.status == com.classicfoo.tetris.engine.GameStatus.PAUSED ||
            state.status == com.classicfoo.tetris.engine.GameStatus.GAME_OVER
        ) {
            drawOverlay(canvas, state, palette)
        }

        if (flashUntil > SystemClock.uptimeMillis()) {
            blockPaint.color = palette.flash
            blockPaint.alpha = 90
            canvas.drawRect(boardLeft, boardTop, boardLeft + boardWidth, boardTop + boardHeight, blockPaint)
            blockPaint.alpha = 255
            postInvalidateOnAnimation()
        }
    }

    private fun drawHud(canvas: Canvas, state: GameState, palette: Palette) {
        textPaint.color = palette.text
        textPaint.textSize = 18f.sp()
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("SCORE ${state.score}", 20f, 28f, textPaint)
        canvas.drawText("LEVEL ${state.level}", 20f, 54f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("LINES ${state.lines}", width - 20f, 28f, textPaint)
        canvas.drawText("${state.lastEventLabel()}", width - 20f, 54f, textPaint)
    }

    private fun drawBoard(
        canvas: Canvas,
        state: GameState,
        palette: Palette,
        left: Float,
        top: Float,
        cellSize: Float,
    ) {
        blockPaint.color = palette.board
        canvas.drawRoundRect(RectF(left - 5f, top - 5f, left + BOARD_WIDTH * cellSize + 5f, top + BOARD_HEIGHT * cellSize + 5f), 12f, 12f, blockPaint)

        if (settings.showGrid) {
            gridPaint.color = palette.grid
            gridPaint.alpha = 175
            for (x in 0..BOARD_WIDTH) {
                canvas.drawLine(left + x * cellSize, top, left + x * cellSize, top + BOARD_HEIGHT * cellSize, gridPaint)
            }
            for (y in 0..BOARD_HEIGHT) {
                canvas.drawLine(left, top + y * cellSize, left + BOARD_WIDTH * cellSize, top + y * cellSize, gridPaint)
            }
            gridPaint.alpha = 255
        }

        state.board.forEachIndexed { y, row ->
            row.forEachIndexed { x, type ->
                if (type != null) drawCell(canvas, palette.color(type), left + x * cellSize, top + y * cellSize, cellSize)
            }
        }

        if (settings.showGhost && state.status == com.classicfoo.tetris.engine.GameStatus.RUNNING) {
            drawPiece(canvas, state.current.copy(y = state.ghostY), palette, left, top, cellSize, ghost = true)
        }
        drawPiece(canvas, state.current, palette, left, top, cellSize, ghost = false)
    }

    private fun drawPreview(
        canvas: Canvas,
        state: GameState,
        palette: Palette,
        boardLeft: Float,
        boardTop: Float,
        cellSize: Float,
    ) {
        val previewCell = cellSize * 0.45f
        val previewX = boardLeft + BOARD_WIDTH * cellSize + 14f
        val previewY = boardTop + 8f
        textPaint.color = palette.text
        textPaint.textSize = 12f.sp()
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("NEXT", previewX, previewY, textPaint)
        state.next.take(settings.previewCount).forEachIndexed { index, type ->
            drawMiniPiece(canvas, type, palette, previewX, previewY + 18f + index * cellSize * 1.9f, previewCell)
        }

        val holdX = (boardLeft - cellSize * 3.6f).coerceAtLeast(8f)
        canvas.drawText("HOLD", holdX, previewY, textPaint)
        state.hold?.let { drawMiniPiece(canvas, it, palette, holdX, previewY + 18f, previewCell) }
    }

    private fun drawMiniPiece(canvas: Canvas, type: Tetromino, palette: Palette, x: Float, y: Float, cell: Float) {
        val cells = com.classicfoo.tetris.engine.PieceDefinitions.cells(type, com.classicfoo.tetris.engine.Rotation.SPAWN)
        val minX = cells.minOf { it.x }
        val minY = cells.minOf { it.y }
        cells.forEach { block ->
            drawCell(canvas, palette.color(type), x + (block.x - minX) * cell, y + (block.y - minY) * cell, cell)
        }
    }

    private fun drawPiece(canvas: Canvas, piece: ActivePiece, palette: Palette, left: Float, top: Float, cell: Float, ghost: Boolean) {
        val cells = com.classicfoo.tetris.engine.PieceDefinitions.cells(piece.type, piece.rotation)
        cells.forEach { block ->
            val x = left + (piece.x + block.x) * cell
            val y = top + (piece.y + block.y) * cell
            if (y + cell >= top) drawCell(canvas, palette.color(piece.type), x, y, cell, ghost)
        }
    }

    private fun drawCell(canvas: Canvas, color: Int, x: Float, y: Float, size: Float, ghost: Boolean = false) {
        blockPaint.color = color
        blockPaint.alpha = if (ghost) 55 else 255
        canvas.drawRoundRect(RectF(x + 1.5f, y + 1.5f, x + size - 1.5f, y + size - 1.5f), size * 0.14f, size * 0.14f, blockPaint)
        if (!ghost) {
            blockPaint.color = android.graphics.Color.WHITE
            blockPaint.alpha = 42
            canvas.drawRoundRect(RectF(x + size * 0.18f, y + size * 0.12f, x + size * 0.72f, y + size * 0.25f), size * 0.06f, size * 0.06f, blockPaint)
        }
        blockPaint.alpha = 255
    }

    private fun drawOverlay(canvas: Canvas, state: GameState, palette: Palette) {
        blockPaint.color = android.graphics.Color.BLACK
        blockPaint.alpha = 155
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), blockPaint)
        blockPaint.alpha = 255
        textPaint.color = palette.text
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 30f.sp()
        canvas.drawText(
            if (state.status == com.classicfoo.tetris.engine.GameStatus.PAUSED) "PAUSED" else "GAME OVER",
            width / 2f,
            height / 2f,
            textPaint,
        )
        textPaint.textSize = 15f.sp()
        canvas.drawText("Tap NEW GAME to play again", width / 2f, height / 2f + 34f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                touchDownAt = SystemClock.uptimeMillis()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - touchDownX
                val dy = event.y - touchDownY
                val duration = SystemClock.uptimeMillis() - touchDownAt
                when {
                    abs(dx) > abs(dy) && abs(dx) > 48f -> dispatch(if (dx > 0) GameAction.MoveRight else GameAction.MoveLeft)
                    dy > 48f -> dispatch(GameAction.HardDrop)
                    dy < -48f -> dispatch(GameAction.RotateClockwise)
                    duration < 300L -> dispatch(GameAction.RotateClockwise)
                }
                performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun GameState.lastEventLabel(): String = when (lastEvent) {
        GameEvent.LINE_CLEAR -> if (lastLines == 4) "TETRIS!" else "CLEAR!"
        GameEvent.T_SPIN -> "T-SPIN!"
        GameEvent.GAME_OVER -> "GAME OVER"
        else -> ""
    }

    private fun Float.sp(): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        this,
        resources.displayMetrics,
    )

    private data class Palette(
        val background: Int,
        val board: Int,
        val grid: Int,
        val text: Int,
        val flash: Int,
        val colors: Map<Tetromino, Int>,
    ) {
        fun color(type: Tetromino): Int = colors.getValue(type)

        companion object {
            fun forTheme(theme: ThemeOption): Palette = when (theme) {
                ThemeOption.CLASSIC -> Palette(
                    background = android.graphics.Color.rgb(16, 19, 28),
                    board = android.graphics.Color.rgb(27, 32, 48),
                    grid = android.graphics.Color.rgb(78, 88, 117),
                    text = android.graphics.Color.WHITE,
                    flash = android.graphics.Color.WHITE,
                    colors = mapOf(
                        Tetromino.I to android.graphics.Color.rgb(0, 188, 212),
                        Tetromino.O to android.graphics.Color.rgb(255, 202, 40),
                        Tetromino.T to android.graphics.Color.rgb(171, 71, 188),
                        Tetromino.J to android.graphics.Color.rgb(63, 81, 181),
                        Tetromino.L to android.graphics.Color.rgb(255, 112, 67),
                        Tetromino.S to android.graphics.Color.rgb(76, 175, 80),
                        Tetromino.Z to android.graphics.Color.rgb(239, 83, 80),
                    ),
                )
                ThemeOption.NEON -> Palette(
                    background = android.graphics.Color.rgb(8, 8, 20),
                    board = android.graphics.Color.rgb(22, 15, 44),
                    grid = android.graphics.Color.rgb(120, 65, 180),
                    text = android.graphics.Color.rgb(236, 224, 255),
                    flash = android.graphics.Color.rgb(255, 64, 200),
                    colors = mapOf(
                        Tetromino.I to android.graphics.Color.rgb(0, 245, 255),
                        Tetromino.O to android.graphics.Color.rgb(255, 238, 0),
                        Tetromino.T to android.graphics.Color.rgb(238, 0, 255),
                        Tetromino.J to android.graphics.Color.rgb(40, 100, 255),
                        Tetromino.L to android.graphics.Color.rgb(255, 128, 0),
                        Tetromino.S to android.graphics.Color.rgb(0, 255, 128),
                        Tetromino.Z to android.graphics.Color.rgb(255, 40, 80),
                    ),
                )
                ThemeOption.MONOCHROME -> Palette(
                    background = android.graphics.Color.rgb(20, 20, 20),
                    board = android.graphics.Color.rgb(43, 43, 43),
                    grid = android.graphics.Color.rgb(105, 105, 105),
                    text = android.graphics.Color.WHITE,
                    flash = android.graphics.Color.WHITE,
                    colors = Tetromino.entries.associateWith { android.graphics.Color.rgb(205, 205, 205) },
                )
            }
        }
    }
}
