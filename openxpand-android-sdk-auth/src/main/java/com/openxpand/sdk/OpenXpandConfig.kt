package com.openxpand.sdk

/**
 * Fixed product URLs for GET /auth requests (IP+port HTTPS and cellular HTTP).
 * Not configurable by the integrating app; the gateway for POST /token is set via
 * [OpenXpandConfig.baseGatewayUrl].
 */
object OpenXpandDefaults {
    const val BASE_AUTH_HTTPS_URL = "https://auth.openxpand.com"
    const val BASE_CELLULAR_HTTP_URL = "http://opengw.openxpand.com"
    const val BASE_CAMARA_API_URL = "https://api.openxpand.com"
}

data class OpenXpandConfig(
    val clientId: String,
    val tenant: String,
    val redirectUri: String,
    /**
     * Optional. Required by Keycloak for confidential clients when exchanging the code at the token endpoint.
     * Leave empty for public clients (PKCE-only).
     */
    val clientSecret: String = "",
    /**
     * Gateway base URL (e.g. for token exchange). Authorization GET requests use
     * the fixed URLs in [OpenXpandDefaults] (auth HTTPS and cellular HTTP).
     */
    val baseGatewayUrl: String = "https://opengw.openxpand.com"
) {
    val authEndpoint: String
        get() =
            "${OpenXpandDefaults.BASE_AUTH_HTTPS_URL}/auth/realms/$tenant/protocol/openid-connect/auth"

    val cellularAuthEndpoint: String
        get() =
            "${OpenXpandDefaults.BASE_CELLULAR_HTTP_URL}/auth/realms/$tenant/protocol/openid-connect/auth"

    val tokenEndpoint: String
        get() = "${baseGatewayUrl.trimEnd('/')}/auth/realms/$tenant/protocol/openid-connect/token"

    val simSwapEndpoint: String
        get() = "${OpenXpandDefaults.BASE_CAMARA_API_URL}/api/camara/sim-swap/v0/check"

    val numberVerificationEndpoint: String
        get() = "${OpenXpandDefaults.BASE_CAMARA_API_URL}/api/camara/number-verification/v0/verify"
}
