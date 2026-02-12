package com.plankton.one102.voiceassistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.plankton.one102.PlanktonApplication

class VoiceAssistantReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? PlanktonApplication ?: return
        val result = readVoiceAssistantResult(intent) ?: return
        val matched = app.voiceAssistantHub.consumeRequest(result.requestId)
        app.voiceAssistantHub.publish(result.copy(requestMatched = matched))
    }
}
