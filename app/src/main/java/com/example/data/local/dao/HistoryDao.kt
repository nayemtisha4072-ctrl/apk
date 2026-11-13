package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.CalculationHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM calculation_history ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<CalculationHistoryEntity>>

    @Query(
        """
        SELECT * FROM calculation_history
        WHERE expression LIKE '%' || :query || '%'
           OR result LIKE '%' || :query || '%'
        ORDER BY createdAtMillis DESC
        """
    )
    fun search(query: String): Flow<List<CalculationHistoryEntity>>

    @Query("SELECT * FROM calculation_history ORDER BY createdAtMillis DESC")
    suspend fun getAllOnce(): List<CalculationHistoryEntity>

    @Query("SELECT * FROM calculation_history ORDER BY createdAtMillis DESC LIMIT 1")
    suspend fun getLatest(): CalculationHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CalculationHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<CalculationHistoryEntity>)

    @Query("DELETE FROM calculation_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM calculation_history")
    suspend fun clearAll()
}
