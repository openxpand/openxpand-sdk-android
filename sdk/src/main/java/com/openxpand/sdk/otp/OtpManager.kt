package com.openxpand.sdk.otp

import android.content.Context
import com.openxpand.sdk.AuthResult
import com.openxpand.sdk.OpenXpandConfig
import com.openxpand.sdk.internal.optionalRefreshToken
import com.openxpand.sdk.internal.SdkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject

internal class OtpManager(
    private val context: Context,
    private val config: OpenXpandConfig
) {

    private val client = SdkHttpClient.create()
    private val smsRetriever = SmsRetrieverHelper(context)

    suspend fun authenticate(phoneNumber: String): AuthResult = coroutineScope {
        if (config.otpSendEndpoint.isBlank() || config.otpValidateEndpoint.isBlank()) {
            return@coroutineScope AuthResult.Error("OTP endpoints not configured")
        }

        // Start listening for SMS before requesting OTP
        val smsDeferred = async { smsRetriever.waitForOtpCode() }

        // Request OTP to be sent
        val sendResult = sendOtp(phoneNumber)
        if (sendResult != null) {
            smsDeferred.cancel()
            return@coroutineScope AuthResult.Error(sendResult)
        }

        // Wait for auto-read OTP code
        val otpCode = try {
            smsDeferred.await()
        } catch (e: Exception) {
            return@coroutineScope AuthResult.Error("Failed to read OTP: ${e.message}", e)
        }

        // Validate OTP and get token
        validateOtp(phoneNumber, otpCode)
    }

    private suspend fun sendOtp(phoneNumber: String): String? = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("phone_number", phoneNumber)
            .add("client_id", config.clientId)
            .build()

        val request = Request.Builder()
            .url(config.otpSendEndpoint)
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            "Failed to send OTP: HTTP ${response.code}"
        } else {
            null
        }
    }

    private suspend fun validateOtp(phoneNumber: String, otpCode: String): AuthResult =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder()
                .add("phone_number", phoneNumber)
                .add("otp_code", otpCode)
                .add("client_id", config.clientId)
                .add("redirect_uri", config.redirectUri)
                .build()

            val request = Request.Builder()
                .url(config.otpValidateEndpoint)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
                ?: return@withContext AuthResult.Error("Empty response from OTP validation")

            if (!response.isSuccessful) {
                return@withContext AuthResult.Error(
                    "OTP validation failed with HTTP ${response.code}: $responseBody"
                )
            }

            val json = JSONObject(responseBody)

            AuthResult.Success(
                accessToken = json.getString("access_token"),
                tokenType = json.optString("token_type", "Bearer"),
                expiresIn = json.optLong("expires_in", 0),
                refreshToken = json.optionalRefreshToken()
            )
        }
}
