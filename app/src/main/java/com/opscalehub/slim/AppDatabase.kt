package com.opscalehub.slim

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM cached_apps ORDER BY label COLLATE NOCASE ASC")
    fun getAllApps(): Flow<List<AppItem>>

    @Query("SELECT * FROM cached_apps WHERE isFavorite = 1 ORDER BY label COLLATE NOCASE ASC")
    fun getFavorites(): Flow<List<AppItem>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertApps(apps: List<AppItem>)

    @Query("UPDATE cached_apps SET isFavorite = :isFav WHERE packageName = :packageName")
    suspend fun setFavorite(packageName: String, isFav: Boolean)

    @Query("UPDATE cached_apps SET launchCount = launchCount + 1, lastTimeUsed = :timestamp WHERE packageName = :packageName")
    suspend fun incrementLaunchCount(packageName: String, timestamp: Long)

    @Query("DELETE FROM cached_apps WHERE packageName = :packageName")
    suspend fun deletePackage(packageName: String)

    @Query("SELECT COUNT(*) FROM cached_apps")
    suspend fun getAppCount(): Int
}

@Database(entities = [AppItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "slim_launcher_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
