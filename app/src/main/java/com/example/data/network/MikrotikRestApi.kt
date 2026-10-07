package com.example.data.network

import com.example.data.network.dto.MikrotikActiveUserDto
import com.example.data.network.dto.MikrotikCardDto
import com.example.data.network.dto.MikrotikHealthDto
import com.example.data.network.dto.MikrotikIdentityDto
import com.example.data.network.dto.MikrotikLogDto
import com.example.data.network.dto.MikrotikResourceDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit Service Interface for MikroTik RouterOS v7 REST API.
 * Provides strongly-typed HTTP endpoints with DTOs for querying and controlling the router.
 */
interface MikrotikRestApi {

    @GET("rest/system/identity")
    suspend fun getSystemIdentity(
        @Header("Authorization") authHeader: String? = null
    ): Response<MikrotikIdentityDto>

    @GET("rest/system/resource")
    suspend fun getSystemResource(
        @Header("Authorization") authHeader: String? = null
    ): Response<MikrotikResourceDto>

    @GET("rest/system/routerboard")
    suspend fun getRouterBoardInfo(
        @Header("Authorization") authHeader: String? = null
    ): Response<Map<String, Any>>

    @GET("rest/system/health")
    suspend fun getSystemHealth(
        @Header("Authorization") authHeader: String? = null
    ): Response<List<MikrotikHealthDto>>

    @GET("rest/log")
    suspend fun getSystemLogs(
        @Header("Authorization") authHeader: String? = null
    ): Response<List<MikrotikLogDto>>

    @GET("rest/ip/hotspot/active")
    suspend fun getActiveHotspotUsers(
        @Header("Authorization") authHeader: String? = null
    ): Response<List<MikrotikActiveUserDto>>

    @GET("rest/ip/hotspot/user")
    suspend fun getHotspotCards(
        @Header("Authorization") authHeader: String? = null
    ): Response<List<MikrotikCardDto>>

    @GET("rest/user-manager/user")
    suspend fun getUserManagerCards(
        @Header("Authorization") authHeader: String? = null
    ): Response<List<MikrotikCardDto>>

    @GET("rest/ip/hotspot/user/profile")
    suspend fun getHotspotProfiles(
        @Header("Authorization") authHeader: String? = null
    ): Response<List<Map<String, Any>>>

    @GET("rest/ip/hotspot/ip-binding")
    suspend fun getIpBindings(
        @Header("Authorization") authHeader: String? = null
    ): Response<List<Map<String, Any>>>

    @GET("rest/ip/dhcp-server/lease")
    suspend fun getDhcpLeases(
        @Header("Authorization") authHeader: String? = null
    ): Response<List<Map<String, Any>>>

    @GET("rest/ip/hotspot/host")
    suspend fun getHotspotHosts(
        @Header("Authorization") authHeader: String? = null
    ): Response<List<Map<String, Any>>>

    @GET("rest/interface/wireless/registration-table")
    suspend fun getWirelessRegistrations(
        @Header("Authorization") authHeader: String? = null
    ): Response<List<Map<String, Any>>>

    @GET("rest/interface/wireless")
    suspend fun getWirelessInterfaces(
        @Header("Authorization") authHeader: String? = null
    ): Response<List<Map<String, Any>>>

    @GET("rest/ip/neighbor")
    suspend fun getDiscoveredNeighbors(
        @Header("Authorization") authHeader: String? = null
    ): Response<List<Map<String, Any>>>

    @GET("rest/ip/pool")
    suspend fun getIpPools(
        @Header("Authorization") authHeader: String? = null
    ): Response<List<Map<String, Any>>>

    @GET("rest/ip/hotspot")
    suspend fun getHotspotServers(
        @Header("Authorization") authHeader: String? = null
    ): Response<List<Map<String, Any>>>

    @POST("rest/ip/hotspot/user/add")
    suspend fun addHotspotUser(
        @Header("Authorization") authHeader: String? = null,
        @Body body: Map<String, String>
    ): Response<Map<String, Any>>

    @POST("rest/ip/hotspot/active/remove")
    suspend fun removeActiveUser(
        @Header("Authorization") authHeader: String? = null,
        @Body body: Map<String, String>
    ): Response<Map<String, Any>>

    @POST("rest/ip/hotspot/user/remove")
    suspend fun removeHotspotUser(
        @Header("Authorization") authHeader: String? = null,
        @Body body: Map<String, String>
    ): Response<Map<String, Any>>

    @POST("rest/system/reboot")
    suspend fun rebootRouter(
        @Header("Authorization") authHeader: String? = null
    ): Response<Map<String, Any>>
}
