package com.openxpand.sdk.entitlement

import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class OperatorTokenSdJwtBuilderTest {

    @Test
    fun `builds a two-part SD-JWT with Telephony issuer and encrypted temp token`() {
        val enc = ECKeyGenerator(Curve.P_256).keyID("enc-1").generate()
        val validated = ValidatedCredentialAuthorization(
            nonce = "nonce-abc",
            consentDataBase64Url = "eyJjb25zZW50X3RleHQiOiJ0ZXN0In0",
            encryptionKey = enc,
            state = "state-1",
            vctHint = null
        )
        val sdJwt = OperatorTokenSdJwtBuilder.build(
            temporaryToken = "temp-token-value",
            vct = "number-verification/device-phone-number/ts43",
            validated = validated,
            carrierHint = "72234",
            androidCarrierHint = 1839,
            apkAudience = LabOperatorTokenDefaults.APK_AUDIENCE
        )
        val parts = sdJwt.split("~")
        assertEquals(2, parts.size)
        val issuer = com.nimbusds.jose.JWSObject.parse(parts[0])
        val issuerClaims = JSONObject(issuer.payload.toString())
        assertEquals("Telephony", issuerClaims.getString("iss"))
        assertTrue(issuer.verify(ECDSAVerifier(LabTelephonyIdentity.signingKey.toECPublicKey())))
        val binding = com.nimbusds.jose.JWSObject.parse(parts[1])
        val bindingClaims = JSONObject(binding.payload.toString())
        assertEquals(validated.nonce, bindingClaims.getString("nonce"))
        assertEquals(LabOperatorTokenDefaults.APK_AUDIENCE, bindingClaims.getString("aud"))
        assertTrue(bindingClaims.has("encrypted_credential"))
    }
}
