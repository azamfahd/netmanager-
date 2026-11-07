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
    val vlanId: Int? = null,
    val parentInterface: String? = null,
    val lastSeenTime: Long = System.currentTimeMillis(),
    val lastCheckTime: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromNetworkNode(node: com.example.data.model.NetworkNode): NetworkNodeEntity {
            return NetworkNodeEntity(
                id = node.id,
                name = node.name,
                ipAddress = node.ipAddress,
                macAddress = node.macAddress,
                interfaceName = node.interfaceName,
                model = node.model,
                role = node.role,
                nodeType = node.nodeType.name,
                status = node.effectiveStatus.name,
                parentTowerId = node.parentTowerId,
                parentTowerName = node.parentTowerName,
                isOnline = node.isOnline,
                pingMs = node.pingMs,
                connectedUsers = node.connectedUsers,
                uptime = node.uptime,
                rxSpeedKbps = node.rxSpeedKbps,
                txSpeedKbps = node.txSpeedKbps,
                totalBytesConsumed = node.totalBytesConsumed,
                signalDbm = node.signalDbm,
                location = node.location,
                cpuLoadPercent = node.cpuLoadPercent,
                memoryUsagePercent = node.memoryUsagePercent,
                temperature = node.temperature,
                voltage = node.voltage,
                frequency = node.frequency,
                channelWidth = node.channelWidth,
                ssid = node.ssid,
                firmwareVersion = node.firmwareVersion,
                vlanId = node.vlanId,
                parentInterface = node.parentInterface,
                lastSeenTime = node.lastSeenTime,
                lastCheckTime = node.lastCheckTime
            )
        }
    }

    fun toNetworkNode(): com.example.data.model.NetworkNode {
        return com.example.data.model.NetworkNode(
            id = id,
            name = name,
            ipAddress = ipAddress,
            macAddress = macAddress,
            interfaceName = interfaceName,
            model = model,
            role = role,
            nodeType = try { com.example.data.model.NetworkNodeType.valueOf(nodeType) } catch (_: Exception) { com.example.data.model.NetworkNodeType.DISTRIBUTION },
            status = try { com.example.data.model.NodeStatus.valueOf(status) } catch (_: Exception) { com.example.data.model.NodeStatus.ACTIVE },
            parentTowerId = parentTowerId,
            parentTowerName = parentTowerName,
            isOnline = isOnline,
            pingMs = pingMs,
            connectedUsers = connectedUsers,
            uptime = uptime,
            rxSpeedKbps = rxSpeedKbps,
            txSpeedKbps = txSpeedKbps,
            totalBytesConsumed = totalBytesConsumed,
            signalDbm = signalDbm,
            location = location,
            cpuLoadPercent = cpuLoadPercent,
            memoryUsagePercent = memoryUsagePercent,
            temperature = temperature,
            voltage = voltage,
            frequency = frequency,
            channelWidth = channelWidth,
            ssid = ssid,
            vlanId = vlanId,
            parentInterface = parentInterface,
            lastSeenTime = lastSeenTime,
            lastCheckTime = lastCheckTime
        )
    }
}
