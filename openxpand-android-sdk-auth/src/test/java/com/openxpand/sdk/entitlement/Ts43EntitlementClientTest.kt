package com.openxpand.sdk.entitlement

import com.openxpand.sdk.EntitlementConfig
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Ts43EntitlementClientTest {

    private val imsi = "722340000000001"
    private val res = hexToBytes("0102030405060708")
    private val challengeHex =
        "01010044170100000105000000112233445566778899aabbccddeeff" +
            "02050000aabbccddeeff00112233445566778899" +
            "0b050000d4a1a935cb2369ee36156f51ce931791"
    private val responseHex =
        "0201002817010000030300400102030405060708" +
            "0b050000e25ae9b9c3c34cf87ec13d441dc1c0c4"

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
    fun `two rounds return the temporary token`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/vnd.gsma.eap-relay.v1.0+json")
                .setBody(
                    """{"eap-relay-packet":"$challengeHex","session_id":"sess","eap_session":"session-token"}"""
                )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/vnd.wap.connectivity-xml")
                .setBody(
                    """<?xml version="1.0"?><wap-provisioningdoc version="1.1"><characteristic type="APPLICATION"><parm name="AppID" value="ap2014"/><parm name="TemporaryToken" value="opaque-token"/><parm name="TemporaryTokenExpiry" value="2026-10-13T13:00:00Z"/></characteristic></wap-provisioningdoc>"""
                )
        )

        val issued = client(UsimAuthResult.Success(res)).acquire()

        assertEquals("opaque-token", issued.token)
        assertEquals("2026-10-13T13:00:00Z", issued.expiresAt)

        val first = server.takeRequest()
        assertEquals("GET", first.method)
        assertEquals(
            "0722340000000001@nai.epc.mnc034.mcc722.3gppnetwork.org",
            first.requestUrl!!.queryParameter("EAP_ID")
        )
        assertEquals("ap2014", first.requestUrl!!.queryParameter("app"))
        assertEquals("AcquireTemporaryToken", first.requestUrl!!.queryParameter("operation"))
        assertEquals("12.0", first.requestUrl!!.queryParameter("entitlement_version"))
        assertTrue(first.getHeader("Accept")!!.contains("eap-relay"))

        val second = server.takeRequest()
        assertEquals("POST", second.method)
        val body = second.body.readUtf8()
        assertTrue(body.contains(""""eap-relay-packet":"$responseHex""""))
        assertTrue(body.contains(""""eap_session":"session-token""""))
    }

    @Test
    fun `diameter failure is reported and no second request is sent`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("X-Diameter-Result-Code", "5003")
                .setHeader("Content-Type", "text/vnd.wap.connectivity-xml")
                .setBody("<error>suspended</error>")
        )

        try {
            client(UsimAuthResult.Success(res)).acquire()
            throw AssertionError("expected EntitlementException")
        } catch (e: EntitlementException) {
            assertEquals(5003, e.diameterResultCode)
            assertTrue(e.message!!.contains("suspended"))
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `sync failure stops before the EAP response`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/vnd.gsma.eap-relay.v1.0+json")
                .setBody("""{"eap-relay-packet":"$challengeHex","eap_session":"session-token"}""")
        )

        try {
            client(UsimAuthResult.SyncFailure(hexToBytes("112233445566778899aabbccdd"))).acquire()
            throw AssertionError("expected EntitlementException")
        } catch (e: EntitlementException) {
            assertTrue(e.message!!.contains("sync"))
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a RES that does not match the challenge MAC is not posted`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/vnd.gsma.eap-relay.v1.0+json")
                .setBody("""{"eap-relay-packet":"$challengeHex","eap_session":"session-token"}""")
        )

        try {
            client(UsimAuthResult.Success(hexToBytes("ffffffffffffffff"))).acquire()
            throw AssertionError("expected EntitlementException")
        } catch (e: EntitlementException) {
            assertTrue(e.message!!.contains("MAC"))
        }
        assertEquals(1, server.requestCount)
    }

    private fun client(result: UsimAuthResult) = Ts43EntitlementClient(
        config = EntitlementConfig(
            entitlementServerUrl = server.url("/").toString().trimEnd('/'),
            forceCellular = false,
            terminalVendor = "Google",
            terminalModel = "Pixel 7 Pro",
            terminalSoftwareVersion = "14",
            terminalId = "356938035643809"
        ),
        identity = SubscriberIdentitySource { imsi },
        usim = object : UsimAuthenticator {
            override fun authenticate(rand: ByteArray, autn: ByteArray): UsimAuthResult = result
        },
        httpClient = OkHttpClient()
    )
}
