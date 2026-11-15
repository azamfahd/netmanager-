package com.example

import com.example.data.local.LocalCardEntity
import com.example.data.local.PosAgentEntity
import com.example.data.local.PosCardAssignmentEntity
import com.example.data.local.PosPaymentEntity
import com.example.data.local.RouterTaskEntity
import com.example.data.model.HotspotCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class LocalFirstSyncAuditTest {

    @Test
    fun test50kCardsSortingSearchingAndPaginationPerformance() {
        val totalCards = 50_000
        val cards = ArrayList<HotspotCard>(totalCards)
        val now = System.currentTimeMillis()

        for (i in 1..totalCards) {
            cards.add(
                HotspotCard(
                    id = "*$i",
                    name = "user_${100000 + i}",
                    password = "pass$i",
                    profile = when (i % 3) {
                        0 -> "1hour"
                        1 -> "1day"
                        else -> "1month"
                    },
                    uptime = "${i % 60}m",
                    bytesIn = (i % 1000) * 1024L * 1024L,
                    bytesOut = (i % 500) * 1024L * 1024L,
                    limitUptime = if (i % 5 == 0) "1h" else "",
                    limitBytesTotal = if (i % 4 == 0) 500_000_000L else 0L,
                    comment = if (i % 10 == 0) "بطاقة تجريبية منتهية expired" else "بطاقة عادية",
                    disabled = (i % 7 == 0),
                    price = "200",
                    cardType = if (i % 8 == 0) "usermanager" else "hotspot"
                )
            )
        }
        assertEquals(totalCards, cards.size)

        // 1. Benchmark Search by Name
        val searchTarget = "user_145000"
        var foundCard: HotspotCard? = null
        val searchTime = measureTimeMillis {
            foundCard = cards.firstOrNull { it.name == searchTarget }
        }
        println("Performance: Searched 50,000 cards in ${searchTime}ms")
        assertEquals(searchTarget, foundCard?.name)
        assertTrue("Search in 50,000 cards should be under 100ms", searchTime < 100)

        // 2. Benchmark Filter by Profile & Status
        var filteredCount = 0
        val filterTime = measureTimeMillis {
            filteredCount = cards.filter { it.profile == "1day" && !it.disabled }.size
        }
        println("Performance: Filtered 50,000 cards in ${filterTime}ms (Count: $filteredCount)")
        assertTrue("Filtering 50,000 cards should take under 150ms", filterTime < 150)
        assertTrue(filteredCount > 0)

        // 3. Benchmark Pagination (Skip 200, Take 50)
        var pageItems: List<HotspotCard> = emptyList()
        val pageTime = measureTimeMillis {
            pageItems = cards.asSequence().drop(200).take(50).toList()
        }
        println("Performance: Extracted page of 50 from 50,000 in ${pageTime}ms")
        assertEquals(50, pageItems.size)
        assertTrue("Pagination in 50,000 items should take under 100ms", pageTime < 100)
    }

    @Test
    fun testDeduplicationAndTimestampResolution() {
        val originalCreatedTs = 1700000000000L
        val incomingCard = HotspotCard(
            id = "*99",
            name = "network_vip",
            password = "secret",
            profile = "unlimited",
            uptime = "45m",
            price = "500"
        )

        // First sync creates entity
        val entity1 = LocalCardEntity.fromHotspotCard(
            card = incomingCard,
            existingCreatedTs = originalCreatedTs,
            source = "router"
        )
        assertEquals(originalCreatedTs, entity1.createdTimestamp)
        assertEquals("network_vip", entity1.name)

        // Second sync with updated uptime should retain the exact original creation timestamp
        val updatedCard = incomingCard.copy(uptime = "2h 10m")
        val entity2 = LocalCardEntity.fromHotspotCard(
            card = updatedCard,
            existingCreatedTs = entity1.createdTimestamp,
            source = "incremental_sync"
        )
        assertEquals(
            "Incremental sync must preserve original creation timestamp to prevent re-sorting churn",
            originalCreatedTs,
            entity2.createdTimestamp
        )
        assertEquals("2h 10m", entity2.uptime)
        assertEquals("incremental_sync", entity2.sourceSystem)
    }

    @Test
    fun testExponentialBackoffScheduleProgression() {
        val now = System.currentTimeMillis()

        val delay0 = RouterTaskEntity.calculateNextAttempt(0, baseDelayMs = 2000L, maxDelayMs = 60000L) - now
        val delay1 = RouterTaskEntity.calculateNextAttempt(1, baseDelayMs = 2000L, maxDelayMs = 60000L) - now
        val delay2 = RouterTaskEntity.calculateNextAttempt(2, baseDelayMs = 2000L, maxDelayMs = 60000L) - now
        val delay3 = RouterTaskEntity.calculateNextAttempt(3, baseDelayMs = 2000L, maxDelayMs = 60000L) - now
        val delay4 = RouterTaskEntity.calculateNextAttempt(4, baseDelayMs = 2000L, maxDelayMs = 60000L) - now
        val delay5 = RouterTaskEntity.calculateNextAttempt(5, baseDelayMs = 2000L, maxDelayMs = 60000L) - now
        val delay6 = RouterTaskEntity.calculateNextAttempt(6, baseDelayMs = 2000L, maxDelayMs = 60000L) - now
        val delay7 = RouterTaskEntity.calculateNextAttempt(7, baseDelayMs = 2000L, maxDelayMs = 60000L) - now

        println("Exponential Backoff delays: 0=$delay0, 1=$delay1, 2=$delay2, 3=$delay3, 4=$delay4, 5=$delay5, 6=$delay6, 7=$delay7")

        assertTrue("Attempt 0 backoff ~2s", delay0 in 1800..2500)
        assertTrue("Attempt 1 backoff ~4s", delay1 in 3800..4500)
        assertTrue("Attempt 2 backoff ~8s", delay2 in 7800..8500)
        assertTrue("Attempt 3 backoff ~16s", delay3 in 15800..16500)
        assertTrue("Attempt 4 backoff ~32s", delay4 in 31800..32500)
        assertTrue("Attempt 5+ backoff capped at ~60s", delay5 in 59000..61000)
        assertTrue("Attempt 6+ backoff capped at ~60s", delay6 in 59000..61000)
        assertTrue("Attempt 7+ backoff capped at ~60s", delay7 in 59000..61000)
    }

    @Test
    fun testPosAccountingMathematicsAndDebtSettlement() {
        val agent = PosAgentEntity(
            id = 10,
            name = "مركز النور للإلكترونيات",
            discountPercent = 15.0 // 15% discount for the reseller
        )

        // 100 assigned cards, each 200 YER
        val assignments = (1..100).map { i ->
            PosCardAssignmentEntity(
                id = i.toLong(),
                agentId = agent.id,
                agentName = agent.name,
                cardName = "card_$i",
                priceAmount = 200.0,
                discountPercent = agent.discountPercent,
                isSold = (i <= 60) // 60 cards sold, 40 unsold
            )
        }

        // Reseller payments
        val payments = listOf(
            PosPaymentEntity(id = 1, agentId = agent.id, agentName = agent.name, amount = 5000.0),
            PosPaymentEntity(id = 2, agentId = agent.id, agentName = agent.name, amount = 3000.0)
        )

        val totalAssigned = assignments.size
        val soldCards = assignments.filter { it.isSold }
        val remainingCards = totalAssigned - soldCards.size

        val totalSalesVolume = soldCards.sumOf { it.priceAmount } // 60 * 200 = 12,000
        val totalCommission = soldCards.sumOf { it.priceAmount * (it.discountPercent / 100.0) } // 12,000 * 0.15 = 1,800
        val totalDueToOwner = totalSalesVolume - totalCommission // 12,000 - 1,800 = 10,200
        val totalPaid = payments.sumOf { it.amount } // 5,000 + 3,000 = 8,000
        val netDebt = (totalDueToOwner - totalPaid).coerceAtLeast(0.0) // 10,200 - 8,000 = 2,200

        assertEquals(100, totalAssigned)
        assertEquals(60, soldCards.size)
        assertEquals(40, remainingCards)
        assertEquals(12000.0, totalSalesVolume, 0.001)
        assertEquals(1800.0, totalCommission, 0.001)
        assertEquals(10200.0, totalDueToOwner, 0.001)
        assertEquals(8000.0, totalPaid, 0.001)
        assertEquals(2200.0, netDebt, 0.001)
    }

    @Test
    fun testHotspotCardExpiryAndSystemClassification() {
        val normalCard = HotspotCard(
            id = "*1",
            name = "active_user",
            password = "123",
            limitBytesTotal = 1000L,
            bytesIn = 200L,
            bytesOut = 300L,
            cardType = "hotspot"
        )
        assertFalse(normalCard.isExpiredQuota)
        assertFalse(normalCard.isExpired)
        assertTrue(normalCard.isHotspotCard)
        assertEquals("هوتسبوت", normalCard.systemLabelAr)

        val exhaustedCard = HotspotCard(
            id = "*2",
            name = "exhausted_user",
            password = "123",
            limitBytesTotal = 1000L,
            bytesIn = 600L,
            bytesOut = 500L, // 1100 > 1000
            cardType = "usermanager"
        )
        assertTrue(exhaustedCard.isExpiredQuota)
        assertTrue(exhaustedCard.isExpired)
        assertTrue(exhaustedCard.isUserManagerCard)
        assertEquals("يوزرمانجر", exhaustedCard.systemLabelAr)

        val commentedExpiredCard = HotspotCard(
            id = "*3",
            name = "expired_comment",
            password = "123",
            comment = "استنفاد رصيد الحزمة"
        )
        assertTrue(commentedExpiredCard.isExpiredQuota)
        assertTrue(commentedExpiredCard.isExpired)
    }

    @Test
    fun testNetworkNodeRouterScopingIsolation() {
        val sharedNode = com.example.data.model.NetworkNode(
            id = "eth2",
            name = "Tower North",
            ipAddress = "10.0.0.2",
            macAddress = "AA:BB:CC:DD:EE:01",
            interfaceName = "ether2",
            firmwareVersion = "7.15"
        )

        val entityRouterA = com.example.data.local.NetworkNodeEntity.fromNetworkNode(sharedNode, "192.168.88.1")
        val entityRouterB = com.example.data.local.NetworkNodeEntity.fromNetworkNode(sharedNode, "10.10.0.1")

        assertEquals("192.168.88.1", entityRouterA.routerIp)
        assertEquals("10.10.0.1", entityRouterB.routerIp)
        assertFalse(entityRouterA == entityRouterB)
        assertEquals("7.15", entityRouterA.toNetworkNode().firmwareVersion)
    }

    @Test
    fun testSnapshotSignatureDetectsValueChangesWithSameCount() {
        val baseSnapshot = com.example.data.local.RouterSnapshot(
            ipAddress = "192.168.88.1",
            routerName = "MainRouter",
            resource = com.example.data.model.RouterResource(
                uptime = "1d 02:00:00",
                version = "7.14",
                boardName = "RB5009",
                cpuLoad = 12
            ),
            routerBoard = com.example.data.model.RouterBoardInfo(model = "RB5009UG+S+"),
            cards = listOf(
                HotspotCard(id = "*1", name = "card100", password = "123", profile = "1day", bytesIn = 1000L, price = "200")
            ),
            profiles = listOf(
                com.example.data.model.HotspotProfile(id = "*1", name = "1day", rateLimit = "2M/2M", priceNumber = 200.0)
            ),
            activeUsers = listOf(
                com.example.data.model.HotspotActiveUser(
                    id = "*10",
                    user = "card100",
                    address = "10.5.50.10",
                    macAddress = "AA:BB:CC:DD:EE:10",
                    uptime = "10m",
                    bytesIn = 500L,
                    bytesOut = 1200L
                )
            ),
            servers = listOf(
                com.example.data.model.HotspotServerInfo(id = "*1", name = "hotspot1", interfaceName = "bridge", profile = "default", addressPool = "hs-pool", disabled = false)
            ),
            nodes = listOf(
                com.example.data.model.NetworkNode(
                    id = "wlan1",
                    name = "AP-1",
                    ipAddress = "10.5.50.200",
                    connectedUsers = 5,
                    pingMs = 4L,
                    isOnline = true
                )
            ),
            logs = emptyList(),
            ipBindings = listOf(
                com.example.data.model.IpBinding(id = "*1", macAddress = "00:11:22:33:44:55", address = "10.5.50.2", type = "bypassed", disabled = false)
            )
        )

        val sigBase = com.example.data.local.RouterCacheManager.computeSnapshotSignature(baseSnapshot)
        val sigSameCopy = com.example.data.local.RouterCacheManager.computeSnapshotSignature(baseSnapshot.copy())
        assertEquals(sigBase, sigSameCopy)

        // Same counts, only node pingMs & connectedUsers change -> MUST change signature
        val updatedNodeSnapshot = baseSnapshot.copy(
            nodes = listOf(baseSnapshot.nodes.first().copy(connectedUsers = 6, pingMs = 18L))
        )
        assertFalse(sigBase == com.example.data.local.RouterCacheManager.computeSnapshotSignature(updatedNodeSnapshot))

        // Same counts, only card bytesIn changes -> MUST change signature
        val updatedCardSnapshot = baseSnapshot.copy(
            cards = listOf(baseSnapshot.cards.first().copy(bytesIn = 999_000L))
        )
        assertFalse(sigBase == com.example.data.local.RouterCacheManager.computeSnapshotSignature(updatedCardSnapshot))

        // Same counts, only profile price changes -> MUST change signature
        val updatedProfileSnapshot = baseSnapshot.copy(
            profiles = listOf(baseSnapshot.profiles.first().copy(priceNumber = 350.0))
        )
        assertFalse(sigBase == com.example.data.local.RouterCacheManager.computeSnapshotSignature(updatedProfileSnapshot))

        // Same counts, only ipBinding disabled flag changes -> MUST change signature
        val updatedBindingSnapshot = baseSnapshot.copy(
            ipBindings = listOf(baseSnapshot.ipBindings.first().copy(disabled = true))
        )
        assertFalse(sigBase == com.example.data.local.RouterCacheManager.computeSnapshotSignature(updatedBindingSnapshot))

        assertTrue(com.example.data.local.RouterCacheManager.hasPersistableData(baseSnapshot))
    }

    @Test
    fun testRealRouterModelDefaultsContainNoFakeDemoValues() {
        val resource = com.example.data.model.RouterResource()
        val board = com.example.data.model.RouterBoardInfo()

        assertEquals("0s", resource.uptime)
        assertEquals("", resource.version)
        assertEquals("", resource.boardName)
        assertEquals(0, resource.cpuLoad)
        assertEquals(0L, resource.freeMemoryBytes)
        assertEquals(0L, resource.totalMemoryBytes)
        assertEquals("", resource.voltage)
        assertEquals("", resource.temperature)

        assertEquals("", board.model)
        assertEquals("", board.serialNumber)
        assertFalse(board.routerboard)
    }

    @Test
    fun testRepositoryWorksWithoutFirebaseAndSetsDemoDataExplicitly() {
        val repo = com.example.data.network.MikrotikRepository(appDao = null)
        assertEquals("", repo.resourceState.value.boardName)
        assertTrue(repo.cards.value.isEmpty())
        assertTrue(repo.networkNodes.value.isEmpty())

        repo.populateDemoData()
        assertTrue(repo.resourceState.value.boardName.contains("Demo"))
        assertTrue(repo.cards.value.isNotEmpty())
        assertTrue(repo.networkNodes.value.isNotEmpty())
    }
}

