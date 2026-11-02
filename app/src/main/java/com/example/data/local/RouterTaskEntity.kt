package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.HotspotCard
import org.json.JSONObject

@Entity(tableName = "router_task_queue")
data class RouterTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskType: String, // TYPE_EDIT_CARD, TYPE_DELETE_CARD, TYPE_TOGGLE_CARD, TYPE_REACTIVATE_CARD
    val cardId: String,
    val cardName: String,
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val maxRetries: Int = 8,
    val nextAttemptTimestamp: Long = System.currentTimeMillis(),
    val status: String = STATUS_PENDING, // PENDING, PROCESSING, FAILED, COMPLETED
    val lastError: String? = null
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_PROCESSING = "PROCESSING"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_COMPLETED = "COMPLETED"

        const val TYPE_EDIT_CARD = "EDIT_CARD"
        const val TYPE_DELETE_CARD = "DELETE_CARD"
        const val TYPE_TOGGLE_CARD = "TOGGLE_CARD"
        const val TYPE_REACTIVATE_CARD = "REACTIVATE_CARD"

        /**
         * Calculates exponential backoff delay based on retryCount:
         * Attempt 0 -> 2 seconds
         * Attempt 1 -> 4 seconds
         * Attempt 2 -> 8 seconds
         * Attempt 3 -> 16 seconds
         * Attempt 4 -> 32 seconds
         * Attempt 5+ -> max 60 seconds
         */
        fun calculateNextAttempt(retryCount: Int, baseDelayMs: Long = 2000L, maxDelayMs: Long = 60_000L): Long {
            val shift = retryCount.coerceIn(0, 6)
            val delay = (baseDelayMs * (1L shl shift)).coerceAtMost(maxDelayMs)
            return System.currentTimeMillis() + delay
        }

        fun createEditTask(card: HotspotCard): RouterTaskEntity {
            val json = JSONObject().apply {
                put("id", card.id)
                put("name", card.name)
                put("password", card.password)
                put("profile", card.profile)
                put("comment", card.comment)
                put("disabled", card.disabled)
                put("limitUptime", card.limitUptime)
                put("limitBytesTotal", card.limitBytesTotal)
                put("price", card.price)
                put("cardType", card.cardType)
            }
            return RouterTaskEntity(
                taskType = TYPE_EDIT_CARD,
                cardId = card.id,
                cardName = card.name,
                payloadJson = json.toString(),
                status = STATUS_PENDING
            )
        }

        fun createDeleteTask(cardId: String, cardName: String): RouterTaskEntity {
            val json = JSONObject().apply {
                put("id", cardId)
                put("name", cardName)
            }
            return RouterTaskEntity(
                taskType = TYPE_DELETE_CARD,
                cardId = cardId,
                cardName = cardName,
                payloadJson = json.toString(),
                status = STATUS_PENDING
            )
        }

        fun createToggleTask(cardId: String, cardName: String, targetDisabled: Boolean): RouterTaskEntity {
            val json = JSONObject().apply {
                put("id", cardId)
                put("name", cardName)
                put("disabled", targetDisabled)
            }
            return RouterTaskEntity(
                taskType = TYPE_TOGGLE_CARD,
                cardId = cardId,
                cardName = cardName,
                payloadJson = json.toString(),
                status = STATUS_PENDING
            )
        }

        fun createReactivateTask(card: HotspotCard): RouterTaskEntity {
            val json = JSONObject().apply {
                put("id", card.id)
                put("name", card.name)
                put("password", card.password)
                put("profile", card.profile)
                put("comment", card.comment)
                put("disabled", false)
                put("limitUptime", card.limitUptime)
                put("limitBytesTotal", card.limitBytesTotal)
            }
            return RouterTaskEntity(
                taskType = TYPE_REACTIVATE_CARD,
                cardId = card.id,
                cardName = card.name,
                payloadJson = json.toString(),
                status = STATUS_PENDING
            )
        }

        fun parseCardFromPayload(payloadJson: String): HotspotCard? {
            return try {
                val json = JSONObject(payloadJson)
                HotspotCard(
                    id = json.optString("id", ""),
                    name = json.optString("name", ""),
                    password = json.optString("password", ""),
                    profile = json.optString("profile", "default"),
                    comment = json.optString("comment", ""),
                    disabled = json.optBoolean("disabled", false),
                    limitUptime = json.optString("limitUptime", ""),
                    limitBytesTotal = json.optLong("limitBytesTotal", 0L),
                    price = json.optString("price", ""),
                    cardType = json.optString("cardType", "hotspot")
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
