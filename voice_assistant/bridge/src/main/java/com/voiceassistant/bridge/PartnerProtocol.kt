package com.voiceassistant.bridge

const val PARTNER_PROTOCOL_VERSION = 2

enum class PartnerScope(val wireValue: String) {
    TRANSCRIBE("transcribe"),
    BACKGROUND_TRANSCRIBE("background_transcribe"),
    PROGRESS_CALLBACK("progress_callback"),
    AUDIO_RETURN("audio_return"),
    DOMAIN_PROFILE("domain_profile");

    companion object {
        fun fromWireValue(value: String): PartnerScope? = entries.firstOrNull {
            it.wireValue == value.trim().lowercase()
        }
    }
}

enum class PartnerProfile(val wireValue: String) {
    GENERIC("generic"),
    PLANKTON_V1("plankton-v1");

    companion object {
        fun fromWireValue(value: String): PartnerProfile? = entries.firstOrNull {
            it.wireValue == value.trim().lowercase()
        }
    }
}

enum class PartnerErrorCode(val wireValue: String) {
    AUTHORIZATION_REQUIRED("AUTHORIZATION_REQUIRED"),
    TOKEN_EXPIRED("TOKEN_EXPIRED"),
    AUDIO_UNREADABLE("AUDIO_UNREADABLE"),
    MODEL_UNAVAILABLE("MODEL_UNAVAILABLE"),
    RATE_LIMITED("RATE_LIMITED"),
    CANCELLED("CANCELLED"),
    INTERNAL_ERROR("INTERNAL_ERROR"),
}

enum class PartnerHelloValidation {
    Valid,
    Malformed,
    UnsupportedVersion,
}

object PartnerProtocol {
    fun parseScopes(rawScopes: Collection<String>): Set<PartnerScope>? {
        if (rawScopes.isEmpty()) return null
        val parsed = rawScopes.map { PartnerScope.fromWireValue(it) ?: return null }.toSet()
        return parsed.takeIf { it.size == rawScopes.size }
    }

    fun validateHello(
        protocolVersion: Int,
        profileId: String,
        clientNonce: String,
        requestedScopes: Set<PartnerScope>,
    ): PartnerHelloValidation {
        if (protocolVersion != PARTNER_PROTOCOL_VERSION) return PartnerHelloValidation.UnsupportedVersion
        if (PartnerProfile.fromWireValue(profileId) == null || clientNonce.trim().length !in 16..128 || requestedScopes.isEmpty()) {
            return PartnerHelloValidation.Malformed
        }
        return PartnerHelloValidation.Valid
    }
}
