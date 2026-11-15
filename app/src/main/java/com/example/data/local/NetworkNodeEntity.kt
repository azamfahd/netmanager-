package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "network_nodes")
data class NetworkNodeEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val ipAddress: String,
    val macAddress: String = "",
    val interfaceName: String = "",
    val model: String = "",
    val role: String = "أكسس بوينت (بث)",
    val nodeType: String = "DISTRIBUTION", // MAIN_TOWER, DISTRIBUTION, HOME_NODE
    val status: String = "ACTIVE", // ACTIVE, OFFLINE, NO_INTERNET, DISABLED
    val parentTowerId: String? = null,
    val parentTowerName: String? = null,
    val isOnline: Boolean = true,
    val pingMs: Long = 0L,
    val connectedUsers: Int = 0,
    val uptime: String = "",
    val rxSpeedKbps: Double = 0.0,
    val txSpeedKbps: Double = 0.0,
    val totalBytesConsumed: Long = 0L,
    val signalDbm: String = "",
    val location: String = "",
    val cpuLoadPercent: Int = 0,
    val memoryUsagePercent: Int = 0,
    val temperature: String = "",
    val voltage: String = "",
    val frequency: String = "",
    val channelWidth: String = "",
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
