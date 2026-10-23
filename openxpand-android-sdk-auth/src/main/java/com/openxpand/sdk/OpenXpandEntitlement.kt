package com.openxpand.sdk

import android.app.Activity
import android.content.Context
import com.openxpand.sdk.OperatorTokenCredentialOffer
import com.openxpand.sdk.OperatorTokenPresentationResult
import com.openxpand.sdk.consent.OperatorTokenConsentDialog
import com.openxpand.sdk.entitlement.ConsentDataDecoder
import com.openxpand.sdk.entitlement.CredentialAuthorizationJwtValidator
import com.openxpand.sdk.entitlement.EntitlementException
import com.openxpand.sdk.entitlement.OperatorTokenSdJwtBuilder
import com.openxpand.sdk.entitlement.SubscriberIdentitySource
import com.openxpand.sdk.entitlement.TelephonySubscriberIdentity
import com.openxpand.sdk.entitlement.TelephonyUsimAuthenticator
import com.openxpand.sdk.entitlement.Ts43EntitlementClient
import com.openxpand.sdk.entitlement.UsimAuthResult
import com.openxpand.sdk.entitlement.UsimAuthenticator
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
 *
 * [labImsi] and [labUsim] together replace the SIM for a lab check. Both must be set,
 * or neither. They are not a production authenticator.
 */
class OpenXpandEntitlement(
    private val context: Context,
    private val config: EntitlementConfig,
    private val labImsi: String? = null,
    private val labUsim: LabUsim? = null
) {

    companion object {
        /**
         * JSON document for `TELEPHONY_TRUST_URL` so the lab aggregator accepts Issuer JWTs from this SDK.
         */
        @JvmStatic
        fun labTelephonyTrustDocumentJson(): String =
            com.openxpand.sdk.entitlement.LabTelephonyIdentity.trustDocumentJson()
    }
    /**
     * Presents an operator token for the CAMARA ap2014 path: validates the aggregator
     * credential, shows consent, runs EAP-AKA, and returns an opaque SD-JWT for the app backend.
     */
    suspend fun presentOperatorToken(
        activity: Activity,
        offer: OperatorTokenCredentialOffer,
        nonce: String,
        phoneNumberHint: String? = null
    ): OperatorTokenPresentationResult {
        if (offer.format != "dc-authorization+sd-jwt") {
            return OperatorTokenPresentationResult.Error("Unsupported credential format: ${offer.format}")
        }
        if (offer.vct.endsWith("/verify/ts43") && phoneNumberHint.isNullOrBlank()) {
            return OperatorTokenPresentationResult.Error(
                "phone_number_hint is required for number-verification/verify/ts43"
            )
        }
        return try {
            val validated = CredentialAuthorizationJwtValidator.validate(
                offer.credentialAuthorizationJwt,
                nonce,
                config.allowedAggregatorRootFingerprints
            )
            val consentText = ConsentDataDecoder.consentText(validated.consentDataBase64Url)
            if (!OperatorTokenConsentDialog.awaitUserConsent(activity, consentText)) {
                return OperatorTokenPresentationResult.Cancelled()
            }
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
                identitySource(),
                usimAuthenticator(),
                http
            ).acquire()
            val carrierHint = identitySource().imsi().take(5)
            val sdJwt = OperatorTokenSdJwtBuilder.build(
                temporaryToken = issued.token,
                vct = offer.vct,
                validated = validated,
                carrierHint = carrierHint,
                androidCarrierHint = config.androidCarrierHint,
                apkAudience = config.apkAudience
            )
            OperatorTokenPresentationResult.Success(sdJwt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: EntitlementException) {
            OperatorTokenPresentationResult.Error(
                e.message ?: "Operator token presentation failed",
                e.diameterResultCode,
                e
            )
        } catch (e: Exception) {
            OperatorTokenPresentationResult.Error(
                e.message ?: "Operator token presentation failed",
                cause = e
            )
        }
    }

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
                identitySource(),
                usimAuthenticator(),
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

    private fun identitySource(): SubscriberIdentitySource {
        val imsi = labImsi
        val usim = labUsim
        if ((imsi == null) != (usim == null)) {
            throw EntitlementException("Lab IMSI and lab USIM must both be set, or neither")
        }
        if (imsi != null) {
            return SubscriberIdentitySource { imsi }
        }
        return TelephonySubscriberIdentity(context, config.subscriptionId)
    }

    private fun usimAuthenticator(): UsimAuthenticator {
        val usim = labUsim ?: return TelephonyUsimAuthenticator(context, config.subscriptionId)
        return object : UsimAuthenticator {
            override fun authenticate(rand: ByteArray, autn: ByteArray): UsimAuthResult {
                val res = usim.res(rand, autn)
                return if (res == null) {
                    UsimAuthResult.SyncFailure(ByteArray(0))
                } else {
                    UsimAuthResult.Success(res)
                }
            }
        }
    }
}
