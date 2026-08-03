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

    // Generates a hexagonal grid within a given radius
    fun generateGrid(radius: Int): Map<HexCoord, Cell> {
        val cells = mutableMapOf<HexCoord, Cell>()
        for (q in -radius..radius) {
            val r1 = max(-radius, -q - radius)
            val r2 = kotlin.math.min(radius, -q + radius)
            for (r in r1..r2) {
                val coord = HexCoord(q, r)
                cells[coord] = Cell(coord)
            }
        }
        return cells
    }

    // Generates a maze using randomized DFS
    fun generateMaze(radius: Int, gameMode: GameMode = GameMode.CLASSIC): Map<HexCoord, Cell> {
        val grid = generateGrid(radius)
        if (grid.isEmpty()) return grid

        // Start from center
        val startCoord = HexCoord(0, 0)
        val stack = mutableListOf<HexCoord>()
        
        grid[startCoord]?.visited = true
        stack.add(startCoord)

        while (stack.isNotEmpty()) {
            val currentCoord = stack.last()
            
            // Find unvisited neighbors in the grid
            val unvisitedNeighbors = mutableListOf<Pair<Int, HexCoord>>()
            for (i in 0 until 6) {
                val neighborCoord = currentCoord.getNeighbor(i)
                val neighborCell = grid[neighborCoord]
                if (neighborCell != null && !neighborCell.visited) {
                    unvisitedNeighbors.add(Pair(i, neighborCoord))
                }
            }

            if (unvisitedNeighbors.isNotEmpty()) {
                // Choose a random neighbor
                val (dir, nextCoord) = unvisitedNeighbors.random()
                
                // Remove walls between current and chosen neighbor
                grid[currentCoord]!!.walls[dir] = false
                val oppositeDir = (dir + 3) % 6
                grid[nextCoord]!!.walls[oppositeDir] = false
                
                grid[nextCoord]!!.visited = true
                stack.add(nextCoord)
            } else {
                stack.removeAt(stack.size - 1)
            }
        }
        
        // Add loops by randomly knocking down some remaining internal walls.
        // This makes the maze "braided", giving the player multiple paths to escape enemies.
        // For CLASSIC and LAVA_FLOOR modes, we want a single path maze with 0.0 braid chance.
        val braidChance = when (gameMode) {
            GameMode.CLASSIC, GameMode.LAVA_FLOOR -> 0.0
            else -> 0.15
        }

        if (braidChance > 0) {
            grid.values.forEach { cell ->
                for (i in 0..5) {
                    if (cell.walls[i]) {
                        // Chance to break an internal wall
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
}
