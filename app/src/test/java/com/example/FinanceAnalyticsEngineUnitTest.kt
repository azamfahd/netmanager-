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
import com.example.data.network.NetworkUtils
import com.example.data.util.FinanceAnalyticsEngine
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Test
    fun historicalRouterDatesUmSessionsAndIntermediateArchivesPopulatePreviousMonths() {
        val now = epochOf(2026, 11, 14, 12)
        val umFeb2026Ts = epochOf(2026, 2, 10, 9)
        val umAug2026Ts = epochOf(2026, 8, 22, 16)
        val wrongLocalTodayTs = epochOf(2026, 11, 14, 8)

        val profiles = listOf(
            HotspotProfile(id = "p1", name = "500-Day", price = "500 ر.ي", priceNumber = 500.0, costPrice = 200.0)
        )

        val cards = listOf(
            // Card 1: Hotspot Mikhmon comment in Oct 2025, stale LocalCard timestamp set to today
            HotspotCard(
                id = "*10",
                name = "mk_oct25",
                password = "1",
                profile = "500-Day",
                comment = "vc-110-10.15.25",
                uptime = "4h",
                bytesOut = 2_000_000L
            ),
            // Card 2: RouterOS comment date in Jul 2026, with legacy poisoned sess_card_ today timestamp that must be ignored
            HotspotCard(
                id = "*11",
                name = "ros_jul26",
                password = "1",
                profile = "500-Day",
                comment = "jul/08/2026 11:30:00",
                uptime = "2h",
                bytesOut = 1_500_000L
            ),
            // Card 3: UserManager card with no comment date, date comes from um_sess_ historical archive (Aug 2026)
            HotspotCard(
                id = "*12",
                name = "um_aug26",
                password = "1",
                profile = "500-Day",
                uptime = "1h",
                bytesIn = 900_000L,
                cardType = "usermanager"
            ),
            // Card 4: UserManager card with explicit creation-time hint in Feb 2026 (still unsold)
            HotspotCard(
                id = "*13",
                name = "um_feb26",
                password = "1",
                profile = "500-Day",
                uptime = "0s",
                cardType = "usermanager",
                createdAtHint = umFeb2026Ts
            )
        )

        // All local cards accidentally stamped with today's sync timestamp (simulates prior bug)
        val staleLocalCards = cards.map {
            LocalCardEntity(
                name = it.name,
                cardId = it.id,
                profile = it.profile,
                createdTimestamp = wrongLocalTodayTs,
                firstSeenTimestamp = wrongLocalTodayTs
            )
        }

        val sessions = listOf(
            // Legacy synthetic snapshot with today's timestamp — MUST NOT overwrite July 2026 into Nov 2026
            SessionArchiveEntity(
                sessionId = "sess_card_ros_jul26",
                username = "ros_jul26",
                timestamp = wrongLocalTodayTs
            ),
            // Real historical UM session in August 2026
            SessionArchiveEntity(
                sessionId = "um_sess_um_aug26_*A1",
                username = "um_aug26",
                timestamp = umAug2026Ts,
                totalBytes = 900_000L
            )
        )

        // Intermediate saved historical month (May 2026) between Feb 2026 and Jul 2026 live data
        val savedMay2026 = listOf(
            MonthlyProfitRecordEntity(
                monthKey = "2026-05",
                monthNameAr = "مايو 2026",
                year = 2026,
                month = 5,
                totalRevenue = 3500.0,
                totalCost = 1400.0,
                netProfit = 2100.0,
                soldCardsCount = 7,
                totalCardsCount = 10,
                topSellingProfile = "500-Day",
                totalBytesConsumed = 4_000_000L
            )
        )

        val months = FinanceAnalyticsEngine.computeMonthlySalesList(
            allCards = cards,
            localCards = staleLocalCards,
            profiles = profiles,
            pricings = emptyList(),
            assignments = emptyList(),
            batches = emptyList(),
            loginHistory = emptyList(),
            archivedSessions = sessions,
            savedMonthlyRecords = savedMay2026,
            nowMillis = now
        )

        val oct25 = months.first { it.monthKey == "2025-10" }
        assertEquals(1, oct25.totalCardsSold)
        assertEquals(500.0, oct25.totalRevenue, 0.01)

        val feb26 = months.first { it.monthKey == "2026-02" }
        assertEquals(1, feb26.totalCardsGenerated)
        assertEquals(1, feb26.totalCardsUnsold)
        assertEquals(0, feb26.totalCardsSold)

        val may26 = months.first { it.monthKey == "2026-05" }
        assertEquals(7, may26.totalCardsSold)
        assertEquals(3500.0, may26.totalRevenue, 0.01)

        val jul26 = months.first { it.monthKey == "2026-07" }
        assertEquals(1, jul26.totalCardsSold)
        assertEquals(500.0, jul26.totalRevenue, 0.01)

        val aug26 = months.first { it.monthKey == "2026-08" }
        assertEquals(1, aug26.totalCardsSold)
        assertEquals(500.0, aug26.totalRevenue, 0.01)

        // Current month (2026-11) must NOT steal the historical sold cards!
        val nov26 = months.first { it.monthKey == "2026-11" }
        assertEquals(0, nov26.totalCardsSold)
        assertEquals(0.0, nov26.totalRevenue, 0.01)
    }

    @Test
    fun parseRouterDateToEpochSkipsInvalidLeadingNumbers() {
        val now = epochOf(2026, 11, 14, 12)

        val isoAfterNumericPrefix = NetworkUtils.parseRouterDateToEpoch("batch-1000-2025-10-15", now)
        assertNotNull(isoAfterNumericPrefix)
        val cal1 = Calendar.getInstance().apply { timeInMillis = isoAfterNumericPrefix!! }
        assertEquals(2025, cal1.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, cal1.get(Calendar.MONTH))
        assertEquals(15, cal1.get(Calendar.DAY_OF_MONTH))

        val mikhmonAfterInvalid = NetworkUtils.parseRouterDateToEpoch("id-99/99/99 vc-120-08.22.26", now)
        assertNotNull(mikhmonAfterInvalid)
        val cal2 = Calendar.getInstance().apply { timeInMillis = mikhmonAfterInvalid!! }
        assertEquals(2026, cal2.get(Calendar.YEAR))
        assertEquals(Calendar.AUGUST, cal2.get(Calendar.MONTH))
        assertEquals(22, cal2.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun cloudValidatorRejectsCorruptRecordsAndSanitizesIdsWhileEngineRestoresBreakdowns() {
        val sanitizedArabic = com.example.data.model.FinanceCloudValidator.sanitizeFirestoreDocId("باقة / 500 يومي")
        assertTrue(Regex("^[a-zA-Z0-9_\\-]{1,128}$").matches(sanitizedArabic))
        val sanitizedAscii = com.example.data.model.FinanceCloudValidator.sanitizeFirestoreDocId("500-Day")
        assertEquals("500-Day", sanitizedAscii)

        val validCandidate = com.example.data.model.FinanceCloudValidator.validateMonthlySummaryForUpload(
            com.example.data.model.MonthlySalesSummary(
                monthKey = "2025-11",
                monthName = "نوفمبر 2025",
                totalCardsGenerated = 20,
                totalCardsSold = 15,
                totalRevenue = 7500.0,
                totalCost = 3000.0,
                netProfit = 4500.0,
                totalBytesConsumed = 2_000_000L
            ),
            userId = "user_abc"
        )
        assertNotNull(validCandidate)
        assertEquals(2025L * 12L + 11L, validCandidate!!.yearMonthSerial)

        val corruptNegative = com.example.data.model.FinanceCloudValidator.validateMonthlySummaryForUpload(
            com.example.data.model.MonthlySalesSummary(
                monthKey = "2025-11",
                monthName = "نوفمبر 2025",
                totalRevenue = 500.0,
                totalCost = 600.0,
                netProfit = -100.0
            ),
            userId = "user_abc"
        )
        assertEquals(null, corruptNegative)

        val now = epochOf(2026, 11, 14, 12)
        val restoredBreakdowns = mapOf(
            "2025-06" to listOf(
                com.example.data.model.MonthlyPackageSales("500-Day", cardsSold = 10, totalRevenue = 5000.0, netProfit = 3000.0, sharePercentage = 62.5f),
                com.example.data.model.MonthlyPackageSales("300-Hour", cardsSold = 10, totalRevenue = 3000.0, netProfit = 1500.0, sharePercentage = 37.5f)
            )
        )
        val savedRecords = listOf(
            MonthlyProfitRecordEntity(
                monthKey = "2025-06",
                monthNameAr = "يونيو 2025",
                year = 2025,
                month = 6,
                totalRevenue = 8000.0,
                totalCost = 3500.0,
                netProfit = 4500.0,
                soldCardsCount = 20,
                totalCardsCount = 25,
                topSellingProfile = "500-Day",
                totalBytesConsumed = 10_000_000L
            ),
            // Corrupted negative SQLite record must be ignored completely
            MonthlyProfitRecordEntity(
                monthKey = "2025-04",
                monthNameAr = "أبريل 2025",
                year = 2025,
                month = 4,
                totalRevenue = -50.0,
                totalCost = 0.0,
                netProfit = -50.0,
                soldCardsCount = 1,
                totalCardsCount = 1
            )
        )

        val months = FinanceAnalyticsEngine.computeMonthlySalesList(
            allCards = emptyList(),
            localCards = emptyList(),
            profiles = emptyList(),
            pricings = emptyList(),
            assignments = emptyList(),
            batches = emptyList(),
            loginHistory = emptyList(),
            archivedSessions = emptyList(),
            savedMonthlyRecords = savedRecords,
            nowMillis = now,
            savedPackageBreakdownsByMonth = restoredBreakdowns
        )

        assertTrue("Corrupt 2025-04 record must be excluded", months.none { it.monthKey == "2025-04" })
        val june25 = months.first { it.monthKey == "2025-06" }
        assertEquals(2, june25.packageBreakdown.size)
        assertEquals("300-Hour", june25.packageBreakdown[1].packageName)
    }
}
