package com.voiceassistant.data

import com.voiceassistant.bridge.PartnerScope
import org.junit.Assert.assertEquals
import org.junit.Test

class CallerAuthorizationTest {
    @Test
    fun unapprovedPackageNeedsApprovalBeforeBackgroundTranscription() {
        val result = CallerAuthorization.authorize(
            packageName = "com.untrusted.client",
            certificateSha256 = "AA:BB",
            requestedScopes = setOf(PartnerScope.BACKGROUND_TRANSCRIBE),
            allowed = emptySet(),
            nowMs = 1L,
        )

        assertEquals(CallerDecision.NeedsUserApproval, result)
    }

    @Test
    fun knownPackageWithDifferentCertificateIsDenied() {
        val allowed = setOf(
            AuthorizedCaller(
                packageName = "com.plankton.one102",
                certificateSha256 = "11:22",
                scopes = setOf(PartnerScope.TRANSCRIBE),
                expiresAtMs = Long.MAX_VALUE,
            ),
        )

        val result = CallerAuthorization.authorize(
            packageName = "com.plankton.one102",
            certificateSha256 = "AA:BB",
            requestedScopes = setOf(PartnerScope.TRANSCRIBE),
            allowed = allowed,
            nowMs = 1L,
        )

        assertEquals(CallerDecision.DeniedSignatureMismatch, result)
    }

    @Test
    fun expiredCallerNeedsNewApproval() {
        val allowed = setOf(
            AuthorizedCaller(
                packageName = "com.partner",
                certificateSha256 = "AA:BB",
                scopes = setOf(PartnerScope.TRANSCRIBE),
                expiresAtMs = 10L,
            ),
        )

        val result = CallerAuthorization.authorize(
            packageName = "com.partner",
            certificateSha256 = "AA:BB",
            requestedScopes = setOf(PartnerScope.TRANSCRIBE),
            allowed = allowed,
            nowMs = 11L,
        )

        assertEquals(CallerDecision.NeedsUserApproval, result)
    }

    @Test
    fun callerCannotEscalateBeyondGrantedScopes() {
        val allowed = setOf(
            AuthorizedCaller(
                packageName = "com.partner",
                certificateSha256 = "AA:BB",
                scopes = setOf(PartnerScope.TRANSCRIBE),
                expiresAtMs = Long.MAX_VALUE,
            ),
        )

        val result = CallerAuthorization.authorize(
            packageName = "com.partner",
            certificateSha256 = "AA:BB",
            requestedScopes = setOf(PartnerScope.TRANSCRIBE, PartnerScope.AUDIO_RETURN),
            allowed = allowed,
            nowMs = 1L,
        )

        assertEquals(CallerDecision.DeniedScope, result)
    }
}
