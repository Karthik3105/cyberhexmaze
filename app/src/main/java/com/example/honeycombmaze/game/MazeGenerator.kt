package com.example.honeycombmaze.game

import kotlin.math.abs
import kotlin.math.max

data class Cell(
    val coord: HexCoord,
    // True if there's a wall in that direction (0 to 5)
    // Indexes match HexCoord.directions
    val walls: BooleanArray = BooleanArray(6) { true },
    var visited: Boolean = false
)

class MazeGenerator {

    // Generates a rectangular hexagonal grid with given columns and rows
    fun generateGrid(cols: Int, rows: Int): Map<HexCoord, Cell> {
        val cells = mutableMapOf<HexCoord, Cell>()
        val halfW = cols / 2
        val halfH = rows / 2
        for (r in -halfH..halfH) {
            val qOffset = r shr 1
            for (col in -halfW..halfW) {
                val q = col - qOffset
                val coord = HexCoord(q, r)
                cells[coord] = Cell(coord)
            }
        }
        return cells
    }

    // Legacy radius helper
    fun generateGrid(radius: Int): Map<HexCoord, Cell> {
        val cols = radius * 2 + 1
        val rows = ((cols * 1.4f).toInt()) or 1
        return generateGrid(cols, rows)
    }

    // Generates a rectangular maze using randomized DFS
    fun generateMaze(cols: Int, rows: Int, gameMode: GameMode = GameMode.CLASSIC): Map<HexCoord, Cell> {
        val grid = generateGrid(cols, rows)
        if (grid.isEmpty()) return grid

        val startCoord = grid.keys.firstOrNull() ?: HexCoord(0, 0)
        val stack = mutableListOf<HexCoord>()
        
        grid[startCoord]?.visited = true
        stack.add(startCoord)

        while (stack.isNotEmpty()) {
            val currentCoord = stack.last()
            
            val unvisitedNeighbors = mutableListOf<Pair<Int, HexCoord>>()
            for (i in 0 until 6) {
                val neighborCoord = currentCoord.getNeighbor(i)
                val neighborCell = grid[neighborCoord]
                if (neighborCell != null && !neighborCell.visited) {
                    unvisitedNeighbors.add(Pair(i, neighborCoord))
                }
            }

            if (unvisitedNeighbors.isNotEmpty()) {
                val (dir, nextCoord) = unvisitedNeighbors.random()
                
                grid[currentCoord]!!.walls[dir] = false
                val oppositeDir = (dir + 3) % 6
                grid[nextCoord]!!.walls[oppositeDir] = false
                
                grid[nextCoord]!!.visited = true
                stack.add(nextCoord)
            } else {
                stack.removeAt(stack.size - 1)
            }
        }
        
        val braidChance = when (gameMode) {
            GameMode.CLASSIC, GameMode.LAVA_FLOOR -> 0.0
            else -> 0.15
        }

        if (braidChance > 0) {
            grid.values.forEach { cell ->
                for (i in 0..5) {
                    if (cell.walls[i]) {
                        if (Math.random() < braidChance) {
                            val neighborCoord = cell.coord.getNeighbor(i)
                            if (grid.containsKey(neighborCoord)) {
                                cell.walls[i] = false
                                grid[neighborCoord]!!.walls[(i + 3) % 6] = false
                            }
                        }
                    }
                }
            }
        }

        return grid
    }

    // Legacy radius overload
    fun generateMaze(radius: Int, gameMode: GameMode = GameMode.CLASSIC): Map<HexCoord, Cell> {
        val cols = radius * 2 + 1
        val rows = ((cols * 1.4f).toInt()) or 1
        return generateMaze(cols, rows, gameMode)
    }
}
