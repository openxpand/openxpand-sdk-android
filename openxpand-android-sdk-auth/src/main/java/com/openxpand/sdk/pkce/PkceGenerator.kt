package com.openxpand.sdk.pkce

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Generates PKCE (Proof Key for Code Exchange) parameters for OAuth2.
 * RFC 7636: https://tools.ietf.org/html/rfc7636
 */
internal object PkceGenerator {

    private const val VERIFIER_LENGTH = 64

    /**
     * Generates a cryptographically random code_verifier.
     * Length: 43-128 characters (we use 64 bytes → 86 base64 chars).
     */
    fun generateCodeVerifier(): String {
        val bytes = ByteArray(VERIFIER_LENGTH)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * Generates code_challenge from code_verifier using S256 method.
     * code_challenge = BASE64URL(SHA256(code_verifier))
     */
    fun generateCodeChallenge(codeVerifier: String): String {
        val bytes = codeVerifier.toByteArray(Charsets.US_ASCII)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * Creates a new PKCE pair (verifier + challenge).
     */
    fun generate(): PkcePair {
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)
        return PkcePair(verifier, challenge)
    }
}

/**
 * Holds the PKCE code_verifier and code_challenge pair.
 */
internal data class PkcePair(
    val codeVerifier: String,
    val codeChallenge: String
)
