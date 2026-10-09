package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {

    @Query("SELECT * FROM sana_routines ORDER BY id ASC")
    fun getAllRoutinesFlow(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM sana_routines ORDER BY id ASC")
    suspend fun getAllRoutines(): List<RoutineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Update
    suspend fun updateRoutine(routine: RoutineEntity)

    @Query("UPDATE sana_routines SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun toggleRoutine(id: Long, isEnabled: Boolean)

    @Query("UPDATE sana_routines SET lastRunTimestamp = :timestamp, lastRunStatus = :status WHERE id = :id")
    suspend fun updateLastRun(id: Long, timestamp: Long, status: String)

    @Query("DELETE FROM sana_routines WHERE id = :id")
    suspend fun deleteRoutineById(id: Long)

    @Query("SELECT COUNT(*) FROM sana_routines")
    suspend fun getCount(): Int
}
