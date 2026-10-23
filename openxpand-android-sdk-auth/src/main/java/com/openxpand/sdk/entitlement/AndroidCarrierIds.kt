package com.openxpand.sdk.entitlement

import android.content.Context

/**
 * Resolves active carrier IDs for OpenID4VP credential requests.
 * On the lab emulator this returns [LabOperatorTokenDefaults.LAB_ANDROID_CARRIER_ID].
 */
object AndroidCarrierIds {

    @Suppress("UNUSED_PARAMETER")
    fun activeCarrierIds(context: Context): List<Int> {
        // Play-certified devices should use Credential Manager with platform carrier IDs.
        // This lab build always advertises the whitelisted Android carrier id.
        return listOf(LabOperatorTokenDefaults.LAB_ANDROID_CARRIER_ID)
    }
}
