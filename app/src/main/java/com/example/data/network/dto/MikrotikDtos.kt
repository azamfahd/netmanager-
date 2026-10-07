package com.example.data.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data Transfer Objects (DTOs) for MikroTik RouterOS v7 REST API endpoints.
 * Provides strongly-typed JSON mappings for Retrofit + Moshi.
 */

@JsonClass(generateAdapter = true)
data class MikrotikCardDto(
    @Json(name = ".id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "password") val password: String? = null,
    @Json(name = "profile") val profile: String? = null,
    @Json(name = "comment") val comment: String? = null,
    @Json(name = "limit-uptime") val limitUptime: String? = null,
    @Json(name = "limit-bytes-total") val limitBytesTotal: String? = null,
    @Json(name = "uptime") val uptime: String? = null,
    @Json(name = "bytes-in") val bytesIn: String? = null,
    @Json(name = "bytes-out") val bytesOut: String? = null,
    @Json(name = "disabled") val disabled: String? = null
)

@JsonClass(generateAdapter = true)
data class MikrotikActiveUserDto(
    @Json(name = ".id") val id: String? = null,
    @Json(name = "user") val user: String? = null,
    @Json(name = "address") val address: String? = null,
    @Json(name = "mac-address") val macAddress: String? = null,
    @Json(name = "uptime") val uptime: String? = null,
    @Json(name = "bytes-in") val bytesIn: String? = null,
    @Json(name = "bytes-out") val bytesOut: String? = null,
    @Json(name = "session-time-left") val sessionTimeLeft: String? = null,
    @Json(name = "comment") val comment: String? = null,
    @Json(name = "server") val server: String? = null
)

@JsonClass(generateAdapter = true)
data class MikrotikResourceDto(
    @Json(name = "uptime") val uptime: String? = null,
    @Json(name = "version") val version: String? = null,
    @Json(name = "board-name") val boardName: String? = null,
    @Json(name = "cpu-load") val cpuLoad: String? = null,
    @Json(name = "free-memory") val freeMemory: String? = null,
    @Json(name = "total-memory") val totalMemory: String? = null,
    @Json(name = "cpu-frequency") val cpuFrequency: String? = null
)

@JsonClass(generateAdapter = true)
data class MikrotikHealthDto(
    @Json(name = "name") val name: String? = null,
    @Json(name = "value") val value: String? = null,
    @Json(name = "type") val type: String? = null
)

@JsonClass(generateAdapter = true)
data class MikrotikLogDto(
    @Json(name = ".id") val id: String? = null,
    @Json(name = "time") val time: String? = null,
    @Json(name = "topics") val topics: String? = null,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class MikrotikIdentityDto(
    @Json(name = "name") val name: String? = null
)
