package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monthly_profit_records")
data class MonthlyProfitRecordEntity(
    @PrimaryKey
    val monthKey: String, // e.g. "2026-10"
    val monthNameAr: String,
    val year: Int,
    val month: Int,
    val totalRevenue: Double = 0.0,
    val totalCost: Double = 0.0,
    val netProfit: Double = 0.0,
    val soldCardsCount: Int = 0,
    val totalCardsCount: Int = 0,
    val topSellingProfile: String = "",
    val totalBytesConsumed: Long = 0L,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)
