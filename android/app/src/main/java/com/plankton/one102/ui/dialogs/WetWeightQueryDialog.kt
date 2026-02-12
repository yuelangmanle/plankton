package com.plankton.one102.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.plankton.one102.data.api.ChatCompletionClient
import com.plankton.one102.domain.Settings
import com.plankton.one102.domain.buildWetWeightPrompt
import com.plankton.one102.ui.components.AiRichText
import com.plankton.one102.ui.components.GlassCard
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

private fun extractNumbers(text: String): List<Double> {
    val regex = Regex("-?\\d+(?:\\.\\d+)?(?:e-?\\d+)?", RegexOption.IGNORE_CASE)
    return regex.findAll(text).mapNotNull { it.value.toDoubleOrNull() }.filter { it.isFinite() }.toList()
}

private fun extractFinalMg(text: String): Double? {
    val line = text.lineSequence().firstOrNull { it.trim().startsWith("FINAL_MG_PER_INDIVIDUAL:", ignoreCase = true) } ?: return null
    val raw = line.substringAfter(":", "").trim()
    if (raw.equals("UNKNOWN", ignoreCase = true)) return null
    return raw.toDoubleOrNull()
}

@Composable
fun WetWeightQueryDialog(
    settings: Settings,
    nameCn: String,
    nameLatin: String?,
    onClose: () -> Unit,
    onApply: (Double) -> Unit,
    onSaveToLibrary: ((Double) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val client = remember { ChatCompletionClient() }
    val prompt = remember(nameCn, nameLatin) { buildWetWeightPrompt(nameCn, nameLatin) }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var api1Text by remember { mutableStateOf("") }
    var api2Text by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf("") }
    var alsoSave by remember { mutableStateOf(false) }

    fun useValue(mg: Double) {
        if (alsoSave) onSaveToLibrary?.invoke(mg)
        onApply(mg)
    }

    val api1Final = extractFinalMg(api1Text)
    val api2Final = extractFinalMg(api2Text)
    val api1Nums = extractNumbers(api1Text)
    val api2Nums = extractNumbers(api2Text)

    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("关闭") } },
        title = { Text("查湿重（双 API）") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("物种：$nameCn", style = MaterialTheme.typography.titleMedium)

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("提示词（会要求回答有依据并提供来源）", style = MaterialTheme.typography.titleSmall)
                        Text(prompt, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        enabled = !loading && settings.api1.baseUrl.isNotBlank() && settings.api2.baseUrl.isNotBlank(),
                        onClick = {
                            loading = true
                            error = null
                            scope.launch {
                                try {
                                    val a1 = async { client.call(settings.api1, prompt) }
                                    val a2 = async { client.call(settings.api2, prompt) }
                                    api1Text = a1.await()
                                    api2Text = a2.await()
                                } catch (e: Exception) {
                                    error = e.message ?: e.toString()
                                } finally {
                                    loading = false
                                }
                            }
                        },
                    ) {
                        if (loading) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                            Text("查询中…")
                        } else {
                            Text("同时调用 API 1 & 2")
                        }
                    }
                }

                if (error != null) {
                    Text("错误：$error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                if (onSaveToLibrary != null) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Checkbox(checked = alsoSave, onCheckedChange = { alsoSave = it })
                            Text("使用时同时保存到“自定义湿重库”", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                ApiResultGlassCard(
                    title = settings.api1.name.ifBlank { "API 1" },
                    text = api1Text,
                    finalMg = api1Final,
                    candidates = api1Nums,
                    onApply = ::useValue,
                )
                ApiResultGlassCard(
                    title = settings.api2.name.ifBlank { "API 2" },
                    text = api2Text,
                    finalMg = api2Final,
                    candidates = api2Nums,
                    onApply = ::useValue,
                )

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("手动输入（mg/个）", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = manual,
                                onValueChange = { manual = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = { Text("例如：0.0005") },
                            )
                            Button(
                                onClick = {
                                    val v = manual.trim().toDoubleOrNull()
                                    if (v != null && v.isFinite() && v > 0) useValue(v)
                                },
                            ) { Text("使用") }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun ApiResultGlassCard(
    title: String,
    text: String,
    finalMg: Double?,
    candidates: List<Double>,
    onApply: (Double) -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (text.isBlank()) {
                Text("（暂无）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            } else {
                AiRichText(text = text, style = MaterialTheme.typography.bodySmall)
            }

            val picks = buildList {
                if (finalMg != null) add(finalMg)
                addAll(candidates)
            }.distinct().take(6)

            if (picks.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (n in picks) {
                        TextButton(onClick = { onApply(n) }) { Text("用 $n") }
                    }
                }
            }
        }
    }
}
