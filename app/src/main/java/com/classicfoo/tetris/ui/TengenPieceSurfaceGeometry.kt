package com.classicfoo.tetris.ui

/** The two directional shades used by one continuous Tengen bevel band. */
enum class TengenSurfaceShade {
    HIGHLIGHT,
    SHADOW,
}

/** A continuous bevel band generated from a run of the tetromino outline. */
data class TengenSurfaceBand(
    val shade: TengenSurfaceShade,
    val polygon: PixelPolygon,
)

/**
 * The complete surface geometry for one tetromino.  The bevel bands are
 * larger outline regions, rather than one polygon per cell edge.
 */
data class TengenPieceSurface(
    val silhouetteRects: List<PixelRect>,
    val faceRects: List<PixelRect>,
    val bands: List<TengenSurfaceBand>,
)

/**
 * Builds Tengen geometry from the whole occupied tetromino silhouette.
 *
 * The cell grid is used only to discover the outer boundary. Consecutive
 * top/left edges become one highlight polygon and consecutive bottom/right
 * edges become one shadow polygon. This is what keeps S and T notches from
 * being rendered as two bevel fragments that merely touch at a corner.
 */
object TengenPieceSurfaceGeometry {
    fun fromCells(
        cells: Iterable<TengenGridCell>,
        gutterPx: Int = 1,
    ): TengenPieceSurface {
        val entries = cells.toList()
        require(entries.isNotEmpty()) { "A Tengen surface needs at least one cell" }
        val parts = TengenBevelGeometry.fromCells(entries, gutterPx)
        val bevel = parts.minOfOrNull { it.bevelPx } ?: 0
        val bands = if (bevel > 0) {
            traceBoundary(entries)
                .flatMap { loop -> shadeRuns(loop).map { run -> makeBand(run, gutterPx, bevel) } }
        } else {
            emptyList()
        }
        return TengenPieceSurface(
            silhouetteRects = entries.map { it.bounds },
            faceRects = parts.map { it.face },
            bands = bands,
        )
    }

    private data class BoundaryEdge(
        val start: PixelPoint,
        val end: PixelPoint,
        val shade: TengenSurfaceShade,
    )

    private fun traceBoundary(cells: List<TengenGridCell>): List<List<BoundaryEdge>> {
        val occupied = cells.associateBy { it.coordinate }
        val edges = buildList {
            cells.forEach { cell ->
                val point = cell.coordinate
                val left = cell.bounds.left
                val top = cell.bounds.top
                val right = cell.bounds.right
                val bottom = cell.bounds.bottom
                if (PixelPoint(point.x, point.y - 1) !in occupied) {
                    add(BoundaryEdge(PixelPoint(left, top), PixelPoint(right, top), TengenSurfaceShade.HIGHLIGHT))
                }
                if (PixelPoint(point.x + 1, point.y) !in occupied) {
                    add(BoundaryEdge(PixelPoint(right, top), PixelPoint(right, bottom), TengenSurfaceShade.SHADOW))
                }
                if (PixelPoint(point.x, point.y + 1) !in occupied) {
                    add(BoundaryEdge(PixelPoint(right, bottom), PixelPoint(left, bottom), TengenSurfaceShade.SHADOW))
                }
                if (PixelPoint(point.x - 1, point.y) !in occupied) {
                    add(BoundaryEdge(PixelPoint(left, bottom), PixelPoint(left, top), TengenSurfaceShade.HIGHLIGHT))
                }
            }
        }
        val remaining = edges.toMutableSet()
        val loops = mutableListOf<List<BoundaryEdge>>()
        while (remaining.isNotEmpty()) {
            val first = remaining.minWithOrNull(compareBy<BoundaryEdge> { it.start.y }.thenBy { it.start.x }) ?: break
            val loop = mutableListOf<BoundaryEdge>()
            var current = first.start
            while (true) {
                val next = remaining
                    .filter { it.start == current }
                    .minWithOrNull(compareBy<BoundaryEdge> { it.end.y }.thenBy { it.end.x })
                    ?: break
                remaining.remove(next)
                loop += next
                current = next.end
                if (current == first.start) break
            }
            if (loop.isNotEmpty()) loops += loop
        }
        return loops
    }

    private fun shadeRuns(loop: List<BoundaryEdge>): List<List<BoundaryEdge>> {
        if (loop.isEmpty()) return emptyList()
        val transition = loop.indices.firstOrNull { index ->
            loop[index].shade != loop[(index - 1 + loop.size) % loop.size].shade
        } ?: 0
        val runs = mutableListOf<List<BoundaryEdge>>()
        var current = mutableListOf<BoundaryEdge>()
        var currentShade: TengenSurfaceShade? = null
        repeat(loop.size) { step ->
            val edge = loop[(transition + step) % loop.size]
            if (currentShade != null && edge.shade != currentShade) {
                runs += current
                current = mutableListOf()
            }
            currentShade = edge.shade
            current += edge
        }
        if (current.isNotEmpty()) runs += current
        return runs
    }

    private fun makeBand(
        run: List<BoundaryEdge>,
        gutter: Int,
        bevel: Int,
    ): TengenSurfaceBand {
        val outer = offsetRun(run, gutter)
        val inner = offsetRun(run, gutter + bevel)
        val points = compact(outer + inner.asReversed())
        return TengenSurfaceBand(
            shade = run.first().shade,
            polygon = PixelPolygon(points),
        )
    }

    private fun offsetRun(run: List<BoundaryEdge>, distance: Int): List<PixelPoint> {
        val points = mutableListOf(offsetPoint(run.first(), distance, atStart = true))
        run.drop(1).forEachIndexed { index, edge ->
            points += offsetJoin(run[index], edge, distance)
        }
        points += offsetPoint(run.last(), distance, atStart = false)
        return points
    }

    private fun offsetPoint(edge: BoundaryEdge, distance: Int, atStart: Boolean): PixelPoint {
        val point = if (atStart) edge.start else edge.end
        return if (edge.start.y == edge.end.y) {
            val inward = if (edge.end.x > edge.start.x) distance else -distance
            point.copy(y = point.y + inward)
        } else {
            val inward = if (edge.end.y > edge.start.y) -distance else distance
            point.copy(x = point.x + inward)
        }
    }

    private fun offsetJoin(previous: BoundaryEdge, next: BoundaryEdge, distance: Int): PixelPoint {
        val previousPoint = offsetPoint(previous, distance, atStart = false)
        val nextPoint = offsetPoint(next, distance, atStart = true)
        val previousHorizontal = previous.start.y == previous.end.y
        val nextHorizontal = next.start.y == next.end.y
        return when {
            previousHorizontal && !nextHorizontal -> PixelPoint(nextPoint.x, previousPoint.y)
            !previousHorizontal && nextHorizontal -> PixelPoint(previousPoint.x, nextPoint.y)
            else -> previousPoint
        }
    }

    private fun compact(points: List<PixelPoint>): List<PixelPoint> {
        val result = mutableListOf<PixelPoint>()
        points.forEach { point ->
            if (result.lastOrNull() != point) result += point
        }
        if (result.size > 1 && result.first() == result.last()) result.removeAt(result.lastIndex)
        return result
    }
}
