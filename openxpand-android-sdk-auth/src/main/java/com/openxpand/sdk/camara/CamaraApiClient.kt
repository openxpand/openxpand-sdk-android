package com.openxpand.sdk.camara

import com.openxpand.sdk.OpenXpandConfig
import com.openxpand.sdk.internal.SdkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal class CamaraApiClient(private val config: OpenXpandConfig) {

    private val client = SdkHttpClient.newBuilder().build()
    private val jsonMediaType = "application/json".toMediaType()

    suspend fun checkSimSwap(accessToken: String, phoneNumber: String): Boolean =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("phoneNumber", phoneNumber)
                .toString()
                .toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url(config.simSwapEndpoint)
                .post(body)
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                throw CamaraApiException("SIM swap check failed: HTTP ${response.code}. $responseBody")
            }

            JSONObject(responseBody).getBoolean("swapped")
        }

    suspend fun verifyNumber(accessToken: String, phoneNumber: String): Boolean =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("phoneNumber", phoneNumber)
                .toString()
                .toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url(config.numberVerificationEndpoint)
                .post(body)
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                throw CamaraApiException("Number verification failed: HTTP ${response.code}. $responseBody")
            }

            JSONObject(responseBody).getBoolean("devicePhoneNumberVerified")
        }
}

class CamaraApiException(message: String, cause: Throwable? = null) : Exception(message, cause)
