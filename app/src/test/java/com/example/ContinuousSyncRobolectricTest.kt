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
}
