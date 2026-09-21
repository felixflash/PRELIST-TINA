package com.example.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class PaystackInitRequest(
    val email: String,
    val amount: Long // In subunits: e.g. 100 GHS or USD = 10000
)

data class PaystackInitResponse(
    @Json(name = "accessCode") val accessCode: String? = null,
    @Json(name = "error") val error: String? = null
)

interface PaystackApiService {
    @POST("api/initialize")
    suspend fun initializeTransaction(@Body request: PaystackInitRequest): Response<PaystackInitResponse>
}

object PaystackClient {
    private const val BASE_URL = "https://paystack-vercel.vercel.app/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val apiService: PaystackApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(PaystackApiService::class.java)
    }
}
