package com.opscalehub.slim

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AppRepository(
    private val context: Context,
    private val appDao: AppDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    val allAppsFlow: Flow<List<AppItem>> = appDao.getAllApps()
    val favoritesFlow: Flow<List<AppItem>> = appDao.getFavorites()

    suspend fun refreshApps() = withContext(ioDispatcher) {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userHandle = Process.myUserHandle()
        val activityList = launcherApps.getActivityList(null, userHandle)

        val cachedApps = mutableListOf<AppItem>()
        for (activity in activityList) {
            val packageName = activity.applicationInfo.packageName
            val className = activity.name
            val label = activity.label.toString()

            cachedApps.add(
                AppItem(
                    packageName = packageName,
                    className = className,
                    label = label
                )
            )
        }

        // If Room is empty, perform seed
        if (appDao.getAppCount() == 0) {
            appDao.insertApps(cachedApps)
        } else {
            // Delete legacy apps from database that are no longer installed
            val currentPackageNames = cachedApps.map { it.packageName }.toSet()
            // In a production app, we would sync. For now, seed matching inserts:
            appDao.insertApps(cachedApps)
        }
    }

    suspend fun setAppAsFavorite(packageName: String, isFavorite: Boolean) = withContext(ioDispatcher) {
        appDao.setFavorite(packageName, isFavorite)
    }

    suspend fun recordAppLaunch(packageName: String) = withContext(ioDispatcher) {
        appDao.incrementLaunchCount(packageName, System.currentTimeMillis())
    }

    suspend fun handlePackageRemoved(packageName: String) = withContext(ioDispatcher) {
        appDao.deletePackage(packageName)
    }

    fun launchApp(appItem: AppItem) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(appItem.packageName, appItem.className)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        context.startActivity(intent)
    }
}
