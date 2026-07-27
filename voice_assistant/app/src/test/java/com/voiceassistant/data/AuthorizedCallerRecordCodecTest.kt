package com.voiceassistant.data

import com.voiceassistant.bridge.PartnerScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthorizedCallerRecordCodecTest {
    @Test
    fun roundTripPreservesCallerExpiryAndScopes() {
        val caller = AuthorizedCaller(
            packageName = "com.partner.example",
            certificateSha256 = "AA:BB",
            scopes = setOf(PartnerScope.TRANSCRIBE, PartnerScope.PROGRESS_CALLBACK),
            expiresAtMs = 1234L,
        )

        assertEquals(caller, AuthorizedCallerRecordCodec.decode(AuthorizedCallerRecordCodec.encode(caller)))
    }

    @Test
    fun malformedRecordIsRejected() {
        assertNull(AuthorizedCallerRecordCodec.decode("com.partner|AA:BB|not-a-time|transcribe"))
        assertNull(AuthorizedCallerRecordCodec.decode("com.partner|AA:BB|1|unknown_scope"))
    }
}
