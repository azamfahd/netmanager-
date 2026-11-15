package com.example.data.network

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.local.MonthlyProfitRecordEntity
import com.example.data.local.PackagePricingEntity
import com.example.data.model.FinanceCloudValidator
import com.example.data.model.FinanceSyncBatchReport
import com.example.data.model.MonthlyPackageSales
import com.example.data.model.MonthlyProfitCloudRecord
import com.example.data.model.MonthlyProfitUploadCandidate
import com.example.data.model.MonthlyProfitValidationResult
import com.example.data.model.MonthlySalesSummary
import com.example.data.model.ValidatedMonthlyFetchResult
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FinanceFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id)),
        auth = Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("AUTH_REQUIRED: User must be signed in before accessing Finance Firestore data.")
    }

    private fun monthlyProfitsCollection(userId: String) =
        db.collection(USERS_COLLECTION)
            .document(userId)
            .collection(MONTHLY_PROFITS_SUBCOLLECTION)

    private fun packagePricingCollection(userId: String) =
        db.collection(USERS_COLLECTION)
            .document(userId)
            .collection(PACKAGE_PRICING_SUBCOLLECTION)

    /**
     * Real-time chronological listener ordered sequentially by yearMonthSerial (oldest -> newest).
     * Rejects malformed/corrupt documents via FinanceCloudValidator and logs rule failures cleanly.
     */
    fun observeMonthlyProfits(userId: String): Flow<ValidatedMonthlyFetchResult> = callbackFlow {
        val cleanUid = userId.trim()
        if (cleanUid.isEmpty()) {
            close(IllegalArgumentException("userId must not be blank"))
            return@callbackFlow
        }

        val query = monthlyProfitsCollection(cleanUid)
            .orderBy("yearMonthSerial", Query.Direction.ASCENDING)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                // Only log if this user is still actively authenticated to avoid spurious noise on sign-out
                if (auth.currentUser?.uid == cleanUid) {
                    handleFirestoreError(error, OperationType.LIST, "$USERS_COLLECTION/$cleanUid/$MONTHLY_PROFITS_SUBCOLLECTION")
                }
                close(error)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val parsed = validateSnapshots(snapshot.documents, cleanUid)
                trySend(parsed)
            }
        }

        awaitClose {
            registration.remove()
        }
    }

    /**
     * Cursor-paginated sequential fetch ordered by yearMonthSerial ascending so every historical
     * month is retrieved reliably without missing records or composite-index requirements.
     */
    suspend fun fetchMonthlyProfitsSequentially(
        userId: String,
        pageSize: Long = DEFAULT_PAGE_SIZE
    ): Result<ValidatedMonthlyFetchResult> = withContext(Dispatchers.IO) {
        val cleanUid = userId.trim()
        if (cleanUid.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("userId must not be blank"))
        }
        val path = "$USERS_COLLECTION/$cleanUid/$MONTHLY_PROFITS_SUBCOLLECTION"
        try {
            val safePageSize = pageSize.coerceIn(1L, 100L)
            val allDocs = mutableListOf<DocumentSnapshot>()
            var lastSnapshot: DocumentSnapshot? = null

            do {
                var query: Query = monthlyProfitsCollection(cleanUid)
                    .orderBy("yearMonthSerial", Query.Direction.ASCENDING)
                    .limit(safePageSize)
                if (lastSnapshot != null) {
                    query = query.startAfter(lastSnapshot)
                }
                val snap = query.get().await()
                val pageDocs = snap.documents
                allDocs.addAll(pageDocs)
                lastSnapshot = pageDocs.lastOrNull()
            } while (pageDocs.size >= safePageSize.toInt() && lastSnapshot != null)

            val result = validateSnapshots(allDocs, cleanUid)
            if (result.rejectedDocsCount > 0) {
                runCatching {
                    Log.w(
                        TAG,
                        "Skipped ${result.rejectedDocsCount} invalid cloud monthly records for $cleanUid: ${result.rejectionReasons.joinToString("; ")}"
                    )
                }
            }
            Result.success(result)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    /**
     * Validates all monthly summaries strictly and commits changes sequentially in WriteBatch chunks:
     * - Rejects NaN/negative/malformed summaries before touching Firestore
     * - Protects non-zero cloud history against accidental zero-clobbers
     * - Skips unchanged months (idempotent diff)
     * - Routes new months to batch.set (with createdAt + updatedAt) and existing months to
     *   batch.update (preserving immutable createdAt/yearMonthSerial)
     */
    suspend fun upsertMonthlyProfitsValidated(
        summaries: List<MonthlySalesSummary>
    ): Result<FinanceSyncBatchReport> = withContext(Dispatchers.IO) {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
        upsertMonthlyProfitsForUser(uid, summaries)
    }

    internal suspend fun upsertMonthlyProfitsForUser(
        userId: String,
        summaries: List<MonthlySalesSummary>
    ): Result<FinanceSyncBatchReport> = withContext(Dispatchers.IO) {
        val cleanUid = userId.trim()
        if (cleanUid.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("userId must not be blank"))
        }
        val collectionPath = "$USERS_COLLECTION/$cleanUid/$MONTHLY_PROFITS_SUBCOLLECTION"

        try {
            var rejectedValidationCount = 0
            val candidates = summaries
                .sortedBy { it.monthKey }
                .mapNotNull { summary ->
                    val candidate = FinanceCloudValidator.validateMonthlySummaryForUpload(summary, cleanUid)
                    if (candidate == null) {
                        rejectedValidationCount++
                        null
                    } else {
                        candidate
                    }
                }

            if (candidates.isEmpty()) {
                return@withContext Result.success(
                    FinanceSyncBatchReport(rejectedValidationCount = rejectedValidationCount)
                )
            }

            // Read existing docs sequentially first to know created vs existing and prevent clobbers
            val existingRawDocs = fetchAllRawMonthlySnapshots(cleanUid)
            val existingDocIds = existingRawDocs.map { it.id }.toSet()
            val validExistingByMonth = validateSnapshots(existingRawDocs, cleanUid).cloudRecordsByMonth

            val pendingOps = mutableListOf<PendingWriteOp>()
            var skippedUnchanged = 0
            var skippedZeroClobber = 0

            for (candidate in candidates) {
                val existsInCloud = existingDocIds.contains(candidate.monthKey)
                val existingValid = validExistingByMonth[candidate.monthKey]
                val existingHasData = existingValid != null && (
                    existingValid.totalRevenue > 0.0 ||
                        existingValid.soldCardsCount > 0L ||
                        existingValid.totalCardsCount > 0L ||
                        existingValid.totalBytesConsumed > 0L
                    )

                if (!candidate.hasMeaningfulData()) {
                    if (existingHasData || !existsInCloud) {
                        if (existingHasData) skippedZeroClobber++ else skippedUnchanged++
                        continue
                    }
                }

                if (existingValid != null && candidate.isEquivalentToCloud(existingValid)) {
                    skippedUnchanged++
                    continue
                }

                if (existsInCloud) {
                    pendingOps.add(PendingWriteOp.Update(candidate))
                } else {
                    pendingOps.add(PendingWriteOp.Create(candidate))
                }
            }

            var createdCount = 0
            var updatedCount = 0
            val colRef = monthlyProfitsCollection(cleanUid)

            for (chunk in pendingOps.chunked(WRITE_BATCH_CHUNK_SIZE)) {
                val batch = db.batch()
                for (op in chunk) {
                    when (op) {
                        is PendingWriteOp.Create -> {
                            batch.set(colRef.document(op.candidate.monthKey), op.candidate.toCreateMap())
                            createdCount++
                        }
                        is PendingWriteOp.Update -> {
                            batch.update(colRef.document(op.candidate.monthKey), op.candidate.toUpdateMap())
                            updatedCount++
                        }
                    }
                }
                batch.commit().await()
            }

            Result.success(
                FinanceSyncBatchReport(
                    createdCount = createdCount,
                    updatedCount = updatedCount,
                    skippedUnchangedCount = skippedUnchanged,
                    skippedZeroClobberCount = skippedZeroClobber,
                    rejectedValidationCount = rejectedValidationCount
                )
            )
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, collectionPath)
            Result.failure(e)
        }
    }

    suspend fun saveValidatedPackagePricing(
        profileName: String,
        costPrice: Double,
        salePrice: Double
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanName = profileName.trim()
        if (cleanName.isEmpty() || cleanName.length > 120) {
            return@withContext Result.failure(IllegalArgumentException("Invalid profileName '$profileName'"))
        }
        if (!costPrice.isFinite() || costPrice < 0.0 || costPrice > 1_000_000_000.0 ||
            !salePrice.isFinite() || salePrice < 0.0 || salePrice > 1_000_000_000.0
        ) {
            return@withContext Result.failure(IllegalArgumentException("Pricing values must be finite non-negative numbers"))
        }

        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        val docId = FinanceCloudValidator.sanitizeFirestoreDocId(cleanName)
        val docPath = "$USERS_COLLECTION/$uid/$PACKAGE_PRICING_SUBCOLLECTION/$docId"
        return@withContext try {
            val payload = mapOf(
                "userId" to uid,
                "profileName" to cleanName,
                "costPrice" to costPrice,
                "salePrice" to salePrice,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            packagePricingCollection(uid)
                .document(docId)
                .set(payload, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun fetchPackagePricings(userId: String): Result<List<PackagePricingEntity>> = withContext(Dispatchers.IO) {
        val cleanUid = userId.trim()
        if (cleanUid.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("userId must not be blank"))
        }
        val path = "$USERS_COLLECTION/$cleanUid/$PACKAGE_PRICING_SUBCOLLECTION"
        return@withContext try {
            val snap = packagePricingCollection(cleanUid)
                .orderBy("profileName", Query.Direction.ASCENDING)
                .get()
                .await()

            val list = snap.documents.mapNotNull { doc ->
                val name = doc.getString("profileName")?.trim().orEmpty()
                val cost = doc.getDouble("costPrice")
                val sale = doc.getDouble("salePrice")
                if (name.isEmpty() ||
                    cost == null || !cost.isFinite() || cost < 0.0 ||
                    sale == null || !sale.isFinite() || sale < 0.0
                ) {
                    null
                } else {
                    val updatedMs = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?.toDate()?.time ?: System.currentTimeMillis()
                    PackagePricingEntity(
                        profileName = name,
                        costPrice = cost,
                        salePrice = sale,
                        updatedAt = updatedMs
                    )
                }
            }
            Result.success(list)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    private suspend fun fetchAllRawMonthlySnapshots(userId: String): List<DocumentSnapshot> {
        val allDocs = mutableListOf<DocumentSnapshot>()
        var lastSnapshot: DocumentSnapshot? = null
        do {
            var query: Query = monthlyProfitsCollection(userId)
                .orderBy("yearMonthSerial", Query.Direction.ASCENDING)
                .limit(DEFAULT_PAGE_SIZE)
            if (lastSnapshot != null) {
                query = query.startAfter(lastSnapshot)
            }
            val snap = query.get().await()
            val pageDocs = snap.documents
            allDocs.addAll(pageDocs)
            lastSnapshot = pageDocs.lastOrNull()
        } while (pageDocs.size >= DEFAULT_PAGE_SIZE.toInt() && lastSnapshot != null)
        return allDocs
    }

    private fun validateSnapshots(
        docs: List<DocumentSnapshot>,
        expectedUserId: String
    ): ValidatedMonthlyFetchResult {
        val entities = mutableListOf<MonthlyProfitRecordEntity>()
        val breakdowns = linkedMapOf<String, List<MonthlyPackageSales>>()
        val records = linkedMapOf<String, MonthlyProfitCloudRecord>()
        val rejectionReasons = mutableListOf<String>()

        for (doc in docs) {
            when (val res = FinanceCloudValidator.validateCloudDocument(doc, expectedUserId)) {
                is MonthlyProfitValidationResult.Valid -> {
                    entities.add(res.entity)
                    if (res.breakdown.isNotEmpty()) {
                        breakdowns[res.entity.monthKey] = res.breakdown
                    }
                    records[res.entity.monthKey] = res.record
                }
                is MonthlyProfitValidationResult.Invalid -> {
                    rejectionReasons.add("${res.docId}: ${res.reason}")
                }
            }
        }

        val sortedEntities = entities.sortedWith(
            compareBy<MonthlyProfitRecordEntity> { it.year }.thenBy { it.month }
        )

        return ValidatedMonthlyFetchResult(
            entities = sortedEntities,
            breakdownsByMonth = breakdowns,
            cloudRecordsByMonth = records,
            rejectedDocsCount = rejectionReasons.size,
            rejectionReasons = rejectionReasons
        )
    }

    private sealed class PendingWriteOp {
        data class Create(val candidate: MonthlyProfitUploadCandidate) : PendingWriteOp()
        data class Update(val candidate: MonthlyProfitUploadCandidate) : PendingWriteOp()
    }

    companion object {
        private const val TAG = "FinanceFirestoreRepo"
        const val USERS_COLLECTION = "users"
        const val MONTHLY_PROFITS_SUBCOLLECTION = "monthlyProfits"
        const val PACKAGE_PRICING_SUBCOLLECTION = "packagePricing"
        const val DEFAULT_PAGE_SIZE = 24L
        const val WRITE_BATCH_CHUNK_SIZE = 400
    }
}
