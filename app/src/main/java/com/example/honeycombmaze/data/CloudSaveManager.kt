package com.example.honeycombmaze.data

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import org.json.JSONObject

fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

object CloudSaveManager {
    private const val SNAPSHOT_NAME = "HoneyMazeSaveV2"
    private var isSdkInitialized = false
    private var isSignInInProgress = false
    private var hasAttemptedInitialSignIn = false

    fun initializeAndSignIn(context: Context, onSignedIn: (() -> Unit)? = null) {
        val activity = context.findActivity() ?: return
        if (isSignInInProgress) return

        try {
            if (!isSdkInitialized) {
                PlayGamesSdk.initialize(activity)
                isSdkInitialized = true
            }

            val gamesSignInClient = PlayGames.getGamesSignInClient(activity)
            
            // Check if user is already authenticated without popping up dialogs
            gamesSignInClient.isAuthenticated.addOnCompleteListener { authTask ->
                val isAuthenticated = authTask.isSuccessful && authTask.result.isAuthenticated
                if (isAuthenticated) {
                    onSignedIn?.invoke()
                } else if (!hasAttemptedInitialSignIn) {
                    // Only attempt interactive sign in ONCE on initial launch
                    hasAttemptedInitialSignIn = true
                    isSignInInProgress = true
                    gamesSignInClient.signIn().addOnCompleteListener { signInTask ->
                        isSignInInProgress = false
                        if (signInTask.isSuccessful && signInTask.result.isAuthenticated) {
                            onSignedIn?.invoke()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            isSignInInProgress = false
            Log.w("CloudSaveManager", "Play Games sign-in skipped: ${e.message}")
        }
    }

    /**
     * Silent check called on onResume - NEVER opens popups or interactive dialogs
     */
    fun checkAuthSilentlyAndLoad(context: Context, prefsManager: PreferencesManager) {
        val activity = context.findActivity() ?: return
        if (isSignInInProgress) return

        try {
            if (!isSdkInitialized) {
                PlayGamesSdk.initialize(activity)
                isSdkInitialized = true
            }

            val gamesSignInClient = PlayGames.getGamesSignInClient(activity)
            gamesSignInClient.isAuthenticated.addOnCompleteListener { authTask ->
                if (authTask.isSuccessful && authTask.result.isAuthenticated) {
                    loadFromCloud(context, prefsManager)
                }
            }
        } catch (_: Exception) {}
    }

    fun saveToCloud(context: Context, prefsManager: PreferencesManager) {
        if (!prefsManager.hasLoadedFromCloud || prefsManager.isCloudRestoreInProgress) return
        val activity = context.findActivity() ?: return
        try {
            val snapshotsClient = PlayGames.getSnapshotsClient(activity)
            snapshotsClient.open(SNAPSHOT_NAME, true, SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED)
                .addOnSuccessListener { dataOrConflict ->
                    val snapshot = dataOrConflict.data ?: return@addOnSuccessListener
                    
                    val unlockedModesArray = org.json.JSONArray()
                    for (m in 0..10) {
                        if (prefsManager.isModeUnlocked(m)) unlockedModesArray.put(m)
                    }
                    val unlockedAvatarsArray = org.json.JSONArray()
                    for (av in AvatarRegistry.AVATARS) {
                        if (prefsManager.isAvatarUnlocked(av.id)) unlockedAvatarsArray.put(av.id)
                    }

                    val unlockedModeLevelsArray = org.json.JSONArray()
                    for (mId in 0..10) {
                        if (prefsManager.isModeLevelsUnlocked(mId)) unlockedModeLevelsArray.put(mId)
                    }

                    val modeLevelsJson = JSONObject()
                    for (mId in 0..10) {
                        modeLevelsJson.put(mId.toString(), prefsManager.getMaxUnlockedLevel(mId))
                    }

                    val finalHoney = prefsManager.honey
                    val json = JSONObject().apply {
                        put("honey", finalHoney)
                        put("selectedAvatar", prefsManager.selectedAvatar)
                        put("isRemoveAdsPurchased", prefsManager.isRemoveAdsPurchased)
                        put("isAllLevelsUnlocked", false)
                        put("unlockedModes", unlockedModesArray)
                        put("unlockedAvatars", unlockedAvatarsArray)
                        put("unlockedModeLevels", unlockedModeLevelsArray)
                        put("modeLevels", modeLevelsJson)
                    }

                    snapshot.snapshotContents.writeBytes(json.toString().toByteArray(Charsets.UTF_8))

                    val change = SnapshotMetadataChange.Builder()
                        .setDescription("HoneyCombMaze Save - $finalHoney Coins")
                        .build()

                    snapshotsClient.commitAndClose(snapshot, change)
                        .addOnSuccessListener {
                            Log.d("CloudSaveManager", "Successfully saved $finalHoney coins to Play Games Cloud!")
                        }
                }
                .addOnFailureListener { e ->
                    Log.w("CloudSaveManager", "Cloud save skipped or failed: ${e.message}")
                }
        } catch (e: Exception) {
            Log.w("CloudSaveManager", "Exception during saveToCloud: ${e.message}")
        }
    }

    fun loadFromCloud(context: Context, prefsManager: PreferencesManager, onComplete: ((Boolean) -> Unit)? = null) {
        val activity = context.findActivity() ?: run {
            prefsManager.hasLoadedFromCloud = true
            onComplete?.invoke(false)
            return
        }
        try {
            val snapshotsClient = PlayGames.getSnapshotsClient(activity)
            snapshotsClient.open(SNAPSHOT_NAME, true, SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED)
                .addOnSuccessListener { dataOrConflict ->
                    val snapshot = dataOrConflict.data ?: run {
                        prefsManager.hasLoadedFromCloud = true
                        onComplete?.invoke(false)
                        return@addOnSuccessListener
                    }

                    try {
                        prefsManager.isCloudRestoreInProgress = true
                        val bytes = snapshot.snapshotContents.readFully()
                        if (bytes.isNotEmpty()) {
                            val jsonString = String(bytes, Charsets.UTF_8)
                            val json = JSONObject(jsonString)
                            val cloudHoney = json.optInt("honey", 0)
                            val selectedAvatar = json.optString("selectedAvatar", "default")
                            val isRemoveAdsPurchased = json.optBoolean("isRemoveAdsPurchased", false)
                            val isAllLevelsUnlocked = json.optBoolean("isAllLevelsUnlocked", false)

                            prefsManager.setHoneySilent(cloudHoney)

                            if (selectedAvatar.isNotEmpty()) {
                                prefsManager.selectedAvatar = selectedAvatar
                            }

                            val unlockedModes = json.optJSONArray("unlockedModes")
                            if (unlockedModes != null) {
                                for (i in 0 until unlockedModes.length()) {
                                    prefsManager.unlockMode(unlockedModes.getInt(i), syncCloud = false)
                                }
                            }
                            val unlockedAvatars = json.optJSONArray("unlockedAvatars")
                            if (unlockedAvatars != null) {
                                for (i in 0 until unlockedAvatars.length()) {
                                    prefsManager.unlockAvatar(unlockedAvatars.getString(i), syncCloud = false)
                                }
                            }
                            val modeLevels = json.optJSONObject("modeLevels")
                            if (modeLevels != null) {
                                for (mId in 0..10) {
                                    val lvl = modeLevels.optInt(mId.toString(), 1)
                                    if (lvl > 1) {
                                        prefsManager.setMaxUnlockedLevel(mId, lvl, syncCloud = false)
                                    }
                                }
                            }
                            val unlockedModeLevels = json.optJSONArray("unlockedModeLevels")
                            if (unlockedModeLevels != null) {
                                for (i in 0 until unlockedModeLevels.length()) {
                                    val mId = unlockedModeLevels.getInt(i)
                                    prefsManager.setModeLevelsUnlocked(mId, true, syncCloud = false)
                                    prefsManager.setMaxUnlockedLevel(mId, 100, syncCloud = false)
                                }
                            }

                            onComplete?.invoke(true)
                        } else {
                            onComplete?.invoke(false)
                        }
                    } catch (e: Exception) {
                        Log.e("CloudSaveManager", "Error parsing cloud save data: ${e.message}")
                        onComplete?.invoke(false)
                    } finally {
                        prefsManager.isCloudRestoreInProgress = false
                        prefsManager.hasLoadedFromCloud = true
                        snapshotsClient.discardAndClose(snapshot)
                    }
                }
                .addOnFailureListener { e ->
                    Log.w("CloudSaveManager", "Cloud load skipped: ${e.message}")
                    prefsManager.hasLoadedFromCloud = true
                    onComplete?.invoke(false)
                }
        } catch (e: Exception) {
            Log.w("CloudSaveManager", "Exception during loadFromCloud: ${e.message}")
            prefsManager.hasLoadedFromCloud = true
            onComplete?.invoke(false)
        }
    }

    fun resetCloudSave(context: Context, prefsManager: PreferencesManager) {
        val activity = context.findActivity() ?: return
        try {
            val snapshotsClient = PlayGames.getSnapshotsClient(activity)
            snapshotsClient.open(SNAPSHOT_NAME, true, SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED)
                .addOnSuccessListener { dataOrConflict ->
                    val snapshot = dataOrConflict.data ?: return@addOnSuccessListener
                    val modeLevelsJson = JSONObject()
                    for (mId in 0..10) {
                        modeLevelsJson.put(mId.toString(), 1)
                    }
                    val json = JSONObject().apply {
                        put("honey", 0)
                        put("selectedAvatar", "default")
                        put("isRemoveAdsPurchased", false)
                        put("isAllLevelsUnlocked", false)
                        put("unlockedModes", org.json.JSONArray())
                        put("unlockedAvatars", org.json.JSONArray())
                        put("unlockedModeLevels", org.json.JSONArray())
                        put("modeLevels", modeLevelsJson)
                    }
                    snapshot.snapshotContents.writeBytes(json.toString().toByteArray(Charsets.UTF_8))
                    val change = SnapshotMetadataChange.Builder()
                        .setDescription("HoneyCombMaze Save - Reset Data")
                        .build()
                    snapshotsClient.commitAndClose(snapshot, change)
                        .addOnSuccessListener {
                            Log.d("CloudSaveManager", "Cloud Save erased successfully!")
                        }
                }
        } catch (e: Exception) {
            Log.e("CloudSaveManager", "Exception resetting cloud save: ${e.message}")
        }
    }
}
