package com.example.honeycombmaze.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONObject
import java.io.File

class PreferencesManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getInternalBackupFile(): File {
        return File(context.filesDir, "honeycomb_internal_save.json")
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
                    for (i in 0 until unlockedModes.length()) {
                        val mId = unlockedModes.getInt(i)
                        prefs.edit().putBoolean("$KEY_MODE_UNLOCKED_$mId", true).apply()
                    }
                }
                val unlockedAvatars = json.optJSONArray("unlockedAvatars")
                if (unlockedAvatars != null) {
                    for (i in 0 until unlockedAvatars.length()) {
                        val avId = unlockedAvatars.getString(i)
                        prefs.edit().putBoolean("avatar_unlocked_$avId", true).apply()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PreferencesManager", "Error reading internal backup: ${e.message}")
        }
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

            val json = JSONObject().apply {
                put("honey", honeyValue)
                put("selectedAvatar", selectedAvatar)
                put("isRemoveAdsPurchased", isRemoveAdsPurchased)
                put("isAllLevelsUnlocked", isAllLevelsUnlocked)
                put("unlockedModes", unlockedModesArray)
                put("unlockedAvatars", unlockedAvatarsArray)
            }
            getInternalBackupFile().writeText(json.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("PreferencesManager", "Error writing internal backup: ${e.message}")
        }
    }

    private var _honeyState = mutableIntStateOf(initHoney())

    var isCloudRestoreInProgress: Boolean = false
    var hasLoadedFromCloud: Boolean = false

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
                CloudSaveManager.saveToCloud(context, this)
            }
        }

    private var _isRemoveAdsPurchasedState = androidx.compose.runtime.mutableStateOf(prefs.getBoolean(KEY_REMOVE_ADS, false))

    var isRemoveAdsPurchased: Boolean
        get() = _isRemoveAdsPurchasedState.value
        set(value) {
            _isRemoveAdsPurchasedState.value = value
            prefs.edit().putBoolean(KEY_REMOVE_ADS, value).apply()
            saveInternalBackup(honey)
            CloudSaveManager.saveToCloud(context, this)
        }

    private var _isAllLevelsUnlockedState = androidx.compose.runtime.mutableStateOf(prefs.getBoolean(KEY_ALL_LEVELS_UNLOCKED, false))

    var isAllLevelsUnlocked: Boolean
        get() = _isAllLevelsUnlockedState.value
        set(value) {
            _isAllLevelsUnlockedState.value = value
            prefs.edit().putBoolean(KEY_ALL_LEVELS_UNLOCKED, value).apply()
            saveInternalBackup(honey)
            CloudSaveManager.saveToCloud(context, this)
        }

    private var _selectedAvatarState = androidx.compose.runtime.mutableStateOf(prefs.getString(KEY_SELECTED_AVATAR, "default") ?: "default")

    var selectedAvatar: String
        get() = _selectedAvatarState.value
        set(value) {
            _selectedAvatarState.value = value
            prefs.edit().putString(KEY_SELECTED_AVATAR, value).apply()
            saveInternalBackup(honey)
            CloudSaveManager.saveToCloud(context, this)
        }

    fun isAvatarUnlocked(avatarId: String): Boolean {
        if (avatarId == "default") return true
        return prefs.getBoolean("avatar_unlocked_$avatarId", false)
    }

    fun unlockAvatar(avatarId: String, syncCloud: Boolean = true) {
        prefs.edit().putBoolean("avatar_unlocked_$avatarId", true).apply()
        saveInternalBackup(honey)
        if (syncCloud && hasLoadedFromCloud && !isCloudRestoreInProgress) {
            CloudSaveManager.saveToCloud(context, this)
        }
    }

    fun isModeUnlocked(modeId: Int): Boolean {
        // Classic, Chasers, Traps, Teleporters (0..3) are unlocked by default
        if (modeId in 0..3) return true
        return prefs.getBoolean("$KEY_MODE_UNLOCKED_$modeId", false)
    }

    fun unlockMode(modeId: Int, syncCloud: Boolean = true) {
        prefs.edit().putBoolean("$KEY_MODE_UNLOCKED_$modeId", true).apply()
        saveInternalBackup(honey)
        if (syncCloud && hasLoadedFromCloud && !isCloudRestoreInProgress) {
            CloudSaveManager.saveToCloud(context, this)
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
            CloudSaveManager.saveToCloud(context, this)
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
            CloudSaveManager.saveToCloud(context, this)
        }
    }

    fun resetAllData() {
        prefs.edit().clear().commit()
        _honeyState.intValue = 0
        _selectedAvatarState.value = "default"
        _isAllLevelsUnlockedState.value = false
        try {
            val file = getInternalBackupFile()
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e("PreferencesManager", "Error deleting internal backup: ${e.message}")
        }
        CloudSaveManager.resetCloudSave(context, this)
    }

    companion object {
        private const val PREFS_NAME = "HoneycombPrefs"
        private const val KEY_HONEY = "total_honey"
        private const val KEY_REMOVE_ADS = "remove_ads_purchased"
        private const val KEY_ALL_LEVELS_UNLOCKED = "all_levels_unlocked"
        private const val KEY_MODE_UNLOCKED_ = "mode_unlocked_"
        private const val KEY_SELECTED_AVATAR = "selected_avatar"
        private const val KEY_USER_GUID = "user_guid"
        
        // Mode unlocking costs
        val MODE_COSTS = mapOf(
            4 to 500,   // DARKNESS
            5 to 2000,   // ICE_SLIDE
            6 to 5000   // TIME_RUSH
        )
    }
}

