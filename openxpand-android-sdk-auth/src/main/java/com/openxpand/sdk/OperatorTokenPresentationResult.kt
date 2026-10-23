package com.openxpand.sdk

sealed class OperatorTokenPresentationResult {
    data class Success(val sdJwt: String) : OperatorTokenPresentationResult()

    data class Cancelled(val message: String = "User declined consent") : OperatorTokenPresentationResult()

    data class Error(
        val message: String,
        val diameterResultCode: Int? = null,
        val cause: Throwable? = null
    ) : OperatorTokenPresentationResult()
}
