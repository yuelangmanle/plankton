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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.plankton.one102.data.AppJson
import com.plankton.one102.data.api.ChatCompletionClient
import com.plankton.one102.domain.Settings
import com.plankton.one102.domain.Taxonomy
import com.plankton.one102.domain.buildTaxonomyPrompt
import com.plankton.one102.domain.normalizeLvl1Name
import com.plankton.one102.ui.components.AiRichText
import com.plankton.one102.ui.components.GlassCard
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

private fun extractFinalTaxonomy(text: String): Taxonomy? {
    val line = text.lineSequence()
        .lastOrNull { it.trim().startsWith("FINAL_TAXONOMY_JSON:", ignoreCase = true) }
        ?: return null

    var raw = line.substringAfter(":", "").trim()
    if (raw.equals("UNKNOWN", ignoreCase = true)) return null
    raw = raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

    val parsed = runCatching { AppJson.decodeFromString(Taxonomy.serializer(), raw) }.getOrNull() ?: return null
    val lvl1 = normalizeLvl1Name(parsed.lvl1)
    return parsed.copy(lvl1 = lvl1)
}

@Composable
fun TaxonomyQueryDialog(
    settings: Settings,
    nameCn: String,
    nameLatin: String?,
    onClose: () -> Unit,
    onApply: (Taxonomy) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val client = remember { ChatCompletionClient() }
    val prompt = remember(nameCn, nameLatin) { buildTaxonomyPrompt(nameCn, nameLatin) }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var api1Text by remember { mutableStateOf("") }
    var api2Text by remember { mutableStateOf("") }

    val api1Taxonomy = extractFinalTaxonomy(api1Text)
    val api2Taxonomy = extractFinalTaxonomy(api2Text)

    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("关闭") } },
        title = { Text("查分类（双 API）") },
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

                ApiTaxonomyGlassCard(
                    title = settings.api1.name.ifBlank { "API 1" },
                    text = api1Text,
                    taxonomy = api1Taxonomy,
                    onApply = onApply,
                )
                ApiTaxonomyGlassCard(
                    title = settings.api2.name.ifBlank { "API 2" },
                    text = api2Text,
                    taxonomy = api2Taxonomy,
                    onApply = onApply,
                )
            }
        },
    )
}

@Composable
private fun ApiTaxonomyGlassCard(
    title: String,
    text: String,
    taxonomy: Taxonomy?,
    onApply: (Taxonomy) -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (taxonomy != null) {
                Text(
                    listOf(
                        taxonomy.lvl1.takeIf { it.isNotBlank() }?.let { "大类：$it" },
                        taxonomy.lvl2.takeIf { it.isNotBlank() }?.let { "纲：$it" },
                        taxonomy.lvl3.takeIf { it.isNotBlank() }?.let { "目：$it" },
                        taxonomy.lvl4.takeIf { it.isNotBlank() }?.let { "科：$it" },
                        taxonomy.lvl5.takeIf { it.isNotBlank() }?.let { "属：$it" },
                    ).filterNotNull().ifEmpty { listOf("（未解析到结构化分类）") }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { onApply(taxonomy) }) { Text("使用此分类") }
                }
            }

            if (text.isBlank()) {
                Text("（暂无）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            } else {
                AiRichText(text = text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
