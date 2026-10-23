package com.openxpand.sdk.nv

import com.openxpand.sdk.OpenXpandConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Ap2014OpenId4VpFlowTest {

    @Test
    fun `forExchange merges authorizeScopes with the NV operation scope`() {
        val config = OpenXpandConfig(
            clientId = "app",
            tenant = "telecom",
            redirectUri = "https://example/cb",
            authorizeScopes = "openid number-verification:verify dpv:FraudPreventionAndDetection#number-verification"
        )
        val scope = Ap2014AccessTokenScopes.forExchange(
            config,
            OpenXpandAggregatorClient.NvScope.DevicePhoneNumber
        )
        assertTrue(scope.contains("openid"))
        assertEquals(1, scope.split(Regex("\\s+")).count { it == "openid" })
        assertTrue(scope.contains("number-verification:device-phone-number:read"))
        assertTrue(scope.contains("number-verification:verify"))
    }

    @Test
    fun `forExchange falls back to NV operation when authorizeScopes is empty`() {
        val config = OpenXpandConfig(
            clientId = "app",
            tenant = "telecom",
            redirectUri = "https://example/cb"
        )
        val scope = Ap2014AccessTokenScopes.forExchange(config, OpenXpandAggregatorClient.NvScope.Verify)
        assertTrue(scope.contains("openid"))
        assertTrue(scope.contains(OpenXpandAggregatorClient.NvScope.Verify.wireValue))
    }
}
