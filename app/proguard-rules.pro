# ProGuard / R8 Optimization Rules for Honey Maze: Brain Puzzle

# 1. Google Play In-App Billing
-keep class com.android.billingclient.api.** { *; }
-keep interface com.android.billingclient.api.** { *; }

# 2. Google Play Games Services & Authentication
-keep class com.google.android.gms.games.** { *; }
-keep interface com.google.android.gms.games.** { *; }
-keep class com.google.android.gms.auth.** { *; }
-keep interface com.google.android.gms.auth.** { *; }



# 4. AndroidX Room Database & App Data Models
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class com.example.honeycombmaze.data.** { *; }
-keepclassmembers class * {
    @androidx.room.Dao *;
    @androidx.room.Entity *;
}

# 5. Org JSON & Gson Serialization
-keep class org.json.** { *; }
-keep class com.google.gson.** { *; }

# 6. Strip remaining android.util.Log calls in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
