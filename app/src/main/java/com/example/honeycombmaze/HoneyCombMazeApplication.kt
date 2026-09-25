package com.example.honeycombmaze

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.google.android.gms.games.PlayGamesSdk
import java.lang.ref.WeakReference

class HoneyCombMazeApplication : Application() {
    companion object {
        private var currentActivityRef: WeakReference<Activity>? = null

        val currentActivity: Activity?
            get() = currentActivityRef?.get()
    }

    override fun onCreate() {
        super.onCreate()
        try {
            PlayGamesSdk.initialize(this)
            Log.d("HoneyCombMazeApp", "PlayGamesSdk initialized in Application.onCreate")
        } catch (e: Exception) {
            Log.e("HoneyCombMazeApp", "Error initializing PlayGamesSdk: ${e.message}")
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                currentActivityRef = WeakReference(activity)
            }

            override fun onActivityStarted(activity: Activity) {
                currentActivityRef = WeakReference(activity)
            }

            override fun onActivityResumed(activity: Activity) {
                currentActivityRef = WeakReference(activity)
            }

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {}

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivityRef?.get() == activity) {
                    currentActivityRef = null
                }
            }
        })
    }
}
