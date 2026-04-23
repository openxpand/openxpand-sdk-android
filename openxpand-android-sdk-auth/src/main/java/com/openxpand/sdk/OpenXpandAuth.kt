package com.openxpand.sdk

import android.content.Context
import com.openxpand.sdk.cellular.CellularRequestManager
import com.openxpand.sdk.pkce.PkceGenerator
import com.openxpand.sdk.internal.SdkHttpClient
import com.openxpand.sdk.silent.SilentLoginManager

class OpenXpandAuth(
    private val context: Context,
    private val config: OpenXpandConfig
) {

    private val silentLoginManager = SilentLoginManager(config)
    private val cellularRequestManager = CellularRequestManager(context, config)

    // ──────────────────────────────────────────────────────────────
    //  Authorization-only methods (return code + verifier)
    //  The caller decides where to exchange: locally or via backend.
    // ──────────────────────────────────────────────────────────────

    /**
     * Obtains an authorization code via IP + source port identification (HTTPS).
     *
     * Returns [AuthorizationResult.Success] with the `authorizationCode` and
     * `codeVerifier` that must be sent to a token endpoint to get tokens.
     */
    suspend fun authorizeViaIpPort(): AuthorizationResult {
        return try {
            val pkce = PkceGenerator.generate()
            val code = silentLoginManager.authorize(pkce.codeChallenge)
            AuthorizationResult.Success(code, pkce.codeVerifier)
        } catch (e: Exception) {
            AuthorizationResult.Error("IP+Port authorization failed: ${e.message}", e)
        }
    }

    /**
     * Obtains an authorization code by identifying the subscriber via the cellular network.
     *
     * Forces the request through the cellular network (HTTP), even if the device is on WiFi.
     *
     * Returns [AuthorizationResult.Success] with the `authorizationCode` and
     * `codeVerifier` that must be sent to a token endpoint to get tokens.
     */
    suspend fun authorizeViaCellular(): AuthorizationResult {
        return try {
            val pkce = PkceGenerator.generate()
            val code = cellularRequestManager.authorize(pkce.codeChallenge)
            AuthorizationResult.Success(code, pkce.codeVerifier)
        } catch (e: Exception) {
            AuthorizationResult.Error("Cellular authorization failed: ${e.message}", e)
        }
    }

    /**
     * Tries cellular identification first; if it fails, falls back
     * to IP+port identification. Returns the first successful result
     * or the last error if both fail.
     */
    suspend fun authorize(): AuthorizationResult {
        val cellularResult = authorizeViaCellular()
        if (cellularResult is AuthorizationResult.Success) return cellularResult

        val ipPortResult = authorizeViaIpPort()
        if (ipPortResult is AuthorizationResult.Success) return ipPortResult

        return AuthorizationResult.Error(
            "All authorization methods failed. " +
                "Cellular: ${(cellularResult as AuthorizationResult.Error).message}. " +
                "IP+Port: ${(ipPortResult as AuthorizationResult.Error).message}."
        )
    }

    companion object {
        /**
         * Activa logging HTTP en Logcat (tag `OpenXpandHttp`) aunque el AAR no sea debug.
         * Debe llamarse antes de crear la primera instancia de [OpenXpandAuth].
         */
        @JvmStatic
        fun setHttpLoggingEnabled(enabled: Boolean) {
            SdkHttpClient.forceHttpLogging = enabled
        }
    }
}
