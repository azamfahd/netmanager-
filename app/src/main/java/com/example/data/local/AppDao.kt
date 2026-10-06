package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM saved_routers ORDER BY lastConnectedTime DESC")
    fun getAllSavedRouters(): Flow<List<SavedRouterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedRouter(router: SavedRouterEntity): Long

    @Query("DELETE FROM saved_routers WHERE id = :id")
    suspend fun deleteSavedRouter(id: Long)

    @Query("SELECT * FROM batch_history ORDER BY createdAt DESC")
    fun getAllBatches(): Flow<List<BatchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(batch: BatchHistoryEntity)

    @Query("DELETE FROM batch_history WHERE batchId = :batchId")
    suspend fun deleteBatch(batchId: String)
}
