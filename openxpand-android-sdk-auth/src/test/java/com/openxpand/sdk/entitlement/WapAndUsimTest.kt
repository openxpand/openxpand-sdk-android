package com.openxpand.sdk.entitlement

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WapAndUsimTest {

    @Test
    fun `reads temporary token parms in either attribute order`() {
        val xml = """
            <?xml version="1.0"?>
            <wap-provisioningdoc version="1.1">
              <characteristic type="APPLICATION">
                <parm name="AppID" value="ap2014"/>
                <parm value="opaque-token" name="TemporaryToken"/>
                <parm name="TemporaryTokenExpiry" value="2026-10-13T13:00:00Z"/>
              </characteristic>
            </wap-provisioningdoc>
        """.trimIndent()

        val parsed = WapProvisioningDoc.temporaryToken(xml)
        assertEquals("opaque-token", parsed.token)
        assertEquals("2026-10-13T13:00:00Z", parsed.expiresAt)
    }

    @Test
    fun `parses a successful USIM authenticate TLV`() {
        val res = hexToBytes("0102030405060708")
        val ck = ByteArray(16) { 0x11 }
        val ik = ByteArray(16) { 0x22 }
        val inner = byteArrayOf(res.size.toByte()) + res +
            byteArrayOf(ck.size.toByte()) + ck +
            byteArrayOf(ik.size.toByte()) + ik
        val tlv = byteArrayOf(0xDB.toByte(), inner.size.toByte()) + inner + byteArrayOf(0x90.toByte(), 0x00)
        val parsed = parseUsimResponse(tlv) as UsimAuthResult.Success
        assertArrayEquals(res, parsed.res)
    }

    @Test
    fun `parses a USIM sync failure`() {
        val auts = hexToBytes("112233445566778899aabbccdd")
        val tlv = byteArrayOf(0xDC.toByte(), auts.size.toByte()) + auts
        val parsed = parseUsimResponse(tlv) as UsimAuthResult.SyncFailure
        assertArrayEquals(auts, parsed.auts)
    }
}
