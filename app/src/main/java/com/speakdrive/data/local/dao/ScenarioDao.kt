package com.speakdrive.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.speakdrive.data.local.entity.CustomScenarioEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScenarioDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scenario: CustomScenarioEntity)

    @Query("SELECT * FROM custom_scenarios ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CustomScenarioEntity>>

    @Query("SELECT * FROM custom_scenarios ORDER BY createdAt DESC")
    suspend fun getAll(): List<CustomScenarioEntity>

    @Query("SELECT * FROM custom_scenarios WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CustomScenarioEntity?

    @Query("DELETE FROM custom_scenarios WHERE id = :id")
    suspend fun deleteById(id: String)
}
