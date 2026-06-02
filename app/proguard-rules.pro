# Proguard rules for Slim Launcher
# Add project specific rules here.

# Keep Room database classes
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * {
    @androidx.room.Database *;
    @androidx.room.Dao *;
    @androidx.room.Entity *;
}
