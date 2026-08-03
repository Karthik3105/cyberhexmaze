package com.example.honeycombmaze.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "game_data")
public class GameData {
    @PrimaryKey
    public int modeId;
    public int maxUnlockedLevel;

    public GameData(int modeId, int maxUnlockedLevel) {
        this.modeId = modeId;
        this.maxUnlockedLevel = maxUnlockedLevel;
    }
}
