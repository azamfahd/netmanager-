package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_login_history")
data class UserLoginHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val username: String,
    val loginTime: String = "",
    val logoutTime: String = "متصل الآن",
    val duration: String = "0s",
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val totalBytes: Long = 0L,
    val ipAddress: String = "",
    val macAddress: String = "",
    val deviceModel: String = "",
    val deviceBrand: String = "",
    val deviceCategory: String = "",
    val nodeName: String = "",
    val profile: String = "",
    val price: String = "",
    val terminateReason: String = "جلسة نشطة",
    val timestamp: Long = System.currentTimeMillis()
)
