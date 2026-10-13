package com.openxpand.sdk.entitlement

internal data class TemporaryTokenPayload(
    val token: String,
    val expiresAt: String
)

/**
 * Reads `parm` elements from a TS.43 WAP provisioning document.
 * The lab document is a small fixed XML shape, parsed without an XML stack so
 * the same code runs in JVM unit tests and on Android.
 */
internal object WapProvisioningDoc {
    private val parmTag = Regex("""<parm\b([^>]*?)/?>""", RegexOption.IGNORE_CASE)
    private val attribute = Regex("""([A-Za-z0-9_]+)\s*=\s*"([^"]*)"""")

    fun temporaryToken(xml: String): TemporaryTokenPayload {
        val parms = linkedMapOf<String, String>()
        for (tag in parmTag.findAll(xml)) {
            val attrs = attribute.findAll(tag.groupValues[1])
                .associate { it.groupValues[1] to it.groupValues[2] }
            val name = attrs["name"] ?: continue
            val value = attrs["value"] ?: continue
            parms[name] = value
        }
        val token = parms["TemporaryToken"]
            ?: throw EntitlementException("Entitlement response has no TemporaryToken")
        val expiry = parms["TemporaryTokenExpiry"]
            ?: throw EntitlementException("Entitlement response has no TemporaryTokenExpiry")
        if (token.isEmpty()) {
            throw EntitlementException("Entitlement response has an empty TemporaryToken")
        }
        return TemporaryTokenPayload(token, expiry)
    }
}
