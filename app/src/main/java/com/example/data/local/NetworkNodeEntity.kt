package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "network_nodes")
data class NetworkNodeEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val ipAddress: String,
    val macAddress: String = "64:D1:54:3B:88:12",
    val interfaceName: String = "ether2",
    val model: String = "MikroTik cAP ac",
    val role: String = "أكسس بوينت (بث)",
    val nodeType: String = "DISTRIBUTION", // MAIN_TOWER, DISTRIBUTION, HOME_NODE
    val status: String = "ACTIVE", // ACTIVE, OFFLINE, NO_INTERNET, DISABLED
    val parentTowerId: String? = null,
    val parentTowerName: String? = null,
    val isOnline: Boolean = true,
    val pingMs: Long = 4L,
    val connectedUsers: Int = 12,
    val uptime: String = "4d 12h",
    val rxSpeedKbps: Double = 4200.0,
    val txSpeedKbps: Double = 1650.0,
    val totalBytesConsumed: Long = 28_500_000_000L,
    val signalDbm: String = "-58 dBm",
    val location: String = "البرج الرئيسي",
    val cpuLoadPercent: Int = 22,
    val memoryUsagePercent: Int = 35,
    val temperature: String = "43°C",
    val voltage: String = "24.2 V",
    val frequency: String = "5 GHz",
    val channelWidth: String = "20/40/80 MHz",
    val ssid: String = "",
    val firmwareVersion: String = "",
    val lastSeenTime: Long = System.currentTimeMillis(),
    val lastCheckTime: Long = System.currentTimeMillis()
)
