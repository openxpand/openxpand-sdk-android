package com.openxpand.sdk.entitlement

/**
 * Lab defaults aligned with [mobile-identity-toolkit mock_openxpand](mock_aggregator/main.py).
 */
object LabOperatorTokenDefaults {
    const val LAB_ANDROID_CARRIER_ID = 1839

    const val NV_SCOPE_VERIFY = "number-verification:verify"
    const val NV_SCOPE_DPN = "number-verification:device-phone-number:read"

    /** `android:apk-key-hash:` + SHA-256 of `personalpay-lab-apk-signing-cert`. */
    const val APK_KEY_HASH =
        "5om3yNE3AxRZcu6dSBc3mm1wDRej4Qw1SW2NPewxvv8"

    val APK_AUDIENCE: String
        get() = "android:apk-key-hash:$APK_KEY_HASH"

    /** Root fingerprints from OpenID4VP trust material whitelisted in the lab stack. */
    val AGGREGATOR_ROOT_FINGERPRINTS: Set<String> = setOf(
        // Keycloak openid4vp lab aggregator (self-signed leaf)
        "828722b45045496479e501dc043cc7cd76a7a0dea491e88ec2433f21d8efe4ef",
        "4c5a2c5768595ccfd34bc0c7be9f473fe5c695228c18b8401867c7136a835d98",
        "4ae5074f7d1f28a305100481c6d47d9ae628a6917549ed655687bbd610a29106",
        "57fc5365ce1acf2ae8df142f0e2f64332baafb432a485591ad2526af5690fe54",
    )

    const val LAB_APP_CHECK_DEBUG_SECRET = "pnv-lab-debug-secret-2026-motorola-edge30-neo"
}
