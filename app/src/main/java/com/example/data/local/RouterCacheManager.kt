package com.example.data.local

import android.content.Context
import com.example.data.model.HotspotActiveUser
import com.example.data.model.HotspotCard
import com.example.data.model.HotspotProfile
import com.example.data.model.HotspotServerInfo
import com.example.data.model.NetworkNode
import com.example.data.model.RouterBoardInfo
import com.example.data.model.RouterResource
import com.example.data.model.SystemLogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RouterCacheSummary(
    val ipAddress: String,
    val routerName: String,
    val cardsCount: Int,
    val profilesCount: Int,
    val activeUsersCount: Int,
    val timestamp: Long,
    val formattedTime: String
)

data class RouterSnapshot(
    val ipAddress: String,
    val routerName: String,
    val timestamp: Long,
    val resource: RouterResource,
    val routerBoard: RouterBoardInfo,
    val cards: List<HotspotCard>,
    val profiles: List<HotspotProfile>,
    val activeUsers: List<HotspotActiveUser>,
    val servers: List<HotspotServerInfo>,
    val nodes: List<NetworkNode>,
    val logs: List<SystemLogEntry>
)

object RouterCacheManager {

    private fun getCacheFile(context: Context, ipAddress: String): File {
        val cleanIp = ipAddress.replace(".", "_").replace(":", "_").trim()
        val dir = File(context.filesDir, "router_cache")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "cache_$cleanIp.json")
    }

    suspend fun hasCache(context: Context, ipAddress: String): Boolean = withContext(Dispatchers.IO) {
        val file = getCacheFile(context, ipAddress)
        file.exists() && file.length() > 50
    }

    suspend fun getCacheSummary(context: Context, ipAddress: String): RouterCacheSummary? = withContext(Dispatchers.IO) {
        try {
            val file = getCacheFile(context, ipAddress)
            if (!file.exists() || file.length() < 30) return@withContext null
            val content = file.readText()
            val json = JSONObject(content)
            val ip = json.optString("ip", ipAddress)
            val name = json.optString("name", "راوتر ميكروتك")
            val ts = json.optLong("timestamp", file.lastModified())
            val cardsCount = json.optInt("cards_count", json.optJSONArray("cards")?.length() ?: 0)
            val profilesCount = json.optInt("profiles_count", json.optJSONArray("profiles")?.length() ?: 0)
            val activeCount = json.optInt("active_count", json.optJSONArray("active_users")?.length() ?: 0)

            val sdf = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale("ar"))
            val formatted = sdf.format(Date(ts))

            RouterCacheSummary(
                ipAddress = ip,
                routerName = name,
                cardsCount = cardsCount,
                profilesCount = profilesCount,
                activeUsersCount = activeCount,
                timestamp = ts,
                formattedTime = formatted
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveSnapshot(
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
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject()
            val now = System.currentTimeMillis()
            root.put("ip", ipAddress)
            root.put("name", routerName)
            root.put("timestamp", now)
            root.put("cards_count", cards.size)
            root.put("profiles_count", profiles.size)
            root.put("active_count", activeUsers.size)

            // Resource
            val resObj = JSONObject().apply {
                put("uptime", resource.uptime)
                put("version", resource.version)
                put("boardName", resource.boardName)
                put("cpuLoad", resource.cpuLoad)
                put("freeMemoryBytes", resource.freeMemoryBytes)
                put("totalMemoryBytes", resource.totalMemoryBytes)
                put("architecture", resource.architecture)
                put("rxSpeedKbps", resource.rxSpeedKbps)
                put("txSpeedKbps", resource.txSpeedKbps)
                put("voltage", resource.voltage)
                put("temperature", resource.temperature)
                put("cpuFrequency", resource.cpuFrequency)
                put("hddFreeBytes", resource.hddFreeBytes)
            }
            root.put("resource", resObj)

            // RouterBoard
            val rbObj = JSONObject().apply {
                put("model", routerBoard.model)
                put("serialNumber", routerBoard.serialNumber)
                put("currentFirmware", routerBoard.currentFirmware)
                put("upgradeFirmware", routerBoard.upgradeFirmware)
                put("routerboard", routerBoard.routerboard)
            }
            root.put("routerBoard", rbObj)

            // Cards
            val cardsArray = JSONArray()
            cards.forEach { c ->
                val co = JSONObject().apply {
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
                    put("posAgent", c.posAgent)
                }
                cardsArray.put(co)
            }
            root.put("cards", cardsArray)

            // Profiles
            val profArray = JSONArray()
            profiles.forEach { p ->
                val po = JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("rateLimit", p.rateLimit)
                    put("sharedUsers", p.sharedUsers)
                    put("validity", p.validity)
                    put("price", p.price)
                    put("costPrice", p.costPrice)
                    put("dataLimit", p.dataLimit)
                    put("sessionTimeout", p.sessionTimeout)
                    put("comment", p.comment)
                }
                profArray.put(po)
            }
            root.put("profiles", profArray)

            // Active Users
            val activeArray = JSONArray()
            activeUsers.forEach { a ->
                val ao = JSONObject().apply {
                    put("id", a.id)
                    put("user", a.user)
                    put("address", a.address)
                    put("macAddress", a.macAddress)
                    put("uptime", a.uptime)
                    put("bytesIn", a.bytesIn)
                    put("bytesOut", a.bytesOut)
                    put("loginBy", a.loginBy)
                    put("sessionTimeLeft", a.sessionTimeLeft)
                    put("comment", a.comment)
                    put("hostName", a.hostName)
                    put("serverName", a.serverName)
                    put("nodeName", a.nodeName)
                    put("deviceModel", a.deviceModel)
                }
                activeArray.put(ao)
            }
            root.put("active_users", activeArray)

            // Servers
            val srvArray = JSONArray()
            servers.forEach { s ->
                val so = JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("interfaceName", s.interfaceName)
                    put("profile", s.profile)
                    put("addressPool", s.addressPool)
                    put("addressesPerMac", s.addressesPerMac)
                    put("disabled", s.disabled)
                }
                srvArray.put(so)
            }
            root.put("servers", srvArray)

            // Nodes
            val nodeArray = JSONArray()
            nodes.forEach { n ->
                val no = JSONObject().apply {
                    put("id", n.id)
                    put("name", n.name)
                    put("interfaceName", n.interfaceName)
                    put("ipAddress", n.ipAddress)
                    put("connectedUsers", n.connectedUsers)
                    put("isOnline", n.isOnline)
                    put("rxSpeedKbps", n.rxSpeedKbps)
                    put("txSpeedKbps", n.txSpeedKbps)
                    put("role", n.role)
                    put("location", n.location)
                    put("frequency", n.frequency)
                }
                nodeArray.put(no)
            }
            root.put("nodes", nodeArray)

            // Logs
            val logsArray = JSONArray()
            logs.take(30).forEach { l ->
                val lo = JSONObject().apply {
                    put("id", l.id)
                    put("time", l.time)
                    put("topics", l.topics)
                    put("message", l.message)
                }
                logsArray.put(lo)
            }
            root.put("logs", logsArray)

            val file = getCacheFile(context, ipAddress)
            val tempFile = File(file.parentFile, "${file.name}.tmp")
            tempFile.writeText(root.toString())
            tempFile.renameTo(file)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun loadSnapshot(context: Context, ipAddress: String): RouterSnapshot? = withContext(Dispatchers.IO) {
        try {
            val file = getCacheFile(context, ipAddress)
            if (!file.exists() || file.length() < 30) return@withContext null
            val content = file.readText()
            val root = JSONObject(content)

            val ip = root.optString("ip", ipAddress)
            val name = root.optString("name", "راوتر ميكروتك")
            val ts = root.optLong("timestamp", file.lastModified())

            // Resource
            val resObj = root.optJSONObject("resource")
            val resource = if (resObj != null) {
                RouterResource(
                    uptime = resObj.optString("uptime", "1d 0h 0m"),
                    version = resObj.optString("version", "7.x"),
                    boardName = resObj.optString("boardName", name),
                    cpuLoad = resObj.optInt("cpuLoad", 15),
                    freeMemoryBytes = resObj.optLong("freeMemoryBytes", 512 * 1024 * 1024L),
                    totalMemoryBytes = resObj.optLong("totalMemoryBytes", 1024 * 1024 * 1024L),
                    architecture = resObj.optString("architecture", "arm64"),
                    rxSpeedKbps = resObj.optDouble("rxSpeedKbps", 0.0),
                    txSpeedKbps = resObj.optDouble("txSpeedKbps", 0.0),
                    voltage = resObj.optString("voltage", "24.0 V"),
                    temperature = resObj.optString("temperature", "42°C"),
                    cpuFrequency = resObj.optString("cpuFrequency", "1400 MHz"),
                    hddFreeBytes = resObj.optLong("hddFreeBytes", 256 * 1024 * 1024L)
                )
            } else RouterResource()

            // RouterBoard
            val rbObj = root.optJSONObject("routerBoard")
            val routerBoard = if (rbObj != null) {
                RouterBoardInfo(
                    model = rbObj.optString("model", "MikroTik Router"),
                    serialNumber = rbObj.optString("serialNumber", ""),
                    currentFirmware = rbObj.optString("currentFirmware", "7.x"),
                    upgradeFirmware = rbObj.optString("upgradeFirmware", "7.x"),
                    routerboard = rbObj.optBoolean("routerboard", true)
                )
            } else RouterBoardInfo()

            // Cards
            val cardsList = mutableListOf<HotspotCard>()
            val cardsArr = root.optJSONArray("cards")
            if (cardsArr != null) {
                for (i in 0 until cardsArr.length()) {
                    val co = cardsArr.getJSONObject(i)
                    cardsList.add(
                        HotspotCard(
                            id = co.optString("id", "*$i"),
                            name = co.optString("name"),
                            password = co.optString("password"),
                            profile = co.optString("profile", "default"),
                            uptime = co.optString("uptime", "0s"),
                            bytesIn = co.optLong("bytesIn", 0L),
                            bytesOut = co.optLong("bytesOut", 0L),
                            limitUptime = co.optString("limitUptime", ""),
                            limitBytesTotal = co.optLong("limitBytesTotal", 0L),
                            comment = co.optString("comment", ""),
                            disabled = co.optBoolean("disabled", false),
                            price = co.optString("price", ""),
                            posAgent = co.optString("posAgent", "")
                        )
                    )
                }
            }

            // Profiles
            val profList = mutableListOf<HotspotProfile>()
            val profArr = root.optJSONArray("profiles")
            if (profArr != null) {
                for (i in 0 until profArr.length()) {
                    val po = profArr.getJSONObject(i)
                    profList.add(
                        HotspotProfile(
                            id = po.optString("id", "*p$i"),
                            name = po.optString("name", "default"),
                            rateLimit = po.optString("rateLimit", "2M/2M"),
                            sharedUsers = po.optInt("sharedUsers", 1),
                            validity = po.optString("validity", ""),
                            price = po.optString("price", ""),
                            costPrice = po.optDouble("costPrice", 0.0),
                            dataLimit = po.optString("dataLimit", ""),
                            sessionTimeout = po.optString("sessionTimeout", "1d"),
                            comment = po.optString("comment", "")
                        )
                    )
                }
            }

            // Active Users
            val activeList = mutableListOf<HotspotActiveUser>()
            val activeArr = root.optJSONArray("active_users")
            if (activeArr != null) {
                for (i in 0 until activeArr.length()) {
                    val ao = activeArr.getJSONObject(i)
                    activeList.add(
                        HotspotActiveUser(
                            id = ao.optString("id", "*a$i"),
                            user = ao.optString("user"),
                            address = ao.optString("address"),
                            macAddress = ao.optString("macAddress"),
                            uptime = ao.optString("uptime", "0s"),
                            bytesIn = ao.optLong("bytesIn", 0L),
                            bytesOut = ao.optLong("bytesOut", 0L),
                            loginBy = ao.optString("loginBy", "http-chap"),
                            sessionTimeLeft = ao.optString("sessionTimeLeft", ""),
                            comment = ao.optString("comment", ""),
                            hostName = ao.optString("hostName", ""),
                            serverName = ao.optString("serverName", "hotspot1"),
                            nodeName = ao.optString("nodeName", "البرج الرئيسي"),
                            deviceModel = ao.optString("deviceModel", "")
                        )
                    )
                }
            }

            // Servers
            val serverList = mutableListOf<HotspotServerInfo>()
            val srvArr = root.optJSONArray("servers")
            if (srvArr != null) {
                for (i in 0 until srvArr.length()) {
                    val so = srvArr.getJSONObject(i)
                    serverList.add(
                        HotspotServerInfo(
                            id = so.optString("id", "*s$i"),
                            name = so.optString("name", "hotspot1"),
                            interfaceName = so.optString("interfaceName", "bridge-hotspot"),
                            profile = so.optString("profile", "hsprof1"),
                            addressPool = so.optString("addressPool", "hs-pool"),
                            addressesPerMac = so.optInt("addressesPerMac", 1),
                            disabled = so.optBoolean("disabled", false)
                        )
                    )
                }
            }

            // Nodes
            val nodeList = mutableListOf<NetworkNode>()
            val nodeArr = root.optJSONArray("nodes")
            if (nodeArr != null) {
                for (i in 0 until nodeArr.length()) {
                    val no = nodeArr.getJSONObject(i)
                    nodeList.add(
                        NetworkNode(
                            id = no.optString("id", "node_$i"),
                            name = no.optString("name", "نقطة توزيع"),
                            interfaceName = no.optString("interfaceName", "ether1"),
                            ipAddress = no.optString("ipAddress", "192.168.88.2"),
                            connectedUsers = no.optInt("connectedUsers", 0),
                            isOnline = no.optBoolean("isOnline", true),
                            rxSpeedKbps = no.optDouble("rxSpeedKbps", 0.0),
                            txSpeedKbps = no.optDouble("txSpeedKbps", 0.0),
                            role = no.optString("role", "AP"),
                            location = no.optString("location", ""),
                            frequency = no.optString("frequency", "")
                        )
                    )
                }
            }

            // Logs
            val logsList = mutableListOf<SystemLogEntry>()
            val logsArr = root.optJSONArray("logs")
            if (logsArr != null) {
                for (i in 0 until logsArr.length()) {
                    val lo = logsArr.getJSONObject(i)
                    logsList.add(
                        SystemLogEntry(
                            id = lo.optString("id", "log_$i"),
                            time = lo.optString("time", ""),
                            topics = lo.optString("topics", "system"),
                            message = lo.optString("message", "")
                        )
                    )
                }
            }

            RouterSnapshot(
                ipAddress = ip,
                routerName = name,
                timestamp = ts,
                resource = resource,
                routerBoard = routerBoard,
                cards = cardsList,
                profiles = profList,
                activeUsers = activeList,
                servers = serverList,
                nodes = nodeList,
                logs = logsList
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun deleteCache(context: Context, ipAddress: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = getCacheFile(context, ipAddress)
            if (file.exists()) file.delete() else true
        } catch (_: Exception) {
            false
        }
    }
}
