package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sana_routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val triggerDescription: String,
    val actionCommand: String,
    val isEnabled: Boolean = true,
    val lastRunTimestamp: Long = 0L,
    val lastRunStatus: String = "Ready"
)
