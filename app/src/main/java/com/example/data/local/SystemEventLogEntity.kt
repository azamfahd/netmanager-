package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity to persist System Event Logs for all Add, Edit, Delete, and Control operations
 * performed on the MikroTik router or local system.
 */
@Entity(tableName = "system_event_logs")
data class SystemEventLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String, // "إضافة", "تعديل", "حذف", "إعادة تشغيل", "طرد"
    val targetType: String, // "كرت", "دفعة كروت", "باقة", "راوتر", "جلسة", "أسعار", "نقطة بيع"
    val targetName: String, // e.g. Card name, Profile name, Batch ID
    val description: String, // Full Arabic human-readable description
    val operatorUsername: String = "admin",
    val routerIp: String = "",
    val status: String = "SUCCESS", // "SUCCESS", "FAILED", "WARNING"
    val details: String = "" // Extra parameters, error messages, or payload info
)
