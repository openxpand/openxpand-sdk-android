package com.openxpand.sdk.silent

import android.net.Uri
import com.openxpand.sdk.OpenXpandConfig
import com.openxpand.sdk.internal.SdkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

internal class SilentLoginManager(private val config: OpenXpandConfig) {

    private val client = SdkHttpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun authorize(codeChallenge: String): String = withContext(Dispatchers.IO) {
        val url = config.authEndpoint.toHttpUrl().newBuilder()
            .addQueryParameter("response_type", "code")
            .addQueryParameter("client_id", config.clientId)
            .addQueryParameter("redirect_uri", config.redirectUri)
            .addQueryParameter("scope", "openid dpv:FraudPreventionAndDetection#number-verification")
            .addQueryParameter("code_challenge", codeChallenge)
            .addQueryParameter("code_challenge_method", "S256")
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = client.newCall(request).execute()

        val statusCode = response.code
        if (statusCode !in 300..399) {
            throw SilentLoginException(
                "Expected redirect from auth server, got HTTP $statusCode"
            )
        }

        val location = response.header("Location")
            ?: throw SilentLoginException("No Location header in redirect response")

        val uri = Uri.parse(location)
        val code = uri.getQueryParameter("code")
            ?: throw SilentLoginException(
                "No authorization code in redirect URL. " +
                    "Error: ${uri.getQueryParameter("error") ?: "unknown"}"
            )

        code
    }
}

class SilentLoginException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
