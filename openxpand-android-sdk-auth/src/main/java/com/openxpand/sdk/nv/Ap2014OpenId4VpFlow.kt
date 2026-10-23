package com.openxpand.sdk.nv

import android.app.Activity
import com.openxpand.sdk.OpenXpandConfig
import com.openxpand.sdk.OpenXpandEntitlement
import com.openxpand.sdk.OperatorTokenPresentationResult
import com.openxpand.sdk.entitlement.AndroidCarrierIds

/** Scopes for the OpenID4VP JWT-bearer assertion (see [OpenXpandConfig.authorizeScopes]). */
internal object Ap2014AccessTokenScopes {
    fun forExchange(config: OpenXpandConfig, op: OpenXpandAggregatorClient.NvScope): String {
        val configured = config.authorizeScopes.trim()
        val tokens = if (configured.isEmpty()) {
            mutableSetOf("openid", op.wireValue)
        } else {
            configured.split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableSet()
        }
        tokens.add("openid")
        tokens.add(op.wireValue)
        return tokens.joinToString(" ")
    }
}

/**
 * TRAM ap2014 sequence against Keycloak as OpenID4VP aggregator.
 */
class Ap2014OpenId4VpFlow(
    private val config: OpenXpandConfig,
    private val entitlement: OpenXpandEntitlement
) {
    private val aggregator = OpenXpandAggregatorClient(config)

    suspend fun run(
        activity: Activity,
        op: OpenXpandAggregatorClient.NvScope,
        phoneNumberHint: String? = null
    ): FlowResult {
        val carrierIds = AndroidCarrierIds.activeCarrierIds(activity.applicationContext)
        val accessScope = Ap2014AccessTokenScopes.forExchange(config, op)
        when (
            val credentials = aggregator.requestCredentials(
                scope = accessScope,
                carrierIds = carrierIds,
                numberVerificationScope = op.wireValue
            )
        ) {
            is OpenXpandAggregatorClient.CredentialsResult.Error ->
                return FlowResult.Error(credentials.message, credentials.cause)
            is OpenXpandAggregatorClient.CredentialsResult.Success -> {
                when (
                    val presented = entitlement.presentOperatorToken(
                        activity = activity,
                        offer = credentials.offer,
                        nonce = credentials.nonce,
                        phoneNumberHint = phoneNumberHint
                    )
                ) {
                    is OperatorTokenPresentationResult.Cancelled ->
                        return FlowResult.Cancelled
                    is OperatorTokenPresentationResult.Error ->
                        return FlowResult.Error(presented.message, presented.cause)
                    is OperatorTokenPresentationResult.Success ->
                        when (val token = aggregator.exchangeVpToken(presented.sdJwt, accessScope)) {
                            is OpenXpandAggregatorClient.TokenExchangeResult.Error ->
                                return FlowResult.Error(token.message, token.cause)
                            is OpenXpandAggregatorClient.TokenExchangeResult.Success ->
                                return FlowResult.Success(
                                    accessToken = token.accessToken,
                                    expiresIn = token.expiresIn
                                )
                        }
                }
            }
        }
    }

    sealed class FlowResult {
        data class Success(val accessToken: String, val expiresIn: Long) : FlowResult()
        data class Error(val message: String, val cause: Throwable? = null) : FlowResult()
        data object Cancelled : FlowResult()
    }
}
