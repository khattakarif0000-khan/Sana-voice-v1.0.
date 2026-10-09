package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM sana_memories ORDER BY timestamp DESC")
    fun getAllMemoriesFlow(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM sana_memories ORDER BY timestamp DESC")
    suspend fun getAllMemories(): List<MemoryEntity>

    @Query("SELECT * FROM sana_memories WHERE content LIKE '%' || :query || '%'")
    suspend fun searchMemories(query: String): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Query("SELECT * FROM sana_memories WHERE category = :category LIMIT 1")
    suspend fun getMemoryByCategory(category: String): MemoryEntity?

    @Query("UPDATE sana_memories SET content = :content, timestamp = :timestamp WHERE id = :id")
    suspend fun updateMemory(id: Long, content: String, timestamp: Long)

    @Query("DELETE FROM sana_memories WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    @Query("DELETE FROM sana_memories WHERE content LIKE '%' || :keyword || '%'")
    suspend fun deleteMemoriesByKeyword(keyword: String): Int

    @Query("DELETE FROM sana_memories")
    suspend fun clearAllMemories()
}
