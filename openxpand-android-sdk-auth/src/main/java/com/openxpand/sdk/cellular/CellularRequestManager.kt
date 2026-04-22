package com.openxpand.sdk.cellular

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.openxpand.sdk.OpenXpandConfig
import com.openxpand.sdk.internal.SdkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Sends an authorization request forced through the cellular network,
 * even when WiFi is available. Uses plain HTTP as required by the
 * OpenXpand gateway endpoint.
 */
internal class CellularRequestManager(
    private val context: Context,
    private val config: OpenXpandConfig
) {

    /**
     * Requests a cellular network from the system and sends the OAuth2
     * authorization request through it. Returns the authorization code
     * from the redirect Location header.
     *
     * @param codeChallenge PKCE code_challenge (S256) for security.
     */
    suspend fun authorize(codeChallenge: String): String {
        val cellularNetwork = acquireCellularNetwork()
        return withContext(Dispatchers.IO) {
            sendAuthRequest(cellularNetwork, codeChallenge)
        }
    }

    private suspend fun acquireCellularNetwork(): Network =
        suspendCancellableCoroutine { cont ->
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    cm.unregisterNetworkCallback(this)
                    cont.resume(network)
                }

                override fun onUnavailable() {
                    cm.unregisterNetworkCallback(this)
                    cont.resumeWithException(
                        CellularRequestException("Cellular network is not available")
                    )
                }
            }

            cont.invokeOnCancellation {
                try {
                    cm.unregisterNetworkCallback(callback)
                } catch (_: IllegalArgumentException) {
                    // already unregistered
                }
            }

            cm.requestNetwork(request, callback, NETWORK_TIMEOUT_MS)
        }

    private fun sendAuthRequest(network: Network, codeChallenge: String): String {
        val client = SdkHttpClient.newBuilder()
            .socketFactory(network.socketFactory)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val url = config.cellularAuthEndpoint.toHttpUrl().newBuilder()
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
            throw CellularRequestException(
                "Expected redirect from auth server, got HTTP $statusCode"
            )
        }

        val location = response.header("Location")
            ?: throw CellularRequestException("No Location header in redirect response")

        val redirectUrl = location.toHttpUrl()
        return redirectUrl.queryParameter("code")
            ?: throw CellularRequestException(
                "No authorization code in redirect URL. " +
                    "Error: ${redirectUrl.queryParameter("error") ?: "unknown"}"
            )
    }

    companion object {
        private const val NETWORK_TIMEOUT_MS = 10_000
    }
}

class CellularRequestException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
