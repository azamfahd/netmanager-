package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_routers")
data class SavedRouterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val ipAddress: String,
    val port: Int = 80,
    val username: String,
    val password: String = "",
    val useHttps: Boolean = false,
    val lastConnectedTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "batch_history")
data class BatchHistoryEntity(
    @PrimaryKey
    val batchId: String,
    val name: String,
    val profileName: String,
    val cardCount: Int,
    val price: String,
    val createdAt: Long = System.currentTimeMillis(),
    val cardsJson: String = "" // Serialized cards summary
)
