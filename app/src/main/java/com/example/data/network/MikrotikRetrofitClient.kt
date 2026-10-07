package com.example.data.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object MikrotikRetrofitClient {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    fun buildOkHttpClient(username: String? = null, password: String? = null): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        builder.addInterceptor(logging)

        if (!username.isNullOrEmpty()) {
            val authInterceptor = Interceptor { chain ->
                val credential = Credentials.basic(username, password.orEmpty())
                val request = chain.request().newBuilder()
                    .header("Authorization", credential)
                    .build()
                chain.proceed(request)
            }
            builder.addInterceptor(authInterceptor)
        }

        return builder.build()
    }

    fun createService(baseUrl: String, username: String? = null, password: String? = null): MikrotikRestApi {
        val cleanUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val client = buildOkHttpClient(username, password)
        return Retrofit.Builder()
            .baseUrl(cleanUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
            .build()
            .create(MikrotikRestApi::class.java)
    }
}
