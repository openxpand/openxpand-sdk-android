package com.openxpand.sdk.entitlement

internal class EntitlementException(
    message: String,
    val diameterResultCode: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause)
