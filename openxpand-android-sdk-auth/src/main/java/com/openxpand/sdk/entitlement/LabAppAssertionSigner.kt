package com.openxpand.sdk.entitlement

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.util.Base64URL
import org.json.JSONObject
import java.util.UUID

/**
 * Signs RFC 7523 JWT Bearer assertions for Keycloak `POST /openid4vp/token`.
 */
internal object LabAppAssertionSigner {

    private val signingKey: ECKey by lazy {
        ECKey.Builder(
            Curve.P_256,
            Base64URL("1Fek2e4wK55Im3ptVPDFvQcdoYqu9yLbjGnuX849Trs"),
            Base64URL("qsdJlteFq-mDTm3DD--3v8BCj6v62duU77Pib-lgQRA")
        )
            .d(Base64URL("efRfiKIMysfI5IOFCsJZs9q91wqcetUFSflnBerRkVs"))
            .keyID("lab-app-assertion")
            .build()
    }

    fun sign(
        clientId: String,
        tokenUrl: String,
        scope: String,
        sdJwt: String
    ): String {
        val now = System.currentTimeMillis() / 1000L
        val payload = JSONObject()
            .put("iss", clientId)
            .put("sub", "vp_token:$sdJwt")
            .put("aud", tokenUrl)
            .put("scope", scope)
            .put("iat", now)
            .put("exp", now + 120)
            .put("jti", UUID.randomUUID().toString())
        val header = JWSHeader.Builder(JWSAlgorithm.ES256)
            .keyID(signingKey.keyID)
            .build()
        val jws = JWSObject(header, Payload(payload.toString()))
        jws.sign(ECDSASigner(signingKey))
        return jws.serialize()
    }
}
