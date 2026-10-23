package com.openxpand.sdk.entitlement

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.util.Base64URL
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Fixed Telephony signing identity for the lab SDK (Issuer JWT `iss = Telephony`).
 *
 * Operators must point `TELEPHONY_TRUST_URL` at a document whose `sha256` matches
 * [certificateSha256Hex] (see [trustDocumentJson]).
 */
internal object LabTelephonyIdentity {
    private const val CERT_DER_B64 =
        "MIIBODCB36ADAgECAgEBMAoGCCqGSM49BAMCMCYxJDAiBgNVBAMMG0FuZHJvaWQgVGVsZXBob255IChsYWIgU0RLKTAeFw0yNjEwMTkxNDU1NTdaFw0zNjEwMTcxNDU1NTdaMCYxJDAiBgNVBAMMG0FuZHJvaWQgVGVsZXBob255IChsYWIgU0RLKTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABPRifbDCcEuXB6HG/T0w2SjxenWYHZNack6IIDmt6gicZT8OKnhokYFJWUaEyHP/QkDhmQ+bCXHW5HebI6RgaBIwCgYIKoZIzj0EAwIDSAAwRQIgFCHvH3h2AZS4SZ02ZIWnS7nw3+MyyLggpsC1iUhxEmECIQDzdLTTovzpsDonpL1iyttm2hCfHtDR9DKqh4Okh6g2Cg=="

    val certificateDer: ByteArray =
        java.util.Base64.getDecoder().decode(CERT_DER_B64)

    val certificateSha256Hex: String by lazy {
        MessageDigest.getInstance("SHA-256")
            .digest(certificateDer)
            .joinToString("") { "%02x".format(it) }
    }

    val signingKey: ECKey by lazy {
        ECKey.Builder(
            Curve.P_256,
            Base64URL("9GJ9sMJwS5cHocb9PTDZKPF6dZgdk1pyToggOa3qCJw"),
            Base64URL("ZT8OKnhokYFJWUaEyHP_QkDhmQ-bCXHW5HebI6RgaBI")
        )
            .d(Base64URL("8HvS9R5PfOuihu0huQJ-Iv_-cUjQRqNJPyn6fYcDf_Y"))
            .keyID("lab-telephony-sdk")
            .build()
    }

    val x5cLeafBase64: String = CERT_DER_B64

    fun trustDocumentJson(): String =
        """{"x5c":["$x5cLeafBase64"],"sha256":"$certificateSha256Hex"}"""

    fun leafCertificate(): X509Certificate {
        val factory = CertificateFactory.getInstance("X.509")
        return factory.generateCertificate(certificateDer.inputStream()) as X509Certificate
    }
}
