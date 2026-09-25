# ProGuard / R8 Optimization Rules for Honey Maze: Brain Puzzle

# 1. Google Play In-App Billing
-keep class com.android.billingclient.api.** { *; }
-keep interface com.android.billingclient.api.** { *; }
-dontwarn com.android.billingclient.**

# 2. Google Play Games Services & Authentication — COMPREHENSIVE
-keep class com.google.android.gms.games.** { *; }
-keep interface com.google.android.gms.games.** { *; }
-keep class com.google.android.gms.auth.** { *; }
-keep interface com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-keep interface com.google.android.gms.tasks.** { *; }
-keep class com.google.android.gms.common.** { *; }
-keep interface com.google.android.gms.common.** { *; }
-keep class com.google.android.gms.internal.** { *; }
-dontwarn com.google.android.gms.**

# 3. Google Mobile Ads (AdMob)
-keep class com.google.android.gms.ads.** { *; }
-keep interface com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# 4. AndroidX Room Database & App Data Models
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * extends androidx.room.RoomDatabase {
    void <init>();
}
-keep class **_Impl {
    public <init>();
}
-keep class com.example.honeycombmaze.data.** { *; }
-keepclassmembers class * {
    @androidx.room.Dao *;
    @androidx.room.Entity *;
}

# AndroidX WorkManager & Startup
-keep class androidx.work.** { *; }
-keep class androidx.startup.** { *; }

# 5. Org JSON & Gson Serialization
-keep class org.json.** { *; }
-keep class com.google.gson.** { *; }

# 6. Keep our Application class and all activities
-keep class com.example.honeycombmaze.HoneyCombMazeApplication { *; }
-keep class com.example.honeycombmaze.MainActivity { *; }

# 7. Kotlin Coroutines — CRITICAL for production
# R8 can strip coroutine internals needed for Dispatchers.IO + suspend functions
-keepclassmembers class kotlinx.coroutines.** { *; }
-keep class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }
-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }
-dontwarn kotlinx.coroutines.**

# 8. Keep Kotlin metadata for reflection-based APIs
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# 9. Keep Compose runtime internals
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# 10. Strip Log calls in release builds (safe — does not affect any logic)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
