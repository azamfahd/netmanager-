package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.MonthlyPackageSales
import com.example.data.model.MonthlySalesSummary
import com.example.data.network.FinanceFirestoreRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class FinanceFirestoreRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun uniqueEmail(prefix: String): String {
        val token = UUID.randomUUID().toString().replace("-", "").take(10)
        return "${prefix}_$token@test.com"
    }

    private fun sampleSummary(
        monthKey: String,
        monthName: String,
        sold: Int,
        total: Int,
        revenue: Double,
        cost: Double,
        bytes: Long = 1_000_000L
    ): MonthlySalesSummary {
        return MonthlySalesSummary(
            monthKey = monthKey,
            monthName = monthName,
            totalCardsGenerated = total,
            totalCardsSold = sold,
            totalCardsUnsold = (total - sold).coerceAtLeast(0),
            totalRevenue = revenue,
            totalCost = cost,
            netProfit = (revenue - cost).coerceAtLeast(0.0),
            totalBytesConsumed = bytes,
            packageBreakdown = listOf(
                MonthlyPackageSales(
                    packageName = "500-Day",
                    cardsSold = sold,
                    totalRevenue = revenue,
                    netProfit = (revenue - cost).coerceAtLeast(0.0),
                    sharePercentage = 100f
                )
            )
        )
    }

    @Test
    fun upsertAndFetchMonthlyProfitsSequentially_multiPageCursor_returnsChronologicalOrderAndBreakdowns() = runBlocking {
        val uid = signInTestUser(uniqueEmail("alice_seq"))
        val repository = FinanceFirestoreRepository(firestore, auth)

        val outOfOrderMonths = listOf(
            sampleSummary("2026-11", "نوفمبر 2026", sold = 10, total = 12, revenue = 5000.0, cost = 2000.0),
            sampleSummary("2025-10", "أكتوبر 2025", sold = 5, total = 8, revenue = 2500.0, cost = 1000.0),
            sampleSummary("2026-06", "يونيو 2026", sold = 8, total = 10, revenue = 4000.0, cost = 1600.0),
            sampleSummary("2025-12", "ديسمبر 2025", sold = 6, total = 6, revenue = 3000.0, cost = 1200.0),
            sampleSummary("2026-01", "يناير 2026", sold = 7, total = 9, revenue = 3500.0, cost = 1400.0)
        )

        val writeReport = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertMonthlyProfitsValidated(outOfOrderMonths).getOrThrow()
        }
        assertEquals(5, writeReport.createdCount)
        assertEquals(0, writeReport.rejectedValidationCount)

        // Fetch using pageSize = 2 to force 3 sequential cursor pages
        val fetchResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.fetchMonthlyProfitsSequentially(uid, pageSize = 2L).getOrThrow()
        }

        assertEquals(
            listOf("2025-10", "2025-12", "2026-01", "2026-06", "2026-11"),
            fetchResult.entities.map { it.monthKey }
        )
        assertEquals(0, fetchResult.rejectedDocsCount)
        assertEquals(2500.0, fetchResult.entities.first().totalRevenue, 0.01)
        assertEquals(5000.0, fetchResult.entities.last().totalRevenue, 0.01)
        assertEquals("500-Day", fetchResult.breakdownsByMonth["2025-10"]?.firstOrNull()?.packageName)
    }

    @Test
    fun upsertMonthlyProfitsValidated_updatesChangedAndSkipsZeroClobberAndUnchanged() = runBlocking {
        val uid = signInTestUser(uniqueEmail("alice_upd"))
        val repository = FinanceFirestoreRepository(firestore, auth)

        val initial = listOf(
            sampleSummary("2026-09", "سبتمبر 2026", sold = 10, total = 15, revenue = 5000.0, cost = 2000.0)
        )
        val firstReport = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertMonthlyProfitsValidated(initial).getOrThrow()
        }
        assertEquals(1, firstReport.createdCount)

        val afterCreate = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.fetchMonthlyProfitsSequentially(uid).getOrThrow()
        }
        val createdTs = afterCreate.cloudRecordsByMonth["2026-09"]?.createdAt
        assertNotNull(createdTs)

        // Idempotent re-submit skips unchanged
        val secondReport = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertMonthlyProfitsValidated(initial).getOrThrow()
        }
        assertEquals(1, secondReport.skippedUnchangedCount)
        assertEquals(0, secondReport.totalWrittenCount)

        // Zeroed summary must NEVER overwrite existing non-zero cloud month
        val zeroed = listOf(
            sampleSummary("2026-09", "سبتمبر 2026", sold = 0, total = 0, revenue = 0.0, cost = 0.0, bytes = 0L)
        )
        val zeroReport = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertMonthlyProfitsValidated(zeroed).getOrThrow()
        }
        assertEquals(1, zeroReport.skippedZeroClobberCount)
        assertEquals(0, zeroReport.totalWrittenCount)

        // Real increase triggers update preserving createdAt
        val updated = listOf(
            sampleSummary("2026-09", "سبتمبر 2026", sold = 14, total = 15, revenue = 7000.0, cost = 2800.0)
        )
        val updateReport = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertMonthlyProfitsValidated(updated).getOrThrow()
        }
        assertEquals(1, updateReport.updatedCount)

        val afterUpdate = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.fetchMonthlyProfitsSequentially(uid).getOrThrow()
        }
        val cloudRecord = afterUpdate.cloudRecordsByMonth["2026-09"]
        assertNotNull(cloudRecord)
        assertEquals(7000.0, cloudRecord!!.totalRevenue, 0.01)
        assertEquals(createdTs, cloudRecord.createdAt)
    }

    @Test
    fun upsertMonthlyProfitsValidated_rejectsCorruptSummariesBeforeFirestoreWrite() = runBlocking {
        val uid = signInTestUser(uniqueEmail("alice_val"))
        val repository = FinanceFirestoreRepository(firestore, auth)

        val mixed = listOf(
            // Bad month key
            MonthlySalesSummary(monthKey = "2026-13", monthName = "bad", totalRevenue = 100.0, totalCardsSold = 1, totalCardsGenerated = 1),
            // Negative profit
            MonthlySalesSummary(monthKey = "2026-04", monthName = "أبريل 2026", totalRevenue = 500.0, totalCost = 100.0, netProfit = -50.0, totalCardsSold = 1, totalCardsGenerated = 2),
            // Negative revenue
            MonthlySalesSummary(monthKey = "2026-05", monthName = "مايو 2026", totalRevenue = -10.0, totalCost = 0.0, netProfit = 0.0, totalCardsSold = 0, totalCardsGenerated = 1),
            // Valid record
            sampleSummary("2026-03", "مارس 2026", sold = 4, total = 5, revenue = 2000.0, cost = 800.0)
        )

        val report = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertMonthlyProfitsValidated(mixed).getOrThrow()
        }
        assertEquals(3, report.rejectedValidationCount)
        assertEquals(1, report.createdCount)

        val fetched = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.fetchMonthlyProfitsSequentially(uid).getOrThrow()
        }
        assertEquals(listOf("2026-03"), fetched.entities.map { it.monthKey })
    }

    @Test
    fun observeMonthlyProfits_authenticatedOwner_emitsRealtimeSequentialUpdates() = runBlocking {
        val uid = signInTestUser(uniqueEmail("alice_rt"))
        val repository = FinanceFirestoreRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertMonthlyProfitsValidated(
                listOf(sampleSummary("2026-07", "يوليو 2026", sold = 3, total = 4, revenue = 1500.0, cost = 600.0))
            ).getOrThrow()
        }

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeMonthlyProfits(uid).first { res ->
                res.entities.any { it.monthKey == "2026-07" }
            }
        }
        assertEquals(1500.0, emitted.entities.first { it.monthKey == "2026-07" }.totalRevenue, 0.01)
    }

    @Test
    fun saveAndFetchPackagePricing_sanitizesArabicAndSlashesSafely() = runBlocking {
        val uid = signInTestUser(uniqueEmail("alice_pkg"))
        val repository = FinanceFirestoreRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveValidatedPackagePricing("باقة / 500 يومي", costPrice = 200.0, salePrice = 500.0).getOrThrow()
        }

        val pricings = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.fetchPackagePricings(uid).getOrThrow()
        }
        assertEquals(1, pricings.size)
        assertEquals("باقة / 500 يومي", pricings.first().profileName)
        assertEquals(200.0, pricings.first().costPrice, 0.01)
        assertEquals(500.0, pricings.first().salePrice, 0.01)
    }

    @Test
    fun fetchMonthlyProfitsSequentially_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(uniqueEmail("alice_iso"))
        val aliceRepo = FinanceFirestoreRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.upsertMonthlyProfitsValidated(
                listOf(sampleSummary("2026-08", "أغسطس 2026", sold = 2, total = 3, revenue = 1000.0, cost = 400.0))
            ).getOrThrow()
        }

        signInTestUser(uniqueEmail("bob_iso"))
        val bobRepo = FinanceFirestoreRepository(firestore, auth)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.fetchMonthlyProfitsSequentially(aliceUid)
        }
        assertTrue(result.isFailure)
        val firestoreEx = result.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException, got ${result.exceptionOrNull()}", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx!!.code)
    }

    @Test
    fun observeMonthlyProfits_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(uniqueEmail("alice_unauth"))
        auth.signOut()

        val repository = FinanceFirestoreRepository(firestore, auth)
        var caught: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeMonthlyProfits(aliceUid).first()
            }
        } catch (e: Exception) {
            caught = e
        }

        assertNotNull("Expected PERMISSION_DENIED when observing while signed out", caught)
        val firestoreEx = caught!!.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException, got $caught", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx!!.code)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
