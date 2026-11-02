package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.HotspotProfile

@Entity(tableName = "local_profiles")
data class LocalProfileEntity(
    @PrimaryKey
    val name: String,
    val profileId: String,
    val rateLimit: String = "",
    val sharedUsers: Int = 1,
    val sessionTimeout: String = "",
    val keepaliveTimeout: String = "",
    val price: String = "",
    val uploadLimit: String = "",
    val downloadLimit: String = "",
    val validity: String = "",
    val dataLimit: String = "",
    val source: String = "hotspot",
    val comment: String = "",
    val priceNumber: Double = 0.0,
    val lastSyncTimestamp: Long = System.currentTimeMillis()
) {
    fun toHotspotProfile(): HotspotProfile {
        return HotspotProfile(
            id = profileId,
            name = name,
            rateLimit = rateLimit,
            sharedUsers = sharedUsers,
            sessionTimeout = sessionTimeout,
            keepaliveTimeout = keepaliveTimeout,
            price = price,
            uploadLimit = uploadLimit,
            downloadLimit = downloadLimit,
            validity = validity,
            dataLimit = dataLimit,
            source = source,
            comment = comment,
            priceNumber = priceNumber
        )
    }

    companion object {
        fun fromHotspotProfile(profile: HotspotProfile): LocalProfileEntity {
            return LocalProfileEntity(
                name = profile.name,
                profileId = profile.id,
                rateLimit = profile.rateLimit,
                sharedUsers = profile.sharedUsers,
                sessionTimeout = profile.sessionTimeout,
                keepaliveTimeout = profile.keepaliveTimeout,
                price = profile.price,
                uploadLimit = profile.uploadLimit,
                downloadLimit = profile.downloadLimit,
                validity = profile.validity,
                dataLimit = profile.dataLimit,
                source = profile.source,
                comment = profile.comment,
                priceNumber = profile.priceNumber,
                lastSyncTimestamp = System.currentTimeMillis()
            )
        }
    }
}
