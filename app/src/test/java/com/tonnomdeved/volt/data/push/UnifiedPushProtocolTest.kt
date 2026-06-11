package com.tonnomdeved.volt.data.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnifiedPushProtocolTest {

    // --- endpointFor ---

    @Test fun `endpoint derives https from wss and strips ws path`() {
        val ep = UnifiedPushProtocol.endpointFor("wss://push.example.org/ws", "tok123")
        assertEquals("https://push.example.org/UP?token=tok123", ep)
    }

    @Test fun `endpoint derives http from ws`() {
        val ep = UnifiedPushProtocol.endpointFor("ws://10.0.0.1:8080", "abc")
        assertEquals("http://10.0.0.1:8080/UP?token=abc", ep)
    }

    @Test fun `endpoint strips trailing slash`() {
        val ep = UnifiedPushProtocol.endpointFor("wss://push.example.org/", "t")
        assertEquals("https://push.example.org/UP?token=t", ep)
    }

    @Test fun `endpoint null for non websocket url`() {
        assertNull(UnifiedPushProtocol.endpointFor("https://example.org", "t"))
        assertNull(UnifiedPushProtocol.endpointFor("", "t"))
    }

    @Test fun `endpoint null for blank token`() {
        assertNull(UnifiedPushProtocol.endpointFor("wss://push.example.org/ws", ""))
    }

    // --- parseWirePayload ---

    @Test fun `parse splits on first colon`() {
        val r = UnifiedPushProtocol.parseWirePayload("tok123:hello world")
        assertEquals("tok123", r?.first)
        assertEquals("hello world", r?.second)
    }

    @Test fun `parse keeps colons in message`() {
        val r = UnifiedPushProtocol.parseWirePayload("tok:a:b:c")
        assertEquals("tok", r?.first)
        assertEquals("a:b:c", r?.second)
    }

    @Test fun `parse rejects malformed payloads`() {
        assertNull(UnifiedPushProtocol.parseWirePayload("nocolon"))
        assertNull(UnifiedPushProtocol.parseWirePayload(":nokey"))
        assertNull(UnifiedPushProtocol.parseWirePayload("nomsg:"))
        assertNull(UnifiedPushProtocol.parseWirePayload(""))
    }
}
