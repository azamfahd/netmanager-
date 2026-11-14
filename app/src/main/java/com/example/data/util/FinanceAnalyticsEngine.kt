package com.example.data.util

import com.example.data.local.BatchHistoryEntity
import com.example.data.local.LocalCardEntity
import com.example.data.local.MonthlyProfitRecordEntity
import com.example.data.local.PackagePricingEntity
import com.example.data.local.PosCardAssignmentEntity
import com.example.data.local.SessionArchiveEntity
import com.example.data.local.UserLoginHistoryEntity
import com.example.data.model.DailySalesPoint
import com.example.data.model.DetailedPackageProfitStat
import com.example.data.model.HotspotCard
import com.example.data.model.HotspotProfile
import com.example.data.model.MonthlyPackageSales
import com.example.data.model.MonthlySalesSummary
import com.example.data.model.RevenueAnalyticsSummary
import com.example.data.model.TimeFilterRange
import com.example.data.network.NetworkUtils
import org.json.JSONArray
import java.util.Calendar
import java.util.Locale
import kotlin.math.min

object FinanceAnalyticsEngine {

    private val MONTH_NAMES_AR = listOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
    )

    private const val MIN_VALID_EPOCH_MS = 1577836800000L // 2020-01-01
    private const val MAX_VALID_EPOCH_MS = 2082758400000L // 2036-01-01

    data class ResolvedPackagePricing(
        val displayName: String,
        val salePrice: Double,
        val costPrice: Double
    ) {
        val profitPerCard: Double get() = (salePrice - costPrice).coerceAtLeast(0.0)
        val marginPercent: Double get() = if (salePrice > 0.0) (profitPerCard / salePrice) * 100.0 else 0.0
    }

    private data class CardFinanceItem(
        val cardKey: String,
        val profileDisplayName: String,
        val profileKey: String,
        val createdTs: Long,
        val isSold: Boolean,
        val soldTs: Long,
        val salePrice: Double,
        val costPrice: Double,
        val bytesConsumed: Long
    )

    private data class YearMonth(val year: Int, val month: Int) : Comparable<YearMonth> {
        val key: String get() = String.format(Locale.US, "%04d-%02d", year, month)
        val labelAr: String get() = "${MONTH_NAMES_AR.getOrElse(month - 1) { month.toString() }} $year"
        val serial: Int get() = year * 12 + (month - 1)

        override fun compareTo(other: YearMonth): Int = serial.compareTo(other.serial)

        fun previous(): YearMonth {
            return if (month <= 1) YearMonth(year - 1, 12) else YearMonth(year, month - 1)
        }
    }

    fun normalizeDigits(input: String): String {
        if (input.isBlank()) return input
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun parsePriceValue(priceStr: String): Double {
        if (priceStr.isBlank()) return 0.0
        val normalized = normalizeDigits(priceStr).replace(",", "")
        val match = Regex("""\d+(?:\.\d+)?""").find(normalized) ?: return 0.0
        return match.value.toDoubleOrNull() ?: 0.0
    }

    fun extractTimestampFromCardMetadata(card: HotspotCard, nowMillis: Long): Long? {
        val idMatch = Regex("""(?:gen_|card_|c_|tum_|um_)?(\d{10,13})""").find(card.id)
        if (idMatch != null) {
            val numStr = idMatch.groupValues[1]
            val parsed = if (numStr.length == 10) numStr.toLongOrNull()?.times(1000L) else numStr.toLongOrNull()
            if (parsed != null && parsed in MIN_VALID_EPOCH_MS..MAX_VALID_EPOCH_MS) {
                return parsed
            }
        }

        val comment = normalizeDigits(card.comment)
        if (comment.isNotBlank()) {
            val dateMatch = Regex("""(\d{4})[-/](\d{1,2})[-/](\d{1,2})""").find(comment)
            if (dateMatch != null) {
                val y = dateMatch.groupValues[1].toIntOrNull()
                val m = dateMatch.groupValues[2].toIntOrNull()
                val d = dateMatch.groupValues[3].toIntOrNull()
                if (y != null && y in 2020..2035 && m != null && m in 1..12 && d != null && d in 1..31) {
                    val cal = Calendar.getInstance()
                    cal.clear()
                    cal.set(y, m - 1, d, 12, 0, 0)
                    return cal.timeInMillis
                }
            }
            val epochMatch = Regex("""\b(1[6-9]\d{8,11})\b""").find(comment)
            if (epochMatch != null) {
                val raw = epochMatch.groupValues[1]
                val num = raw.toLongOrNull()
                if (num != null) {
                    val ms = if (raw.length == 10) num * 1000L else num
                    if (ms in MIN_VALID_EPOCH_MS..MAX_VALID_EPOCH_MS) return ms
                }
            }
        }

        val uptimeSec = NetworkUtils.parseUptimeToSeconds(card.uptime)
        if (uptimeSec > 0L) {
            val approx = nowMillis - (uptimeSec * 1000L)
            if (approx in MIN_VALID_EPOCH_MS..nowMillis) return approx
        }
        return null
    }

    fun isCardUsedOrSold(card: HotspotCard): Boolean {
        return card.isActive ||
            card.isExpired ||
            (card.uptime.isNotBlank() && card.uptime != "0s") ||
            card.bytesIn > 0L ||
            card.bytesOut > 0L
    }

    fun resolveAllPackagePricings(
        profileList: List<HotspotProfile>,
        cardList: List<HotspotCard>,
        pricingList: List<PackagePricingEntity>,
        assignmentList: List<PosCardAssignmentEntity>
    ): Map<String, ResolvedPackagePricing> {
        val displayNamesByKey = linkedMapOf<String, String>()
        fun registerName(name: String) {
            val trimmed = name.trim()
            if (trimmed.isNotBlank()) {
                displayNamesByKey.putIfAbsent(trimmed.lowercase(Locale.ROOT), trimmed)
            }
        }

        profileList.forEach { registerName(it.name) }
        cardList.forEach { registerName(it.profile) }
        assignmentList.forEach { registerName(it.profileName) }

        val profileMap = profileList
            .filter { it.name.isNotBlank() }
            .associateBy { it.name.trim().lowercase(Locale.ROOT) }
        val pricingMap = pricingList
            .filter { it.profileName.isNotBlank() }
            .associateBy { it.profileName.trim().lowercase(Locale.ROOT) }
        val cardsByProfile = cardList
            .filter { it.profile.isNotBlank() }
            .groupBy { it.profile.trim().lowercase(Locale.ROOT) }
        val assignmentsByProfile = assignmentList
            .filter { it.profileName.isNotBlank() }
            .groupBy { it.profileName.trim().lowercase(Locale.ROOT) }

        val result = linkedMapOf<String, ResolvedPackagePricing>()
        for ((key, displayName) in displayNamesByKey) {
            val matchingProfile = profileMap[key]
            val savedPricing = pricingMap[key]
            val profCards = cardsByProfile[key].orEmpty()
            val profAssignments = assignmentsByProfile[key].orEmpty()

            val firstCardPrice = profCards.asSequence()
                .map { parsePriceValue(it.price) }
                .firstOrNull { it > 0.0 } ?: 0.0
            val firstAssignmentPrice = profAssignments.asSequence()
                .map { it.priceAmount }
                .firstOrNull { it > 0.0 } ?: 0.0

            val profileParsedPrice = matchingProfile?.let {
                it.priceNumber.takeIf { p -> p > 0.0 } ?: parsePriceValue(it.price)
            } ?: 0.0

            val salePrice = savedPricing?.salePrice?.takeIf { it > 0.0 }
                ?: profileParsedPrice.takeIf { it > 0.0 }
                ?: firstCardPrice.takeIf { it > 0.0 }
                ?: firstAssignmentPrice

            val costPrice = savedPricing?.costPrice?.takeIf { it >= 0.0 }
                ?: matchingProfile?.costPrice?.takeIf { it > 0.0 }
                ?: 0.0

            result[key] = ResolvedPackagePricing(
                displayName = displayName,
                salePrice = salePrice.coerceAtLeast(0.0),
                costPrice = costPrice.coerceAtLeast(0.0)
            )
        }
        return result
    }

    fun computeDetailedPackageProfitStats(
        cardList: List<HotspotCard>,
        profileList: List<HotspotProfile>,
        pricingList: List<PackagePricingEntity>,
        assignmentList: List<PosCardAssignmentEntity>,
        timeFilter: TimeFilterRange,
        localCards: List<LocalCardEntity> = emptyList(),
        loginHistory: List<UserLoginHistoryEntity> = emptyList(),
        archivedSessions: List<SessionArchiveEntity> = emptyList(),
        batches: List<BatchHistoryEntity> = emptyList(),
        nowMillis: Long = System.currentTimeMillis()
    ): List<DetailedPackageProfitStat> {
        val resolvedPrices = resolveAllPackagePricings(profileList, cardList, pricingList, assignmentList)
        if (resolvedPrices.isEmpty()) return emptyList()

        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = cal.timeInMillis
        val startOfWeek = startOfToday - (6L * 24L * 60L * 60L * 1000L)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val startOfMonth = cal.timeInMillis

        val windowStart = when (timeFilter) {
            TimeFilterRange.ALL -> Long.MIN_VALUE
            TimeFilterRange.TODAY -> startOfToday
            TimeFilterRange.WEEK -> startOfWeek
            TimeFilterRange.MONTH -> startOfMonth
        }

        val localCardMap = localCards.associateBy { it.name.trim().lowercase(Locale.ROOT) }
        val batchCardCreatedAt = buildBatchCreatedAtMap(batches)
        val latestUsageMap = buildLatestUsageTimestampMap(loginHistory, archivedSessions)

        val cardsByProfile = cardList
            .distinctBy { it.name.trim().lowercase(Locale.ROOT) }
            .groupBy { it.profile.trim().lowercase(Locale.ROOT) }
        val assignmentsByProfile = assignmentList
            .groupBy { it.profileName.trim().lowercase(Locale.ROOT) }

        val rawStats = resolvedPrices.map { (profKey, pricing) ->
            val profCards = cardsByProfile[profKey].orEmpty()
            val profAssignments = assignmentsByProfile[profKey].orEmpty()
            val assignmentByCard = profAssignments.associateBy { it.cardName.trim().lowercase(Locale.ROOT) }

            val generatedNames = HashSet<String>()
            profCards.forEach { generatedNames.add(it.name.trim().lowercase(Locale.ROOT)) }
            profAssignments.forEach {
                val n = it.cardName.trim().lowercase(Locale.ROOT)
                if (n.isNotBlank()) generatedNames.add(n)
            }

            val activeCount = profCards.count { it.isActive }
            val unusedCount = profCards.count { it.isUnused }
            val expiredCount = profCards.count { it.isExpired }

            val soldNamesInWindow = HashSet<String>()
            for (card in profCards) {
                val key = card.name.trim().lowercase(Locale.ROOT)
                val assignment = assignmentByCard[key]
                val sold = isCardUsedOrSold(card) || (assignment?.isSold == true)
                if (!sold) continue

                if (timeFilter == TimeFilterRange.ALL) {
                    soldNamesInWindow.add(key)
                } else {
                    val local = localCardMap[key]
                    val createdTs = batchCardCreatedAt[key]
                        ?: local?.createdTimestamp?.takeIf { it > 0L }
                        ?: local?.firstSeenTimestamp?.takeIf { it > 0L }
                        ?: assignment?.assignedAt?.takeIf { it > 0L }
                        ?: extractTimestampFromCardMetadata(card, nowMillis)
                        ?: nowMillis
                    val soldTs = assignment?.takeIf { it.isSold && it.soldAt > 0L }?.soldAt
                        ?: latestUsageMap[key]?.takeIf { it > 0L }
                        ?: createdTs
                    if (soldTs >= windowStart) {
                        soldNamesInWindow.add(key)
                    }
                }
            }
            for (assignment in profAssignments) {
                if (!assignment.isSold) continue
                val key = assignment.cardName.trim().lowercase(Locale.ROOT)
                if (key.isBlank() || soldNamesInWindow.contains(key)) continue
                if (timeFilter == TimeFilterRange.ALL) {
                    soldNamesInWindow.add(key)
                } else {
                    val soldTs = assignment.soldAt.takeIf { it > 0L }
                        ?: latestUsageMap[key]?.takeIf { it > 0L }
                        ?: assignment.assignedAt
                    if (soldTs >= windowStart) {
                        soldNamesInWindow.add(key)
                    }
                }
            }

            val soldInWindow = soldNamesInWindow.size
            val totalRev = soldInWindow * pricing.salePrice
            val totalCost = soldInWindow * pricing.costPrice
            val netProfit = (totalRev - totalCost).coerceAtLeast(0.0)

            DetailedPackageProfitStat(
                profileName = pricing.displayName,
                costPrice = pricing.costPrice,
                salePrice = pricing.salePrice,
                profitPerCard = pricing.profitPerCard,
                profitMarginPercent = pricing.marginPercent,
                totalCardsGenerated = generatedNames.size,
                soldCardsCount = soldInWindow,
                activeCardsCount = activeCount,
                unusedCardsCount = unusedCount,
                expiredCardsCount = expiredCount,
                totalRevenue = totalRev,
                totalCost = totalCost,
                netProfit = netProfit,
                profitSharePercent = 0f,
                salesSharePercent = 0f
            )
        }

        val totalNetworkProfit = rawStats.sumOf { it.netProfit }.coerceAtLeast(1.0)
        val totalNetworkSold = rawStats.sumOf { it.soldCardsCount }.coerceAtLeast(1)

        return rawStats.map { stat ->
            stat.copy(
                profitSharePercent = ((stat.netProfit / totalNetworkProfit) * 100f).toFloat().coerceIn(0f, 100f),
                salesSharePercent = ((stat.soldCardsCount.toFloat() / totalNetworkSold.toFloat()) * 100f).coerceIn(0f, 100f)
            )
        }.sortedWith(
            compareByDescending<DetailedPackageProfitStat> { it.netProfit }
                .thenByDescending { it.totalRevenue }
                .thenByDescending { it.soldCardsCount }
        )
    }

    fun computeRevenueAnalyticsSummary(
        stats: List<DetailedPackageProfitStat>,
        allCards: List<HotspotCard>,
        timeFilter: TimeFilterRange
    ): RevenueAnalyticsSummary {
        val grossSales = stats.sumOf { it.totalRevenue }
        val totalCost = stats.sumOf { it.totalCost }
        val netProfit = stats.sumOf { it.netProfit }
        val totalGenerated = maxOf(stats.sumOf { it.totalCardsGenerated }, allCards.size)
        var activeCount = 0
        var unusedCount = 0
        var expiredCount = 0
        for (c in allCards) {
            if (c.isActive) activeCount++
            if (c.isUnused) unusedCount++
            if (c.isExpired) expiredCount++
        }

        val activeRev = stats.sumOf { it.activeCardsCount * it.salePrice }
        val inventoryVal = stats.sumOf { it.unusedCardsCount * it.salePrice }
        val totalSold = stats.sumOf { it.soldCardsCount }
        val totalGenInStats = stats.sumOf { it.totalCardsGenerated }
        val avgPrice = when {
            totalSold > 0 && grossSales > 0.0 -> grossSales / totalSold
            totalGenInStats > 0 -> stats.sumOf { it.totalCardsGenerated * it.salePrice } / totalGenInStats
            stats.any { it.salePrice > 0.0 } -> stats.filter { it.salePrice > 0.0 }.map { it.salePrice }.average()
            else -> 0.0
        }
        val avgMargin = if (grossSales > 0.0) ((netProfit / grossSales) * 100.0) else 0.0

        val topSelling = stats.filter { it.soldCardsCount > 0 }.maxByOrNull { it.soldCardsCount }
        val topProfitable = stats.filter { it.netProfit > 0.0 }.maxByOrNull { it.netProfit }

        return RevenueAnalyticsSummary(
            totalEstimatedRevenue = grossSales,
            activeRevenue = activeRev,
            availableInventoryValue = inventoryVal,
            totalCardsCount = totalGenerated,
            activeCardsCount = activeCount,
            unusedCardsCount = unusedCount,
            expiredCardsCount = expiredCount,
            topSellingPackage = topSelling?.profileName ?: "لا توجد مبيعات بعد",
            topSellingPackageCount = topSelling?.soldCardsCount ?: 0,
            averageCardPrice = avgPrice,
            totalCost = totalCost,
            totalNetProfit = netProfit,
            averageProfitMarginPercent = avgMargin,
            topProfitablePackage = topProfitable?.profileName ?: "لا توجد أرباح بعد",
            topProfitablePackageProfit = topProfitable?.netProfit ?: 0.0,
            selectedTimeFilter = timeFilter
        )
    }

    fun computeMonthlySalesList(
        allCards: List<HotspotCard>,
        localCards: List<LocalCardEntity>,
        profiles: List<HotspotProfile>,
        pricings: List<PackagePricingEntity>,
        assignments: List<PosCardAssignmentEntity>,
        batches: List<BatchHistoryEntity>,
        loginHistory: List<UserLoginHistoryEntity>,
        archivedSessions: List<SessionArchiveEntity>,
        savedMonthlyRecords: List<MonthlyProfitRecordEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<MonthlySalesSummary> {
        val currentYm = timestampToYearMonth(nowMillis)
        val packagePriceMap = resolveAllPackagePricings(profiles, allCards, pricings, assignments)

        val localCardMap = localCards.associateBy { it.name.trim().lowercase(Locale.ROOT) }
        val batchCardCreatedAt = buildBatchCreatedAtMap(batches)
        val earliestUsageMap = buildEarliestUsageTimestampMap(loginHistory, archivedSessions)
        val latestUsageMap = buildLatestUsageTimestampMap(loginHistory, archivedSessions)

        // Pick best assignment per card (prefer sold assignment or newest assignedAt)
        val assignmentByCard = linkedMapOf<String, PosCardAssignmentEntity>()
        assignments.sortedWith(
            compareByDescending<PosCardAssignmentEntity> { it.isSold }
                .thenByDescending { it.soldAt }
                .thenByDescending { it.assignedAt }
        ).forEach { assignment ->
            val key = assignment.cardName.trim().lowercase(Locale.ROOT)
            if (key.isNotBlank()) {
                assignmentByCard.putIfAbsent(key, assignment)
            }
        }

        val items = mutableListOf<CardFinanceItem>()
        val seenCardKeys = HashSet<String>()

        val distinctRouterCards = allCards.distinctBy { it.name.trim().lowercase(Locale.ROOT) }
        for (card in distinctRouterCards) {
            val cardKey = card.name.trim().lowercase(Locale.ROOT)
            if (cardKey.isBlank()) continue
            seenCardKeys.add(cardKey)

            val local = localCardMap[cardKey]
            val assignment = assignmentByCard[cardKey]

            var createdTs = batchCardCreatedAt[cardKey]?.takeIf { it > 0L }
                ?: local?.createdTimestamp?.takeIf { it > 0L }
                ?: local?.firstSeenTimestamp?.takeIf { it > 0L }
                ?: assignment?.assignedAt?.takeIf { it > 0L }
                ?: extractTimestampFromCardMetadata(card, nowMillis)
                ?: nowMillis

            val earliestUsage = earliestUsageMap[cardKey]
            if (earliestUsage != null && earliestUsage in MIN_VALID_EPOCH_MS until createdTs) {
                createdTs = earliestUsage
            }

            val sold = isCardUsedOrSold(card) || (assignment?.isSold == true)
            val soldTs = if (sold) {
                assignment?.takeIf { it.isSold && it.soldAt > 0L }?.soldAt
                    ?: latestUsageMap[cardKey]?.takeIf { it > 0L }
                    ?: createdTs
            } else {
                0L
            }

            val profileDisplay = card.profile.trim().ifBlank { assignment?.profileName?.trim().orEmpty().ifBlank { "افتراضي" } }
            val profileKey = profileDisplay.lowercase(Locale.ROOT)
            val pkgPricing = packagePriceMap[profileKey]

            val salePrice = pkgPricing?.salePrice?.takeIf { it > 0.0 }
                ?: parsePriceValue(card.price).takeIf { it > 0.0 }
                ?: assignment?.priceAmount?.takeIf { it > 0.0 }
                ?: 0.0
            val costPrice = pkgPricing?.costPrice ?: 0.0

            val bytesFromCard = card.bytesIn + card.bytesOut
            val bytesFromLocal = (local?.bytesIn ?: 0L) + (local?.bytesOut ?: 0L)

            items.add(
                CardFinanceItem(
                    cardKey = cardKey,
                    profileDisplayName = pkgPricing?.displayName ?: profileDisplay,
                    profileKey = profileKey,
                    createdTs = createdTs,
                    isSold = sold,
                    soldTs = soldTs,
                    salePrice = salePrice,
                    costPrice = costPrice,
                    bytesConsumed = maxOf(bytesFromCard, bytesFromLocal)
                )
            )
        }

        // Include POS assignments whose cards were removed from the live router list
        for ((cardKey, assignment) in assignmentByCard) {
            if (seenCardKeys.contains(cardKey)) continue
            seenCardKeys.add(cardKey)

            val local = localCardMap[cardKey]
            var createdTs = batchCardCreatedAt[cardKey]?.takeIf { it > 0L }
                ?: local?.createdTimestamp?.takeIf { it > 0L }
                ?: assignment.assignedAt.takeIf { it > 0L }
                ?: nowMillis

            val earliestUsage = earliestUsageMap[cardKey]
            if (earliestUsage != null && earliestUsage in MIN_VALID_EPOCH_MS until createdTs) {
                createdTs = earliestUsage
            }

            val sold = assignment.isSold
            val soldTs = if (sold) {
                assignment.soldAt.takeIf { it > 0L }
                    ?: latestUsageMap[cardKey]?.takeIf { it > 0L }
                    ?: createdTs
            } else {
                0L
            }

            val profileDisplay = assignment.profileName.trim().ifBlank { "افتراضي" }
            val profileKey = profileDisplay.lowercase(Locale.ROOT)
            val pkgPricing = packagePriceMap[profileKey]
            val salePrice = pkgPricing?.salePrice?.takeIf { it > 0.0 } ?: assignment.priceAmount.coerceAtLeast(0.0)
            val costPrice = pkgPricing?.costPrice ?: 0.0
            val bytesFromLocal = (local?.bytesIn ?: 0L) + (local?.bytesOut ?: 0L)

            items.add(
                CardFinanceItem(
                    cardKey = cardKey,
                    profileDisplayName = pkgPricing?.displayName ?: profileDisplay,
                    profileKey = profileKey,
                    createdTs = createdTs,
                    isSold = sold,
                    soldTs = soldTs,
                    salePrice = salePrice,
                    costPrice = costPrice,
                    bytesConsumed = bytesFromLocal
                )
            )
        }

        // Account for historical BatchHistoryEntity card counts whose individual cards are no longer present
        val matchedCountByBatchId = mutableMapOf<String, Int>()
        for (batch in batches) {
            if (batch.createdAt <= 0L) continue
            val namesInBatch = parseBatchCardNames(batch.cardsJson)
            val matched = if (namesInBatch.isNotEmpty()) {
                namesInBatch.count { seenCardKeys.contains(it) }
            } else {
                val batchYm = timestampToYearMonth(batch.createdAt)
                items.count {
                    timestampToYearMonth(it.createdTs) == batchYm &&
                        it.profileKey == batch.profileName.trim().lowercase(Locale.ROOT)
                }
            }
            matchedCountByBatchId[batch.batchId] = matched
        }

        val extraGenByMonth = mutableMapOf<YearMonth, Int>()
        val extraTargetRevByMonth = mutableMapOf<YearMonth, Double>()
        for (batch in batches) {
            if (batch.createdAt <= 0L) continue
            val matched = matchedCountByBatchId[batch.batchId] ?: 0
            val extra = (batch.cardCount - matched).coerceAtLeast(0)
            if (extra > 0) {
                val ym = timestampToYearMonth(batch.createdAt)
                extraGenByMonth[ym] = (extraGenByMonth[ym] ?: 0) + extra
                val profKey = batch.profileName.trim().lowercase(Locale.ROOT)
                val unitPrice = packagePriceMap[profKey]?.salePrice?.takeIf { it > 0.0 }
                    ?: parsePriceValue(batch.price)
                extraTargetRevByMonth[ym] = (extraTargetRevByMonth[ym] ?: 0.0) + (extra * unitPrice)
            }
        }

        // Extra bytes from archived sessions / login history for users no longer in allCards/assignments
        val extraBytesByUser = mutableMapOf<String, Pair<YearMonth, Long>>()
        for (session in archivedSessions) {
            val uKey = session.username.trim().lowercase(Locale.ROOT)
            if (uKey.isBlank() || seenCardKeys.contains(uKey) || session.timestamp <= 0L) continue
            val bytes = maxOf(session.totalBytes, session.bytesIn + session.bytesOut)
            if (bytes > 0L) {
                val prev = extraBytesByUser[uKey]
                if (prev == null || bytes > prev.second) {
                    extraBytesByUser[uKey] = timestampToYearMonth(session.timestamp) to bytes
                }
            }
        }
        for (login in loginHistory) {
            val uKey = login.username.trim().lowercase(Locale.ROOT)
            if (uKey.isBlank() || seenCardKeys.contains(uKey) || login.timestamp <= 0L) continue
            val bytes = maxOf(login.totalBytes, login.bytesIn + login.bytesOut)
            if (bytes > 0L) {
                val prev = extraBytesByUser[uKey]
                if (prev == null || bytes > prev.second) {
                    extraBytesByUser[uKey] = timestampToYearMonth(login.timestamp) to bytes
                }
            }
        }
        val extraBytesByMonth = mutableMapOf<YearMonth, Long>()
        for ((_, pair) in extraBytesByUser) {
            extraBytesByMonth[pair.first] = (extraBytesByMonth[pair.first] ?: 0L) + pair.second
        }

        // Partition CardFinanceItems by created Month and sold Month
        val generatedByMonth = items.groupBy { timestampToYearMonth(it.createdTs) }
        val soldByMonth = items.filter { it.isSold }.groupBy { timestampToYearMonth(it.soldTs) }
        val bytesByMonth = mutableMapOf<YearMonth, Long>()
        for (item in items) {
            if (item.bytesConsumed <= 0L) continue
            val ym = if (item.isSold) timestampToYearMonth(item.soldTs) else timestampToYearMonth(item.createdTs)
            bytesByMonth[ym] = (bytesByMonth[ym] ?: 0L) + item.bytesConsumed
        }
        for ((ym, extraB) in extraBytesByMonth) {
            bytesByMonth[ym] = (bytesByMonth[ym] ?: 0L) + extraB
        }

        // Collect all relevant months
        val dataMonths = linkedSetOf<YearMonth>()
        dataMonths.addAll(generatedByMonth.keys)
        dataMonths.addAll(soldByMonth.keys)
        dataMonths.addAll(extraGenByMonth.keys)
        dataMonths.addAll(extraBytesByMonth.keys)

        val savedRecordsMap = savedMonthlyRecords.associateBy { it.monthKey }
        val nonEmptySavedMonths = savedMonthlyRecords
            .filter {
                it.year in 2020..2035 &&
                    it.month in 1..12 &&
                    (it.soldCardsCount > 0 || it.totalCardsCount > 0 || it.totalRevenue > 0.0 || it.totalBytesConsumed > 0L)
            }
            .map { YearMonth(it.year, it.month) }

        val earliestLiveYm = dataMonths.minOrNull()

        val allMonthSet = linkedSetOf<YearMonth>()
        // Always include the latest 6 calendar months up to currentYm
        var cursor = currentYm
        repeat(6) {
            allMonthSet.add(cursor)
            cursor = cursor.previous()
        }
        allMonthSet.addAll(dataMonths)
        allMonthSet.addAll(nonEmptySavedMonths)

        // Fill contiguous months from earliest recent/active month (up to 24 months back) to currentYm
        val minRecentSerial = currentYm.serial - 23
        val earliestToFill = allMonthSet
            .filter { it.serial >= minRecentSerial && it.serial <= currentYm.serial }
            .minOrNull()
        if (earliestToFill != null) {
            var fillCursor = currentYm
            while (fillCursor.serial >= earliestToFill.serial) {
                allMonthSet.add(fillCursor)
                fillCursor = fillCursor.previous()
            }
        }

        val sortedMonths = allMonthSet
            .filter { it.serial <= currentYm.serial }
            .sortedDescending()

        val rawSummaries = sortedMonths.map { ym ->
            val genItems = generatedByMonth[ym].orEmpty()
            val soldItems = soldByMonth[ym].orEmpty()
            val unsoldItems = genItems.filter { !it.isSold }
            val extraGen = extraGenByMonth[ym] ?: 0
            val extraTargetRev = extraTargetRevByMonth[ym] ?: 0.0
            val monthBytes = bytesByMonth[ym] ?: 0L

            val hasLiveData = genItems.isNotEmpty() || soldItems.isNotEmpty() || extraGen > 0 || monthBytes > 0L
            val savedRec = savedRecordsMap[ym.key]

            if (!hasLiveData && savedRec != null &&
                (earliestLiveYm == null || ym < earliestLiveYm) &&
                (savedRec.soldCardsCount > 0 || savedRec.totalCardsCount > 0 || savedRec.totalRevenue > 0.0 || savedRec.totalBytesConsumed > 0L)
            ) {
                val gen = savedRec.totalCardsCount.coerceAtLeast(savedRec.soldCardsCount)
                val sold = savedRec.soldCardsCount
                val unsold = (gen - sold).coerceAtLeast(0)
                val rev = savedRec.totalRevenue
                val cost = savedRec.totalCost
                val profit = savedRec.netProfit.takeIf { it > 0.0 } ?: (rev - cost).coerceAtLeast(0.0)
                val salesPct = if (gen > 0) ((sold.toFloat() / gen.toFloat()) * 100f).coerceIn(0f, 100f) else if (sold > 0) 100f else 0f
                val pkgList = if (savedRec.topSellingProfile.isNotBlank() && sold > 0) {
                    listOf(
                        MonthlyPackageSales(
                            packageName = savedRec.topSellingProfile,
                            cardsSold = sold,
                            totalRevenue = rev,
                            netProfit = profit,
                            sharePercentage = 100f
                        )
                    )
                } else emptyList()

                MonthlySalesSummary(
                    monthKey = ym.key,
                    monthName = ym.labelAr,
                    totalCardsGenerated = gen,
                    totalCardsSold = sold,
                    totalCardsUnsold = unsold,
                    totalRevenue = rev,
                    totalCost = cost,
                    netProfit = profit,
                    potentialUnsoldValue = 0.0,
                    salesPercentage = salesPct,
                    targetDeficit = 0.0,
                    isCurrentMonth = (ym == currentYm),
                    packageBreakdown = pkgList,
                    dailyBreakdown = emptyList(),
                    totalBytesConsumed = savedRec.totalBytesConsumed
                )
            } else {
                val totalGen = genItems.size + extraGen
                val totalSold = soldItems.size
                val totalUnsold = unsoldItems.size
                val revenue = soldItems.sumOf { it.salePrice }
                val cost = soldItems.sumOf { it.costPrice }
                val profit = (revenue - cost).coerceAtLeast(0.0)
                val potentialUnsold = unsoldItems.sumOf { it.salePrice }
                val targetRev = genItems.sumOf { it.salePrice } + extraTargetRev
                val deficit = (targetRev - revenue).coerceAtLeast(0.0)
                val salesPct = when {
                    totalGen > 0 -> ((totalSold.toFloat() / totalGen.toFloat()) * 100f).coerceIn(0f, 100f)
                    totalSold > 0 -> 100f
                    else -> 0f
                }

                val pkgBreakdown = soldItems
                    .groupBy { it.profileKey }
                    .values
                    .map { pkgItems ->
                        val pkgName = pkgItems.first().profileDisplayName
                        val pkgSold = pkgItems.size
                        val pkgRev = pkgItems.sumOf { it.salePrice }
                        val pkgCost = pkgItems.sumOf { it.costPrice }
                        val pkgProfit = (pkgRev - pkgCost).coerceAtLeast(0.0)
                        val share = when {
                            revenue > 0.0 -> ((pkgRev / revenue) * 100f).toFloat().coerceIn(0f, 100f)
                            totalSold > 0 -> ((pkgSold.toFloat() / totalSold.toFloat()) * 100f).coerceIn(0f, 100f)
                            else -> 0f
                        }
                        MonthlyPackageSales(
                            packageName = pkgName,
                            cardsSold = pkgSold,
                            totalRevenue = pkgRev,
                            netProfit = pkgProfit,
                            sharePercentage = share
                        )
                    }
                    .sortedWith(
                        compareByDescending<MonthlyPackageSales> { it.netProfit }
                            .thenByDescending { it.totalRevenue }
                            .thenByDescending { it.cardsSold }
                    )

                val monthShortName = MONTH_NAMES_AR.getOrElse(ym.month - 1) { ym.month.toString() }
                val dailyBreakdown = soldItems
                    .groupBy { dayOfMonthFromTs(it.soldTs) }
                    .entries
                    .sortedBy { it.key }
                    .map { (dayNum, dayItems) ->
                        val dayRev = dayItems.sumOf { it.salePrice }
                        val dayCost = dayItems.sumOf { it.costPrice }
                        DailySalesPoint(
                            dayNumber = dayNum,
                            dayLabel = "$dayNum $monthShortName",
                            cardsSold = dayItems.size,
                            revenue = dayRev,
                            profit = (dayRev - dayCost).coerceAtLeast(0.0)
                        )
                    }

                MonthlySalesSummary(
                    monthKey = ym.key,
                    monthName = ym.labelAr,
                    totalCardsGenerated = totalGen,
                    totalCardsSold = totalSold,
                    totalCardsUnsold = totalUnsold,
                    totalRevenue = revenue,
                    totalCost = cost,
                    netProfit = profit,
                    potentialUnsoldValue = potentialUnsold,
                    salesPercentage = salesPct,
                    targetDeficit = deficit,
                    isCurrentMonth = (ym == currentYm),
                    packageBreakdown = pkgBreakdown,
                    dailyBreakdown = dailyBreakdown,
                    totalBytesConsumed = monthBytes
                )
            }
        }

        return rawSummaries.mapIndexed { index, item ->
            val prevMonth = rawSummaries.getOrNull(index + 1)
            val growth = if (prevMonth != null && prevMonth.netProfit > 0.0) {
                ((item.netProfit - prevMonth.netProfit) / prevMonth.netProfit) * 100.0
            } else if (prevMonth != null && prevMonth.totalRevenue > 0.0 && prevMonth.netProfit == 0.0 && item.netProfit == 0.0) {
                ((item.totalRevenue - prevMonth.totalRevenue) / prevMonth.totalRevenue) * 100.0
            } else {
                0.0
            }
            item.copy(growthRatePercent = growth)
        }
    }

    private fun buildBatchCreatedAtMap(batches: List<BatchHistoryEntity>): Map<String, Long> {
        val map = mutableMapOf<String, Long>()
        for (batch in batches) {
            if (batch.createdAt <= 0L || batch.cardsJson.isBlank()) continue
            val names = parseBatchCardNames(batch.cardsJson)
            for (name in names) {
                val existing = map[name]
                if (existing == null || batch.createdAt < existing) {
                    map[name] = batch.createdAt
                }
            }
        }
        return map
    }

    private fun parseBatchCardNames(cardsJson: String): List<String> {
        if (cardsJson.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(cardsJson)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val n = arr.optString(i, "").trim().lowercase(Locale.ROOT)
                    if (n.isNotBlank()) add(n)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun buildEarliestUsageTimestampMap(
        loginHistory: List<UserLoginHistoryEntity>,
        archivedSessions: List<SessionArchiveEntity>
    ): Map<String, Long> {
        val map = mutableMapOf<String, Long>()
        for (login in loginHistory) {
            val key = login.username.trim().lowercase(Locale.ROOT)
            if (key.isBlank() || login.timestamp <= 0L) continue
            val cur = map[key]
            map[key] = if (cur == null) login.timestamp else min(cur, login.timestamp)
        }
        for (session in archivedSessions) {
            val key = session.username.trim().lowercase(Locale.ROOT)
            if (key.isBlank() || session.timestamp <= 0L) continue
            val cur = map[key]
            map[key] = if (cur == null) session.timestamp else min(cur, session.timestamp)
        }
        return map
    }

    private fun buildLatestUsageTimestampMap(
        loginHistory: List<UserLoginHistoryEntity>,
        archivedSessions: List<SessionArchiveEntity>
    ): Map<String, Long> {
        val map = mutableMapOf<String, Long>()
        for (login in loginHistory) {
            val key = login.username.trim().lowercase(Locale.ROOT)
            if (key.isBlank() || login.timestamp <= 0L) continue
            val cur = map[key]
            map[key] = if (cur == null) login.timestamp else maxOf(cur, login.timestamp)
        }
        for (session in archivedSessions) {
            val key = session.username.trim().lowercase(Locale.ROOT)
            if (key.isBlank() || session.timestamp <= 0L) continue
            val cur = map[key]
            map[key] = if (cur == null) session.timestamp else maxOf(cur, session.timestamp)
        }
        return map
    }

    private fun timestampToYearMonth(ts: Long): YearMonth {
        val cal = Calendar.getInstance()
        cal.timeInMillis = ts
        return YearMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    private fun dayOfMonthFromTs(ts: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = ts
        return cal.get(Calendar.DAY_OF_MONTH)
    }
}
