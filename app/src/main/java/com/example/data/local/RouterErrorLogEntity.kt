package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "router_error_logs")
data class RouterErrorLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val operation: String, // e.g. "CONNECT", "GENERATE_BATCH", "ADD_CARD"
    val errorMessage: String,
    val details: String = ""
)
