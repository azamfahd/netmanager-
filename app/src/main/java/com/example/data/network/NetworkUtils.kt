package com.example.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.RouteInfo
import android.net.wifi.WifiManager
import com.example.data.model.DiscoveredRouter
import com.example.data.model.LocalNetworkInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale

object NetworkUtils {

    fun getLocalNetworkInfo(context: Context): LocalNetworkInfo {
        return try {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val wifiManager =
                context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

            val activeNetwork = connectivityManager.activeNetwork
            val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
            val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

            var deviceIp: String? = null
            var gatewayIp: String? = null
            var ssid: String? = null

            // 1. Try to read Gateway from LinkProperties (modern standard Android)
            if (activeNetwork != null) {
                val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
                if (linkProperties != null) {
                    for (route in linkProperties.routes) {
                        val gateway = route.gateway
                        if (gateway != null && !gateway.isAnyLocalAddress && gateway.hostAddress != null) {
                            val host = gateway.hostAddress ?: ""
                            if (host.contains(".") && host != "0.0.0.0") { // IPv4
                                gatewayIp = host
                                break
                            }
                        }
                    }
                    for (linkAddress in linkProperties.linkAddresses) {
                        val addr = linkAddress.address
                        if (addr != null && !addr.isLoopbackAddress && addr.hostAddress != null) {
                            val host = addr.hostAddress ?: ""
                            if (host.contains(".") && host != "0.0.0.0") {
                                deviceIp = host
                                break
                            }
                        }
                    }
                }
            }

            // 2. Try WifiManager DhcpInfo if not already found
            if (wifiManager != null) {
                val dhcpInfo = wifiManager.dhcpInfo
                if (dhcpInfo != null) {
                    if (gatewayIp.isNullOrBlank() && dhcpInfo.gateway != 0) {
                        gatewayIp = intToIp(dhcpInfo.gateway)
                    }
                    if (deviceIp.isNullOrBlank() && dhcpInfo.ipAddress != 0) {
                        deviceIp = intToIp(dhcpInfo.ipAddress)
                    }
                }
                val wifiInfo = wifiManager.connectionInfo
                if (wifiInfo != null && wifiInfo.ssid != null && wifiInfo.ssid != "<unknown ssid>") {
                    ssid = wifiInfo.ssid.removeSurrounding("\"")
                }
            }

            // 3. Fallback standard default for MikroTik routers
            if (gatewayIp.isNullOrBlank() || gatewayIp == "0.0.0.0") {
                gatewayIp = "192.168.88.1"
            }

            LocalNetworkInfo(
                isConnectedToWifi = isWifi || (activeNetwork != null),
                wifiSsid = ssid ?: if (isWifi) "شبكة Wi-Fi نشطة" else "شبكة اتصال محلية (LAN)",
                deviceIp = deviceIp ?: "192.168.88.245",
                gatewayIp = gatewayIp
            )
        } catch (e: Exception) {
            LocalNetworkInfo(
                isConnectedToWifi = true,
                wifiSsid = "شبكة Wi-Fi نشطة",
                deviceIp = "192.168.88.245",
                gatewayIp = "192.168.88.1"
            )
        }
    }

    suspend fun discoverRouter(context: Context): DiscoveredRouter {
        return withContext(Dispatchers.IO) {
            val netInfo = getLocalNetworkInfo(context)
            val gateway = netInfo.gatewayIp ?: "192.168.88.1"
            val ssid = netInfo.wifiSsid ?: "الشبكة الحالية"

            // Probe standard MikroTik ports
            val portsToTest = listOf(80, 443, 8728, 8080)
            var activePort = 80
            var isHttps = false
            var foundReachable = false

            for (port in portsToTest) {
                if (testSocketConnection(gateway, port, timeoutMs = 800)) {
                    activePort = port
                    isHttps = (port == 443)
                    foundReachable = true
                    break
                }
            }

            DiscoveredRouter(
                ip = gateway,
                port = activePort,
                isReachable = foundReachable,
                useHttps = isHttps,
                networkSsid = ssid,
                statusMessage = if (foundReachable)
                    "تم اكتشاف راوتر ميكروتك النشط على العنوان $gateway:$activePort"
                else
                    "تم التعرف على عنوان بوابة الشبكة ($gateway) تلقائياً"
            )
        }
    }

    private fun intToIp(ipInt: Int): String {
        return "${ipInt and 0xFF}.${ipInt shr 8 and 0xFF}.${ipInt shr 16 and 0xFF}.${ipInt shr 24 and 0xFF}"
    }

    suspend fun testSocketConnection(host: String, port: Int, timeoutMs: Int = 1500): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    val socketAddress = InetSocketAddress(host, port)
                    socket.connect(socketAddress, timeoutMs)
                    true
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    suspend fun pingHost(host: String, timeoutMs: Int = 2000): Pair<Boolean, Long> {
        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            try {
                val address = InetAddress.getByName(host)
                val reachable = address.isReachable(timeoutMs)
                val duration = System.currentTimeMillis() - start
                Pair(reachable, if (reachable) duration else -1L)
            } catch (e: Exception) {
                Pair(false, -1L)
            }
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    fun formatSpeed(kbps: Double): String {
        val mbps = kbps / 1024.0
        return if (mbps >= 1.0) {
            String.format(Locale.US, "%.2f Mbps", mbps)
        } else {
            String.format(Locale.US, "%.0f Kbps", kbps)
        }
    }
}
