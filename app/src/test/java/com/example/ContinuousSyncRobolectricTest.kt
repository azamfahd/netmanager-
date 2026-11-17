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
}
