package com.example.data.local

import android.content.Context
import com.example.data.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RouterCacheSummary(
    val cardsCount: Int,
    val profilesCount: Int,
    val formattedTime: String
)

data class RouterSnapshot(
    val ipAddress: String,
    val routerName: String,
    val resource: RouterResource,
    val routerBoard: RouterBoardInfo,
    val cards: List<HotspotCard>,
    val profiles: List<HotspotProfile>,
    val activeUsers: List<HotspotActiveUser>,
    val servers: List<HotspotServerInfo>,
    val nodes: List<NetworkNode>,
    val logs: List<SystemLogEntry>,
    val timestamp: Long = System.currentTimeMillis()
)

object RouterCacheManager {
    private fun getCacheFile(context: Context, ipAddress: String): File {
        val sanitizedIp = ipAddress.replace(Regex("[^a-zA-Z0-9_]"), "_")
        // Prefer persistent internal files directory so Android OS never clears it on low memory
        val persistentFile = File(context.filesDir, "router_snapshot_$sanitizedIp.json")
        if (persistentFile.exists()) return persistentFile
        val cacheFile = File(context.cacheDir, "router_snapshot_$sanitizedIp.json")
        if (cacheFile.exists()) return cacheFile
        return persistentFile
    }

    private fun getTargetSaveFile(context: Context, ipAddress: String): File {
        val sanitizedIp = ipAddress.replace(Regex("[^a-zA-Z0-9_]"), "_")
        return File(context.filesDir, "router_snapshot_$sanitizedIp.json")
    }

    fun saveSnapshot(
        context: Context,
        ipAddress: String,
        routerName: String,
        resource: RouterResource,
        routerBoard: RouterBoardInfo,
        cards: List<HotspotCard>,
        profiles: List<HotspotProfile>,
        activeUsers: List<HotspotActiveUser>,
        servers: List<HotspotServerInfo>,
        nodes: List<NetworkNode>,
        logs: List<SystemLogEntry>
    ) {
        try {
            val root = JSONObject().apply {
                put("ipAddress", ipAddress)
                put("routerName", routerName)
                put("timestamp", System.currentTimeMillis())
                
                put("resource", JSONObject().apply {
                    put("uptime", resource.uptime)
                    put("version", resource.version)
                    put("boardName", resource.boardName)
                    put("cpuLoad", resource.cpuLoad)
                    put("freeMemoryBytes", resource.freeMemoryBytes)
                    put("totalMemoryBytes", resource.totalMemoryBytes)
                    put("architecture", resource.architecture)
                })

                put("routerBoard", JSONObject().apply {
                    put("model", routerBoard.model)
                    put("serialNumber", routerBoard.serialNumber)
                    put("currentFirmware", routerBoard.currentFirmware)
                    put("upgradeFirmware", routerBoard.upgradeFirmware)
                    put("routerboard", routerBoard.routerboard)
                })

                put("cards", JSONArray().apply {
                    cards.forEach { c ->
                        put(JSONObject().apply {
                            put("id", c.id)
                            put("name", c.name)
                            put("password", c.password)
                            put("profile", c.profile)
                            put("uptime", c.uptime)
                            put("bytesIn", c.bytesIn)
                            put("bytesOut", c.bytesOut)
                            put("limitUptime", c.limitUptime)
                            put("limitBytesTotal", c.limitBytesTotal)
                            put("comment", c.comment)
                            put("disabled", c.disabled)
                            put("price", c.price)
                        })
                    }
                })

                put("profiles", JSONArray().apply {
                    profiles.forEach { p ->
                        put(JSONObject().apply {
                            put("id", p.id)
                            put("name", p.name)
                            put("sharedUsers", p.sharedUsers)
                            put("rateLimit", p.rateLimit)
                            put("validity", p.validity)
                            put("price", p.price)
                            put("dataLimit", p.dataLimit)
                        })
                    }
                })

                put("activeUsers", JSONArray().apply {
                    activeUsers.forEach { u ->
                        put(JSONObject().apply {
                            put("id", u.id)
                            put("user", u.user)
                            put("address", u.address)
                            put("macAddress", u.macAddress)
                            put("uptime", u.uptime)
                            put("bytesIn", u.bytesIn)
                            put("bytesOut", u.bytesOut)
                            put("hostName", u.hostName)
                            put("nodeName", u.nodeName)
                            put("serverName", u.serverName)
                            put("deviceModel", u.deviceModel)
                        })
                    }
                })

                put("servers", JSONArray().apply {
                    servers.forEach { s ->
                        put(JSONObject().apply {
                            put("id", s.id)
                            put("name", s.name)
                            put("interfaceName", s.interfaceName)
                            put("profile", s.profile)
                            put("addressPool", s.addressPool)
                            put("addressesPerMac", s.addressesPerMac)
                            put("disabled", s.disabled)
                        })
                    }
                })

                put("nodes", JSONArray().apply {
                    nodes.forEach { n ->
                        put(JSONObject().apply {
                            put("id", n.id)
                            put("name", n.name)
                            put("ipAddress", n.ipAddress)
                            put("macAddress", n.macAddress)
                            put("interfaceName", n.interfaceName)
                            put("model", n.model)
                            put("role", n.role)
                            put("isOnline", n.isOnline)
                            put("pingMs", n.pingMs)
                            put("connectedUsers", n.connectedUsers)
                            put("uptime", n.uptime)
                            put("rxSpeedKbps", n.rxSpeedKbps)
                            put("txSpeedKbps", n.txSpeedKbps)
                            put("totalBytesConsumed", n.totalBytesConsumed)
                            put("signalDbm", n.signalDbm)
                            put("location", n.location)
                            put("cpuLoadPercent", n.cpuLoadPercent)
                            put("memoryUsagePercent", n.memoryUsagePercent)
                            put("temperature", n.temperature)
                            put("voltage", n.voltage)
                            put("frequency", n.frequency)
                            put("channelWidth", n.channelWidth)
                            put("ssid", n.ssid)
                            put("firmwareVersion", n.firmwareVersion)
                            put("lastSeenTime", n.lastSeenTime)
                        })
                    }
                })

                put("logs", JSONArray().apply {
                    logs.forEach { l ->
                        put(JSONObject().apply {
                            put("id", l.id)
                            put("time", l.time)
                            put("topics", l.topics)
                            put("message", l.message)
                        })
                    }
                })
            }

            val targetFile = getTargetSaveFile(context, ipAddress)
            val tempFile = File(context.filesDir, "${targetFile.name}.tmp")
            tempFile.writeText(root.toString())
            if (tempFile.exists()) {
                tempFile.renameTo(targetFile)
            }
        } catch (_: Exception) {}
    }

    fun loadSnapshot(context: Context, ipAddress: String): RouterSnapshot? {
        try {
            val file = getCacheFile(context, ipAddress)
            if (!file.exists()) return null
            val root = JSONObject(file.readText())

            val ip = root.optString("ipAddress", ipAddress)
            val rName = root.optString("routerName", "")
            val time = root.optLong("timestamp", System.currentTimeMillis())

            val resObj = root.optJSONObject("resource")
            val resource = RouterResource(
                uptime = resObj?.optString("uptime", "") ?: "",
                version = resObj?.optString("version", "") ?: "",
                boardName = resObj?.optString("boardName", "") ?: "",
                cpuLoad = resObj?.optInt("cpuLoad", 0) ?: 0,
                freeMemoryBytes = resObj?.optLong("freeMemoryBytes", 0L) ?: 0L,
                totalMemoryBytes = resObj?.optLong("totalMemoryBytes", 0L) ?: 0L,
                architecture = resObj?.optString("architecture", "") ?: ""
            )

            val rbObj = root.optJSONObject("routerBoard")
            val routerBoard = RouterBoardInfo(
                model = rbObj?.optString("model", "") ?: "",
                serialNumber = rbObj?.optString("serialNumber", "") ?: "",
                currentFirmware = rbObj?.optString("currentFirmware", "") ?: "",
                upgradeFirmware = rbObj?.optString("upgradeFirmware", "") ?: "",
                routerboard = rbObj?.optBoolean("routerboard", true) ?: true
            )

            val cardsList = mutableListOf<HotspotCard>()
            val cArr = root.optJSONArray("cards") ?: JSONArray()
            for (i in 0 until cArr.length()) {
                val o = cArr.getJSONObject(i)
                cardsList.add(
                    HotspotCard(
                        id = o.optString("id", ""),
                        name = o.optString("name", ""),
                        password = o.optString("password", ""),
                        profile = o.optString("profile", ""),
                        uptime = o.optString("uptime", ""),
                        bytesIn = o.optLong("bytesIn", 0L),
                        bytesOut = o.optLong("bytesOut", 0L),
                        limitUptime = o.optString("limitUptime", ""),
                        limitBytesTotal = o.optLong("limitBytesTotal", 0L),
                        comment = o.optString("comment", ""),
                        disabled = o.optBoolean("disabled", false),
                        price = o.optString("price", "")
                    )
                )
            }

            val profList = mutableListOf<HotspotProfile>()
            val pArr = root.optJSONArray("profiles") ?: JSONArray()
            for (i in 0 until pArr.length()) {
                val o = pArr.getJSONObject(i)
                profList.add(
                    HotspotProfile(
                        id = o.optString("id", ""),
                        name = o.optString("name", ""),
                        sharedUsers = o.optInt("sharedUsers", 1),
                        rateLimit = o.optString("rateLimit", ""),
                        validity = o.optString("validity", ""),
                        price = o.optString("price", ""),
                        dataLimit = o.optString("dataLimit", "")
                    )
                )
            }

            val activeList = mutableListOf<HotspotActiveUser>()
            val aArr = root.optJSONArray("activeUsers") ?: JSONArray()
            for (i in 0 until aArr.length()) {
                val o = aArr.getJSONObject(i)
                activeList.add(
                    HotspotActiveUser(
                        id = o.optString("id", ""),
                        user = o.optString("user", ""),
                        address = o.optString("address", ""),
                        macAddress = o.optString("macAddress", ""),
                        uptime = o.optString("uptime", ""),
                        bytesIn = o.optLong("bytesIn", 0L),
                        bytesOut = o.optLong("bytesOut", 0L),
                        hostName = o.optString("hostName", ""),
                        nodeName = o.optString("nodeName", ""),
                        serverName = o.optString("serverName", ""),
                        deviceModel = o.optString("deviceModel", "")
                    )
                )
            }

            val serversList = mutableListOf<HotspotServerInfo>()
            val sArr = root.optJSONArray("servers") ?: JSONArray()
            for (i in 0 until sArr.length()) {
                val o = sArr.getJSONObject(i)
                serversList.add(
                    HotspotServerInfo(
                        id = o.optString("id", ""),
                        name = o.optString("name", ""),
                        interfaceName = o.optString("interfaceName", ""),
                        profile = o.optString("profile", ""),
                        addressPool = o.optString("addressPool", ""),
                        addressesPerMac = o.optInt("addressesPerMac", 1),
                        disabled = o.optBoolean("disabled", false)
                    )
                )
            }

            val nodesList = mutableListOf<NetworkNode>()
            val nArr = root.optJSONArray("nodes") ?: JSONArray()
            for (i in 0 until nArr.length()) {
                val o = nArr.getJSONObject(i)
                nodesList.add(
                    NetworkNode(
                        id = o.optString("id", ""),
                        name = o.optString("name", ""),
                        ipAddress = o.optString("ipAddress", ""),
                        macAddress = o.optString("macAddress", "64:D1:54:3B:88:12"),
                        interfaceName = o.optString("interfaceName", "ether2"),
                        model = o.optString("model", "MikroTik cAP ac"),
                        role = o.optString("role", ""),
                        isOnline = o.optBoolean("isOnline", true),
                        pingMs = o.optLong("pingMs", 4L),
                        connectedUsers = o.optInt("connectedUsers", 12),
                        uptime = o.optString("uptime", "4d 12h"),
                        rxSpeedKbps = o.optDouble("rxSpeedKbps", 4200.0),
                        txSpeedKbps = o.optDouble("txSpeedKbps", 1650.0),
                        totalBytesConsumed = o.optLong("totalBytesConsumed", 28_500_000_000L),
                        signalDbm = o.optString("signalDbm", "-58 dBm"),
                        location = o.optString("location", ""),
                        cpuLoadPercent = o.optInt("cpuLoadPercent", 22),
                        memoryUsagePercent = o.optInt("memoryUsagePercent", 35),
                        temperature = o.optString("temperature", "43°C"),
                        voltage = o.optString("voltage", "24.2 V"),
                        frequency = o.optString("frequency", ""),
                        channelWidth = o.optString("channelWidth", ""),
                        ssid = o.optString("ssid", ""),
                        firmwareVersion = o.optString("firmwareVersion", ""),
                        lastSeenTime = o.optLong("lastSeenTime", System.currentTimeMillis())
                    )
                )
            }

            val logsList = mutableListOf<SystemLogEntry>()
            val lArr = root.optJSONArray("logs") ?: JSONArray()
            for (i in 0 until lArr.length()) {
                val o = lArr.getJSONObject(i)
                logsList.add(
                    SystemLogEntry(
                        id = o.optString("id", ""),
                        time = o.optString("time", ""),
                        topics = o.optString("topics", ""),
                        message = o.optString("message", "")
                    )
                )
            }

            return RouterSnapshot(
                ipAddress = ip,
                routerName = rName,
                resource = resource,
                routerBoard = routerBoard,
                cards = cardsList,
                profiles = profList,
                activeUsers = activeList,
                servers = serversList,
                nodes = nodesList,
                logs = logsList,
                timestamp = time
            )
        } catch (_: Exception) {}
        return null
    }

    fun getCacheSummary(context: Context, ipAddress: String): RouterCacheSummary? {
        val snapshot = loadSnapshot(context, ipAddress) ?: return null
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        val timeStr = sdf.format(Date(snapshot.timestamp))
        return RouterCacheSummary(
            cardsCount = snapshot.cards.size,
            profilesCount = snapshot.profiles.size,
            formattedTime = timeStr
        )
    }

    fun hasCachedSnapshot(context: Context, ipAddress: String): Boolean {
        val file = getCacheFile(context, ipAddress)
        return file.exists() && file.length() > 20
    }

    fun getLastCacheTimestamp(context: Context, ipAddress: String): Long {
        val snapshot = loadSnapshot(context, ipAddress)
        return snapshot?.timestamp ?: 0L
    }

    fun formatCacheAge(timestamp: Long): String {
        if (timestamp <= 0L) return "غير متوفر"
        val diffMs = System.currentTimeMillis() - timestamp
        val diffMin = diffMs / (60 * 1000L)
        val diffHour = diffMin / 60
        return when {
            diffMin < 1 -> "منذ لحظات"
            diffMin < 60 -> "منذ $diffMin دقيقة"
            diffHour < 24 -> "منذ $diffHour ساعة"
            else -> {
                val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }
}
