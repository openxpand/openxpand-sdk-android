package com.openxpand.sdk.entitlement

import com.nimbusds.jose.util.Base64URL
import org.json.JSONObject

internal object ConsentDataDecoder {
    fun consentText(consentDataBase64Url: String): String {
        val padded = consentDataBase64Url + "=".repeat((4 - consentDataBase64Url.length % 4) % 4)
        val json = String(Base64URL.from(padded).decode(), Charsets.UTF_8)
        return JSONObject(json).optString("consent_text", "Share the phone number of this line")
    }
}
