package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "materialized_summaries")
data class MaterializedSummaryEntity(
    @PrimaryKey
    val id: String = "global_summary",
    val routerIp: String = "",
    val totalEstimatedRevenue: Double = 0.0,
    val totalNetProfit: Double = 0.0,
    val activeCardsCount: Int = 0,
    val unusedCardsCount: Int = 0,
    val expiredCardsCount: Int = 0,
    val totalCardsCount: Int = 0,
    val totalActiveUsers: Int = 0,
    val lastSyncTimestamp: Long = System.currentTimeMillis()
)
