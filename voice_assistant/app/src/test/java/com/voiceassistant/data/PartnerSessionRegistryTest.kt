package com.voiceassistant.data

import com.voiceassistant.bridge.PartnerProfile
import com.voiceassistant.bridge.PartnerScope
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PartnerSessionRegistryTest {
    @Test
    fun sessionIsUnavailableToDifferentCertificateAndAfterExpiry() {
        val registry = PartnerSessionRegistry(idFactory = { "session-1" })
        val owner = PartnerCallerIdentity("com.partner", "AA:BB")
        val session = registry.create(owner, PartnerProfile.GENERIC, setOf(PartnerScope.TRANSCRIBE), nowMs = 10L, ttlMs = 100L)

        assertSame(session, registry.find("session-1", owner, nowMs = 11L))
        assertNull(registry.find("session-1", PartnerCallerIdentity("com.partner", "CC:DD"), nowMs = 11L))
        assertNull(registry.find("session-1", owner, nowMs = 110L))
    }
}
