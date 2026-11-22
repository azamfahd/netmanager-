package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.BatchGenerationConfig
import com.example.data.model.HotspotCard
import com.example.data.model.HotspotProfile
import com.example.data.model.RouterConnectionConfig
import com.example.data.network.MikrotikRepository
import com.example.data.util.SerialCorner
import com.example.data.util.VoucherSerialEngine
import com.example.ui.util.CardDesignConfig
import com.example.ui.util.CardTemplateType
import com.example.ui.util.VoucherPrintUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class VoucherSerialStockUnitTest {

    private class TextCaptureCanvas(bitmap: Bitmap) : Canvas(bitmap) {
        data class DrawnText(val text: String, val x: Float, val y: Float, val color: Int)
        val drawnTexts = mutableListOf<DrawnText>()

        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            drawnTexts.add(DrawnText(text, x, y, paint.color))
            super.drawText(text, x, y, paint)
        }
    }

    private fun readRosLength(inp: InputStream): Int {
        val b0 = inp.read()
        if (b0 < 0) return -1
        return when {
            (b0 and 0x80) == 0 -> b0
            (b0 and 0xC0) == 0x80 -> ((b0 and 0x3F) shl 8) or inp.read()
            (b0 and 0xE0) == 0xC0 -> ((b0 and 0x1F) shl 16) or (inp.read() shl 8) or inp.read()
            (b0 and 0xF0) == 0xE0 -> ((b0 and 0x0F) shl 24) or (inp.read() shl 16) or (inp.read() shl 8) or inp.read()
            else -> ((inp.read() and 0xFF) shl 24) or ((inp.read() and 0xFF) shl 16) or ((inp.read() and 0xFF) shl 8) or (inp.read() and 0xFF)
        }
    }

    private fun readRosSentence(inp: InputStream): List<String> {
        val words = mutableListOf<String>()
        while (true) {
            val len = readRosLength(inp)
            if (len < 0) return emptyList()
            if (len == 0) break
            val buf = ByteArray(len)
            var off = 0
            while (off < len) {
                val r = inp.read(buf, off, len - off)
                if (r < 0) return emptyList()
                off += r
            }
            words.add(String(buf, Charsets.UTF_8))
        }
        return words
    }

    private fun writeRosLength(out: OutputStream, len: Int) {
        if (len < 0x80) {
            out.write(len)
        } else if (len < 0x4000) {
            val v = len or 0x8000
            out.write((v ushr 8) and 0xFF)
            out.write(v and 0xFF)
        } else {
            val v = len or 0xC00000
            out.write((v ushr 16) and 0xFF)
            out.write((v ushr 8) and 0xFF)
            out.write(v and 0xFF)
        }
    }

    private fun writeRosSentence(out: OutputStream, words: List<String>) {
        val baos = ByteArrayOutputStream()
        for (w in words) {
            val bytes = w.toByteArray(Charsets.UTF_8)
            writeRosLength(baos, bytes.size)
            baos.write(bytes)
        }
        baos.write(0)
        out.write(baos.toByteArray())
        out.flush()
    }

    @Test
    fun serialEngineSelectsNext48ConsecutiveDisabledFrom4000StockAfterLastActive() {
        val stock4000 = (1..4000).map { idx ->
            val enabled = idx in 1..48 && idx != 5 // Keep #5 disabled below watermark to test watermark skipping
            HotspotCard(
                id = "*${idx.toString(16)}",
                name = "VCH${idx.toString().padStart(4, '0')}",
                password = "",
                profile = "500",
                comment = "مخزون رقم seq:${idx.toString().padStart(4, '0')}",
                disabled = !enabled
            )
        }.reversed() // Intentionally scrambled input order

        val slice1 = VoucherSerialEngine.analyzeStockAndSelectSlice(
            scopedCards = stock4000,
            requestedCount = 48,
            lastSavedSerial = null
        )

        assertEquals(4000, slice1.totalInScope)
        assertEquals(48L, slice1.highestUsedOrActiveSerial)
        assertEquals(48L, slice1.effectiveLastSerial)
        assertEquals(3953, slice1.totalDisabledStock) // 49..4000 plus #5
        assertEquals(48, slice1.selectedCards.size)
        assertEquals("VCH0049", slice1.selectedCards.first().name)
        assertEquals("VCH0096", slice1.selectedCards.last().name)
        assertEquals("0049", slice1.startSerialLabel)
        assertEquals("0096", slice1.endSerialLabel)
        assertFalse(slice1.selectedCards.any { it.name == "VCH0005" })

        // When lastSavedSerial is 96 (e.g., queued for print before router sync), slice advances to 97..144
        val slice2 = VoucherSerialEngine.analyzeStockAndSelectSlice(
            scopedCards = stock4000,
            requestedCount = 48,
            lastSavedSerial = 96L
        )
        assertEquals(96L, slice2.effectiveLastSerial)
        assertEquals("VCH0097", slice2.selectedCards.first().name)
        assertEquals("VCH0144", slice2.selectedCards.last().name)
    }

    @Test
    fun serialEngineSmartExtractionHandlesCommentTagCardNameAndTimestampFallback() {
        val fromSeqComment = HotspotCard(id = "*1", name = "AB9999", password = "", profile = "200", comment = "دفعة seq:0125")
        val fromHashComment = HotspotCard(id = "*2", name = "XY100", password = "", profile = "200", comment = "كرت #77")
        val fromNameSuffix = HotspotCard(id = "*3", name = "VIP0033", password = "", profile = "200", comment = "دفعة عامة")
        val fromNumericName = HotspotCard(id = "*4", name = "80042", password = "", profile = "200", comment = "لا يوجد تسلسل")
        val noSerialEarly = HotspotCard(id = "*A", name = "ALPHA", password = "", profile = "200", comment = "عام", createdAtHint = 1000L)
        val noSerialLate = HotspotCard(id = "*10", name = "BETA", password = "", profile = "200", comment = "عام", createdAtHint = 2000L)

        assertEquals(125L, VoucherSerialEngine.extractCardSerial(fromSeqComment))
        assertEquals(77L, VoucherSerialEngine.extractCardSerial(fromHashComment))
        assertEquals(33L, VoucherSerialEngine.extractCardSerial(fromNameSuffix))
        assertEquals(80042L, VoucherSerialEngine.extractCardSerial(fromNumericName))
        assertEquals(null, VoucherSerialEngine.extractCardSerial(noSerialEarly))

        assertEquals(10L, VoucherSerialEngine.parseRouterIdOrder("*A"))
        assertEquals(16L, VoucherSerialEngine.parseRouterIdOrder("*10"))

        val sorted = VoucherSerialEngine.sortCardsSequentially(
            listOf(noSerialLate, fromSeqComment, noSerialEarly, fromNameSuffix, fromHashComment)
        )
        assertEquals(
            listOf("VIP0033", "XY100", "AB9999", "ALPHA", "BETA"),
            sorted.map { it.card.name }
        )

        val suggested = VoucherSerialEngine.suggestNextStartSerial(listOf(fromNameSuffix, fromHashComment))
        assertEquals(78L, suggested)
        assertEquals("دفعة مخزون | seq:0005", VoucherSerialEngine.appendSerialToComment("دفعة مخزون", 5L))
    }

    @Test
    fun batchActivateCardsEnablesOnRouterAndPreservesPartialFailure() {
        val running = AtomicBoolean(true)
        val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port = serverSocket.localPort
        val patchedIds = CopyOnWriteArrayList<String>()

        val serverThread = Thread {
            while (running.get()) {
                try {
                    val client = serverSocket.accept()
                    Thread {
                        client.use { sock ->
                            sock.soTimeout = 3000
                            val inp = sock.getInputStream().bufferedReader()
                            val requestLine = inp.readLine() ?: return@use
                            var contentLength = 0
                            while (true) {
                                val line = inp.readLine() ?: break
                                if (line.isEmpty()) break
                                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                                }
                            }
                            if (contentLength > 0) {
                                val buf = CharArray(contentLength)
                                var readTotal = 0
                                while (readTotal < contentLength) {
                                    val n = inp.read(buf, readTotal, contentLength - readTotal)
                                    if (n < 0) break
                                    readTotal += n
                                }
                            }

                            val out = sock.getOutputStream()
                            val path = requestLine.split(" ").getOrNull(1).orEmpty()
                            val method = requestLine.split(" ").getOrNull(0).orEmpty()

                            fun respond(status: String, body: String) {
                                val bodyBytes = body.toByteArray(Charsets.UTF_8)
                                val headers = "HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${bodyBytes.size}\r\nConnection: close\r\n\r\n"
                                out.write(headers.toByteArray(Charsets.UTF_8))
                                out.write(bodyBytes)
                                out.flush()
                            }

                            when {
                                path.contains("/rest/system/resource") ->
                                    respond("200 OK", """{"board-name":"CHR","version":"7.14"}""")
                                path.contains("/rest/system/identity") ->
                                    respond("200 OK", """{"name":"StockRestRouter"}""")
                                method == "PATCH" && path.contains("/rest/ip/hotspot/user/") -> {
                                    val id = path.substringAfterLast("/")
                                    if (id == "*3") {
                                        respond("403 Forbidden", """{"error":403,"message":"permission denied"}""")
                                    } else {
                                        patchedIds.add(id)
                                        respond("200 OK", """{".id":"$id","disabled":"false"}""")
                                    }
                                }
                                method == "PATCH" ->
                                    respond("404 Not Found", """{"error":404,"message":"no such item"}""")
                                path.contains("/rest/ip/hotspot/user") ->
                                    respond("200 OK", "[]")
                                else ->
                                    respond("200 OK", "[]")
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

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = MikrotikRepository(appDao = db.appDao())
            val stockCards = listOf(
                HotspotCard(id = "*1", name = "S1", password = "", profile = "500", comment = "seq:0001", disabled = true),
                HotspotCard(id = "*2", name = "S2", password = "", profile = "500", comment = "seq:0002", disabled = true),
                HotspotCard(id = "*3", name = "S3", password = "", profile = "500", comment = "seq:0003", disabled = true),
                HotspotCard(id = "*4", name = "S4", password = "", profile = "500", comment = "seq:0004", disabled = true)
            )
            runBlocking {
                val conn = repo.verifyAndConnect(
                    RouterConnectionConfig(
                        ipAddress = "127.0.0.1",
                        port = port,
                        username = "admin",
                        password = "123",
                        useHttps = false,
                        autoDetectProtocol = false
                    )
                )
                assertTrue(conn.isSuccess)
                repo.restoreFromSnapshot(
                    repo.getCurrentSnapshot("127.0.0.1", "StockRestRouter").copy(cards = stockCards)
                )

                val result = repo.batchActivateCards(stockCards)
                assertEquals(3, result.activatedCards.size)
                assertEquals(1, result.failedCards.size)
                assertEquals("*3", result.failedCards.single().id)
                assertFalse(result.isCompleteSuccess)
                assertEquals(4L, result.highestActivatedSerial)
                assertTrue(patchedIds.containsAll(listOf("*1", "*2", "*4")))

                val currentCards = repo.cards.value.associateBy { it.id }
                assertFalse(currentCards.getValue("*1").disabled)
                assertFalse(currentCards.getValue("*2").disabled)
                assertTrue(currentCards.getValue("*3").disabled)
                assertFalse(currentCards.getValue("*4").disabled)
            }
        } finally {
            running.set(false)
            runCatching { serverSocket.close() }
            db.close()
        }
    }

    @Test
    fun generateBatchHonorsStartDisabledAndSequentialSerialComment() {
        val running = AtomicBoolean(true)
        val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port = serverSocket.localPort
        val addedSentences = CopyOnWriteArrayList<List<String>>()
        var idSeq = 10

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
                                when {
                                    cmd == "/login" -> writeRosSentence(out, listOf("!done"))
                                    cmd == "/system/resource/print" -> {
                                        writeRosSentence(out, listOf("!re", "=board-name=CHR", "=version=7.14"))
                                        writeRosSentence(out, listOf("!done"))
                                    }
                                    cmd == "/ip/hotspot/user/profile/print" -> {
                                        writeRosSentence(out, listOf("!re", "=.id=*1", "=name=500"))
                                        writeRosSentence(out, listOf("!done"))
                                    }
                                    cmd == "/ip/hotspot/user/add" -> {
                                        addedSentences.add(sentence)
                                        idSeq++
                                        writeRosSentence(out, listOf("!done", "=ret=*$idSeq"))
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

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = MikrotikRepository(appDao = db.appDao())
            runBlocking {
                val conn = repo.verifyAndConnect(
                    RouterConnectionConfig(
                        ipAddress = "127.0.0.1",
                        port = port,
                        username = "admin",
                        password = "123",
                        useHttps = false,
                        autoDetectProtocol = false,
                        directApiOnly = true
                    )
                )
                assertTrue("Expected binary login success: ${conn.exceptionOrNull()?.message}", conn.isSuccess)
                repo.restoreFromSnapshot(
                    repo.getCurrentSnapshot("127.0.0.1", "BinaryStockRouter").copy(
                        profiles = listOf(HotspotProfile(id = "*1", name = "500"))
                    )
                )

                val genRes = repo.generateBatch(
                    config = BatchGenerationConfig(
                        count = 3,
                        profile = "500",
                        codeLength = 6,
                        batchComment = "مخزون 500",
                        targetSystem = "hotspot",
                        profileSource = "hotspot",
                        startDisabled = true,
                        includeSerialInComment = true,
                        serialStartNumber = 1L
                    ),
                    dao = db.appDao()
                )

                assertTrue("Expected generateBatch success: ${genRes.exceptionOrNull()?.message}", genRes.isSuccess)
                val generated = genRes.getOrThrow().successfulCards
                assertEquals(3, generated.size)
                assertTrue(generated.all { it.disabled })
                assertEquals(3, addedSentences.size)
                assertTrue(addedSentences.all { it.contains("=disabled=yes") })
                assertTrue(addedSentences[0].any { it.contains("seq:0001") })
                assertTrue(addedSentences[1].any { it.contains("seq:0002") })
                assertTrue(addedSentences[2].any { it.contains("seq:0003") })
                assertTrue(repo.cards.value.all { it.disabled })
            }
        } finally {
            running.set(false)
            runCatching { serverSocket.close() }
            db.close()
        }
    }

    @Test
    fun voucherPrintUtilsDrawsSerialBadgeWhenEnabledAndHonorsCornerAndPrefix() {
        val pageBitmap = Bitmap.createBitmap(1240, 1754, Bitmap.Config.ARGB_8888)
        val customCardBitmap = Bitmap.createBitmap(300, 120, Bitmap.Config.ARGB_8888)
        val card = HotspotCard(
            id = "*49",
            name = "998877",
            password = "",
            profile = "500",
            comment = "seq:0049",
            price = "500 ر.ي",
            disabled = false
        )
        val serialMap = mapOf(card.id to "0049")

        try {
            val canvasDisabled = TextCaptureCanvas(pageBitmap)
            VoucherPrintUtils.drawCardsPage(
                canvas = canvasDisabled,
                pageIndex = 0,
                pageCards = listOf(card),
                config = CardDesignConfig(
                    templateType = CardTemplateType.CUSTOM_IMAGE,
                    showSerialNumber = false,
                    serialPrefix = "S/N:",
                    serialMap = serialMap
                ),
                customBgBitmap = customCardBitmap,
                pageWidth = 1240,
                pageHeight = 1754
            )
            assertFalse(canvasDisabled.drawnTexts.any { it.text.contains("0049") })

            val canvasTopEnd = TextCaptureCanvas(pageBitmap)
            VoucherPrintUtils.drawCardsPage(
                canvas = canvasTopEnd,
                pageIndex = 0,
                pageCards = listOf(card),
                config = CardDesignConfig(
                    templateType = CardTemplateType.CUSTOM_IMAGE,
                    showSerialNumber = true,
                    serialPrefix = "S/N:",
                    serialCorner = SerialCorner.TOP_END,
                    serialMap = serialMap
                ),
                customBgBitmap = customCardBitmap,
                pageWidth = 1240,
                pageHeight = 1754
            )
            val topEndCall = canvasTopEnd.drawnTexts.firstOrNull { it.text == "S/N:0049" }
            assertNotNull("Expected S/N:0049 drawn when showSerialNumber=true", topEndCall)

            val canvasBottomStart = TextCaptureCanvas(pageBitmap)
            VoucherPrintUtils.drawCardsPage(
                canvas = canvasBottomStart,
                pageIndex = 0,
                pageCards = listOf(card),
                config = CardDesignConfig(
                    templateType = CardTemplateType.CUSTOM_IMAGE,
                    showSerialNumber = true,
                    serialPrefix = "S/N:",
                    serialCorner = SerialCorner.BOTTOM_START,
                    serialMap = serialMap
                ),
                customBgBitmap = customCardBitmap,
                pageWidth = 1240,
                pageHeight = 1754
            )
            val bottomStartCall = canvasBottomStart.drawnTexts.firstOrNull { it.text == "S/N:0049" }
            assertNotNull("Expected S/N:0049 drawn in BOTTOM_START corner", bottomStartCall)
            assertTrue("BOTTOM_START must sit lower than TOP_END", bottomStartCall!!.y > topEndCall!!.y)
            assertTrue("BOTTOM_START must sit further right (RTL Start) than TOP_END", bottomStartCall.x > topEndCall.x)
        } finally {
            pageBitmap.recycle()
            customCardBitmap.recycle()
        }
    }
}
