package com.example.honeycombmaze.game

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Axial coordinates for hexagon
data class HexCoord(val q: Int, val r: Int) {
    operator fun plus(other: HexCoord) = HexCoord(q + other.q, r + other.r)
    operator fun minus(other: HexCoord) = HexCoord(q - other.q, r - other.r)

    fun neighbors(): List<HexCoord> {
        return directions.map { this + it }
    }
    
    fun getNeighbor(dirIndex: Int): HexCoord {
        return this + directions[dirIndex]
    }

    companion object {
        val directions = listOf(
            HexCoord(1, 0), HexCoord(1, -1), HexCoord(0, -1),
            HexCoord(-1, 0), HexCoord(-1, 1), HexCoord(0, 1)
        )
    }
}

// 2D Point
data class Point(val x: Float, val y: Float)

// Layout for converting hex to pixel
data class HexLayout(val size: Float, val origin: Point) {
    // For pointy-topped hexagons
    fun hexToPixel(h: HexCoord): Point {
        val x = size * sqrt(3.0f) * (h.q + h.r / 2.0f)
        val y = size * 3.0f / 2.0f * h.r
        return Point(x + origin.x, y + origin.y)
    }

    fun polygonCorners(h: HexCoord): List<Point> {
        val center = hexToPixel(h)
        return (0..5).map { i ->
            val angleDeg = -60 * i + 30 // Map wall i to direction i correctly
            val angleRad = Math.PI / 180 * angleDeg
            Point(
                (center.x + size * cos(angleRad)).toFloat(),
                (center.y + size * sin(angleRad)).toFloat()
            )
        }
    }
}
