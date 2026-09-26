package com.classicfoo.tetris.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
    private val pixelPaint = Paint().apply {
        isAntiAlias = false
        isDither = false
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pixelGridPaint = Paint().apply {
        isAntiAlias = false
        isDither = false
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD)
        isSubpixelText = true
    }
    private var settings = initialSettings
    private var running = false
    private var lastFrameAt = 0L
    private var lastFeedbackEvent = GameEvent.NONE
    private var stateListener: ((GameState) -> Unit)? = null
    private var gestureInterpreter = GestureInterpreter()
    private val clearFlashController = ClearFlashController()
    private var tengenBoardCacheKey: TengenBoardCacheKey? = null
    private var tengenBoardSurface: TengenBitmapSurface? = null

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
        clearFlashController.cancel(engine.state.clearSequence)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        tengenBoardCacheKey = null
        tengenBoardSurface = null
        updateGestureConfig()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val state = engine.state
        val palette = Palette.forTheme(settings.theme)
        val layout = calculateLayout()
        val now = SystemClock.uptimeMillis()
        val clearFlash = clearFlashController.observe(state, now)
        canvas.drawColor(palette.background)

        drawHud(canvas, state, palette, layout)
        drawBoard(canvas, state, palette, layout, clearFlash)
        drawAccolade(canvas, state, palette, layout)

        if (state.status == GameStatus.PAUSED || state.status == GameStatus.GAME_OVER) {
            drawOverlay(canvas, palette)
        }

        // Keep ticking through the intentionally dark gaps between flashes.
        if (clearFlash.rows.isNotEmpty()) {
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

    }

    private fun drawAccolade(canvas: Canvas, state: GameState, palette: Palette, layout: BoardLayout) {
        val label = state.lastEventLabel()
        if (label.isEmpty()) return

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = canvasSp(18f, 24f)
        val centerX = (layout.boardLeft + layout.boardRight) / 2f
        val baseline = (layout.boardTop + layout.boardBottom) / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        val horizontalPadding = dp(16f)
        val verticalPadding = dp(9f)
        val box = RectF(
            centerX - textPaint.measureText(label) / 2f - horizontalPadding,
            baseline + textPaint.ascent() - verticalPadding,
            centerX + textPaint.measureText(label) / 2f + horizontalPadding,
            baseline + textPaint.descent() + verticalPadding,
        )

        blockPaint.style = Paint.Style.FILL
        blockPaint.color = palette.overlay
        blockPaint.alpha = 232
        canvas.drawRoundRect(box, dp(10f), dp(10f), blockPaint)
        blockPaint.style = Paint.Style.STROKE
        blockPaint.strokeWidth = max(1f, dp(1f))
        blockPaint.color = palette.accent
        blockPaint.alpha = 220
        canvas.drawRoundRect(box, dp(10f), dp(10f), blockPaint)
        blockPaint.style = Paint.Style.FILL
        textPaint.color = palette.accent
        canvas.drawText(label, centerX, baseline, textPaint)
        blockPaint.alpha = 255
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

    private fun drawBoard(
        canvas: Canvas,
        state: GameState,
        palette: Palette,
        layout: BoardLayout,
        clearFlash: ClearFlashFrame,
    ) {
        val outer = RectF(
            snap(layout.boardLeft - dp(4f)),
            snap(layout.boardTop - dp(4f)),
            snap(layout.boardRight + dp(4f)),
            snap(layout.boardBottom + dp(4f)),
        )
        if (palette.pixelStyle) {
            pixelPaint.style = Paint.Style.FILL
            pixelPaint.alpha = 255
            pixelPaint.color = palette.pixelOutline
            canvas.drawRect(outer, pixelPaint)
            pixelPaint.color = palette.board
            canvas.drawRect(
                snap(layout.boardLeft),
                snap(layout.boardTop),
                snap(layout.boardRight),
                snap(layout.boardBottom),
                pixelPaint,
            )
        } else {
            blockPaint.style = Paint.Style.FILL
            blockPaint.color = palette.board
            canvas.drawRoundRect(outer, dp(8f), dp(8f), blockPaint)
        }

        if (settings.showGrid) {
            if (palette.pixelStyle) {
                drawPixelGrid(canvas, palette, layout)
            } else {
                gridPaint.color = palette.grid
                gridPaint.alpha = palette.gridAlpha
                gridPaint.strokeWidth = max(1f, dp(1f))
                for (x in 0..BOARD_WIDTH) {
                    canvas.drawLine(layout.boardLeft + x * layout.cellSize, layout.boardTop, layout.boardLeft + x * layout.cellSize, layout.boardBottom, gridPaint)
                }
                for (y in 0..BOARD_HEIGHT) {
                    canvas.drawLine(layout.boardLeft, layout.boardTop + y * layout.cellSize, layout.boardRight, layout.boardTop + y * layout.cellSize, gridPaint)
                }
                gridPaint.alpha = 255
            }
        }

        if (palette.bevel) {
            drawTengenBoard(canvas, state, palette, layout)
        } else {
            state.board.forEachIndexed { y, row ->
                row.forEachIndexed { x, type ->
                    if (type != null) drawCell(canvas, type, layout.boardLeft + x * layout.cellSize, layout.boardTop + y * layout.cellSize, layout.cellSize, palette)
                }
            }
        }

        // During a staged clear, the completed rows are still present. Draw
        // the pulse over them so the player can see exactly what is about to
        // disappear; after compaction the controller cancels on the next
        // snapshot, so it cannot flash unrelated rows.
        drawClearFlash(canvas, layout, palette, clearFlash)

        if (!state.isClearing && settings.showGhost && state.status == GameStatus.RUNNING) {
            drawPiece(canvas, state.current.copy(y = state.ghostY), palette, layout, ghost = true)
        }
        if (!state.isClearing) {
            drawPiece(canvas, state.current, palette, layout, ghost = false)
        }
    }

    private fun drawTengenBoard(canvas: Canvas, state: GameState, palette: Palette, layout: BoardLayout) {
        val key = TengenBoardCacheKey(
            boardHash = state.board.hashCode(),
            theme = settings.theme,
            clearSequence = state.clearSequence,
            clearing = state.isClearing,
            clearedRowsHash = state.lastClearedRows.hashCode(),
            left = snap(layout.boardLeft).toInt(),
            top = snap(layout.boardTop).toInt(),
            right = snap(layout.boardRight).toInt(),
            bottom = snap(layout.boardBottom).toInt(),
        )
        if (key != tengenBoardCacheKey) {
            val cells = buildList {
                state.board.forEachIndexed { y, row ->
                    row.forEachIndexed { x, type ->
                        if (type != null) {
                            add(
                                TengenSurfaceCell(
                                    coordinate = PixelPoint(x, y),
                                    bounds = pixelGridCell(x, y, layout.boardLeft, layout.boardTop, layout.cellSize),
                                    ramp = palette.ramp(type),
                                ),
                            )
                        }
                    }
                }
            }
            tengenBoardSurface = TengenSurfaceRasterizer.rasterize(cells, palette.pixelOutline)?.toBitmapSurface()
            tengenBoardCacheKey = key
        }

        val surface = tengenBoardSurface ?: return
        pixelPaint.alpha = 255
        pixelPaint.isFilterBitmap = false
        canvas.drawBitmap(surface.bitmap, surface.left.toFloat(), surface.top.toFloat(), pixelPaint)
    }

    private fun drawClearFlash(
        canvas: Canvas,
        layout: BoardLayout,
        palette: Palette,
        flash: ClearFlashFrame,
    ) {
        if (!flash.active) return
        pixelPaint.style = Paint.Style.FILL
        pixelPaint.color = palette.flash
        pixelPaint.alpha = flash.alpha
        flash.rows.forEach { row ->
            if (row !in 0 until BOARD_HEIGHT) return@forEach
            val top = snap(layout.boardTop + row * layout.cellSize)
            val bottom = snap(layout.boardTop + (row + 1) * layout.cellSize)
            canvas.drawRect(snap(layout.boardLeft), top, snap(layout.boardRight), bottom, pixelPaint)
        }
        pixelPaint.alpha = 255
    }

    private fun drawPixelGrid(canvas: Canvas, palette: Palette, layout: BoardLayout) {
        pixelGridPaint.color = palette.grid
        pixelGridPaint.alpha = palette.gridAlpha
        val top = snap(layout.boardTop)
        val bottom = snap(layout.boardBottom)
        val left = snap(layout.boardLeft)
        val right = snap(layout.boardRight)
        for (x in 1 until BOARD_WIDTH) {
            val px = snap(layout.boardLeft + x * layout.cellSize)
            canvas.drawRect(px, top, px + 1f, bottom, pixelGridPaint)
        }
        for (y in 1 until BOARD_HEIGHT) {
            val py = snap(layout.boardTop + y * layout.cellSize)
            canvas.drawRect(left, py, right, py + 1f, pixelGridPaint)
        }
        pixelGridPaint.alpha = 255
    }

    private fun drawPiece(canvas: Canvas, piece: ActivePiece, palette: Palette, layout: BoardLayout, ghost: Boolean) {
        val blocks = PieceDefinitions.cells(piece.type, piece.rotation)
        if (palette.bevel && ghost) {
            drawTengenGhost(canvas, piece, palette, layout)
            return
        }
        if (palette.bevel && !ghost) {
            val visibleCells = blocks.mapNotNull { block ->
                val gridX = piece.x + block.x
                val gridY = piece.y + block.y
                val cell = pixelGridCell(gridX, gridY, layout.boardLeft, layout.boardTop, layout.cellSize)
                if (cell.bottom >= snap(layout.boardTop).toInt() && cell.top <= snap(layout.boardBottom).toInt()) {
                    TengenSurfaceCell(
                        coordinate = PixelPoint(gridX, gridY),
                        bounds = cell,
                        ramp = palette.ramp(piece.type),
                    )
                } else {
                    null
                }
            }
            if (visibleCells.isNotEmpty()) drawTengenPiece(canvas, visibleCells, palette)
            return
        }
        blocks.forEach { block ->
            val x = layout.boardLeft + (piece.x + block.x) * layout.cellSize
            val y = layout.boardTop + (piece.y + block.y) * layout.cellSize
            if (y + layout.cellSize >= layout.boardTop && y <= layout.boardBottom) {
                drawCell(canvas, piece.type, x, y, layout.cellSize, palette, ghost)
            }
        }
    }

    /** Tengen ghosts are a light, connected silhouette rather than four outlines. */
    private fun drawTengenGhost(
        canvas: Canvas,
        piece: ActivePiece,
        palette: Palette,
        layout: BoardLayout,
    ) {
        val geometry = GhostPieceGeometry.from(piece)
        pixelPaint.style = Paint.Style.FILL
        pixelPaint.color = palette.ghostColor
        pixelPaint.alpha = palette.ghostAlpha
        geometry.solidRegions.forEach { region ->
            if (region.bottom <= 0 || region.top >= BOARD_HEIGHT) return@forEach
            val left = snap(layout.boardLeft + region.left * layout.cellSize)
            val top = snap(layout.boardTop + region.top * layout.cellSize)
            val right = snap(layout.boardLeft + region.right * layout.cellSize)
            val bottom = snap(layout.boardTop + region.bottom * layout.cellSize)
            canvas.drawRect(left, top, right, bottom, pixelPaint)
        }
        pixelPaint.alpha = 255
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
        if (palette.bevel) {
            drawTengenPiece(
                canvas = canvas,
                cells = cells.map { block ->
                    tengenCell(
                        gridX = block.x - minX,
                        gridY = block.y - minY,
                        originX = x,
                        originY = y,
                        size = cell,
                        ramp = palette.ramp(type),
                    )
                },
                palette = palette,
            )
            return
        }
        cells.forEach { block ->
            drawCell(canvas, type, x + (block.x - minX) * cell, y + (block.y - minY) * cell, cell, palette)
        }
    }

    private fun pixelGridCell(gridX: Int, gridY: Int, originX: Float, originY: Float, size: Float): PixelRect {
        val left = snap(originX + gridX * size).toInt()
        val top = snap(originY + gridY * size).toInt()
        val right = snap(originX + (gridX + 1) * size).toInt()
        val bottom = snap(originY + (gridY + 1) * size).toInt()
        return PixelRect(left, top, right, bottom)
    }

    private fun tengenCell(
        gridX: Int,
        gridY: Int,
        originX: Float,
        originY: Float,
        size: Float,
        ramp: OpaqueColorRamp,
    ): TengenSurfaceCell = TengenSurfaceCell(
        coordinate = PixelPoint(gridX, gridY),
        bounds = pixelGridCell(gridX, gridY, originX, originY, size),
        ramp = ramp,
    )

    private fun drawTengenPiece(
        canvas: Canvas,
        cells: List<TengenSurfaceCell>,
        palette: Palette,
    ) {
        val raster = TengenSurfaceRasterizer.rasterize(cells, palette.pixelOutline) ?: return
        val bitmap = raster.toBitmap()
        pixelPaint.alpha = 255
        pixelPaint.isFilterBitmap = false
        canvas.drawBitmap(bitmap, raster.bounds.left.toFloat(), raster.bounds.top.toFloat(), pixelPaint)
    }

    private fun TengenRaster.toBitmap(): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
        it.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    private fun TengenRaster.toBitmapSurface(): TengenBitmapSurface = TengenBitmapSurface(
        bitmap = toBitmap(),
        left = bounds.left,
        top = bounds.top,
    )

    private fun drawCell(
        canvas: Canvas,
        type: Tetromino,
        x: Float,
        y: Float,
        size: Float,
        palette: Palette,
        ghost: Boolean = false,
    ) {
        val color = palette.color(type)
        if (palette.pixelStyle) {
            drawPixelCell(canvas, palette, palette.ramp(type), x, y, size, ghost)
            return
        }
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
        val radius = size * 0.12f
        canvas.drawRoundRect(rect, radius, radius, blockPaint)
        blockPaint.color = Color.WHITE
        blockPaint.alpha = 38
        canvas.drawRoundRect(
            RectF(rect.left + size * 0.16f, rect.top + size * 0.12f, rect.right - size * 0.2f, rect.top + size * 0.24f),
            size * 0.05f,
            size * 0.05f,
            blockPaint,
        )
        blockPaint.alpha = 255
    }

    private fun drawPixelCell(
        canvas: Canvas,
        palette: Palette,
        ramp: OpaqueColorRamp,
        x: Float,
        y: Float,
        size: Float,
        ghost: Boolean,
    ) {
        if (palette.bevel && !ghost) {
            val raster = TengenSurfaceRasterizer.rasterize(
                cells = listOf(tengenCell(0, 0, x, y, size, ramp)),
                outlineColor = palette.pixelOutline,
            ) ?: return
            val bitmap = raster.toBitmap()
            pixelPaint.alpha = 255
            pixelPaint.isFilterBitmap = false
            canvas.drawBitmap(bitmap, raster.bounds.left.toFloat(), raster.bounds.top.toFloat(), pixelPaint)
            return
        }
        pixelPaint.style = if (ghost) Paint.Style.STROKE else Paint.Style.FILL
        pixelPaint.strokeWidth = 1f
        pixelPaint.alpha = if (ghost) palette.ghostAlpha else 255
        if (ghost) {
            pixelPaint.color = palette.ghostColor
            val cell = pixelGridCell(0, 0, x, y, size)
            canvas.drawRect(
                cell.left + 0.5f,
                cell.top + 0.5f,
                cell.right - 0.5f,
                cell.bottom - 0.5f,
                pixelPaint,
            )
        } else {
            val cell = pixelGridCell(0, 0, x, y, size)
            pixelPaint.color = palette.boardFrame
            canvas.drawRect(cell.left.toFloat(), cell.top.toFloat(), cell.right.toFloat(), cell.bottom.toFloat(), pixelPaint)
            pixelPaint.color = ramp.base
            canvas.drawRect(
                (cell.left + 1).toFloat(),
                (cell.top + 1).toFloat(),
                (cell.right - 1).toFloat(),
                (cell.bottom - 1).toFloat(),
                pixelPaint,
            )
        }
        pixelPaint.alpha = 255
        pixelPaint.style = Paint.Style.FILL
    }

    private fun snap(value: Float): Float = value.roundToInt().toFloat()

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
        if (engine.state.status != GameStatus.RUNNING || engine.state.isClearing) {
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
        GameEvent.LINE_CLEAR -> ClearAccolade.forLines(lastLines).label
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

    private data class TengenBoardCacheKey(
        val boardHash: Int,
        val theme: ThemeOption,
        val clearSequence: Long,
        val clearing: Boolean,
        val clearedRowsHash: Int,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    private data class TengenBitmapSurface(
        val bitmap: Bitmap,
        val left: Int,
        val top: Int,
    )

    private data class Palette(
        val background: Int,
        val panel: Int,
        val board: Int,
        val boardFrame: Int,
        val pixelOutline: Int,
        val grid: Int,
        val gridAlpha: Int,
        val text: Int,
        val mutedText: Int,
        val accent: Int,
        val flash: Int,
        val overlay: Int,
        val ghostColor: Int,
        val ghostAlpha: Int,
        val bevel: Boolean,
        val isGameBoy: Boolean,
        val colors: Map<Tetromino, OpaqueColorRamp>,
    ) {
        val pixelStyle: Boolean get() = bevel || isGameBoy

        fun ramp(type: Tetromino): OpaqueColorRamp = colors.getValue(type)

        fun color(type: Tetromino): Int = ramp(type).base

        companion object {
            fun forTheme(theme: ThemeOption): Palette = when (theme) {
                ThemeOption.CLASSIC -> Palette(
                    background = Color.rgb(12, 15, 23),
                    panel = Color.rgb(27, 32, 48),
                    board = Color.rgb(19, 23, 34),
                    boardFrame = Color.rgb(27, 32, 48),
                    pixelOutline = Color.rgb(12, 15, 23),
                    grid = Color.rgb(72, 82, 108),
                    gridAlpha = 175,
                    text = Color.WHITE,
                    mutedText = Color.rgb(173, 182, 204),
                    accent = Color.rgb(112, 190, 255),
                    flash = Color.WHITE,
                    overlay = Color.rgb(5, 7, 12),
                    ghostColor = Color.WHITE,
                    ghostAlpha = 72,
                    bevel = false,
                    isGameBoy = false,
                    colors = mapOf(
                        Tetromino.I to OpaqueColorRamp(Color.rgb(0, 188, 212), Color.WHITE, Color.BLACK),
                        Tetromino.O to OpaqueColorRamp(Color.rgb(255, 202, 40), Color.WHITE, Color.BLACK),
                        Tetromino.T to OpaqueColorRamp(Color.rgb(171, 71, 188), Color.WHITE, Color.BLACK),
                        Tetromino.J to OpaqueColorRamp(Color.rgb(63, 81, 181), Color.WHITE, Color.BLACK),
                        Tetromino.L to OpaqueColorRamp(Color.rgb(255, 112, 67), Color.WHITE, Color.BLACK),
                        Tetromino.S to OpaqueColorRamp(Color.rgb(76, 175, 80), Color.WHITE, Color.BLACK),
                        Tetromino.Z to OpaqueColorRamp(Color.rgb(239, 83, 80), Color.WHITE, Color.BLACK),
                    ),
                )
                ThemeOption.TENGEN_BEVEL -> Palette(
                    background = Color.rgb(6, 17, 38),
                    panel = Color.rgb(18, 38, 74),
                    board = Color.rgb(11, 23, 49),
                    boardFrame = Color.rgb(39, 79, 150),
                    pixelOutline = Color.rgb(5, 10, 23),
                    grid = Color.rgb(37, 65, 111),
                    gridAlpha = 64,
                    text = Color.rgb(255, 245, 204),
                    mutedText = Color.rgb(177, 190, 220),
                    accent = Color.rgb(255, 206, 82),
                    flash = Color.rgb(255, 240, 158),
                    overlay = Color.rgb(4, 8, 19),
                    ghostColor = Color.rgb(255, 245, 204),
                    ghostAlpha = 128,
                    bevel = true,
                    isGameBoy = false,
                    colors = TengenBevelPalette.ramps,
                )
                ThemeOption.GAME_BOY -> Palette(
                    background = Color.rgb(155, 188, 15),
                    panel = Color.rgb(139, 172, 15),
                    board = Color.rgb(35, 75, 35),
                    boardFrame = Color.rgb(15, 56, 15),
                    pixelOutline = Color.rgb(15, 56, 15),
                    grid = Color.rgb(87, 126, 51),
                    gridAlpha = 85,
                    text = Color.rgb(15, 56, 15),
                    mutedText = Color.rgb(48, 98, 48),
                    accent = Color.rgb(15, 56, 15),
                    flash = Color.rgb(155, 188, 15),
                    overlay = Color.rgb(155, 188, 15),
                    ghostColor = Color.rgb(155, 188, 15),
                    ghostAlpha = 170,
                    bevel = false,
                    isGameBoy = true,
                    colors = mapOf(
                        Tetromino.I to OpaqueColorRamp(Color.rgb(15, 56, 15), Color.rgb(48, 98, 48), Color.rgb(15, 56, 15)),
                        Tetromino.O to OpaqueColorRamp(Color.rgb(155, 188, 15), Color.rgb(48, 98, 48), Color.rgb(15, 56, 15)),
                        Tetromino.T to OpaqueColorRamp(Color.rgb(87, 126, 51), Color.rgb(155, 188, 15), Color.rgb(15, 56, 15)),
                        Tetromino.J to OpaqueColorRamp(Color.rgb(15, 56, 15), Color.rgb(48, 98, 48), Color.rgb(15, 56, 15)),
                        Tetromino.L to OpaqueColorRamp(Color.rgb(139, 172, 15), Color.rgb(48, 98, 48), Color.rgb(15, 56, 15)),
                        Tetromino.S to OpaqueColorRamp(Color.rgb(87, 126, 51), Color.rgb(155, 188, 15), Color.rgb(15, 56, 15)),
                        Tetromino.Z to OpaqueColorRamp(Color.rgb(15, 56, 15), Color.rgb(48, 98, 48), Color.rgb(15, 56, 15)),
                    ),
                )
            }
        }
    }
}
