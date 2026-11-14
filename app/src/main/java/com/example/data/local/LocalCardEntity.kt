package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.HotspotCard

@Entity(
    tableName = "local_cards",
    indices = [
        Index(value = ["createdTimestamp"]),
        Index(value = ["profile"]),
        Index(value = ["disabled"]),
        Index(value = ["sourceSystem"]),
        Index(value = ["cardId"])
    ]
)
data class LocalCardEntity(
    @PrimaryKey
    val name: String,
    val cardId: String,
    val password: String = "",
    val profile: String = "",
    val uptime: String = "0s",
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val limitUptime: String = "",
    val limitBytesTotal: Long = 0L,
    val comment: String = "",
    val disabled: Boolean = false,
    val price: String = "",
    val cardType: String = "",
    val createdTimestamp: Long = 0L,
    val firstSeenTimestamp: Long = System.currentTimeMillis(),
    val lastSyncTimestamp: Long = System.currentTimeMillis(),
    val sourceSystem: String = "router"
) {
    fun toHotspotCard(): HotspotCard {
        return HotspotCard(
            id = cardId,
            name = name,
            password = password,
            profile = profile,
            uptime = uptime,
            bytesIn = bytesIn,
            bytesOut = bytesOut,
            limitUptime = limitUptime,
            limitBytesTotal = limitBytesTotal,
            comment = comment,
            disabled = disabled,
            price = price,
            cardType = cardType,
            createdAtHint = createdTimestamp
        )
    }

    companion object {
        fun fromHotspotCard(
            card: HotspotCard,
            existingCreatedTs: Long? = null,
            source: String = "router"
        ): LocalCardEntity {
            val now = System.currentTimeMillis()
            val resolvedCreated = existingCreatedTs?.takeIf { it > 0L } ?: now
            return LocalCardEntity(
                name = card.name,
                cardId = card.id,
                password = card.password,
                profile = card.profile,
                uptime = card.uptime,
                bytesIn = card.bytesIn,
                bytesOut = card.bytesOut,
                limitUptime = card.limitUptime,
                limitBytesTotal = card.limitBytesTotal,
                comment = card.comment,
                disabled = card.disabled,
                price = card.price,
                cardType = card.cardType,
                createdTimestamp = resolvedCreated,
                firstSeenTimestamp = resolvedCreated,
                lastSyncTimestamp = now,
                sourceSystem = source
            )
        }
    }
}
