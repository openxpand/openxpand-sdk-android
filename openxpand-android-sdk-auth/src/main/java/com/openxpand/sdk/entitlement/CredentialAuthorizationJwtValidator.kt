package com.openxpand.sdk.entitlement

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.util.Base64URL
import org.json.JSONObject
import java.security.MessageDigest
import java.security.cert.X509Certificate

internal data class ValidatedCredentialAuthorization(
    val nonce: String,
    val consentDataBase64Url: String,
    val encryptionKey: ECKey,
    val state: String?,
    val vctHint: String?
)

internal object CredentialAuthorizationJwtValidator {

    fun validate(
        credentialAuthorizationJwt: String,
        expectedNonce: String,
        allowedRootFingerprints: Set<String>
    ): ValidatedCredentialAuthorization {
        val parts = credentialAuthorizationJwt.split(".")
        if (parts.size != 3) {
            throw EntitlementException("credential_authorization_jwt must be a compact JWS")
        }
        val header = JSONObject(decodeJsonPart(parts[0]))
        if (header.optString("alg") != "ES256") {
            throw EntitlementException("credential_authorization_jwt must use ES256")
        }
        val x5c = header.optJSONArray("x5c")
            ?: throw EntitlementException("credential_authorization_jwt must include x5c")
        if (x5c.length() < 1) {
            throw EntitlementException("credential_authorization_jwt x5c chain is empty")
        }
        val chain = (0 until x5c.length()).map { index ->
            parseDerCertificate(java.util.Base64.getDecoder().decode(x5c.getString(index)))
        }
        verifyChain(chain, allowedRootFingerprints)
        val leaf = chain.first()
        val signed = com.nimbusds.jose.JWSObject.parse(credentialAuthorizationJwt)
        if (!signed.verify(ECDSAVerifier(leaf.publicKey as java.security.interfaces.ECPublicKey))) {
            throw EntitlementException("credential_authorization_jwt signature is not valid")
        }
        val claims = JSONObject(signed.payload.toString())
        val nonce = claims.optString("nonce")
        if (nonce != expectedNonce) {
            throw EntitlementException("credential_authorization_jwt nonce does not match the initiate request")
        }
        val consentData = claims.optString("consent_data")
        if (consentData.isNullOrEmpty()) {
            throw EntitlementException("credential_authorization_jwt is missing consent_data")
        }
        val supported = claims.optJSONArray("encrypted_response_enc_values_supported")
            ?: throw EntitlementException("credential_authorization_jwt must declare encrypted_response_enc_values_supported")
        if (!supported.toString().contains("A128GCM")) {
            throw EntitlementException("credential_authorization_jwt must support A128GCM")
        }
        val keys = claims.optJSONObject("jwks")?.optJSONArray("keys")
            ?: throw EntitlementException("credential_authorization_jwt must carry jwks.keys")
        if (keys.length() != 1) {
            throw EntitlementException("credential_authorization_jwt must carry one ephemeral encryption key")
        }
        val encKeyJson = keys.getJSONObject(0)
        if (encKeyJson.optString("crv") != "P-256" || encKeyJson.optString("use") != "enc") {
            throw EntitlementException("encryption key must be P-256 with use=enc")
        }
        val encJwk = ECKey.parse(encKeyJson.toString())
        return ValidatedCredentialAuthorization(
            nonce = nonce,
            consentDataBase64Url = consentData,
            encryptionKey = encJwk,
            state = claims.optString("state").takeIf { it.isNotEmpty() },
            vctHint = null
        )
    }

    private fun verifyChain(chain: List<X509Certificate>, allowedRootFingerprints: Set<String>) {
        val root = chain.last()
        val rootFp = MessageDigest.getInstance("SHA-256")
            .digest(root.encoded)
            .joinToString("") { "%02x".format(it) }
        if (allowedRootFingerprints.isNotEmpty() && rootFp !in allowedRootFingerprints) {
            throw EntitlementException("aggregator root certificate is not whitelisted for this carrier")
        }
        for (index in 0 until chain.size - 1) {
            chain[index].verify(chain[index + 1].publicKey)
        }
        root.verify(root.publicKey)
    }

    private fun parseDerCertificate(der: ByteArray): X509Certificate {
        val factory = java.security.cert.CertificateFactory.getInstance("X.509")
        return factory.generateCertificate(der.inputStream()) as X509Certificate
    }

    private fun decodeJsonPart(part: String): String {
        val padded = part + "=".repeat((4 - part.length % 4) % 4)
        return String(Base64URL.from(padded).decode(), Charsets.UTF_8)
    }
}
