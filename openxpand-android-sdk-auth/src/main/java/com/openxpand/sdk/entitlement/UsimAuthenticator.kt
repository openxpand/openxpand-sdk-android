package com.openxpand.sdk.entitlement

internal interface UsimAuthenticator {
    fun authenticate(rand: ByteArray, autn: ByteArray): UsimAuthResult
}

internal sealed class UsimAuthResult {
    data class Success(val res: ByteArray) : UsimAuthResult()
    data class SyncFailure(val auts: ByteArray) : UsimAuthResult()
}

/**
 * Parses a USIM AUTHENTICATE response (3GPP TS 31.102):
 * success is `DB | L | RES length | RES | ...`, sync failure is `DC | L | AUTS`.
 */
internal fun parseUsimResponse(bytes: ByteArray): UsimAuthResult {
    if (bytes.isEmpty()) {
        throw EntitlementException("USIM returned an empty authentication response")
    }
    var data = bytes
    if (data.size >= 2 &&
        data[data.size - 2].toUIntByte() == 0x90 &&
        data[data.size - 1].toUIntByte() == 0x00
    ) {
        data = data.copyOf(data.size - 2)
    }
    if (data.size < 2) {
        throw EntitlementException("USIM authentication response is truncated")
    }
    return when (data[0].toUIntByte()) {
        0xDC -> {
            val autsLen = data[1].toUIntByte()
            if (data.size < 2 + autsLen) {
                throw EntitlementException("USIM sync-failure response is truncated")
            }
            UsimAuthResult.SyncFailure(data.copyOfRange(2, 2 + autsLen))
        }
        0xDB -> {
            val innerLen = data[1].toUIntByte()
            if (data.size < 2 + innerLen || innerLen < 1) {
                throw EntitlementException("USIM authentication response is truncated")
            }
            val resLen = data[2].toUIntByte()
            if (resLen == 0 || data.size < 3 + resLen) {
                throw EntitlementException("USIM RES is missing")
            }
            UsimAuthResult.Success(data.copyOfRange(3, 3 + resLen))
        }
        else -> throw EntitlementException(
            "Unexpected USIM authentication tag 0x${data[0].toUIntByte().toString(16)}"
        )
    }
}

internal fun Byte.toUIntByte(): Int = toInt() and 0xFF
