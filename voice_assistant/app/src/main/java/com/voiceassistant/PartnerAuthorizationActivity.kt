package com.voiceassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.voiceassistant.bridge.PartnerProtocol
import com.voiceassistant.data.AuthorizedCaller
import com.voiceassistant.data.AuthorizedCallerStore
import com.voiceassistant.ui.theme.VoiceAssistantTheme
import kotlinx.coroutines.launch

class PartnerAuthorizationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        val certificate = intent.getStringExtra(EXTRA_CERTIFICATE).orEmpty()
        val scopes = PartnerProtocol.parseScopes(intent.getStringArrayListExtra(EXTRA_SCOPES).orEmpty())
        if (packageName.isBlank() || certificate.isBlank() || scopes == null) { finish(); return }
        setContent {
            VoiceAssistantTheme {
                val scope = rememberCoroutineScope()
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("允许应用使用语音转写？", style = MaterialTheme.typography.titleLarge)
                    Text(packageName, style = MaterialTheme.typography.bodyMedium)
                    Text("能力：${scopes.joinToString { it.wireValue }}", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { scope.launch { grant(packageName, certificate, scopes, 10 * 60 * 1000L); finish() } }, modifier = Modifier.fillMaxWidth()) { Text("仅本次允许") }
                    OutlinedButton(onClick = { scope.launch { grant(packageName, certificate, scopes, 7 * 24 * 60 * 60 * 1000L); finish() } }, modifier = Modifier.fillMaxWidth()) { Text("允许 7 天") }
                    OutlinedButton(onClick = { scope.launch { grant(packageName, certificate, scopes, Long.MAX_VALUE); finish() } }, modifier = Modifier.fillMaxWidth()) { Text("始终允许") }
                    OutlinedButton(onClick = ::finish, modifier = Modifier.fillMaxWidth()) { Text("拒绝") }
                }
            }
        }
    }

    private suspend fun grant(packageName: String, certificate: String, scopes: Set<com.voiceassistant.bridge.PartnerScope>, ttlMs: Long) {
        AuthorizedCallerStore(this).upsert(AuthorizedCaller(packageName, certificate, scopes, if (ttlMs == Long.MAX_VALUE) Long.MAX_VALUE else System.currentTimeMillis() + ttlMs))
    }

    companion object { const val EXTRA_PACKAGE_NAME = "partner_package"; const val EXTRA_CERTIFICATE = "partner_certificate"; const val EXTRA_SCOPES = "partner_scopes"; const val EXTRA_PROFILE_ID = "partner_profile" }
}
