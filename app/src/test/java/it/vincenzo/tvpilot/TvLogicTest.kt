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

    @Test fun voice() {
        assertEquals(it.vincenzo.tvpilot.tv.Voice.Command.VolumeSet(20), it.vincenzo.tvpilot.tv.Voice.parse("Volume 20"))
        assertEquals(it.vincenzo.tvpilot.tv.Voice.Command.VolumeSet(20), it.vincenzo.tvpilot.tv.Voice.parse("volume a venti"))
        assertEquals(it.vincenzo.tvpilot.tv.Voice.Command.VolumeUp(3), it.vincenzo.tvpilot.tv.Voice.parse("alza il volume"))
        assertEquals(it.vincenzo.tvpilot.tv.Voice.Command.VolumeDown(5), it.vincenzo.tvpilot.tv.Voice.parse("abbassa il volume di 5"))
        assertEquals(it.vincenzo.tvpilot.tv.Voice.Command.Sleep(30), it.vincenzo.tvpilot.tv.Voice.parse("spegni tra 30 minuti"))
        assertEquals(it.vincenzo.tvpilot.tv.Voice.Command.Sleep(30), it.vincenzo.tvpilot.tv.Voice.parse("spegni tra mezz'ora"))
        assertEquals(it.vincenzo.tvpilot.tv.Voice.Command.Sleep(60), it.vincenzo.tvpilot.tv.Voice.parse("spegni tra un'ora"))
        assertEquals(it.vincenzo.tvpilot.tv.Voice.Command.PowerOff, it.vincenzo.tvpilot.tv.Voice.parse("Spegni la TV"))
        assertEquals(it.vincenzo.tvpilot.tv.Voice.Command.Channel("5"), it.vincenzo.tvpilot.tv.Voice.parse("canale 5"))
        assertEquals(it.vincenzo.tvpilot.tv.Voice.Command.Input(2), it.vincenzo.tvpilot.tv.Voice.parse("HDMI 2"))
        assertEquals(it.vincenzo.tvpilot.tv.Voice.Command.OpenApp("youtube"), it.vincenzo.tvpilot.tv.Voice.parse("Apri YouTube"))
        assertEquals(it.vincenzo.tvpilot.tv.Voice.Command.Mute, it.vincenzo.tvpilot.tv.Voice.parse("muto"))
        assertEquals(it.vincenzo.tvpilot.tv.Voice.Command.Unmute, it.vincenzo.tvpilot.tv.Voice.parse("togli il muto"))
        val apps = listOf(
            it.vincenzo.tvpilot.tv.Tv.AppItem("netflix", "Netflix", "", true, false),
            it.vincenzo.tvpilot.tv.Tv.AppItem("amazon", "Prime Video", "", true, false),
        )
        assertEquals("amazon", it.vincenzo.tvpilot.tv.Voice.bestApp("prime", apps)?.id)
        assertEquals("netflix", it.vincenzo.tvpilot.tv.Voice.bestApp("netflix", apps)?.id)
    }

    @Test fun youtube() {
        assertEquals("dQw4w9WgXcQ", it.vincenzo.tvpilot.tv.Tv.youtubeId("https://youtu.be/dQw4w9WgXcQ?si=abc"))
        assertEquals("dQw4w9WgXcQ", it.vincenzo.tvpilot.tv.Tv.youtubeId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10"))
        assertEquals("abcdefghijk", it.vincenzo.tvpilot.tv.Tv.youtubeId("https://youtube.com/shorts/abcdefghijk"))
        assertNull(it.vincenzo.tvpilot.tv.Tv.youtubeId("https://example.com"))
    }

    @Test fun manifest() {
        val withKey = Manifest.registration("abc")
        assertEquals("abc", withKey.getString("client-key"))
        assertEquals("PROMPT", withKey.getString("pairingType"))
        assertFalse(Manifest.registration(null).has("client-key"))
        assertTrue(withKey.getJSONObject("manifest").getJSONArray("signatures").getJSONObject(0).getString("signature").endsWith("oa7NQnAtw=="))
    }
}
