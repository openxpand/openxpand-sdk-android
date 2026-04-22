package com.openxpand.sdk

/**
 * URLs fijas del producto para las peticiones GET /auth (IP+puerto HTTPS y celular HTTP).
 * No se configuran desde la app integradora; el gateway para POST /token se define en
 * [OpenXpandConfig.baseGatewayUrl].
 */
object OpenXpandDefaults {
    const val BASE_AUTH_HTTPS_URL = "https://auth.openxpand.com"
    const val BASE_CELLULAR_HTTP_URL = "http://opengw.openxpand.com"
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
     * Base del gateway (p. ej. intercambio de tokens). Las peticiones GET de autorización usan
     * [OpenXpandDefaults] (auth HTTPS y celular HTTP).
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
}
