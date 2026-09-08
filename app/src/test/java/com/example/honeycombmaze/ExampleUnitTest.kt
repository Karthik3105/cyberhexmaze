package com.example.honeycombmaze

import com.example.honeycombmaze.game.GameMode
import com.example.honeycombmaze.game.GameState
import org.junit.Test
import org.junit.Assert.*

class ExampleUnitTest {
    @Test
    fun testDeterministicLevelGenerationAcrossReplays() {
        // Test all game modes and sample levels (1, 10, 50, 100)
        val sampleLevels = listOf(1, 10, 50, 100)

        for (mode in GameMode.values()) {
            for (level in sampleLevels) {
                val state1 = GameState().apply {
                    this.gameMode = mode
                    this.level = level
                    this.startNewGame()
                }

                val state2 = GameState().apply {
                    this.gameMode = mode
                    this.level = level
                    this.startNewGame()
                }

                // Verify same player position and goal position
                assertEquals("Mode $mode Level $level playerPos mismatch", state1.playerPos, state2.playerPos)
                assertEquals("Mode $mode Level $level goalPos mismatch", state1.goalPos, state2.goalPos)

                // Verify same grid dimensions and cells
                assertEquals("Mode $mode Level $level grid size mismatch", state1.grid.size, state2.grid.size)
                for ((coord, cell1) in state1.grid) {
                    val cell2 = state2.grid[coord]
                    assertNotNull("Cell missing at $coord in mode $mode level $level", cell2)
                    assertArrayEquals("Walls mismatch at $coord in mode $mode level $level", cell1.walls, cell2!!.walls)
                }

                // Verify same enemy/trap/bonus spawns
                assertEquals("Mode $mode Level $level enemies mismatch", state1.enemies, state2.enemies)
                assertEquals("Mode $mode Level $level traps mismatch", state1.traps, state2.traps)
                assertEquals("Mode $mode Level $level timeBonusOrbs mismatch", state1.timeBonusOrbs, state2.timeBonusOrbs)
            }
        }
    }

    @Test
    fun testDualSyncSolvability() {
        fun isSolvable(state: GameState): Boolean {
            val startHero = state.playerPos
            val startClone = state.clonePos
            val targetHero = state.goalPos
            val targetClone = state.cloneGoalPos
            val grid = state.grid

            val queue = java.util.ArrayDeque<Pair<com.example.honeycombmaze.game.HexCoord, com.example.honeycombmaze.game.HexCoord>>()
            val visited = mutableSetOf<Pair<com.example.honeycombmaze.game.HexCoord, com.example.honeycombmaze.game.HexCoord>>()

            val startState = Pair(startHero, startClone)
            queue.add(startState)
            visited.add(startState)

            while (queue.isNotEmpty()) {
                val (hero, clone) = queue.removeFirst()
                if (hero == targetHero && clone == targetClone) {
                    return true
                }

                for (dir in 0..5) {
                    val heroCell = grid[hero]
                    var nextHero = hero
                    if (heroCell != null && !heroCell.walls[dir]) {
                        val nxt = hero.getNeighbor(dir)
                        if (grid.containsKey(nxt)) nextHero = nxt
                    }

                    val cloneCell = grid[clone]
                    var nextClone = clone
                    if (cloneCell != null && !cloneCell.walls[dir]) {
                        val nxt = clone.getNeighbor(dir)
                        if (grid.containsKey(nxt)) nextClone = nxt
                    }

                    val nextState = Pair(nextHero, nextClone)
                    if (nextState !in visited) {
                        visited.add(nextState)
                        queue.add(nextState)
                    }
                }
            }
            return false
        }

        val targetLevels = listOf(3, 8, 16, 52)
        for (lvl in targetLevels) {
            val state = GameState().apply {
                gameMode = GameMode.DUAL_SYNC
                level = lvl
                startNewGame()
            }
            assertTrue("Dual Sync level $lvl should be solvable", isSolvable(state))
        }
    }
}