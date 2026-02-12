package com.plankton.one102.ui.dialogs

import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plankton.one102.data.api.AiBulkAction
import com.plankton.one102.data.api.ChatCompletionClient
import com.plankton.one102.data.api.buildSpeciesTaxonomyAutofillPrompt
import com.plankton.one102.data.api.buildSpeciesWetWeightAutofillPrompt
import com.plankton.one102.data.api.extractAllJsonPayloads
import com.plankton.one102.data.api.extractFinalSpeciesJson
import com.plankton.one102.data.api.estimateQuality
import com.plankton.one102.data.api.parseAiBulkCommand
import com.plankton.one102.data.api.parseAiSpeciesInfo
import com.plankton.one102.domain.ApiConfig
import com.plankton.one102.domain.BatchCountCommand
import com.plankton.one102.domain.NameCorrection
import com.plankton.one102.domain.Point
import com.plankton.one102.domain.Species
import com.plankton.one102.domain.Taxonomy
import com.plankton.one102.domain.TaxonomyRecord
import com.plankton.one102.domain.WetWeightEntry
import com.plankton.one102.domain.WetWeightTaxonomy
import com.plankton.one102.domain.bestCanonicalName
import com.plankton.one102.domain.MergeCountsMode
import com.plankton.one102.domain.mergeDuplicateSpeciesByName
import com.plankton.one102.domain.newId
import com.plankton.one102.domain.normalizeLvl1Name
import com.plankton.one102.domain.parseBatchCountCommands
import com.plankton.one102.domain.parseIntSmart
import com.plankton.one102.domain.resolveSiteAndDepthForPoint
import com.plankton.one102.importer.CountMergeOptions
import com.plankton.one102.importer.mergeCountsFromExcelIntoDataset
import com.plankton.one102.ui.MainViewModel
import com.plankton.one102.ui.components.GlassCard
import com.plankton.one102.voiceassistant.VoiceAssistantContract
import com.plankton.one102.voiceassistant.VoiceAssistantRequest
import com.plankton.one102.voiceassistant.VoiceAssistantResult
import com.plankton.one102.voiceassistant.buildRequestIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.UUID

private const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private const val AUTO_CORRECT_SCORE = 0.95
private const val FUZZY_PENDING_SCORE = 0.85
private const val FUZZY_HINT_SCORE = 0.6
private const val SHORT_NAME_PENDING_SCORE = 0.72
private const val POINT_AUTO_SCORE = 0.92
private const val POINT_PENDING_SCORE = 0.82

private enum class ParseMode(val label: String) {
    Local("本地规则"),
    Api1("API1"),
    Api2("API2"),
}

private enum class ParseTemplate(val label: String) {
    General("综合模板"),
    Counts("计数模板"),
}

private enum class FillSource { Local, Api }

private data class PreviewLine(
    val text: String,
    val kind: Kind = Kind.Normal,
) {
    enum class Kind { Normal, Warn, Error }
}

private data class PendingItem(
    val text: String,
    val reason: String,
)

private data class ParsedResult(
    val actions: List<AiBulkAction>,
    val errors: List<String>,
    val warnings: List<String>,
    val notes: List<String>,
    val unparsed: List<String>,
)

private enum class CorrectionKind { Species, Point }

private data class PendingCorrection(
    val kind: CorrectionKind,
    val raw: String,
    val suggestion: String,
    val reason: String,
    val score: Double?,
)

private sealed interface ResolvedEdit {
    data class AddPoint(val point: Point) : ResolvedEdit
    data class DeletePoint(val pointId: String, val label: String) : ResolvedEdit
    data class RenamePoint(val pointId: String, val label: String, val site: String?, val depthM: Double?) : ResolvedEdit
    data class UpdatePoint(val pointId: String, val vc: Double?, val vo: Double?) : ResolvedEdit

    data class AddSpecies(val nameCn: String) : ResolvedEdit
    data class DeleteSpecies(val speciesId: String, val nameCn: String) : ResolvedEdit
    data class RenameSpecies(val speciesId: String, val nameCn: String) : ResolvedEdit

    data class SetCount(val pointId: String, val speciesNameCn: String, val value: Int) : ResolvedEdit
    data class DeltaCount(val pointId: String, val speciesNameCn: String, val delta: Int) : ResolvedEdit

    data class SetWetWeight(
        val speciesNameCn: String,
        val value: Double,
        val nameLatin: String?,
        val writeToDb: Boolean,
        val onlyIfBlank: Boolean,
    ) : ResolvedEdit

    data class AutofillWetWeight(val speciesNameCn: String, val source: FillSource, val writeToDb: Boolean) : ResolvedEdit

    data class SetTaxonomy(
        val speciesNameCn: String,
        val taxonomy: Taxonomy,
        val nameLatin: String?,
        val writeToDb: Boolean,
        val onlyIfBlank: Boolean,
    ) : ResolvedEdit

    data class AutofillTaxonomy(val speciesNameCn: String, val source: FillSource, val writeToDb: Boolean) : ResolvedEdit
}

private data class IntParseResult(
    val value: Int,
    val rounded: Boolean,
)

private fun normalizePointLabel(label: String): String {
    return label.trim()
        .replace("－", "-")
        .replace("—", "-")
        .replace("–", "-")
        .replace("＿", "_")
        .replace(" ", "")
}

private val POINT_NUMBER_REGEX = Regex("""[\d一二三四五六七八九十百千两零]+""")

private fun extractPointNumberCandidates(raw: String): List<Int> {
    return POINT_NUMBER_REGEX.findAll(raw)
        .map { parseIntSmart(it.value) }
        .filter { it > 0 }
        .toList()
}

private fun normalizePointToken(raw: String): String {
    return normalizePointLabel(raw)
        .replace("采样点", "")
        .replace("点位", "")
        .replace("点", "")
}

private fun suggestPointLabel(raw: String): String? {
    val cleaned = normalizePointToken(raw)
    if (cleaned.isBlank()) return null
    if (cleaned.contains('-') || cleaned.contains('.') || cleaned.contains('_')) {
        return cleaned
    }
    val candidates = extractPointNumberCandidates(raw)
    if (candidates.isEmpty()) return cleaned
    val distinct = candidates.distinct()
    return if (distinct.size == 1) distinct.first().toString() else null
}

private fun normalizeActionType(raw: String): String {
    val cleaned = raw.trim().lowercase()
    if (cleaned.isBlank()) return ""
    val normalized = cleaned
        .replace('_', '.')
        .replace('-', '.')
        .replace(" ", "")
    return when (normalized) {
        "point.add", "point.create", "add.point", "addpoint" -> "point.add"
        "point.delete", "point.remove", "point.del", "point.rm" -> "point.delete"
        "point.rename", "point.ren", "rename.point" -> "point.rename"
        "point.update", "point.set", "point.edit", "point.volume" -> "point.update"
        "species.add", "species.create", "add.species", "addspecies" -> "species.add"
        "species.delete", "species.remove", "species.del", "species.rm" -> "species.delete"
        "species.rename", "species.ren", "rename.species" -> "species.rename"
        "count.set", "count.assign", "count.update", "count.edit" -> "count.set"
        "count.delta", "count.change", "count.add", "count.sub" -> "count.delta"
        "count.clear" -> "count.clear"
        "wetweight.set", "wetweight.update", "wetweight.edit" -> "wetweight.set"
        "wetweight.autofill", "wetweight.fill", "wetweight.auto" -> "wetweight.autofill"
        "taxonomy.autofill", "taxonomy.fill", "taxonomy.auto" -> "taxonomy.autofill"
        else -> {
            val text = normalized
            if (text.contains("点位") || text.contains("采样点")) {
                when {
                    text.contains("新增") || text.contains("添加") || text.contains("创建") -> return "point.add"
                    text.contains("删除") || text.contains("移除") || text.contains("去掉") -> return "point.delete"
                    text.contains("改名") || text.contains("重命名") -> return "point.rename"
                    text.contains("更新") || text.contains("修改") || text.contains("参数") || text.contains("vc") || text.contains("vo") -> return "point.update"
                }
            }
            if (text.contains("物种")) {
                when {
                    text.contains("新增") || text.contains("添加") || text.contains("创建") -> return "species.add"
                    text.contains("删除") || text.contains("移除") || text.contains("去掉") -> return "species.delete"
                    text.contains("改名") || text.contains("重命名") -> return "species.rename"
                }
            }
            if (text.contains("计数") || text.contains("数量") || text.contains("个数")) {
                when {
                    text.contains("增加") || text.contains("减少") || text.contains("增减") || text.contains("变化") -> return "count.delta"
                    text.contains("修改") || text.contains("设置") || text.contains("改为") || text.contains("设为") -> return "count.set"
                }
            }
            if (text.contains("湿重")) {
                return if (text.contains("补齐") || text.contains("自动")) {
                    "wetweight.autofill"
                } else {
                    "wetweight.set"
                }
            }
            if (text.contains("分类")) {
                if (text.contains("补齐") || text.contains("自动")) {
                    return "taxonomy.autofill"
                }
            }
            normalized
        }
    }
}

private fun parseFillSource(raw: String?): FillSource? {
    val text = raw?.trim()?.lowercase().orEmpty()
    if (text.isBlank()) return null
    return when {
        text.contains("local") || text.contains("本地") || text.contains("本机") || text.contains("库") -> FillSource.Local
        text.contains("api") || text.contains("ai") || text.contains("接口") -> FillSource.Api
        else -> null
    }
}

private fun parseCountInt(value: Double?): IntParseResult? {
    if (value == null || !value.isFinite()) return null
    val rounded = value.roundToInt()
    val diff = abs(value - rounded.toDouble())
    return IntParseResult(value = rounded, rounded = diff >= 0.01)
}
private fun buildBulkParsePrompt(
    template: ParseTemplate,
    input: String,
    activePointLabel: String?,
    pointLabels: List<String>,
    speciesNames: List<String>,
    aliasMap: Map<String, String>,
): String {
    val pointsPreview = pointLabels.take(160)
    val speciesPreview = speciesNames.take(180)
    val aliasPreview = aliasMap.entries.take(80)

    val pointsText = if (pointsPreview.isEmpty()) "（无）" else pointsPreview.joinToString("、")
    val speciesText = if (speciesPreview.isEmpty()) "（无）" else speciesPreview.joinToString("、")
    val aliasText = if (aliasPreview.isEmpty()) {
        "（无）"
    } else {
        aliasPreview.joinToString("\n") { "- ${it.key} -> ${it.value}" }
    }

    val templateHint = when (template) {
        ParseTemplate.General -> """
            可用动作类型与字段：
            - point.add {point, vc?, vo?}
            - point.delete {point}
            - point.rename {from, to}
            - point.update {point, vc?, vo?}
            - species.add {species}
            - species.delete {species}
            - species.rename {from, to}
            - count.set {point, species, value}
            - count.delta {point, species, delta}
            - wetweight.set {species, value, writeToDb?}
            - wetweight.autofill {species, source("local"/"api"), writeToDb?}
            - taxonomy.autofill {species, source("local"/"api"), writeToDb?}
            说明：point=点位名称；species=物种中文名；“当前点”用 point="当前点"。
        """.trimIndent()

        ParseTemplate.Counts -> """
            仅解析计数相关动作：
            - count.set {point, species, value}
            - count.delta {point, species, delta}
            - species.add / species.delete（仅当明确说明新增/删除物种）
            其他内容放入 unparsed。
        """.trimIndent()
    }

    return """
        你是“浮游动物一体化”应用的批量录入解析助手。
        目标：把用户的自然语言指令转换成结构化操作清单。

        当前点：${activePointLabel ?: "（无）"}
        采样点清单（前 ${pointsPreview.size}/${pointLabels.size}）：$pointsText
        物种清单（前 ${speciesPreview.size}/${speciesNames.size}）：$speciesText
        别名映射（部分）：
        $aliasText

        解析模板：${template.label}
        $templateHint

        输出要求（必须满足）：
        1) 仅输出 JSON，不要解释。
        2) 最后一行严格输出：FINAL_BULK_JSON: <JSON>
        3) JSON 格式：{"actions":[{"type":"point.add","point":"1-0.3","vc":57,"vo":20}],"unparsed":[...],"notes":[...],"warnings":[...]}
        4) actions 必须是数组；每条动作必须包含 "type" 字段，并在同一层给出 point/species/value 等字段。
        5) 不要输出 {"point.add":{...}} 或 {"action":"point.add"} 之类的变体。
        6) 数字必须是阿拉伯数字；point/species 必须是清单中的名称或用户明确的新名称。
        7) 无法确定的指令放入 unparsed 原文。

        用户指令：
        ${input.trim()}
    """.trimIndent()
}
@Composable
fun BatchCountDialog(
    viewModel: MainViewModel,
    activePointId: String,
    incomingPayload: VoiceAssistantResult? = null,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val app = context.applicationContext as com.plankton.one102.PlanktonApplication
    val scope = rememberCoroutineScope()
    val client = remember { ChatCompletionClient() }

    val contentResolver = context.contentResolver

    val dataset by viewModel.currentDataset.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val ds = dataset

    if (ds == null) {
        AlertDialog(
            onDismissRequest = onClose,
            confirmButton = { TextButton(onClick = onClose) { Text("关闭") } },
            title = { Text("批量录入") },
            text = { Text("数据加载中…") },
        )
        return
    }

    val wetWeightRepo = app.wetWeightRepository
    val taxonomyRepo = app.taxonomyRepository
    val taxonomyOverrideRepo = app.taxonomyOverrideRepository
    val aliasRepo = app.aliasRepository
    val aiCacheRepo = app.aiCacheRepository

    val voicePayload = incomingPayload
    val incomingText = voicePayload?.rawText?.trim().orEmpty()
    val incomingBulkJson = voicePayload?.bulkJson?.trim().takeIf { !it.isNullOrBlank() }
    val incomingError = voicePayload?.errorMessage?.trim().orEmpty()
    val incomingWarnings = voicePayload?.warnings?.trim().orEmpty()

    var inputText by remember { mutableStateOf("") }
    var autoCorrectNames by remember { mutableStateOf(true) }
    var parseMode by remember { mutableStateOf(ParseMode.Local) }
    var parseTemplate by remember { mutableStateOf(ParseTemplate.General) }
    var parseModeMenuOpen by remember { mutableStateOf(false) }
    var templateMenuOpen by remember { mutableStateOf(false) }

    var excelCreateMissingPoints by remember { mutableStateOf(false) }
    var excelOverwriteCounts by remember { mutableStateOf(true) }

    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var applyProgress by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<List<PreviewLine>>(emptyList()) }
    var resolvedEdits by remember { mutableStateOf<List<ResolvedEdit>>(emptyList()) }
    var pendingItems by remember { mutableStateOf<List<PendingItem>>(emptyList()) }
    var pendingCorrections by remember { mutableStateOf<List<PendingCorrection>>(emptyList()) }
    var pendingDeleteNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var confirmDeleteDialog by remember { mutableStateOf(false) }
    var lastAiRaw by remember { mutableStateOf<String?>(null) }
    var lastAiRawLabel by remember { mutableStateOf<String?>(null) }
    var rawDialogOpen by remember { mutableStateOf(false) }
    var autoParsedByVoice by remember { mutableStateOf(false) }
    var requireCorrectionConfirm by remember { mutableStateOf(true) }
    var manualSpeciesOverrides by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var manualSpeciesKeep by remember { mutableStateOf<Set<String>>(emptySet()) }
    var manualPointOverrides by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var manualPointKeep by remember { mutableStateOf<Set<String>>(emptySet()) }
    var lastParseKey by remember { mutableStateOf<String?>(null) }
    var lastParsedResult by remember { mutableStateOf<ParsedResult?>(null) }
    var lastUsedParseMode by remember { mutableStateOf<ParseMode?>(null) }

    LaunchedEffect(ds.id, parseMode, parseTemplate, voicePayload?.requestId) {
        if (voicePayload != null && autoParsedByVoice) {
            return@LaunchedEffect
        }
        preview = emptyList()
        resolvedEdits = emptyList()
        pendingItems = emptyList()
        pendingCorrections = emptyList()
        pendingDeleteNames = emptyList()
        lastAiRaw = null
        lastAiRawLabel = null
        rawDialogOpen = false
        lastParseKey = null
        lastParsedResult = null
        lastUsedParseMode = null
    }

    suspend fun buildNameCandidates(): Set<String> = withContext(Dispatchers.IO) {
        val names = LinkedHashSet<String>()
        ds.species.mapTo(names) { it.nameCn.trim() }
        runCatching { taxonomyRepo.getBuiltinEntryMap().keys }.getOrNull()?.forEach { names.add(it.trim()) }
        runCatching { wetWeightRepo.getBuiltinEntries().map { it.nameCn } }.getOrNull()?.forEach { names.add(it.trim()) }
        runCatching { wetWeightRepo.getCustomEntries().map { it.nameCn } }.getOrNull()?.forEach { names.add(it.trim()) }
        runCatching { taxonomyOverrideRepo.getCustomEntries().map { it.nameCn } }.getOrNull()?.forEach { names.add(it.trim()) }
        names.removeIf { it.isBlank() }
        names
    }

    fun apiConfigured(api: ApiConfig): Boolean = api.baseUrl.isNotBlank() && api.model.isNotBlank()
    fun preferParseMode(): ParseMode {
        if (settings.aiAssistantEnabled && apiConfigured(settings.api1)) return ParseMode.Api1
        if (settings.aiAssistantEnabled && apiConfigured(settings.api2)) return ParseMode.Api2
        return ParseMode.Local
    }

    fun activePointLabel(): String? = ds.points.firstOrNull { it.id == activePointId }?.label
    suspend fun buildPreviewAndCommands(reuseParsed: Boolean = false) {
        val text = inputText.trim()
        resolvedEdits = emptyList()
        pendingItems = emptyList()
        pendingCorrections = emptyList()
        pendingDeleteNames = emptyList()
        applyProgress = null

        if (text.isBlank() && incomingBulkJson.isNullOrBlank()) {
            preview = listOf(PreviewLine("请输入指令", PreviewLine.Kind.Error))
            return
        }

        val parseSourceKey = if (text.isBlank() && !incomingBulkJson.isNullOrBlank()) {
            "voice:${voicePayload?.requestId ?: ""}:${incomingBulkJson}"
        } else {
            "text:$text"
        }
        val parseKey = "${parseMode.name}|${parseTemplate.name}|$parseSourceKey"
        val cached = if (reuseParsed && parseKey == lastParseKey) lastParsedResult else null
        if (cached == null) {
            lastAiRaw = null
            lastAiRawLabel = null
            lastUsedParseMode = null
        }

        val aliasMap = runCatching {
            aliasRepo.getAll().associate { it.alias.trim() to it.canonicalNameCn.trim() }
        }.getOrElse { emptyMap() }
        val candidates = buildNameCandidates()

        val lines = mutableListOf<PreviewLine>()
        val edits = mutableListOf<ResolvedEdit>()
        val pending = mutableListOf<PendingItem>()
        val deleteAllNames = mutableSetOf<String>()
        val corrections = mutableListOf<NameCorrection>()
        val correctionPending = mutableListOf<PendingCorrection>()

        var actions: List<AiBulkAction> = emptyList()
        val parseErrors = mutableListOf<String>()
        val aiWarnings = mutableListOf<String>()
        val aiNotes = mutableListOf<String>()
        val aiUnparsed = mutableListOf<String>()
        val unknownActions = mutableListOf<String>()

        if (cached != null) {
            actions = cached.actions
            parseErrors.addAll(cached.errors)
            aiWarnings.addAll(cached.warnings)
            aiNotes.addAll(cached.notes)
            aiUnparsed.addAll(cached.unparsed)
        } else {
            if (text.isBlank() && !incomingBulkJson.isNullOrBlank()) {
                val parsed = parseAiBulkCommand(incomingBulkJson)
                if (parsed == null) {
                    preview = listOf(PreviewLine("语音助手 JSON 无法解析。", PreviewLine.Kind.Error))
                    return
                }
                lastAiRaw = incomingBulkJson
                lastAiRawLabel = "语音助手"
                actions = parsed.actions
                aiWarnings.addAll(parsed.warnings)
                aiNotes.addAll(parsed.notes)
                aiUnparsed.addAll(parsed.unparsed)
                lastUsedParseMode = parseMode
            } else when (parseMode) {
                ParseMode.Local -> {
                    val res = parseBatchCountCommands(text)
                    actions = res.commands.map { cmd ->
                        when (cmd) {
                            is BatchCountCommand.SetCount -> AiBulkAction(
                                type = "count.set",
                                point = cmd.pointToken,
                                species = cmd.speciesToken,
                                value = cmd.value.toDouble(),
                            )
                            is BatchCountCommand.DeltaCount -> AiBulkAction(
                                type = "count.delta",
                                point = cmd.pointToken,
                                species = cmd.speciesToken,
                                delta = cmd.delta.toDouble(),
                            )
                            is BatchCountCommand.DeleteSpecies -> {
                                if (cmd.pointToken == null) {
                                    AiBulkAction(type = "species.delete", species = cmd.speciesToken)
                                } else {
                                    AiBulkAction(
                                        type = "count.set",
                                        point = cmd.pointToken,
                                        species = cmd.speciesToken,
                                        value = 0.0,
                                    )
                                }
                            }
                        }
                    }
                    parseErrors.addAll(res.errors)
                    lastUsedParseMode = ParseMode.Local
                }

                ParseMode.Api1, ParseMode.Api2 -> {
                    if (!settings.aiAssistantEnabled) {
                        preview = listOf(PreviewLine("请先在设置中开启 AI 功能。", PreviewLine.Kind.Error))
                        return
                    }
                    if (!apiConfigured(settings.api1) && !apiConfigured(settings.api2)) {
                        preview = listOf(PreviewLine("API1/API2 均未配置 Base URL / Model。", PreviewLine.Kind.Error))
                        return
                    }

                    val prompt = buildBulkParsePrompt(
                        template = parseTemplate,
                        input = text,
                        activePointLabel = activePointLabel(),
                        pointLabels = ds.points.map { it.label.ifBlank { it.id } },
                        speciesNames = ds.species.map { it.nameCn.ifBlank { it.id } },
                        aliasMap = aliasMap,
                    )

                    data class ApiParseResult(
                        val label: String,
                        val mode: ParseMode,
                        val raw: String?,
                        val parsed: com.plankton.one102.data.api.AiBulkCommand?,
                        val error: String? = null,
                    )

                    suspend fun tryParseWithApi(apiConfig: ApiConfig, label: String, mode: ParseMode): ApiParseResult {
                        if (!apiConfigured(apiConfig)) {
                            return ApiParseResult(label = label, mode = mode, raw = null, parsed = null, error = "$label 未配置 Base URL / Model。")
                        }
                        val raw = runCatching { client.call(apiConfig, prompt, maxTokens = 1400) }
                            .getOrElse { err ->
                                return ApiParseResult(label = label, mode = mode, raw = null, parsed = null, error = "$label 调用失败：${err.message}")
                            }
                        val payloads = extractAllJsonPayloads(raw, "FINAL_BULK_JSON").ifEmpty {
                            extractAllJsonPayloads(raw, null)
                        }
                        val parsedCandidates = payloads.mapNotNull { json ->
                            parseAiBulkCommand(json)?.let { json to it }
                        }
                        val best = parsedCandidates.maxByOrNull { (_, cmd) -> cmd.estimateQuality() }
                        val parsed = best?.second
                        if (parsed == null) {
                            return ApiParseResult(label = label, mode = mode, raw = raw, parsed = null, error = "$label 未输出可解析的 JSON。")
                        }
                        return ApiParseResult(label = label, mode = mode, raw = raw, parsed = parsed)
                    }

                    val primaryMode = if (parseMode == ParseMode.Api1) ParseMode.Api1 else ParseMode.Api2
                    val secondaryMode = if (primaryMode == ParseMode.Api1) ParseMode.Api2 else ParseMode.Api1
                    val primaryApi = if (primaryMode == ParseMode.Api1) settings.api1 else settings.api2
                    val secondaryApi = if (secondaryMode == ParseMode.Api1) settings.api1 else settings.api2
                    val primaryLabel = if (primaryMode == ParseMode.Api1) "API1" else "API2"
                    val secondaryLabel = if (secondaryMode == ParseMode.Api1) "API1" else "API2"

                    val primaryResult = tryParseWithApi(primaryApi, primaryLabel, primaryMode)
                    val secondaryResult = if (primaryResult.parsed == null) {
                        tryParseWithApi(secondaryApi, secondaryLabel, secondaryMode)
                    } else {
                        null
                    }

                    val chosen = when {
                        primaryResult.parsed != null -> primaryResult
                        secondaryResult?.parsed != null -> {
                            aiWarnings.add("$primaryLabel 解析失败，已改用 $secondaryLabel")
                            secondaryResult
                        }
                        else -> null
                    }

                    if (chosen == null) {
                        listOf(primaryResult, secondaryResult).filterNotNull().forEach { res ->
                            res.error?.let { aiWarnings.add(it) }
                            if (!res.raw.isNullOrBlank() && lastAiRaw.isNullOrBlank()) {
                                lastAiRaw = res.raw
                                lastAiRawLabel = res.label
                            }
                        }
                        aiWarnings.add("API 解析失败，已降级为本地规则解析。")
                        val res = parseBatchCountCommands(text)
                        actions = res.commands.map { cmd ->
                            when (cmd) {
                                is BatchCountCommand.SetCount -> AiBulkAction(
                                    type = "count.set",
                                    point = cmd.pointToken,
                                    species = cmd.speciesToken,
                                    value = cmd.value.toDouble(),
                                )
                                is BatchCountCommand.DeltaCount -> AiBulkAction(
                                    type = "count.delta",
                                    point = cmd.pointToken,
                                    species = cmd.speciesToken,
                                    delta = cmd.delta.toDouble(),
                                )
                                is BatchCountCommand.DeleteSpecies -> {
                                    if (cmd.pointToken == null) {
                                        AiBulkAction(type = "species.delete", species = cmd.speciesToken)
                                    } else {
                                        AiBulkAction(
                                            type = "count.set",
                                            point = cmd.pointToken,
                                            species = cmd.speciesToken,
                                            value = 0.0,
                                        )
                                    }
                                }
                            }
                        }
                        parseErrors.addAll(res.errors)
                        lastUsedParseMode = ParseMode.Local
                    } else {
                        lastAiRaw = chosen.raw
                        lastAiRawLabel = chosen.label
                        actions = chosen.parsed?.actions.orEmpty()
                        aiWarnings.addAll(chosen.parsed?.warnings.orEmpty())
                        aiNotes.addAll(chosen.parsed?.notes.orEmpty())
                        aiUnparsed.addAll(chosen.parsed?.unparsed.orEmpty())
                        lastUsedParseMode = chosen.mode
                    }
                }
            }
            lastParsedResult = ParsedResult(
                actions = actions,
                errors = parseErrors.toList(),
                warnings = aiWarnings.toList(),
                notes = aiNotes.toList(),
                unparsed = aiUnparsed.toList(),
            )
            lastParseKey = parseKey
        }

        if (actions.isEmpty() && parseErrors.isEmpty() && aiUnparsed.isEmpty()) {
            preview = listOf(PreviewLine("未解析到有效指令", PreviewLine.Kind.Error))
            return
        }

        aiNotes.forEach { lines += PreviewLine("备注：$it", PreviewLine.Kind.Normal) }
        aiWarnings.forEach { lines += PreviewLine("提示：$it", PreviewLine.Kind.Warn) }

        val simPoints = ds.points.toMutableList()
        val simSpecies = ds.species.toMutableList()
        val implicitAdds = mutableSetOf<String>()

        fun addPreview(text: String, kind: PreviewLine.Kind = PreviewLine.Kind.Normal) {
            lines += PreviewLine(text, kind)
        }

        fun addPending(text: String, reason: String) {
            pending += PendingItem(text = text, reason = reason)
            addPreview("待确认：$text（$reason）", PreviewLine.Kind.Warn)
        }

        fun addCorrection(kind: CorrectionKind, raw: String, suggestion: String, score: Double?, reason: String) {
            if (correctionPending.any { it.kind == kind && it.raw == raw && it.suggestion == suggestion }) return
            correctionPending += PendingCorrection(
                kind = kind,
                raw = raw,
                suggestion = suggestion,
                reason = reason,
                score = score,
            )
            val kindLabel = if (kind == CorrectionKind.Species) "物种" else "点位"
            addPreview("待校准：$kindLabel $raw → $suggestion（$reason）", PreviewLine.Kind.Warn)
        }

        fun addError(text: String) {
            addPreview(text, PreviewLine.Kind.Error)
        }

        fun addErrorSummary(prefix: String, items: List<String>) {
            val unique = items.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            if (unique.isEmpty()) return
            val head = unique.take(3).joinToString("、")
            val tail = if (unique.size > 3) " 等${unique.size}条" else ""
            addError("$prefix：$head$tail")
        }
        fun pendingScoreForToken(token: String): Double {
            return if (token.length <= 4) SHORT_NAME_PENDING_SCORE else FUZZY_PENDING_SCORE
        }

        data class PointResolve(
            val point: Point? = null,
            val label: String? = null,
            val reason: String? = null,
            val suggestion: String? = null,
            val needsCorrection: Boolean = false,
        )

        fun resolvePointToken(raw: String?, allowNew: Boolean): PointResolve {
            val tokenRaw = raw?.trim().orEmpty()
            val isCurrent = tokenRaw.isBlank() || tokenRaw.contains("当前") || tokenRaw.contains("本点")
            if (isCurrent) {
                val current = simPoints.firstOrNull { it.id == activePointId }
                return if (current == null) PointResolve(reason = "当前点位为空") else PointResolve(point = current, label = current.label)
            }

            val locked = manualPointKeep.contains(tokenRaw)
            val token = manualPointOverrides[tokenRaw] ?: tokenRaw
            val normalized = normalizePointLabel(token)
            val exact = simPoints.filter { normalizePointLabel(it.label) == normalized }
            if (exact.size == 1) return PointResolve(point = exact.first(), label = exact.first().label)
            if (exact.size > 1) return PointResolve(reason = "匹配到多个点位")

            val partial = simPoints.filter {
                val label = normalizePointLabel(it.label)
                normalized.contains(label) || label.contains(normalized)
            }
            if (partial.size == 1) return PointResolve(point = partial.first(), label = partial.first().label)
            if (partial.size > 1) return PointResolve(reason = "匹配到多个点位")

            if (locked) {
                return if (allowNew) PointResolve(label = tokenRaw) else PointResolve(reason = "未识别点位")
            }

            val suggestionToken = if (manualPointOverrides.containsKey(tokenRaw)) token else tokenRaw
            val labelSuggestion = suggestPointLabel(suggestionToken)
            if (!labelSuggestion.isNullOrBlank()) {
                val suggestedNormalized = normalizePointLabel(labelSuggestion)
                val suggested = simPoints.filter { normalizePointLabel(it.label) == suggestedNormalized }
                if (suggested.size == 1) {
                    val target = suggested.first()
                    if (!locked && normalizePointLabel(target.label) != normalized) {
                        val reason = "可能是 ${target.label}"
                        if (requireCorrectionConfirm) {
                            addCorrection(
                                kind = CorrectionKind.Point,
                                raw = tokenRaw,
                                suggestion = target.label,
                                score = 1.0,
                                reason = reason,
                            )
                            return PointResolve(reason = reason, suggestion = target.label, needsCorrection = true)
                        }
                        if (autoCorrectNames && target.label != tokenRaw) {
                            corrections += NameCorrection(raw = tokenRaw, canonical = target.label, score = 1.0)
                        }
                    }
                    return PointResolve(point = target, label = target.label)
                }
            }

            val pointCandidates = simPoints.map { normalizePointLabel(it.label) }.distinct()
            val best = bestCanonicalName(normalized, pointCandidates)
            if (best != null) {
                val bestPoint = simPoints.firstOrNull { normalizePointLabel(it.label) == best.canonical }
                if (bestPoint != null && !locked) {
                    if (autoCorrectNames && !requireCorrectionConfirm && best.score >= POINT_AUTO_SCORE) {
                        corrections += NameCorrection(raw = tokenRaw, canonical = bestPoint.label, score = best.score)
                        return PointResolve(point = bestPoint, label = bestPoint.label)
                    }
                    if (best.score >= POINT_PENDING_SCORE) {
                        val reason = "可能是 ${bestPoint.label}（${(best.score * 100).toInt()}%）"
                        if (requireCorrectionConfirm) {
                            addCorrection(
                                kind = CorrectionKind.Point,
                                raw = tokenRaw,
                                suggestion = bestPoint.label,
                                score = best.score,
                                reason = reason,
                            )
                            return PointResolve(reason = reason, suggestion = bestPoint.label, needsCorrection = true)
                        }
                        return PointResolve(reason = reason, suggestion = bestPoint.label)
                    }
                }
            }

            val indexCandidate = extractPointNumberCandidates(token).firstOrNull()
            if (indexCandidate != null) {
                val idx = indexCandidate - 1
                val point = simPoints.getOrNull(idx)
                if (point != null) {
                    if (!locked && requireCorrectionConfirm && normalizePointLabel(point.label) != normalized) {
                        val reason = "可能是 ${point.label}"
                        addCorrection(
                            kind = CorrectionKind.Point,
                            raw = tokenRaw,
                            suggestion = point.label,
                            score = 0.9,
                            reason = reason,
                        )
                        return PointResolve(reason = reason, suggestion = point.label, needsCorrection = true)
                    }
                    return PointResolve(point = point, label = point.label)
                }
            }

            if (allowNew) {
                val fallback = labelSuggestion ?: normalizePointToken(token)
                if (fallback.isBlank()) return PointResolve(reason = "未识别点位")
                if (!locked && requireCorrectionConfirm && normalizePointLabel(fallback) != normalized) {
                    val reason = "可能是 $fallback"
                    addCorrection(
                        kind = CorrectionKind.Point,
                        raw = tokenRaw,
                        suggestion = fallback,
                        score = null,
                        reason = reason,
                    )
                    return PointResolve(reason = reason, suggestion = fallback, needsCorrection = true)
                }
                return PointResolve(label = fallback)
            }

            return PointResolve(reason = "未识别点位")
        }

        data class SpeciesResolve(
            val name: String? = null,
            val species: Species? = null,
            val reason: String? = null,
            val suggestion: String? = null,
            val needsCorrection: Boolean = false,
        )

        fun resolveSpeciesToken(raw: String?, allowNew: Boolean): SpeciesResolve {
            val token = raw?.trim().orEmpty()
            if (token.isBlank()) return SpeciesResolve(reason = "未识别物种名称")
            val manual = manualSpeciesOverrides[token]
            if (!manual.isNullOrBlank()) {
                val existing = simSpecies.firstOrNull { it.nameCn.trim() == manual.trim() }
                return SpeciesResolve(name = manual, species = existing)
            }
            if (manualSpeciesKeep.contains(token)) {
                val existing = simSpecies.firstOrNull { it.nameCn.trim() == token }
                return if (existing != null) {
                    SpeciesResolve(name = token, species = existing)
                } else if (allowNew) {
                    SpeciesResolve(name = token)
                } else {
                    SpeciesResolve(reason = "未找到物种")
                }
            }

            val alias = aliasMap[token]
            if (!alias.isNullOrBlank()) {
                if (alias != token) corrections += NameCorrection(raw = token, canonical = alias, score = 1.0)
                val existing = simSpecies.firstOrNull { it.nameCn.trim() == alias.trim() }
                return SpeciesResolve(name = alias, species = existing)
            }

            val existing = simSpecies.firstOrNull { it.nameCn.trim() == token }
            if (existing != null) return SpeciesResolve(name = token, species = existing)

            val bestExisting = bestCanonicalName(token, simSpecies.map { it.nameCn.trim() }.filter { it.isNotBlank() })
            if (bestExisting != null) {
                if (autoCorrectNames && !requireCorrectionConfirm && bestExisting.score >= AUTO_CORRECT_SCORE) {
                    corrections += bestExisting
                    val found = simSpecies.firstOrNull { it.nameCn.trim() == bestExisting.canonical.trim() }
                    return SpeciesResolve(name = bestExisting.canonical, species = found)
                }
                val pendingScore = pendingScoreForToken(token)
                if (bestExisting.score >= pendingScore) {
                    val reason = "可能是 ${bestExisting.canonical}（${(bestExisting.score * 100).toInt()}%）"
                    if (requireCorrectionConfirm) {
                        addCorrection(
                            kind = CorrectionKind.Species,
                            raw = token,
                            suggestion = bestExisting.canonical,
                            score = bestExisting.score,
                            reason = reason,
                        )
                        return SpeciesResolve(reason = reason, suggestion = bestExisting.canonical, needsCorrection = true)
                    }
                    if (!allowNew) {
                        return SpeciesResolve(reason = reason, suggestion = bestExisting.canonical)
                    }
                }
            }

            if (!allowNew) {
                val hint = bestExisting?.takeIf { it.score >= FUZZY_HINT_SCORE }?.let { "可能是 ${it.canonical}" }
                return SpeciesResolve(reason = hint ?: "未找到物种")
            }

            val suggestion = bestCanonicalName(token, candidates)?.takeIf { it.score >= FUZZY_HINT_SCORE }?.canonical
            return SpeciesResolve(name = token, suggestion = suggestion)
        }

        fun addPointToSpecies(pointId: String) {
            for (i in simSpecies.indices) {
                val sp = simSpecies[i]
                if (sp.countsByPointId.containsKey(pointId)) continue
                simSpecies[i] = sp.copy(countsByPointId = sp.countsByPointId + (pointId to 0))
            }
        }

        fun removePointFromSpecies(pointId: String) {
            for (i in simSpecies.indices) {
                val sp = simSpecies[i]
                if (!sp.countsByPointId.containsKey(pointId)) continue
                simSpecies[i] = sp.copy(countsByPointId = sp.countsByPointId - pointId)
            }
        }

        fun ensureSpeciesInSim(nameCn: String): Species {
            val key = nameCn.trim()
            val existing = simSpecies.firstOrNull { it.nameCn.trim() == key }
            if (existing != null) return existing
            val sp = Species(
                id = newId(),
                nameCn = key,
                nameLatin = "",
                taxonomy = Taxonomy(),
                avgWetWeightMg = null,
                countsByPointId = simPoints.associate { it.id to 0 },
            )
            simSpecies.add(sp)
            return sp
        }

        fun mergeTaxonomyForSim(list: List<Taxonomy>): Taxonomy {
            var lvl1 = ""
            var lvl2 = ""
            var lvl3 = ""
            var lvl4 = ""
            var lvl5 = ""
            for (t in list) {
                if (lvl1.isBlank() && t.lvl1.isNotBlank()) lvl1 = t.lvl1
                if (lvl2.isBlank() && t.lvl2.isNotBlank()) lvl2 = t.lvl2
                if (lvl3.isBlank() && t.lvl3.isNotBlank()) lvl3 = t.lvl3
                if (lvl4.isBlank() && t.lvl4.isNotBlank()) lvl4 = t.lvl4
                if (lvl5.isBlank() && t.lvl5.isNotBlank()) lvl5 = t.lvl5
            }
            return Taxonomy(lvl1 = lvl1, lvl2 = lvl2, lvl3 = lvl3, lvl4 = lvl4, lvl5 = lvl5)
        }

        fun mergeSimSpecies(base: Species, other: Species): Species {
            val mergedLatin = sequenceOf(base.nameLatin, other.nameLatin).firstOrNull { it.isNotBlank() }.orEmpty()
            val mergedWet = base.avgWetWeightMg ?: other.avgWetWeightMg
            val mergedTaxonomy = mergeTaxonomyForSim(listOf(base.taxonomy, other.taxonomy))
            val mergedCounts = buildMap {
                for (p in simPoints) {
                    val a = base.countsByPointId[p.id] ?: 0
                    val b = other.countsByPointId[p.id] ?: 0
                    put(p.id, maxOf(a, b))
                }
            }
            return base.copy(
                nameLatin = mergedLatin,
                taxonomy = mergedTaxonomy,
                avgWetWeightMg = mergedWet,
                countsByPointId = mergedCounts,
            )
        }

        for (action in actions) {
            val type = normalizeActionType(action.type)
            when (type) {
                "point.add" -> {
                    val resolved = resolvePointToken(action.point, allowNew = true)
                    val label = resolved.label ?: resolved.point?.label
                    if (resolved.reason != null || label.isNullOrBlank()) {
                        if (!resolved.needsCorrection) {
                            addPending("新增点位", resolved.reason ?: "缺少点位名称")
                        }
                        continue
                    }
                    if (simPoints.any { normalizePointLabel(it.label) == normalizePointLabel(label) }) {
                        addPreview("点位已存在：$label", PreviewLine.Kind.Warn)
                        continue
                    }
                    val vc = action.vc
                    if (vc != null && (!vc.isFinite() || vc <= 0)) {
                        addPending("新增点位 $label", "Vc 数值无效")
                        continue
                    }
                    val vo = action.vo
                    if (vo != null && (!vo.isFinite() || vo <= 0)) {
                        addPending("新增点位 $label", "Vo 数值无效")
                        continue
                    }
                    val (site, depth) = resolveSiteAndDepthForPoint(label = label, site = null, depthM = null)
                    val point = Point(
                        id = newId(),
                        label = label,
                        vConcMl = vc,
                        vOrigL = vo ?: settings.defaultVOrigL,
                        site = site,
                        depthM = depth,
                    )
                    edits += ResolvedEdit.AddPoint(point)
                    simPoints.add(point)
                    addPointToSpecies(point.id)
                    addPreview("新增点位：$label（Vc=${vc ?: "默认"}，Vo=${vo ?: settings.defaultVOrigL}）")
                }

                "point.delete" -> {
                    val resolved = resolvePointToken(action.point, allowNew = false)
                    val point = resolved.point
                    if (point == null) {
                        if (!resolved.needsCorrection) {
                            addPending("删除点位", resolved.reason ?: "未识别点位")
                        }
                        continue
                    }
                    edits += ResolvedEdit.DeletePoint(pointId = point.id, label = point.label)
                    simPoints.removeAll { it.id == point.id }
                    removePointFromSpecies(point.id)
                    addPreview("删除点位：${point.label}", PreviewLine.Kind.Warn)
                }

                "point.rename" -> {
                    val from = action.from?.trim().orEmpty()
                    val to = action.to?.trim().orEmpty()
                    if (from.isBlank() || to.isBlank()) {
                        addPending("点位重命名", "缺少旧名/新名")
                        continue
                    }
                    val fromResolved = resolvePointToken(from, allowNew = false)
                    val point = fromResolved.point
                    if (point == null) {
                        if (!fromResolved.needsCorrection) {
                            addPending("点位重命名：$from → $to", fromResolved.reason ?: "未识别点位")
                        }
                        continue
                    }
                    val toResolved = resolvePointToken(to, allowNew = true)
                    val nextLabel = toResolved.label ?: toResolved.point?.label
                    if (toResolved.reason != null || nextLabel.isNullOrBlank()) {
                        if (!toResolved.needsCorrection) {
                            addPending("点位重命名：$from → $to", toResolved.reason ?: "未识别点位")
                        }
                        continue
                    }
                    if (simPoints.any { it.id != point.id && normalizePointLabel(it.label) == normalizePointLabel(nextLabel) }) {
                        addPending("点位重命名：$from → $to", "新名称已存在")
                        continue
                    }
                    val (site, depth) = resolveSiteAndDepthForPoint(label = nextLabel, site = null, depthM = null)
                    edits += ResolvedEdit.RenamePoint(pointId = point.id, label = nextLabel, site = site, depthM = depth)
                    val idx = simPoints.indexOfFirst { it.id == point.id }
                    if (idx >= 0) simPoints[idx] = point.copy(label = nextLabel, site = site, depthM = depth)
                    addPreview("点位改名：${point.label} → $nextLabel")
                }

                "point.update" -> {
                    val resolved = resolvePointToken(action.point, allowNew = false)
                    val point = resolved.point
                    if (point == null) {
                        if (!resolved.needsCorrection) {
                            addPending("点位参数修改", resolved.reason ?: "未识别点位")
                        }
                        continue
                    }
                    val vc = action.vc
                    val vo = action.vo
                    if (vc == null && vo == null) {
                        addPending("点位参数修改：${point.label}", "缺少 Vc/Vo")
                        continue
                    }
                    if (vc != null && (!vc.isFinite() || vc <= 0)) {
                        addPending("点位参数修改：${point.label}", "Vc 数值无效")
                        continue
                    }
                    if (vo != null && (!vo.isFinite() || vo <= 0)) {
                        addPending("点位参数修改：${point.label}", "Vo 数值无效")
                        continue
                    }
                    edits += ResolvedEdit.UpdatePoint(pointId = point.id, vc = vc, vo = vo)
                    val idx = simPoints.indexOfFirst { it.id == point.id }
                    if (idx >= 0) {
                        val next = simPoints[idx].copy(
                            vConcMl = vc ?: simPoints[idx].vConcMl,
                            vOrigL = vo ?: simPoints[idx].vOrigL,
                        )
                        simPoints[idx] = next
                    }
                    addPreview("点位参数：${point.label} Vc=${vc ?: "保持"} · Vo=${vo ?: "保持"}")
                }

                "species.add" -> {
                    val resolved = resolveSpeciesToken(action.species, allowNew = true)
                    val name = resolved.name
                    if (resolved.reason != null || name.isNullOrBlank()) {
                        if (!resolved.needsCorrection) {
                            addPending("新增物种", resolved.reason ?: "未识别物种名称")
                        }
                        continue
                    }
                    if (simSpecies.any { it.nameCn.trim() == name.trim() }) {
                        addPreview("物种已存在：$name", PreviewLine.Kind.Warn)
                        continue
                    }
                    edits += ResolvedEdit.AddSpecies(nameCn = name)
                    ensureSpeciesInSim(name)
                    addPreview("新增物种：$name")
                }

                "species.delete" -> {
                    val resolved = resolveSpeciesToken(action.species, allowNew = false)
                    val target = resolved.species
                    if (resolved.reason != null || target == null) {
                        if (!resolved.needsCorrection) {
                            addPending("删除物种", resolved.reason ?: "未识别物种")
                        }
                        continue
                    }
                    edits += ResolvedEdit.DeleteSpecies(speciesId = target.id, nameCn = target.nameCn)
                    deleteAllNames += target.nameCn
                    simSpecies.removeAll { it.id == target.id }
                    addPreview("删除物种：${target.nameCn}", PreviewLine.Kind.Warn)
                }

                "species.rename" -> {
                    val from = action.from?.trim().orEmpty()
                    val to = action.to?.trim().orEmpty()
                    if (from.isBlank() || to.isBlank()) {
                        addPending("物种重命名", "缺少旧名/新名")
                        continue
                    }
                    val fromResolved = resolveSpeciesToken(from, allowNew = false)
                    val target = fromResolved.species
                    if (fromResolved.reason != null || target == null) {
                        if (!fromResolved.needsCorrection) {
                            addPending("物种重命名：$from → $to", fromResolved.reason ?: "未识别物种")
                        }
                        continue
                    }
                    val toResolved = resolveSpeciesToken(to, allowNew = true)
                    val nextName = toResolved.name
                    if (toResolved.reason != null || nextName.isNullOrBlank()) {
                        if (!toResolved.needsCorrection) {
                            addPending("物种重命名：$from → $to", toResolved.reason ?: "未识别物种")
                        }
                        continue
                    }
                    val existing = simSpecies.firstOrNull { it.id != target.id && it.nameCn.trim() == nextName.trim() }
                    edits += ResolvedEdit.RenameSpecies(speciesId = target.id, nameCn = nextName)
                    if (existing != null) {
                        val existingIdx = simSpecies.indexOfFirst { it.id == existing.id }
                        val merged = mergeSimSpecies(existing, target.copy(nameCn = nextName))
                        simSpecies.removeAll { it.id == target.id }
                        if (existingIdx >= 0) {
                            simSpecies[existingIdx] = merged
                        } else {
                            simSpecies.add(merged)
                        }
                        addPreview("物种改名：${target.nameCn} → $nextName（同名合并，计数取最大值）", PreviewLine.Kind.Warn)
                    } else {
                        val idx = simSpecies.indexOfFirst { it.id == target.id }
                        if (idx >= 0) simSpecies[idx] = target.copy(nameCn = nextName)
                        addPreview("物种改名：${target.nameCn} → $nextName")
                    }
                }

                "count.set", "count.clear" -> {
                    val pointResolved = resolvePointToken(action.point, allowNew = false)
                    val point = pointResolved.point
                    if (point == null) {
                        if (!pointResolved.needsCorrection) {
                            addPending("设置计数", pointResolved.reason ?: "未识别点位")
                        }
                        continue
                    }
                    val resolved = resolveSpeciesToken(action.species, allowNew = true)
                    val name = resolved.name
                    if (resolved.reason != null || name.isNullOrBlank()) {
                        if (!resolved.needsCorrection) {
                            addPending("设置计数：${point.label}", resolved.reason ?: "未识别物种")
                        }
                        continue
                    }
                    val parsed = if (type == "count.clear") IntParseResult(0, false) else parseCountInt(action.value)
                    if (parsed == null) {
                        addPending("设置计数：${point.label} · $name", "缺少计数值")
                        continue
                    }
                    if (parsed.value < 0) {
                        addPending("设置计数：${point.label} · $name", "计数不能为负数")
                        continue
                    }
                    if (resolved.species == null) {
                        ensureSpeciesInSim(name)
                        if (implicitAdds.add(name)) {
                            addPreview("新增物种：$name（由计数触发）", PreviewLine.Kind.Warn)
                        }
                    }
                    val warn = if (parsed.rounded) "（已四舍五入）" else ""
                    addPreview("设置：${point.label} · $name = ${parsed.value}$warn")
                    edits += ResolvedEdit.SetCount(pointId = point.id, speciesNameCn = name, value = parsed.value)
                }

                "count.delta" -> {
                    val pointResolved = resolvePointToken(action.point, allowNew = false)
                    val point = pointResolved.point
                    if (point == null) {
                        if (!pointResolved.needsCorrection) {
                            addPending("增减计数", pointResolved.reason ?: "未识别点位")
                        }
                        continue
                    }
                    val resolved = resolveSpeciesToken(action.species, allowNew = true)
                    val name = resolved.name
                    if (resolved.reason != null || name.isNullOrBlank()) {
                        if (!resolved.needsCorrection) {
                            addPending("增减计数：${point.label}", resolved.reason ?: "未识别物种")
                        }
                        continue
                    }
                    val parsed = parseCountInt(action.delta)
                    if (parsed == null) {
                        addPending("增减计数：${point.label} · $name", "缺少增减数量")
                        continue
                    }
                    if (resolved.species == null) {
                        ensureSpeciesInSim(name)
                        if (implicitAdds.add(name)) {
                            addPreview("新增物种：$name（由计数触发）", PreviewLine.Kind.Warn)
                        }
                    }
                    val delta = parsed.value
                    val sign = if (delta >= 0) "+" else ""
                    val warn = if (parsed.rounded) "（已四舍五入）" else ""
                    addPreview("增减：${point.label} · $name $sign$delta$warn")
                    edits += ResolvedEdit.DeltaCount(pointId = point.id, speciesNameCn = name, delta = delta)
                }

                "wetweight.set" -> {
                    val resolved = resolveSpeciesToken(action.species, allowNew = true)
                    val name = resolved.name
                    if (resolved.reason != null || name.isNullOrBlank()) {
                        if (!resolved.needsCorrection) {
                            addPending("设置湿重", resolved.reason ?: "未识别物种")
                        }
                        continue
                    }
                    val value = action.value
                    if (value == null || !value.isFinite() || value <= 0) {
                        addPending("设置湿重：$name", "湿重数值无效")
                        continue
                    }
                    val writeToDb = action.writeToDb == true
                    edits += ResolvedEdit.SetWetWeight(
                        speciesNameCn = name,
                        value = value,
                        nameLatin = null,
                        writeToDb = writeToDb,
                        onlyIfBlank = false,
                    )
                    addPreview("湿重：$name = $value mg/个${if (writeToDb) "（写入库）" else ""}")
                }

                "wetweight.autofill" -> {
                    val resolved = resolveSpeciesToken(action.species, allowNew = true)
                    val name = resolved.name
                    if (resolved.reason != null || name.isNullOrBlank()) {
                        if (!resolved.needsCorrection) {
                            addPending("补齐湿重", resolved.reason ?: "未识别物种")
                        }
                        continue
                    }
                    val source = parseFillSource(action.source)
                    if (source == null) {
                        addPending("补齐湿重：$name", "缺少 source（local/api）")
                        continue
                    }
                    val writeToDb = action.writeToDb ?: settings.autoMatchWriteToDb
                    edits += ResolvedEdit.AutofillWetWeight(speciesNameCn = name, source = source, writeToDb = writeToDb)
                    addPreview("湿重补齐：$name（${if (source == FillSource.Local) "本机" else "AI"}）${if (writeToDb) "·写入库" else ""}")
                }

                "taxonomy.autofill" -> {
                    val resolved = resolveSpeciesToken(action.species, allowNew = true)
                    val name = resolved.name
                    if (resolved.reason != null || name.isNullOrBlank()) {
                        if (!resolved.needsCorrection) {
                            addPending("补齐分类", resolved.reason ?: "未识别物种")
                        }
                        continue
                    }
                    val source = parseFillSource(action.source)
                    if (source == null) {
                        addPending("补齐分类：$name", "缺少 source（local/api）")
                        continue
                    }
                    val writeToDb = action.writeToDb ?: settings.autoMatchWriteToDb
                    edits += ResolvedEdit.AutofillTaxonomy(speciesNameCn = name, source = source, writeToDb = writeToDb)
                    addPreview("分类补齐：$name（${if (source == FillSource.Local) "本机" else "AI"}）${if (writeToDb) "·写入库" else ""}")
                }

                else -> {
                    val detail = action.note?.trim().orEmpty().ifBlank { action.type }
                    unknownActions += detail
                }
            }
        }

        if (corrections.isNotEmpty()) {
            val summary = corrections.distinctBy { it.raw + it.canonical }.take(4)
                .joinToString { "${it.raw}→${it.canonical}" }
            lines.add(0, PreviewLine("已自动纠错：$summary${if (corrections.size > 4) "…" else ""}"))
        }
        if (correctionPending.isNotEmpty()) {
            val summary = correctionPending.distinctBy { it.kind.name + it.raw + it.suggestion }.take(3)
                .joinToString { "${it.raw}→${it.suggestion}" }
            lines.add(0, PreviewLine("待校准：$summary${if (correctionPending.size > 3) "…" else ""}", PreviewLine.Kind.Warn))
        }

        addErrorSummary("解析错误", parseErrors)
        addErrorSummary("无法解析", aiUnparsed)
        addErrorSummary("未识别动作", unknownActions)

        pendingDeleteNames = deleteAllNames.toList().sorted()
        resolvedEdits = edits
        pendingItems = pending
        pendingCorrections = correctionPending
        preview = lines
    }

    LaunchedEffect(voicePayload?.requestId) {
        if (voicePayload == null) {
            autoParsedByVoice = false
            return@LaunchedEffect
        }
        if (autoParsedByVoice) return@LaunchedEffect

        if (incomingText.isNotBlank()) {
            inputText = if (inputText.isBlank()) incomingText else inputText.trimEnd() + "\n" + incomingText
        }
        parseMode = preferParseMode()
        parseTemplate = ParseTemplate.General
        autoParsedByVoice = true

        if (incomingText.isNotBlank() || !incomingBulkJson.isNullOrBlank()) {
            busy = true
            runCatching { buildPreviewAndCommands() }.onFailure {
                preview = listOf(PreviewLine("解析失败：${it.message}", PreviewLine.Kind.Error))
            }
            busy = false
        }
    }

    suspend fun applyEdits(confirmedDeleteAll: Boolean) {
        if (resolvedEdits.isEmpty()) return

        busy = true
        applyProgress = "处理中…"
        message = null

        val finalEdits = mutableListOf<ResolvedEdit>()
        val errors = mutableListOf<String>()
        val wetWriteMap = LinkedHashMap<String, WetWeightEntry>()
        val taxWriteMap = LinkedHashMap<String, TaxonomyRecord>()
        val aliasMap = runCatching {
            aliasRepo.getAll().associate { it.alias.trim() to it.canonicalNameCn.trim() }
        }.getOrElse { emptyMap() }

        val effectiveParseMode = lastUsedParseMode ?: parseMode
        val api = when (effectiveParseMode) {
            ParseMode.Api1 -> settings.api1
            ParseMode.Api2 -> settings.api2
            ParseMode.Local -> null
        }
        val apiTag = when (effectiveParseMode) {
            ParseMode.Api1 -> "api1"
            ParseMode.Api2 -> "api2"
            ParseMode.Local -> ""
        }

        suspend fun callAiSpeciesInfo(nameCn: String, prompt: String): com.plankton.one102.data.api.AiSpeciesInfo? {
            val cached = runCatching { aiCacheRepo.getSpeciesInfo(apiTag, nameCn) }.getOrNull()
            if (cached != null && cached.prompt == prompt && cached.raw.isNotBlank()) {
                val json = extractFinalSpeciesJson(cached.raw)
                val info = json?.let { parseAiSpeciesInfo(it) }
                if (info != null) return info
            }
            val apiConfig = api ?: return null
            val raw = runCatching { client.call(apiConfig, prompt, maxTokens = 650) }.getOrNull() ?: return null
            val json = extractFinalSpeciesJson(raw) ?: return null
            val info = parseAiSpeciesInfo(json) ?: return null
            val tax = Taxonomy(
                lvl1 = normalizeLvl1Name(info.lvl1.orEmpty()),
                lvl2 = info.lvl2?.trim().orEmpty(),
                lvl3 = info.lvl3?.trim().orEmpty(),
                lvl4 = info.lvl4?.trim().orEmpty(),
                lvl5 = info.lvl5?.trim().orEmpty(),
            )
            runCatching {
                aiCacheRepo.upsertSpeciesInfo(
                    apiTag = apiTag,
                    nameCn = nameCn,
                    nameLatin = info.nameLatin,
                    wetWeightMg = info.wetWeightMg,
                    taxonomy = tax,
                    prompt = prompt,
                    raw = raw,
                )
            }
            return info
        }

        fun buildWetWeightEntry(nameCn: String, nameLatin: String?, wetWeightMg: Double, taxonomy: Taxonomy?): WetWeightEntry {
            val t = taxonomy ?: Taxonomy()
            return WetWeightEntry(
                nameCn = nameCn,
                nameLatin = nameLatin?.trim().takeIf { !it.isNullOrBlank() },
                wetWeightMg = wetWeightMg,
                taxonomy = WetWeightTaxonomy(
                    group = t.lvl1.trim().takeIf { it.isNotBlank() },
                    sub = t.lvl4.trim().takeIf { it.isNotBlank() },
                ),
            )
        }

        fun hasTaxonomy(t: Taxonomy): Boolean {
            return listOf(t.lvl1, t.lvl2, t.lvl3, t.lvl4, t.lvl5).any { it.isNotBlank() }
        }

        fun buildLookupNames(nameCn: String): List<String> {
            val key = nameCn.trim()
            if (key.isBlank()) return emptyList()
            val canonical = aliasMap[key].orEmpty()
            return buildList {
                add(key)
                if (canonical.isNotBlank() && canonical != key) add(canonical)
            }
        }

        for ((i, edit) in resolvedEdits.withIndex()) {
            when (edit) {
                is ResolvedEdit.AutofillWetWeight -> {
                    applyProgress = "补齐湿重 ${i + 1}/${resolvedEdits.size}：${edit.speciesNameCn}"
                    val name = edit.speciesNameCn
                    val existing = ds.species.firstOrNull { it.nameCn.trim() == name.trim() }
                    val latin = existing?.nameLatin?.takeIf { it.isNotBlank() }
                    when (edit.source) {
                        FillSource.Local -> {
                            val lookupNames = buildLookupNames(name)
                            var entry: WetWeightEntry? = null
                            for (n in lookupNames) {
                                entry = runCatching { wetWeightRepo.findByNameCn(n) }.getOrNull()
                                if (entry != null) break
                            }
                            val value = entry?.wetWeightMg
                            if (value == null || !value.isFinite() || value <= 0) {
                                errors += "湿重库未找到：$name"
                                continue
                            }
                            finalEdits += ResolvedEdit.SetWetWeight(
                                speciesNameCn = name,
                                value = value,
                                nameLatin = entry?.nameLatin,
                                writeToDb = edit.writeToDb,
                                onlyIfBlank = true,
                            )
                            if (edit.writeToDb) {
                                wetWriteMap[name] = buildWetWeightEntry(name, entry?.nameLatin, value, existing?.taxonomy)
                            }
                        }

                        FillSource.Api -> {
                            if (api == null || !apiConfigured(api)) {
                                errors += "API 未配置：$name"
                                continue
                            }
                            val prompt = buildSpeciesWetWeightAutofillPrompt(name, latin)
                            val info = callAiSpeciesInfo(name, prompt)
                            val value = info?.wetWeightMg
                            if (value == null || !value.isFinite() || value <= 0) {
                                errors += "AI 未返回湿重：$name"
                                continue
                            }
                            finalEdits += ResolvedEdit.SetWetWeight(
                                speciesNameCn = name,
                                value = value,
                                nameLatin = info.nameLatin,
                                writeToDb = edit.writeToDb,
                                onlyIfBlank = true,
                            )
                            if (edit.writeToDb) {
                                wetWriteMap[name] = buildWetWeightEntry(name, info.nameLatin, value, existing?.taxonomy)
                            }
                        }
                    }
                }

                is ResolvedEdit.AutofillTaxonomy -> {
                    applyProgress = "补齐分类 ${i + 1}/${resolvedEdits.size}：${edit.speciesNameCn}"
                    val name = edit.speciesNameCn
                    val existing = ds.species.firstOrNull { it.nameCn.trim() == name.trim() }
                    val latin = existing?.nameLatin?.takeIf { it.isNotBlank() }
                    when (edit.source) {
                        FillSource.Local -> {
                            val lookupNames = buildLookupNames(name)
                            var entry: TaxonomyRecord? = null
                            for (n in lookupNames) {
                                entry = runCatching { taxonomyOverrideRepo.findCustomByNameCn(n) }.getOrNull()
                                    ?: runCatching { taxonomyRepo.findEntryByNameCn(n) }.getOrNull()
                                        ?.let { TaxonomyRecord(nameCn = it.nameCn, nameLatin = it.nameLatin, taxonomy = it.taxonomy) }
                                if (entry != null) break
                            }
                            val tax = entry?.taxonomy
                            if (entry == null || tax == null || !hasTaxonomy(tax)) {
                                errors += "分类库未找到：$name"
                                continue
                            }
                            finalEdits += ResolvedEdit.SetTaxonomy(
                                speciesNameCn = name,
                                taxonomy = tax,
                                nameLatin = entry.nameLatin,
                                writeToDb = edit.writeToDb,
                                onlyIfBlank = true,
                            )
                            if (edit.writeToDb) {
                                taxWriteMap[name] = TaxonomyRecord(nameCn = name, nameLatin = entry.nameLatin, taxonomy = tax)
                            }
                        }

                        FillSource.Api -> {
                            if (api == null || !apiConfigured(api)) {
                                errors += "API 未配置：$name"
                                continue
                            }
                            val prompt = buildSpeciesTaxonomyAutofillPrompt(name, latin)
                            val info = callAiSpeciesInfo(name, prompt)
                            val tax = info?.let {
                                Taxonomy(
                                    lvl1 = normalizeLvl1Name(it.lvl1.orEmpty()),
                                    lvl2 = it.lvl2?.trim().orEmpty(),
                                    lvl3 = it.lvl3?.trim().orEmpty(),
                                    lvl4 = it.lvl4?.trim().orEmpty(),
                                    lvl5 = it.lvl5?.trim().orEmpty(),
                                )
                            }
                            if (tax == null || !hasTaxonomy(tax)) {
                                errors += "AI 未返回分类：$name"
                                continue
                            }
                            finalEdits += ResolvedEdit.SetTaxonomy(
                                speciesNameCn = name,
                                taxonomy = tax,
                                nameLatin = info.nameLatin,
                                writeToDb = edit.writeToDb,
                                onlyIfBlank = true,
                            )
                            if (edit.writeToDb) {
                                taxWriteMap[name] = TaxonomyRecord(nameCn = name, nameLatin = info.nameLatin, taxonomy = tax)
                            }
                        }
                    }
                }

                else -> finalEdits += edit
            }
        }

        if (finalEdits.isEmpty()) {
            message = errors.joinToString("\n").ifBlank { "没有可应用的指令" }
            busy = false
            applyProgress = null
            return
        }

        for (edit in finalEdits) {
            when (edit) {
                is ResolvedEdit.SetWetWeight -> {
                    if (!edit.writeToDb) continue
                    val existing = ds.species.firstOrNull { it.nameCn.trim() == edit.speciesNameCn.trim() }
                    val latin = edit.nameLatin ?: existing?.nameLatin
                    wetWriteMap.putIfAbsent(
                        edit.speciesNameCn,
                        buildWetWeightEntry(edit.speciesNameCn, latin, edit.value, existing?.taxonomy),
                    )
                }

                is ResolvedEdit.SetTaxonomy -> {
                    if (!edit.writeToDb || !hasTaxonomy(edit.taxonomy)) continue
                    val existing = ds.species.firstOrNull { it.nameCn.trim() == edit.speciesNameCn.trim() }
                    val latin = edit.nameLatin ?: existing?.nameLatin
                    taxWriteMap.putIfAbsent(
                        edit.speciesNameCn,
                        TaxonomyRecord(nameCn = edit.speciesNameCn, nameLatin = latin, taxonomy = edit.taxonomy),
                    )
                }

                else -> Unit
            }
        }

        var mergeNote: String? = null
        viewModel.updateCurrentDataset { cur ->
            var next = cur

            fun ensureSpecies(nameCn: String): Species {
                val key = nameCn.trim()
                val existing = next.species.firstOrNull { it.nameCn.trim() == key }
                if (existing != null) return existing
                val sp = Species(
                    id = newId(),
                    nameCn = key,
                    nameLatin = "",
                    taxonomy = Taxonomy(),
                    avgWetWeightMg = null,
                    countsByPointId = next.points.associate { it.id to 0 },
                )
                next = next.copy(species = next.species + sp)
                return sp
            }

            fun setCount(speciesId: String, pointId: String, v: Int) {
                val nv = v.coerceAtLeast(0)
                next = next.copy(
                    species = next.species.map { sp ->
                        if (sp.id != speciesId) sp else sp.copy(countsByPointId = sp.countsByPointId + (pointId to nv))
                    },
                )
            }

            fun deltaCount(speciesId: String, pointId: String, delta: Int) {
                val sp = next.species.firstOrNull { it.id == speciesId } ?: return
                val prev = sp.countsByPointId[pointId] ?: 0
                setCount(speciesId, pointId, prev + delta)
            }

            fun applyTaxonomy(cur: Taxonomy, incoming: Taxonomy, onlyIfBlank: Boolean): Taxonomy {
                fun pick(current: String, inc: String): String {
                    return if (onlyIfBlank) {
                        if (current.isBlank()) inc else current
                    } else {
                        if (inc.isBlank()) current else inc
                    }
                }
                return Taxonomy(
                    lvl1 = pick(cur.lvl1, incoming.lvl1),
                    lvl2 = pick(cur.lvl2, incoming.lvl2),
                    lvl3 = pick(cur.lvl3, incoming.lvl3),
                    lvl4 = pick(cur.lvl4, incoming.lvl4),
                    lvl5 = pick(cur.lvl5, incoming.lvl5),
                )
            }

            for (e in finalEdits) {
                when (e) {
                    is ResolvedEdit.AddPoint -> {
                        if (next.points.any { normalizePointLabel(it.label) == normalizePointLabel(e.point.label) }) continue
                        val point = e.point
                        next = next.copy(
                            points = next.points + point,
                            species = next.species.map { sp ->
                                if (sp.countsByPointId.containsKey(point.id)) sp
                                else sp.copy(countsByPointId = sp.countsByPointId + (point.id to 0))
                            },
                        )
                    }

                    is ResolvedEdit.DeletePoint -> {
                        if (next.points.none { it.id == e.pointId }) continue
                        next = next.copy(
                            points = next.points.filterNot { it.id == e.pointId },
                            species = next.species.map { sp -> sp.copy(countsByPointId = sp.countsByPointId - e.pointId) },
                        )
                    }

                    is ResolvedEdit.RenamePoint -> {
                        if (next.points.none { it.id == e.pointId }) continue
                        next = next.copy(
                            points = next.points.map { p ->
                                if (p.id != e.pointId) p else p.copy(label = e.label, site = e.site, depthM = e.depthM)
                            },
                        )
                    }

                    is ResolvedEdit.UpdatePoint -> {
                        if (next.points.none { it.id == e.pointId }) continue
                        next = next.copy(
                            points = next.points.map { p ->
                                if (p.id != e.pointId) p
                                else p.copy(vConcMl = e.vc ?: p.vConcMl, vOrigL = e.vo ?: p.vOrigL)
                            },
                        )
                    }

                    is ResolvedEdit.AddSpecies -> {
                        ensureSpecies(e.nameCn)
                    }

                    is ResolvedEdit.DeleteSpecies -> {
                        if (!confirmedDeleteAll) continue
                        next = next.copy(species = next.species.filterNot { it.id == e.speciesId })
                    }

                    is ResolvedEdit.RenameSpecies -> {
                        if (next.species.none { it.id == e.speciesId }) continue
                        next = next.copy(
                            species = next.species.map { sp -> if (sp.id == e.speciesId) sp.copy(nameCn = e.nameCn) else sp },
                        )
                    }

                    is ResolvedEdit.SetCount -> {
                        if (next.points.none { it.id == e.pointId }) continue
                        val sp = ensureSpecies(e.speciesNameCn)
                        setCount(sp.id, e.pointId, e.value)
                    }

                    is ResolvedEdit.DeltaCount -> {
                        if (next.points.none { it.id == e.pointId }) continue
                        val sp = ensureSpecies(e.speciesNameCn)
                        deltaCount(sp.id, e.pointId, e.delta)
                    }

                    is ResolvedEdit.SetWetWeight -> {
                        val sp = ensureSpecies(e.speciesNameCn)
                        val shouldApply = !e.onlyIfBlank || sp.avgWetWeightMg == null
                        if (!shouldApply) continue
                        val nextLatin = if (sp.nameLatin.isBlank() && !e.nameLatin.isNullOrBlank()) e.nameLatin else sp.nameLatin
                        next = next.copy(
                            species = next.species.map { s ->
                                if (s.id != sp.id) s else s.copy(avgWetWeightMg = e.value, nameLatin = nextLatin)
                            },
                        )
                    }

                    is ResolvedEdit.SetTaxonomy -> {
                        val sp = ensureSpecies(e.speciesNameCn)
                        val nextTax = applyTaxonomy(sp.taxonomy, e.taxonomy, e.onlyIfBlank)
                        val nextLatin = if (sp.nameLatin.isBlank() && !e.nameLatin.isNullOrBlank()) e.nameLatin else sp.nameLatin
                        next = next.copy(
                            species = next.species.map { s ->
                                if (s.id != sp.id) s else s.copy(taxonomy = nextTax, nameLatin = nextLatin)
                            },
                        )
                    }

                    else -> Unit
                }
            }
            val merged = mergeDuplicateSpeciesByName(next, MergeCountsMode.Max)
            if (merged.mergedCount > 0) {
                mergeNote = "已合并同名物种 ${merged.mergedCount} 条（计数取最大值）"
            }
            merged.dataset
        }

        if (wetWriteMap.isNotEmpty() || taxWriteMap.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                for (entry in wetWriteMap.values) {
                    runCatching { wetWeightRepo.upsertAutoMatched(entry) }
                }
                for (record in taxWriteMap.values) {
                    runCatching { taxonomyOverrideRepo.upsertCustom(record) }
                }
            }
        }

        val errText = errors.takeIf { it.isNotEmpty() }?.joinToString("\n")
        message = when {
            errText != null && mergeNote != null -> "已应用部分指令，但有异常：\n$errText\n$mergeNote"
            errText != null -> "已应用部分指令，但有异常：\n$errText"
            mergeNote != null -> "已应用批量指令\n$mergeNote"
            else -> "已应用批量指令"
        }
        busy = false
        applyProgress = null
    }

    fun launchVoiceAssistant() {
        val requestId = UUID.randomUUID().toString()
        val request = VoiceAssistantRequest(
            requestId = requestId,
            inputType = VoiceAssistantContract.INPUT_TYPE_VOICE,
            inputText = null,
            template = if (parseTemplate == ParseTemplate.Counts) {
                VoiceAssistantContract.TEMPLATE_COUNTS
            } else {
                VoiceAssistantContract.TEMPLATE_GENERAL
            },
            contextUri = null,
            returnAction = VoiceAssistantContract.ACTION_RECEIVE_BULK_COMMAND,
            returnPackage = context.packageName,
            requireConfirm = true,
        )
        val intent = buildRequestIntent(request).apply {
            setPackage("com.voiceassistant")
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            Toast.makeText(context, "未安装语音助手（com.voiceassistant）", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.registerVoiceAssistantRequest(requestId)
        runCatching { context.startActivity(intent) }.onFailure {
            viewModel.cancelVoiceAssistantRequest(requestId)
            Toast.makeText(context, "启动语音助手失败：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        val list = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val text = list?.firstOrNull()?.trim().orEmpty()
        if (text.isNotBlank()) {
            inputText = if (inputText.isBlank()) text else inputText.trimEnd() + "\n" + text
        }
    }

    val excelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || busy) return@rememberLauncherForActivityResult
        busy = true
        message = null
        scope.launch {
            val res = runCatching {
                val aliasMap = runCatching {
                    aliasRepo.getAll().associate { it.alias.trim() to it.canonicalNameCn.trim() }
                }.getOrElse { emptyMap() }
                val candidates = buildNameCandidates()
                var corrected = 0
                val resolver: (String) -> String = { raw ->
                    val t = raw.trim()
                    if (t.isEmpty()) {
                        t
                    } else {
                        val alias = aliasMap[t]
                        if (!alias.isNullOrBlank()) {
                            if (alias != t) corrected += 1
                            alias
                        } else {
                            if (!autoCorrectNames) {
                                t
                            } else {
                                val best = bestCanonicalName(t, candidates)
                                if (best != null && best.score >= 0.90) {
                                    if (best.canonical != t) corrected += 1
                                    best.canonical
                                } else {
                                    t
                                }
                            }
                        }
                    }
                }
                val (next, summary) = mergeCountsFromExcelIntoDataset(
                    contentResolver = contentResolver,
                    uri = uri,
                    dataset = ds,
                    defaultVOrigL = settings.defaultVOrigL,
                    options = CountMergeOptions(
                        createMissingPoints = excelCreateMissingPoints,
                        overwriteCounts = excelOverwriteCounts,
                    ),
                    resolveSpeciesNameCn = resolver,
                )
                viewModel.updateCurrentDataset { next }
                summary to corrected
            }
            message = res.fold(
                onSuccess = { (s, corrected) ->
                    val corr = if (corrected > 0) " · 已纠错物种名 $corrected" else ""
                    "Excel 导入完成（${s.format}）：新增点位 ${s.pointsAdded} · 新增物种 ${s.speciesAdded} · 写入单元格 ${s.cellsUpdated} · 忽略 ${s.ignoredCells}$corr"
                },
                onFailure = { "Excel 导入失败：${it.message ?: it.toString()}" },
            )
            busy = false
        }
    }
    val applyEnabled = !busy && resolvedEdits.isNotEmpty() && (!requireCorrectionConfirm || pendingCorrections.isEmpty())
    fun rerunPreviewFromCache() {
        if (busy) return
        busy = true
        scope.launch {
            runCatching { buildPreviewAndCommands(reuseParsed = true) }.onFailure {
                preview = listOf(PreviewLine("解析失败：${it.message}", PreviewLine.Kind.Error))
            }
            busy = false
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = {
            Button(
                enabled = applyEnabled,
                onClick = {
                    if (resolvedEdits.isEmpty()) {
                        Toast.makeText(context, "请先点击“解析预览”", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (pendingDeleteNames.isNotEmpty()) {
                        confirmDeleteDialog = true
                    } else {
                        scope.launch {
                            applyEdits(confirmedDeleteAll = true)
                            Toast.makeText(context, "已应用批量指令", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            ) { Text("应用") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text("关闭") } },
        title = { Text("批量录入") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (applyProgress != null) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(applyProgress!!, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (message != null) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(message!!, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("语音 / 文字指令", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "示例：新增点位 1-0.3；把1号点位的湖沼砂壳虫改为5；无节幼体增加2；补齐无节幼体湿重（本机/AI）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                        if (voicePayload != null) {
                            val statusLabel = when (voicePayload.status) {
                                VoiceAssistantContract.STATUS_OK -> "成功"
                                VoiceAssistantContract.STATUS_ERROR -> "失败"
                                VoiceAssistantContract.STATUS_CANCEL -> "已取消"
                                else -> voicePayload.status
                            }
                            val matchHint = if (!voicePayload.requestMatched) "（非当前请求）" else ""
                            Text("语音助手结果：$statusLabel$matchHint", style = MaterialTheme.typography.bodySmall)
                            if (incomingWarnings.isNotBlank()) {
                                Text("提示：$incomingWarnings", style = MaterialTheme.typography.bodySmall)
                            }
                            if (incomingError.isNotBlank()) {
                                Text(
                                    "错误：$incomingError",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            enabled = !busy,
                            label = { Text("输入指令（可多行）") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 6,
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                enabled = !busy,
                                onClick = {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "说出批量录入指令")
                                    }
                                    runCatching { voiceLauncher.launch(intent) }.onFailure {
                                        Toast.makeText(context, "无法启动语音识别：${it.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Outlined.Mic, contentDescription = "语音", modifier = Modifier.padding(end = 8.dp))
                                Text("语音")
                            }
                            Button(
                                enabled = !busy,
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        runCatching { buildPreviewAndCommands() }.onFailure {
                                            preview = listOf(PreviewLine("解析失败：${it.message}", PreviewLine.Kind.Error))
                                        }
                                        busy = false
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                if (busy) {
                                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                                    Text("解析中…")
                                } else {
                                    Text("解析预览")
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                enabled = !busy,
                                onClick = { launchVoiceAssistant() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("语音助手")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("解析方式", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(0.dp))
                                Box {
                                    OutlinedButton(
                                        enabled = !busy,
                                        onClick = { parseModeMenuOpen = true },
                                        modifier = Modifier.padding(start = 8.dp),
                                    ) { Text(parseMode.label) }
                                    DropdownMenu(
                                        expanded = parseModeMenuOpen,
                                        onDismissRequest = { parseModeMenuOpen = false },
                                    ) {
                                        DropdownMenuItem(text = { Text("本地规则") }, onClick = {
                                            parseMode = ParseMode.Local
                                            parseModeMenuOpen = false
                                        })
                                        DropdownMenuItem(text = { Text("API1") }, onClick = {
                                            parseMode = ParseMode.Api1
                                            parseModeMenuOpen = false
                                        })
                                        DropdownMenuItem(text = { Text("API2") }, onClick = {
                                            parseMode = ParseMode.Api2
                                            parseModeMenuOpen = false
                                        })
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("模板", style = MaterialTheme.typography.bodySmall)
                                Box {
                                    OutlinedButton(
                                        enabled = !busy && parseMode != ParseMode.Local,
                                        onClick = { templateMenuOpen = true },
                                        modifier = Modifier.padding(start = 8.dp),
                                    ) { Text(parseTemplate.label) }
                                    DropdownMenu(
                                        expanded = templateMenuOpen,
                                        onDismissRequest = { templateMenuOpen = false },
                                    ) {
                                        DropdownMenuItem(text = { Text("综合模板") }, onClick = {
                                            parseTemplate = ParseTemplate.General
                                            templateMenuOpen = false
                                        })
                                        DropdownMenuItem(text = { Text("计数模板") }, onClick = {
                                            parseTemplate = ParseTemplate.Counts
                                            templateMenuOpen = false
                                        })
                                    }
                                }
                            }
                        }

                        if (parseMode != ParseMode.Local) {
                            val api = if (parseMode == ParseMode.Api1) settings.api1 else settings.api2
                            val warn = when {
                                !settings.aiAssistantEnabled -> "请先在设置中开启 AI 功能。"
                                !apiConfigured(api) -> "未配置 Base URL / Model。"
                                else -> null
                            }
                            if (warn != null) {
                                Text(warn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        if (parseMode != ParseMode.Local && lastAiRaw != null) {
                            TextButton(enabled = !busy, onClick = { rawDialogOpen = true }) {
                                Text("查看原始响应（${lastAiRawLabel ?: parseMode.label}）")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = autoCorrectNames, onCheckedChange = { autoCorrectNames = it }, enabled = !busy)
                            Text("自动纠错名称（物种/点位）")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = requireCorrectionConfirm,
                                onCheckedChange = { requireCorrectionConfirm = it },
                                enabled = !busy,
                            )
                            Text("纠错需确认（进入待校准列表）")
                        }

                        if (preview.isNotEmpty()) {
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    for (p in preview.take(14)) {
                                        val color = when (p.kind) {
                                            PreviewLine.Kind.Normal -> MaterialTheme.colorScheme.onBackground
                                            PreviewLine.Kind.Warn -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                            PreviewLine.Kind.Error -> MaterialTheme.colorScheme.error
                                        }
                                        Text(p.text, style = MaterialTheme.typography.bodySmall, color = color)
                                    }
                                    if (preview.size > 14) {
                                        Text(
                                            "…（共 ${preview.size} 行）",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                        )
                                    }
                                }
                            }
                        }

                        if (pendingCorrections.isNotEmpty()) {
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("待校准列表", style = MaterialTheme.typography.bodySmall)
                                    for (item in pendingCorrections.take(6)) {
                                        val kindLabel = if (item.kind == CorrectionKind.Species) "物种" else "点位"
                                        val scoreText = item.score?.let { "（${(it * 100).toInt()}%）" }.orEmpty()
                                        Text(
                                            "• $kindLabel：${item.raw} → ${item.suggestion}$scoreText",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Text(
                                            item.reason,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                enabled = !busy,
                                                onClick = {
                                                    when (item.kind) {
                                                        CorrectionKind.Species -> {
                                                            manualSpeciesOverrides = manualSpeciesOverrides + (item.raw to item.suggestion)
                                                            manualSpeciesKeep = manualSpeciesKeep - item.raw
                                                        }
                                                        CorrectionKind.Point -> {
                                                            manualPointOverrides = manualPointOverrides + (item.raw to item.suggestion)
                                                            manualPointKeep = manualPointKeep - item.raw
                                                        }
                                                    }
                                                    rerunPreviewFromCache()
                                                },
                                            ) { Text("采用校正") }
                                            TextButton(
                                                enabled = !busy,
                                                onClick = {
                                                    when (item.kind) {
                                                        CorrectionKind.Species -> {
                                                            manualSpeciesKeep = manualSpeciesKeep + item.raw
                                                            manualSpeciesOverrides = manualSpeciesOverrides - item.raw
                                                        }
                                                        CorrectionKind.Point -> {
                                                            manualPointKeep = manualPointKeep + item.raw
                                                            manualPointOverrides = manualPointOverrides - item.raw
                                                        }
                                                    }
                                                    rerunPreviewFromCache()
                                                },
                                            ) { Text("保留原词") }
                                        }
                                    }
                                    if (pendingCorrections.size > 6) {
                                        Text(
                                            "…（共 ${pendingCorrections.size} 条）",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                        )
                                    }
                                    if (requireCorrectionConfirm && pendingCorrections.isNotEmpty()) {
                                        Text(
                                            "提示：处理完待校准项后才可应用。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }

                        if (pendingItems.isNotEmpty()) {
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("待确认列表", style = MaterialTheme.typography.bodySmall)
                                    for (item in pendingItems.take(8)) {
                                        Text("• ${item.text}（${item.reason}）", style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (pendingItems.size > 8) {
                                        Text(
                                            "…（共 ${pendingItems.size} 条）",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                        )
                                    }
                                }
                            }
                        }
                        if (!busy && inputText.trim().isNotBlank() && preview.isNotEmpty() && resolvedEdits.isEmpty() && pendingCorrections.isEmpty()) {
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "解析结果为 0：没有可应用指令，请修改指令或切换模板/解析方式。",
                                    modifier = Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Excel 批量导入计数", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "支持：表1.xlsx（浮游动物计数 sheet）/ 简表（四大类+物种）/ “点位-物种-计数”简易表。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Row {
                                Switch(checked = excelOverwriteCounts, onCheckedChange = { excelOverwriteCounts = it }, enabled = !busy)
                                Text(if (excelOverwriteCounts) "覆盖计数" else "叠加计数")
                            }
                            Row {
                                Switch(checked = excelCreateMissingPoints, onCheckedChange = { excelCreateMissingPoints = it }, enabled = !busy)
                                Text("新增缺失点位")
                            }
                        }

                        OutlinedButton(
                            enabled = !busy,
                            onClick = { excelLauncher.launch(arrayOf(MIME_XLSX)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.UploadFile, contentDescription = "导入")
                            Text("选择 Excel 并导入")
                        }
                    }
                }
            }
        },
    )

    if (confirmDeleteDialog) {
        AlertDialog(
            onDismissRequest = { confirmDeleteDialog = false },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            confirmDeleteDialog = false
                            scope.launch {
                                applyEdits(confirmedDeleteAll = false)
                                Toast.makeText(context, "已应用（未删除物种）", Toast.LENGTH_SHORT).show()
                            }
                        },
                    ) { Text("仅应用其它") }
                    Button(
                        onClick = {
                            confirmDeleteDialog = false
                            scope.launch {
                                applyEdits(confirmedDeleteAll = true)
                                Toast.makeText(context, "已应用批量指令", Toast.LENGTH_SHORT).show()
                            }
                        },
                    ) { Text("确认删除") }
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteDialog = false }) { Text("取消") } },
            title = { Text("确认删除物种") },
            text = { Text("将从当前数据集移除：${pendingDeleteNames.take(8).joinToString()}${if (pendingDeleteNames.size > 8) "…" else ""}") },
        )
    }

    if (rawDialogOpen && lastAiRaw != null) {
        AlertDialog(
            onDismissRequest = { rawDialogOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(lastAiRaw ?: ""))
                        rawDialogOpen = false
                    },
                ) { Text("复制") }
            },
            dismissButton = { TextButton(onClick = { rawDialogOpen = false }) { Text("关闭") } },
            title = { Text("原始响应（${lastAiRawLabel ?: "API"}）") },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    SelectionContainer {
                        Text(lastAiRaw ?: "", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
        )
    }
}
