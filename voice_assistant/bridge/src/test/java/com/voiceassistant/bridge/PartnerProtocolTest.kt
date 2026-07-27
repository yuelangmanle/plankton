package com.voiceassistant.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PartnerProtocolTest {
    @Test
    fun parsesOnlyDeclaredScopes() {
        assertEquals(
            setOf(PartnerScope.TRANSCRIBE, PartnerScope.BACKGROUND_TRANSCRIBE),
            PartnerProtocol.parseScopes(listOf("transcribe", "background_transcribe")),
        )
        assertNull(PartnerProtocol.parseScopes(listOf("transcribe", "root_access")))
    }

    @Test
    fun validatesKnownProfileAndNonceForVersionTwoHello() {
        assertEquals(
            PartnerHelloValidation.Valid,
            PartnerProtocol.validateHello(
                protocolVersion = 2,
                profileId = PartnerProfile.PLANKTON_V1.wireValue,
                clientNonce = "6e13d28f-56d4-42fb-8d89-94acb7db118e",
                requestedScopes = setOf(PartnerScope.TRANSCRIBE),
            ),
        )
        assertEquals(
            PartnerHelloValidation.Malformed,
            PartnerProtocol.validateHello(2, "custom-script", "", setOf(PartnerScope.TRANSCRIBE)),
        )
    }
}
