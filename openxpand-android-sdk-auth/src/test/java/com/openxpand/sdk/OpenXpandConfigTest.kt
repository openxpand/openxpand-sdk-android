package com.openxpand.sdk

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenXpandConfigTest {

    private val config = OpenXpandConfig(
        clientId = "test-client",
        tenant = "acme",
        redirectUri = "https://app.example.com/callback"
    )

    @Test
    fun `authEndpoint uses fixed HTTPS base URL and tenant`() {
        assertEquals(
            "https://auth.openxpand.com/auth/realms/acme/protocol/openid-connect/auth",
            config.authEndpoint
        )
    }

    @Test
    fun `cellularAuthEndpoint uses fixed HTTP base URL and tenant`() {
        assertEquals(
            "http://opengw.openxpand.com/auth/realms/acme/protocol/openid-connect/auth",
            config.cellularAuthEndpoint
        )
    }

    @Test
    fun `tokenEndpoint uses default gateway URL and tenant`() {
        assertEquals(
            "https://opengw.openxpand.com/auth/realms/acme/protocol/openid-connect/token",
            config.tokenEndpoint
        )
    }

    @Test
    fun `tokenEndpoint strips trailing slash from baseGatewayUrl`() {
        val configWithSlash = config.copy(baseGatewayUrl = "https://opengw.openxpand.com/")
        assertEquals(
            "https://opengw.openxpand.com/auth/realms/acme/protocol/openid-connect/token",
            configWithSlash.tokenEndpoint
        )
    }

    @Test
    fun `tokenEndpoint uses custom gateway URL`() {
        val configCustomGw = config.copy(baseGatewayUrl = "https://my-gateway.example.com")
        assertEquals(
            "https://my-gateway.example.com/auth/realms/acme/protocol/openid-connect/token",
            configCustomGw.tokenEndpoint
        )
    }

    @Test
    fun `clientSecret defaults to empty string`() {
        assertEquals("", config.clientSecret)
    }

    @Test
    fun `simSwapEndpoint uses default CAMARA base URL`() {
        assertEquals(
            "https://api.openxpand.com/api/camara/sim-swap/v0/check",
            config.simSwapEndpoint
        )
    }

    @Test
    fun `numberVerificationEndpoint uses default CAMARA base URL`() {
        assertEquals(
            "https://api.openxpand.com/api/camara/number-verification/v2/verify",
            config.numberVerificationEndpoint
        )
    }

    @Test
    fun `simSwapEndpoint uses custom baseCamaraApiUrl`() {
        val custom = config.copy(baseCamaraApiUrl = "https://staging.api.openxpand.com")
        assertEquals(
            "https://staging.api.openxpand.com/api/camara/sim-swap/v0/check",
            custom.simSwapEndpoint
        )
    }

    @Test
    fun `numberVerificationEndpoint strips trailing slash from baseCamaraApiUrl`() {
        val custom = config.copy(baseCamaraApiUrl = "https://api.openxpand.com/")
        assertEquals(
            "https://api.openxpand.com/api/camara/number-verification/v2/verify",
            custom.numberVerificationEndpoint
        )
    }

    @Test
    fun `endpoints reflect tenant change`() {
        val other = config.copy(tenant = "telecom")
        assertEquals(
            "https://auth.openxpand.com/auth/realms/telecom/protocol/openid-connect/auth",
            other.authEndpoint
        )
        assertEquals(
            "https://opengw.openxpand.com/auth/realms/telecom/protocol/openid-connect/token",
            other.tokenEndpoint
        )
    }

    @Test
    fun `local keycloak uses configurable hosts without the legacy auth prefix`() {
        val local = config.copy(
            baseAuthUrl = "http://10.0.2.2:8080",
            baseCellularAuthUrl = "http://10.0.2.2:8080",
            baseGatewayUrl = "http://10.0.2.2:8080/",
            includeLegacyAuthPrefix = false
        )
        assertEquals(
            "http://10.0.2.2:8080/realms/acme/protocol/openid-connect/auth",
            local.authEndpoint
        )
        assertEquals(
            "http://10.0.2.2:8080/realms/acme/protocol/openid-connect/auth",
            local.cellularAuthEndpoint
        )
        assertEquals(
            "http://10.0.2.2:8080/realms/acme/protocol/openid-connect/token",
            local.tokenEndpoint
        )
    }

    @Test
    fun `openId4VpBaseUrl uses gateway host and tenant without legacy auth prefix`() {
        val local = config.copy(
            baseGatewayUrl = "http://10.0.2.2:8080",
            includeLegacyAuthPrefix = false
        )
        assertEquals(
            "http://10.0.2.2:8080/realms/acme/openid4vp",
            local.openId4VpBaseUrl
        )
        assertEquals(
            "http://10.0.2.2:8080/realms/acme/openid4vp/token",
            local.openId4VpTokenUrl
        )
    }

    @Test
    fun `devicePhoneNumberEndpoint uses the v2 share path`() {
        assertEquals(
            "https://api.openxpand.com/api/camara/number-verification/v2/device-phone-number",
            config.devicePhoneNumberEndpoint
        )
    }
}
