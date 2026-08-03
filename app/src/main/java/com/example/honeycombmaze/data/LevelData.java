package com.example.honeycombmaze.data;

import androidx.room.Entity;

@Entity(tableName = "level_data", primaryKeys = {"modeId", "level"})
public class LevelData {
    public int modeId;
    public int level;
    public int bestMoves;
    public int bestTimeSeconds;

    public LevelData(int modeId, int level, int bestMoves, int bestTimeSeconds) {
        this.modeId = modeId;
        this.level = level;
        this.bestMoves = bestMoves;
        this.bestTimeSeconds = bestTimeSeconds;
    }
}
