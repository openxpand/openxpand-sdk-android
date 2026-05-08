package com.openxpand.sdk.token

import com.openxpand.sdk.AuthResult
import com.openxpand.sdk.OpenXpandConfig
import com.openxpand.sdk.internal.SdkHttpClient
import com.openxpand.sdk.internal.optionalRefreshToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject

internal class TokenExchanger(private val config: OpenXpandConfig) {

    private val client = SdkHttpClient.newBuilder().build()

    suspend fun exchange(authCode: String, codeVerifier: String): AuthResult =
        withContext(Dispatchers.IO) {
            val formBody = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", config.clientId)
                .add("redirect_uri", config.redirectUri)
                .add("code", authCode)
                .add("code_verifier", codeVerifier)
                .apply { if (config.clientSecret.isNotEmpty()) add("client_secret", config.clientSecret) }
                .build()

            val request = Request.Builder()
                .url(config.tokenEndpoint)
                .post(formBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext AuthResult.Error(
                    "Token exchange failed: HTTP ${response.code}. $responseBody"
                )
            }

            try {
                val json = JSONObject(responseBody)
                AuthResult.Success(
                    accessToken = json.getString("access_token"),
                    tokenType = json.optString("token_type", "Bearer"),
                    expiresIn = json.optLong("expires_in", 0),
                    refreshToken = json.optionalRefreshToken()
                )
            } catch (e: Exception) {
                AuthResult.Error("Failed to parse token response: ${e.message}", e)
            }
        }
}
