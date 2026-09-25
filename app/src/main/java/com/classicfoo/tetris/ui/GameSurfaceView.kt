package com.classicfoo.tetris.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
import com.classicfoo.tetris.engine.GameStatus
import com.classicfoo.tetris.engine.PieceDefinitions
import com.classicfoo.tetris.engine.Rotation
import com.classicfoo.tetris.engine.Tetromino
import com.classicfoo.tetris.settings.GameSettings
import com.classicfoo.tetris.settings.ThemeOption
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class GameSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    private val engine: GameEngine = GameEngine(),
    initialSettings: GameSettings = GameSettings(),
    private val feedback: Feedback? = null,
) : View(context, attrs) {
    companion object {
        fun iconColor(theme: ThemeOption): Int = Palette.forTheme(theme).text

        fun backgroundColor(theme: ThemeOption): Int = Palette.forTheme(theme).background
    }

    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD)
        isSubpixelText = true
    }
    private var settings = initialSettings
    private var running = false
    private var lastFrameAt = 0L
    private var lastFeedbackEvent = GameEvent.NONE
    private var flashUntil = 0L
    private var stateListener: ((GameState) -> Unit)? = null
    private var gestureInterpreter = GestureInterpreter()

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
        contentDescription = "Tetris game board. Swipe left or right to move, tap a side to rotate, swipe down to drop, and swipe up to hold."
        setBackgroundColor(Color.TRANSPARENT)
    }

    fun setStateListener(listener: (GameState) -> Unit) {
        stateListener = listener
        listener(engine.state)
    }

    fun updateSettings(newSettings: GameSettings) {
        settings = newSettings
        updateGestureConfig()
        feedback?.updateSettings(settings)
        invalidate()
    }

    fun currentSettings(): GameSettings = settings

    fun state(): GameState = engine.state

    fun dispatch(action: GameAction) {
        val before = engine.state
        val after = engine.dispatch(action)
        feedback?.onStateChanged(action, before, after, settings)

        if (after.lastEvent != GameEvent.NONE &&
            (action !is GameAction.Tick || after.lastEvent != lastFeedbackEvent)
        ) {
            feedback?.play(after.lastEvent, settings)
            lastFeedbackEvent = after.lastEvent
        } else if (after.lastEvent == GameEvent.NONE) {
            lastFeedbackEvent = GameEvent.NONE
        }
        if (after.lastLines > 0) flashUntil = SystemClock.uptimeMillis() + 220L
        stateListener?.invoke(after)
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

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateGestureConfig()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val state = engine.state
        val palette = Palette.forTheme(settings.theme)
        val layout = calculateLayout()
        canvas.drawColor(palette.background)

        drawHud(canvas, state, palette, layout)
        drawBoard(canvas, state, palette, layout)

        if (state.status == GameStatus.PAUSED || state.status == GameStatus.GAME_OVER) {
            drawOverlay(canvas, palette)
        }

        if (flashUntil > SystemClock.uptimeMillis()) {
            blockPaint.style = Paint.Style.FILL
            blockPaint.color = palette.flash
            blockPaint.alpha = 86
            canvas.drawRect(layout.boardLeft, layout.boardTop, layout.boardRight, layout.boardBottom, blockPaint)
            blockPaint.alpha = 255
            postInvalidateOnAnimation()
        }
    }

    private fun calculateLayout(): BoardLayout {
        val margin = dp(12f)
        val hudHeight = min(
            height * 0.42f,
            max(dp(126f), dp(88f + settings.previewCount * 24f)),
        )
        val boardTopArea = hudHeight + dp(10f)
        val boardBottomArea = height - dp(10f)
        val cell = min(
            (width - margin * 2f) / BOARD_WIDTH,
            (boardBottomArea - boardTopArea) / BOARD_HEIGHT,
        ).coerceAtLeast(1f)
        val boardWidth = cell * BOARD_WIDTH
        val boardHeight = cell * BOARD_HEIGHT
        val boardLeft = (width - boardWidth) / 2f
        val boardTop = boardTopArea + ((boardBottomArea - boardTopArea - boardHeight).coerceAtLeast(0f) / 2f)
        return BoardLayout(
            hudHeight = hudHeight,
            cellSize = cell,
            boardLeft = boardLeft,
            boardTop = boardTop,
            boardRight = boardLeft + boardWidth,
            boardBottom = boardTop + boardHeight,
        )
    }

    private fun drawHud(canvas: Canvas, state: GameState, palette: Palette, layout: BoardLayout) {
        val margin = dp(12f)
        val headerTop = dp(8f)
        val headerBottom = dp(54f)
        val actionReserve = dp(112f)
        val statsRight = (width - actionReserve)
            .coerceAtLeast(margin + dp(120f))
            .coerceAtMost(width - margin)
        drawPanel(canvas, palette.panel, margin, headerTop, statsRight, headerBottom, dp(12f))

        val columnWidth = (statsRight - margin) / 3f
        drawStat(canvas, "SCORE", state.score.toString(), margin + columnWidth * 0.5f, headerTop, headerBottom, palette)
        drawStat(canvas, "LEVEL", state.level.toString(), margin + columnWidth * 1.5f, headerTop, headerBottom, palette)
        drawStat(canvas, "LINES", state.lines.toString(), margin + columnWidth * 2.5f, headerTop, headerBottom, palette)

        val cardTop = dp(64f)
        val cardBottom = (layout.hudHeight - dp(8f)).coerceAtLeast(cardTop + dp(38f))
        val cardWidth = dp(78f)
        drawPanel(canvas, palette.panel, margin, cardTop, margin + cardWidth, cardBottom, dp(12f))
        drawPanel(canvas, palette.panel, width - margin - cardWidth, cardTop, width - margin, cardBottom, dp(12f))
        drawCardLabel(canvas, "HOLD", margin + cardWidth / 2f, cardTop + dp(17f), palette)
        drawCardLabel(canvas, "NEXT", width - margin - cardWidth / 2f, cardTop + dp(17f), palette)

        val cardHeight = cardBottom - cardTop
        val holdCell = min(dp(17f), cardWidth / 5f)
        state.hold?.let {
            drawMiniPiece(canvas, it, palette, margin, cardTop + dp(23f), cardWidth, dp(29f), holdCell)
        }
        val nextItems = state.next.take(settings.previewCount)
        if (nextItems.isNotEmpty()) {
            val slotHeight = ((cardHeight - dp(24f)) / nextItems.size).coerceAtLeast(dp(18f))
            val nextCell = min(dp(10f), min(cardWidth / 7f, slotHeight / 4.2f))
            nextItems.forEachIndexed { index, type ->
                drawMiniPiece(
                    canvas,
                    type,
                    palette,
                    width - margin - cardWidth,
                    cardTop + dp(22f) + index * slotHeight,
                    cardWidth,
                    slotHeight,
                    nextCell,
                )
            }
        }

        val eventLabel = state.lastEventLabel()
        if (eventLabel.isNotEmpty()) {
            textPaint.color = palette.accent
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = canvasSp(15f, 17f)
            canvas.drawText(eventLabel, width / 2f, cardTop + cardHeight / 2f + dp(5f), textPaint)
        }
    }

    private fun drawStat(canvas: Canvas, label: String, value: String, centerX: Float, top: Float, bottom: Float, palette: Palette) {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = palette.mutedText
        textPaint.textSize = canvasSp(9f, 11f)
        canvas.drawText(label, centerX, top + dp(16f), textPaint)
        textPaint.color = palette.text
        textPaint.textSize = canvasSp(15f, 17f)
        canvas.drawText(value, centerX, bottom - dp(9f), textPaint)
    }

    private fun drawCardLabel(canvas: Canvas, label: String, centerX: Float, baseline: Float, palette: Palette) {
        textPaint.color = palette.mutedText
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = canvasSp(10f, 12f)
        canvas.drawText(label, centerX, baseline, textPaint)
    }

    private fun drawBoard(canvas: Canvas, state: GameState, palette: Palette, layout: BoardLayout) {
        blockPaint.style = Paint.Style.FILL
        blockPaint.color = palette.board
        canvas.drawRoundRect(
            RectF(layout.boardLeft - dp(4f), layout.boardTop - dp(4f), layout.boardRight + dp(4f), layout.boardBottom + dp(4f)),
            dp(8f),
            dp(8f),
            blockPaint,
        )

        if (settings.showGrid) {
            gridPaint.color = palette.grid
            gridPaint.alpha = if (palette.isGameBoy) 135 else 175
            gridPaint.strokeWidth = max(1f, dp(1f))
            for (x in 0..BOARD_WIDTH) {
                canvas.drawLine(layout.boardLeft + x * layout.cellSize, layout.boardTop, layout.boardLeft + x * layout.cellSize, layout.boardBottom, gridPaint)
            }
            for (y in 0..BOARD_HEIGHT) {
                canvas.drawLine(layout.boardLeft, layout.boardTop + y * layout.cellSize, layout.boardRight, layout.boardTop + y * layout.cellSize, gridPaint)
            }
            gridPaint.alpha = 255
        }

        state.board.forEachIndexed { y, row ->
            row.forEachIndexed { x, type ->
                if (type != null) drawCell(canvas, palette.color(type), layout.boardLeft + x * layout.cellSize, layout.boardTop + y * layout.cellSize, layout.cellSize, palette)
            }
        }
        if (settings.showGhost && state.status == GameStatus.RUNNING) {
            drawPiece(canvas, state.current.copy(y = state.ghostY), palette, layout, ghost = true)
        }
        drawPiece(canvas, state.current, palette, layout, ghost = false)
    }

    private fun drawPiece(canvas: Canvas, piece: ActivePiece, palette: Palette, layout: BoardLayout, ghost: Boolean) {
        PieceDefinitions.cells(piece.type, piece.rotation).forEach { block ->
            val x = layout.boardLeft + (piece.x + block.x) * layout.cellSize
            val y = layout.boardTop + (piece.y + block.y) * layout.cellSize
            if (y + layout.cellSize >= layout.boardTop && y <= layout.boardBottom) {
                drawCell(canvas, palette.color(piece.type), x, y, layout.cellSize, palette, ghost)
            }
        }
    }

    private fun drawMiniPiece(canvas: Canvas, type: Tetromino, palette: Palette, left: Float, top: Float, width: Float, height: Float, cell: Float) {
        val cells = PieceDefinitions.cells(type, Rotation.SPAWN)
        val minX = cells.minOf { it.x }
        val minY = cells.minOf { it.y }
        val maxX = cells.maxOf { it.x }
        val pieceWidth = (maxX - minX + 1) * cell
        val pieceHeight = (cells.maxOf { it.y } - minY + 1) * cell
        val x = left + (width - pieceWidth) / 2f
        val y = top + max(0f, (height - pieceHeight) / 2f)
        cells.forEach { block ->
            drawCell(canvas, palette.color(type), x + (block.x - minX) * cell, y + (block.y - minY) * cell, cell, palette)
        }
    }

    private fun drawCell(
        canvas: Canvas,
        color: Int,
        x: Float,
        y: Float,
        size: Float,
        palette: Palette,
        ghost: Boolean = false,
    ) {
        val inset = max(dp(1f), size * 0.055f)
        val rect = RectF(x + inset, y + inset, x + size - inset, y + size - inset)
        blockPaint.color = color
        blockPaint.alpha = if (ghost) 72 else 255
        if (ghost) {
            blockPaint.style = Paint.Style.STROKE
            blockPaint.strokeWidth = max(dp(1f), size * 0.08f)
            canvas.drawRect(rect, blockPaint)
            blockPaint.style = Paint.Style.FILL
            blockPaint.alpha = 255
            return
        }

        blockPaint.style = Paint.Style.FILL
        if (palette.bevel) {
            canvas.drawRect(rect, blockPaint)
            val bevel = max(dp(1f), size * 0.12f)
            blockPaint.color = palette.highlight
            canvas.drawRect(rect.left, rect.top, rect.right, rect.top + bevel, blockPaint)
            canvas.drawRect(rect.left, rect.top, rect.left + bevel, rect.bottom, blockPaint)
            blockPaint.color = palette.shadow
            canvas.drawRect(rect.left, rect.bottom - bevel, rect.right, rect.bottom, blockPaint)
            canvas.drawRect(rect.right - bevel, rect.top, rect.right, rect.bottom, blockPaint)
        } else {
            val radius = if (palette.isGameBoy) 0f else size * 0.12f
            canvas.drawRoundRect(rect, radius, radius, blockPaint)
            if (!palette.isGameBoy) {
                blockPaint.color = Color.WHITE
                blockPaint.alpha = 38
                canvas.drawRoundRect(
                    RectF(rect.left + size * 0.16f, rect.top + size * 0.12f, rect.right - size * 0.2f, rect.top + size * 0.24f),
                    size * 0.05f,
                    size * 0.05f,
                    blockPaint,
                )
            }
        }
        blockPaint.alpha = 255
    }

    private fun drawOverlay(canvas: Canvas, palette: Palette) {
        blockPaint.style = Paint.Style.FILL
        blockPaint.color = palette.overlay
        blockPaint.alpha = 220
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), blockPaint)
        blockPaint.alpha = 255
    }

    private fun drawPanel(canvas: Canvas, color: Int, left: Float, top: Float, right: Float, bottom: Float, radius: Float) {
        blockPaint.style = Paint.Style.FILL
        blockPaint.color = color
        blockPaint.alpha = 255
        canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, blockPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount > 1) {
            gestureInterpreter.cancel()
            parent?.requestDisallowInterceptTouchEvent(false)
            return true
        }
        if (engine.state.status != GameStatus.RUNNING) {
            gestureInterpreter.cancel()
            return true
        }
        val point = GesturePoint(event.x, event.y, event.eventTime)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                gestureInterpreter.onDown(point)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                gestureInterpreter.onMove(point).forEach(::dispatchGestureCommand)
                return true
            }
            MotionEvent.ACTION_UP -> {
                gestureInterpreter.onUp(point, width.toFloat()).forEach(::dispatchGestureCommand)
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                gestureInterpreter.cancel()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    private fun dispatchGestureCommand(command: GestureCommand) {
        when (command) {
            GestureCommand.MoveLeft -> dispatch(GameAction.MoveLeft)
            GestureCommand.MoveRight -> dispatch(GameAction.MoveRight)
            GestureCommand.SoftDrop -> dispatch(GameAction.SoftDrop)
            GestureCommand.HardDrop -> dispatch(GameAction.HardDrop)
            GestureCommand.RotateClockwise -> dispatch(GameAction.RotateClockwise)
            GestureCommand.RotateCounterClockwise -> dispatch(GameAction.RotateCounterClockwise)
            GestureCommand.Hold -> dispatch(GameAction.Hold)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateGestureConfig() {
        if (width <= 0 || height <= 0) return
        val layout = calculateLayout()
        gestureInterpreter = GestureInterpreter(
            touchSlopPx = dp(18f),
            horizontalStepPx = max(dp(22f), layout.cellSize * 0.42f),
            softDropStepPx = max(dp(18f), layout.cellSize * 0.36f),
            holdDistancePx = dp(68f),
            hardDropDistancePx = dp(76f),
            hardDropVelocityPxPerSecond = dp(900f),
        )
    }

    private fun GameState.lastEventLabel(): String = when (lastEvent) {
        GameEvent.LINE_CLEAR -> if (lastLines == 4) "TETRIS!" else "CLEAR!"
        GameEvent.T_SPIN -> "T-SPIN!"
        GameEvent.PERFECT_CLEAR -> "PERFECT CLEAR!"
        GameEvent.GAME_OVER -> "GAME OVER"
        else -> ""
    }

    private fun dp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        resources.displayMetrics,
    )

    private fun sp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        value,
        resources.displayMetrics,
    )

    private fun canvasSp(value: Float, maximumDp: Float): Float = min(sp(value), dp(maximumDp))

    private data class BoardLayout(
        val hudHeight: Float,
        val cellSize: Float,
        val boardLeft: Float,
        val boardTop: Float,
        val boardRight: Float,
        val boardBottom: Float,
    )

    private data class Palette(
        val background: Int,
        val panel: Int,
        val board: Int,
        val grid: Int,
        val text: Int,
        val mutedText: Int,
        val accent: Int,
        val flash: Int,
        val overlay: Int,
        val highlight: Int,
        val shadow: Int,
        val bevel: Boolean,
        val isGameBoy: Boolean,
        val colors: Map<Tetromino, Int>,
    ) {
        fun color(type: Tetromino): Int = colors.getValue(type)

        companion object {
            fun forTheme(theme: ThemeOption): Palette = when (theme) {
                ThemeOption.CLASSIC -> Palette(
                    background = Color.rgb(12, 15, 23),
                    panel = Color.rgb(27, 32, 48),
                    board = Color.rgb(19, 23, 34),
                    grid = Color.rgb(72, 82, 108),
                    text = Color.WHITE,
                    mutedText = Color.rgb(173, 182, 204),
                    accent = Color.rgb(112, 190, 255),
                    flash = Color.WHITE,
                    overlay = Color.rgb(5, 7, 12),
                    highlight = Color.WHITE,
                    shadow = Color.BLACK,
                    bevel = false,
                    isGameBoy = false,
                    colors = mapOf(
                        Tetromino.I to Color.rgb(0, 188, 212),
                        Tetromino.O to Color.rgb(255, 202, 40),
                        Tetromino.T to Color.rgb(171, 71, 188),
                        Tetromino.J to Color.rgb(63, 81, 181),
                        Tetromino.L to Color.rgb(255, 112, 67),
                        Tetromino.S to Color.rgb(76, 175, 80),
                        Tetromino.Z to Color.rgb(239, 83, 80),
                    ),
                )
                ThemeOption.TENGEN_BEVEL -> Palette(
                    background = Color.rgb(10, 16, 34),
                    panel = Color.rgb(27, 38, 68),
                    board = Color.rgb(8, 12, 26),
                    grid = Color.rgb(41, 58, 94),
                    text = Color.rgb(255, 245, 204),
                    mutedText = Color.rgb(177, 190, 220),
                    accent = Color.rgb(255, 206, 82),
                    flash = Color.rgb(255, 240, 158),
                    overlay = Color.rgb(4, 8, 19),
                    highlight = Color.argb(170, 255, 255, 255),
                    shadow = Color.argb(190, 0, 0, 0),
                    bevel = true,
                    isGameBoy = false,
                    colors = mapOf(
                        Tetromino.I to Color.rgb(38, 190, 210),
                        Tetromino.O to Color.rgb(247, 193, 43),
                        Tetromino.T to Color.rgb(157, 78, 178),
                        Tetromino.J to Color.rgb(44, 92, 184),
                        Tetromino.L to Color.rgb(222, 94, 47),
                        Tetromino.S to Color.rgb(48, 156, 74),
                        Tetromino.Z to Color.rgb(208, 54, 55),
                    ),
                )
                ThemeOption.GAME_BOY -> Palette(
                    background = Color.rgb(155, 188, 15),
                    panel = Color.rgb(139, 172, 15),
                    board = Color.rgb(35, 75, 35),
                    grid = Color.rgb(87, 126, 51),
                    text = Color.rgb(15, 56, 15),
                    mutedText = Color.rgb(48, 98, 48),
                    accent = Color.rgb(15, 56, 15),
                    flash = Color.rgb(155, 188, 15),
                    overlay = Color.rgb(155, 188, 15),
                    highlight = Color.rgb(155, 188, 15),
                    shadow = Color.rgb(15, 56, 15),
                    bevel = false,
                    isGameBoy = true,
                    colors = mapOf(
                        Tetromino.I to Color.rgb(15, 56, 15),
                        Tetromino.O to Color.rgb(155, 188, 15),
                        Tetromino.T to Color.rgb(87, 126, 51),
                        Tetromino.J to Color.rgb(15, 56, 15),
                        Tetromino.L to Color.rgb(139, 172, 15),
                        Tetromino.S to Color.rgb(87, 126, 51),
                        Tetromino.Z to Color.rgb(15, 56, 15),
                    ),
                )
            }
        }
    }
}
