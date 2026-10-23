package com.openxpand.sdk

sealed class DevicePhoneNumberResult {
    data class Success(val devicePhoneNumber: String) : DevicePhoneNumberResult()

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : DevicePhoneNumberResult()
}
