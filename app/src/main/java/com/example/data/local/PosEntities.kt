package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pos_agents")
data class PosAgentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phone: String = "",
    val location: String = "",
    val discountPercent: Double = 15.0, // Default discount percentage (e.g. 15%)
    val createdAt: Long = System.currentTimeMillis(),
    val notes: String = ""
)

@Entity(tableName = "pos_card_assignments")
data class PosCardAssignmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val agentId: Long,
    val agentName: String,
    val cardName: String,
    val batchId: String = "",
    val profileName: String = "",
    val priceAmount: Double = 200.0,
    val discountPercent: Double = 15.0,
    val assignedAt: Long = System.currentTimeMillis(),
    val isSold: Boolean = false,
    val soldAt: Long = 0L
)

@Entity(tableName = "pos_payments")
data class PosPaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val agentId: Long,
    val agentName: String,
    val amount: Double,
    val paymentDate: Long = System.currentTimeMillis(),
    val paymentNote: String = "دفعة نقدية"
)

data class PosAgentSummary(
    val agent: PosAgentEntity,
    val totalAssignedCards: Int = 0,
    val soldCardsCount: Int = 0,
    val remainingCardsCount: Int = 0,
    val totalSalesVolume: Double = 0.0,
    val totalAgentCommission: Double = 0.0,
    val totalDueToOwner: Double = 0.0,
    val totalPaidByAgent: Double = 0.0,
    val netRemainingDebt: Double = 0.0,
    val assignedCards: List<PosCardAssignmentEntity> = emptyList(),
    val payments: List<PosPaymentEntity> = emptyList()
)

@Entity(tableName = "package_pricing")
data class PackagePricingEntity(
    @PrimaryKey
    val profileName: String,
    val costPrice: Double,
    val salePrice: Double,
    val updatedAt: Long = System.currentTimeMillis()
)
