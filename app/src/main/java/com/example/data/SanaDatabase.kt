package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MemoryEntity::class, RoutineEntity::class], version = 2, exportSchema = false)
abstract class SanaDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun routineDao(): RoutineDao

    companion object {
        @Volatile
        private var INSTANCE: SanaDatabase? = null

        fun getInstance(context: Context): SanaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SanaDatabase::class.java,
                    "sana_vault.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
