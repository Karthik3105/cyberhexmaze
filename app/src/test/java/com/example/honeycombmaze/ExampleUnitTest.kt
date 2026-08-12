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
}