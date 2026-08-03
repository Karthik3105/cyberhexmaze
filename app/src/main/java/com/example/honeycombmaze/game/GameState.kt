package com.example.honeycombmaze.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.max
import kotlin.math.min

enum class GameMode(val id: Int, val title: String) {
    CLASSIC(0, "CLASSIC"),
    CHASERS(1, "CHASERS MODE"),
    TRAPS(2, "TRAPS MODE"),
    LAVA_FLOOR(3, "LAVA FLOOR"),
    DARKNESS(4, "DARKNESS MODE"),
    ICE_SLIDE(5, "ICE SLIDE"),
    TIME_RUSH(6, "TIME RUSH")
}

class GameState {
    var level by mutableStateOf(1)
    var radius by mutableStateOf(3)
    var grid by mutableStateOf<Map<HexCoord, Cell>>(emptyMap())
    var playerPos by mutableStateOf(HexCoord(0, 0))
    var goalPos by mutableStateOf(HexCoord(0, 0))
    var isPaused by mutableStateOf(false)
    var isWon by mutableStateOf(false)
    var isGameOver by mutableStateOf(false)
    var enemies by mutableStateOf<List<HexCoord>>(emptyList())
    var traps by mutableStateOf<List<HexCoord>>(emptyList())
    var lavaTiles by mutableStateOf<Set<HexCoord>>(emptySet())
    var gameMode by mutableStateOf(GameMode.CLASSIC)
    
    var moves by mutableStateOf(0)
    var timeSeconds by mutableStateOf(0)
    var bestMoves by mutableStateOf(-1)
    
    var timeRemaining by mutableStateOf(0)
    var timeBonusOrbs by mutableStateOf<Set<HexCoord>>(emptySet())

    private val mazeGenerator = MazeGenerator()

    init {
        startNewGame()
    }

    fun startNewGame() {
        // Radius scales slowly: L1-2: r=2, L3-5: r=3, L6-8: r=4, L9-11: r=5... maxes out around 18 for L50
        radius = 2 + ((level - 1) / 3) 
        grid = mazeGenerator.generateMaze(radius, gameMode)
        
        // Pick any random cell for the start position
        val allCells = grid.keys.toList()
        playerPos = allCells.randomOrNull() ?: HexCoord(0, 0)
        
        // Target distance starts small for early levels, and caps at a reasonable distance for the maze size
        val targetDistance = min(level, (radius * 1.5).toInt())
        
        // Filter cells that meet the target distance requirement
        val possibleGoals = allCells.filter { it != playerPos && hexDistance(it, playerPos) >= targetDistance }
        if (gameMode == GameMode.ICE_SLIDE) {
            var attempts = 0
            var validGoalFound = false
            while (attempts < 15 && !validGoalFound) {
                if (attempts > 0) {
                    grid = mazeGenerator.generateMaze(radius, gameMode)
                }
                val validIceGoals = allCells.filter { cell ->
                    cell != playerPos && hexDistance(cell, playerPos) >= targetDistance && hasIceSlidePath(playerPos, cell, grid)
                }
                val fallbackIceGoals = allCells.filter { cell ->
                    cell != playerPos && hasIceSlidePath(playerPos, cell, grid)
                }
                val chosen = validIceGoals.randomOrNull() ?: fallbackIceGoals.maxByOrNull { hexDistance(it, playerPos) }
                if (chosen != null) {
                    goalPos = chosen
                    validGoalFound = true
                } else {
                    attempts++
                }
            }
            if (!validGoalFound) {
                goalPos = possibleGoals.randomOrNull() ?: (allCells.filter { it != playerPos }.maxByOrNull { hexDistance(it, playerPos) } ?: HexCoord(0, 0))
            }
        } else {
            goalPos = possibleGoals.randomOrNull() ?: (allCells.filter { it != playerPos }.maxByOrNull { hexDistance(it, playerPos) } ?: HexCoord(0, 0))
        }
        
        // Make the goal an "exit" by knocking down its outer walls
        grid[goalPos]?.let { goalCell ->
            for (i in 0..5) {
                val neighbor = goalCell.coord.getNeighbor(i)
                if (!grid.containsKey(neighbor)) {
                    goalCell.walls[i] = false
                }
            }
        }
        
        // Make the start an "entry" by knocking down its outer walls
        grid[playerPos]?.let { startCell ->
            for (i in 0..5) {
                val neighbor = startCell.coord.getNeighbor(i)
                if (!grid.containsKey(neighbor)) {
                    startCell.walls[i] = false
                }
            }
        }
        
        // Spawn more enemies as the level goes up, starting far from the player
        val edgeCells = allCells.filter { 
            kotlin.math.abs(it.q) == radius || kotlin.math.abs(it.r) == radius || kotlin.math.abs(it.q + it.r) == radius
        }
        val possibleEnemySpawns = edgeCells.filter { 
            it != playerPos && it != goalPos && hexDistance(it, playerPos) >= radius / 2
        }.shuffled()
        
        // Chasers scale up slowly: 1 for L1-4, 2 for L5-9, etc., up to 5 max
        val numEnemies = if (gameMode == GameMode.CHASERS) min((level - 1) / 5 + 1, 5) else 0 
        enemies = possibleEnemySpawns.take(numEnemies)
        
        // Spawn Traps with Guaranteed Path Validation
        if (gameMode == GameMode.TRAPS) {
            val maxTrapsForLevel = if (level <= 10) level / 2 else level
            val targetCount = maxTrapsForLevel.coerceAtLeast(1)
            val candidates = allCells.filter { it != playerPos && it != goalPos }.shuffled()
            val chosenTraps = mutableListOf<HexCoord>()

            for (candidate in candidates) {
                if (chosenTraps.size >= targetCount) break
                val testSet = (chosenTraps + candidate).toSet()
                if (hasPathToGoal(playerPos, goalPos, grid, testSet)) {
                    chosenTraps.add(candidate)
                }
            }
            traps = chosenTraps
        } else {
            traps = emptyList()
        }
        
        // Reset Lava Tiles
        lavaTiles = emptySet()

        // Spawn Time Rush bonuses & set timer
        if (gameMode == GameMode.TIME_RUSH) {
            val initialTime = max(10, 30 - (level / 3))
            timeRemaining = initialTime
            val validTimeCells = grid.keys.filter { it != playerPos && it != goalPos }.shuffled()
            timeBonusOrbs = validTimeCells.take(2).toSet()
        } else {
            timeRemaining = 0
            timeBonusOrbs = emptySet()
        }
        
        isWon = false
        isGameOver = false
        isPaused = false
        moves = 0
        timeSeconds = 0
    }

    private fun hasIceSlidePath(start: HexCoord, goal: HexCoord, gridMap: Map<HexCoord, Cell>): Boolean {
        val queue = java.util.ArrayDeque<HexCoord>()
        val visited = mutableSetOf<HexCoord>()
        queue.add(start)
        visited.add(start)

        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            if (curr == goal) return true

            val currentCell = gridMap[curr] ?: continue
            for (dir in 0..5) {
                if (!currentCell.walls[dir]) {
                    var nextPos = curr
                    var reachedGoal = false
                    while (true) {
                        val candidate = nextPos.getNeighbor(dir)
                        if (gridMap.containsKey(candidate)) {
                            val cellAtNext = gridMap[nextPos]
                            if (cellAtNext != null && !cellAtNext.walls[dir]) {
                                nextPos = candidate
                                if (nextPos == goal) {
                                    reachedGoal = true
                                    break
                                }
                            } else {
                                break
                            }
                        } else {
                            break
                        }
                    }
                    if (reachedGoal) return true
                    if (nextPos !in visited) {
                        visited.add(nextPos)
                        queue.add(nextPos)
                    }
                }
            }
        }
        return false
    }

    private fun hasPathToGoal(start: HexCoord, goal: HexCoord, gridMap: Map<HexCoord, Cell>, trapSet: Set<HexCoord>): Boolean {
        val queue = java.util.ArrayDeque<HexCoord>()
        val visited = mutableSetOf<HexCoord>()
        queue.add(start)
        visited.add(start)

        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            if (curr == goal) return true

            val cell = gridMap[curr] ?: continue
            for (i in 0..5) {
                if (!cell.walls[i]) {
                    val neighbor = curr.getNeighbor(i)
                    if (gridMap.containsKey(neighbor) && !visited.contains(neighbor) && !trapSet.contains(neighbor)) {
                        visited.add(neighbor)
                        queue.add(neighbor)
                    }
                }
            }
        }
        return false
    }

    private fun hexDistance(a: HexCoord, b: HexCoord): Int {
        return (kotlin.math.abs(a.q - b.q) + kotlin.math.abs(a.q + a.r - b.q - b.r) + kotlin.math.abs(a.r - b.r)) / 2
    }

    fun movePlayer(directionIndex: Int) {
        if (isWon || isGameOver || isPaused) return
        
        val currentCell = grid[playerPos] ?: return
        
        // Check if there is a wall in that direction
        if (!currentCell.walls[directionIndex]) {
            var newPos = playerPos
            
            // Loop for Ice Slide
            do {
                val nextPos = newPos.getNeighbor(directionIndex)
                if (grid.containsKey(nextPos)) {
                    val nextCell = grid[newPos]
                    if (nextCell != null && !nextCell.walls[directionIndex]) {
                        newPos = nextPos
                        // In Ice Slide, we continue unless we hit goal, enemy, or trap
                        if (gameMode != GameMode.ICE_SLIDE) break
                        
                        if (newPos == goalPos || enemies.contains(newPos) || traps.contains(newPos)) {
                            break // Stop sliding when hitting an entity
                        }
                    } else {
                        break // Hit a wall
                    }
                } else {
                    break // Out of bounds
                }
            } while(gameMode == GameMode.ICE_SLIDE)
            
            if (newPos != playerPos) {
                val oldPos = playerPos
                playerPos = newPos
                moves++
                com.example.honeycombmaze.game.SoundManager.playMoveSound()
                
                // Lava Floor: turn previous tile into lava
                if (gameMode == GameMode.LAVA_FLOOR) {
                    lavaTiles = lavaTiles + oldPos
                }

                // Check Time Rush bonuses (+5s)
                if (gameMode == GameMode.TIME_RUSH && timeBonusOrbs.contains(playerPos)) {
                    timeRemaining += 5
                    timeBonusOrbs = timeBonusOrbs - playerPos
                    com.example.honeycombmaze.game.SoundManager.playHoneyCollectSound()
                }
                
                if (playerPos == goalPos) {
                    isWon = true
                    com.example.honeycombmaze.game.SoundManager.playWinSound()
                } else if (enemies.contains(playerPos)) {
                    isGameOver = true
                    com.example.honeycombmaze.game.SoundManager.playGameOverSound()
                } else if (gameMode == GameMode.TRAPS && traps.contains(playerPos)) {
                    isGameOver = true
                    com.example.honeycombmaze.game.SoundManager.playTrapSound()
                } else if (gameMode == GameMode.LAVA_FLOOR && lavaTiles.contains(playerPos)) {
                    isGameOver = true
                    com.example.honeycombmaze.game.SoundManager.playTrapSound()
                }
            }
        } else {
            com.example.honeycombmaze.game.SoundManager.playWallHitSound()
        }
    }
    
    fun moveEnemies() {
        if (isWon || isGameOver || isPaused || enemies.isEmpty()) return

        // BFS from player to all cells to find the direction to move
        val queue = mutableListOf(playerPos)
        // Map from a cell to the neighbor that leads closer to the player
        val nextStepMap = mutableMapOf<HexCoord, HexCoord>()
        val visited = mutableSetOf(playerPos)
        
        while(queue.isNotEmpty()) {
            val curr = queue.removeAt(0)
            val cell = grid[curr] ?: continue
            for(i in 0..5) {
                if (!cell.walls[i]) {
                    val neighbor = curr.getNeighbor(i)
                    if (neighbor !in visited && grid.containsKey(neighbor)) {
                        visited.add(neighbor)
                        nextStepMap[neighbor] = curr
                        queue.add(neighbor)
                    }
                }
            }
        }
        
        // Move each enemy
        enemies = enemies.map { enemy ->
            val next = nextStepMap[enemy]
            if (next != null) {
                if (next == playerPos) {
                    isGameOver = true // Player got caught, trigger game over
                    com.example.honeycombmaze.game.SoundManager.playGameOverSound()
                }
                next
            } else {
                enemy
            }
        }
    }
    
    fun nextLevel() {
        if (level < 100) {
            level++
            startNewGame()
        }
    }
}
