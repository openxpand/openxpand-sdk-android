package com.openxpand.sdk

import android.content.Context
import com.openxpand.sdk.entitlement.EntitlementException
import com.openxpand.sdk.entitlement.TelephonySubscriberIdentity
import com.openxpand.sdk.entitlement.TelephonyUsimAuthenticator
import com.openxpand.sdk.entitlement.Ts43EntitlementClient
import com.openxpand.sdk.entitlement.cellularSocketFactory
import com.openxpand.sdk.internal.SdkHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * On-device GSMA TS.43 client for ap2014 AcquireTemporaryToken.
 *
 * The call runs EAP-AKA against [EntitlementConfig.entitlementServerUrl] and returns
 * an opaque temporary token. It does not obtain an OpenXpand access token and it does
 * not return the MSISDN. The hosting app must have carrier privileges on the SIM;
 * otherwise the platform refuses the USIM authentication.
 */
class OpenXpandEntitlement(
    private val context: Context,
    private val config: EntitlementConfig
) {
    suspend fun acquireTemporaryToken(): TemporaryTokenResult {
        return try {
            val cellularFactory = if (config.forceCellular) {
                cellularSocketFactory(context, config.cellularNetworkTimeoutMs)
            } else {
                null
            }
            val http = withContext(Dispatchers.IO) {
                SdkHttpClient.newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .apply {
                        if (cellularFactory != null) {
                            socketFactory(cellularFactory)
                        }
                    }
                    .build()
            }
            val issued = Ts43EntitlementClient(
                config,
                TelephonySubscriberIdentity(context, config.subscriptionId),
                TelephonyUsimAuthenticator(context, config.subscriptionId),
                http
            ).acquire()
            TemporaryTokenResult.Success(issued.token, issued.expiresAt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: EntitlementException) {
            TemporaryTokenResult.Error(
                e.message ?: "Entitlement request failed",
                e.diameterResultCode,
                e
            )
        } catch (e: Exception) {
            TemporaryTokenResult.Error(e.message ?: "Entitlement request failed", cause = e)
        }
    }
}
