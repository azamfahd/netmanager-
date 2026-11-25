package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.LocalCardEntity
import com.example.data.local.RouterTaskEntity
import com.example.data.local.RouterTaskQueueManager
import com.example.data.model.FinanceCloudValidator
import com.example.data.model.HotspotActiveUser
import com.example.data.model.HotspotCard
import com.example.data.model.HotspotProfile
import com.example.data.model.MonthlySalesSummary
import com.example.data.model.RouterConnectionConfig
import com.example.data.model.TimeFilterRange
import com.example.data.network.MikrotikRepository
import com.example.data.network.NetworkUtils
import com.example.data.network.RouterOsApiClient
import com.example.data.util.AppUpdateManager
import com.example.data.util.AppUpdateService
import com.example.data.util.FinanceAnalyticsEngine
import com.example.data.util.SavedVoucherSettings
import com.example.data.util.VoucherSettingsManager
import com.example.ui.util.CardDesignConfig
import com.example.ui.util.CardTemplateType
import com.example.ui.util.VoucherPrintUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Calendar
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EngineeringRepairVerificationTest {

    private class RecordingPaintCanvas(bitmap: Bitmap) : Canvas(bitmap) {
        data class TextCall(val text: String, val x: Float, val y: Float, val color: Int)

        val textCalls = mutableListOf<TextCall>()

        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            textCalls.add(TextCall(text, x, y, paint.color))
            super.drawText(text, x, y, paint)
        }
    }

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

    @Test
    fun migration19To20PreservesValidPosDataCleansOrphansAndEnforcesCascadeDelete() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_19_20_verify_${System.nanoTime()}.db"
        context.deleteDatabase(dbName)

        val callback = object : SupportSQLiteOpenHelper.Callback(20) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                // Create v19 schema without FKs
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pos_agents (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        phone TEXT NOT NULL,
                        location TEXT NOT NULL,
                        discountPercent REAL NOT NULL,
                        createdAt INTEGER NOT NULL,
                        notes TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pos_card_assignments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        agentId INTEGER NOT NULL,
                        agentName TEXT NOT NULL,
                        cardName TEXT NOT NULL,
                        batchId TEXT NOT NULL,
                        profileName TEXT NOT NULL,
                        priceAmount REAL NOT NULL,
                        discountPercent REAL NOT NULL,
                        assignedAt INTEGER NOT NULL,
                        isSold INTEGER NOT NULL,
                        soldAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pos_payments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        agentId INTEGER NOT NULL,
                        agentName TEXT NOT NULL,
                        amount REAL NOT NULL,
                        paymentDate INTEGER NOT NULL,
                        paymentNote TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )
        try {
            val db = helper.writableDatabase
            // Seed valid agent (id=10) + child records + orphan rows (agentId=999)
            db.execSQL(
                "INSERT INTO pos_agents (id, name, phone, location, discountPercent, createdAt, notes) VALUES (10, 'أحمد', '777', 'تعز', 15.0, 1000, '')"
            )
            db.execSQL(
                "INSERT INTO pos_card_assignments (id, agentId, agentName, cardName, batchId, profileName, priceAmount, discountPercent, assignedAt, isSold, soldAt) VALUES (1, 10, 'أحمد', 'C100', 'b1', '500', 500.0, 15.0, 1000, 1, 2000)"
            )
            db.execSQL(
                "INSERT INTO pos_card_assignments (id, agentId, agentName, cardName, batchId, profileName, priceAmount, discountPercent, assignedAt, isSold, soldAt) VALUES (2, 999, 'محذوف', 'CORPHAN', 'b1', '500', 500.0, 15.0, 1000, 0, 0)"
            )
            db.execSQL(
                "INSERT INTO pos_payments (id, agentId, agentName, amount, paymentDate, paymentNote) VALUES (1, 10, 'أحمد', 425.0, 2000, 'دفعة')"
            )
            db.execSQL(
                "INSERT INTO pos_payments (id, agentId, agentName, amount, paymentDate, paymentNote) VALUES (2, 999, 'محذوف', 100.0, 2000, 'يتيم')"
            )

            // Execute Room's actual MIGRATION_19_20
            AppDatabase.MIGRATION_19_20.migrate(db)

            // Verify valid rows preserved non-destructively & orphans removed
            db.query("SELECT cardName, priceAmount FROM pos_card_assignments").use { c ->
                assertEquals(1, c.count)
                assertTrue(c.moveToFirst())
                assertEquals("C100", c.getString(0))
                assertEquals(500.0, c.getDouble(1), 0.01)
            }
            db.query("SELECT amount, paymentNote FROM pos_payments").use { c ->
                assertEquals(1, c.count)
                assertTrue(c.moveToFirst())
                assertEquals(425.0, c.getDouble(0), 0.01)
                assertEquals("دفعة", c.getString(1))
            }

            // Enable foreign keys and delete agent #10 -> both child tables must cascade-delete
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL("DELETE FROM pos_agents WHERE id = 10")

            db.query("SELECT COUNT(*) FROM pos_card_assignments").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM pos_payments").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
        } finally {
            helper.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun routerOsApiClientPropagatesTrapFailureInsteadOfFalseSuccess() {
        val running = AtomicBoolean(true)
        val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port = serverSocket.localPort

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
                                    cmd == "/ip/hotspot/user/print" -> {
                                        writeRosSentence(out, listOf("!re", "=.id=*1", "=name=card_01"))
                                        writeRosSentence(out, listOf("!done"))
                                    }
                                    cmd == "/ip/hotspot/user/set" || cmd == "/ip/hotspot/user/remove" -> {
                                        writeRosSentence(out, listOf("!trap", "=message=permission denied"))
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
            val api = RouterOsApiClient()
            runBlocking {
                val login = api.connectAndLogin("127.0.0.1", port, "admin", "123", timeoutMs = 2000, useTls = false)
                assertTrue("Expected binary login success", login.isSuccess)

                val toggleResult = api.toggleCardDisabled("*1", currentDisabled = false, cardName = "card_01")
                assertTrue("Expected !trap failure on toggle", toggleResult.isFailure)
                assertTrue(
                    "Expected !trap message preserved, got ${toggleResult.exceptionOrNull()?.message}",
                    toggleResult.exceptionOrNull()?.message?.contains("permission denied", ignoreCase = true) == true
                )

                val deleteResult = api.deleteCard("*1", cardName = "card_01")
                assertTrue("Expected !trap failure on delete when router denies permission", deleteResult.isFailure)
                assertTrue(
                    deleteResult.exceptionOrNull()?.message?.contains("permission denied", ignoreCase = true) == true
                )
            }
        } finally {
            running.set(false)
            runCatching { serverSocket.close() }
        }
    }

    @Test
    fun voucherPrintUtilsRendersSecondPageAndAppliesColorAndOffsets() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cards = (1..65).map { idx ->
            HotspotCard(
                id = "*$idx",
                name = "CARD${1000 + idx}",
                password = "",
                profile = "500",
                limitUptime = "1d",
                limitBytesTotal = 1024L * 1024L * 1024L,
                price = "500 ر.ي"
            )
        }

        // Page index 1 (second page) must contain cards 61..65 and must NOT be blank white
        val page2Bitmap = VoucherPrintUtils.renderPageBitmap(
            context = context,
            cards = cards,
            pageIndex = 1,
            config = CardDesignConfig(columns = 4, rows = 15, templateType = CardTemplateType.SCOUD_BLUE),
            targetWidth = 600
        )
        assertNotNull(page2Bitmap)
        try {
            var nonWhitePixels = 0
            val stepX = (page2Bitmap!!.width / 60).coerceAtLeast(1)
            val stepY = (page2Bitmap.height / 60).coerceAtLeast(1)
            for (y in 0 until page2Bitmap.height step stepY) {
                for (x in 0 until page2Bitmap.width step stepX) {
                    if (page2Bitmap.getPixel(x, y) != Color.WHITE) {
                        nonWhitePixels++
                    }
                }
            }
            assertTrue("Expected page 2 bitmap to contain rendered cards, not blank white", nonWhitePixels > 5)
        } finally {
            page2Bitmap?.recycle()
        }

        // Verify customCodeColorArgb and X/Y offsets apply cleanly
        val customBg = Bitmap.createBitmap(200, 80, Bitmap.Config.ARGB_8888)
        val outZero = Bitmap.createBitmap(1240, 1754, Bitmap.Config.ARGB_8888)
        val outShifted = Bitmap.createBitmap(1240, 1754, Bitmap.Config.ARGB_8888)
        try {
            val canvasZero = RecordingPaintCanvas(outZero)
            VoucherPrintUtils.drawCardsPage(
                canvas = canvasZero,
                pageIndex = 0,
                pageCards = listOf(cards.first()),
                config = CardDesignConfig(
                    templateType = CardTemplateType.CUSTOM_IMAGE,
                    offsetXPercent = 0f,
                    offsetYPercent = 0f,
                    customCodeColorArgb = Color.RED
                ),
                customBgBitmap = customBg,
                pageWidth = 1240,
                pageHeight = 1754
            )

            val canvasShifted = RecordingPaintCanvas(outShifted)
            VoucherPrintUtils.drawCardsPage(
                canvas = canvasShifted,
                pageIndex = 0,
                pageCards = listOf(cards.first()),
                config = CardDesignConfig(
                    templateType = CardTemplateType.CUSTOM_IMAGE,
                    offsetXPercent = 25f,
                    offsetYPercent = 15f,
                    customCodeColorArgb = Color.RED
                ),
                customBgBitmap = customBg,
                pageWidth = 1240,
                pageHeight = 1754
            )

            val callZero = canvasZero.textCalls.single { it.text == "CARD1001" }
            val callShifted = canvasShifted.textCalls.single { it.text == "CARD1001" }
            assertEquals(Color.RED, callZero.color)
            assertEquals(Color.RED, callShifted.color)
            assertTrue("Expected positive X offset shift", callShifted.x > callZero.x + 10f)
            assertTrue("Expected positive Y offset shift", callShifted.y > callZero.y + 5f)
        } finally {
            customBg.recycle()
            outZero.recycle()
            outShifted.recycle()
        }
    }

    @Test
    fun voucherSettingsManagerRoundTripsCodeLengthAndLegacyKey() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = VoucherSettingsManager(context)
        manager.resetToDefaults()

        manager.saveSettings(SavedVoucherSettings(codeLength = "8", batchCount = "50"))
        val loaded = manager.loadSettings()
        assertEquals("8", loaded.codeLength)
        assertEquals("50", loaded.batchCount)

        // Simulate pre-fix preferences storing camelCase "codeLength" only
        val prefs = context.getSharedPreferences("voucher_generation_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("code_length").putString("codeLength", "7").apply()
        assertEquals("7", manager.loadSettings().codeLength)
    }

    @Test
    fun appUpdateManagerEnforcesHttpsSha256AndMinorVersionCheck() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertTrue(AppUpdateManager.isMinorUpdate("v1.5.0", "1.4.0"))
        assertTrue(AppUpdateManager.isMinorUpdate("1.4.2-beta", "1.4.0"))
        assertFalse(AppUpdateManager.isMinorUpdate("v2.0.0", "1.4.0"))

        assertTrue(AppUpdateManager.isSecureUpdateUrl("https://github.com/azamfahd/netmanager/releases/download/v1.0/app.apk"))
        assertFalse(AppUpdateManager.isSecureUpdateUrl("http://insecure.example.com/app.apk"))

        runBlocking {
            val rejected = AppUpdateManager.downloadApk(
                context = context,
                downloadUrl = "http://insecure.example.com/app.apk"
            ) { _, _, _ -> }
            assertTrue("Expected HTTP download URL to be rejected", rejected.isFailure)
        }

        val tempFile = File.createTempFile("sha_test", ".bin", context.cacheDir)
        try {
            tempFile.writeBytes("abc".toByteArray(Charsets.UTF_8))
            // Standard SHA-256("abc")
            assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                AppUpdateManager.computeFileSha256(tempFile)
            )
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun financeAnalyticsEnginePreservesNegativeProfitWhileCloudValidatorRejectsInvalidUploads() {
        val novTs = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.NOVEMBER, 10, 12, 0, 0)
        }.timeInMillis

        val profiles = listOf(
            HotspotProfile(id = "p_loss", name = "LossPkg", price = "400 ر.ي", priceNumber = 400.0, costPrice = 600.0),
            HotspotProfile(id = "p_win", name = "WinPkg", price = "500 ر.ي", priceNumber = 500.0, costPrice = 100.0)
        )
        val cards = listOf(
            HotspotCard(id = "*1", name = "loss_card", password = "1", profile = "LossPkg", uptime = "1h", bytesIn = 1024L),
            HotspotCard(id = "*2", name = "win_card", password = "1", profile = "WinPkg", uptime = "1h", bytesIn = 1024L)
        )
        val localCards = listOf(
            LocalCardEntity(name = "loss_card", cardId = "*1", profile = "LossPkg", createdTimestamp = novTs),
            LocalCardEntity(name = "win_card", cardId = "*2", profile = "WinPkg", createdTimestamp = novTs)
        )

        val stats = FinanceAnalyticsEngine.computeDetailedPackageProfitStats(
            cardList = cards,
            profileList = profiles,
            pricingList = emptyList(),
            assignmentList = emptyList(),
            timeFilter = TimeFilterRange.ALL
        )
        val lossStat = stats.first { it.profileName == "LossPkg" }
        val winStat = stats.first { it.profileName == "WinPkg" }

        assertEquals(-200.0, lossStat.profitPerCard, 0.01)
        assertEquals(-200.0, lossStat.netProfit, 0.01)
        assertEquals(0f, lossStat.profitSharePercent, 0.01f)
        assertEquals(400.0, winStat.netProfit, 0.01)
        assertEquals(100f, winStat.profitSharePercent, 0.01f)

        // Loss-only monthly calculation must preserve true negative netProfit (-200)
        val lossMonths = FinanceAnalyticsEngine.computeMonthlySalesList(
            allCards = listOf(cards.first()),
            localCards = listOf(localCards.first()),
            profiles = listOf(profiles.first()),
            pricings = emptyList(),
            assignments = emptyList(),
            batches = emptyList(),
            loginHistory = emptyList(),
            archivedSessions = emptyList(),
            savedMonthlyRecords = emptyList(),
            nowMillis = novTs
        )
        val novLossMonth = lossMonths.first { it.monthKey == "2026-11" }
        assertEquals(-200.0, novLossMonth.netProfit, 0.01)

        // Cloud validator continues rejecting negative netProfit uploads as intended
        assertNull(
            FinanceCloudValidator.validateMonthlySummaryForUpload(
                MonthlySalesSummary(
                    monthKey = "2026-11",
                    monthName = "نوفمبر 2026",
                    totalRevenue = 400.0,
                    totalCost = 600.0,
                    netProfit = -200.0
                ),
                userId = "user1"
            )
        )
    }

    @Test
    fun networkUtilsParsesFractionalGbAndHotspotCardSeparatesManualDisableFromExpiry() {
        assertEquals(5120L, NetworkUtils.parseSizeToBytes("5GB") / (1024L * 1024L))
        assertEquals(2560L, NetworkUtils.parseSizeToBytes("2.5G") / (1024L * 1024L))

        val manuallyDisabledUnused = HotspotCard(
            id = "*1",
            name = "unused_disabled",
            password = "",
            disabled = true,
            limitUptime = "1d",
            uptime = "0s",
            bytesIn = 0L,
            bytesOut = 0L
        )
        assertTrue(manuallyDisabledUnused.isManuallyDisabled)
        assertFalse(manuallyDisabledUnused.isExpired)
        assertTrue(manuallyDisabledUnused.expiryReason.contains("معطل يدوياً"))

        val exhausted = HotspotCard(
            id = "*2",
            name = "used_quota",
            password = "",
            disabled = true,
            limitBytesTotal = 1000L,
            bytesIn = 600L,
            bytesOut = 500L
        )
        assertTrue(exhausted.isExpired)
        assertFalse(exhausted.isManuallyDisabled)
    }

    @Test
    fun routerTaskQueueManagerCoalescesPerTypeAndRetriesFailedTasksWithReset() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

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
                                    val line = reader.readLine() ?: break
                                    if (line.isEmpty()) break
                                }
                            }
                            val body = when {
                                reqLine.contains("/rest/system/resource") ->
                                    """{"uptime":"1d","version":"7.15","board-name":"RB5009","cpu-load":5,"free-memory":536870912,"total-memory":1073741824}"""
                                reqLine.contains("/rest/system/identity") -> """{"name":"QueueRouter"}"""
                                reqLine.contains("/rest/ip/hotspot/user") ->
                                    """[{"id":"*99",".id":"*99","name":"Q_CARD","profile":"default"}]"""
                                else -> "{}"
                            }
                            val bytes = body.toByteArray(Charsets.UTF_8)
                            val out = sock.getOutputStream()
                            out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
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
            val dao = db.appDao()
            val queueManager = RouterTaskQueueManager(dao) { "127.0.0.1" }
            val repo = MikrotikRepository(appDao = dao)

            runBlocking {
                // Coalescing preserves distinct task types for same card while replacing duplicate pending task of same type
                val card = HotspotCard(id = "*99", name = "Q_CARD", password = "1", profile = "default")
                queueManager.enqueueEditCard(card.copy(comment = "v1"))
                queueManager.enqueueEditCard(card.copy(comment = "v2"))
                queueManager.enqueueToggleDisabled("*99", "Q_CARD", targetDisabled = true)

                val activeBeforeRetry = dao.getAllActiveTasksFlow().first()
                assertEquals(2, activeBeforeRetry.size)
                assertEquals(1, activeBeforeRetry.count { it.taskType == RouterTaskEntity.TYPE_EDIT_CARD })
                assertEquals(1, activeBeforeRetry.count { it.taskType == RouterTaskEntity.TYPE_TOGGLE_CARD })

                // Also insert a previously FAILED task with high retryCount
                dao.clearCompletedTasks()
                dao.deleteTasksByCardName("127.0.0.1", "Q_CARD")
                dao.insertTask(
                    RouterTaskEntity.createToggleTask("*99", "Q_CARD", targetDisabled = true, routerKey = "127.0.0.1").copy(
                        status = RouterTaskEntity.STATUS_FAILED,
                        retryCount = 8,
                        nextAttemptTimestamp = System.currentTimeMillis() + 999_999L,
                        lastError = "old timeout"
                    )
                )

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
                assertTrue("Expected mock REST connection success: ${conn.exceptionOrNull()?.message}", conn.isSuccess)

                val result = queueManager.forceRetryAllPending(repo)
                assertEquals(1, result.processedCount)
                assertEquals(1, result.successCount)
                assertEquals(0, result.failedCount)
                assertNotEquals(0, result.processedCount)
                assertTrue(dao.getAllRetryableTasks("127.0.0.1").isEmpty())
            }
        } finally {
            running.set(false)
            runCatching { serverSocket.close() }
            db.close()
        }
    }

    @Test
    fun kickUserRestPropagatesFailureAndFallsBackToUserManagerSession() {
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
                            val inp = sock.getInputStream()
                            val firstByte = inp.read()
                            if (firstByte == -1) return@use
                            val reader = inp.bufferedReader()
                            val reqLine = buildString {
                                append(firstByte.toChar())
                                append(reader.readLine().orEmpty())
                            }
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                            }
                            val (status, body) = when {
                                reqLine.contains("/rest/system/resource") ->
                                    "200 OK" to """{"uptime":"1d","version":"7.15","board-name":"RB5009","cpu-load":5,"free-memory":536870912,"total-memory":1073741824}"""
                                reqLine.contains("/rest/system/identity") ->
                                    "200 OK" to """{"name":"RestKickRouter"}"""
                                reqLine.startsWith("DELETE /rest/ip/hotspot/active/*A1") ->
                                    "403 Forbidden" to """{"error":403,"message":"permission denied"}"""
                                reqLine.startsWith("GET /rest/ip/hotspot/active?user=hs_fail") ->
                                    "200 OK" to "[]"
                                reqLine.startsWith("GET /rest/user-manager/session?user=hs_fail") ->
                                    "403 Forbidden" to """{"error":403}"""
                                reqLine.startsWith("DELETE /rest/ip/hotspot/active/*A2") ->
                                    "404 Not Found" to """{"error":404}"""
                                reqLine.startsWith("GET /rest/ip/hotspot/active?user=um_user") ->
                                    "200 OK" to "[]"
                                reqLine.startsWith("GET /rest/user-manager/session?user=um_user") ->
                                    "200 OK" to """[{".id":"*S2","user":"um_user","active":"true"}]"""
                                reqLine.startsWith("DELETE /rest/user-manager/session/*S2") ->
                                    "200 OK" to "{}"
                                else -> "200 OK" to "{}"
                            }
                            val bytes = body.toByteArray(Charsets.UTF_8)
                            val out = sock.getOutputStream()
                            out.write("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
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

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = MikrotikRepository(appDao = db.appDao())
            val hsFailUser = HotspotActiveUser(
                id = "*A1",
                user = "hs_fail",
                address = "10.0.0.2",
                macAddress = "AA:BB:CC:DD:EE:01",
                uptime = "1m",
                bytesIn = 100L,
                bytesOut = 200L
            )
            val umUser = HotspotActiveUser(
                id = "*A2",
                user = "um_user",
                address = "10.0.0.3",
                macAddress = "AA:BB:CC:DD:EE:02",
                uptime = "2m",
                bytesIn = 100L,
                bytesOut = 200L
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
                assertTrue("Expected mock REST login success", conn.isSuccess)
                repo.restoreFromSnapshot(
                    repo.getCurrentSnapshot("127.0.0.1", "RestKickRouter")
                        .copy(activeUsers = listOf(hsFailUser, umUser))
                )

                val failResult = repo.kickUser(hsFailUser)
                assertTrue("Expected REST kick failure when router rejects disconnect", failResult.isFailure)
                assertTrue("Active user must remain when router rejects kick", repo.activeUsers.value.any { it.id == "*A1" })

                val umResult = repo.kickUser(umUser)
                assertTrue("Expected UserManager REST session fallback to succeed", umResult.isSuccess)
                assertFalse("UM user should be removed after session delete", repo.activeUsers.value.any { it.id == "*A2" })
            }
        } finally {
            running.set(false)
            runCatching { serverSocket.close() }
            db.close()
        }
    }

    @Test
    fun toggleCardDisabledFallsBackToUserManagerV7OnMissingHotspotItem() {
        val running = AtomicBoolean(true)
        val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port = serverSocket.localPort
        val umSetCalled = AtomicBoolean(false)

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
                                    cmd == "/ip/hotspot/user/print" || cmd == "/user-manager/user/print" || cmd == "/tool/user-manager/user/print" ->
                                        writeRosSentence(out, listOf("!done"))
                                    cmd == "/ip/hotspot/user/set" -> {
                                        writeRosSentence(out, listOf("!trap", "=message=no such item"))
                                        writeRosSentence(out, listOf("!done"))
                                    }
                                    cmd == "/user-manager/user/set" -> {
                                        umSetCalled.set(true)
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
            val api = RouterOsApiClient()
            runBlocking {
                val login = api.connectAndLogin("127.0.0.1", port, "admin", "123", timeoutMs = 2000, useTls = false)
                assertTrue("Expected binary login success", login.isSuccess)

                val result = api.toggleCardDisabled("99999", currentDisabled = false, cardName = "um_card")
                assertTrue("Expected fallback to /user-manager/user/set to succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
                assertTrue("Expected /user-manager/user/set to be invoked", umSetCalled.get())
            }
        } finally {
            running.set(false)
            runCatching { serverSocket.close() }
        }
    }

    @Test
    fun restoreLocalCardReinsertsOptimisticallyDeletedCardAndNotificationIdIsDistinct() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = MikrotikRepository(appDao = db.appDao())
            val card = HotspotCard(id = "*55", name = "CARD55", password = "1", profile = "default")
            repo.restoreFromSnapshot(repo.getCurrentSnapshot("127.0.0.1", "Test").copy(cards = listOf(card)))
            assertEquals(1, repo.cards.value.size)

            repo.deleteLocalCard(card.id, card.name)
            assertTrue(repo.cards.value.isEmpty())

            repo.restoreLocalCard(card)
            assertEquals(1, repo.cards.value.size)
            assertEquals("CARD55", repo.cards.value.first().name)
            assertEquals(1002, AppUpdateService.RESULT_NOTIFICATION_ID)
        } finally {
            db.close()
        }
    }
}

