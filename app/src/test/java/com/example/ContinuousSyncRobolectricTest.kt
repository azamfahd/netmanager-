package com.example

import com.example.data.model.RouterConnectionConfig
import com.example.data.network.MikrotikRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ContinuousSyncRobolectricTest {

    @Test
    fun testBackToBackRefreshDataExecutesImmediatelyWithoutCooldownSkip() {
        val resourceHits = AtomicInteger(0)
        val running = AtomicBoolean(true)
        val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port = serverSocket.localPort

        val serverThread = Thread {
            while (running.get()) {
                try {
                    val client = serverSocket.accept()
                    Thread {
                        client.use { sock ->
                            sock.soTimeout = 2000
                            val input = sock.getInputStream()
                            val firstByte = input.read()
                            // Immediately reject non-HTTP binary RouterOS socket handshake
                            if (firstByte == -1 || (firstByte != 'G'.code && firstByte != 'P'.code && firstByte != 'D'.code && firstByte != 'H'.code)) {
                                return@use
                            }
                            val rest = buildString {
                                append(firstByte.toChar())
                                val reader = input.bufferedReader()
                                val restLine = reader.readLine().orEmpty()
                                append(restLine)
                                while (true) {
                                    val line = reader.readLine() ?: break
                                    if (line.isEmpty()) break
                                }
                            }
                            if (rest.contains("/rest/system/resource")) {
                                resourceHits.incrementAndGet()
                            }
                            val body = when {
                                rest.contains("/rest/system/resource") ->
                                    """{"uptime":"1d","version":"7.15","board-name":"RB5009","cpu-load":10,"free-memory":536870912,"total-memory":1073741824}"""
                                rest.contains("/rest/system/identity") ->
                                    """{"name":"LiveRouter"}"""
                                else -> "[]"
                            }
                            val bytes = body.toByteArray(Charsets.UTF_8)
                            val out = sock.getOutputStream()
                            val header = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                            out.write(header.toByteArray(Charsets.US_ASCII))
                            out.write(bytes)
                            out.flush()
                        }
                    }.start()
                } catch (_: Exception) {
                    break
                }
            }
        }
        serverThread.isDaemon = true
        serverThread.start()

        try {
            val repo = MikrotikRepository(appDao = null)
            runBlocking {
                val cfg = RouterConnectionConfig(
                    ipAddress = "127.0.0.1",
                    port = port,
                    username = "admin",
                    password = "123",
                    useHttps = false,
                    autoDetectProtocol = false
                )
                val connectRes = repo.verifyAndConnect(cfg)
                assertTrue(
                    "Expected local REST connection to succeed: ${connectRes.exceptionOrNull()?.message}",
                    connectRes.isSuccess
                )

                resourceHits.set(0)
                val first = repo.refreshData()
                val second = repo.refreshData()
                assertTrue(first.isSuccess)
                assertTrue(second.isSuccess)
                assertTrue(
                    "Expected both back-to-back refreshData calls to reach router immediately without 1500ms cooldown drop, hits=${resourceHits.get()}",
                    resourceHits.get() >= 2
                )
            }
        } finally {
            running.set(false)
            try { serverSocket.close() } catch (_: Exception) {}
        }
    }

    @Test
    fun testVerifyAndConnectSmart_autoOpensMikroTikWebFigWhenApiAndRestUnavailable() {
        val running = AtomicBoolean(true)
        val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port = serverSocket.localPort

        val serverThread = Thread {
            while (running.get()) {
                try {
                    val client = serverSocket.accept()
                    Thread {
                        client.use { sock ->
                            sock.soTimeout = 2000
                            val input = sock.getInputStream()
                            val firstByte = input.read()
                            if (firstByte == -1 || (firstByte != 'G'.code && firstByte != 'P'.code && firstByte != 'H'.code)) {
                                return@use
                            }
                            val firstLine = buildString {
                                append(firstByte.toChar())
                                val reader = input.bufferedReader()
                                append(reader.readLine().orEmpty())
                                while (true) {
                                    val line = reader.readLine() ?: break
                                    if (line.isEmpty()) break
                                }
                            }
                            val out = sock.getOutputStream()
                            if (firstLine.contains("/rest/")) {
                                val errBody = "<html><body>404 Not Found</body></html>".toByteArray(Charsets.UTF_8)
                                val hdr = "HTTP/1.1 404 Not Found\r\nContent-Type: text/html\r\nContent-Length: ${errBody.size}\r\nConnection: close\r\n\r\n"
                                out.write(hdr.toByteArray(Charsets.US_ASCII))
                                out.write(errBody)
                            } else {
                                val webFigHtml = """
                                    <!doctype html>
                                    <html>
                                    <head><title>RouterOS Router Configuration</title></head>
                                    <body><h1>MikroTik WebFig</h1><input id="name"/><input id="password" type="password"/></body>
                                    </html>
                                """.trimIndent().toByteArray(Charsets.UTF_8)
                                val hdr = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${webFigHtml.size}\r\nConnection: close\r\n\r\n"
                                out.write(hdr.toByteArray(Charsets.US_ASCII))
                                out.write(webFigHtml)
                            }
                            out.flush()
                        }
                    }.start()
                } catch (_: Exception) {
                    break
                }
            }
        }
        serverThread.isDaemon = true
        serverThread.start()

        try {
            val repo = MikrotikRepository(appDao = null)
            runBlocking {
                val cfg = RouterConnectionConfig(
                    name = "Test Router",
                    ipAddress = "127.0.0.1",
                    port = port,
                    username = "admin",
                    password = "secret",
                    useHttps = false,
                    autoDetectProtocol = false
                )
                val outcome = repo.verifyAndConnectSmart(cfg)
                assertTrue(
                    "Expected WebPortalDetected for WebFig-only router, got $outcome",
                    outcome is MikrotikRepository.VerifyConnectOutcome.WebPortalDetected
                )
                val portalOutcome = outcome as MikrotikRepository.VerifyConnectOutcome.WebPortalDetected
                assertTrue("Expected isMikroTikWebFig = true", portalOutcome.isMikroTikWebFig)
                assertTrue(portalOutcome.url.contains("127.0.0.1:$port"))
            }
        } finally {
            running.set(false)
            try { serverSocket.close() } catch (_: Exception) {}
        }
    }

    @Test
    fun testWebPortalCacheManager_savesLoadsAndValidatesMhtArchive() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val portalUrl = "https://aslamnet-taz-ac.apluswifi.com/cards/cards"
        val archiveFile = com.example.data.local.WebPortalCacheManager.getArchiveTargetFile(context, portalUrl)
        archiveFile.writeText("From: <Saved by WebKit>\r\nSubject: APlusWiFi\r\n\r\n<html><body>Cached</body></html>")

        val meta = com.example.data.local.CachedPortalMeta(
            host = "aslamnet-taz-ac.apluswifi.com",
            serverName = "سيرفر APlusWiFi",
            baseUrl = "https://aslamnet-taz-ac.apluswifi.com/",
            lastVisitedUrl = portalUrl,
            lastPageTitle = "Cards Management",
            targetPathUrl = portalUrl,
            username = "ahmed771233700@gmail.com",
            isAplusWifi = true,
            isMikroTikWebFig = false,
            archiveFilePath = archiveFile.absolutePath,
            lastSavedTime = System.currentTimeMillis()
        )
        com.example.data.local.WebPortalCacheManager.saveMeta(context, meta)

        val loadedByHost = com.example.data.local.WebPortalCacheManager.loadMeta(context, "aslamnet-taz-ac.apluswifi.com")
        val loadedByUrl = com.example.data.local.WebPortalCacheManager.loadMeta(context, portalUrl)
        val latest = com.example.data.local.WebPortalCacheManager.getLatestCachedPortal(context)

        org.junit.Assert.assertNotNull(loadedByHost)
        org.junit.Assert.assertNotNull(loadedByUrl)
        org.junit.Assert.assertNotNull(latest)
        assertTrue(loadedByHost!!.hasValidArchive)
        org.junit.Assert.assertEquals(portalUrl, loadedByUrl!!.lastVisitedUrl)
        org.junit.Assert.assertEquals("ahmed771233700@gmail.com", latest!!.username)
    }

    @Test
    fun testWebPortalCacheManager_updatesLastVisitedUrlAndAppendsActivity() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val host = "aslamnet-taz-ac.apluswifi.com"
        com.example.data.local.WebPortalCacheManager.saveMeta(
            context,
            com.example.data.local.CachedPortalMeta(
                host = host,
                serverName = "سيرفر APlusWiFi",
                baseUrl = "https://$host/",
                lastVisitedUrl = "https://$host/login",
                isAplusWifi = true,
                lastSavedTime = 1_000L
            )
        )

        com.example.data.local.WebPortalCacheManager.updateLastVisitedUrl(
            context = context,
            hostOrUrl = host,
            visitedUrl = "https://$host/dashboard",
            pageTitle = "الاحصائيات"
        )
        com.example.data.local.WebPortalCacheManager.appendPortalActivity(
            context = context,
            hostOrUrl = host,
            activitySummary = "تعبئة بيانات الدخول تلقائياً"
        )

        val reloaded = com.example.data.local.WebPortalCacheManager.loadMeta(context, host)
        org.junit.Assert.assertNotNull(reloaded)
        org.junit.Assert.assertEquals("https://$host/dashboard", reloaded!!.lastVisitedUrl)
        org.junit.Assert.assertEquals("الاحصائيات", reloaded.lastPageTitle)
        assertTrue(
            "Expected activity history to contain both autofill and dashboard navigation, got ${reloaded.recentActions}",
            reloaded.recentActions.size >= 2 &&
                reloaded.recentActions[0].contains("تعبئة بيانات الدخول") &&
                reloaded.recentActions[1].contains("/dashboard")
        )
    }

    @Test
    fun testVerifyAndConnectSmart_detectsCloudPortalFastWithout12sBlocking() {
        val running = AtomicBoolean(true)
        val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port = serverSocket.localPort

        val serverThread = Thread {
            while (running.get()) {
                try {
                    val client = serverSocket.accept()
                    Thread {
                        client.use { sock ->
                            sock.soTimeout = 2000
                            val input = sock.getInputStream()
                            val firstByte = input.read()
                            if (firstByte == -1 || (firstByte != 'G'.code && firstByte != 'P'.code && firstByte != 'H'.code)) {
                                return@use
                            }
                            val firstLine = buildString {
                                append(firstByte.toChar())
                                val reader = input.bufferedReader()
                                append(reader.readLine().orEmpty())
                                while (true) {
                                    val line = reader.readLine() ?: break
                                    if (line.isEmpty()) break
                                }
                            }
                            val out = sock.getOutputStream()
                            if (firstLine.contains("/rest/")) {
                                val errBody = "Not Found".toByteArray(Charsets.UTF_8)
                                val hdr = "HTTP/1.1 404 Not Found\r\nContent-Type: text/plain\r\nContent-Length: ${errBody.size}\r\nConnection: close\r\n\r\n"
                                out.write(hdr.toByteArray(Charsets.US_ASCII))
                                out.write(errBody)
                            } else {
                                val spaHtml = """
                                    <!doctype html>
                                    <html>
                                    <head><title>APlusWiFi Cloud Dashboard</title></head>
                                    <body><div id="app"></div><script src="/assets/app.js"></script></body>
                                    </html>
                                """.trimIndent().toByteArray(Charsets.UTF_8)
                                val hdr = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${spaHtml.size}\r\nConnection: close\r\n\r\n"
                                out.write(hdr.toByteArray(Charsets.US_ASCII))
                                out.write(spaHtml)
                            }
                            out.flush()
                        }
                    }.start()
                } catch (_: Exception) {
                    break
                }
            }
        }
        serverThread.isDaemon = true
        serverThread.start()

        try {
            val repo = MikrotikRepository(appDao = null)
            runBlocking {
                val startMs = System.currentTimeMillis()
                val cfg = RouterConnectionConfig(
                    name = "Cloud AC",
                    ipAddress = "http://127.0.0.1:$port/dashboard",
                    port = port,
                    username = "user@example.com",
                    password = "secret",
                    useHttps = false,
                    autoDetectProtocol = true
                )
                val outcome = repo.verifyAndConnectSmart(cfg)
                val elapsedMs = System.currentTimeMillis() - startMs

                assertTrue(
                    "Expected WebPortalDetected outcome, got $outcome",
                    outcome is MikrotikRepository.VerifyConnectOutcome.WebPortalDetected
                )
                assertTrue(
                    "Expected explicit cloud web URL to connect rapidly (< 3000ms), took ${elapsedMs}ms",
                    elapsedMs < 3000L
                )
                val portal = outcome as MikrotikRepository.VerifyConnectOutcome.WebPortalDetected
                assertTrue("Expected targetPathUrl to preserve /dashboard, got ${portal.targetPathUrl}", portal.targetPathUrl?.contains("/dashboard") == true)
            }
        } finally {
            running.set(false)
            try { serverSocket.close() } catch (_: Exception) {}
        }
    }

    @Test
    fun testConnectDirectViaRouterOsApi_connectsDirectlyWithoutWebFallbackAndRejectsHttpIntermediaries() {
        val running = AtomicBoolean(true)
        val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port = serverSocket.localPort

        val serverThread = Thread {
            while (running.get()) {
                try {
                    val client = serverSocket.accept()
                    Thread {
                        client.use { sock ->
                            sock.soTimeout = 1500
                            val input = sock.getInputStream()
                            val buf = ByteArray(1024)
                            input.read(buf)
                            val html = "<html><title>APlusWiFi Cloud</title><body>Cloud Portal</body></html>".toByteArray()
                            val out = sock.getOutputStream()
                            out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${html.size}\r\n\r\n".toByteArray())
                            out.write(html)
                            out.flush()
                        }
                    }.start()
                } catch (_: Exception) {
                    break
                }
            }
        }
        serverThread.isDaemon = true
        serverThread.start()

        try {
            val repo = MikrotikRepository(appDao = null)
            runBlocking {
                val cfg = RouterConnectionConfig(
                    name = "Direct IP",
                    ipAddress = "127.0.0.1",
                    port = port,
                    username = "admin",
                    password = "123",
                    useHttps = false,
                    autoDetectProtocol = false,
                    directApiOnly = true
                )
                val outcome = repo.verifyAndConnectSmart(cfg)
                assertTrue(
                    "Expected directApiOnly=true to reject HTTP web portal intermediary with Failure, got $outcome",
                    outcome is MikrotikRepository.VerifyConnectOutcome.Failure
                )
                val failure = outcome as MikrotikRepository.VerifyConnectOutcome.Failure
                assertTrue(
                    "Expected fallbackPortalUrl to be null in direct API mode",
                    failure.fallbackPortalUrl == null
                )
            }
        } finally {
            running.set(false)
            try { serverSocket.close() } catch (_: Exception) {}
        }
    }

    @Test
    fun testRouterOsApiClient_handshakeTimeoutFailsFastOnSilentPort() {
        val running = AtomicBoolean(true)
        val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port = serverSocket.localPort
        val heldSockets = java.util.Collections.synchronizedList(mutableListOf<java.net.Socket>())

        val serverThread = Thread {
            while (running.get()) {
                try {
                    val client = serverSocket.accept()
                    heldSockets.add(client)
                } catch (_: Exception) {
                    break
                }
            }
        }
        serverThread.isDaemon = true
        serverThread.start()

        try {
            val apiClient = com.example.data.network.RouterOsApiClient()
            runBlocking {
                val startMs = System.currentTimeMillis()
                val result = apiClient.connectAndLogin(
                    host = "127.0.0.1",
                    port = port,
                    username = "admin",
                    password = "secret",
                    timeoutMs = 700,
                    useTls = false
                )
                val elapsedMs = System.currentTimeMillis() - startMs
                assertTrue("Expected failure on silent port", result.isFailure)
                assertTrue(
                    "Expected SocketTimeoutException on silent port handshake, got ${result.exceptionOrNull()}",
                    result.exceptionOrNull() is java.net.SocketTimeoutException
                )
                assertTrue(
                    "Expected fast handshake timeout (< 4000ms) instead of 25s stall, took ${elapsedMs}ms",
                    elapsedMs < 4000L
                )
            }
        } finally {
            running.set(false)
            heldSockets.forEach { runCatching { it.close() } }
            try { serverSocket.close() } catch (_: Exception) {}
        }
    }

    @Test
    fun testConnectDirectViaRouterOsApi_succeedsWithNativeBinaryRouterOsProtocol() {
        val running = AtomicBoolean(true)
        val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port = serverSocket.localPort

        fun readRosWord(inp: java.io.InputStream): String {
            val first = inp.read()
            if (first <= 0) return ""
            val len = when {
                (first and 0x80) == 0 -> first
                (first and 0xC0) == 0x80 -> ((first and 0x3F) shl 8) or inp.read()
                else -> first
            }
            val bytes = ByteArray(len)
            var off = 0
            while (off < len) {
                val r = inp.read(bytes, off, len - off)
                if (r <= 0) break
                off += r
            }
            return String(bytes, 0, off, Charsets.UTF_8)
        }

        fun readRosSentence(inp: java.io.InputStream): List<String> {
            val list = mutableListOf<String>()
            while (true) {
                val w = readRosWord(inp)
                if (w.isEmpty()) break
                list.add(w)
            }
            return list
        }

        fun writeRosWord(out: java.io.OutputStream, word: String) {
            val b = word.toByteArray(Charsets.UTF_8)
            if (b.size < 0x80) {
                out.write(b.size)
            } else {
                out.write((b.size shr 8) or 0x80)
                out.write(b.size and 0xFF)
            }
            out.write(b)
        }

        fun writeRosSentence(out: java.io.OutputStream, words: List<String>) {
            words.forEach { writeRosWord(out, it) }
            out.write(0x00)
            out.flush()
        }

        val serverThread = Thread {
            while (running.get()) {
                try {
                    val client = serverSocket.accept()
                    Thread {
                        client.use { sock ->
                            sock.soTimeout = 3000
                            val inp = sock.getInputStream()
                            val out = sock.getOutputStream()
                            while (running.get() && !sock.isClosed) {
                                val sentence = readRosSentence(inp)
                                if (sentence.isEmpty()) break
                                val cmd = sentence.first()
                                when (cmd) {
                                    "/login" -> writeRosSentence(out, listOf("!done"))
                                    "/system/identity/print" -> {
                                        writeRosSentence(out, listOf("!re", "=name=LiveDirectRouter"))
                                        writeRosSentence(out, listOf("!done"))
                                    }
                                    "/system/resource/print" -> {
                                        writeRosSentence(
                                            out,
                                            listOf(
                                                "!re",
                                                "=uptime=3d",
                                                "=version=7.16",
                                                "=board-name=CCR2004",
                                                "=cpu-load=12",
                                                "=free-memory=536870912",
                                                "=total-memory=1073741824"
                                            )
                                        )
                                        writeRosSentence(out, listOf("!done"))
                                    }
                                    else -> writeRosSentence(out, listOf("!done"))
                                }
                            }
                        }
                    }.start()
                } catch (_: Exception) {
                    break
                }
            }
        }
        serverThread.isDaemon = true
        serverThread.start()

        try {
            val repo = MikrotikRepository(appDao = null)
            runBlocking {
                val cfg = RouterConnectionConfig(
                    name = "Direct API Router",
                    ipAddress = "127.0.0.1",
                    port = port,
                    username = "admin",
                    password = "123",
                    useHttps = false,
                    autoDetectProtocol = false,
                    directApiOnly = true
                )
                val outcome = repo.verifyAndConnectSmart(cfg)
                assertTrue(
                    "Expected MikroTikSuccess for direct binary RouterOS API, got $outcome",
                    outcome is MikrotikRepository.VerifyConnectOutcome.MikroTikSuccess
                )
                val success = outcome as MikrotikRepository.VerifyConnectOutcome.MikroTikSuccess
                assertTrue("Expected resolvedConfig.directApiOnly = true", success.resolvedConfig.directApiOnly)
                assertTrue(
                    "Expected boardName to contain LiveDirectRouter, got ${repo.resourceState.value.boardName}",
                    repo.resourceState.value.boardName.contains("LiveDirectRouter")
                )
            }
        } finally {
            running.set(false)
            try { serverSocket.close() } catch (_: Exception) {}
        }
    }
}
