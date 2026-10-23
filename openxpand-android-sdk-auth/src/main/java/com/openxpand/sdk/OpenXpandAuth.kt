package com.openxpand.sdk

import android.content.Context
import com.openxpand.sdk.cellular.CellularRequestManager
import com.openxpand.sdk.pkce.PkceGenerator
import com.openxpand.sdk.internal.SdkHttpClient
import com.openxpand.sdk.internal.SecurityProviderUpdater
import com.openxpand.sdk.silent.SilentLoginManager
import com.openxpand.sdk.camara.CamaraApiClient
import com.openxpand.sdk.token.TokenExchanger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OpenXpandAuth(
    private val context: Context,
    private val config: OpenXpandConfig
) {

    private val silentLoginManager = SilentLoginManager(config)
    private val cellularRequestManager = CellularRequestManager(context, config)
    private val tokenExchanger = TokenExchanger(config)
    private val camaraApiClient = CamaraApiClient(config)
    private val securityProviderScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        securityProviderScope.launch {
            val providerStatus = prepareSecurityProvider()
            if (providerStatus != null) {
                notifySecurityProviderStatus(providerStatus)
            }
        }
    }

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

    // ──────────────────────────────────────────────────────────────
    //  Full-flow methods (authorize + token exchange in the device)
    //  Convenient for testing. In production, prefer doing the token
    //  exchange from a backend so client_secret never leaves the server.
    // ──────────────────────────────────────────────────────────────

    suspend fun authenticateViaIpPort(): AuthResult {
        return when (val result = authorizeViaIpPort()) {
            is AuthorizationResult.Success ->
                tokenExchanger.exchange(result.authorizationCode, result.codeVerifier)
            is AuthorizationResult.Error ->
                AuthResult.Error(result.message, result.cause)
        }
    }

    suspend fun authenticateViaCellular(): AuthResult {
        return when (val result = authorizeViaCellular()) {
            is AuthorizationResult.Success ->
                tokenExchanger.exchange(result.authorizationCode, result.codeVerifier)
            is AuthorizationResult.Error ->
                AuthResult.Error(result.message, result.cause)
        }
    }

    suspend fun authenticate(): AuthResult {
        val cellularResult = authenticateViaCellular()
        if (cellularResult is AuthResult.Success) return cellularResult

        return authenticateViaIpPort()
    }

    // ──────────────────────────────────────────────────────────────
    //  CAMARA API — SIM Swap + Number Verification
    // ──────────────────────────────────────────────────────────────

    /**
     * Verifies that [phoneNumber] belongs to the authenticated subscriber.
     *
     * First checks for a recent SIM swap. If the SIM was swapped ([NumberVerificationResult.SimSwapped]),
     * the call is aborted to prevent SIM-swap fraud. Only when the SIM is stable does it proceed
     * to confirm the phone number via the Number Verification API.
     *
     * @param accessToken Bearer token obtained from [authenticate] or your own token exchange.
     * @param phoneNumber Phone number to verify, E.164 format recommended **without the
     *                    mobile `9` prefix for Argentine numbers** (e.g. `"+541198765432"`,
     *                    not `"+5491198765432"`). The CAMARA backend rejects the mobile-prefixed form.
     */
    suspend fun verifyNumber(accessToken: String, phoneNumber: String): NumberVerificationResult {
        return try {
            val swapped = camaraApiClient.checkSimSwap(accessToken, phoneNumber)
            if (swapped) return NumberVerificationResult.SimSwapped

            val verified = camaraApiClient.verifyNumber(accessToken, phoneNumber)
            NumberVerificationResult.Success(verified)
        } catch (e: Exception) {
            NumberVerificationResult.Error("Number verification failed: ${e.message}", e)
        }
    }

    /**
     * CAMARA Number Verification v2 verify. Compares [phoneNumber] with the MSISDN in the
     * access token and does not call SIM Swap.
     */
    suspend fun verifyDevicePhoneNumber(accessToken: String, phoneNumber: String): NumberVerificationResult {
        return try {
            NumberVerificationResult.Success(camaraApiClient.verifyNumber(accessToken, phoneNumber))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NumberVerificationResult.Error("Number verification failed: ${e.message}", e)
        }
    }

    /**
     * CAMARA Number Verification v2 device-phone-number. Returns the MSISDN associated
     * with the access token.
     */
    suspend fun devicePhoneNumber(accessToken: String): DevicePhoneNumberResult {
        return try {
            DevicePhoneNumberResult.Success(camaraApiClient.devicePhoneNumber(accessToken))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DevicePhoneNumberResult.Error("Device phone number request failed: ${e.message}", e)
        }
    }

    private suspend fun prepareSecurityProvider(): SecurityProviderStatus? {
        return when (val status = SecurityProviderUpdater.ensureUpToDate(context)) {
            is SecurityProviderUpdater.InstallStatus.Installed -> SecurityProviderStatus.UpToDate
            is SecurityProviderUpdater.InstallStatus.RepairableFailure ->
                SecurityProviderStatus.UserActionRequired(status.connectionStatusCode)
            is SecurityProviderUpdater.InstallStatus.NotAvailableFailure ->
                SecurityProviderStatus.NotAvailable(status.errorCode)
            is SecurityProviderUpdater.InstallStatus.NotAttempted ->
                SecurityProviderStatus.Error("Security provider update has not been attempted yet.")
        }.let { resolved ->
            if (resolved is SecurityProviderStatus.UpToDate) null else resolved
        }
    }

    private fun notifySecurityProviderStatus(status: SecurityProviderStatus) {
        try {
            config.onSecurityProviderStatus?.invoke(status)
        } catch (_: Throwable) {
            // Host callback failures must never break SDK behavior.
        }
    }

    companion object {
        /**
         * Enables HTTP logging to Logcat (tag `OpenXpandHttp`) even when the AAR is not a debug build.
         * Must be called before creating the first [OpenXpandAuth] instance.
         */
        @JvmStatic
        fun setHttpLoggingEnabled(enabled: Boolean) {
            SdkHttpClient.forceHttpLogging = enabled
        }
    }
}
