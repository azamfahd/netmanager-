package com.example

import com.example.data.local.BatchHistoryEntity
import com.example.data.model.HotspotCard
import com.example.ui.viewmodel.MainViewModel
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BatchSeparationUnitTest {

    @Test
    fun twoBatchesWithIdenticalCommentAndProfileStayStrictlySeparated() {
        val batch1Cards = listOf(
            HotspotCard(id = "*1", name = "1001", password = "", profile = "500", comment = "دفعة كروت هوتسبوت", price = "500 ر.ي"),
            HotspotCard(id = "*2", name = "1002", password = "", profile = "500", comment = "دفعة كروت هوتسبوت", price = "500 ر.ي")
        )
        val batch2Cards = listOf(
            HotspotCard(id = "*3", name = "2001", password = "", profile = "500", comment = "دفعة كروت هوتسبوت", price = "500 ر.ي"),
            HotspotCard(id = "*4", name = "2002", password = "", profile = "500", comment = "دفعة كروت هوتسبوت", price = "500 ر.ي"),
            HotspotCard(id = "*5", name = "2003", password = "", profile = "500", comment = "دفعة كروت هوتسبوت", price = "500 ر.ي")
        )
        val individualCard = HotspotCard(id = "*6", name = "9999", password = "", profile = "500", comment = "كرت فردي", price = "500 ر.ي")

        val batch1 = BatchHistoryEntity(
            batchId = "batch_1000000",
            name = "دفعة كروت هوتسبوت",
            profileName = "500",
            cardCount = 2,
            price = "500 ر.ي",
            createdAt = 1_700_000_000_000L,
            cardsJson = JSONArray(batch1Cards.map { it.name }).toString()
        )
        val batch2 = BatchHistoryEntity(
            batchId = "batch_2000000",
            name = "دفعة كروت هوتسبوت",
            profileName = "500",
            cardCount = 3,
            price = "500 ر.ي",
            createdAt = 1_700_000_060_000L,
            cardsJson = JSONArray(batch2Cards.map { it.name }).toString()
        )

        val result = MainViewModel.partitionCardsIntoIndependentBatches(
            allCards = batch1Cards + batch2Cards + individualCard,
            activeUserNamesLower = emptySet(),
            batches = listOf(batch1, batch2),
            printedKeys = setOf("batch_1000000"),
            timestampProvider = { 1_700_000_000_000L }
        )

        assertEquals(2, result.batchGroups.size)

        val newest = result.batchGroups[0]
        val older = result.batchGroups[1]

        assertEquals("batch_2000000", newest.batchKey)
        assertEquals(2, newest.batchNumber)
        assertEquals(3, newest.cards.size)
        assertEquals(listOf("2001", "2002", "2003"), newest.cards.map { it.name })
        assertFalse(newest.isPrintedBefore)

        assertEquals("batch_1000000", older.batchKey)
        assertEquals(1, older.batchNumber)
        assertEquals(2, older.cards.size)
        assertEquals(listOf("1001", "1002"), older.cards.map { it.name })
        assertTrue(older.isPrintedBefore)

        assertEquals(listOf("9999"), result.ungroupedCards.map { it.name })
        assertEquals(2, result.printableOptions.size)
        assertEquals("batch_2000000", result.printableOptions[0].batchKey)
        assertEquals("batch_1000000", result.printableOptions[1].batchKey)
    }

    @Test
    fun batchTracksReadyActiveUsedAndExpiredCardsAccurately() {
        val cards = listOf(
            HotspotCard(id = "*1", name = "c_ready", password = "", profile = "vip", price = ""),
            HotspotCard(id = "*2", name = "c_active", password = "", profile = "vip", uptime = "5m", bytesIn = 1024L, price = ""),
            HotspotCard(id = "*3", name = "c_used", password = "", profile = "vip", uptime = "15m", bytesIn = 4096L, price = ""),
            HotspotCard(id = "*4", name = "c_expired", password = "", profile = "vip", uptime = "1h", limitUptime = "1h", disabled = true, price = "")
        )
        val batch = BatchHistoryEntity(
            batchId = "batch_track_1",
            name = "مقهى الأمل",
            profileName = "vip",
            cardCount = 4,
            price = "1000 ر.ي",
            createdAt = 1_700_001_000_000L,
            cardsJson = JSONArray(cards.map { it.name }).toString()
        )

        val result = MainViewModel.partitionCardsIntoIndependentBatches(
            allCards = cards,
            activeUserNamesLower = setOf("c_active"),
            batches = listOf(batch),
            printedKeys = emptySet(),
            timestampProvider = { 1_700_001_000_000L }
        )

        assertEquals(1, result.batchGroups.size)
        val group = result.batchGroups.first()
        assertEquals(4, group.totalGeneratedCount)
        assertEquals(1, group.readyCount)
        assertEquals(1, group.activeCount)
        assertEquals(1, group.usedOfflineCount)
        assertEquals(1, group.expiredCount)
        assertEquals(listOf("c_ready"), group.readyCards.map { it.name })
        assertEquals(4000, group.totalValue.toInt())
        assertTrue(result.ungroupedCards.isEmpty())
    }

    @Test
    fun routerSideCardsWithSameCommentAtDifferentTimesSplitIntoSeparateClusters() {
        val t1 = 1_700_000_000_000L
        val t2 = 1_700_000_600_000L // 10 minutes later
        val cards = listOf(
            HotspotCard(id = "*10", name = "r1", password = "", profile = "200", comment = "دفعة"),
            HotspotCard(id = "*11", name = "r2", password = "", profile = "200", comment = "دفعة"),
            HotspotCard(id = "*12", name = "r3", password = "", profile = "200", comment = "دفعة"),
            HotspotCard(id = "*13", name = "r4", password = "", profile = "200", comment = "دفعة")
        )
        val timestamps = mapOf("r1" to t1, "r2" to t1 + 1000L, "r3" to t2, "r4" to t2 + 1000L)

        val result = MainViewModel.partitionCardsIntoIndependentBatches(
            allCards = cards,
            activeUserNamesLower = emptySet(),
            batches = emptyList(),
            printedKeys = emptySet(),
            timestampProvider = { timestamps[it.name] ?: 0L }
        )

        assertEquals(2, result.batchGroups.size)
        assertEquals(listOf("r3", "r4"), result.batchGroups[0].cards.map { it.name }.sorted())
        assertEquals(listOf("r1", "r2"), result.batchGroups[1].cards.map { it.name }.sorted())
        assertEquals(2, result.printableOptions.size)
    }
}
