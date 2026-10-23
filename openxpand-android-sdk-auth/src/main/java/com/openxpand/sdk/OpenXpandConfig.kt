package com.openxpand.sdk

/**
 * Product URL defaults. [OpenXpandConfig] can override the auth hosts and drop the
 * legacy `/auth` path prefix used by older Keycloak deployments.
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
     * Host for the authorization GET (IP + port). Local Keycloak is
     * `http://10.0.2.2:8080` from the emulator.
     */
    val baseAuthUrl: String = OpenXpandDefaults.BASE_AUTH_HTTPS_URL,
    /**
     * Host for the cellular authorization GET.
     */
    val baseCellularAuthUrl: String = OpenXpandDefaults.BASE_CELLULAR_HTTP_URL,
    /**
     * When true, realm paths keep the legacy `/auth` prefix
     * (`/auth/realms/{tenant}/...`). Keycloak 25 local realms omit it.
     */
    val includeLegacyAuthPrefix: Boolean = true,
    /**
     * Gateway base URL (e.g. for token exchange). With [includeLegacyAuthPrefix]
     * false this is the Keycloak host, such as `http://10.0.2.2:8080`.
     */
    val baseGatewayUrl: String = "https://opengw.openxpand.com",
    /**
     * Base URL for CAMARA APIs (SIM swap, number verification).
     * Override for staging or testing environments.
     */
    val baseCamaraApiUrl: String = OpenXpandDefaults.BASE_CAMARA_API_URL,
    /**
     * Timeout in ms to wait for the cellular network to become available.
     * On some Android 10 devices (e.g. Samsung) the cellular radio can take
     * longer than 10 s to wake from idle when WiFi is active.
     * Increase to 20 000–30 000 on affected devices.
     */
    val cellularNetworkTimeoutMs: Int = 20_000,
    /**
     * Space-separated scopes for the authorize request.
     * Empty keeps the SDK defaults: the dpv scopes, plus the two CAMARA
     * operation scopes when the request carries an operator-token login hint.
     */
    val authorizeScopes: String = "",
    /**
     * Optional callback to notify the host app when the Google Play security provider
     * cannot be updated or validated. SDK operations continue running normally.
     */
    val onSecurityProviderStatus: ((SecurityProviderStatus) -> Unit)? = null
) {
    val authEndpoint: String
        get() = "${realmRoot(baseAuthUrl)}/auth"

    val cellularAuthEndpoint: String
        get() = "${realmRoot(baseCellularAuthUrl)}/auth"

    val tokenEndpoint: String
        get() = "${realmRoot(baseGatewayUrl)}/token"

    /** Keycloak OpenID4VP aggregator base (`/realms/{tenant}/openid4vp`). */
    val openId4VpBaseUrl: String
        get() {
            val prefix = if (includeLegacyAuthPrefix) "/auth" else ""
            return "${baseGatewayUrl.trimEnd('/')}$prefix/realms/$tenant/openid4vp"
        }

    val openId4VpTokenUrl: String
        get() = "$openId4VpBaseUrl/token"

    private fun realmRoot(host: String): String {
        val prefix = if (includeLegacyAuthPrefix) "/auth" else ""
        return "${host.trimEnd('/')}$prefix/realms/$tenant/protocol/openid-connect"
    }

    val simSwapEndpoint: String
        get() = "${baseCamaraApiUrl.trimEnd('/')}/api/camara/sim-swap/v0/check"

    val numberVerificationEndpoint: String
        get() = "${baseCamaraApiUrl.trimEnd('/')}/api/camara/number-verification/v2/verify"

    val devicePhoneNumberEndpoint: String
        get() = "${baseCamaraApiUrl.trimEnd('/')}/api/camara/number-verification/v2/device-phone-number"
}
