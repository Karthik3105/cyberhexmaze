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
    TIME_RUSH(6, "TIME RUSH"),
    DUAL_SYNC(7, "DUAL SYNC"),
    LASER_SENTINELS(8, "LASER SENTINELS"),
    CIRCUIT_GATES(9, "CIRCUIT GATES"),
    STEALTH_PATROL(10, "STEALTH PATROL")
}

// Mode 8: Laser Sentinels
data class LaserSentinel(val pos: HexCoord, var angleDir: Int)

// Mode 9: Circuit Gates
enum class CircuitColor { RED, BLUE, GREEN }
data class CircuitGate(val fromCoord: HexCoord, val wallIndex: Int, val color: CircuitColor)
data class CircuitSwitch(val pos: HexCoord, val color: CircuitColor)

// Mode 10: Stealth Patrol
data class PatrolDrone(
    val id: Int,
    var pos: HexCoord,
    var facingDir: Int,
    val patrolNodes: List<HexCoord>,
    var patrolIndex: Int,
    var movingForward: Boolean = true,
    val isGoalGuardian: Boolean = false
)

fun getLevelCoinReward(level: Int): Int {
    return when (level) {
        in 1..20 -> 1
        in 21..40 -> 2
        in 41..60 -> 3
        in 61..80 -> 4
        else -> 5
    }
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

    // --- Mode 7: Dual Sync ---
    var clonePos by mutableStateOf(HexCoord(0, 0))
    var cloneGoalPos by mutableStateOf(HexCoord(0, 0))

    // --- Mode 8: Laser Sentinels ---
    var sentinels by mutableStateOf<List<LaserSentinel>>(emptyList())
    var activeLaserBeams by mutableStateOf<Set<HexCoord>>(emptySet())

    // --- Mode 9: Circuit Gates ---
    var circuitGates by mutableStateOf<List<CircuitGate>>(emptyList())
    var circuitSwitches by mutableStateOf<List<CircuitSwitch>>(emptyList())
    var activeCircuitColor by mutableStateOf(CircuitColor.RED)

    // --- Mode 10: Stealth Patrol ---
    var patrolDrones by mutableStateOf<List<PatrolDrone>>(emptyList())
    var droneVisionTiles by mutableStateOf<Set<HexCoord>>(emptySet())

    var gridCols by mutableStateOf(5)
    var gridRows by mutableStateOf(7)

    private val mazeGenerator = MazeGenerator()

    init {
        startNewGame()
    }

    companion object {
        fun getLevelSeed(gameMode: GameMode, level: Int): Long {
            if (gameMode == GameMode.STEALTH_PATROL && level == 78) {
                return (gameMode.id.toLong() * 1000033L) + (level.toLong() * 999983L) + 7919L + 100L
            }
            if (gameMode == GameMode.DUAL_SYNC && (level == 3 || level == 8 || level == 16 || level == 52)) {
                return (gameMode.id.toLong() * 1000033L) + (level.toLong() * 999983L) + 7919L + 1L
            }
            return (gameMode.id.toLong() * 1000033L) + (level.toLong() * 999983L) + 7919L
        }
    }

    fun computeLaserBeams(): Set<HexCoord> {
        val beams = mutableSetOf<HexCoord>()
        for (s in sentinels) {
            var curr = s.pos
            val dir = s.angleDir
            while (true) {
                val cell = grid[curr] ?: break
                if (cell.walls[dir]) break
                val nxt = curr.getNeighbor(dir)
                if (!grid.containsKey(nxt)) break
                beams.add(nxt)
                curr = nxt
            }
        }
        return beams
    }

    fun computeDroneVision(): Set<HexCoord> {
        val vision = mutableSetOf<HexCoord>()
        for (d in patrolDrones) {
            val fDir = d.facingDir
            val dirsToCheck = listOf((fDir + 5) % 6, fDir, (fDir + 1) % 6)
            for (dir in dirsToCheck) {
                var curr = d.pos
                for (step in 1..2) {
                    val cell = grid[curr] ?: break
                    if (cell.walls[dir]) break
                    val nxt = curr.getNeighbor(dir)
                    if (!grid.containsKey(nxt)) break
                    vision.add(nxt)
                    curr = nxt
                }
            }
        }
        return vision
    }

    fun startNewGame() {
        val rng = kotlin.random.Random(getLevelSeed(gameMode, level))

        val colsBase = when (gameMode) {
            GameMode.ICE_SLIDE -> when {
                level < 8 -> 5
                level < 15 -> 7
                level < 30 -> 9
                level < 56 -> 11
                level < 80 -> 13
                else -> 15
            }
            GameMode.DUAL_SYNC,
            GameMode.LASER_SENTINELS,
            GameMode.CIRCUIT_GATES,
            GameMode.STEALTH_PATROL -> when {
                level < 10 -> 7
                level < 25 -> 9
                level < 50 -> 11
                level < 75 -> 13
                else -> 15
            }
            else -> 5 + ((level - 1) * 16 / 99)
        }
        gridCols = if (colsBase % 2 == 0) colsBase + 1 else colsBase
        val rowsBase = ((gridCols * 1.38f).toInt())
        gridRows = if (rowsBase % 2 == 0) rowsBase + 1 else rowsBase
        radius = gridCols / 2

        grid = mazeGenerator.generateMaze(gridCols, gridRows, gameMode, rng)
        
        // Pick start position deterministically
        val allCells = grid.keys.sortedWith(compareBy({ it.r }, { it.q }))
        playerPos = allCells.randomOrNull(rng) ?: HexCoord(0, 0)
        
        // Adjust start position for Stealth Patrol 46 and 48 without affecting RNG
        if (gameMode == GameMode.STEALTH_PATROL && (level == 46 || level == 48)) {
            val currentIndex = allCells.indexOf(playerPos)
            if (currentIndex != -1) {
                playerPos = allCells[(currentIndex + allCells.size / 2) % allCells.size]
            }
        }
        
        // Target distance starts small for early levels, and caps at a reasonable distance for the maze size
        val targetDistance = min(level, (gridCols * 1.2).toInt())
        
        // Filter cells that meet the target distance requirement
        val possibleGoals = allCells.filter { it != playerPos && hexDistance(it, playerPos) >= targetDistance }
        if (gameMode == GameMode.ICE_SLIDE) {
            val isBorder = { coord: HexCoord -> (0..5).any { dir -> !grid.containsKey(coord.getNeighbor(dir)) } }

            val targetMinSlides = when {
                level < 5 -> 2
                level < 15 -> 3
                level < 30 -> 4
                level < 56 -> 5
                level < 80 -> 6
                else -> 7
            }

            var attempts = 0
            var validGoalFound = false
            while (attempts < 25 && !validGoalFound) {
                if (attempts > 0) {
                    grid = mazeGenerator.generateMaze(gridCols, gridRows, gameMode, rng)
                }
                val reachableMap = getReachableIceSlideGoals(playerPos, grid)

                var qualified = emptyList<HexCoord>()
                for (desiredSlides in targetMinSlides downTo 2) {
                    val matching = reachableMap.filter { (coord, slides) ->
                        coord != playerPos && slides >= desiredSlides
                    }.keys.toList()
                    if (matching.isNotEmpty()) {
                        qualified = matching
                        break
                    }
                }
                if (qualified.isEmpty()) {
                    qualified = reachableMap.filter { it.key != playerPos }.keys.toList()
                }

                if (qualified.isNotEmpty()) {
                    val innerGoals = qualified.filter { !isBorder(it) }
                    val borderGoals = qualified.filter { isBorder(it) }

                    val chosen = if (innerGoals.isNotEmpty() && borderGoals.isNotEmpty()) {
                        if (rng.nextBoolean()) innerGoals.random(rng) else borderGoals.random(rng)
                    } else if (innerGoals.isNotEmpty()) {
                        innerGoals.random(rng)
                    } else {
                        borderGoals.random(rng)
                    }
                    goalPos = chosen
                    validGoalFound = true
                } else {
                    attempts++
                }
            }
            if (!validGoalFound) {
                goalPos = possibleGoals.randomOrNull(rng) ?: (allCells.filter { it != playerPos }.maxByOrNull { hexDistance(it, playerPos) } ?: HexCoord(0, 0))
            }
        } else {
            goalPos = possibleGoals.randomOrNull(rng) ?: (allCells.filter { it != playerPos }.maxByOrNull { hexDistance(it, playerPos) } ?: HexCoord(0, 0))
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
        val isBorderCell = { coord: HexCoord -> (0..5).any { dir -> !grid.containsKey(coord.getNeighbor(dir)) } }
        val edgeCells = allCells.filter(isBorderCell)
        val possibleEnemySpawns = (edgeCells.filter { 
            it != playerPos && it != goalPos && hexDistance(it, playerPos) >= gridCols / 2
        } + allCells.filter {
            it != playerPos && it != goalPos && hexDistance(it, playerPos) >= gridCols / 2
        }).distinct().shuffled(rng)
        
        val numEnemies = if (gameMode == GameMode.CHASERS) {
            when {
                level <= 5 -> 1  // 1 to 5: 1 chaser
                level <= 13 -> 2 // 6 to 13: 2 chasers
                level <= 22 -> 3 // 14 to 22: 3 chasers
                level <= 30 -> 4 // 23 to 30: 4 chasers
                level <= 40 -> 5 // 31 to 40: 5 chasers
                level <= 50 -> 6 // 41 to 50: 6 chasers
                level <= 60 -> 7 // 51 to 60: 7 chasers
                level <= 70 -> 8 // 61 to 70: 8 chasers
                level <= 80 -> 9 // 71 to 80: 9 chasers
                level <= 90 -> 10 // 81 to 90: 10 chasers
                else -> 11       // 91 to 100: 11 chasers
            }
        } else 0 
        enemies = possibleEnemySpawns.take(numEnemies)
        
        // Spawn Traps with Guaranteed Path Validation
        if (gameMode == GameMode.TRAPS) {
            val maxTrapsForLevel = if (level <= 10) level / 2 else level
            val targetCount = maxTrapsForLevel.coerceAtLeast(1)
            val candidates = allCells.filter { it != playerPos && it != goalPos }.shuffled(rng)
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

        // Reset Time Rush
        if (gameMode == GameMode.TIME_RUSH) {
            val initialTime = when {
                level <= 10 -> 5
                level <= 50 -> 10
                level < 85 -> 15
                else -> 20
            }
            timeRemaining = initialTime
            val validTimeCells = grid.keys.filter { it != playerPos && it != goalPos }.shuffled(rng)
            timeBonusOrbs = validTimeCells.take(2).toSet()
        } else {
            timeRemaining = 0
            timeBonusOrbs = emptySet()
        }

        // Mode 7: Dual Sync Setup
        if (gameMode == GameMode.DUAL_SYNC) {
            val candidatesForClone = allCells.filter { it != playerPos && it != goalPos && hexDistance(it, playerPos) >= 3 }.shuffled(rng)
            clonePos = candidatesForClone.firstOrNull() ?: allCells.first { it != playerPos }
            val candidatesForCloneGoal = allCells.filter { it != playerPos && it != goalPos && it != clonePos && hexDistance(it, clonePos) >= 3 }.shuffled(rng)
            cloneGoalPos = candidatesForCloneGoal.firstOrNull() ?: allCells.last { it != goalPos }
        }

        // Mode 8: Laser Sentinels Setup
        if (gameMode == GameMode.LASER_SENTINELS) {
            val sentinelCount = when {
                level <= 10 -> 1
                level <= 30 -> 2
                level <= 65 -> 3
                else -> 4
            }
            // Pick cells that are open corridors and not player/goal
            val openIntersections = allCells.filter { coord ->
                coord != playerPos && coord != goalPos && hexDistance(coord, playerPos) >= 2 &&
                (grid[coord]?.walls?.count { !it } ?: 0) >= 2
            }.shuffled(rng)
            
            sentinels = openIntersections.take(sentinelCount).map { LaserSentinel(it, rng.nextInt(6)) }
            activeLaserBeams = computeLaserBeams()
        } else {
            sentinels = emptyList()
            activeLaserBeams = emptySet()
        }

        // Mode 9: Circuit Gates Setup - Max 2 Colors (Red & Blue), Max 1 Gate Per Color, Zero Direct Bypass, Tricky Puzzle Depth
        if (gameMode == GameMode.CIRCUIT_GATES) {
            activeCircuitColor = CircuitColor.RED

            // 1. Find the main path from start to goal
            val mainPathEdges = findDirectPathEdges(playerPos, goalPos, grid, emptyList(), CircuitColor.RED)
            val pathLen = mainPathEdges.size

            val chosenGates = mutableListOf<CircuitGate>()
            val switches = mutableListOf<CircuitSwitch>()
            var puzzleConstructed = false

            // For Level >= 5 and when main path is long enough (>= 6 edges):
            // Construct the Tricky 2-Gate Relay:
            // Sector 1 [Start & Blue Switch] -> (Blue Gate) -> Sector 2 [Red Switch] -> (Red Gate) -> Sector 3 [Goal]
            // At start (RED): Blue Gate is CLOSED -> Cannot reach Goal.
            // When BLUE active: Blue Gate is OPEN, but Red Gate is CLOSED -> Cannot reach Goal.
            // Player must find Blue Switch in Sector 1, cross Blue Gate, find Red Switch in Sector 2, cross Red Gate to win!
            if (level >= 5 && pathLen >= 6) {
                val blueGateIdx = (pathLen / 3).coerceIn(1, pathLen - 3)
                val redGateIdx = (pathLen * 2 / 3).coerceIn(blueGateIdx + 1, pathLen - 1)

                val blueEdge = mainPathEdges[blueGateIdx]
                val redEdge = mainPathEdges[redGateIdx]

                val testBlueGate = CircuitGate(blueEdge.first, blueEdge.second, CircuitColor.BLUE)
                val testRedGate = CircuitGate(redEdge.first, redEdge.second, CircuitColor.RED)
                val testGates = listOf(testBlueGate, testRedGate)

                // Sector 1 cells (reachable from start with Blue Gate closed)
                val sector1Cells = allCells.filter { coord ->
                    coord != playerPos && coord != goalPos &&
                    canReachGoalDirectly(playerPos, coord, grid, listOf(testBlueGate), CircuitColor.RED)
                }

                // Sector 2 cells (reachable from downstream side of Blue Gate with Red Gate closed)
                val blueGateDownstream = blueEdge.first.getNeighbor(blueEdge.second)
                val sector2Cells = allCells.filter { coord ->
                    coord != playerPos && coord != goalPos && coord !in sector1Cells &&
                    canReachGoalDirectly(blueGateDownstream, coord, grid, listOf(testRedGate), CircuitColor.BLUE)
                }

                // Find deepest dead-ends in Sector 1 for Blue Switch (degree 1 preferred, plus distance)
                val blueSwitchCandidate = sector1Cells.maxByOrNull { c ->
                    val degree = (0..5).count { dir ->
                        val cell = grid[c] ?: return@count false
                        !cell.walls[dir] && grid.containsKey(c.getNeighbor(dir))
                    }
                    (if (degree == 1) 1000 else 0) + hexDistance(c, playerPos)
                }

                // Find deepest dead-ends in Sector 2 for Red Switch
                val redSwitchCandidate = sector2Cells.maxByOrNull { c ->
                    val degree = (0..5).count { dir ->
                        val cell = grid[c] ?: return@count false
                        !cell.walls[dir] && grid.containsKey(c.getNeighbor(dir))
                    }
                    (if (degree == 1) 1000 else 0) + hexDistance(c, redEdge.first)
                }

                if (blueSwitchCandidate != null && redSwitchCandidate != null) {
                    val testSwitches = listOf(
                        CircuitSwitch(blueSwitchCandidate, CircuitColor.BLUE),
                        CircuitSwitch(redSwitchCandidate, CircuitColor.RED)
                    )

                    // Verify zero direct bypass & 100% solvability
                    if (!canReachGoalDirectly(playerPos, goalPos, grid, testGates, CircuitColor.RED) &&
                        !canReachGoalDirectly(playerPos, goalPos, grid, testGates, CircuitColor.BLUE) &&
                        hasCircuitGateSolution(playerPos, goalPos, grid, testGates, testSwitches)) {
                        chosenGates.addAll(testGates)
                        switches.addAll(testSwitches)
                        puzzleConstructed = true
                    }
                }
            }

            // Fallback & Intro Levels (Level 1-4): Single Blue Gate guarding the Goal corridor
            if (!puzzleConstructed) {
                chosenGates.clear()
                switches.clear()
                val gateIdx = (pathLen / 2).coerceIn(1, (pathLen - 1).coerceAtLeast(1))
                val edge = mainPathEdges[gateIdx]
                val blueGate = CircuitGate(edge.first, edge.second, CircuitColor.BLUE)
                chosenGates.add(blueGate)

                val sector1Cells = allCells.filter { coord ->
                    coord != playerPos && coord != goalPos &&
                    canReachGoalDirectly(playerPos, coord, grid, chosenGates, CircuitColor.RED)
                }

                // Place Blue Switch deep in the farthest dead-end of Sector 1
                val bestSwitchPos = sector1Cells.maxByOrNull { c ->
                    val degree = (0..5).count { dir ->
                        val cell = grid[c] ?: return@count false
                        !cell.walls[dir] && grid.containsKey(c.getNeighbor(dir))
                    }
                    (if (degree == 1) 1000 else 0) + hexDistance(c, playerPos)
                } ?: allCells.first { it != playerPos && it != goalPos }

                switches.add(CircuitSwitch(bestSwitchPos, CircuitColor.BLUE))
            }

            circuitGates = chosenGates
            circuitSwitches = switches
        } else {
            circuitGates = emptyList()
            circuitSwitches = emptyList()
        }

        // Mode 10: Stealth Patrol Setup
        // Drone counts: 2 (L1-10), 4 (L11-20), 6 (L21-40), 8 (L41-50), 11 (L51-65), 13 (L66-85), 15 (L86-100)
        // Drones can spawn anywhere throughout the maze corridors (not necessarily near goals)
        if (gameMode == GameMode.STEALTH_PATROL) {
            val totalDroneCount = when {
                level <= 10 -> 2
                level <= 20 -> 4
                level <= 40 -> 6
                level <= 50 -> 8
                level <= 65 -> 11
                level <= 85 -> 13
                else -> 15 // 86 to 100
            }

            val drones = mutableListOf<PatrolDrone>()
            val usedStartPositions = mutableSetOf<HexCoord>()

            val getOpenNeighbors = { coord: HexCoord ->
                val cell = grid[coord]
                if (cell != null) {
                    (0..5).filter { !cell.walls[it] }
                        .map { coord.getNeighbor(it) }
                        .filter { grid.containsKey(it) }
                } else emptyList()
            }

            val candidates = allCells.filter {
                it != playerPos && it != goalPos &&
                hexDistance(it, playerPos) >= 3
            }.shuffled(rng)

            for (candidate in candidates) {
                if (drones.size >= totalDroneCount) break
                if (candidate in usedStartPositions) continue

                val path = mutableListOf(candidate)
                var curr = candidate
                val targetLen = rng.nextInt(2, 5) // 2 to 4 nodes patrol loop
                for (step in 1 until targetLen) {
                    val openNbrs = getOpenNeighbors(curr).filter {
                        it != goalPos && it != playerPos &&
                        it !in path
                    }
                    if (openNbrs.isNotEmpty()) {
                        val nxt = openNbrs.random(rng)
                        path.add(nxt)
                        curr = nxt
                    } else break
                }

                if (path.size < 2) {
                    val fallbackNbr = getOpenNeighbors(candidate).firstOrNull { it != goalPos && it != playerPos }
                    if (fallbackNbr != null) {
                        path.add(fallbackNbr)
                    }
                }

                if (path.size >= 2) {
                    usedStartPositions.addAll(path)
                    val initialFacing = (0..5).firstOrNull { path[0].getNeighbor(it) == path[1] } ?: rng.nextInt(6)
                    drones.add(PatrolDrone(
                        id = drones.size,
                        pos = candidate,
                        facingDir = initialFacing,
                        patrolNodes = path,
                        patrolIndex = 0,
                        movingForward = true,
                        isGoalGuardian = false
                    ))
                }
            }

            patrolDrones = drones
            droneVisionTiles = computeDroneVision()
        } else {
            patrolDrones = emptyList()
            droneVisionTiles = emptySet()
        }
        
        isWon = false
        isGameOver = false
        isPaused = false
        moves = 0
        timeSeconds = 0
    }

    private fun getReachableIceSlideGoals(start: HexCoord, gridMap: Map<HexCoord, Cell>): Map<HexCoord, Int> {
        val minSlidesMap = mutableMapOf<HexCoord, Int>()
        val queue = java.util.ArrayDeque<Pair<HexCoord, Int>>()
        val visitedStopPositions = mutableSetOf<HexCoord>()
        
        queue.add(start to 0)
        visitedStopPositions.add(start)

        while (queue.isNotEmpty()) {
            val (curr, slides) = queue.removeFirst()
            val currentCell = gridMap[curr] ?: continue
            val nextSlides = slides + 1

            for (dir in 0..5) {
                if (!currentCell.walls[dir]) {
                    var slidePos = curr
                    while (true) {
                        val neighbor = slidePos.getNeighbor(dir)
                        if (!gridMap.containsKey(neighbor)) break
                        val cellAtPos = gridMap[slidePos] ?: break
                        if (cellAtPos.walls[dir]) break

                        slidePos = neighbor
                        val existing = minSlidesMap[slidePos]
                        if (existing == null || nextSlides < existing) {
                            minSlidesMap[slidePos] = nextSlides
                        }
                    }
                    if (slidePos !in visitedStopPositions) {
                        visitedStopPositions.add(slidePos)
                        queue.add(slidePos to nextSlides)
                    }
                }
            }
        }
        return minSlidesMap
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

    private fun hasCircuitGateSolution(
        start: HexCoord,
        goal: HexCoord,
        gridMap: Map<HexCoord, Cell>,
        gatesList: List<CircuitGate>,
        switchesList: List<CircuitSwitch>
    ): Boolean {
        val switchMap = switchesList.associateBy { it.pos }
        val queue = java.util.ArrayDeque<Pair<HexCoord, CircuitColor>>()
        val visited = mutableSetOf<Pair<HexCoord, CircuitColor>>()

        val initialState = start to CircuitColor.RED
        queue.add(initialState)
        visited.add(initialState)

        while (queue.isNotEmpty()) {
            val (curr, color) = queue.removeFirst()
            if (curr == goal) return true

            val cell = gridMap[curr] ?: continue
            for (dir in 0..5) {
                if (cell.walls[dir]) continue
                val nxt = curr.getNeighbor(dir)
                if (!gridMap.containsKey(nxt)) continue

                // Check if gate is closed on this edge
                val oppositeDir = (dir + 3) % 6
                val isGateClosed = gatesList.any { g ->
                    g.color != color && (
                        (g.fromCoord == curr && g.wallIndex == dir) ||
                        (g.fromCoord == nxt && g.wallIndex == oppositeDir)
                    )
                }
                if (isGateClosed) continue

                val nextColor = switchMap[nxt]?.color ?: color
                val nextState = nxt to nextColor
                if (nextState !in visited) {
                    visited.add(nextState)
                    queue.add(nextState)
                }
            }
        }
        return false
    }

    private fun canReachGoalDirectly(
        start: HexCoord,
        goal: HexCoord,
        gridMap: Map<HexCoord, Cell>,
        gatesList: List<CircuitGate>,
        activeColor: CircuitColor = CircuitColor.RED
    ): Boolean {
        val queue = java.util.ArrayDeque<HexCoord>()
        val visited = mutableSetOf<HexCoord>()
        queue.add(start)
        visited.add(start)

        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            if (curr == goal) return true

            val cell = gridMap[curr] ?: continue
            for (dir in 0..5) {
                if (cell.walls[dir]) continue
                val nxt = curr.getNeighbor(dir)
                if (!gridMap.containsKey(nxt) || nxt in visited) continue

                val oppositeDir = (dir + 3) % 6
                val isGateClosed = gatesList.any { g ->
                    g.color != activeColor && (
                        (g.fromCoord == curr && g.wallIndex == dir) ||
                        (g.fromCoord == nxt && g.wallIndex == oppositeDir)
                    )
                }
                if (isGateClosed) continue

                visited.add(nxt)
                queue.add(nxt)
            }
        }
        return false
    }

    private fun findDirectPathEdges(
        start: HexCoord,
        goal: HexCoord,
        gridMap: Map<HexCoord, Cell>,
        gatesList: List<CircuitGate>,
        activeColor: CircuitColor = CircuitColor.RED
    ): List<Pair<HexCoord, Int>> {
        val queue = java.util.ArrayDeque<HexCoord>()
        val parent = mutableMapOf<HexCoord, Pair<HexCoord, Int>>()
        val visited = mutableSetOf<HexCoord>()

        queue.add(start)
        visited.add(start)

        var reached = false
        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            if (curr == goal) {
                reached = true
                break
            }

            val cell = gridMap[curr] ?: continue
            for (dir in 0..5) {
                if (cell.walls[dir]) continue
                val nxt = curr.getNeighbor(dir)
                if (!gridMap.containsKey(nxt) || nxt in visited) continue

                val oppositeDir = (dir + 3) % 6
                val isGateClosed = gatesList.any { g ->
                    g.color != activeColor && (
                        (g.fromCoord == curr && g.wallIndex == dir) ||
                        (g.fromCoord == nxt && g.wallIndex == oppositeDir)
                    )
                }
                if (isGateClosed) continue

                visited.add(nxt)
                parent[nxt] = curr to dir
                queue.add(nxt)
            }
        }

        if (!reached) return emptyList()

        val pathEdges = mutableListOf<Pair<HexCoord, Int>>()
        var curr = goal
        while (curr != start) {
            val p = parent[curr] ?: break
            pathEdges.add(p.first to p.second)
            curr = p.first
        }
        return pathEdges.reversed()
    }

    private fun hexDistance(a: HexCoord, b: HexCoord): Int {
        return (kotlin.math.abs(a.q - b.q) + kotlin.math.abs(a.q + a.r - b.q - b.r) + kotlin.math.abs(a.r - b.r)) / 2
    }

    fun isGateBlocked(fromCoord: HexCoord, dir: Int): Boolean {
        if (gameMode != GameMode.CIRCUIT_GATES) return false
        val oppositeDir = (dir + 3) % 6
        val toCoord = fromCoord.getNeighbor(dir)
        return circuitGates.any { gate ->
            gate.color != activeCircuitColor && (
                (gate.fromCoord == fromCoord && gate.wallIndex == dir) ||
                (gate.fromCoord == toCoord && gate.wallIndex == oppositeDir)
            )
        }
    }

    fun movePlayer(directionIndex: Int) {
        if (isWon || isGameOver || isPaused) return
        
        // --- Mode 7: Dual Sync Simultaneous Control ---
        if (gameMode == GameMode.DUAL_SYNC) {
            val currentHeroCell = grid[playerPos]
            var newHeroPos = playerPos
            if (currentHeroCell != null && !currentHeroCell.walls[directionIndex]) {
                val nxt = playerPos.getNeighbor(directionIndex)
                if (grid.containsKey(nxt)) newHeroPos = nxt
            }

            val currentCloneCell = grid[clonePos]
            var newClonePos = clonePos
            if (currentCloneCell != null && !currentCloneCell.walls[directionIndex]) {
                val nxt = clonePos.getNeighbor(directionIndex)
                if (grid.containsKey(nxt)) newClonePos = nxt
            }

            if (newHeroPos != playerPos || newClonePos != clonePos) {
                playerPos = newHeroPos
                clonePos = newClonePos
                moves++
                com.example.honeycombmaze.game.SoundManager.playMoveSound()

                // Check Win condition: Both reach their twin goals
                if (playerPos == goalPos && clonePos == cloneGoalPos) {
                    isWon = true
                    com.example.honeycombmaze.game.SoundManager.playWinSound()
                }
            } else {
                com.example.honeycombmaze.game.SoundManager.playWallHitSound()
            }
            return
        }

        val currentCell = grid[playerPos] ?: return
        
        // Check if there is a wall or closed circuit gate in that direction
        if (!currentCell.walls[directionIndex] && !isGateBlocked(playerPos, directionIndex)) {
            var newPos = playerPos
            
            // Loop for Ice Slide
            do {
                val nextPos = newPos.getNeighbor(directionIndex)
                if (grid.containsKey(nextPos)) {
                    val nextCell = grid[newPos]
                    if (nextCell != null && !nextCell.walls[directionIndex] && !isGateBlocked(newPos, directionIndex)) {
                        newPos = nextPos
                        if (gameMode != GameMode.ICE_SLIDE) break
                        if (newPos == goalPos || enemies.contains(newPos) || traps.contains(newPos)) {
                            break
                        }
                    } else {
                        break
                    }
                } else {
                    break
                }
            } while(gameMode == GameMode.ICE_SLIDE)
            
            if (newPos != playerPos) {
                val oldPos = playerPos
                playerPos = newPos
                moves++
                com.example.honeycombmaze.game.SoundManager.playMoveSound()
                
                // Lava Floor
                if (gameMode == GameMode.LAVA_FLOOR) {
                    lavaTiles = lavaTiles + oldPos
                }

                // Time Rush (+5s)
                if (gameMode == GameMode.TIME_RUSH && timeBonusOrbs.contains(playerPos)) {
                    timeRemaining += 5
                    timeBonusOrbs = timeBonusOrbs - playerPos
                    com.example.honeycombmaze.game.SoundManager.playHoneyCollectSound()
                }

                // Mode 9: Circuit Gates Switch Activation
                if (gameMode == GameMode.CIRCUIT_GATES) {
                    val sw = circuitSwitches.find { it.pos == playerPos }
                    if (sw != null && sw.color != activeCircuitColor) {
                        activeCircuitColor = sw.color
                        com.example.honeycombmaze.game.SoundManager.playHoneyCollectSound()
                    }
                }

                // Mode 8: Laser Sentinels Sweep
                if (gameMode == GameMode.LASER_SENTINELS) {
                    for (s in sentinels) {
                        s.angleDir = (s.angleDir + 1) % 6
                    }
                    activeLaserBeams = computeLaserBeams()
                    if (activeLaserBeams.contains(playerPos) || sentinels.any { it.pos == playerPos }) {
                        isGameOver = true
                        com.example.honeycombmaze.game.SoundManager.playTrapSound()
                    }
                }

                // Mode 10: Stealth Patrol Drone Movement
                if (gameMode == GameMode.STEALTH_PATROL) {
                    for (d in patrolDrones) {
                        if (d.patrolNodes.size > 1) {
                            if (d.movingForward) {
                                d.patrolIndex++
                                if (d.patrolIndex >= d.patrolNodes.size) {
                                    d.patrolIndex = max(0, d.patrolNodes.size - 2)
                                    d.movingForward = false
                                }
                            } else {
                                d.patrolIndex--
                                if (d.patrolIndex < 0) {
                                    d.patrolIndex = min(1, d.patrolNodes.size - 1)
                                    d.movingForward = true
                                }
                            }
                            val prevPos = d.pos
                            d.pos = d.patrolNodes[d.patrolIndex]
                            for (i in 0..5) {
                                if (prevPos.getNeighbor(i) == d.pos) {
                                    d.facingDir = i
                                    break
                                }
                            }
                        }
                    }
                    droneVisionTiles = computeDroneVision()
                    if (playerPos == goalPos) {
                        // Escaped successfully into the extraction goal!
                    } else if (droneVisionTiles.contains(playerPos) || patrolDrones.any { it.pos == playerPos }) {
                        isGameOver = true
                        com.example.honeycombmaze.game.SoundManager.playGameOverSound()
                    }
                }
                
                if (playerPos == goalPos) {
                    isWon = true
                    isGameOver = false
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

        // BFS from player to all cells to find the direction and distance to move
        val queue = java.util.ArrayDeque<HexCoord>()
        queue.add(playerPos)
        val nextStepMap = mutableMapOf<HexCoord, HexCoord>()
        val distToPlayer = mutableMapOf<HexCoord, Int>()
        val visited = mutableSetOf(playerPos)
        distToPlayer[playerPos] = 0
        
        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            val currDist = distToPlayer[curr] ?: 0
            val cell = grid[curr] ?: continue
            for (i in 0..5) {
                if (!cell.walls[i]) {
                    val neighbor = curr.getNeighbor(i)
                    if (neighbor !in visited && grid.containsKey(neighbor)) {
                        visited.add(neighbor)
                        nextStepMap[neighbor] = curr
                        distToPlayer[neighbor] = currDist + 1
                        queue.add(neighbor)
                    }
                }
            }
        }
        
        // Sort enemy indices so the chaser closest to the player gets priority
        val sortedIndices = enemies.indices.sortedBy { distToPlayer[enemies[it]] ?: 999 }

        val newPositions = enemies.toMutableList()
        val occupied = mutableSetOf<HexCoord>()
        val unmovedPositions = enemies.toMutableSet()

        for (idx in sortedIndices) {
            val enemy = enemies[idx]
            unmovedPositions.remove(enemy)

            val primaryDesired = nextStepMap[enemy]
            val cell = grid[enemy]

            // Candidates: primary optimal BFS step first, then alternative open passages to avoid freezing
            val candidates = mutableListOf<HexCoord>()
            if (primaryDesired != null) {
                candidates.add(primaryDesired)
            }
            if (cell != null) {
                val altNeighbors = (0..5).filter { !cell.walls[it] }
                    .map { enemy.getNeighbor(it) }
                    .filter { grid.containsKey(it) && it != primaryDesired }
                    .sortedBy { distToPlayer[it] ?: 999 }
                candidates.addAll(altNeighbors)
            }

            var chosen = enemy
            for (cand in candidates) {
                if (cand == playerPos) {
                    chosen = cand
                    break
                }
                // Allowed to move into cand if not occupied by an already moved enemy and not occupied by an unmoved enemy
                if (cand !in occupied && cand !in unmovedPositions) {
                    chosen = cand
                    break
                }
            }

            if (chosen == playerPos) {
                isGameOver = true
                com.example.honeycombmaze.game.SoundManager.playGameOverSound()
            }

            occupied.add(chosen)
            newPositions[idx] = chosen
        }

        enemies = newPositions
    }
    
    fun nextLevel() {
        if (level < 100) {
            level++
            startNewGame()
        }
    }
}
