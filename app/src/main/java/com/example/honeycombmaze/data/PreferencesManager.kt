package com.example.honeycombmaze.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject
import java.io.File

class PreferencesManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getInternalBackupFile(): File {
        return File(appContext.filesDir, "honeycomb_internal_save.json")
    }

    private fun initHoney(): Int {
        val prefsHoney = prefs.getInt(KEY_HONEY, 0)
        var backupHoney = 0
        try {
            val file = getInternalBackupFile()
            if (file.exists()) {
                val json = JSONObject(file.readText(Charsets.UTF_8))
                backupHoney = json.optInt("honey", 0)
                val unlockedModes = json.optJSONArray("unlockedModes")
                if (unlockedModes != null) {
                    val setupDone = prefs.getBoolean("swap_darkness_stealth_v1", false)
                    for (i in 0 until unlockedModes.length()) {
                        val mId = unlockedModes.getInt(i)
                        if (mId != 9 && !(mId == 4 && !setupDone)) {
                            prefs.edit().putBoolean("$KEY_MODE_UNLOCKED_$mId", true).apply()
                        }
                    }
                }
                val unlockedAvatars = json.optJSONArray("unlockedAvatars")
                if (unlockedAvatars != null) {
                    for (i in 0 until unlockedAvatars.length()) {
                        val avId = unlockedAvatars.getString(i)
                        prefs.edit().putBoolean("avatar_unlocked_$avId", true).apply()
                    }
                }
                val unlockedModeLevels = json.optJSONArray("unlockedModeLevels")
                if (unlockedModeLevels != null) {
                    for (i in 0 until unlockedModeLevels.length()) {
                        val mId = unlockedModeLevels.getInt(i)
                        prefs.edit().putBoolean("mode_levels_unlocked_$mId", true).apply()
                        prefs.edit().putInt("mode_max_level_$mId", 100).apply()
                    }
                }
                val modeLevels = json.optJSONObject("modeLevels")
                if (modeLevels != null) {
                    for (mId in 0..10) {
                        val lvl = modeLevels.optInt(mId.toString(), 1)
                        if (lvl > 1) {
                            val currentLvl = prefs.getInt("mode_max_level_$mId", 1)
                            if (lvl > currentLvl) {
                                prefs.edit().putInt("mode_max_level_$mId", lvl).apply()
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return maxOf(prefsHoney, backupHoney)
    }

    private fun saveInternalBackup(honeyValue: Int) {
        try {
            val unlockedModesArray = org.json.JSONArray()
            for (m in 0..10) {
                if (isModeUnlocked(m)) unlockedModesArray.put(m)
            }
            val unlockedAvatarsArray = org.json.JSONArray()
            for (av in com.example.honeycombmaze.data.AvatarRegistry.AVATARS) {
                if (isAvatarUnlocked(av.id)) unlockedAvatarsArray.put(av.id)
            }
            val unlockedModeLevelsArray = org.json.JSONArray()
            for (mId in 0..10) {
                if (isModeLevelsUnlocked(mId)) unlockedModeLevelsArray.put(mId)
            }
            val modeLevelsJson = JSONObject()
            for (mId in 0..10) {
                modeLevelsJson.put(mId.toString(), getMaxUnlockedLevel(mId))
            }

            val json = JSONObject().apply {
                put("honey", honeyValue)
                put("selectedAvatar", selectedAvatar)
                put("isRemoveAdsPurchased", isRemoveAdsPurchased)
                put("isAllLevelsUnlocked", false)
                put("unlockedModes", unlockedModesArray)
                put("unlockedAvatars", unlockedAvatarsArray)
                put("unlockedModeLevels", unlockedModeLevelsArray)
                put("modeLevels", modeLevelsJson)
            }
            getInternalBackupFile().writeText(json.toString(), Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    private var _honeyState = mutableIntStateOf(initHoney())

    @Volatile var isCloudRestoreInProgress: Boolean = false
    @Volatile var hasLoadedFromCloud: Boolean = false

    fun setHoneySilent(value: Int) {
        _honeyState.intValue = value
        prefs.edit().putInt(KEY_HONEY, value).apply()
        saveInternalBackup(value)
    }

    var honey: Int
        get() = _honeyState.intValue
        set(value) {
            _honeyState.intValue = value
            prefs.edit().putInt(KEY_HONEY, value).apply()
            saveInternalBackup(value)
            if (hasLoadedFromCloud && !isCloudRestoreInProgress) {
                CloudSaveManager.saveToCloud(appContext, this)
            }
        }

    private var _isRemoveAdsPurchasedState = mutableStateOf(prefs.getBoolean(KEY_REMOVE_ADS, false))

    var isRemoveAdsPurchased: Boolean
        get() = _isRemoveAdsPurchasedState.value
        set(value) {
            _isRemoveAdsPurchasedState.value = value
            prefs.edit().putBoolean(KEY_REMOVE_ADS, value).apply()
            saveInternalBackup(honey)
            CloudSaveManager.saveToCloud(appContext, this)
        }

    var isAllLevelsUnlocked: Boolean
        get() = false
        set(_) {}

    private var _selectedAvatarState = mutableStateOf(prefs.getString(KEY_SELECTED_AVATAR, "default") ?: "default")

    var selectedAvatar: String
        get() = _selectedAvatarState.value
        set(value) {
            _selectedAvatarState.value = value
            prefs.edit().putString(KEY_SELECTED_AVATAR, value).apply()
            saveInternalBackup(honey)
            CloudSaveManager.saveToCloud(appContext, this)
        }

    init {
        // Migration: Ensure Darkness (4) starts locked at 2500 coins, Stealth Patrol (10) is free, Circuit Gates (9) is removed
        if (!prefs.getBoolean("swap_darkness_stealth_v1", false)) {
            prefs.edit()
                .remove("${KEY_MODE_UNLOCKED_}4")
                .remove("mode_levels_unlocked_4")
                .remove("${KEY_MODE_UNLOCKED_}9")
                .putBoolean("${KEY_MODE_UNLOCKED_}10", true)
                .putBoolean("swap_darkness_stealth_v1", true)
                .apply()
            try {
                val file = getInternalBackupFile()
                if (file.exists()) {
                    val json = JSONObject(file.readText(Charsets.UTF_8))
                    val unlockedModes = json.optJSONArray("unlockedModes")
                    if (unlockedModes != null) {
                        val newModes = org.json.JSONArray()
                        for (i in 0 until unlockedModes.length()) {
                            val mId = unlockedModes.getInt(i)
                            if (mId != 4 && mId != 9) {
                                newModes.put(mId)
                            }
                        }
                        newModes.put(10)
                        json.put("unlockedModes", newModes)
                        file.writeText(json.toString(), Charsets.UTF_8)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun isAvatarUnlocked(avatarId: String): Boolean {
        if (avatarId == "default") return true
        return prefs.getBoolean("avatar_unlocked_$avatarId", false)
    }

    fun unlockAvatar(avatarId: String, syncCloud: Boolean = true) {
        prefs.edit().putBoolean("avatar_unlocked_$avatarId", true).apply()
        saveInternalBackup(honey)
        if (syncCloud && hasLoadedFromCloud && !isCloudRestoreInProgress) {
            CloudSaveManager.saveToCloud(appContext, this)
        }
    }

    fun isModeUnlocked(modeId: Int): Boolean {
        // Free modes: Classic (0), Chasers (1), Traps (2), Lava Floor (3), Ice Slide (5), Time Rush (6), Stealth Patrol (10)
        // Darkness (4) requires 2500 coins, Dual Sync (7) requires 1000 coins
        if ((modeId in 0..6 && modeId != 4) || modeId == 10) return true
        return prefs.getBoolean("$KEY_MODE_UNLOCKED_$modeId", false)
    }

    fun unlockMode(modeId: Int, syncCloud: Boolean = true) {
        prefs.edit().putBoolean("$KEY_MODE_UNLOCKED_$modeId", true).apply()
        saveInternalBackup(honey)
        if (syncCloud && hasLoadedFromCloud && !isCloudRestoreInProgress) {
            CloudSaveManager.saveToCloud(appContext, this)
        }
    }

    fun getOrCreateUserGuid(): String {
        var guid = prefs.getString(KEY_USER_GUID, null)
        if (guid == null) {
            guid = java.util.UUID.randomUUID().toString()
            prefs.edit().putString(KEY_USER_GUID, guid).apply()
        }
        return guid
    }

    fun isPurchaseTokenProcessed(token: String): Boolean {
        return prefs.getBoolean("processed_token_$token", false)
    }

    fun markPurchaseTokenProcessed(token: String) {
        prefs.edit().putBoolean("processed_token_$token", true).apply()
    }

    fun getMaxUnlockedLevel(modeId: Int): Int {
        if (isModeLevelsUnlocked(modeId)) return 100
        return prefs.getInt("mode_max_level_$modeId", 1)
    }

    fun setMaxUnlockedLevel(modeId: Int, maxLevel: Int, syncCloud: Boolean = true) {
        prefs.edit().putInt("mode_max_level_$modeId", maxLevel).apply()
        saveInternalBackup(honey)
        if (syncCloud && hasLoadedFromCloud && !isCloudRestoreInProgress) {
            CloudSaveManager.saveToCloud(appContext, this)
        }
    }

    fun isModeLevelsUnlocked(modeId: Int): Boolean {
        return prefs.getBoolean("mode_levels_unlocked_$modeId", false)
    }

    fun setModeLevelsUnlocked(modeId: Int, unlocked: Boolean, syncCloud: Boolean = true) {
        prefs.edit().putBoolean("mode_levels_unlocked_$modeId", unlocked).apply()
        if (unlocked) {
            prefs.edit().putInt("mode_max_level_$modeId", 100).apply()
        }
        saveInternalBackup(honey)
        if (syncCloud && hasLoadedFromCloud && !isCloudRestoreInProgress) {
            CloudSaveManager.saveToCloud(appContext, this)
        }
    }

    var lastPlayerId: String?
        get() = prefs.getString("last_signed_in_player_id", null)
        set(value) {
            prefs.edit().putString("last_signed_in_player_id", value).apply()
        }

    fun applyProfile(json: JSONObject) {
        val newHoney = json.optInt("honey", 0)
        val newAvatar = json.optString("selectedAvatar", "default")
        val newRemoveAds = json.optBoolean("isRemoveAdsPurchased", false)

        _honeyState.intValue = newHoney
        _selectedAvatarState.value = newAvatar
        _isRemoveAdsPurchasedState.value = newRemoveAds

        val editor = prefs.edit()
        editor.putInt(KEY_HONEY, newHoney)
        editor.putString(KEY_SELECTED_AVATAR, newAvatar)
        editor.putBoolean(KEY_REMOVE_ADS, newRemoveAds)

        // Reset mode unlock flags & levels
        for (m in 0..10) {
            editor.remove("$KEY_MODE_UNLOCKED_$m")
            editor.remove("mode_levels_unlocked_$m")
            editor.putInt("mode_max_level_$m", 1)
        }

        // Reset avatar unlock flags
        for (av in com.example.honeycombmaze.data.AvatarRegistry.AVATARS) {
            if (av.id != "default") {
                editor.remove("avatar_unlocked_${av.id}")
            }
        }

        // Apply modes
        val unlockedModes = json.optJSONArray("unlockedModes")
        if (unlockedModes != null) {
            for (i in 0 until unlockedModes.length()) {
                val mId = unlockedModes.getInt(i)
                if (mId != 9 && mId != 4) {
                    editor.putBoolean("$KEY_MODE_UNLOCKED_$mId", true)
                }
            }
        }

        // Apply avatars
        val unlockedAvatars = json.optJSONArray("unlockedAvatars")
        if (unlockedAvatars != null) {
            for (i in 0 until unlockedAvatars.length()) {
                editor.putBoolean("avatar_unlocked_${unlockedAvatars.getString(i)}", true)
            }
        }

        // Apply mode levels
        val modeLevels = json.optJSONObject("modeLevels")
        if (modeLevels != null) {
            for (mId in 0..10) {
                val lvl = modeLevels.optInt(mId.toString(), 1)
                editor.putInt("mode_max_level_$mId", lvl)
            }
        }

        // Apply unlocked mode levels
        val unlockedModeLevels = json.optJSONArray("unlockedModeLevels")
        if (unlockedModeLevels != null) {
            for (i in 0 until unlockedModeLevels.length()) {
                val mId = unlockedModeLevels.getInt(i)
                editor.putBoolean("mode_levels_unlocked_$mId", true)
                editor.putInt("mode_max_level_$mId", 100)
            }
        }

        editor.apply()
        saveInternalBackup(newHoney)
    }

    fun resetToDefaultProfile() {
        _honeyState.intValue = 0
        _selectedAvatarState.value = "default"
        _isRemoveAdsPurchasedState.value = false

        val editor = prefs.edit()
        editor.putInt(KEY_HONEY, 0)
        editor.putString(KEY_SELECTED_AVATAR, "default")
        editor.putBoolean(KEY_REMOVE_ADS, false)

        for (m in 0..10) {
            editor.remove("$KEY_MODE_UNLOCKED_$m")
            editor.remove("mode_levels_unlocked_$m")
            editor.putInt("mode_max_level_$m", 1)
        }

        for (av in com.example.honeycombmaze.data.AvatarRegistry.AVATARS) {
            if (av.id != "default") {
                editor.remove("avatar_unlocked_${av.id}")
            }
        }

        editor.apply()
        saveInternalBackup(0)
    }

    fun clearLocalData() {
        prefs.edit().clear().commit()
        _honeyState.intValue = 0
        _selectedAvatarState.value = "default"
        _isRemoveAdsPurchasedState.value = false
        try {
            val file = getInternalBackupFile()
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e("PreferencesManager", "Error deleting internal backup: ${e.message}")
        }
    }

    fun resetAllData() {
        clearLocalData()
        CloudSaveManager.resetCloudSave(appContext, this)
    }

    companion object {
        private const val PREFS_NAME = "HoneycombPrefs"
        private const val KEY_HONEY = "total_honey"
        private const val KEY_REMOVE_ADS = "remove_ads_purchased"
        private const val KEY_ALL_LEVELS_UNLOCKED = "all_levels_unlocked"
        private const val KEY_MODE_UNLOCKED_ = "mode_unlocked_"
        private const val KEY_SELECTED_AVATAR = "selected_avatar"
        private const val KEY_USER_GUID = "user_guid"

        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also { instance = it }
            }
        }

        operator fun invoke(context: Context): PreferencesManager = getInstance(context)
        
        // Mode unlocking costs
        val MODE_COSTS = mapOf(
            7 to 1000,   // DUAL_SYNC
            4 to 2500    // DARKNESS
        )
    }
}
