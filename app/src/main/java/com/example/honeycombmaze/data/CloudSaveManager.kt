package com.example.honeycombmaze.data

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.example.honeycombmaze.HoneyCombMazeApplication
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.snapshot.Snapshot
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return HoneyCombMazeApplication.currentActivity
}

object CloudSaveManager {
    private const val SNAPSHOT_NAME = "HoneyMazeSaveV2"
    private val cloudScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val snapshotMutex = Mutex()

    @Volatile private var isInitialized = false
    @Volatile private var hasPendingSave = false
    @Volatile private var isSignedIn = false

    fun initSdk(context: Context) {
        if (!isInitialized) {
            try {
                PlayGamesSdk.initialize(context)
                isInitialized = true
                Log.d("CloudSaveManager", "PlayGamesSdk initialized successfully.")
            } catch (e: Exception) {
                Log.e("CloudSaveManager", "Error initializing PlayGamesSdk: ${e.message}")
            }
        }
    }

    fun openAccountPicker(context: Context) {
        val activity = context.findActivity() ?: return
        try {
            val intent = android.accounts.AccountManager.newChooseAccountIntent(
                null,
                null,
                arrayOf("com.google"),
                true,
                null,
                null,
                null,
                null
            )
            activity.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = android.content.Intent("com.google.android.gms.games.CHANGE_ACCOUNT")
                intent.setPackage("com.google.android.play.games")
                activity.startActivity(intent)
            } catch (e2: Exception) {
                Log.e("CloudSaveManager", "Could not open account picker: ${e2.message}")
            }
        }
    }

    @Volatile private var hasAttemptedLaunchSignIn = false
    @Volatile private var isSignInPromptActive = false

    fun initializeAndSignIn(context: Context, force: Boolean = false, onSignedIn: (() -> Unit)? = null) {
        val activity = context.findActivity() ?: return
        initSdk(activity)

        if (hasAttemptedLaunchSignIn && !force) {
            checkSilentAuth(activity, onSignedIn)
            return
        }
        if (isSignInPromptActive) return
        isSignInPromptActive = true
        hasAttemptedLaunchSignIn = true

        try {
            val gamesSignInClient = PlayGames.getGamesSignInClient(activity)
            gamesSignInClient.isAuthenticated().addOnCompleteListener { authTask ->
                if (authTask.isSuccessful && authTask.result.isAuthenticated) {
                    isSignInPromptActive = false
                    isSignedIn = true
                    Log.d("CloudSaveManager", "User already authenticated with Play Games.")
                    onSignedIn?.invoke()
                } else {
                    Log.d("CloudSaveManager", "Attempting Play Games signIn...")
                    gamesSignInClient.signIn().addOnCompleteListener { signInTask ->
                        isSignInPromptActive = false
                        if (signInTask.isSuccessful && signInTask.result.isAuthenticated) {
                            isSignedIn = true
                            Log.d("CloudSaveManager", "Play Games signIn successful.")
                            onSignedIn?.invoke()
                        } else {
                            val ex = signInTask.exception
                            Log.w("CloudSaveManager", "Play Games signIn not completed. Exception: $ex", ex)
                            if (ex is com.google.android.gms.common.api.ApiException) {
                                Log.e("CloudSaveManager", "Play Games ApiException status code: ${ex.statusCode}")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            isSignInPromptActive = false
            Log.e("CloudSaveManager", "Exception during signIn: ${e.message}")
        }
    }

    fun checkSilentAuth(context: Context, onSignedIn: (() -> Unit)? = null) {
        val activity = context.findActivity() ?: return
        initSdk(activity)
        try {
            val gamesSignInClient = PlayGames.getGamesSignInClient(activity)
            gamesSignInClient.isAuthenticated().addOnCompleteListener { task ->
                if (task.isSuccessful && task.result.isAuthenticated) {
                    isSignedIn = true
                    onSignedIn?.invoke()
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Build the JSON payload from current PreferencesManager state.
     * Extracted to avoid code duplication.
     */
    private fun buildSaveJson(prefsManager: PreferencesManager): JSONObject {
        val unlockedModesArray = JSONArray()
        for (m in 0..10) {
            if (prefsManager.isModeUnlocked(m)) unlockedModesArray.put(m)
        }

        val unlockedAvatarsArray = JSONArray()
        for (av in AvatarRegistry.AVATARS) {
            if (prefsManager.isAvatarUnlocked(av.id)) unlockedAvatarsArray.put(av.id)
        }

        val unlockedModeLevelsArray = JSONArray()
        for (mId in 0..10) {
            if (prefsManager.isModeLevelsUnlocked(mId)) unlockedModeLevelsArray.put(mId)
        }

        val modeLevelsJson = JSONObject()
        for (mId in 0..10) {
            modeLevelsJson.put(mId.toString(), prefsManager.getMaxUnlockedLevel(mId))
        }

        val finalHoney = prefsManager.honey
        return JSONObject().apply {
            put("honey", finalHoney)
            put("selectedAvatar", prefsManager.selectedAvatar)
            put("isRemoveAdsPurchased", prefsManager.isRemoveAdsPurchased)
            put("isAllLevelsUnlocked", false)
            put("unlockedModes", unlockedModesArray)
            put("unlockedAvatars", unlockedAvatarsArray)
            put("unlockedModeLevels", unlockedModeLevelsArray)
            put("modeLevels", modeLevelsJson)
        }
    }

    private fun getPlayerCacheFile(context: Context, playerId: String): java.io.File {
        val safeId = playerId.replace(Regex("[^a-zA-Z0-9_]"), "_")
        return java.io.File(context.filesDir, "player_cache_${safeId}.json")
    }

    private fun savePlayerLocalCache(context: Context, playerId: String, json: JSONObject) {
        try {
            getPlayerCacheFile(context, playerId).writeText(json.toString(), Charsets.UTF_8)
            Log.d("CloudSaveManager", "Saved local player cache for $playerId")
        } catch (e: Exception) {
            Log.e("CloudSaveManager", "Error saving local player cache: ${e.message}")
        }
    }

    private fun getPlayerLocalCache(context: Context, playerId: String): JSONObject? {
        return try {
            val file = getPlayerCacheFile(context, playerId)
            if (file.exists()) JSONObject(file.readText(Charsets.UTF_8)) else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Core save logic that actually writes to the snapshot.
     * Called internally after all guards have been checked.
     */
    private suspend fun doSaveToCloudInternal(activity: Activity, prefsManager: PreferencesManager) {
        try {
            val signInClient = PlayGames.getGamesSignInClient(activity)
            val authResult = try {
                Tasks.await(signInClient.isAuthenticated())
            } catch (e: Exception) {
                null
            }

            if (authResult == null || !authResult.isAuthenticated) {
                Log.w("CloudSaveManager", "saveToCloud aborted: Not authenticated.")
                return
            }

            val playersClient = PlayGames.getPlayersClient(activity)
            val currentPlayerId = try {
                Tasks.await(playersClient.currentPlayerId)
            } catch (e: Exception) {
                null
            }

            // Guard: do not save data belonging to a different player into current player snapshot
            if (currentPlayerId != null && prefsManager.lastPlayerId != null && currentPlayerId != prefsManager.lastPlayerId) {
                Log.w("CloudSaveManager", "saveToCloud aborted: Player ID mismatch ($currentPlayerId vs ${prefsManager.lastPlayerId}).")
                return
            }

            val snapshotsClient = PlayGames.getSnapshotsClient(activity)
            val openTask = snapshotsClient.open(
                SNAPSHOT_NAME,
                true,
                SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED
            )
            val dataOrConflict = Tasks.await(openTask)
            val snapshot: Snapshot = dataOrConflict.data ?: run {
                Log.w("CloudSaveManager", "saveToCloud failed: Snapshot data is null.")
                return
            }

            val json = buildSaveJson(prefsManager)
            val finalHoney = prefsManager.honey

            snapshot.snapshotContents.writeBytes(json.toString().toByteArray(Charsets.UTF_8))

            val change = SnapshotMetadataChange.Builder()
                .setDescription("HoneyCombMaze Save - $finalHoney Coins")
                .build()

            Tasks.await(snapshotsClient.commitAndClose(snapshot, change))
            Log.d("CloudSaveManager", "Successfully saved $finalHoney coins & levels to Play Games Cloud for $currentPlayerId!")

            if (currentPlayerId != null) {
                savePlayerLocalCache(activity, currentPlayerId, json)
            }
        } catch (e: Exception) {
            Log.e("CloudSaveManager", "Exception during saveToCloud: ${e.message}")
        }
    }

    /**
     * Regular save - called during gameplay when state changes.
     * If cloud load hasn't completed yet, marks a pending save that will
     * be automatically executed after loadFromCloud finishes.
     * This ensures NO save is ever silently dropped.
     */
    fun saveToCloud(context: Context, prefsManager: PreferencesManager) {
        if (prefsManager.isCloudRestoreInProgress) {
            Log.d("CloudSaveManager", "saveToCloud skipped: cloud restore is in progress")
            return
        }

        // CRITICAL FIX: If cloud hasn't loaded yet, don't silently skip!
        // Instead, mark a pending save so it runs after loadFromCloud completes.
        if (!prefsManager.hasLoadedFromCloud) {
            hasPendingSave = true
            Log.d("CloudSaveManager", "saveToCloud: cloud not loaded yet, marking pending save")
            return
        }

        val activity = context.findActivity() ?: return

        cloudScope.launch {
            snapshotMutex.withLock {
                doSaveToCloudInternal(activity, prefsManager)
            }
        }
    }

    /**
     * Force save - used by lifecycle methods (onPause, onStop, onDestroy).
     * This ALWAYS attempts to save, bypassing the hasLoadedFromCloud check.
     * Ensures user progress is saved even if cloud load is still pending.
     */
    fun forceSaveToCloud(context: Context, prefsManager: PreferencesManager) {
        if (prefsManager.isCloudRestoreInProgress) {
            return
        }

        val activity = context.findActivity() ?: return

        cloudScope.launch {
            snapshotMutex.withLock {
                doSaveToCloudInternal(activity, prefsManager)
            }
        }
    }

    fun loadFromCloud(
        context: Context,
        prefsManager: PreferencesManager,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val activity = context.findActivity() ?: run {
            prefsManager.hasLoadedFromCloud = true
            if (hasPendingSave) {
                hasPendingSave = false
            }
            onComplete?.invoke(false)
            return
        }

        cloudScope.launch {
            snapshotMutex.withLock {
                try {
                    val signInClient = PlayGames.getGamesSignInClient(activity)
                    val authResult = try {
                        Tasks.await(signInClient.isAuthenticated())
                    } catch (e: Exception) {
                        null
                    }

                    if (authResult == null || !authResult.isAuthenticated) {
                        Log.w("CloudSaveManager", "loadFromCloud skipped: Not authenticated.")
                        prefsManager.hasLoadedFromCloud = true
                        flushPendingSave(activity, prefsManager)
                        withContext(Dispatchers.Main) { onComplete?.invoke(false) }
                        return@withLock
                    }

                    val playersClient = PlayGames.getPlayersClient(activity)
                    val currentPlayerId = try {
                        Tasks.await(playersClient.currentPlayerId)
                    } catch (e: Exception) {
                        null
                    }

                    val lastPlayerId = prefsManager.lastPlayerId
                    val isAccountSwitched = (lastPlayerId != null && currentPlayerId != null && lastPlayerId != currentPlayerId)

                    if (isAccountSwitched) {
                        Log.d("CloudSaveManager", "Account switch detected: $lastPlayerId -> $currentPlayerId")
                        // 1. Save old player state locally
                        val oldPlayerJson = buildSaveJson(prefsManager)
                        savePlayerLocalCache(activity, lastPlayerId, oldPlayerJson)

                        // 2. Pre-load new player cache if exists, or reset to default profile
                        val newPlayerCached = getPlayerLocalCache(activity, currentPlayerId)
                        withContext(Dispatchers.Main) {
                            if (newPlayerCached != null) {
                                prefsManager.applyProfile(newPlayerCached)
                            } else {
                                prefsManager.resetToDefaultProfile()
                            }
                        }

                        val dao = AppDatabase.getDatabase(activity.applicationContext).gameDataDao()
                        dao.deleteAllGameData()
                        for (mId in 0..10) {
                            dao.saveGameData(GameData(mId, prefsManager.getMaxUnlockedLevel(mId)))
                        }
                    }

                    if (currentPlayerId != null) {
                        prefsManager.lastPlayerId = currentPlayerId
                    }

                    val snapshotsClient = PlayGames.getSnapshotsClient(activity)
                    val openTask = snapshotsClient.open(
                        SNAPSHOT_NAME,
                        true,
                        SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED
                    )
                    val dataOrConflict = Tasks.await(openTask)
                    val snapshot: Snapshot = dataOrConflict.data ?: run {
                        Log.w("CloudSaveManager", "loadFromCloud failed: Snapshot data is null.")
                        prefsManager.hasLoadedFromCloud = true
                        flushPendingSave(activity, prefsManager)
                        withContext(Dispatchers.Main) { onComplete?.invoke(false) }
                        return@withLock
                    }

                    try {
                        prefsManager.isCloudRestoreInProgress = true
                        val bytes = snapshot.snapshotContents.readFully()

                        if (bytes.isNotEmpty()) {
                            val jsonString = String(bytes, Charsets.UTF_8)
                            val json = JSONObject(jsonString)
                            val cloudHoney = json.optInt("honey", 0)

                            if (isAccountSwitched) {
                                // Direct restore for switched account — DO NOT merge with previous account
                                withContext(Dispatchers.Main) {
                                    prefsManager.applyProfile(json)
                                }
                                val dao = AppDatabase.getDatabase(activity.applicationContext).gameDataDao()
                                dao.deleteAllGameData()
                                val modeLevels = json.optJSONObject("modeLevels")
                                for (mId in 0..10) {
                                    val lvl = modeLevels?.optInt(mId.toString(), 1) ?: 1
                                    dao.saveGameData(GameData(mId, lvl))
                                }
                                if (currentPlayerId != null) {
                                    savePlayerLocalCache(activity, currentPlayerId, json)
                                }
                                Tasks.await(snapshotsClient.discardAndClose(snapshot))
                                Log.d("CloudSaveManager", "Restored switched account ($currentPlayerId) with $cloudHoney coins from cloud.")
                            } else {
                                // SAME ACCOUNT: merge logic
                                val selectedAvatar = json.optString("selectedAvatar", "default")
                                val isRemoveAdsPurchased = json.optBoolean("isRemoveAdsPurchased", false)

                                val mergedHoney = maxOf(prefsManager.honey, cloudHoney)
                                withContext(Dispatchers.Main) {
                                    prefsManager.setHoneySilent(mergedHoney)
                                    if (selectedAvatar.isNotEmpty() && prefsManager.selectedAvatar == "default") {
                                        prefsManager.selectedAvatar = selectedAvatar
                                    }
                                    if (isRemoveAdsPurchased) {
                                        prefsManager.isRemoveAdsPurchased = true
                                    }
                                }

                                val unlockedModes = json.optJSONArray("unlockedModes")
                                if (unlockedModes != null) {
                                    for (i in 0 until unlockedModes.length()) {
                                        val mId = unlockedModes.getInt(i)
                                        if (mId != 9 && mId != 4) {
                                            prefsManager.unlockMode(mId, syncCloud = false)
                                        }
                                    }
                                }

                                val unlockedAvatars = json.optJSONArray("unlockedAvatars")
                                if (unlockedAvatars != null) {
                                    for (i in 0 until unlockedAvatars.length()) {
                                        prefsManager.unlockAvatar(unlockedAvatars.getString(i), syncCloud = false)
                                    }
                                }

                                val modeLevels = json.optJSONObject("modeLevels")
                                val dao = AppDatabase.getDatabase(activity.applicationContext).gameDataDao()
                                if (modeLevels != null) {
                                    for (mId in 0..10) {
                                        val cloudLvl = modeLevels.optInt(mId.toString(), 1)
                                        val localLvl = prefsManager.getMaxUnlockedLevel(mId)
                                        val maxLvl = maxOf(cloudLvl, localLvl)
                                        if (maxLvl > 1) {
                                            prefsManager.setMaxUnlockedLevel(mId, maxLvl, syncCloud = false)
                                            try {
                                                dao.saveGameData(GameData(mId, maxLvl))
                                            } catch (_: Exception) {}
                                        }
                                    }
                                }

                                val unlockedModeLevels = json.optJSONArray("unlockedModeLevels")
                                if (unlockedModeLevels != null) {
                                    for (i in 0 until unlockedModeLevels.length()) {
                                        val mId = unlockedModeLevels.getInt(i)
                                        prefsManager.setModeLevelsUnlocked(mId, true, syncCloud = false)
                                        prefsManager.setMaxUnlockedLevel(mId, 100, syncCloud = false)
                                        try {
                                            dao.saveGameData(GameData(mId, 100))
                                        } catch (_: Exception) {}
                                    }
                                }

                                if (mergedHoney > cloudHoney) {
                                    val mergedJson = buildSaveJson(prefsManager)
                                    snapshot.snapshotContents.writeBytes(mergedJson.toString().toByteArray(Charsets.UTF_8))
                                    val change = SnapshotMetadataChange.Builder()
                                        .setDescription("HoneyCombMaze Save - $mergedHoney Coins")
                                        .build()
                                    Tasks.await(snapshotsClient.commitAndClose(snapshot, change))
                                } else {
                                    Tasks.await(snapshotsClient.discardAndClose(snapshot))
                                }

                                if (currentPlayerId != null) {
                                    savePlayerLocalCache(activity, currentPlayerId, buildSaveJson(prefsManager))
                                }
                                Log.d("CloudSaveManager", "Loaded & merged $mergedHoney coins for account $currentPlayerId.")
                            }

                            withContext(Dispatchers.Main) { onComplete?.invoke(true) }
                        } else {
                            if (isAccountSwitched) {
                                // Fresh snapshot for brand new account: initialize with 0 coins and default profile
                                withContext(Dispatchers.Main) {
                                    prefsManager.resetToDefaultProfile()
                                }
                                val dao = AppDatabase.getDatabase(activity.applicationContext).gameDataDao()
                                dao.deleteAllGameData()
                                for (mId in 0..10) {
                                    dao.saveGameData(GameData(mId, 1))
                                }
                                val freshJson = buildSaveJson(prefsManager)
                                snapshot.snapshotContents.writeBytes(freshJson.toString().toByteArray(Charsets.UTF_8))
                                val change = SnapshotMetadataChange.Builder()
                                    .setDescription("HoneyCombMaze Save - 0 Coins")
                                    .build()
                                Tasks.await(snapshotsClient.commitAndClose(snapshot, change))
                                if (currentPlayerId != null) {
                                    savePlayerLocalCache(activity, currentPlayerId, freshJson)
                                }
                                Log.d("CloudSaveManager", "Initialized fresh cloud save for new account $currentPlayerId.")
                            } else {
                                // Guest user signing in for first time: save local progress
                                val json = buildSaveJson(prefsManager)
                                val currentHoney = prefsManager.honey
                                snapshot.snapshotContents.writeBytes(json.toString().toByteArray(Charsets.UTF_8))
                                val change = SnapshotMetadataChange.Builder()
                                    .setDescription("HoneyCombMaze Save - $currentHoney Coins")
                                    .build()
                                Tasks.await(snapshotsClient.commitAndClose(snapshot, change))
                                if (currentPlayerId != null) {
                                    savePlayerLocalCache(activity, currentPlayerId, json)
                                }
                                Log.d("CloudSaveManager", "Linked local progress ($currentHoney coins) to account $currentPlayerId.")
                            }
                            withContext(Dispatchers.Main) { onComplete?.invoke(true) }
                        }
                    } catch (e: Exception) {
                        Log.e("CloudSaveManager", "Error parsing cloud save data: ${e.message}")
                        withContext(Dispatchers.Main) { onComplete?.invoke(false) }
                    } finally {
                        prefsManager.isCloudRestoreInProgress = false
                        prefsManager.hasLoadedFromCloud = true
                        flushPendingSave(activity, prefsManager)
                    }
                } catch (e: Exception) {
                    Log.e("CloudSaveManager", "Exception during loadFromCloud: ${e.message}")
                    prefsManager.hasLoadedFromCloud = true
                    flushPendingSave(activity, prefsManager)
                    withContext(Dispatchers.Main) { onComplete?.invoke(false) }
                }
            }
        }
    }

    /**
     * Flush any pending save that was queued while cloud load was in progress.
     * MUST be called within snapshotMutex lock OR after releasing it (it acquires its own).
     */
    private fun flushPendingSave(activity: Activity, prefsManager: PreferencesManager) {
        if (hasPendingSave) {
            hasPendingSave = false
            Log.d("CloudSaveManager", "Flushing pending save after cloud load completed")
            // Launch a new coroutine to save — this will acquire snapshotMutex
            cloudScope.launch {
                snapshotMutex.withLock {
                    doSaveToCloudInternal(activity, prefsManager)
                }
            }
        }
    }

    fun resetCloudSave(context: Context, prefsManager: PreferencesManager) {
        val activity = context.findActivity() ?: return
        cloudScope.launch {
            snapshotMutex.withLock {
                try {
                    val snapshotsClient = PlayGames.getSnapshotsClient(activity)
                    val openTask = snapshotsClient.open(
                        SNAPSHOT_NAME,
                        true,
                        SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED
                    )
                    val dataOrConflict = Tasks.await(openTask)
                    val snapshot: Snapshot = dataOrConflict.data ?: return@withLock
                    val modeLevelsJson = JSONObject()
                    for (mId in 0..10) {
                        modeLevelsJson.put(mId.toString(), 1)
                    }
                    val json = JSONObject().apply {
                        put("honey", 0)
                        put("selectedAvatar", "default")
                        put("isRemoveAdsPurchased", false)
                        put("isAllLevelsUnlocked", false)
                        put("unlockedModes", JSONArray())
                        put("unlockedAvatars", JSONArray())
                        put("unlockedModeLevels", JSONArray())
                        put("modeLevels", modeLevelsJson)
                    }
                    snapshot.snapshotContents.writeBytes(json.toString().toByteArray(Charsets.UTF_8))
                    val change = SnapshotMetadataChange.Builder()
                        .setDescription("HoneyCombMaze Save - Reset Data")
                        .build()
                    Tasks.await(snapshotsClient.commitAndClose(snapshot, change))
                    Log.d("CloudSaveManager", "Cloud Save erased successfully!")
                } catch (e: Exception) {
                    Log.e("CloudSaveManager", "Exception resetting cloud save: ${e.message}")
                }
            }
        }
    }
}
