package com.openxpand.sdk.pkce

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class PkceGeneratorTest {

    @Test
    fun `verifier length is within RFC 7636 bounds`() {
        val verifier = PkceGenerator.generateCodeVerifier()
        // RFC 7636 §4.1: 43 to 128 characters
        assertTrue(
            "Verifier length ${verifier.length} out of range [43, 128]",
            verifier.length in 43..128
        )
    }

    @Test
    fun `verifier only contains URL-safe characters without padding`() {
        val verifier = PkceGenerator.generateCodeVerifier()
        val urlSafePattern = Regex("^[A-Za-z0-9\\-_]+$")
        assertTrue(
            "Verifier contains invalid characters: $verifier",
            urlSafePattern.matches(verifier)
        )
    }

    @Test
    fun `challenge is S256 of verifier`() {
        val verifier = PkceGenerator.generateCodeVerifier()
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val expectedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)

        val challenge = PkceGenerator.generateCodeChallenge(verifier)

        assertEquals(expectedChallenge, challenge)
    }

    @Test
    fun `challenge does not contain padding`() {
        val verifier = PkceGenerator.generateCodeVerifier()
        val challenge = PkceGenerator.generateCodeChallenge(verifier)
        assertTrue("Challenge must not contain '='", !challenge.contains('='))
    }

    @Test
    fun `generate returns unique pairs on each call`() {
        val pair1 = PkceGenerator.generate()
        val pair2 = PkceGenerator.generate()
        assertNotEquals(pair1.codeVerifier, pair2.codeVerifier)
        assertNotEquals(pair1.codeChallenge, pair2.codeChallenge)
    }

    @Test
    fun `generate returns consistent verifier and challenge`() {
        val pair = PkceGenerator.generate()
        val expectedChallenge = PkceGenerator.generateCodeChallenge(pair.codeVerifier)
        assertEquals(expectedChallenge, pair.codeChallenge)
    }
}
