package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM saved_routers ORDER BY lastConnectedTime DESC")
    fun getAllSavedRouters(): Flow<List<SavedRouterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedRouter(router: SavedRouterEntity): Long

    @Query("DELETE FROM saved_routers WHERE id = :id")
    suspend fun deleteSavedRouter(id: Long)

    @Query("SELECT * FROM batch_history ORDER BY createdAt DESC")
    fun getAllBatches(): Flow<List<BatchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(batch: BatchHistoryEntity)

    @Query("DELETE FROM batch_history WHERE batchId = :batchId")
    suspend fun deleteBatch(batchId: String)

    // ==========================================
    // POS Agents & Resellers
    // ==========================================
    @Query("SELECT * FROM pos_agents ORDER BY name ASC")
    fun getAllPosAgents(): Flow<List<PosAgentEntity>>

    @Query("SELECT * FROM pos_agents ORDER BY name ASC")
    suspend fun getAllPosAgentsSync(): List<PosAgentEntity>

    @Query("SELECT * FROM pos_agents WHERE id = :agentId")
    suspend fun getPosAgentById(agentId: Long): PosAgentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosAgent(agent: PosAgentEntity): Long

    @Query("DELETE FROM pos_agents WHERE id = :agentId")
    suspend fun deletePosAgent(agentId: Long)

    // ==========================================
    // POS Card Assignments
    // ==========================================
    @Query("SELECT * FROM pos_card_assignments ORDER BY assignedAt DESC")
    fun getAllCardAssignments(): Flow<List<PosCardAssignmentEntity>>

    @Query("SELECT * FROM pos_card_assignments WHERE agentId = :agentId ORDER BY assignedAt DESC")
    fun getAssignmentsByAgent(agentId: Long): Flow<List<PosCardAssignmentEntity>>

    @Query("SELECT * FROM pos_card_assignments WHERE cardName = :cardName LIMIT 1")
    suspend fun getAssignmentByCardName(cardName: String): PosCardAssignmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCardAssignments(assignments: List<PosCardAssignmentEntity>)

    @Query("UPDATE pos_card_assignments SET isSold = 1, soldAt = :soldAt WHERE cardName = :cardName AND isSold = 0")
    suspend fun markCardAsSold(cardName: String, soldAt: Long)

    @Query("DELETE FROM pos_card_assignments WHERE id = :assignmentId")
    suspend fun deleteCardAssignment(assignmentId: Long)

    @Query("DELETE FROM pos_card_assignments WHERE agentId = :agentId")
    suspend fun deleteAssignmentsByAgent(agentId: Long)

    // ==========================================
    // POS Payments
    // ==========================================
    @Query("SELECT * FROM pos_payments ORDER BY paymentDate DESC")
    fun getAllPosPayments(): Flow<List<PosPaymentEntity>>

    @Query("SELECT * FROM pos_payments WHERE agentId = :agentId ORDER BY paymentDate DESC")
    fun getPaymentsByAgent(agentId: Long): Flow<List<PosPaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosPayment(payment: PosPaymentEntity): Long

    @Query("DELETE FROM pos_payments WHERE id = :paymentId")
    suspend fun deletePosPayment(paymentId: Long)

    // ==========================================
    // Package Pricing (Cost & Sale Price per Package)
    // ==========================================
    @Query("SELECT * FROM package_pricing")
    fun getAllPackagePricings(): Flow<List<PackagePricingEntity>>

    @Query("SELECT * FROM package_pricing")
    suspend fun getAllPackagePricingsSync(): List<PackagePricingEntity>

    @Query("SELECT * FROM package_pricing WHERE profileName = :profileName LIMIT 1")
    suspend fun getPricingForProfile(profileName: String): PackagePricingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackagePricing(pricing: PackagePricingEntity)

    @Query("DELETE FROM package_pricing WHERE profileName = :profileName")
    suspend fun deletePackagePricing(profileName: String)
}
