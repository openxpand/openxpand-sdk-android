package com.openxpand.sdk

sealed class NumberVerificationResult {
    data class Success(val verified: Boolean) : NumberVerificationResult()

    /**
     * SIM card was recently swapped. Verification aborted — treat as authentication failure
     * to prevent SIM-swap fraud.
     */
    object SimSwapped : NumberVerificationResult()

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : NumberVerificationResult()
}
