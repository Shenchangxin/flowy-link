package com.flowy.link

import org.junit.Assert.assertEquals
import org.junit.Test

class PairingUrlTest {
    @Test
    fun parsesTheSharedVector() {
        val fingerprint = "ab".repeat(32)
        val nonce = "cd".repeat(16)
        val url = "flowy://pair?v=1&p=25818&fp=$fingerprint&n=$nonce&h=192.168.1.20,10.0.0.8&name=Study"
        val parsed = parsePairingUrl(url)
        assertEquals(25818, parsed.port)
        assertEquals(listOf("192.168.1.20", "10.0.0.8"), parsed.addresses)
        assertEquals("Study", parsed.name)
        assertEquals(fingerprint, parsed.fingerprint)
    }

    @Test
    fun rejectsAShortFingerprint() {
        val url = "flowy://pair?v=1&p=1&fp=abcd&n=00112233445566778899aabbccddeeff&h=127.0.0.1&name=a"
        try {
            parsePairingUrl(url)
            error("expected rejection")
        } catch (_: IllegalStateException) {
        }
    }
}
