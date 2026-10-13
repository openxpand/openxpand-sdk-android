package com.openxpand.sdk

/**
 * Result of ap2014 AcquireTemporaryToken.
 *
 * [Success.token] is an opaque single-use credential. It does not contain the MSISDN.
 * The app backend redeems it with the entitlement server.
 */
sealed class TemporaryTokenResult {
    data class Success(
        val token: String,
        val expiresAt: String
    ) : TemporaryTokenResult()

    data class Error(
        val message: String,
        val diameterResultCode: Int? = null,
        val cause: Throwable? = null
    ) : TemporaryTokenResult()
}
