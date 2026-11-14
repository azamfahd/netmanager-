package com.example.data.model

import com.example.data.local.MonthlyProfitRecordEntity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs

@IgnoreExtraProperties
data class MonthlyPackageSalesCloud(
    val packageName: String = "",
    val cardsSold: Long = 0L,
    val totalRevenue: Double = 0.0,
    val netProfit: Double = 0.0,
    val sharePercentage: Double = 0.0
)

@IgnoreExtraProperties
data class MonthlyProfitCloudRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val monthKey: String = "",
    val monthNameAr: String = "",
    val year: Long = 0L,
    val month: Long = 0L,
    val yearMonthSerial: Long = 0L,
    val totalRevenue: Double = 0.0,
    val totalCost: Double = 0.0,
    val netProfit: Double = 0.0,
    val soldCardsCount: Long = 0L,
    val totalCardsCount: Long = 0L,
    val topSellingProfile: String = "",
    val totalBytesConsumed: Long = 0L,
    val packageBreakdown: List<MonthlyPackageSalesCloud> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

sealed class FinanceCloudSyncStatus {
    data object SignedOut : FinanceCloudSyncStatus()
    data object Syncing : FinanceCloudSyncStatus()
    data class Synced(
        val syncedMonthsCount: Int,
        val skippedInvalidCount: Int = 0,
        val lastSyncedAtMs: Long = System.currentTimeMillis()
    ) : FinanceCloudSyncStatus()
    data class Error(val message: String) : FinanceCloudSyncStatus()
}

data class ValidatedMonthlyFetchResult(
    val entities: List<MonthlyProfitRecordEntity> = emptyList(),
    val breakdownsByMonth: Map<String, List<MonthlyPackageSales>> = emptyMap(),
    val cloudRecordsByMonth: Map<String, MonthlyProfitCloudRecord> = emptyMap(),
    val rejectedDocsCount: Int = 0,
    val rejectionReasons: List<String> = emptyList()
)

data class FinanceSyncBatchReport(
    val createdCount: Int = 0,
    val updatedCount: Int = 0,
    val skippedUnchangedCount: Int = 0,
    val skippedZeroClobberCount: Int = 0,
    val rejectedValidationCount: Int = 0
) {
    val totalWrittenCount: Int get() = createdCount + updatedCount
}

sealed class MonthlyProfitValidationResult {
    data class Valid(
        val entity: MonthlyProfitRecordEntity,
        val breakdown: List<MonthlyPackageSales>,
        val record: MonthlyProfitCloudRecord
    ) : MonthlyProfitValidationResult()

    data class Invalid(
        val docId: String,
        val reason: String
    ) : MonthlyProfitValidationResult()
}

data class MonthlyProfitUploadCandidate(
    val userId: String,
    val monthKey: String,
    val monthNameAr: String,
    val year: Int,
    val month: Int,
    val yearMonthSerial: Long,
    val totalRevenue: Double,
    val totalCost: Double,
    val netProfit: Double,
    val soldCardsCount: Long,
    val totalCardsCount: Long,
    val topSellingProfile: String,
    val totalBytesConsumed: Long,
    val packageBreakdown: List<MonthlyPackageSalesCloud>
) {
    fun hasMeaningfulData(): Boolean {
        return totalRevenue > 0.0 ||
            soldCardsCount > 0L ||
            totalCardsCount > 0L ||
            totalBytesConsumed > 0L
    }

    fun isEquivalentToCloud(cloud: MonthlyProfitCloudRecord): Boolean {
        if (monthNameAr != cloud.monthNameAr) return false
        if (abs(totalRevenue - cloud.totalRevenue) > 0.001) return false
        if (abs(totalCost - cloud.totalCost) > 0.001) return false
        if (abs(netProfit - cloud.netProfit) > 0.001) return false
        if (soldCardsCount != cloud.soldCardsCount) return false
        if (totalCardsCount != cloud.totalCardsCount) return false
        if (topSellingProfile != cloud.topSellingProfile) return false
        if (totalBytesConsumed != cloud.totalBytesConsumed) return false
        if (packageBreakdown.size != cloud.packageBreakdown.size) return false
        for (i in packageBreakdown.indices) {
            val a = packageBreakdown[i]
            val b = cloud.packageBreakdown[i]
            if (a.packageName != b.packageName ||
                a.cardsSold != b.cardsSold ||
                abs(a.totalRevenue - b.totalRevenue) > 0.001 ||
                abs(a.netProfit - b.netProfit) > 0.001 ||
                abs(a.sharePercentage - b.sharePercentage) > 0.01
            ) {
                return false
            }
        }
        return true
    }

    private fun packageBreakdownToMaps(): List<Map<String, Any>> {
        return packageBreakdown.map { pkg ->
            mapOf(
                "packageName" to pkg.packageName,
                "cardsSold" to pkg.cardsSold,
                "totalRevenue" to pkg.totalRevenue,
                "netProfit" to pkg.netProfit,
                "sharePercentage" to pkg.sharePercentage
            )
        }
    }

    fun toCreateMap(): Map<String, Any> {
        return mapOf(
            "userId" to userId,
            "monthKey" to monthKey,
            "monthNameAr" to monthNameAr,
            "year" to year.toLong(),
            "month" to month.toLong(),
            "yearMonthSerial" to yearMonthSerial,
            "totalRevenue" to totalRevenue,
            "totalCost" to totalCost,
            "netProfit" to netProfit,
            "soldCardsCount" to soldCardsCount,
            "totalCardsCount" to totalCardsCount,
            "topSellingProfile" to topSellingProfile,
            "totalBytesConsumed" to totalBytesConsumed,
            "packageBreakdown" to packageBreakdownToMaps(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    fun toUpdateMap(): Map<String, Any> {
        return mapOf(
            "monthNameAr" to monthNameAr,
            "totalRevenue" to totalRevenue,
            "totalCost" to totalCost,
            "netProfit" to netProfit,
            "soldCardsCount" to soldCardsCount,
            "totalCardsCount" to totalCardsCount,
            "topSellingProfile" to topSellingProfile,
            "totalBytesConsumed" to totalBytesConsumed,
            "packageBreakdown" to packageBreakdownToMaps(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}

object FinanceCloudValidator {
    private val MONTH_KEY_REGEX = Regex("^20(2[0-9]|3[0-5])-(0[1-9]|1[0-2])$")
    private val SAFE_DOC_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]{1,128}$")
    private const val MAX_MONEY = 1_000_000_000.0
    private const val MAX_CARDS = 10_000_000L
    private const val MAX_BREAKDOWN_ITEMS = 50

    fun isValidMonthKey(monthKey: String): Boolean {
        return MONTH_KEY_REGEX.matches(monthKey.trim())
    }

    fun sanitizeFirestoreDocId(raw: String): String {
        val trimmed = raw.trim()
        if (SAFE_DOC_ID_REGEX.matches(trimmed)) {
            return trimmed
        }
        val slug = trimmed
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(64)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(trimmed.toByteArray(Charsets.UTF_8))
            .take(6)
            .joinToString("") { "%02x".format(Locale.US, it) }
        return if (slug.isNotBlank()) "pkg_${slug}_$digest" else "pkg_$digest"
    }

    fun validateCloudDocument(
        doc: DocumentSnapshot,
        expectedUserId: String? = null
    ): MonthlyProfitValidationResult {
        val docId = doc.id
        if (!isValidMonthKey(docId)) {
            return MonthlyProfitValidationResult.Invalid(docId, "Invalid month document ID: '$docId'")
        }
        val record = try {
            doc.toObject(
                MonthlyProfitCloudRecord::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        } catch (e: Exception) {
            return MonthlyProfitValidationResult.Invalid(docId, "Deserialization failed: ${e.message}")
        } ?: return MonthlyProfitValidationResult.Invalid(docId, "Empty/null document snapshot")

        return validateRecordFields(docId, record, expectedUserId)
    }

    fun validateRecordFields(
        docId: String,
        record: MonthlyProfitCloudRecord,
        expectedUserId: String? = null
    ): MonthlyProfitValidationResult {
        if (!isValidMonthKey(docId) || record.monthKey != docId) {
            return MonthlyProfitValidationResult.Invalid(
                docId,
                "monthKey '${record.monthKey}' mismatches docId '$docId'"
            )
        }
        if (record.userId.isBlank() || record.userId.length > 128) {
            return MonthlyProfitValidationResult.Invalid(docId, "Missing/invalid userId")
        }
        if (expectedUserId != null && record.userId != expectedUserId) {
            return MonthlyProfitValidationResult.Invalid(docId, "userId ownership mismatch")
        }
        val monthName = record.monthNameAr.trim()
        if (monthName.isEmpty() || monthName.length > 60) {
            return MonthlyProfitValidationResult.Invalid(docId, "Invalid monthNameAr")
        }
        if (record.year !in 2020L..2035L || record.month !in 1L..12L) {
            return MonthlyProfitValidationResult.Invalid(
                docId,
                "Out-of-range year/month (${record.year}/${record.month})"
            )
        }
        val formattedYm = String.format(Locale.US, "%04d-%02d", record.year, record.month)
        if (formattedYm != docId) {
            return MonthlyProfitValidationResult.Invalid(
                docId,
                "Formatted year-month '$formattedYm' != '$docId'"
            )
        }
        val expectedSerial = record.year * 12L + record.month
        if (record.yearMonthSerial != expectedSerial) {
            return MonthlyProfitValidationResult.Invalid(
                docId,
                "yearMonthSerial ${record.yearMonthSerial} != expected $expectedSerial"
            )
        }
        if (!record.totalRevenue.isFinite() || record.totalRevenue < 0.0 || record.totalRevenue > MAX_MONEY ||
            !record.totalCost.isFinite() || record.totalCost < 0.0 || record.totalCost > MAX_MONEY ||
            !record.netProfit.isFinite() || record.netProfit < 0.0 || record.netProfit > MAX_MONEY
        ) {
            return MonthlyProfitValidationResult.Invalid(docId, "Invalid negative/NaN/overflow monetary fields")
        }
        if (record.soldCardsCount !in 0L..MAX_CARDS ||
            record.totalCardsCount !in record.soldCardsCount..MAX_CARDS
        ) {
            return MonthlyProfitValidationResult.Invalid(
                docId,
                "Invalid card counts: sold=${record.soldCardsCount}, total=${record.totalCardsCount}"
            )
        }
        if (record.totalBytesConsumed < 0L) {
            return MonthlyProfitValidationResult.Invalid(docId, "Negative bytes consumed")
        }
        if (record.topSellingProfile.length > 120) {
            return MonthlyProfitValidationResult.Invalid(docId, "topSellingProfile exceeds 120 chars")
        }
        if (record.packageBreakdown.size > MAX_BREAKDOWN_ITEMS) {
            return MonthlyProfitValidationResult.Invalid(docId, "packageBreakdown exceeds max size $MAX_BREAKDOWN_ITEMS")
        }

        val validatedBreakdown = mutableListOf<MonthlyPackageSales>()
        for (pkg in record.packageBreakdown) {
            val pkgName = pkg.packageName.trim()
            if (pkgName.isEmpty() || pkgName.length > 120 ||
                pkg.cardsSold !in 0L..MAX_CARDS ||
                !pkg.totalRevenue.isFinite() || pkg.totalRevenue < 0.0 || pkg.totalRevenue > MAX_MONEY ||
                !pkg.netProfit.isFinite() || pkg.netProfit < 0.0 || pkg.netProfit > MAX_MONEY ||
                !pkg.sharePercentage.isFinite() || pkg.sharePercentage < 0.0 || pkg.sharePercentage > 100.1
            ) {
                return MonthlyProfitValidationResult.Invalid(docId, "Invalid packageBreakdown entry '$pkgName'")
            }
            validatedBreakdown.add(
                MonthlyPackageSales(
                    packageName = pkgName,
                    cardsSold = pkg.cardsSold.toInt(),
                    totalRevenue = pkg.totalRevenue,
                    netProfit = pkg.netProfit,
                    sharePercentage = pkg.sharePercentage.toFloat().coerceIn(0f, 100f)
                )
            )
        }

        val updatedMs = record.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
        val entity = MonthlyProfitRecordEntity(
            monthKey = docId,
            monthNameAr = monthName,
            year = record.year.toInt(),
            month = record.month.toInt(),
            totalRevenue = record.totalRevenue,
            totalCost = record.totalCost,
            netProfit = record.netProfit,
            soldCardsCount = record.soldCardsCount.toInt(),
            totalCardsCount = record.totalCardsCount.toInt(),
            topSellingProfile = record.topSellingProfile.trim(),
            totalBytesConsumed = record.totalBytesConsumed,
            lastUpdatedTimestamp = updatedMs
        )
        return MonthlyProfitValidationResult.Valid(
            entity = entity,
            breakdown = validatedBreakdown,
            record = record.copy(id = docId)
        )
    }

    fun validateMonthlySummaryForUpload(
        summary: MonthlySalesSummary,
        userId: String
    ): MonthlyProfitUploadCandidate? {
        val cleanUid = userId.trim()
        val key = summary.monthKey.trim()
        if (cleanUid.isEmpty() || cleanUid.length > 128 || !isValidMonthKey(key)) return null

        val parts = key.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val month = parts.getOrNull(1)?.toIntOrNull() ?: return null
        if (year !in 2020..2035 || month !in 1..12) return null

        val revenue = summary.totalRevenue
        val cost = summary.totalCost
        val profit = summary.netProfit
        if (!revenue.isFinite() || revenue < 0.0 || revenue > MAX_MONEY ||
            !cost.isFinite() || cost < 0.0 || cost > MAX_MONEY ||
            !profit.isFinite() || profit < 0.0 || profit > MAX_MONEY
        ) {
            return null
        }

        val sold = summary.totalCardsSold.toLong()
        val total = maxOf(summary.totalCardsGenerated.toLong(), sold)
        if (sold !in 0L..MAX_CARDS || total !in sold..MAX_CARDS) return null
        val bytes = summary.totalBytesConsumed
        if (bytes < 0L) return null

        val monthName = summary.monthName.trim().ifBlank { key }.take(60)
        val topPkg = (summary.packageBreakdown.firstOrNull()?.packageName ?: "").trim().take(120)

        val breakdown = summary.packageBreakdown
            .asSequence()
            .filter {
                it.packageName.isNotBlank() &&
                    it.cardsSold >= 0 &&
                    it.totalRevenue.isFinite() && it.totalRevenue >= 0.0 && it.totalRevenue <= MAX_MONEY &&
                    it.netProfit.isFinite() && it.netProfit >= 0.0 && it.netProfit <= MAX_MONEY
            }
            .take(MAX_BREAKDOWN_ITEMS)
            .map { pkg ->
                val share = if (pkg.sharePercentage.isFinite()) {
                    pkg.sharePercentage.toDouble().coerceIn(0.0, 100.0)
                } else {
                    0.0
                }
                MonthlyPackageSalesCloud(
                    packageName = pkg.packageName.trim().take(120),
                    cardsSold = pkg.cardsSold.toLong(),
                    totalRevenue = pkg.totalRevenue,
                    netProfit = pkg.netProfit,
                    sharePercentage = share
                )
            }
            .toList()

        return MonthlyProfitUploadCandidate(
            userId = cleanUid,
            monthKey = key,
            monthNameAr = monthName,
            year = year,
            month = month,
            yearMonthSerial = year.toLong() * 12L + month.toLong(),
            totalRevenue = revenue,
            totalCost = cost,
            netProfit = profit,
            soldCardsCount = sold,
            totalCardsCount = total,
            topSellingProfile = topPkg,
            totalBytesConsumed = bytes,
            packageBreakdown = breakdown
        )
    }
}
