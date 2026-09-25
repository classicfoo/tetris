package com.classicfoo.tetris.engine

internal object PieceDefinitions {
    private val spawnCells = mapOf(
        Tetromino.I to listOf(Cell(0, 1), Cell(1, 1), Cell(2, 1), Cell(3, 1)),
        Tetromino.O to listOf(Cell(1, 0), Cell(2, 0), Cell(1, 1), Cell(2, 1)),
        Tetromino.T to listOf(Cell(1, 0), Cell(0, 1), Cell(1, 1), Cell(2, 1)),
        Tetromino.J to listOf(Cell(0, 0), Cell(0, 1), Cell(1, 1), Cell(2, 1)),
        Tetromino.L to listOf(Cell(2, 0), Cell(0, 1), Cell(1, 1), Cell(2, 1)),
        Tetromino.S to listOf(Cell(1, 0), Cell(2, 0), Cell(0, 1), Cell(1, 1)),
        Tetromino.Z to listOf(Cell(0, 0), Cell(1, 0), Cell(1, 1), Cell(2, 1)),
    )

    fun cells(type: Tetromino, rotation: Rotation): List<Cell> {
        if (type == Tetromino.O) return spawnCells.getValue(type)

        var result = spawnCells.getValue(type)
        val dimension = if (type == Tetromino.I) 4 else 3
        repeat(rotation.ordinal) {
            result = result.map { cell ->
                Cell(dimension - 1 - cell.y, cell.x)
            }
        }
        return result
    }

    fun width(type: Tetromino): Int = if (type == Tetromino.I) 4 else 3
}

internal object BoardRules {
    fun emptyBoard(): List<List<Tetromino?>> = List(BOARD_HEIGHT) {
        List<Tetromino?>(BOARD_WIDTH) { null }
    }

    fun canPlace(board: List<List<Tetromino?>>, piece: ActivePiece): Boolean {
        return PieceDefinitions.cells(piece.type, piece.rotation).all { cell ->
            val x = piece.x + cell.x
            val y = piece.y + cell.y
            x in 0 until BOARD_WIDTH &&
                y < BOARD_HEIGHT &&
                (y < 0 || board[y][x] == null)
        }
    }

    fun ghostY(board: List<List<Tetromino?>>, piece: ActivePiece): Int {
        var y = piece.y
        while (canPlace(board, piece.copy(y = y + 1))) y++
        return y
    }

    fun lock(board: List<List<Tetromino?>>, piece: ActivePiece): List<List<Tetromino?>> {
        val result = board.map { it.toMutableList() }.toMutableList()
        for (cell in PieceDefinitions.cells(piece.type, piece.rotation)) {
            val x = piece.x + cell.x
            val y = piece.y + cell.y
            if (x in 0 until BOARD_WIDTH && y in 0 until BOARD_HEIGHT) {
                result[y][x] = piece.type
            }
        }
        return result.map { it.toList() }
    }

    data class LineClearResult(
        val board: List<List<Tetromino?>>,
        val count: Int,
        val clearedRows: List<Int>,
    )

    fun clearLinesWithRows(board: List<List<Tetromino?>>): LineClearResult {
        val clearedRows = board.mapIndexedNotNull { index, row ->
            index.takeIf { row.all { it != null } }
        }
        val remaining = board.filterIndexed { index, _ -> index !in clearedRows }
        val cleared = clearedRows.size
        val result = MutableList(BOARD_HEIGHT) { List<Tetromino?>(BOARD_WIDTH) { null } }
        remaining.forEachIndexed { index, row ->
            result[BOARD_HEIGHT - remaining.size + index] = row
        }
        return LineClearResult(
            board = result,
            count = cleared,
            clearedRows = clearedRows,
        )
    }

    /** Compatibility wrapper for callers that only need the resulting board and count. */
    fun clearLines(board: List<List<Tetromino?>>): Pair<List<List<Tetromino?>>, Int> {
        val result = clearLinesWithRows(board)
        return result.board to result.count
    }

    fun isEmpty(board: List<List<Tetromino?>>): Boolean = board.all { row -> row.all { it == null } }
}

internal class PieceBag(private val random: java.util.Random) {
    private val queue = java.util.ArrayDeque<Tetromino>()

    fun next(): Tetromino {
        if (queue.isEmpty()) refill()
        return queue.removeFirst()
    }

    private fun refill() {
        val bag = Tetromino.entries.toMutableList()
        java.util.Collections.shuffle(bag, random)
        queue.addAll(bag)
    }
}
