package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_files")
data class RecentFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val mediaType: String, // "IMAGE" or "VIDEO"
    val timestamp: Long = System.currentTimeMillis()
)
