package com.openxpand.sdk.internal

import org.json.JSONObject

internal fun JSONObject.optionalRefreshToken(): String? =
    when {
        !has("refresh_token") || isNull("refresh_token") -> null
        else -> getString("refresh_token").takeIf { it.isNotEmpty() }
    }
