package com.openxpand.sdk.silent

import com.openxpand.sdk.OpenXpandConfig
import com.openxpand.sdk.internal.SdkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

internal class SilentLoginManager(private val config: OpenXpandConfig) {

    private val client = SdkHttpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val networkScopes =
        "openid dpv:FraudPreventionAndDetection#number-verification dpv:FraudPreventionAndDetection#sim-swap"

    /** CAMARA Number Verification 2.1 operation scopes, requested with the temporary token. */
    private val operatorTokenScopes =
        "$networkScopes number-verification:verify number-verification:device-phone-number:read"

    suspend fun authorize(codeChallenge: String): String =
        when (val result = authorizeForResult(codeChallenge, null)) {
            is AuthorizeCallResult.Code -> result.code
            is AuthorizeCallResult.ConsentRequired ->
                throw SilentLoginException("Authorization requires consent at ${result.url}")
        }

    suspend fun authorizeForResult(codeChallenge: String, loginHint: String?): AuthorizeCallResult =
        withContext(Dispatchers.IO) {
        val url = config.authEndpoint.toHttpUrl().newBuilder()
            .addQueryParameter("response_type", "code")
            .addQueryParameter("client_id", config.clientId)
            .addQueryParameter("redirect_uri", config.redirectUri)
            .addQueryParameter("scope", authorizeScopes(loginHint))
            .addQueryParameter("code_challenge", codeChallenge)
            .addQueryParameter("code_challenge_method", "S256")
            .apply {
                if (!loginHint.isNullOrEmpty()) {
                    addQueryParameter("login_hint", loginHint)
                }
            }
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

        val redirectUrl = resolveRedirect(response.request.url, location)
        val code = redirectUrl.queryParameter("code")
        if (!code.isNullOrEmpty()) {
            return@withContext AuthorizeCallResult.Code(code)
        }
        val error = redirectUrl.queryParameter("error")
        if (!error.isNullOrEmpty()) {
            throw SilentLoginException("No authorization code in redirect URL. Error: $error")
        }
        AuthorizeCallResult.ConsentRequired(
            url = redirectUrl.toString(),
            cookies = response.headers("Set-Cookie")
        )
    }

    private fun resolveRedirect(requestUrl: HttpUrl, location: String): HttpUrl {
        return location.toHttpUrlOrNull()
            ?: requestUrl.resolve(location)
            ?: throw SilentLoginException("Invalid redirect URL: $location")
    }

    private fun authorizeScopes(loginHint: String?): String {
        val configured = config.authorizeScopes.trim()
        if (configured.isNotEmpty()) {
            return configured
        }
        return if (loginHint.isNullOrEmpty()) networkScopes else operatorTokenScopes
    }
}

internal sealed class AuthorizeCallResult {
    data class Code(val code: String) : AuthorizeCallResult()

    /** Keycloak redirected to its own page (consent or another required action) instead of issuing a code. */
    data class ConsentRequired(val url: String, val cookies: List<String>) : AuthorizeCallResult()
}

class SilentLoginException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
