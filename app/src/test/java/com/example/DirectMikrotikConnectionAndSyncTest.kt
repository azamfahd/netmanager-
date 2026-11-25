package com.example

import com.example.data.model.RouterConnectionConfig
import com.example.data.network.MikrotikRepository
import com.example.data.network.RouterOsApiClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

class DirectMikrotikConnectionAndSyncTest {

    private fun readRosWord(inp: InputStream): String {
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

    private fun readRosSentence(inp: InputStream): List<String> {
        val list = mutableListOf<String>()
        while (true) {
            val w = readRosWord(inp)
            if (w.isEmpty()) break
            list.add(w)
        }
        return list
    }

    private fun writeRosWord(out: OutputStream, word: String) {
        val b = word.toByteArray(Charsets.UTF_8)
        if (b.size < 0x80) {
            out.write(b.size)
        } else {
            out.write((b.size shr 8) or 0x80)
            out.write(b.size and 0xFF)
        }
        out.write(b)
    }

    private fun writeRosSentence(out: OutputStream, words: List<String>) {
        words.forEach { writeRosWord(out, it) }
        out.write(0x00)
        out.flush()
    }

    private fun md5Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun legacyPre643Md5ChallengeLoginAndFullSocketSyncPopulatesAllRouterData() {
        val running = AtomicBoolean(true)
        val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port = serverSocket.localPort
        val challengeBytes = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88.toByte())
        val challengeHex = challengeBytes.joinToString("") { "%02x".format(it) }
        val expectedPass = "mikrotik_pass"
        val expectedMd5Input = byteArrayOf(0) + expectedPass.toByteArray(Charsets.UTF_8) + challengeBytes
        val expectedResponse = "00" + md5Hex(expectedMd5Input)

        val serverThread = Thread {
            while (running.get()) {
                try {
                    val client = serverSocket.accept()
                    Thread {
                        client.use { sock ->
                            sock.soTimeout = 4000
                            val inp = sock.getInputStream()
                            val out = sock.getOutputStream()
                            while (running.get() && !sock.isClosed) {
                                val sentence = readRosSentence(inp)
                                if (sentence.isEmpty()) break
                                val cmd = sentence.first()
                                when (cmd) {
                                    "/login" -> {
                                        val hasName = sentence.any { it.startsWith("=name=") }
                                        val hasPass = sentence.any { it.startsWith("=password=") }
                                        val respWord = sentence.firstOrNull { it.startsWith("=response=") }
                                            ?.removePrefix("=response=")
                                        when {
                                            hasName && hasPass -> {
                                                // Simulate RouterOS < 6.43 rejecting cleartext /login and trailing !done
                                                writeRosSentence(out, listOf("!trap", "=message=cannot log in"))
                                                writeRosSentence(out, listOf("!done"))
                                            }
                                            !hasName && !hasPass && respWord == null -> {
                                                // Pre-6.43 bare /login issues challenge
                                                writeRosSentence(out, listOf("!done", "=ret=$challengeHex"))
                                            }
                                            respWord == expectedResponse -> {
                                                writeRosSentence(out, listOf("!done"))
                                            }
                                            else -> {
                                                writeRosSentence(out, listOf("!trap", "=message=invalid user name or password."))
                                                writeRosSentence(out, listOf("!done"))
                                            }
                                        }
                                    }
                                    "/system/identity/print" -> {
                                        writeRosSentence(out, listOf("!re", "=name=AlNoor-Core"))
                                        writeRosSentence(out, listOf("!done"))
                                    }
                                    "/system/resource/print" -> {
                                        writeRosSentence(
                                            out,
                                            listOf(
                                                "!re",
                                                "=uptime=5d12h",
                                                "=version=6.42.12",
                                                "=board-name=RB3011UiAS",
                                                "=architecture-name=arm",
                                                "=cpu-load=18",
                                                "=cpu-frequency=1400",
                                                "=free-memory=600000000",
                                                "=total-memory=1073741824",
                                                "=free-hdd-space=80000000"
                                            )
                                        )
                                        writeRosSentence(out, listOf("!done"))
                                    }
                                    "/system/health/print" -> {
                                        writeRosSentence(out, listOf("!re", "=voltage=24.1", "=temperature=43"))
                                        writeRosSentence(out, listOf("!done"))
                                    }
                                    "/system/routerboard/print" -> {
                                        writeRosSentence(out, listOf("!re", "=model=RB3011UiAS-RM", "=serial-number=SN123456", "=current-firmware=6.42.12"))
                                        writeRosSentence(out, listOf("!done"))
                                    }
                                    "/ip/hotspot/print" -> {
                                        writeRosSentence(out, listOf("!re", "=.id=*1", "=name=hs_main", "=interface=bridge1", "=profile=hsprof1", "=address-pool=hs_pool"))
                                        writeRosSentence(out, listOf("!done"))
                                    }
                                    "/ip/pool/print" -> {
                                        writeRosSentence(out, listOf("!re", "=.id=*1", "=name=hs_pool", "=ranges=10.0.0.10-10.0.0.250"))
                                        writeRosSentence(out, listOf("!done"))
                                    }
                                    "/ip/hotspot/user/profile/print" -> {
                                        writeRosSentence(
                                            out,
                                            listOf(
                                                "!re",
                                                "=.id=*1",
                                                "=name=VIP_Gold",
                                                "=rate-limit=2M/10M",
                                                "=shared-users=1",
                                                "=session-timeout=1d",
                                                "=comment=price:500"
                                            )
                                        )
                                        writeRosSentence(out, listOf("!done"))
                                    }
                                    "/ip/hotspot/ip-binding/print" -> {
                                        writeRosSentence(out, listOf("!re", "=.id=*1", "=mac-address=AA:BB:CC:DD:EE:01", "=address=10.0.0.5", "=type=bypassed", "=comment=AP-North"))
                                        writeRosSentence(out, listOf("!done"))
                                    }
                                    "/user/print" -> {
                                        writeRosSentence(out, listOf("!re", "=.id=*1", "=name=admin", "=group=full"))
                                        writeRosSentence(out, listOf("!done"))
                                    }
                                    "/ip/dhcp-server/lease/print" -> {
                                        writeRosSentence(out, listOf("!re", "=mac-address=D4:6A:A8:12:34:56", "=address=10.0.0.25", "=host-name=Galaxy-S24"))
                                        writeRosSentence(out, listOf("!done"))
                                    }
                                    "/ip/hotspot/host/print" -> {
                                        writeRosSentence(out, listOf("!re", "=mac-address=D4:6A:A8:12:34:56", "=interface=wlan1", "=server=hs_main"))
                                        writeRosSentence(out, listOf("!done"))
                                    }
                                    "/ip/hotspot/active/print" -> {
                                        writeRosSentence(
                                            out,
                                            listOf(
                                                "!re",
                                                "=.id=*A1",
                                                "=user=card_777",
                                                "=address=10.0.0.25",
                                                "=mac-address=D4:6A:A8:12:34:56",
                                                "=uptime=15m",
                                                "=bytes-in=102400",
                                                "=bytes-out=2048000",
                                                "=login-by=http-chap",
                                                "=server=hs_main"
                                            )
                                        )
                                        writeRosSentence(out, listOf("!done"))
                                    }
                                    "/ip/hotspot/user/print" -> {
                                        writeRosSentence(
                                            out,
                                            listOf(
                                                "!re",
                                                "=.id=*C1",
                                                "=name=card_777",
                                                "=password=pass1",
                                                "=profile=VIP_Gold",
                                                "=uptime=15m",
                                                "=bytes-in=102400",
                                                "=bytes-out=2048000",
                                                "=comment=batch_1"
                                            )
                                        )
                                        writeRosSentence(out, listOf("!done"))
                                    }
                                    "/interface/print" -> {
                                        writeRosSentence(
                                            out,
                                            listOf(
                                                "!re",
                                                "=.id=*1",
                                                "=name=ether1-WAN",
                                                "=type=ether",
                                                "=rx-byte=5000000",
                                                "=tx-byte=1200000",
                                                "=running=true"
                                            )
                                        )
                                        writeRosSentence(
                                            out,
                                            listOf(
                                                "!re",
                                                "=.id=*2",
                                                "=name=wlan1",
                                                "=type=wlan",
                                                "=rx-byte=1200000",
                                                "=tx-byte=4500000",
                                                "=running=true"
                                            )
                                        )
                                        writeRosSentence(out, listOf("!done"))
                                    }
                                    "/log/print" -> {
                                        writeRosSentence(out, listOf("!re", "=.id=*L1", "=time=10:00:00", "=topics=hotspot,info", "=message=card_777 logged in"))
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
                    name = "Legacy Router",
                    ipAddress = "127.0.0.1",
                    port = port,
                    username = "admin",
                    password = expectedPass,
                    useHttps = false,
                    autoDetectProtocol = false,
                    directApiOnly = true
                )
                val outcome = repo.verifyAndConnectSmart(cfg)
                assertTrue("Expected MikroTikSuccess on pre-6.43 challenge login, got $outcome", outcome is MikrotikRepository.VerifyConnectOutcome.MikroTikSuccess)

                // Verify ALL data fetched cleanly into system flows
                assertTrue(repo.resourceState.value.boardName.contains("AlNoor-Core"))
                assertEquals("24.1 V", repo.resourceState.value.voltage)
                assertEquals("43°C", repo.resourceState.value.temperature)
                assertEquals("RB3011UiAS-RM", repo.routerBoardInfo.value.model)
                assertEquals(1, repo.hotspotServers.value.size)
                assertEquals(1, repo.ipPools.value.size)
                assertEquals(1, repo.profiles.value.size)
                assertEquals(1, repo.ipBindings.value.size)
                assertEquals(1, repo.adminUsers.value.size)
                assertEquals(2, repo.interfaceStats.value.size)
                assertEquals(1, repo.systemLogs.value.size)

                // Card price resolved from profile VIP_Gold (comment price:500) even though card comment lacked price
                val card = repo.cards.value.firstOrNull { it.name == "card_777" }
                assertNotNull(card)
                assertEquals("500 ر.ي", card!!.price)

                // Active user enriched with DHCP hostName + hotspot server + interface nodeName + deviceModel
                val active = repo.activeUsers.value.firstOrNull { it.user == "card_777" }
                assertNotNull(active)
                assertEquals("Galaxy-S24", active!!.hostName)
                assertEquals("hs_main", active.serverName)
                assertTrue("Expected nodeName to reference wlan1, got ${active.nodeName}", active.nodeName.contains("wlan1"))
                assertTrue(active.deviceModel.isNotBlank())
            }
        } finally {
            running.set(false)
            runCatching { serverSocket.close() }
        }
    }

    @Test
    fun legacyLoginRejectsWrongPasswordWithClearAuthFailure() {
        val running = AtomicBoolean(true)
        val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port = serverSocket.localPort
        val challengeHex = "1122334455667788"

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
                                if (cmd == "/login") {
                                    val hasPass = sentence.any { it.startsWith("=password=") }
                                    val hasResp = sentence.any { it.startsWith("=response=") }
                                    when {
                                        hasPass -> {
                                            writeRosSentence(out, listOf("!trap", "=message=cannot log in"))
                                            writeRosSentence(out, listOf("!done"))
                                        }
                                        !hasResp -> writeRosSentence(out, listOf("!done", "=ret=$challengeHex"))
                                        else -> {
                                            writeRosSentence(out, listOf("!trap", "=message=invalid user name or password."))
                                            writeRosSentence(out, listOf("!done"))
                                        }
                                    }
                                } else {
                                    writeRosSentence(out, listOf("!done"))
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
            val client = RouterOsApiClient()
            runBlocking {
                val res = client.connectAndLogin(
                    host = "127.0.0.1",
                    port = port,
                    username = "admin",
                    password = "wrong_password",
                    timeoutMs = 1500,
                    useTls = false
                )
                assertTrue("Expected auth failure", res.isFailure)
                val msg = res.exceptionOrNull()?.message.orEmpty()
                assertTrue("Expected Arabic credential error, got: $msg", msg.contains("بيانات الدخول") || msg.contains("كلمة المرور"))
            }
        } finally {
            running.set(false)
            runCatching { serverSocket.close() }
        }
    }

    @Test
    fun directModeTriesRestJsonFallbackAndRejectsHtmlWithoutBrowser() {
        val running = AtomicBoolean(true)
        val htmlServer = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val htmlPort = htmlServer.localPort
        val authRestServer = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val authRestPort = authRestServer.localPort

        fun startHttpThread(server: ServerSocket, respond: (String, OutputStream) -> Unit): Thread {
            return Thread {
                while (running.get()) {
                    try {
                        val client = server.accept()
                        Thread {
                            client.use { sock ->
                                sock.soTimeout = 2000
                                val inp = sock.getInputStream()
                                val firstByte = inp.read()
                                if (firstByte == -1 || (firstByte != 'G'.code && firstByte != 'P'.code && firstByte != 'H'.code)) {
                                    return@use
                                }
                                val reqLine = buildString {
                                    append(firstByte.toChar())
                                    val reader = inp.bufferedReader()
                                    append(reader.readLine().orEmpty())
                                    while (true) {
                                        val l = reader.readLine() ?: break
                                        if (l.isEmpty()) break
                                    }
                                }
                                respond(reqLine, sock.getOutputStream())
                            }
                        }.start()
                    } catch (_: Exception) {
                        break
                    }
                }
            }.apply {
                isDaemon = true
                start()
            }
        }

        startHttpThread(htmlServer) { _, out ->
            val html = "<html><body><h1>MikroTik WebFig</h1></body></html>".toByteArray(Charsets.UTF_8)
            val hdr = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${html.size}\r\nConnection: close\r\n\r\n"
            out.write(hdr.toByteArray(Charsets.US_ASCII))
            out.write(html)
            out.flush()
        }

        val restHit = AtomicBoolean(false)
        startHttpThread(authRestServer) { reqLine, out ->
            if (reqLine.contains("/rest/system/resource")) {
                restHit.set(true)
            }
            val errJson = """{"error":401,"message":"Unauthorized"}""".toByteArray(Charsets.UTF_8)
            val hdr = "HTTP/1.1 401 Unauthorized\r\nWWW-Authenticate: Basic realm=\"RouterOS REST\"\r\nContent-Type: application/json\r\nContent-Length: ${errJson.size}\r\nConnection: close\r\n\r\n"
            out.write(hdr.toByteArray(Charsets.US_ASCII))
            out.write(errJson)
            out.flush()
        }

        try {
            val repo = MikrotikRepository(appDao = null)
            runBlocking {
                // 1. HTML portal must be rejected even with autoDetectProtocol=true in direct mode (never opens browser)
                val htmlOutcome = repo.verifyAndConnectSmart(
                    RouterConnectionConfig(
                        name = "HTML Only",
                        ipAddress = "127.0.0.1",
                        port = htmlPort,
                        username = "admin",
                        password = "123",
                        useHttps = false,
                        autoDetectProtocol = true,
                        directApiOnly = true
                    )
                )
                assertTrue("Expected Failure for HTML portal in direct mode, got $htmlOutcome", htmlOutcome is MikrotikRepository.VerifyConnectOutcome.Failure)
                assertNull((htmlOutcome as MikrotikRepository.VerifyConnectOutcome.Failure).fallbackPortalUrl)

                // 2. Native RouterOS REST JSON endpoint is actively probed as fallback when socket API is closed
                val restOutcome = repo.verifyAndConnectSmart(
                    RouterConnectionConfig(
                        name = "REST Direct Router",
                        ipAddress = "127.0.0.1",
                        port = authRestPort,
                        username = "admin",
                        password = "wrong",
                        useHttps = false,
                        autoDetectProtocol = true,
                        directApiOnly = true
                    )
                )
                assertTrue("Expected REST fallback to hit /rest/system/resource", restHit.get())
                assertTrue("Expected Failure on 401 from REST API, got $restOutcome", restOutcome is MikrotikRepository.VerifyConnectOutcome.Failure)
                val fail = restOutcome as MikrotikRepository.VerifyConnectOutcome.Failure
                assertNull(fail.fallbackPortalUrl)
                assertTrue("Expected REST auth error message, got ${fail.message}", fail.message.contains("REST API"))
            }
        } finally {
            running.set(false)
            runCatching { htmlServer.close() }
            runCatching { authRestServer.close() }
        }
    }

    @Test
    fun verifyAndConnectSmartDoesNotAbortLocalSocketForCaptiveHtmlPortal() {
        val running = AtomicBoolean(true)
        val apiServer = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val apiPort = apiServer.localPort

        // API socket server takes 1350ms before answering /login (longer than old 1100ms webJob kill timer)
        val apiThread = Thread {
            while (running.get()) {
                try {
                    val client = apiServer.accept()
                    Thread {
                        client.use { sock ->
                            sock.soTimeout = 4000
                            val inp = sock.getInputStream()
                            val out = sock.getOutputStream()
                            while (running.get() && !sock.isClosed) {
                                val sentence = readRosSentence(inp)
                                if (sentence.isEmpty()) break
                                when (sentence.first()) {
                                    "/login" -> {
                                        Thread.sleep(1350)
                                        writeRosSentence(out, listOf("!done"))
                                    }
                                    "/system/identity/print" -> {
                                        writeRosSentence(out, listOf("!re", "=name=LocalDirectWins"))
                                        writeRosSentence(out, listOf("!done"))
                                    }
                                    "/system/resource/print" -> {
                                        writeRosSentence(out, listOf("!re", "=uptime=1d", "=version=7.15", "=board-name=hEX", "=cpu-load=10"))
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
        apiThread.isDaemon = true
        apiThread.start()

        try {
            val repo = MikrotikRepository(appDao = null)
            runBlocking {
                val cfg = RouterConnectionConfig(
                    name = "Local MikroTik",
                    ipAddress = "127.0.0.1",
                    port = apiPort,
                    username = "admin",
                    password = "123",
                    useHttps = false,
                    autoDetectProtocol = true,
                    directApiOnly = false
                )
                val outcome = repo.verifyAndConnectSmart(cfg)
                assertTrue(
                    "Expected local MikroTik API socket to win over web portal check, got $outcome",
                    outcome is MikrotikRepository.VerifyConnectOutcome.MikroTikSuccess
                )
                assertTrue(repo.resourceState.value.boardName.contains("LocalDirectWins"))
            }
        } finally {
            running.set(false)
            runCatching { apiServer.close() }
        }
    }

    @Test
    fun customPlatformModeOpensLocalIpWebPortalWhenRequested() {
        val running = AtomicBoolean(true)
        val portalServer = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val portalPort = portalServer.localPort

        Thread {
            while (running.get()) {
                try {
                    val s = portalServer.accept()
                    Thread {
                        s.use { sock ->
                            val reader = sock.getInputStream().bufferedReader()
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                            }
                            val html = "<html><head><title>APlusWiFi Subscriber Platform</title></head><body><h1>Login</h1></body></html>"
                            val resp = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${html.toByteArray().size}\r\nConnection: close\r\n\r\n$html"
                            sock.getOutputStream().write(resp.toByteArray())
                            sock.getOutputStream().flush()
                        }
                    }.start()
                } catch (_: Exception) {
                    break
                }
            }
        }.apply { isDaemon = true; start() }

        try {
            val repo = MikrotikRepository(appDao = null)
            runBlocking {
                // Option 2 (Custom Server / Platform Mode) opens local/custom IP web platform
                val platformOutcome = repo.verifyAndConnectSmart(
                    RouterConnectionConfig(
                        name = "My Platform",
                        ipAddress = "127.0.0.1",
                        port = portalPort,
                        username = "owner",
                        password = "secret",
                        useHttps = false,
                        autoDetectProtocol = true,
                        directApiOnly = false,
                        preferWebPortal = true
                    )
                )
                assertTrue(
                    "Expected WebPortalDetected in Custom Platform mode, got $platformOutcome",
                    platformOutcome is MikrotikRepository.VerifyConnectOutcome.WebPortalDetected
                )
                val detected = platformOutcome as MikrotikRepository.VerifyConnectOutcome.WebPortalDetected
                assertTrue(detected.url.contains("127.0.0.1:$portalPort"))

                // Also when preferWebPortal=false in Custom Mode and API/REST fail, CloudWebPortal opens on custom IP
                val smartPlatformOutcome = repo.verifyAndConnectSmart(
                    RouterConnectionConfig(
                        name = "My Platform",
                        ipAddress = "127.0.0.1",
                        port = portalPort,
                        username = "owner",
                        password = "secret",
                        useHttps = false,
                        autoDetectProtocol = false,
                        directApiOnly = false,
                        preferWebPortal = false
                    )
                )
                assertTrue(
                    "Expected WebPortalDetected on non-API CloudWebPortal IP in Custom Mode, got $smartPlatformOutcome",
                    smartPlatformOutcome is MikrotikRepository.VerifyConnectOutcome.WebPortalDetected
                )
            }
        } finally {
            running.set(false)
            runCatching { portalServer.close() }
        }
    }

    @Test
    fun directModeConnectsOnCustomNonDefaultPortAndDiagnosesRefusalClearly() {
        val running = AtomicBoolean(true)
        val customApiServer = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val customPort = customApiServer.localPort

        Thread {
            while (running.get()) {
                try {
                    val client = customApiServer.accept()
                    Thread {
                        client.use { sock ->
                            sock.soTimeout = 3000
                            val inp = sock.getInputStream()
                            val out = sock.getOutputStream()
                            while (running.get() && !sock.isClosed) {
                                val sentence = readRosSentence(inp)
                                if (sentence.isEmpty()) break
                                when (sentence.first()) {
                                    "/login" -> writeRosSentence(out, listOf("!done"))
                                    "/system/identity/print" -> {
                                        writeRosSentence(out, listOf("!re", "=name=CustomPortCore"))
                                        writeRosSentence(out, listOf("!done"))
                                    }
                                    "/system/resource/print" -> {
                                        writeRosSentence(out, listOf("!re", "=uptime=2h", "=version=7.14", "=board-name=RB750Gr3"))
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
        }.apply { isDaemon = true; start() }

        val closedSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val closedPort = closedSocket.localPort
        closedSocket.close()

        try {
            val repo = MikrotikRepository(appDao = null)
            runBlocking {
                // 1. Custom non-8728 API port configured from Advanced Options succeeds cleanly in direct mode
                val successOutcome = repo.verifyAndConnectSmart(
                    RouterConnectionConfig(
                        name = "شبكة النور",
                        ipAddress = "127.0.0.1",
                        port = customPort,
                        username = "admin",
                        password = "secret",
                        useHttps = false,
                        autoDetectProtocol = true,
                        directApiOnly = true,
                        preferWebPortal = false
                    )
                )
                assertTrue(
                    "Expected MikroTikSuccess on custom API port $customPort, got $successOutcome",
                    successOutcome is MikrotikRepository.VerifyConnectOutcome.MikroTikSuccess
                )
                assertEquals(customPort, (successOutcome as MikrotikRepository.VerifyConnectOutcome.MikroTikSuccess).resolvedConfig.port)
                assertEquals("شبكة النور", successOutcome.resolvedConfig.name)
                assertTrue(repo.resourceState.value.boardName.contains("CustomPortCore"))

                // 2. Closed port with autoDetectProtocol=false produces clear refusal guidance mentioning الخيارات المتقدمة
                val failOutcome = repo.verifyAndConnectSmart(
                    RouterConnectionConfig(
                        name = "راوتر مغلق",
                        ipAddress = "127.0.0.1",
                        port = closedPort,
                        username = "admin",
                        password = "secret",
                        useHttps = false,
                        autoDetectProtocol = false,
                        directApiOnly = true,
                        preferWebPortal = false
                    )
                )
                assertTrue("Expected Failure on closed port $closedPort, got $failOutcome", failOutcome is MikrotikRepository.VerifyConnectOutcome.Failure)
                val msg = (failOutcome as MikrotikRepository.VerifyConnectOutcome.Failure).message
                assertTrue("Expected refusal guidance mentioning الخيارات المتقدمة, got: $msg", msg.contains("الخيارات المتقدمة"))
            }
        } finally {
            running.set(false)
            runCatching { customApiServer.close() }
        }
    }

    @Test
    fun emptyRouterSnapshotIsRejectedSoUserIsNotTrappedInOfflineMode() {
        val emptyResource = com.example.data.model.RouterResource(
            uptime = "0s",
            version = "",
            boardName = ""
        )
        val hasEmptyData = com.example.data.local.RouterCacheManager.hasMeaningfulRouterData(
            resource = emptyResource,
            cards = emptyList(),
            profiles = emptyList(),
            activeUsers = emptyList(),
            servers = emptyList(),
            nodes = emptyList(),
            logs = emptyList()
        )
        org.junit.Assert.assertFalse("Empty zero-data snapshot must NOT be treated as valid offline cache", hasEmptyData)

        val validResource = emptyResource.copy(uptime = "2d4h", version = "7.14")
        val hasValidData = com.example.data.local.RouterCacheManager.hasMeaningfulRouterData(
            resource = validResource,
            cards = emptyList(),
            profiles = emptyList(),
            activeUsers = emptyList(),
            servers = emptyList(),
            nodes = emptyList(),
            logs = emptyList()
        )
        assertTrue("Router snapshot with real uptime/version should pass guard", hasValidData)
    }
}


