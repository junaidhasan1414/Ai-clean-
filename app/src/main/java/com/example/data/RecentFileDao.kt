package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentFileDao {
    @Query("SELECT * FROM recent_files ORDER BY timestamp DESC")
    fun getAllRecentFiles(): Flow<List<RecentFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentFile(recentFile: RecentFile): Long

    @Query("DELETE FROM recent_files WHERE id = :id")
    suspend fun deleteRecentFileById(id: Long)

    @Query("DELETE FROM recent_files")
    suspend fun clearAll()
}
