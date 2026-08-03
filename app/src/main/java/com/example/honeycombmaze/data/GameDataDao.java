package com.example.honeycombmaze.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface GameDataDao {
    @Query("SELECT * FROM game_data WHERE modeId = :modeId LIMIT 1")
    GameData getGameData(int modeId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void saveGameData(GameData gameData);
    @Query("SELECT * FROM level_data WHERE modeId = :modeId AND level = :level LIMIT 1")
    LevelData getLevelData(int modeId, int level);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void saveLevelData(LevelData levelData);

    @Query("DELETE FROM game_data")
    void deleteAllGameData();

    @Query("DELETE FROM level_data")
    void deleteAllLevelData();
}
