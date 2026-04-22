package com.openxpand.sdk

sealed class AuthResult {
    data class Success(
        val accessToken: String,
        val tokenType: String = "Bearer",
        val expiresIn: Long = 0,
        val refreshToken: String? = null
    ) : AuthResult()

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : AuthResult()
}
