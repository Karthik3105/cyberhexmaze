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
    private const val SNAPSHOT_NAME = "HoneyCombMazeCloudSave"

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

    fun initializeAndSignIn(context: Context, onSignedIn: (() -> Unit)? = null) {
        val activity = context.findActivity() ?: return
        try {
            PlayGamesSdk.initialize(activity)
            val gamesSignInClient = PlayGames.getGamesSignInClient(activity)
            gamesSignInClient.signIn().addOnCompleteListener { signInTask ->
                if (signInTask.isSuccessful && signInTask.result.isAuthenticated) {
                    Log.d("CloudSaveManager", "Play Games sign in successful!")
                    onSignedIn?.invoke()
                } else {
                    gamesSignInClient.isAuthenticated().addOnCompleteListener { isAuthenticatedTask ->
                        val isAuthenticated = isAuthenticatedTask.isSuccessful && isAuthenticatedTask.result.isAuthenticated
                        if (isAuthenticated) {
                            Log.d("CloudSaveManager", "User authenticated with Play Games Services!")
                            onSignedIn?.invoke()
                        } else {
                            Log.w("CloudSaveManager", "Play Games sign in not completed.")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CloudSaveManager", "Exception in initializeAndSignIn: ${e.message}")
        }
    }

    fun saveToCloud(context: Context, prefsManager: PreferencesManager) {
        if (!prefsManager.hasLoadedFromCloud || prefsManager.isCloudRestoreInProgress) return
        val activity = context.findActivity() ?: run {
            Log.e("CloudSaveManager", "saveToCloud failed: Activity context not found.")
            return
        }
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
            Log.e("CloudSaveManager", "Exception during saveToCloud: ${e.message}")
        }
    }

    fun loadFromCloud(context: Context, prefsManager: PreferencesManager, onComplete: ((Boolean) -> Unit)? = null) {
        val activity = context.findActivity() ?: run {
            Log.e("CloudSaveManager", "loadFromCloud failed: Activity context not found.")
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
                            if (isAllLevelsUnlocked) {
                                prefsManager.isAllLevelsUnlocked = true
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

                            Log.d("CloudSaveManager", "Successfully loaded $cloudHoney coins and unlocked content from Play Games Cloud!")
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                android.widget.Toast.makeText(context, "☁️ Cloud Save Restored: $cloudHoney Coins!", android.widget.Toast.LENGTH_LONG).show()
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
            Log.e("CloudSaveManager", "Exception during loadFromCloud: ${e.message}")
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
