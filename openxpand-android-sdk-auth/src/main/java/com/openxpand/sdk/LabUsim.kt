package com.openxpand.sdk

/**
 * Lab stand-in for a USIM. Used only by test apps that cannot call
 * `TelephonyManager.getIccAuthentication`.
 *
 * [res] returns the EAP-AKA RES for the given challenge, or `null` when the
 * card reports a sequence sync failure.
 */
fun interface LabUsim {
    fun res(rand: ByteArray, autn: ByteArray): ByteArray?
}
