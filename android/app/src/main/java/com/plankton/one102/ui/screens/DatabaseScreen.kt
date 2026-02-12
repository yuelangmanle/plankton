package com.plankton.one102.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plankton.one102.PlanktonApplication
import com.plankton.one102.data.AppJson
import com.plankton.one102.data.api.ChatCompletionClient
import com.plankton.one102.domain.ImportedSpecies
import com.plankton.one102.domain.ImportTemplateType
import com.plankton.one102.domain.Settings
import com.plankton.one102.domain.Taxonomy
import com.plankton.one102.domain.SpeciesDbItem
import com.plankton.one102.domain.DEFAULT_WET_WEIGHT_LIBRARY_ID
import com.plankton.one102.domain.DEFAULT_WET_WEIGHT_LIBRARY_NAME
import com.plankton.one102.domain.normalizeLvl1Name
import com.plankton.one102.importer.buildExcelPreviewForAi
import com.plankton.one102.importer.importSpeciesFromExcel
import com.plankton.one102.ui.DatabaseViewModel
import com.plankton.one102.ui.MainViewModel
import com.plankton.one102.ui.components.GlassBackground
import com.plankton.one102.ui.components.GlassCard
import com.plankton.one102.ui.dialogs.TaxonomyQueryDialog
import com.plankton.one102.ui.dialogs.WetWeightQueryDialog
import com.plankton.one102.ui.theme.GlassWhite
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID

private const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

private enum class ImportMode { Strict, Ai }

private fun Context.copyToClipboard(label: String, text: String) {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

@Serializable
private data class AiImportedSpecies(
    val nameCn: String,
    val nameLatin: String? = null,
    @SerialName("wetWeightMg") val wetWeightMg: Double? = null,
    val lvl1: String? = null,
    val lvl2: String? = null,
    val lvl3: String? = null,
    val lvl4: String? = null,
    val lvl5: String? = null,
)

private fun collectTaxonomyCounts(items: List<SpeciesDbItem>): List<Map<String, Int>> {
    val counts = List(5) { linkedMapOf<String, Int>() }
    for (item in items) {
        val t = item.taxonomy ?: continue
        val values = listOf(t.lvl1, t.lvl2, t.lvl3, t.lvl4, t.lvl5)
        for (i in values.indices) {
            val raw = values[i].trim()
            if (raw.isBlank()) continue
            val key = if (i == 0) normalizeLvl1Name(raw) else raw
            if (key.isBlank()) continue
            val map = counts[i]
            map[key] = (map[key] ?: 0) + 1
        }
    }
    return counts
}

private fun suggestTaxonomy(values: Map<String, Int>, query: String, limit: Int = 6): List<String> {
    if (values.isEmpty()) return emptyList()
    val q = query.trim()
    val qLower = q.lowercase()
    val filtered = if (q.isBlank()) {
        values.entries
    } else {
        values.entries.filter { it.key.contains(q) || it.key.lowercase().contains(qLower) }
    }
    return filtered
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key }
        .distinct()
        .take(limit)
}

private fun buildAiImportPrompt(preview: String): String {
    return """
        你是一个 Excel 表格解析器。请把下面“表格预览”的每一行解析成物种条目（忽略空行/表头/汇总行）。

        目标字段（JSON 每条必须包含这些 key；没有值用 null）：
        - nameCn: 中文名（必须有）
        - nameLatin: 拉丁名（可空）
        - wetWeightMg: 平均湿重（单位 mg/个，可空；科学计数法也要能解析）
        - lvl1: 大类（原生动物 / 轮虫类 / 枝角类 / 桡足类，可空）
        - lvl2: 纲（可空）
        - lvl3: 目（可空）
        - lvl4: 科（可空）
        - lvl5: 属（可空）

        要求：
        1) 仅根据预览内容提取，不要推测不存在的数据。
        2) nameCn 为空的行请忽略。
        3) wetWeightMg 必须是 JSON number 或 null（绝对不要出现 “\"wetWeightMg\": ” 后面空着的情况）。
        3) 最后一行严格输出（只输出一行，不要加代码块）：
           FINAL_IMPORT_JSON: <JSON数组 或 UNKNOWN>

        表格预览（制表符分列）：
        $preview
    """.trimIndent()
}

private fun extractFinalImportJson(text: String): String? {
    val line = text.lineSequence().lastOrNull { it.trim().startsWith("FINAL_IMPORT_JSON:", ignoreCase = true) } ?: return null
    var raw = line.substringAfter(":", "").trim()
    if (raw.equals("UNKNOWN", ignoreCase = true)) return null
    raw = raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    return raw.takeIf { it.isNotBlank() }
}

private fun repairAiJsonArray(json: String): String {
    var s = json.trim()
    // Replace empty numeric fields like: "wetWeightMg": , or "wetWeightMg":}
    s = s.replace(Regex("(\"wetWeightMg\"\\s*:\\s*)(?=[,}\\]])"), "$1null")
    // Replace trailing "wetWeightMg": at end-of-string
    s = s.replace(Regex("(\"wetWeightMg\"\\s*:\\s*)$"), "$1null")
    return s
}

private fun extractJsonObjects(text: String): List<String> {
    val s = text.trim()
    val out = mutableListOf<String>()
    var i = s.indexOf('{')
    while (i >= 0 && i < s.length) {
        var depth = 0
        var inString = false
        var escape = false
        val start = i
        var j = i
        while (j < s.length) {
            val ch = s[j]
            if (inString) {
                if (escape) {
                    escape = false
                } else if (ch == '\\') {
                    escape = true
                } else if (ch == '"') {
                    inString = false
                }
            } else {
                when (ch) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            out += s.substring(start, j + 1)
                            i = s.indexOf('{', j + 1)
                            break
                        }
                    }
                }
            }
            j++
        }
        if (j >= s.length) break
    }
    return out
}

private fun parseAiImportedSpecies(json: String): List<AiImportedSpecies> {
    val repaired = repairAiJsonArray(json)
    val direct = runCatching { AppJson.decodeFromString(ListSerializer(AiImportedSpecies.serializer()), repaired) }.getOrNull()
    if (direct != null) return direct

    // Fallback: salvage by decoding each object independently (ignore broken tail objects).
    val objs = extractJsonObjects(repaired)
    val out = mutableListOf<AiImportedSpecies>()
    for (obj in objs) {
        val fixed = repairAiJsonArray(obj)
        val parsed = runCatching { AppJson.decodeFromString(AiImportedSpecies.serializer(), fixed) }.getOrNull() ?: continue
        out += parsed
    }
    return out
}

private fun toImported(list: List<AiImportedSpecies>): List<ImportedSpecies> {
    return list.mapNotNull { row ->
        val nameCn = row.nameCn.trim()
        if (nameCn.isBlank()) return@mapNotNull null
        val wet = row.wetWeightMg?.takeIf { it.isFinite() && it > 0 }
        val taxonomy = listOf(row.lvl1, row.lvl2, row.lvl3, row.lvl4, row.lvl5).any { !it.isNullOrBlank() }.let { has ->
            if (!has) null else Taxonomy(
                lvl1 = normalizeLvl1Name(row.lvl1.orEmpty()),
                lvl2 = row.lvl2?.trim().orEmpty(),
                lvl3 = row.lvl3?.trim().orEmpty(),
                lvl4 = row.lvl4?.trim().orEmpty(),
                lvl5 = row.lvl5?.trim().orEmpty(),
            )
        }
        ImportedSpecies(
            nameCn = nameCn,
            nameLatin = row.nameLatin?.trim().takeIf { !it.isNullOrBlank() },
            wetWeightMg = wet,
            taxonomy = taxonomy,
        )
    }
}

private fun matchQuery(item: SpeciesDbItem, query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim()
    val qLower = q.lowercase()
    if (item.nameCn.contains(q)) return true
    if ((item.nameLatin ?: "").lowercase().contains(qLower)) return true
    val t = item.taxonomy
    if (t != null) {
        if (t.lvl1.contains(q) || t.lvl2.contains(q) || t.lvl3.contains(q) || t.lvl4.contains(q) || t.lvl5.contains(q)) return true
    }
    return false
}

private fun formatMg(v: Double): String {
    if (!v.isFinite() || v <= 0) return "—"
    val abs = kotlin.math.abs(v)
    return if (abs in 0.001..1000.0) {
        String.format("%.6f", v).trimEnd('0').trimEnd('.')
    } else {
        String.format("%.3g", v)
    }
}

@Composable
fun DatabaseScreen(
    mainViewModel: MainViewModel,
    dbViewModel: DatabaseViewModel,
    padding: PaddingValues,
    onOpenTree: () -> Unit,
    onOpenMindMap: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { ChatCompletionClient() }
    val app = context.applicationContext as PlanktonApplication
    val aliasRepo = app.aliasRepository
    val wetWeightRepo = app.wetWeightRepository

    val settings by mainViewModel.settings.collectAsStateWithLifecycle()
    val items by dbViewModel.items.collectAsStateWithLifecycle()
    val libraries by wetWeightRepo.observeLibraries().collectAsStateWithLifecycle(emptyList())

    val activeLibraryName = libraries.firstOrNull {
        it.id == settings.activeWetWeightLibraryId.trim().ifBlank { DEFAULT_WET_WEIGHT_LIBRARY_ID }
    }?.name ?: DEFAULT_WET_WEIGHT_LIBRARY_NAME
    val importTemplates = remember(settings.importTemplates) {
        settings.importTemplates.filter { it.type == ImportTemplateType.SpeciesLibrary }
    }
    val activeImportTemplate = remember(importTemplates, settings.activeImportTemplateId) {
        importTemplates.firstOrNull { it.id == settings.activeImportTemplateId } ?: importTemplates.firstOrNull()
    }

    val dialogColor = if (settings.glassEffectEnabled) GlassWhite else MaterialTheme.colorScheme.surface
    val dialogShape = RoundedCornerShape(24.dp)

    var q by remember { mutableStateOf("") }
    var editor by remember { mutableStateOf<SpeciesDbItem?>(null) }
    var importExpanded by rememberSaveable { mutableStateOf(false) }
    var importBusy by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var importMode by remember { mutableStateOf(ImportMode.Strict) }
    var templateMenuOpen by remember { mutableStateOf(false) }
    var importAlsoOverwriteTaxonomy by remember { mutableStateOf(false) }
    var libraryMenuOpen by remember { mutableStateOf(false) }
    var libraryCreateOpen by remember { mutableStateOf(false) }
    var libraryNameDraft by remember { mutableStateOf("") }
    var libraryError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<SpeciesDbItem?>(null) }
    var lastAiRaw by remember { mutableStateOf<String?>(null) }
    var confirmClearImportedWetWeights by remember { mutableStateOf(false) }

    fun hasApi(settings: Settings): Boolean = settings.api1.baseUrl.isNotBlank() && settings.api1.model.isNotBlank()

    suspend fun applyImported(rows: List<ImportedSpecies>): String {
        val importBatchId = UUID.randomUUID().toString()
        val existingByName = items.associateBy { it.nameCn }
        val importedNames = rows.map { it.nameCn.trim() }.filter { it.isNotBlank() }.toSet()
        val newNames = importedNames - existingByName.keys

        var wetWrites = 0
        var taxWrites = 0
        var aliasWrites = 0
        var wetBuiltinSkips = 0

        for (r in rows) {
            val nameCn = r.nameCn.trim()
            if (nameCn.isBlank()) continue
            val latin = r.nameLatin?.trim().takeIf { !it.isNullOrBlank() }
            val taxonomy = r.taxonomy?.let {
                it.copy(lvl1 = normalizeLvl1Name(it.lvl1))
            }

            val existing = existingByName[nameCn]
            val hasBuiltinWet = existing?.sources?.builtinWetWeight == true

            val wet = r.wetWeightMg
            if (wet != null) {
                if (hasBuiltinWet) {
                    wetBuiltinSkips += 1
                } else {
                    val group = taxonomy?.lvl1?.trim().takeIf { !it.isNullOrBlank() }
                    val sub = taxonomy?.lvl4?.trim().takeIf { !it.isNullOrBlank() }
                    dbViewModel.upsertWetWeightSync(nameCn, latin, wet, group, sub, importBatchId)
                    wetWrites += 1
                }
            }
            val hasBuiltinTax = existing?.sources?.builtinTaxonomy == true
            val hasAnyTax = taxonomy != null && listOf(taxonomy.lvl1, taxonomy.lvl2, taxonomy.lvl3, taxonomy.lvl4, taxonomy.lvl5).any { it.isNotBlank() }
            val shouldWriteTax = (latin != null || hasAnyTax) && (importAlsoOverwriteTaxonomy || !hasBuiltinTax)
            if (shouldWriteTax) {
                dbViewModel.upsertTaxonomyOverrideSync(nameCn, latin, taxonomy ?: Taxonomy())
                taxWrites += 1
            }

            if (r.aliases.isNotEmpty()) {
                for (alias in r.aliases) {
                    val key = alias.trim()
                    if (key.isBlank() || key == nameCn) continue
                    runCatching { aliasRepo.upsert(key, nameCn) }.onSuccess { aliasWrites += 1 }
                }
            }
        }

        val newCountPart = if (newNames.isNotEmpty()) "；新增物种 ${newNames.size} 条" else ""
        val wetSkipPart = if (wetBuiltinSkips > 0) "；内置湿重保留 $wetBuiltinSkips 条" else ""
        return "读取 ${rows.size} 行；写入湿重 $wetWrites 条；写入分类/拉丁名 $taxWrites 条；写入别名 $aliasWrites 条$wetSkipPart$newCountPart（导入批次：${importBatchId.take(8)}）"
    }

    fun importStrict(uri: Uri) {
        importBusy = true
        importMessage = null
        lastAiRaw = null
        scope.launch {
            val res = runCatching {
                val rows = importSpeciesFromExcel(context.contentResolver, uri, activeImportTemplate)
                applyImported(rows)
            }
            val msg = res.fold(
                onSuccess = { "导入完成：$it" },
                onFailure = { "导入失败：${it.message ?: it.toString()}" },
            )
            importMessage = msg
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            importBusy = false
        }
    }

    fun importWithAi(uri: Uri) {
        importBusy = true
        importMessage = null
        lastAiRaw = null
        scope.launch {
            val res = runCatching {
                if (!hasApi(settings)) throw IllegalArgumentException("未配置 API1 Base URL / Model（请先到设置里填写）")
                val preview = buildExcelPreviewForAi(context.contentResolver, uri)
                if (preview.isBlank()) throw IllegalArgumentException("未读取到可识别的预览内容")
                val prompt = buildAiImportPrompt(preview)
                val raw = client.call(settings.api1, prompt, maxTokens = 1200)
                lastAiRaw = raw
                val json = extractFinalImportJson(raw) ?: throw IllegalArgumentException("AI 未输出 FINAL_IMPORT_JSON（可改用规范表头导入）")
                val list = parseAiImportedSpecies(json)
                if (list.isEmpty()) throw IllegalArgumentException("AI 输出无法解析为有效条目（可改用规范表头导入）")
                val rows = toImported(list)
                if (rows.isEmpty()) throw IllegalArgumentException("AI 识别结果为空（请检查表格内容/表头）")
                applyImported(rows)
            }
            val msg = res.fold(
                onSuccess = { "AI 导入完成：$it" },
                onFailure = { "AI 导入失败：${it.message ?: it.toString()}" },
            )
            importMessage = msg
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            importBusy = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (importBusy) return@rememberLauncherForActivityResult
        when (importMode) {
            ImportMode.Strict -> importStrict(uri)
            ImportMode.Ai -> importWithAi(uri)
        }
    }

    val filtered = remember(items, q) { items.filter { matchQuery(it, q) } }

    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("数据库", style = MaterialTheme.typography.titleLarge)
        Text(
            "展示本机内置“分类库”与“平均湿重库”，并支持用“自定义覆盖”进行增删改。这里的修改会影响后续自动补齐与导出计算。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("导入/清理（Excel）", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { importExpanded = !importExpanded }) {
                        Icon(
                            imageVector = if (importExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = if (importExpanded) "收起" else "展开",
                        )
                    }
                }

                if (!importExpanded) {
                    val hint = if (settings.aiUiHidden) {
                        "已收起。展开后可导入 Excel、清空“导入的平均湿重”。"
                    } else {
                        "已收起。展开后可导入 Excel、AI 识别导入、清空“导入的平均湿重”。"
                    }
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    if (importMessage != null) {
                        Text(
                            importMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        )
                    }
                } else {
                    Text(
                        "支持两种：1) 规范表（推荐表头：物种名称、拉丁名、平均湿重(mg/个)、大类、纲、目、科、属、别名；也支持无表头列序：1=中文名 2=拉丁名 3=平均湿重 4-8=大类/纲/目/科/属 9=别名；别名可多列或用“/、,，；换行”等分隔多个别名；重复名称自动合并；与内置湿重同名时默认保留内置值）；2) 直接导入“表三”格式（3列：种类/拉丁名/平均湿重，含层级标题）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "导入湿重写入：",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                        Box {
                            OutlinedButton(onClick = { libraryMenuOpen = true }) { Text(activeLibraryName) }
                            DropdownMenu(expanded = libraryMenuOpen, onDismissRequest = { libraryMenuOpen = false }) {
                                libraries.forEach { lib ->
                                    DropdownMenuItem(
                                        text = { Text(lib.name) },
                                        onClick = {
                                            libraryMenuOpen = false
                                            mainViewModel.saveSettings(settings.copy(activeWetWeightLibraryId = lib.id))
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("＋ 新建湿重库") },
                                    onClick = {
                                        libraryMenuOpen = false
                                        libraryCreateOpen = true
                                        libraryNameDraft = ""
                                        libraryError = null
                                    },
                                )
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "导入映射模板：",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                        Box {
                            OutlinedButton(
                                onClick = { templateMenuOpen = true },
                                enabled = importTemplates.isNotEmpty(),
                            ) {
                                Text(activeImportTemplate?.name ?: "未配置模板")
                            }
                            DropdownMenu(
                                expanded = templateMenuOpen,
                                onDismissRequest = { templateMenuOpen = false },
                            ) {
                                importTemplates.forEach { template ->
                                    DropdownMenuItem(
                                        text = { Text(template.name) },
                                        onClick = {
                                            templateMenuOpen = false
                                            mainViewModel.saveSettings(settings.copy(activeImportTemplateId = template.id))
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (activeImportTemplate != null) {
                        Text(
                            "当前映射：${activeImportTemplate.mapping.ifBlank { "${activeImportTemplate.nameCnColumn}=中文名, ${activeImportTemplate.wetWeightColumn}=湿重" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            enabled = !importBusy,
                            onClick = {
                                importMode = ImportMode.Strict
                                importLauncher.launch(arrayOf(MIME_XLSX))
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            if (importBusy && importMode == ImportMode.Strict) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                                Text("导入中…")
                            } else {
                                Text("规范导入")
                            }
                        }
                        if (!settings.aiUiHidden) {
                            OutlinedButton(
                                enabled = !importBusy && hasApi(settings),
                                onClick = {
                                    importMode = ImportMode.Ai
                                    importLauncher.launch(arrayOf(MIME_XLSX))
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("AI 识别导入") }
                        }
                    }
                    OutlinedButton(
                        enabled = !importBusy,
                        onClick = { confirmClearImportedWetWeights = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("一键清空：所有导入的平均湿重") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = importAlsoOverwriteTaxonomy, onCheckedChange = { importAlsoOverwriteTaxonomy = it })
                        Text(
                            "导入时覆盖分类（谨慎：可能覆盖表1的更完整分类）",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (importMessage != null) {
                        Text(
                            importMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        )
                    }
                    if (lastAiRaw != null && importMessage?.startsWith("AI 导入失败") == true) {
                        OutlinedButton(
                            onClick = {
                                editor = null
                                confirmDelete = null
                                Toast.makeText(context, "已复制 AI 原始输出到剪贴板", Toast.LENGTH_SHORT).show()
                                context.copyToClipboard("AI 原始输出", lastAiRaw.orEmpty())
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("复制 AI 原始输出（用于排查）") }
                    }
                    if (!settings.aiUiHidden && !hasApi(settings)) {
                        Text(
                            "提示：如需 AI 识别导入，请先到设置填写 API1 的 Base URL 与 Model。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("搜索") },
                placeholder = { Text("中文名/拉丁名/分类…") },
            )
            Button(
                onClick = { editor = SpeciesDbItem(nameCn = "") },
            ) { Text("新增") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onOpenTree, modifier = Modifier.weight(1f)) { Text("分类路线图") }
            OutlinedButton(onClick = onOpenMindMap, modifier = Modifier.weight(1f)) { Text("思维导图") }
        }
        Text(
            "共 ${filtered.size} 条",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (filtered.isEmpty()) {
            Text("暂无条目。", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(filtered, key = { it.nameCn }) { item ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = 0.dp,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    item.nameCn,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                val t = item.taxonomy
                                val taxLine = if (t != null && listOf(t.lvl1, t.lvl2, t.lvl3, t.lvl4, t.lvl5).any { it.isNotBlank() }) {
                                    listOf(
                                        t.lvl1.takeIf { it.isNotBlank() },
                                        t.lvl2.takeIf { it.isNotBlank() },
                                        t.lvl3.takeIf { it.isNotBlank() },
                                        t.lvl4.takeIf { it.isNotBlank() },
                                        t.lvl5.takeIf { it.isNotBlank() },
                                    ).filterNotNull().joinToString(" · ")
                                } else {
                                    "（未填写分类）"
                                }
                                Text(
                                    taxLine,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                if (!item.nameLatin.isNullOrBlank()) {
                                    Text(
                                        item.nameLatin.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.widthIn(min = 120.dp),
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    item.wetWeightMg?.let { "${formatMg(it)} mg/个" } ?: "未查到湿重",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (item.wetWeightMg == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    IconButton(onClick = { editor = item }) {
                                        Icon(imageVector = Icons.Outlined.Edit, contentDescription = "编辑")
                                    }
                                    IconButton(
                                        enabled = item.sources.customTaxonomy || item.sources.customWetWeight,
                                        onClick = { confirmDelete = item },
                                    ) {
                                        Icon(imageVector = Icons.Outlined.Delete, contentDescription = "删除自定义")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editor != null) {
        val editing = editor!!
        val isNew = editing.nameCn.isBlank()
        var nameCn by remember(editing.nameCn) { mutableStateOf(editing.nameCn) }
        var latin by remember(editing.nameCn) { mutableStateOf(editing.nameLatin ?: "") }
        var wet by remember(editing.nameCn) { mutableStateOf(editing.wetWeightMg?.toString() ?: "") }
        var lvl1 by remember(editing.nameCn) { mutableStateOf(editing.taxonomy?.lvl1 ?: "") }
        var lvl2 by remember(editing.nameCn) { mutableStateOf(editing.taxonomy?.lvl2 ?: "") }
        var lvl3 by remember(editing.nameCn) { mutableStateOf(editing.taxonomy?.lvl3 ?: "") }
        var lvl4 by remember(editing.nameCn) { mutableStateOf(editing.taxonomy?.lvl4 ?: "") }
        var lvl5 by remember(editing.nameCn) { mutableStateOf(editing.taxonomy?.lvl5 ?: "") }
        var error by remember(editing.nameCn) { mutableStateOf<String?>(null) }
        var taxQuery by remember(editing.nameCn) { mutableStateOf(false) }
        var wetQuery by remember(editing.nameCn) { mutableStateOf(false) }

        val configuration = LocalConfiguration.current
        val maxDialogHeight = (configuration.screenHeightDp.dp * 0.72f)

        val taxonomyCounts = remember(items) { collectTaxonomyCounts(items) }
        fun suggestForLevel(level: Int, query: String, limit: Int = 6): List<String> {
            if (level == 0) {
                val base = listOf("原生动物", "轮虫类", "枝角类", "桡足类")
                val queryText = query.trim()
                val matches = if (queryText.isBlank()) base else base.filter { it.contains(queryText) }
                return matches.take(limit)
            }
            val values = taxonomyCounts.getOrNull(level) ?: emptyMap()
            return suggestTaxonomy(values, query, limit)
        }

        @Composable
        fun TaxonomySuggestionRow(values: List<String>, onPick: (String) -> Unit) {
            if (values.isEmpty()) return
            Text(
                "关键词联想：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (v in values) {
                    OutlinedButton(onClick = { onPick(v) }) { Text(v) }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { editor = null },
            title = { Text(if (isNew) "新增条目" else "编辑条目") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = maxDialogHeight).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

                    OutlinedTextField(
                        value = nameCn,
                        onValueChange = { nameCn = it },
                        enabled = isNew,
                        label = { Text("中文名（唯一）") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = latin,
                        onValueChange = { latin = it },
                        label = { Text("拉丁名（可空）") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = wet,
                        onValueChange = { wet = it },
                        label = { Text("平均湿重（mg/个，可空）") },
                        singleLine = true,
                        placeholder = { Text("例如：0.0005") },
                    )
                    if (!settings.aiUiHidden) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { taxQuery = true },
                                modifier = Modifier.weight(1f),
                            ) { Text("双 API 查分类") }
                            OutlinedButton(
                                onClick = { wetQuery = true },
                                modifier = Modifier.weight(1f),
                            ) { Text("双 API 查湿重") }
                        }
                    }
                    OutlinedTextField(
                        value = lvl1,
                        onValueChange = { lvl1 = it },
                        label = { Text("大类（原生动物/轮虫类/枝角类/桡足类）") },
                        singleLine = true,
                    )
                    val lvl1Suggestions = remember(lvl1, taxonomyCounts) { suggestForLevel(0, lvl1) }
                    TaxonomySuggestionRow(lvl1Suggestions) { lvl1 = normalizeLvl1Name(it) }
                    OutlinedTextField(value = lvl2, onValueChange = { lvl2 = it }, label = { Text("纲") }, singleLine = true)
                    val lvl2Suggestions = remember(lvl2, taxonomyCounts) { if (lvl2.isBlank()) emptyList() else suggestForLevel(1, lvl2) }
                    TaxonomySuggestionRow(lvl2Suggestions) { lvl2 = it }
                    OutlinedTextField(value = lvl3, onValueChange = { lvl3 = it }, label = { Text("目") }, singleLine = true)
                    val lvl3Suggestions = remember(lvl3, taxonomyCounts) { if (lvl3.isBlank()) emptyList() else suggestForLevel(2, lvl3) }
                    TaxonomySuggestionRow(lvl3Suggestions) { lvl3 = it }
                    OutlinedTextField(value = lvl4, onValueChange = { lvl4 = it }, label = { Text("科") }, singleLine = true)
                    val lvl4Suggestions = remember(lvl4, taxonomyCounts) { if (lvl4.isBlank()) emptyList() else suggestForLevel(3, lvl4) }
                    TaxonomySuggestionRow(lvl4Suggestions) { lvl4 = it }
                    OutlinedTextField(value = lvl5, onValueChange = { lvl5 = it }, label = { Text("属") }, singleLine = true)
                    val lvl5Suggestions = remember(lvl5, taxonomyCounts) { if (lvl5.isBlank()) emptyList() else suggestForLevel(4, lvl5) }
                    TaxonomySuggestionRow(lvl5Suggestions) { lvl5 = it }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        error = null
                        val key = nameCn.trim()
                        if (key.isBlank()) {
                            error = "中文名不能为空。"
                            return@TextButton
                        }
                        if (isNew && items.any { it.nameCn == key }) {
                            error = "已存在同名条目：$key"
                            return@TextButton
                        }

                        val latinFixed = latin.trim().ifBlank { null }
                        val wetFixed = wet.trim().takeIf { it.isNotBlank() }?.toDoubleOrNull()
                        if (wetFixed != null && (!wetFixed.isFinite() || wetFixed <= 0)) {
                            error = "平均湿重需为大于 0 的数值（单位 mg/个），或留空。"
                            return@TextButton
                        }

                        val anyTax = listOf(lvl1, lvl2, lvl3, lvl4, lvl5).any { it.trim().isNotBlank() }
                        val taxonomy = if (anyTax) Taxonomy(
                            lvl1 = lvl1.trim(),
                            lvl2 = lvl2.trim(),
                            lvl3 = lvl3.trim(),
                            lvl4 = lvl4.trim(),
                            lvl5 = lvl5.trim(),
                        ) else null

                        if (latinFixed != null || taxonomy != null) {
                            dbViewModel.upsertTaxonomyOverride(key, latinFixed, taxonomy ?: Taxonomy())
                        } else if (!isNew) {
                            dbViewModel.deleteTaxonomyOverride(key)
                        }

                        if (wetFixed != null) {
                            val group = taxonomy?.lvl1?.trim().takeIf { !it.isNullOrBlank() }
                            val sub = taxonomy?.lvl4?.trim().takeIf { !it.isNullOrBlank() }
                            dbViewModel.upsertWetWeight(key, latinFixed, wetFixed, group, sub)
                        }

                        editor = null
                    },
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editor = null }) { Text("取消") } },
            containerColor = dialogColor,
            shape = dialogShape,
        )

        if (taxQuery && !settings.aiUiHidden) {
            TaxonomyQueryDialog(
                settings = settings,
                nameCn = nameCn.trim(),
                nameLatin = latin.trim().ifBlank { null },
                onClose = { taxQuery = false },
                onApply = { taxonomy ->
                    lvl1 = taxonomy.lvl1
                    lvl2 = taxonomy.lvl2
                    lvl3 = taxonomy.lvl3
                    lvl4 = taxonomy.lvl4
                    lvl5 = taxonomy.lvl5
                    taxQuery = false
                },
            )
        }

        if (wetQuery && !settings.aiUiHidden) {
            WetWeightQueryDialog(
                settings = settings,
                nameCn = nameCn.trim(),
                nameLatin = latin.trim().ifBlank { null },
                onClose = { wetQuery = false },
                onApply = { mg ->
                    wet = mg.toString()
                    wetQuery = false
                },
                onSaveToLibrary = { mg ->
                    val key = nameCn.trim()
                    if (key.isBlank()) return@WetWeightQueryDialog
                    val taxonomy = Taxonomy(
                        lvl1 = normalizeLvl1Name(lvl1),
                        lvl2 = lvl2.trim(),
                        lvl3 = lvl3.trim(),
                        lvl4 = lvl4.trim(),
                        lvl5 = lvl5.trim(),
                    )
                    dbViewModel.upsertWetWeight(
                        key,
                        latin.trim().ifBlank { null },
                        mg,
                        taxonomy.lvl1.takeIf { it.isNotBlank() },
                        taxonomy.lvl4.takeIf { it.isNotBlank() },
                    )
                },
            )
        }
    }

    if (confirmDelete != null) {
        val item = confirmDelete!!
        var delTax by remember(item.nameCn) { mutableStateOf(item.sources.customTaxonomy) }
        var delWet by remember(item.nameCn) { mutableStateOf(item.sources.customWetWeight) }

        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除自定义覆盖") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "内置数据不会被删除；这里只删除你自己保存的“自定义覆盖”。\n\n物种：${item.nameCn}",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    if (item.sources.customTaxonomy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = delTax, onCheckedChange = { delTax = it })
                            Text("删除自定义分类/拉丁名", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (item.sources.customWetWeight) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = delWet, onCheckedChange = { delWet = it })
                            Text("删除自定义湿重", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (!delTax && !delWet) {
                        Text("请至少选择一项。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = delTax || delWet,
                    onClick = {
                        if (delTax) dbViewModel.deleteTaxonomyOverride(item.nameCn)
                        if (delWet) dbViewModel.deleteWetWeightOverride(item.nameCn)
                        confirmDelete = null
                    },
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } },
            containerColor = dialogColor,
            shape = dialogShape,
        )
    }

    if (confirmClearImportedWetWeights) {
        AlertDialog(
            onDismissRequest = { confirmClearImportedWetWeights = false },
            title = { Text("清空导入湿重") },
            text = {
                Text(
                    "将删除当前湿重库里“导入产生”的自定义平均湿重（不会删除内置湿重库数据，也不会删除你手动新增/修改的湿重）。",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearImportedWetWeights = false
                        scope.launch {
                            val n = runCatching { dbViewModel.clearImportedWetWeights() }.getOrElse { -1 }
                            val msg = if (n >= 0) "已清空导入湿重：$n 条" else "清空失败"
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                ) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { confirmClearImportedWetWeights = false }) { Text("取消") } },
            containerColor = dialogColor,
            shape = dialogShape,
        )
    }

    if (libraryCreateOpen) {
        AlertDialog(
            onDismissRequest = { libraryCreateOpen = false },
            title = { Text("新建湿重库") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (libraryError != null) {
                        Text(libraryError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(
                        value = libraryNameDraft,
                        onValueChange = { libraryNameDraft = it },
                        singleLine = true,
                        label = { Text("库名称") },
                        placeholder = { Text("例如：推荐湿重库") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = libraryNameDraft.trim()
                        if (name.isBlank()) {
                            libraryError = "库名称不能为空。"
                            return@TextButton
                        }
                        scope.launch {
                            val created = runCatching { wetWeightRepo.createLibrary(name) }
                            created.onSuccess { lib ->
                                mainViewModel.saveSettings(settings.copy(activeWetWeightLibraryId = lib.id))
                                libraryCreateOpen = false
                            }.onFailure {
                                libraryError = it.message ?: "新建失败"
                            }
                        }
                    },
                ) { Text("创建并切换") }
            },
            dismissButton = { TextButton(onClick = { libraryCreateOpen = false }) { Text("取消") } },
            containerColor = dialogColor,
            shape = dialogShape,
        )
    }
}
}
