package com.voiceassistant.data

import com.voiceassistant.bridge.PartnerProfile
import com.voiceassistant.bridge.PartnerScope
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

internal data class PartnerCallerIdentity(
    val packageName: String,
    val certificateSha256: String,
)

internal data class PartnerSession(
    val id: String,
    val caller: PartnerCallerIdentity,
    val profile: PartnerProfile,
    val scopes: Set<PartnerScope>,
    val expiresAtMs: Long,
)

internal class PartnerSessionRegistry(
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private val sessions = ConcurrentHashMap<String, PartnerSession>()

    fun create(
        caller: PartnerCallerIdentity,
        profile: PartnerProfile,
        scopes: Set<PartnerScope>,
        nowMs: Long,
        ttlMs: Long,
    ): PartnerSession {
        val session = PartnerSession(
            id = idFactory(),
            caller = caller,
            profile = profile,
            scopes = scopes,
            expiresAtMs = nowMs + ttlMs.coerceAtLeast(1L),
        )
        sessions[session.id] = session
        return session
    }

    fun find(id: String, caller: PartnerCallerIdentity, nowMs: Long): PartnerSession? {
        val session = sessions[id] ?: return null
        if (session.expiresAtMs <= nowMs) {
            sessions.remove(id, session)
            return null
        }
        return session.takeIf { it.caller == caller }
    }
}
