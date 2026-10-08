package com.example.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class HubtelPaymentRequest(
    val totalAmount: Double,
    val description: String,
    val callbackUrl: String,
    val returnUrl: String,
    val cancellationUrl: String,
    val merchantTaskId: String
)

data class HubtelPaymentData(
    val checkoutUrl: String? = null,
    val checkoutId: String? = null
)

data class HubtelPaymentResponse(
    val status: String,
    val message: String? = null,
    val data: HubtelPaymentData? = null
)

interface HubtelApiService {
    @POST("v2/merchant/transactions/online-checkout/v3/initiate")
    suspend fun initiateCheckout(
        @Header("Authorization") authHeader: String,
        @Body request: HubtelPaymentRequest
    ): Response<HubtelPaymentResponse>
}

object HubtelClient {
    private const val BASE_URL = "https://api-proxy.hubtel.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val apiService: HubtelApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(HubtelApiService::class.java)
    }
}
