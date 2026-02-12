package com.plankton.one102.voiceassistant

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.Collections

class VoiceAssistantHub {
    private val pendingIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val _incoming = MutableSharedFlow<VoiceAssistantResult>(extraBufferCapacity = 1)
    val incoming: SharedFlow<VoiceAssistantResult> = _incoming

    fun registerRequest(requestId: String) {
        if (requestId.isBlank()) return
        pendingIds.add(requestId)
    }

    fun consumeRequest(requestId: String): Boolean {
        if (requestId.isBlank()) return false
        return pendingIds.remove(requestId)
    }

    fun cancelRequest(requestId: String) {
        if (requestId.isBlank()) return
        pendingIds.remove(requestId)
    }

    fun publish(result: VoiceAssistantResult) {
        _incoming.tryEmit(result)
    }
}
