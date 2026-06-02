package com.opscalehub.slim

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_apps")
data class AppItem(
    @PrimaryKey val packageName: String,
    val className: String,
    val label: String,
    val isFavorite: Boolean = false,
    val launchCount: Int = 0,
    val lastTimeUsed: Long = 0L,
    val customTag: String? = null
)
