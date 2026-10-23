package com.openxpand.sdk.entitlement

import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.JWEHeader
import com.nimbusds.jose.JWEObject
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.ECDHEncrypter
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.util.Base64
import com.nimbusds.jose.util.Base64URL
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant

internal object OperatorTokenSdJwtBuilder {

    private const val CREDENTIAL_TTL_SECONDS = 300

    fun build(
        temporaryToken: String,
        vct: String,
        validated: ValidatedCredentialAuthorization,
        carrierHint: String,
        androidCarrierHint: Int,
        apkAudience: String
    ): String {
        val deviceKey = ECKeyGenerator(Curve.P_256).keyID("device-binding").generate()
        val now = Instant.now().epochSecond
        val issuerJwt = signIssuerJwt(vct, deviceKey, now)
        val sdHash = base64UrlSha256((issuerJwt + "~").toByteArray())
        val consentHash = base64UrlSha256(validated.consentDataBase64Url.toByteArray())
        val encryptedCredential = encryptTemporaryToken(temporaryToken, validated.encryptionKey)
        val bindingPayload = JSONObject()
            .put("iat", now)
            .put("aud", apkAudience)
            .put("nonce", validated.nonce)
            .put("encrypted_credential", encryptedCredential)
            .put("consent_data_hash", consentHash)
            .put("sd_hash", sdHash)
            .put("subscription_hint", 1)
            .put("carrier_hint", carrierHint)
            .put("android_carrier_hint", androidCarrierHint)
        validated.state?.let { bindingPayload.put("state", it) }
        val bindingJwt = signBindingJwt(bindingPayload, deviceKey)
        return "$issuerJwt~$bindingJwt"
    }

    private fun signIssuerJwt(vct: String, deviceKey: ECKey, now: Long): String {
        val cnf = JSONObject().put("jwk", JSONObject(deviceKey.toPublicJWK().toJSONObject()))
        val payload = JSONObject()
            .put("iss", "Telephony")
            .put("vct", vct)
            .put("cnf", cnf)
            .put("exp", now + CREDENTIAL_TTL_SECONDS)
            .put("iat", now)
        val header = JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(JOSEObjectType("dc-authorization+sd-jwt"))
            .x509CertChain(listOf(Base64.encode(LabTelephonyIdentity.certificateDer)))
            .build()
        val jws = JWSObject(header, Payload(payload.toString()))
        jws.sign(ECDSASigner(LabTelephonyIdentity.signingKey))
        return jws.serialize()
    }

    private fun signBindingJwt(payload: JSONObject, deviceKey: ECKey): String {
        val header = JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(com.nimbusds.jose.JOSEObjectType("kb+jwt"))
            .build()
        val jws = JWSObject(header, Payload(payload.toString()))
        jws.sign(ECDSASigner(deviceKey))
        return jws.serialize()
    }

    private fun encryptTemporaryToken(temporaryToken: String, recipient: ECKey): String {
        val plaintext = JSONObject().put("temp_token", temporaryToken).toString()
        val header = JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A128GCM)
            .keyID(recipient.keyID)
            .build()
        val jwe = JWEObject(header, Payload(plaintext))
        jwe.encrypt(ECDHEncrypter(recipient.toPublicJWK()))
        return jwe.serialize()
    }

    private fun base64UrlSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return Base64URL.encode(digest).toString()
    }
}
