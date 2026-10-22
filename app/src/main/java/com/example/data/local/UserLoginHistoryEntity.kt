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

fun UserLoginHistoryEntity.toUserSessionRecord(): com.example.data.model.UserSessionRecord {
    val category = try {
        com.example.data.model.DeviceCategory.valueOf(deviceCategory)
    } catch (_: Exception) {
        com.example.data.model.DeviceCategory.SMART_DEVICE
    }
    return com.example.data.model.UserSessionRecord(
        id = "hist_$id",
        username = username,
        ipAddress = ipAddress,
        macAddress = macAddress,
        isOnline = logoutTime == "متصل الآن",
        loginTime = loginTime,
        logoutTime = logoutTime,
        duration = duration,
        bytesIn = bytesIn,
        bytesOut = bytesOut,
        totalBytes = totalBytes,
        terminateReason = terminateReason,
        profile = profile,
        price = price,
        deviceInfo = com.example.data.model.DeviceIdentification(
            brand = deviceBrand.ifBlank { "غير محدد" },
            modelName = deviceModel.ifBlank { "جهاز مستخدم" },
            category = category
        ),
        nodeName = nodeName,
        packageLimitBytes = 0L,
        isQuotaExhausted = terminateReason.contains("الرصيد") || terminateReason.contains("البيانات")
    )
}

