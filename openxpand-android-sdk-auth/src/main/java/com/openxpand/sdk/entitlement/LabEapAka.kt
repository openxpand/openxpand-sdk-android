package com.openxpand.sdk.entitlement

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

/**
 * EAP-AKA (RFC 4187) packet codec matched to the lab AAA in
 * `mobile-identity-toolkit/core/aaa_server`.
 *
 * That AAA does not use the RFC 4187 key derivation. Both sides use
 * `K_aut = SHA-256(IMSI digits || RAND || RES)[:16]`, then HMAC-SHA1 over the
 * EAP packet with AT_MAC set to zeros. CK and IK from the USIM are not used.
 */
internal object LabEapAka {

    data class Challenge(
        val identifier: Int,
        val rand: ByteArray,
        val autn: ByteArray
    )

    fun rootNai(imsi: String, mcc: String, mnc: String): String {
        if (!imsi.all { it.isDigit() } || imsi.length !in 6..15) {
            throw EntitlementException("IMSI must be 6 to 15 digits")
        }
        if (mcc.length != 3 || !mcc.all { it.isDigit() }) {
            throw EntitlementException("MCC must be 3 digits")
        }
        if (mnc.length !in 2..3 || !mnc.all { it.isDigit() }) {
            throw EntitlementException("MNC must be 2 or 3 digits, without domain padding")
        }
        if (!imsi.startsWith(mcc + mnc)) {
            throw EntitlementException("IMSI does not belong to MCC $mcc MNC $mnc")
        }
        val mncDomain = mnc.padStart(3, '0')
        return "0$imsi@nai.epc.mnc$mncDomain.mcc$mcc.3gppnetwork.org"
    }

    fun parseChallenge(packet: ByteArray): Challenge {
        val length = declaredLength(packet)
        if (u8(packet, 0) != 1) {
            throw EntitlementException("Expected an EAP-Request")
        }
        if (u8(packet, 4) != 23 || u8(packet, 5) != 1) {
            throw EntitlementException("Expected an EAP-AKA Challenge")
        }
        var rand: ByteArray? = null
        var autn: ByteArray? = null
        walkAttributes(packet, length) { type, value ->
            when (type) {
                1 -> if (value.size == 16) rand = value
                2 -> if (value.size == 16) autn = value
            }
        }
        val parsedRand = rand
        val parsedAutn = autn
        if (parsedRand == null || parsedAutn == null) {
            throw EntitlementException("EAP-AKA challenge is missing RAND or AUTN")
        }
        return Challenge(u8(packet, 1), parsedRand, parsedAutn)
    }

    fun challengeMacValid(packet: ByteArray, imsi: String, res: ByteArray): Boolean {
        val length = declaredLength(packet)
        val mac = macValue(packet, length) ?: return false
        val zeroed = packet.copyOf(length)
        val macAt = macOffset(packet, length) ?: return false
        for (i in 0 until 16) {
            zeroed[macAt + i] = 0
        }
        val expected = hmacSha1(kAut(imsi, parseChallenge(packet).rand, res), zeroed)
        return mac.contentEquals(expected)
    }

    fun buildResponse(identifier: Int, imsi: String, rand: ByteArray, res: ByteArray): ByteArray {
        val atRes = atRes(res)
        val headerAndMac = 8 + 20
        val eapLen = headerAndMac + atRes.size
        val zeroed = ByteArray(eapLen)
        zeroed[0] = 2
        zeroed[1] = identifier.toByte()
        zeroed[2] = (eapLen ushr 8).toByte()
        zeroed[3] = (eapLen and 0xFF).toByte()
        zeroed[4] = 23
        zeroed[5] = 1
        atRes.copyInto(zeroed, 8)
        val macAt = 8 + atRes.size
        zeroed[macAt] = 11
        zeroed[macAt + 1] = 5
        hmacSha1(kAut(imsi, rand, res), zeroed).copyInto(zeroed, macAt + 4)
        return zeroed
    }

    fun kAut(imsi: String, rand: ByteArray, res: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(imsi.toByteArray(Charsets.UTF_8))
        digest.update(rand)
        digest.update(res)
        return digest.digest().copyOf(16)
    }

    private fun atRes(res: ByteArray): ByteArray {
        val pad = (4 - (res.size % 4)) % 4
        val total = 4 + res.size + pad
        val out = ByteArray(total)
        out[0] = 3
        out[1] = (total / 4).toByte()
        val bits = res.size * 8
        out[2] = (bits ushr 8).toByte()
        out[3] = (bits and 0xFF).toByte()
        res.copyInto(out, 4)
        return out
    }

    private fun declaredLength(packet: ByteArray): Int {
        if (packet.size < 8) {
            throw EntitlementException("EAP packet is too short")
        }
        val length = u16(packet, 2)
        if (length < 8 || length > packet.size) {
            throw EntitlementException("EAP packet length is invalid")
        }
        return length
    }

    private fun macOffset(packet: ByteArray, length: Int): Int? {
        var found: Int? = null
        var index = 8
        while (index + 2 <= length) {
            val type = u8(packet, index)
            val attrLen = u8(packet, index + 1) * 4
            if (attrLen < 4 || index + attrLen > length) {
                throw EntitlementException("Malformed EAP attribute")
            }
            if (type == 11 && attrLen >= 20) {
                found = index + 4
            }
            index += attrLen
        }
        return found
    }

    private fun macValue(packet: ByteArray, length: Int): ByteArray? {
        val offset = macOffset(packet, length) ?: return null
        return packet.copyOfRange(offset, offset + 16)
    }

    private fun walkAttributes(
        packet: ByteArray,
        length: Int,
        onValue: (type: Int, value: ByteArray) -> Unit
    ) {
        var index = 8
        while (index + 2 <= length) {
            val type = u8(packet, index)
            val attrLen = u8(packet, index + 1) * 4
            if (attrLen < 4 || index + attrLen > length) {
                throw EntitlementException("Malformed EAP attribute")
            }
            val valueStart = index + 4
            onValue(type, packet.copyOfRange(valueStart, index + attrLen))
            index += attrLen
        }
    }

    private fun hmacSha1(key: ByteArray, message: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        return mac.doFinal(message).copyOf(16)
    }

    private fun u8(packet: ByteArray, index: Int): Int = packet[index].toUIntByte()

    private fun u16(packet: ByteArray, index: Int): Int =
        (u8(packet, index) shl 8) or u8(packet, index + 1)
}
