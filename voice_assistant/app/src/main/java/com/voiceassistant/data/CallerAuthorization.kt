package com.voiceassistant.data

import com.voiceassistant.bridge.PartnerScope

internal data class AuthorizedCaller(
    val packageName: String,
    val certificateSha256: String,
    val scopes: Set<PartnerScope>,
    val expiresAtMs: Long,
)

internal enum class CallerDecision {
    Allowed,
    NeedsUserApproval,
    DeniedSignatureMismatch,
    DeniedMalformedRequest,
    DeniedScope,
    DeniedRateLimit,
}

internal object CallerAuthorization {
    fun authorize(
        packageName: String,
        certificateSha256: String,
        requestedScopes: Set<PartnerScope>,
        allowed: Set<AuthorizedCaller>,
        nowMs: Long,
    ): CallerDecision {
        val normalizedPackage = packageName.trim()
        val normalizedCertificate = normalizeCertificate(certificateSha256)
        if (normalizedPackage.isEmpty() || normalizedCertificate.isEmpty() || requestedScopes.isEmpty()) {
            return CallerDecision.DeniedMalformedRequest
        }

        val packageEntries = allowed.filter { it.packageName.trim() == normalizedPackage }
        if (packageEntries.isNotEmpty() && packageEntries.none {
                normalizeCertificate(it.certificateSha256) == normalizedCertificate
            }
        ) {
            return CallerDecision.DeniedSignatureMismatch
        }

        val activeEntries = packageEntries.filter {
            normalizeCertificate(it.certificateSha256) == normalizedCertificate && it.expiresAtMs >= nowMs
        }
        if (activeEntries.isEmpty()) return CallerDecision.NeedsUserApproval
        if (activeEntries.any { it.scopes.containsAll(requestedScopes) }) return CallerDecision.Allowed
        return CallerDecision.DeniedScope
    }

    private fun normalizeCertificate(value: String): String = value
        .filter(Char::isLetterOrDigit)
        .uppercase()
}
