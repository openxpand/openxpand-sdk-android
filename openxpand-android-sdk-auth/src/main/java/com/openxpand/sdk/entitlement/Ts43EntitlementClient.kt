package com.openxpand.sdk.entitlement

import com.openxpand.sdk.EntitlementConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

internal fun interface SubscriberIdentitySource {
    fun imsi(): String
}

internal class Ts43EntitlementClient(
    private val config: EntitlementConfig,
    private val identity: SubscriberIdentitySource,
    private val usim: UsimAuthenticator,
    private val httpClient: OkHttpClient
) {
    suspend fun acquire(): TemporaryTokenPayload = withContext(Dispatchers.IO) {
        val imsi = identity.imsi()
        val eapId = LabEapAka.rootNai(imsi, config.mcc, config.mnc)
        val challengeResponse = getChallenge(eapId)
        val eapRequest = hexToBytes(challengeResponse.packetHex)
        val challenge = LabEapAka.parseChallenge(eapRequest)
        when (val auth = usim.authenticate(challenge.rand, challenge.autn)) {
            is UsimAuthResult.SyncFailure ->
                throw EntitlementException(
                    "USIM reported a sequence sync failure. AUTS resynchronization is not supported"
                )
            is UsimAuthResult.Success -> {
                if (!LabEapAka.challengeMacValid(eapRequest, imsi, auth.res)) {
                    throw EntitlementException("EAP-AKA challenge MAC does not match the USIM response")
                }
                val eapResponse = LabEapAka.buildResponse(
                    challenge.identifier,
                    imsi,
                    challenge.rand,
                    auth.res
                )
                postResponse(challengeResponse.session, eapResponse)
            }
        }
    }

    private fun getChallenge(eapId: String): ChallengeHttp {
        val url = (config.entitlementServerUrl.trimEnd('/') + "/").toHttpUrl().newBuilder()
            .addQueryParameter("app", "ap2014")
            .addQueryParameter("operation", "AcquireTemporaryToken")
            .addQueryParameter("EAP_ID", eapId)
            .addQueryParameter("terminal_id", config.terminalId)
            .addQueryParameter("terminal_vendor", config.terminalVendor)
            .addQueryParameter("terminal_model", config.terminalModel)
            .addQueryParameter("terminal_sw_version", config.terminalSoftwareVersion)
            .addQueryParameter("entitlement_version", config.entitlementVersion)
            .addQueryParameter("vers", "1")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", ACCEPT)
            .header("User-Agent", userAgent())
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw httpFailure(response)
            }
            val type = response.header("Content-Type").orEmpty()
            val body = response.body?.string().orEmpty()
            if (!type.contains(EAP_RELAY_MEDIA)) {
                throw EntitlementException("Expected an EAP-AKA challenge from the entitlement server")
            }
            val packet = jsonStringField(body, "eap-relay-packet")
                ?: throw EntitlementException("EAP challenge is missing eap-relay-packet")
            val session = jsonStringField(body, "eap_session")
                ?: cookieValue(response.header("Set-Cookie"))
                ?: throw EntitlementException("EAP challenge is missing eap_session")
            return ChallengeHttp(packet, session)
        }
    }

    private fun postResponse(session: String, eapResponse: ByteArray): TemporaryTokenPayload {
        val body = buildString {
            append("{\"eap-relay-packet\":")
            append(jsonQuoted(eapResponse.toHex()))
            append(",\"eap_session\":")
            append(jsonQuoted(session))
            append('}')
        }
        val request = Request.Builder()
            .url(config.entitlementServerUrl.trimEnd('/') + "/")
            .header("Accept", ACCEPT)
            .header("Content-Type", EAP_RELAY_MEDIA)
            .header("User-Agent", userAgent())
            .post(body.toRequestBody(EAP_RELAY_MEDIA.toMediaType()))
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw httpFailure(response)
            }
            val type = response.header("Content-Type").orEmpty()
            val xml = response.body?.string().orEmpty()
            if (type.contains(EAP_RELAY_MEDIA)) {
                throw EntitlementException("Entitlement server returned another EAP challenge")
            }
            if (!type.contains("wap.connectivity-xml") && !xml.contains("TemporaryToken")) {
                throw EntitlementException("Entitlement server did not return a provisioning document")
            }
            return WapProvisioningDoc.temporaryToken(xml)
        }
    }

    private fun userAgent(): String {
        val vendor = headerSafe(config.terminalVendor)
        val model = headerSafe(config.terminalModel).replace(' ', '_')
        val version = headerSafe(config.terminalSoftwareVersion)
        return "PRD-TS43 $vendor/$model CarrierEntitlement/2.0 OS-Android/$version"
    }

    private fun httpFailure(response: Response): EntitlementException {
        val diameter = response.header("X-Diameter-Result-Code")?.trim()?.toIntOrNull()
        val message = when (diameter) {
            5001 -> "IMSI is not provisioned on the entitlement server"
            5003 -> "The line is suspended"
            5012 -> "EAP-AKA authentication was rejected"
            else -> "Entitlement server returned HTTP ${response.code}"
        }
        return EntitlementException(message, diameter)
    }

    private data class ChallengeHttp(val packetHex: String, val session: String)

    private companion object {
        const val ACCEPT = "application/vnd.gsma.eap-relay.v1.0+json, text/vnd.wap.connectivity-xml"
        const val EAP_RELAY_MEDIA = "application/vnd.gsma.eap-relay.v1.0+json"
    }
}

private fun cookieValue(setCookie: String?): String? =
    setCookie
        ?.substringBefore(';')
        ?.substringAfter("eap_session=", "")
        ?.takeIf { it.isNotEmpty() }

private fun headerSafe(value: String): String =
    value.replace(Regex("[\\r\\n]"), "").ifBlank { "unknown" }

internal fun jsonStringField(json: String, key: String): String? {
    val match = Regex(""""${Regex.escape(key)}"\s*:\s*"((?:\\.|[^"\\])*)"""")
        .find(json) ?: return null
    return match.groupValues[1]
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
}

private fun jsonQuoted(value: String): String = buildString {
    append('"')
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            else -> append(char)
        }
    }
    append('"')
}

internal fun hexToBytes(hex: String): ByteArray {
    val clean = hex.trim()
    if (clean.length % 2 != 0 || clean.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) {
        throw EntitlementException("EAP packet is not valid hex")
    }
    return ByteArray(clean.length / 2) { index ->
        clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toUIntByte()) }
