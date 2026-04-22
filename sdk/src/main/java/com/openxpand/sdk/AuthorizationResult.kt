package com.openxpand.sdk

/**
 * Result of the authorization step only (no token exchange).
 * Contains the authorization code and PKCE code_verifier needed
 * to exchange for tokens — either locally or via a backend.
 */
sealed class AuthorizationResult {
    data class Success(
        val authorizationCode: String,
        val codeVerifier: String
    ) : AuthorizationResult()

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : AuthorizationResult()
}
