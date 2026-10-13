package com.openxpand.sdk.entitlement

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabEapAkaTest {

    private val imsi = "722340000000001"
    private val rand = hexToBytes("00112233445566778899aabbccddeeff")
    private val autn = hexToBytes("aabbccddeeff00112233445566778899")
    private val res = hexToBytes("0102030405060708")
    private val challenge = hexToBytes(
        "01010044170100000105000000112233445566778899aabbccddeeff" +
            "02050000aabbccddeeff00112233445566778899" +
            "0b050000d4a1a935cb2369ee36156f51ce931791"
    )
    private val response = hexToBytes(
        "0201002817010000030300400102030405060708" +
            "0b050000e25ae9b9c3c34cf87ec13d441dc1c0c4"
    )

    @Test
    fun `root NAI uses a leading zero and a three digit MNC`() {
        assertEquals(
            "0722340000000001@nai.epc.mnc034.mcc722.3gppnetwork.org",
            LabEapAka.rootNai(imsi, "722", "34")
        )
    }

    @Test
    fun `root NAI rejects an IMSI from another network`() {
        try {
            LabEapAka.rootNai(imsi, "722", "07")
            throw AssertionError("expected EntitlementException")
        } catch (e: EntitlementException) {
            assertTrue(e.message!!.contains("IMSI"))
        }
    }

    @Test
    fun `challenge parse and MAC match the lab AAA vector`() {
        val parsed = LabEapAka.parseChallenge(challenge)
        assertEquals(1, parsed.identifier)
        assertArrayEquals(rand, parsed.rand)
        assertArrayEquals(autn, parsed.autn)
        assertTrue(LabEapAka.challengeMacValid(challenge, imsi, res))
        assertFalse(LabEapAka.challengeMacValid(challenge, imsi, hexToBytes("ffffffffffffffff")))
    }

    @Test
    fun `response bytes match the lab AAA vector`() {
        assertArrayEquals(response, LabEapAka.buildResponse(1, imsi, rand, res))
        assertEquals(
            "f0b3e8b69fd1ef50fccd905867e4a65c",
            LabEapAka.kAut(imsi, rand, res).toHex()
        )
    }

    @Test
    fun `AT_RES is padded to a four byte boundary`() {
        val odd = hexToBytes("0102030405")
        val packet = LabEapAka.buildResponse(7, imsi, rand, odd)
        assertEquals(2, packet[0].toUIntByte())
        assertEquals(7, packet[1].toUIntByte())
        assertEquals(3, packet[8].toUIntByte())
        assertEquals(3, packet[9].toUIntByte())
        assertEquals(0, packet.size % 4)
    }
}
