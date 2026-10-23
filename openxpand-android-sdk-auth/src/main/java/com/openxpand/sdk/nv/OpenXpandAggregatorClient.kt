package com.openxpand.sdk.nv

import com.openxpand.sdk.OpenXpandConfig
import com.openxpand.sdk.OperatorTokenCredentialOffer
import com.openxpand.sdk.entitlement.LabAppAssertionSigner
import com.openxpand.sdk.entitlement.LabOperatorTokenDefaults
import com.openxpand.sdk.internal.SdkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Keycloak OpenID4VP aggregator client (`POST /credentials`, `POST /token`).
 */
class OpenXpandAggregatorClient(
    private val config: OpenXpandConfig
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val http = SdkHttpClient.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun requestCredentials(
        scope: String,
        carrierIds: List<Int>,
        numberVerificationScope: String? = null,
        nonce: String = UUID.randomUUID().toString().replace("-", ""),
        lang: String = "es"
    ): CredentialsResult = withContext(Dispatchers.IO) {
        if (config.clientSecret.isBlank()) {
            return@withContext CredentialsResult.Error("clientSecret is required for OpenID4VP credentials")
        }
        val body = JSONObject()
            .put("scope", scope)
            .put("lang", lang)
            .put("nonce", nonce)
            .put("carrier_ids", org.json.JSONArray(carrierIds))
        if (!numberVerificationScope.isNullOrBlank()) {
            body.put("number_verification_scope", numberVerificationScope)
        }
        val url = "${config.openId4VpBaseUrl.trimEnd('/')}/credentials"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(config.clientId, config.clientSecret))
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext CredentialsResult.Error(
                    "POST /credentials failed with HTTP ${response.code}: ${text.take(200)}"
                )
            }
            try {
                val json = JSONObject(text)
                CredentialsResult.Success(
                    offer = OperatorTokenCredentialOffer.fromOpenId4VpCredentialsJson(json),
                    nonce = nonce
                )
            } catch (e: Exception) {
                CredentialsResult.Error("Invalid /credentials payload: ${e.message}", e)
            }
        }
    }

    suspend fun exchangeVpToken(
        sdJwt: String,
        scope: String
    ): TokenExchangeResult = withContext(Dispatchers.IO) {
        val assertion = LabAppAssertionSigner.sign(
            clientId = config.clientId,
            tokenUrl = config.openId4VpTokenUrl,
            scope = scope,
            sdJwt = sdJwt
        )
        val form = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            .add("assertion", assertion)
            .build()
        val request = Request.Builder()
            .url(config.openId4VpTokenUrl)
            .post(form)
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext TokenExchangeResult.Error(
                    "POST /token failed with HTTP ${response.code}: ${text.take(200)}"
                )
            }
            try {
                val json = JSONObject(text)
                val accessToken = json.optString("access_token")
                if (accessToken.isNullOrEmpty()) {
                    return@withContext TokenExchangeResult.Error("Token response missing access_token")
                }
                TokenExchangeResult.Success(
                    accessToken = accessToken,
                    expiresIn = json.optLong("expires_in", 300L),
                    scope = json.optString("scope").takeIf { it.isNotEmpty() }
                )
            } catch (e: Exception) {
                TokenExchangeResult.Error("Invalid token response: ${e.message}", e)
            }
        }
    }

    enum class NvScope(val wireValue: String) {
        Verify(LabOperatorTokenDefaults.NV_SCOPE_VERIFY),
        DevicePhoneNumber(LabOperatorTokenDefaults.NV_SCOPE_DPN)
    }

    sealed class CredentialsResult {
        data class Success(val offer: OperatorTokenCredentialOffer, val nonce: String) : CredentialsResult()
        data class Error(val message: String, val cause: Throwable? = null) : CredentialsResult()
    }

    sealed class TokenExchangeResult {
        data class Success(
            val accessToken: String,
            val expiresIn: Long,
            val scope: String? = null
        ) : TokenExchangeResult()

        data class Error(val message: String, val cause: Throwable? = null) : TokenExchangeResult()
    }
}
