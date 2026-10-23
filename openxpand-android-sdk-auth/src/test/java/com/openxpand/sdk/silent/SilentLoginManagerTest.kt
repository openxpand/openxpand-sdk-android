package com.openxpand.sdk.silent

import com.openxpand.sdk.OpenXpandConfig
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SilentLoginManagerTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `authorize sends operatortoken login hint and returns the code`() = runTest {
        val base = server.url("/").toString().trimEnd('/')
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "https://app.example.com/callback?code=auth-code&state=s")
        )
        val manager = SilentLoginManager(
            OpenXpandConfig(
                clientId = "demo",
                tenant = "telecom",
                redirectUri = "https://app.example.com/callback",
                baseAuthUrl = base,
                includeLegacyAuthPrefix = false
            )
        )

        val result = manager.authorizeForResult("challenge", "operatortoken:temp-token")

        assertTrue(result is AuthorizeCallResult.Code)
        assertEquals("auth-code", (result as AuthorizeCallResult.Code).code)
        val request = server.takeRequest()
        val path = request.path ?: ""
        assertTrue(path.startsWith("/realms/telecom/protocol/openid-connect/auth?"))
        assertTrue(path.contains("login_hint=operatortoken%3Atemp-token"))
        assertTrue(path.contains("scope=openid"))
        assertTrue(path.contains("number-verification%3Averify"))
        assertTrue(path.contains("number-verification%3Adevice-phone-number%3Aread"))
        assertTrue(path.contains("code_challenge=challenge"))
    }

    @Test
    fun `authorize uses the scopes configured by the app`() = runTest {
        val base = server.url("/").toString().trimEnd('/')
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "https://app.example.com/callback?code=auth-code")
        )
        val manager = SilentLoginManager(
            OpenXpandConfig(
                clientId = "demo",
                tenant = "telecom",
                redirectUri = "https://app.example.com/callback",
                baseAuthUrl = base,
                includeLegacyAuthPrefix = false,
                authorizeScopes = "openid number-verification:verify"
            )
        )

        manager.authorizeForResult("challenge", "operatortoken:temp-token")

        val path = server.takeRequest().path ?: ""
        assertTrue(path.contains("scope=openid+number-verification%3Averify") ||
            path.contains("scope=openid%20number-verification%3Averify"))
        assertTrue(!path.contains("sim-swap"))
    }

    @Test
    fun `authorize returns the consent redirect and its cookies when no code is issued`() = runTest {
        val base = server.url("/").toString().trimEnd('/')
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", "/realms/telecom/login-actions/consent?client_id=demo")
                .addHeader("Set-Cookie", "AUTH_SESSION_ID=session-1; Path=/realms/telecom")
                .addHeader("Set-Cookie", "KC_RESTART=restart-1; Path=/realms/telecom")
        )
        val manager = SilentLoginManager(
            OpenXpandConfig(
                clientId = "demo",
                tenant = "telecom",
                redirectUri = "https://app.example.com/callback",
                baseAuthUrl = base,
                includeLegacyAuthPrefix = false
            )
        )

        val result = manager.authorizeForResult("challenge", "operatortoken:temp-token")

        assertTrue(result is AuthorizeCallResult.ConsentRequired)
        val consent = result as AuthorizeCallResult.ConsentRequired
        assertTrue(consent.url.contains("/login-actions/consent"))
        assertEquals(
            listOf(
                "AUTH_SESSION_ID=session-1; Path=/realms/telecom",
                "KC_RESTART=restart-1; Path=/realms/telecom"
            ),
            consent.cookies
        )
    }
}
