package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.DeviceCategory
import com.example.data.model.DeviceIdentification
import com.example.data.model.UserSessionRecord

@Entity(tableName = "session_archive")
data class SessionArchiveEntity(
    @PrimaryKey
    val sessionId: String,
    val username: String,
    val ipAddress: String = "",
    val macAddress: String = "",
    val isOnline: Boolean = true,
    val loginTime: String = "",
    val logoutTime: String = "",
    val duration: String = "",
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val totalBytes: Long = 0L,
    val terminateReason: String = "",
    val profile: String = "",
    val price: String = "",
    val nodeName: String = "",
    val deviceModel: String = "",
    val deviceBrand: String = "",
    val deviceCategory: String = "",
    val packageLimitBytes: Long = 0L,
    val isQuotaExhausted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

fun SessionArchiveEntity.toUserSessionRecord(): UserSessionRecord {
    val category = try {
        DeviceCategory.valueOf(deviceCategory)
    } catch (_: Exception) {
        DeviceCategory.SMART_DEVICE
    }
    return UserSessionRecord(
        id = sessionId,
        username = username,
        ipAddress = ipAddress,
        macAddress = macAddress,
        isOnline = isOnline,
        loginTime = loginTime,
        logoutTime = logoutTime,
        duration = duration,
        bytesIn = bytesIn,
        bytesOut = bytesOut,
        totalBytes = totalBytes,
        terminateReason = terminateReason,
        profile = profile,
        price = price,
        deviceInfo = DeviceIdentification(
            brand = deviceBrand,
            modelName = deviceModel,
            category = category
        ),
        nodeName = nodeName,
        packageLimitBytes = packageLimitBytes,
        isQuotaExhausted = isQuotaExhausted
    )
}
