package com.openxpand.sdk.nv

import com.openxpand.sdk.OperatorTokenCredentialOffer
import com.openxpand.sdk.entitlement.LabOperatorTokenDefaults
import com.openxpand.sdk.internal.SdkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Calls the application backend (`site_backend`) device NV endpoints.
 */
class DeviceNvClient(
    private val backendBaseUrl: String,
    private val appCheckSecret: String = LabOperatorTokenDefaults.LAB_APP_CHECK_DEBUG_SECRET,
    private val playIntegrityToken: String = appCheckSecret
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val http = SdkHttpClient.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun initiate(
        op: NvOperation,
        nonce: String = UUID.randomUUID().toString().replace("-", ""),
        mccMnc: String = "72234",
        carrierIds: List<Int> = listOf(LabOperatorTokenDefaults.LAB_ANDROID_CARRIER_ID),
        lang: String = "es"
    ): InitiateResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("op", op.wireValue)
            .put("nonce", nonce)
            .put("mcc_mnc", mccMnc)
            .put("carrier_ids", org.json.JSONArray(carrierIds))
            .put("lang", lang)
        val request = Request.Builder()
            .url("${backendBaseUrl.trimEnd('/')}/api/device/nv-initiate")
            .header("X-Firebase-AppCheck", appCheckSecret)
            .header("X-Play-Integrity-Token", playIntegrityToken)
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext InitiateResult.Error(
                    "nv-initiate failed with HTTP ${response.code}: ${text.take(200)}"
                )
            }
            try {
                val json = JSONObject(text)
                InitiateResult.Success(
                    offer = OperatorTokenCredentialOffer.fromInitiateJson(json),
                    nonce = nonce
                )
            } catch (e: Exception) {
                InitiateResult.Error("nv-initiate returned an invalid payload: ${e.message}", e)
            }
        }
    }

    suspend fun exchange(
        sdJwt: String,
        op: NvOperation,
        caso: String? = null,
        phoneNumber: String? = null
    ): ExchangeResult = withContext(Dispatchers.IO) {
        if (op == NvOperation.Verify && phoneNumber.isNullOrBlank()) {
            return@withContext ExchangeResult.Error("phone_number is required for verify")
        }
        val body = JSONObject()
            .put("op", op.wireValue)
            .put("sd_jwt", sdJwt)
        caso?.let { body.put("caso", it) }
        phoneNumber?.let { body.put("phone_number", it) }
        val request = Request.Builder()
            .url("${backendBaseUrl.trimEnd('/')}/api/device/nv-exchange")
            .header("X-Firebase-AppCheck", appCheckSecret)
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext ExchangeResult.Error(
                    "nv-exchange failed with HTTP ${response.code}: ${text.take(200)}"
                )
            }
            try {
                ExchangeResult.Success(JSONObject(text))
            } catch (e: Exception) {
                ExchangeResult.Error("nv-exchange returned invalid JSON: ${e.message}", e)
            }
        }
    }

    enum class NvOperation(val wireValue: String) {
        Verify("verify"),
        DevicePhoneNumber("device-phone-number")
    }

    sealed class InitiateResult {
        data class Success(val offer: OperatorTokenCredentialOffer, val nonce: String) : InitiateResult()
        data class Error(val message: String, val cause: Throwable? = null) : InitiateResult()
    }

    sealed class ExchangeResult {
        data class Success(val body: JSONObject) : ExchangeResult()
        data class Error(val message: String, val cause: Throwable? = null) : ExchangeResult()
    }
}
