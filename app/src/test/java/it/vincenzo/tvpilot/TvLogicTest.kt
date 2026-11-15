package it.vincenzo.tvpilot

import it.vincenzo.tvpilot.tv.Discovery
import it.vincenzo.tvpilot.tv.Manifest
import it.vincenzo.tvpilot.tv.Risk
import it.vincenzo.tvpilot.tv.Risks
import it.vincenzo.tvpilot.tv.Wol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLogicTest {
    @Test fun magicPacket() {
        val p = Wol.magicPacket("AA:BB:CC:DD:EE:FF")
        assertEquals(102, p.size)
        assertTrue(p.take(6).all { it == 0xFF.toByte() })
        assertEquals(0xAA.toByte(), p[6])
        assertEquals(0xFF.toByte(), p[101])
    }

    @Test(expected = IllegalArgumentException::class) fun badMac() { Wol.macBytes("12:34") }

    @Test fun risks() {
        assertEquals(Risk.HIGH, Risks.of("hotelMode", "enable"))
        assertEquals(Risk.HIGH, Risks.of("option", "storeMode"))
        assertEquals(Risk.LOW, Risks.of("option", "livePlus"))
        assertEquals(Risk.LOW, Risks.of("picture", "backlight"))
        assertEquals(Risk.MEDIUM, Risks.of("option", "animationGuide"))
        assertEquals(Risk.LOW, Risks.of("general", "homePromotion"))
    }

    @Test fun ssdp() {
        val r = "HTTP/1.1 200 OK\r\nLocation: http://192.168.1.20:1234/desc.xml\r\nST: urn:lge-com:service:webos-second-screen:1\r\n\r\n"
        assertEquals("http://192.168.1.20:1234/desc.xml", Discovery.location(r))
        assertNull(Discovery.location("HTTP/1.1 200 OK\r\n\r\n"))
        assertEquals("[LG] webOS TV", Discovery.friendlyName("<root><friendlyName>[LG] webOS TV</friendlyName></root>"))
        assertTrue(Discovery.searchMessage().contains("webos-second-screen"))
    }

    @Test fun manifest() {
        val withKey = Manifest.registration("abc")
        assertEquals("abc", withKey.getString("client-key"))
        assertEquals("PROMPT", withKey.getString("pairingType"))
        assertFalse(Manifest.registration(null).has("client-key"))
        assertTrue(withKey.getJSONObject("manifest").getJSONArray("signatures").getJSONObject(0).getString("signature").endsWith("oa7NQnAtw=="))
    }
}
