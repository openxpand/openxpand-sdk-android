package com.openxpand.sdk

sealed class SecurityProviderStatus {
    data object UpToDate : SecurityProviderStatus()
    data class UserActionRequired(val statusCode: Int) : SecurityProviderStatus()
    data class NotAvailable(val statusCode: Int) : SecurityProviderStatus()
    data class Error(val message: String, val cause: Throwable? = null) : SecurityProviderStatus()
}
