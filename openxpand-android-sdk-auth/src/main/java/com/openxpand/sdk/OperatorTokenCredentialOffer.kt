package com.openxpand.sdk

/**
 * DCQL credential object returned by the app backend after `POST /api/device/nv-initiate`.
 */
data class OperatorTokenCredentialOffer(
    val id: String,
    val format: String,
    val vct: String,
    val credentialAuthorizationJwt: String,
    val resolvedCaso: String? = null
) {
    companion object {
        fun fromOpenId4VpCredentialsJson(body: org.json.JSONObject): OperatorTokenCredentialOffer {
            return fromInitiateJson(body)
        }

        fun fromInitiateJson(body: org.json.JSONObject): OperatorTokenCredentialOffer {
            val format = body.optString("format")
            val meta = body.optJSONObject("meta")
                ?: throw IllegalArgumentException("initiate response is missing meta")
            val vcts = meta.optJSONArray("vct_values")
                ?: throw IllegalArgumentException("initiate response is missing vct_values")
            if (vcts.length() != 1) {
                throw IllegalArgumentException("expected exactly one vct")
            }
            val caj = meta.optString("credential_authorization_jwt")
            if (caj.isNullOrEmpty()) {
                throw IllegalArgumentException("initiate response is missing credential_authorization_jwt")
            }
            return OperatorTokenCredentialOffer(
                id = body.optString("id"),
                format = format,
                vct = vcts.getString(0),
                credentialAuthorizationJwt = caj,
                resolvedCaso = body.optString("resolved_caso").takeIf { it.isNotEmpty() }
            )
        }
    }
}
