package com.openxpand.sdk.entitlement

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Base64

internal class TelephonySubscriberIdentity(
    private val context: Context,
    private val subscriptionId: Int?
) : SubscriberIdentitySource {
    override fun imsi(): String {
        val imsi = try {
            @Suppress("DEPRECATION")
            telephony().subscriberId
        } catch (e: SecurityException) {
            throw EntitlementException(
                "Carrier privileges are required to read the IMSI",
                cause = e
            )
        }
        if (imsi.isNullOrBlank()) {
            throw EntitlementException("The SIM did not return an IMSI. Carrier privileges are required")
        }
        return imsi
    }

    private fun telephony(): TelephonyManager = telephonyManager(context, subscriptionId)
}

internal class TelephonyUsimAuthenticator(
    private val context: Context,
    private val subscriptionId: Int?
) : UsimAuthenticator {
    override fun authenticate(rand: ByteArray, autn: ByteArray): UsimAuthResult {
        val payload = ByteArray(2 + rand.size + autn.size)
        payload[0] = rand.size.toByte()
        rand.copyInto(payload, 1)
        payload[1 + rand.size] = autn.size.toByte()
        autn.copyInto(payload, 2 + rand.size)
        val encoded = Base64.encodeToString(payload, Base64.NO_WRAP)
        val response = try {
            telephonyManager(context, subscriptionId).getIccAuthentication(
                TelephonyManager.APPTYPE_USIM,
                TelephonyManager.AUTHTYPE_EAP_AKA,
                encoded
            )
        } catch (e: SecurityException) {
            throw EntitlementException(
                "Carrier privileges are required to authenticate the USIM",
                cause = e
            )
        } ?: throw EntitlementException("USIM returned no authentication response")
        return parseUsimResponse(Base64.decode(response, Base64.DEFAULT))
    }
}

private fun telephonyManager(context: Context, subscriptionId: Int?): TelephonyManager {
    val manager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    return if (subscriptionId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        manager.createForSubscriptionId(subscriptionId)
    } else {
        manager
    }
}
