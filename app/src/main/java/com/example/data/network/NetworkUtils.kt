package com.example.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.RouteInfo
import android.net.wifi.WifiManager
import com.example.data.model.DiscoveredRouter
import com.example.data.model.LocalNetworkInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
                    // Check routes for default gateway (destination 0.0.0.0/0 or destination with prefix 0)
                    for (route in linkProperties.routes) {
                        val gw = route.gateway?.hostAddress
                        if (!gw.isNullOrBlank() && gw.contains(".") && gw != "0.0.0.0") {
                            if (route.isDefaultRoute) {
                                gatewayIp = gw
                                break
                            } else if (gatewayIp == null) {
                                gatewayIp = gw
                            }
                        }
                    }

                    // Android 11+ (API 30+) DHCP Server address
                    if (gatewayIp.isNullOrBlank() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        val dhcpServer = linkProperties.dhcpServerAddress?.hostAddress
                        if (!dhcpServer.isNullOrBlank() && dhcpServer.contains(".") && dhcpServer != "0.0.0.0") {
                            gatewayIp = dhcpServer
                        }
                    }

                    // Local DNS server as router fallback
                    if (gatewayIp.isNullOrBlank()) {
                        for (dns in linkProperties.dnsServers) {
                            val dnsHost = dns.hostAddress ?: ""
                            if (dnsHost.startsWith("192.168.") || dnsHost.startsWith("10.") || dnsHost.startsWith("172.")) {
                                gatewayIp = dnsHost
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
                    if (gatewayIp.isNullOrBlank() && dhcpInfo.serverAddress != 0) {
                        gatewayIp = intToIp(dhcpInfo.serverAddress)
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

            // Target candidates to probe: detected gateway first, then standard 192.168.88.1 if gateway is different
            val ipCandidates = if (gateway != "192.168.88.1") listOf(gateway, "192.168.88.1") else listOf(gateway)
            // MikroTik standard service ports to test
            val portsToTest = listOf(8728, 8729, 8291, 80, 443, 8080)

            var resolvedIp = gateway
            var activePort = 8728
            var isHttps = false
            var foundReachable = false
            var openPortsList = listOf<Int>()
            var serviceType = "MikroTik API (8728)"

            for (targetIp in ipCandidates) {
                // Test all candidate ports in parallel with short timeout for instant response
                val testedPorts = coroutineScope {
                    portsToTest.map { port ->
                        async {
                            val open = testSocketConnection(targetIp, port, timeoutMs = 450)
                            port to open
                        }
                    }.awaitAll()
                }

                val reachablePorts = testedPorts.filter { it.second }.map { it.first }
                if (reachablePorts.isNotEmpty()) {
                    resolvedIp = targetIp
                    foundReachable = true
                    openPortsList = reachablePorts

                    // Intelligently select best port for MikroTik connection:
                    // 1) 8728 API is premier
                    // 2) 8729 API-SSL
                    // 3) 80 HTTP / REST
                    // 4) 443 HTTPS
                    // 5) First open port
                    activePort = when {
                        reachablePorts.contains(8728) -> 8728
                        reachablePorts.contains(8729) -> 8729
                        reachablePorts.contains(80) -> 80
                        reachablePorts.contains(443) -> 443
                        else -> reachablePorts.first()
                    }
                    isHttps = (activePort == 443 || activePort == 8729)
                    serviceType = when (activePort) {
                        8728 -> "منفذ MikroTik API الرسمي (8728)"
                        8729 -> "منفذ MikroTik API-SSL المشفر (8729)"
                        8291 -> "منفذ WinBox النشط (8291)"
                        80 -> "منفذ WebFig / REST (80)"
                        443 -> "منفذ WebFig HTTPS (443)"
                        else -> "منفذ مخصص ($activePort)"
                    }
                    break
                }
            }

            DiscoveredRouter(
                ip = resolvedIp,
                port = activePort,
                isReachable = foundReachable,
                useHttps = isHttps,
                networkSsid = ssid,
                openPorts = openPortsList,
                serviceType = serviceType,
                statusMessage = if (foundReachable)
                    "تم التعرف تلقائياً على الراوتر ($resolvedIp) والمنفذ المفتوح ($activePort - $serviceType)"
                else
                    "تم التعرف على عنوان بوابة الشبكة ($resolvedIp) تلقائياً من شبكتك المتصلة"
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
