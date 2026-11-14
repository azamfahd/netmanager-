package com.example

import com.example.data.local.BatchHistoryEntity
import com.example.data.local.LocalCardEntity
import com.example.data.local.MonthlyProfitRecordEntity
import com.example.data.local.PackagePricingEntity
import com.example.data.local.PosCardAssignmentEntity
import com.example.data.local.SessionArchiveEntity
import com.example.data.local.UserLoginHistoryEntity
import com.example.data.model.HotspotCard
import com.example.data.model.HotspotProfile
import com.example.data.model.TimeFilterRange
import com.example.data.util.FinanceAnalyticsEngine
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class FinanceAnalyticsEngineUnitTest {

    private fun epochOf(year: Int, month1Based: Int, day: Int, hour: Int = 12): Long {
        return Calendar.getInstance().apply {
            clear()
            set(year, month1Based - 1, day, hour, 0, 0)
        }.timeInMillis
    }

    @Test
    fun monthlyStatsAreSortedChronologicallyAndNeverDoubleCountAcrossMonths() {
        val originalLocale = Locale.getDefault()
        try {
            // Test with Arabic locale to guarantee ASCII monthKey formatting
            Locale.setDefault(Locale("ar", "EG"))

            val now = epochOf(2026, 11, 13, 15)
            val dec2025Ts = epochOf(2025, 12, 10, 10)
            val sep2026Ts = epochOf(2026, 9, 5, 10)
            val oct2026Ts = epochOf(2026, 10, 18, 14)
            val nov2026Ts = epochOf(2026, 11, 2, 11)

            val profiles = listOf(
                HotspotProfile(id = "p1", name = "100-Hour", price = "100 ر.ي", priceNumber = 100.0, costPrice = 40.0),
                HotspotProfile(id = "p2", name = "6000-VIP", price = "6000 ر.ي", priceNumber = 6000.0, costPrice = 2500.0)
            )
            val pricings = listOf(
                PackagePricingEntity(profileName = "100-Hour", costPrice = 40.0, salePrice = 100.0)
            )

            val cards = listOf(
                // Dec 2025: 1 sold 100-Hour card
                HotspotCard(id = "*1", name = "c_dec25", password = "1", profile = "100-Hour", uptime = "1h", bytesIn = 1_000_000L, bytesOut = 2_000_000L),
                // Sep 2026: 2 cards generated; c_sep_sold sold in Sep, c_sep_unsold still unsold
                HotspotCard(id = "*2", name = "c_sep_sold", password = "1", profile = "100-Hour", uptime = "30m", bytesIn = 500_000L),
                HotspotCard(id = "*3", name = "c_sep_unsold", password = "1", profile = "100-Hour", uptime = "0s"),
                // Sep 2026 card sold via POS in Oct 2026
                HotspotCard(id = "*4", name = "c_sep_pos_oct", password = "1", profile = "6000-VIP", uptime = "2d", bytesIn = 5_000_000L),
                // Nov 2026: 1 sold 100-Hour card
                HotspotCard(id = "*5", name = "c_nov_sold", password = "1", profile = "100-Hour", uptime = "20m", bytesOut = 3_000_000L)
            )

            val localCards = listOf(
                LocalCardEntity(name = "c_dec25", cardId = "*1", profile = "100-Hour", createdTimestamp = dec2025Ts),
                LocalCardEntity(name = "c_sep_sold", cardId = "*2", profile = "100-Hour", createdTimestamp = sep2026Ts),
                LocalCardEntity(name = "c_sep_unsold", cardId = "*3", profile = "100-Hour", createdTimestamp = sep2026Ts),
                LocalCardEntity(name = "c_sep_pos_oct", cardId = "*4", profile = "6000-VIP", createdTimestamp = sep2026Ts),
                LocalCardEntity(name = "c_nov_sold", cardId = "*5", profile = "100-Hour", createdTimestamp = nov2026Ts)
            )

            val assignments = listOf(
                PosCardAssignmentEntity(
                    agentId = 1L,
                    agentName = "وكيل",
                    cardName = "c_sep_pos_oct",
                    profileName = "6000-VIP",
                    priceAmount = 6000.0,
                    assignedAt = sep2026Ts,
                    isSold = true,
                    soldAt = oct2026Ts
                )
            )

            val months = FinanceAnalyticsEngine.computeMonthlySalesList(
                allCards = cards,
                localCards = localCards,
                profiles = profiles,
                pricings = pricings,
                assignments = assignments,
                batches = emptyList(),
                loginHistory = emptyList(),
                archivedSessions = emptyList(),
                savedMonthlyRecords = emptyList(),
                nowMillis = now
            )

            // Must be strictly sorted descending by monthKey
            val keys = months.map { it.monthKey }
            assertEquals(keys.sortedDescending(), keys)
            assertTrue("Should include Dec 2025 beyond 6-month window", keys.contains("2025-12"))
            assertEquals("2026-11", keys.first())

            val nov = months.first { it.monthKey == "2026-11" }
            assertTrue(nov.isCurrentMonth)
            assertEquals(1, nov.totalCardsGenerated)
            assertEquals(1, nov.totalCardsSold)
            assertEquals(0, nov.totalCardsUnsold)
            assertEquals(100.0, nov.totalRevenue, 0.01)
            assertEquals(40.0, nov.totalCost, 0.01)
            assertEquals(60.0, nov.netProfit, 0.01)
            assertEquals(3_000_000L, nov.totalBytesConsumed)

            val oct = months.first { it.monthKey == "2026-10" }
            assertEquals(0, oct.totalCardsGenerated)
            assertEquals(1, oct.totalCardsSold)
            assertEquals(6000.0, oct.totalRevenue, 0.01)
            assertEquals(2500.0, oct.totalCost, 0.01)
            assertEquals(3500.0, oct.netProfit, 0.01)
            assertEquals(5_000_000L, oct.totalBytesConsumed)
            assertEquals(1, oct.dailyBreakdown.size)
            assertEquals(18, oct.dailyBreakdown.first().dayNumber)

            val sep = months.first { it.monthKey == "2026-09" }
            assertEquals(3, sep.totalCardsGenerated)
            assertEquals(1, sep.totalCardsSold)
            assertEquals(1, sep.totalCardsUnsold)
            assertEquals(100.0, sep.totalRevenue, 0.01)
            assertEquals(60.0, sep.netProfit, 0.01)

            val dec25 = months.first { it.monthKey == "2025-12" }
            assertEquals(1, dec25.totalCardsGenerated)
            assertEquals(1, dec25.totalCardsSold)
            assertEquals(100.0, dec25.totalRevenue, 0.01)
            assertEquals(60.0, dec25.netProfit, 0.01)
            assertEquals(3_000_000L, dec25.totalBytesConsumed)

            // Global invariant: sum across all months equals total cards, sold, unsold, revenue, profit
            assertEquals(5, months.sumOf { it.totalCardsGenerated })
            assertEquals(4, months.sumOf { it.totalCardsSold })
            assertEquals(1, months.sumOf { it.totalCardsUnsold })
            assertEquals(6300.0, months.sumOf { it.totalRevenue }, 0.01)
            assertEquals(3680.0, months.sumOf { it.netProfit }, 0.01)

            // Each month's package breakdown sums must match the month's totals
            months.forEach { m ->
                assertEquals(m.totalCardsSold, m.packageBreakdown.sumOf { it.cardsSold })
                assertEquals(m.totalRevenue, m.packageBreakdown.sumOf { it.totalRevenue }, 0.01)
                assertEquals(m.netProfit, m.packageBreakdown.sumOf { it.netProfit }, 0.01)
            }
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun archivedSavedMonthlyRecordsBeforeLiveDataArePreservedWithoutOverwritingActiveMonths() {
        val now = epochOf(2026, 11, 13, 15)
        val oct2026Ts = epochOf(2026, 10, 5, 10)

        val savedHistorical = listOf(
            MonthlyProfitRecordEntity(
                monthKey = "2025-05",
                monthNameAr = "مايو 2025",
                year = 2025,
                month = 5,
                totalRevenue = 45000.0,
                totalCost = 18000.0,
                netProfit = 27000.0,
                soldCardsCount = 90,
                totalCardsCount = 100,
                topSellingProfile = "500-Day",
                totalBytesConsumed = 50_000_000_000L
            ),
            // Corrupted legacy record for an active month (2026-10) should be recalculated from live data
            MonthlyProfitRecordEntity(
                monthKey = "2026-10",
                monthNameAr = "أكتوبر 2026",
                year = 2026,
                month = 10,
                totalRevenue = 999999.0,
                totalCost = 0.0,
                netProfit = 999999.0,
                soldCardsCount = 500,
                totalCardsCount = 500
            )
        )

        val cards = listOf(
            HotspotCard(id = "gen_${oct2026Ts}_1", name = "u1", password = "p", profile = "500-Day", uptime = "1h", price = "500 ر.ي")
        )
        val profiles = listOf(
            HotspotProfile(id = "p1", name = "500-Day", price = "500 ر.ي", priceNumber = 500.0, costPrice = 200.0)
        )

        val months = FinanceAnalyticsEngine.computeMonthlySalesList(
            allCards = cards,
            localCards = emptyList(),
            profiles = profiles,
            pricings = emptyList(),
            assignments = emptyList(),
            batches = emptyList(),
            loginHistory = emptyList(),
            archivedSessions = emptyList(),
            savedMonthlyRecords = savedHistorical,
            nowMillis = now
        )

        val may25 = months.first { it.monthKey == "2025-05" }
        assertEquals(45000.0, may25.totalRevenue, 0.01)
        assertEquals(27000.0, may25.netProfit, 0.01)
        assertEquals(90, may25.totalCardsSold)
        assertEquals(50_000_000_000L, may25.totalBytesConsumed)

        val oct26 = months.first { it.monthKey == "2026-10" }
        assertEquals(1, oct26.totalCardsSold)
        assertEquals(500.0, oct26.totalRevenue, 0.01)
        assertEquals(300.0, oct26.netProfit, 0.01)
    }

    @Test
    fun deletedRouterProfilesStillAppearInPackageAndRevenueStatsWithArabicDigitsParsed() {
        val cards = listOf(
            HotspotCard(id = "*1", name = "a1", password = "1", profile = "Legacy500", uptime = "2h", price = "٥٠٠ ر.ي"),
            HotspotCard(id = "*2", name = "a2", password = "1", profile = "Legacy500", uptime = "0s", price = "٥٠٠ ر.ي"),
            HotspotCard(id = "*3", name = "b1", password = "1", profile = "Active200", uptime = "10m", bytesIn = 100L, price = "200")
        )
        val profiles = listOf(
            HotspotProfile(id = "p1", name = "Active200", price = "200 ر.ي", priceNumber = 200.0, costPrice = 80.0)
        )

        val stats = FinanceAnalyticsEngine.computeDetailedPackageProfitStats(
            cardList = cards,
            profileList = profiles,
            pricingList = listOf(PackagePricingEntity("Legacy500", costPrice = 200.0, salePrice = 500.0)),
            assignmentList = emptyList(),
            timeFilter = TimeFilterRange.ALL
        )

        assertEquals(2, stats.size)
        val summary = FinanceAnalyticsEngine.computeRevenueAnalyticsSummary(stats, cards, TimeFilterRange.ALL)
        assertEquals(700.0, summary.totalEstimatedRevenue, 0.01)
        assertEquals(280.0, summary.totalCost, 0.01)
        assertEquals(420.0, summary.totalNetProfit, 0.01)
        // Weighted average card price across sold cards = 700 / 2 = 350.0
        assertEquals(350.0, summary.averageCardPrice, 0.01)
    }
}
