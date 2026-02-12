package com.plankton.one102.ui.components

import android.Manifest
import android.content.Intent
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plankton.one102.PlanktonApplication
import com.plankton.one102.domain.ApiConfig
import com.plankton.one102.domain.LVL1_ORDER
import com.plankton.one102.domain.NameCorrection
import com.plankton.one102.domain.VoiceAssistantConfig
import com.plankton.one102.domain.buildPointTrace
import com.plankton.one102.domain.bestCanonicalName
import com.plankton.one102.domain.parseIntSmart
import com.plankton.one102.domain.UiMode
import com.plankton.one102.domain.validateDataset
import com.plankton.one102.ui.DatabaseViewModel
import com.plankton.one102.ui.MainViewModel
import com.plankton.one102.ui.PreviewCommand
import com.plankton.one102.ui.buildAssistantContextWithDocs
import com.plankton.one102.ui.components.AiRichText
import com.plankton.one102.voiceassistant.VoiceAssistantAudioShare
import com.plankton.one102.voiceassistant.VoiceAssistantContract
import com.plankton.one102.voiceassistant.VoiceAssistantRecorder
import com.plankton.one102.voiceassistant.VoiceAssistantResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

private fun apiMatches(state: com.plankton.one102.ui.ApiHealthState?, api: ApiConfig): Boolean {
    return state != null && state.baseUrl == api.baseUrl && state.model == api.model
}

private fun normalizeCommand(text: String): String {
    return text.trim()
        .replace("－", "-")
        .replace("—", "-")
        .replace("–", "-")
        .replace("＿", "_")
        .replace(" ", "")
        .lowercase()
}

private val POINT_NUMERAL_PREFIX = Regex("""(点位|采样点|站位|点)\s*([一二三四五六七八九十百千零两]+)""")
private val POINT_NUMERAL_SUFFIX = Regex("""([一二三四五六七八九十百千零两]+)\s*(点位|采样点|站位|点)""")
private val POINT_DUPLICATE = Regex("""(\d+)\s*点位\s*\1""")

private fun normalizePointNumberTokens(text: String): String {
    var updated = text
    updated = POINT_NUMERAL_PREFIX.replace(updated) { m ->
        val value = parseIntSmart(m.groupValues[2]).coerceAtLeast(0)
        "${m.groupValues[1]}$value"
    }
    updated = POINT_NUMERAL_SUFFIX.replace(updated) { m ->
        val value = parseIntSmart(m.groupValues[1]).coerceAtLeast(0)
        "${value}${m.groupValues[2]}"
    }
    updated = POINT_DUPLICATE.replace(updated) { m ->
        "点位${m.groupValues[1]}"
    }
    return updated
}

private val QUERY_STOP_TOKENS = setOf(
    "点位",
    "采样点",
    "点",
    "物种",
    "数量",
    "计数",
    "增加",
    "减少",
    "新增",
    "删除",
    "改为",
    "设置",
    "打开",
    "导出",
    "查看",
    "查询",
    "分析",
    "检测",
    "平板",
    "手机",
    "自动",
    "模式",
    "表1",
    "表2",
    "表一",
    "表二",
)

private val QUERY_TOKEN_REGEX = Regex("""[\p{IsHan}A-Za-z0-9_.-]{2,}""")

private fun extractQueryTokens(text: String): List<String> {
    return QUERY_TOKEN_REGEX.findAll(text).map { it.value }.toList()
}

private fun queryCorrectionThreshold(token: String): Double {
    return when {
        token.length <= 2 -> 0.9
        token.length == 3 -> 0.82
        else -> 0.74
    }
}

private fun applyFuzzyCorrections(
    text: String,
    aliasMap: Map<String, String>,
    candidates: Collection<String>,
): Pair<String, List<NameCorrection>> {
    val tokens = extractQueryTokens(text)
    if (tokens.isEmpty()) return text to emptyList()
    val replacements = LinkedHashMap<String, String>()
    val corrections = mutableListOf<NameCorrection>()

    for (raw in tokens) {
        val token = raw.trim()
        if (token.isBlank() || QUERY_STOP_TOKENS.contains(token)) continue
        val alias = aliasMap[token]
        if (!alias.isNullOrBlank() && alias != token) {
            replacements[token] = alias
            corrections += NameCorrection(raw = token, canonical = alias, score = 0.99)
            continue
        }
        val correction = bestCanonicalName(token, candidates) ?: continue
        val threshold = queryCorrectionThreshold(token)
        if (correction.score >= threshold && correction.canonical != token) {
            replacements[token] = correction.canonical
            corrections += correction
        }
    }

    if (replacements.isEmpty()) return text to emptyList()
    val sorted = replacements.entries.sortedByDescending { it.key.length }
    var updated = text
    for ((raw, canonical) in sorted) {
        updated = updated.replace(raw, canonical)
    }
    return updated to corrections
}

private fun formatCorrections(corrections: List<NameCorrection>, maxItems: Int = 4): String {
    if (corrections.isEmpty()) return ""
    val items = corrections.take(maxItems).joinToString("、") { "${it.raw}→${it.canonical}" }
    val suffix = if (corrections.size > maxItems) "…等 ${corrections.size} 条" else ""
    return "已自动校正：$items$suffix"
}

private data class MatchedPoint(val id: String, val label: String)

private enum class VoiceInputMode {
    Command,
    Query,
}

private data class AskNotice(
    val id: String,
    val title: String,
    val body: String,
)

@Composable
private fun AskNoticeCard(
    title: String,
    onOpen: () -> Unit,
    onClose: () -> Unit,
) {
    GlassCard(
        modifier = Modifier
            .widthIn(min = 220.dp, max = 320.dp)
            .clickable { onOpen() },
        elevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f))
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("结果已生成，点击查看。", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "关闭")
            }
        }
    }
}

private sealed class GlobalAction {
    data object OpenSettings : GlobalAction()
    data object OpenAssistant : GlobalAction()
    data object OpenPreview : GlobalAction()
    data object OpenPoints : GlobalAction()
    data object OpenSpecies : GlobalAction()
    data object OpenDatasets : GlobalAction()
    data object OpenDatabase : GlobalAction()
    data object OpenDatabaseTree : GlobalAction()
    data object OpenMindMap : GlobalAction()
    data object OpenFocus : GlobalAction()
    data object OpenAliases : GlobalAction()
    data object OpenAiCache : GlobalAction()
    data object OpenDocs : GlobalAction()
    data object OpenWetWeights : GlobalAction()
    data object OpenCharts : GlobalAction()
    data object CheckApi : GlobalAction()
    data object AiAnalyze : GlobalAction()
    data class SetUiMode(val mode: UiMode) : GlobalAction()
    data class TracePoint(val pointId: String, val label: String) : GlobalAction()
    data object ExportTable1 : GlobalAction()
    data object ExportTable2 : GlobalAction()
    data object PreviewTable1 : GlobalAction()
    data object PreviewTable2 : GlobalAction()
    data class ExportMindMap(val scopeLevel: String, val scopeValue: String?) : GlobalAction()
}

@Composable
fun GlobalAssistantOverlay(
    viewModel: MainViewModel,
    databaseViewModel: DatabaseViewModel,
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as PlanktonApplication
    val voicePayload by viewModel.voiceAssistantPayload.collectAsStateWithLifecycle()
    val dataset by viewModel.currentDataset.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val voiceConfig = settings.voiceAssistant
    val assistantState by viewModel.assistantState.collectAsStateWithLifecycle()
    val api1HealthState by viewModel.api1Health.collectAsStateWithLifecycle()
    val api2HealthState by viewModel.api2Health.collectAsStateWithLifecycle()
    val activePointId by viewModel.activePointId.collectAsStateWithLifecycle()

    val haptics = LocalHapticFeedback.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showVoiceSettings by remember { mutableStateOf(false) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var inputText by rememberSaveable { mutableStateOf("") }
    var commandMessage by remember { mutableStateOf<String?>(null) }
    var commandError by remember { mutableStateOf<String?>(null) }
    var askMessage by remember { mutableStateOf<String?>(null) }
    var fullTextTitle by remember { mutableStateOf("") }
    var fullTextBody by remember { mutableStateOf<String?>(null) }
    var panelWidthPx by remember { mutableStateOf(0) }
    var pendingVoiceMode by remember { mutableStateOf<VoiceInputMode?>(null) }
    var longPressActive by remember { mutableStateOf(false) }
    var longPressMode by remember { mutableStateOf<VoiceInputMode?>(null) }
    var longPressOffset by remember { mutableStateOf(Offset.Zero) }
    var longPressCancelled by remember { mutableStateOf(false) }
    var lastAiError by remember { mutableStateOf<String?>(null) }
    var askNotice1 by remember { mutableStateOf<AskNotice?>(null) }
    var askNotice2 by remember { mutableStateOf<AskNotice?>(null) }
    var lastAnswer1 by remember { mutableStateOf("") }
    var lastAnswer2 by remember { mutableStateOf("") }

    val ds = dataset
    val issues = remember(ds?.id, ds?.updatedAt) { ds?.let { validateDataset(it) } ?: emptyList() }
    val wetWeightRepo = app.wetWeightRepository
    val taxonomyRepo = app.taxonomyRepository
    val taxonomyOverrideRepo = app.taxonomyOverrideRepository
    val aliasRepo = app.aliasRepository
    val aiState = assistantState.ai
    val api1Ok = apiMatches(api1HealthState, settings.api1) && api1HealthState?.ok == true
    val api2Ok = apiMatches(api2HealthState, settings.api2) && api2HealthState?.ok == true

    val recorder = remember { VoiceAssistantRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var recordStartMs by remember { mutableStateOf(0L) }
    var recordElapsedMs by remember { mutableStateOf(0L) }
    var recordMessage by remember { mutableStateOf<String?>(null) }
    var recordFile by remember { mutableStateOf<File?>(null) }
    var recordUri by remember { mutableStateOf<String?>(null) }
    var transcribeBusy by remember { mutableStateOf(false) }
    var transcribeMessage by remember { mutableStateOf<String?>(null) }
    var pendingRequestId by remember { mutableStateOf<String?>(null) }

    val recordGranted = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        recordGranted.value = granted
        if (!granted) {
            Toast.makeText(context, "录音权限未授权", Toast.LENGTH_SHORT).show()
        }
    }

    fun formatDuration(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%02d:%02d".format(min, sec)
    }

    fun updateVoiceConfig(update: (VoiceAssistantConfig) -> VoiceAssistantConfig) {
        viewModel.saveSettings(settings.copy(voiceAssistant = update(settings.voiceAssistant)))
    }

    fun isVoiceAssistantInstalled(): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo("com.voiceassistant", 0)
            true
        }.getOrDefault(false)
    }

    data class AiApiChoice(
        val primary: ApiConfig,
        val secondary: ApiConfig,
        val useDual: Boolean,
    )

    fun canUseAiNow(): Boolean {
        if (settings.aiUiHidden) return false
        if (!settings.aiAssistantEnabled) return false
        return api1Ok || api2Ok
    }

    fun hasApiConfig(api: ApiConfig): Boolean = api.baseUrl.isNotBlank() && api.model.isNotBlank()

    fun resolveAiChoice(
        h1: com.plankton.one102.ui.ApiHealthState? = api1HealthState,
        h2: com.plankton.one102.ui.ApiHealthState? = api2HealthState,
    ): AiApiChoice {
        val api1Ready = apiMatches(h1, settings.api1) && h1?.ok == true
        val api2Ready = apiMatches(h2, settings.api2) && h2?.ok == true
        val has1 = hasApiConfig(settings.api1)
        val useDual = settings.aiUseDualApi && api1Ready && api2Ready
        val primary = when {
            api1Ready -> settings.api1
            api2Ready -> settings.api2
            has1 -> settings.api1
            else -> settings.api2
        }
        val secondary = if (useDual) {
            settings.api2
        } else if (primary == settings.api1) {
            settings.api2
        } else {
            settings.api1
        }
        return AiApiChoice(primary = primary, secondary = secondary, useDual = useDual)
    }

    fun findPointMatch(text: String, activeId: String?): MatchedPoint? {
        val data = ds ?: return null
        val normalized = normalizeCommand(text)
        if (normalized.contains("当前点") || normalized.contains("当前点位") || normalized.contains("当前采样点")) {
            val cur = activeId?.let { id -> data.points.firstOrNull { it.id == id } }
            if (cur != null) {
                val label = cur.label.ifBlank { cur.id }
                return MatchedPoint(cur.id, label)
            }
        }

        val sorted = data.points.sortedByDescending { it.label.length }
        for (p in sorted) {
            val label = p.label.ifBlank { p.id }
            val normalizedLabel = normalizeCommand(label)
            if (normalizedLabel.isNotBlank() && normalized.contains(normalizedLabel)) {
                return MatchedPoint(p.id, label)
            }
        }

        val regex = Regex("(?:点位|采样点|站位|点)\\s*([0-9]+(?:[-.]\\d+)?)")
        val match = regex.find(text)
        if (match != null) {
            val token = normalizeCommand(match.groupValues[1])
            for (p in data.points) {
                val label = normalizeCommand(p.label.ifBlank { p.id })
                if (label == token || label.startsWith(token)) {
                    return MatchedPoint(p.id, p.label.ifBlank { p.id })
                }
            }
        }

        return null
    }

    fun parseMindMapExport(text: String): GlobalAction.ExportMindMap? {
        val normalized = normalizeCommand(text)
        if (!normalized.contains("导图") && !normalized.contains("思维")) return null
        if (!normalized.contains("导出") && !normalized.contains("生成") && !normalized.contains("保存")) return null

        if (normalized.contains("全部") || normalized.contains("全库") || normalized.contains("所有")) {
            return GlobalAction.ExportMindMap(scopeLevel = "All", scopeValue = null)
        }

        val lvl1Hit = LVL1_ORDER.firstOrNull { text.contains(it) }
        if (lvl1Hit != null) {
            return GlobalAction.ExportMindMap(scopeLevel = "Lvl1", scopeValue = lvl1Hit)
        }

        data class LevelSpec(val level: String, val keys: List<String>)
        val specs = listOf(
            LevelSpec("Lvl1", listOf("大类", "类群")),
            LevelSpec("Lvl2", listOf("纲")),
            LevelSpec("Lvl3", listOf("目")),
            LevelSpec("Lvl4", listOf("科")),
            LevelSpec("Lvl5", listOf("属")),
            LevelSpec("Species", listOf("物种", "种类")),
        )
        for (spec in specs) {
            for (key in spec.keys) {
                val regex = Regex("""([\p{IsHan}A-Za-z0-9_.-]{1,16})$key""")
                val match = regex.find(text)
                if (match != null) {
                    val value = match.groupValues[1].trim().ifBlank { null }
                    return GlobalAction.ExportMindMap(scopeLevel = spec.level, scopeValue = value)
                }
                if (normalized.contains(key)) {
                    return GlobalAction.ExportMindMap(scopeLevel = spec.level, scopeValue = null)
                }
            }
        }
        return GlobalAction.ExportMindMap(scopeLevel = "All", scopeValue = null)
    }

    fun parseGlobalAction(text: String): GlobalAction? {
        val normalized = normalizeCommand(text)
        fun has(vararg keys: String): Boolean = keys.any { normalized.contains(it) }

        parseMindMapExport(text)?.let { return it }

        if (has("打开设置", "进入设置", "去设置", "设置页")) return GlobalAction.OpenSettings
        if (has("打开导出", "导出页", "预览页")) return GlobalAction.OpenPreview
        if (has("打开助手", "助手页", "ai监测", "ai助手")) return GlobalAction.OpenAssistant
        if (has("打开采样点", "采样点页", "去采样点")) return GlobalAction.OpenPoints
        if (has("打开物种", "物种页", "去物种页")) return GlobalAction.OpenSpecies
        if (has("历史数据集", "数据集列表", "打开数据集")) return GlobalAction.OpenDatasets
        if (has("打开数据库树", "数据库树", "分类树", "树状数据库")) return GlobalAction.OpenDatabaseTree
        if (has("打开思维导图", "分类思维导图", "思维导图页", "思维导图")) return GlobalAction.OpenMindMap
        if (has("打开数据库", "数据库页", "物种库")) return GlobalAction.OpenDatabase
        if (has("打开专注录入", "专注录入", "专注页", "专注模式")) return GlobalAction.OpenFocus
        if (has("别名管理", "别名库", "别名页")) return GlobalAction.OpenAliases
        if (has("ai缓存", "ai缓存页", "缓存页")) return GlobalAction.OpenAiCache
        if (has("项目书", "查看项目书", "项目文档", "查看说明")) return GlobalAction.OpenDocs
        if (has("打开湿重库", "湿重库")) return GlobalAction.OpenWetWeights
        if (has("打开图表", "结果图表", "图表页")) return GlobalAction.OpenCharts
        if (has("切换平板模式", "平板模式", "平板布局")) return GlobalAction.SetUiMode(UiMode.Tablet)
        if (has("切换手机模式", "手机模式", "手机布局")) return GlobalAction.SetUiMode(UiMode.Phone)
        if (has("切换自动模式", "自动模式", "自动布局")) return GlobalAction.SetUiMode(UiMode.Auto)
        if (has("检测api", "检查api", "api检测", "api是否可用", "测试api")) return GlobalAction.CheckApi
        if (has("ai分析当前数据", "分析当前数据", "ai分析", "分析当前")) return GlobalAction.AiAnalyze
        if (has("点位追溯", "追溯分析", "溯源分析", "溯源")) {
            val match = findPointMatch(text, activePointId)
            if (match != null) return GlobalAction.TracePoint(match.id, match.label)
            if (activePointId != null) {
                val cur = ds?.points?.firstOrNull { it.id == activePointId }
                if (cur != null) {
                    return GlobalAction.TracePoint(cur.id, cur.label.ifBlank { cur.id })
                }
            }
            return GlobalAction.TracePoint("", "")
        }
        if (has("预览表1", "查看表1", "表1预览", "表一预览", "打开表1")) return GlobalAction.PreviewTable1
        if (has("预览表2", "查看表2", "表2预览", "表二预览", "打开表2")) return GlobalAction.PreviewTable2
        if (has("导出表1", "导出表一", "表1导出", "表一导出", "导出表格1", "导出表格一")) return GlobalAction.ExportTable1
        if (has("导出表2", "导出表二", "表2导出", "表二导出", "导出表格2", "导出表格二")) return GlobalAction.ExportTable2
        if (has("导出表")) return GlobalAction.OpenPreview

        return null
    }

    fun sendManualText(text: String, warnings: String? = null) {
        val content = text.trim()
        if (content.isBlank()) {
            Toast.makeText(context, "请输入指令文本", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.pushVoiceAssistantPayload(
            VoiceAssistantResult(
                requestId = UUID.randomUUID().toString(),
                status = VoiceAssistantContract.STATUS_OK,
                rawText = content,
                warnings = warnings ?: "手动输入",
                requestMatched = true,
            ),
        )
        inputText = ""
    }

    fun runApiCheck() {
        if (assistantState.apiCheckBusy) {
            commandMessage = "API 正在检测中…"
            return
        }
        commandError = null
        commandMessage = "API 检测中…"
        viewModel.updateAssistantApiCheckBusy(true)
        scope.launch {
            val (h1, h2) = viewModel.checkApis(settings)
            viewModel.updateAssistantApiCheckBusy(false)
            commandMessage = "API1：${h1.message}；API2：${h2.message}"
        }
    }

    fun runApiCheckWithMessage(update: (String) -> Unit) {
        if (assistantState.apiCheckBusy) {
            update("API 正在检测中…")
            return
        }
        update("API 检测中…")
        viewModel.updateAssistantApiCheckBusy(true)
        scope.launch {
            val (h1, h2) = viewModel.checkApis(settings)
            viewModel.updateAssistantApiCheckBusy(false)
            update("API1：${h1.message}；API2：${h2.message}")
        }
    }

    fun runAiAnalysis() {
        val data = ds
        if (data == null) {
            commandError = "数据集加载中，请稍后再试。"
            return
        }
        if (!settings.aiAssistantEnabled) {
            commandError = "请先在设置中开启 AI 功能。"
            return
        }
        if (!hasApiConfig(settings.api1) && !hasApiConfig(settings.api2)) {
            commandError = "请先配置 API1 或 API2。"
            return
        }
        commandError = null
        commandMessage = "已开始 AI 分析，结果将显示在助手页。"
        scope.launch {
            viewModel.launchAssistantTask {
                val ctx = buildAssistantContextWithDocs(
                    context = context,
                    dataset = data,
                    issues = issues,
                    taxonomyRepo = taxonomyRepo,
                    taxonomyOverrideRepo = taxonomyOverrideRepo,
                    wetWeightRepo = wetWeightRepo,
                    aliasRepo = aliasRepo,
                )
                val prompt = buildString {
                    appendLine(ctx)
                    appendLine()
                    appendLine("请你作为“监测助手”，基于以上问题列表给出：")
                    appendLine("1) 你认为最需要我优先确认的 3-5 个点；")
                    appendLine("2) 这些问题可能造成的影响（会影响哪些指标/导出表）；")
                    appendLine("3) 我应如何逐步核对（按操作步骤列出）。")
                }
                val choice = resolveAiChoice(api1HealthState, api2HealthState)
                viewModel.startAssistantAiTask(
                    prompt = prompt,
                    api1 = choice.primary,
                    api2 = choice.secondary,
                    useDualApi = choice.useDual,
                    taskLabel = "AI 分析当前数据",
                )
            }
        }
    }

    fun runPointTrace(action: GlobalAction.TracePoint) {
        val data = ds
        if (data == null) {
            commandError = "数据集加载中，请稍后再试。"
            return
        }
        if (action.pointId.isBlank()) {
            commandError = "未识别到点位，请说明点位名或编号。"
            return
        }
        commandError = null
        commandMessage = "已生成点位追溯：${action.label}（可在助手页查看）。"
        viewModel.updateAssistantTraceState { it.copy(error = null) }
        viewModel.startAssistantTrace(data, action.pointId) { dataset, pid ->
            withContext(Dispatchers.Default) { buildPointTrace(dataset, pid) }
        }
    }

    suspend fun buildFuzzyContext(): Pair<Map<String, String>, Set<String>> = withContext(Dispatchers.IO) {
        val aliasMap = runCatching {
            aliasRepo.getAll().associate { it.alias.trim() to it.canonicalNameCn.trim() }
        }.getOrElse { emptyMap() }
        val names = LinkedHashSet<String>()
        ds?.species?.mapTo(names) { it.nameCn.trim() }
        ds?.points?.mapTo(names) { it.label.trim().ifBlank { it.id.trim() } }
        names.addAll(LVL1_ORDER)
        runCatching { taxonomyRepo.getBuiltinEntryMap().keys }.getOrNull()?.forEach { names.add(it.trim()) }
        runCatching { wetWeightRepo.getBuiltinEntries().map { it.nameCn } }.getOrNull()?.forEach { names.add(it.trim()) }
        runCatching { wetWeightRepo.getCustomEntries().map { it.nameCn } }.getOrNull()?.forEach { names.add(it.trim()) }
        runCatching { taxonomyOverrideRepo.getCustomEntries().map { it.nameCn } }.getOrNull()?.forEach { names.add(it.trim()) }
        names.removeIf { it.isBlank() }
        aliasMap to names
    }

    fun runAskQuestion(textInput: String) {
        val data = ds
        val text = textInput.trim()
        if (text.isBlank()) {
            askMessage = "请输入问题"
            return
        }
        if (data == null) {
            askMessage = "数据集加载中，请稍后再试。"
            return
        }
        if (!settings.aiAssistantEnabled) {
            askMessage = "请先在设置中开启 AI 功能。"
            return
        }
        if (!hasApiConfig(settings.api1) && !hasApiConfig(settings.api2)) {
            askMessage = "请先配置 API1 或 API2。"
            return
        }
        askMessage = "询问中…"
        scope.launch {
            val (aliasMap, candidates) = buildFuzzyContext()
            val normalized = normalizePointNumberTokens(text)
            val (corrected, corrections) = applyFuzzyCorrections(normalized, aliasMap, candidates)
            if (corrected.isNotBlank() && corrected != text) {
                inputText = corrected
            }
            viewModel.updateAssistantQuestion(corrected.ifBlank { text })
            val notice = formatCorrections(corrections)
            askMessage = if (notice.isNotBlank()) notice else "询问中…"
            viewModel.launchAssistantTask {
                val ctx = buildAssistantContextWithDocs(
                    context = context,
                    dataset = data,
                    issues = issues,
                    taxonomyRepo = taxonomyRepo,
                    taxonomyOverrideRepo = taxonomyOverrideRepo,
                    wetWeightRepo = wetWeightRepo,
                    aliasRepo = aliasRepo,
                )
                val prompt = buildString {
                    appendLine(ctx)
                    appendLine()
                    appendLine("【用户问题】${corrected.ifBlank { text }}")
                }
                val choice = resolveAiChoice(api1HealthState, api2HealthState)
                viewModel.startAssistantAiTask(
                    prompt = prompt,
                    api1 = choice.primary,
                    api2 = choice.secondary,
                    useDualApi = choice.useDual,
                    taskLabel = "全局助手 · 询问",
                )
            }
        }
    }

    fun handleCommand(textInput: String) {
        val text = textInput.trim()
        if (text.isBlank()) {
            Toast.makeText(context, "请输入指令文本", Toast.LENGTH_SHORT).show()
            return
        }
        commandMessage = null
        commandError = null
        scope.launch {
            val (aliasMap, candidates) = buildFuzzyContext()
            val normalized = normalizePointNumberTokens(text)
            val (corrected, corrections) = applyFuzzyCorrections(normalized, aliasMap, candidates)
            val finalText = corrected.ifBlank { text }
            val notice = formatCorrections(corrections)
            if (finalText != text) {
                inputText = finalText
            }
            val action = parseGlobalAction(finalText)
            if (action == null) {
                sendManualText(finalText, warnings = notice.ifBlank { "手动输入" })
                commandMessage = if (notice.isNotBlank()) {
                    "$notice 已发送到批量录入解析。"
                } else {
                    "已发送到批量录入解析。"
                }
                return@launch
            }
            if (notice.isNotBlank()) {
                commandMessage = notice
            }
            when (action) {
                GlobalAction.OpenSettings -> {
                    onNavigate("settings")
                    commandMessage = "已打开设置。"
                }
                GlobalAction.OpenAssistant -> {
                    onNavigate("assistant")
                    commandMessage = "已打开助手页。"
                }
                GlobalAction.OpenPreview -> {
                    onNavigate("preview")
                    commandMessage = "已打开导出页，请选择表1或表2。"
                }
                GlobalAction.OpenPoints -> {
                    onNavigate("points")
                    commandMessage = "已打开采样点页。"
                }
                GlobalAction.OpenSpecies -> {
                    onNavigate("species")
                    commandMessage = "已打开物种页。"
                }
                GlobalAction.OpenDatasets -> {
                    onNavigate("datasets")
                    commandMessage = "已打开历史数据集。"
                }
                GlobalAction.OpenDatabase -> {
                    onNavigate("database")
                    commandMessage = "已打开数据库。"
                }
                GlobalAction.OpenDatabaseTree -> {
                    onNavigate("database/tree")
                    commandMessage = "已打开数据库树。"
                }
                GlobalAction.OpenMindMap -> {
                    onNavigate("database/mindmap")
                    commandMessage = "已打开思维导图。"
                }
                GlobalAction.OpenFocus -> {
                    onNavigate("focus")
                    commandMessage = "已打开专注录入。"
                }
                GlobalAction.OpenAliases -> {
                    onNavigate("aliases")
                    commandMessage = "已打开别名管理。"
                }
                GlobalAction.OpenAiCache -> {
                    onNavigate("aicache")
                    commandMessage = "已打开 AI 缓存。"
                }
                GlobalAction.OpenDocs -> {
                    onNavigate("docs")
                    commandMessage = "已打开项目文档。"
                }
                GlobalAction.OpenWetWeights -> {
                    onNavigate("wetweights")
                    commandMessage = "已打开湿重库。"
                }
                GlobalAction.OpenCharts -> {
                    onNavigate("charts")
                    commandMessage = "已打开结果图表。"
                }
                GlobalAction.CheckApi -> runApiCheck()
                GlobalAction.AiAnalyze -> runAiAnalysis()
                is GlobalAction.SetUiMode -> {
                    viewModel.saveSettings(settings.copy(uiMode = action.mode))
                    val label = when (action.mode) {
                        UiMode.Auto -> "自动"
                        UiMode.Phone -> "手机"
                        UiMode.Tablet -> "平板"
                    }
                    commandMessage = "已切换界面模式：$label"
                }
                is GlobalAction.TracePoint -> runPointTrace(action)
                GlobalAction.ExportTable1 -> {
                    viewModel.requestPreviewCommand(PreviewCommand.ExportTable1)
                    onNavigate("preview")
                    commandMessage = "请选择导出位置（表1）。"
                }
                GlobalAction.ExportTable2 -> {
                    viewModel.requestPreviewCommand(PreviewCommand.ExportTable2)
                    onNavigate("preview")
                    commandMessage = "请选择导出位置（表2）。"
                }
                GlobalAction.PreviewTable1 -> {
                    viewModel.requestPreviewCommand(PreviewCommand.PreviewTable1)
                    onNavigate("preview")
                    commandMessage = "已打开表1预览。"
                }
                GlobalAction.PreviewTable2 -> {
                    viewModel.requestPreviewCommand(PreviewCommand.PreviewTable2)
                    onNavigate("preview")
                    commandMessage = "已打开表2预览。"
                }
                is GlobalAction.ExportMindMap -> {
                    databaseViewModel.requestMindMapExport(action.scopeLevel, action.scopeValue)
                    onNavigate("database/mindmap")
                    commandMessage = "已打开思维导图导出。"
                }
            }
            inputText = ""
        }
    }

    fun startRecording(): Boolean {
        if (!recordGranted.value) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return false
        }
        if (recording) return false
        val started = recorder.start()
        if (started) {
            recordStartMs = SystemClock.elapsedRealtime()
            recording = true
            recordMessage = "录音中…（上滑=指令，下滑=询问）"
            recordFile = null
            recordUri = null
        } else {
            recordMessage = "录音启动失败"
        }
        return started
    }

    fun startVoiceAssistantTranscribe(audio: File, mode: VoiceInputMode) {
        if (!isVoiceAssistantInstalled()) {
            Toast.makeText(context, "未安装语音识别助手", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = VoiceAssistantAudioShare.toShareUri(context, audio)
        context.grantUriPermission(
            "com.voiceassistant",
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        val requestId = UUID.randomUUID().toString()
        pendingRequestId = requestId
        pendingVoiceMode = mode
        transcribeBusy = true
        transcribeMessage = "已发送转写请求，等待回传…"

        val intent = Intent(VoiceAssistantContract.ACTION_TRANSCRIBE_AUDIO).apply {
            setPackage("com.voiceassistant")
            putExtra(VoiceAssistantContract.EXTRA_REQUEST_ID, requestId)
            putExtra(VoiceAssistantContract.EXTRA_AUDIO_URI, uri.toString())
            putExtra(VoiceAssistantContract.EXTRA_RETURN_ACTION, VoiceAssistantContract.ACTION_RECEIVE_BULK_COMMAND)
            putExtra(VoiceAssistantContract.EXTRA_RETURN_PACKAGE, context.packageName)
            if (settings.voiceAssistantOverrideEnabled) {
                putExtra(VoiceAssistantContract.EXTRA_ENGINE, voiceConfig.engine)
                putExtra(VoiceAssistantContract.EXTRA_MODEL_ID, voiceConfig.modelId)
                putExtra(VoiceAssistantContract.EXTRA_DECODE_MODE, voiceConfig.decodeMode)
                putExtra(VoiceAssistantContract.EXTRA_USE_GPU, voiceConfig.useGpu)
                putExtra(VoiceAssistantContract.EXTRA_AUTO_STRATEGY, voiceConfig.autoStrategy)
                putExtra(VoiceAssistantContract.EXTRA_USE_MULTITHREAD, voiceConfig.useMultithread)
                putExtra(VoiceAssistantContract.EXTRA_THREAD_COUNT, voiceConfig.threadCount)
                putExtra(VoiceAssistantContract.EXTRA_SHERPA_PROVIDER, voiceConfig.sherpaProvider)
                putExtra(VoiceAssistantContract.EXTRA_SHERPA_STREAMING_MODEL, voiceConfig.sherpaStreamingModel)
                putExtra(VoiceAssistantContract.EXTRA_SHERPA_OFFLINE_MODEL, voiceConfig.sherpaOfflineModel)
            }
        }
        runCatching {
            ContextCompat.startForegroundService(context, intent)
        }.onFailure {
            transcribeBusy = false
            viewModel.cancelVoiceAssistantRequest(requestId)
            Toast.makeText(context, "转写请求失败：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun cancelVoiceAssistantTranscribe() {
        val requestId = pendingRequestId ?: return
        pendingRequestId = null
        pendingVoiceMode = null
        transcribeBusy = false
        transcribeMessage = "已取消转写"
        viewModel.cancelVoiceAssistantRequest(requestId)

        if (!isVoiceAssistantInstalled()) return
        val intent = Intent(VoiceAssistantContract.ACTION_CANCEL_TRANSCRIBE_AUDIO).apply {
            setPackage("com.voiceassistant")
            putExtra(VoiceAssistantContract.EXTRA_REQUEST_ID, requestId)
            putExtra(VoiceAssistantContract.EXTRA_RETURN_ACTION, VoiceAssistantContract.ACTION_RECEIVE_BULK_COMMAND)
            putExtra(VoiceAssistantContract.EXTRA_RETURN_PACKAGE, context.packageName)
        }
        runCatching {
            ContextCompat.startForegroundService(context, intent)
        }
    }

    fun stopRecordingAndTranscribe(mode: VoiceInputMode) {
        if (!recording) return
        scope.launch {
            val result = recorder.stop()
            recording = false
            recordFile = result.file
            recordUri = result.file?.let { VoiceAssistantAudioShare.toShareUri(context, it).toString() }
            recordMessage = if (result.error == null) {
                "录音完成：${formatDuration(result.durationMs)}"
            } else {
                "录音失败：${result.error}"
            }
            if (result.error == null && result.file != null) {
                startVoiceAssistantTranscribe(result.file, mode)
            }
        }
    }

    fun cancelRecording() {
        if (!recording) return
        scope.launch {
            val result = recorder.stop()
            recording = false
            recordFile = null
            recordUri = null
            runCatching { result.file?.delete() }
            recordMessage = "录音已取消"
            transcribeMessage = null
        }
    }

    fun openVoiceAssistantApp() {
        val intent = context.packageManager.getLaunchIntentForPackage("com.voiceassistant")
        if (intent == null) {
            Toast.makeText(context, "未安装语音识别助手", Toast.LENGTH_SHORT).show()
            return
        }
        context.startActivity(intent)
    }

    LaunchedEffect(recording) {
        if (recording) {
            while (recording) {
                val now = SystemClock.elapsedRealtime()
                recordElapsedMs = (now - recordStartMs).coerceAtLeast(0)
                delay(200)
            }
        } else {
            recordElapsedMs = 0
        }
    }

    LaunchedEffect(voicePayload?.requestId) {
        val payload = voicePayload ?: return@LaunchedEffect
        val reqId = pendingRequestId ?: return@LaunchedEffect
        if (payload.requestId != reqId) return@LaunchedEffect

        transcribeBusy = false
        transcribeMessage = when (payload.status) {
            VoiceAssistantContract.STATUS_OK -> "转写完成"
            VoiceAssistantContract.STATUS_CANCEL -> payload.errorMessage ?: "转写已取消"
            else -> payload.errorMessage ?: "转写失败"
        }
        pendingRequestId = null
        val mode = pendingVoiceMode ?: VoiceInputMode.Command
        pendingVoiceMode = null

        if (payload.status != VoiceAssistantContract.STATUS_OK) return@LaunchedEffect
        val rawText = payload.rawText?.trim().orEmpty()
        if (rawText.isBlank() && !payload.bulkJson.isNullOrBlank()) {
            viewModel.pushVoiceAssistantPayload(
                payload.copy(
                    requestId = UUID.randomUUID().toString(),
                    requestMatched = true,
                ),
            )
            commandMessage = "已发送到批量录入解析。"
            return@LaunchedEffect
        }
        if (rawText.isBlank()) {
            if (mode == VoiceInputMode.Query) {
                askMessage = "转写结果为空，请重试。"
            } else {
                commandError = "转写结果为空，请重试。"
            }
            return@LaunchedEffect
        }
        when (mode) {
            VoiceInputMode.Command -> handleCommand(rawText)
            VoiceInputMode.Query -> runAskQuestion(rawText)
        }
    }

    LaunchedEffect(aiState.busy, aiState.lastUpdatedAt, aiState.error) {
        if (!aiState.busy && askMessage == "询问中…") {
            askMessage = null
        }
    }

    LaunchedEffect(aiState.answer1) {
        if (aiState.answer1 != lastAnswer1) {
            lastAnswer1 = aiState.answer1
            if (aiState.answer1.isNotBlank()) {
                val title = aiState.answer1Label.ifBlank { settings.api1.name.ifBlank { "API 1" } }
                askNotice1 = AskNotice(id = UUID.randomUUID().toString(), title = title, body = aiState.answer1)
            } else {
                askNotice1 = null
            }
        }
    }

    LaunchedEffect(aiState.answer2) {
        if (aiState.answer2 != lastAnswer2) {
            lastAnswer2 = aiState.answer2
            if (aiState.answer2.isNotBlank()) {
                val title = aiState.answer2Label.ifBlank { settings.api2.name.ifBlank { "API 2" } }
                askNotice2 = AskNotice(id = UUID.randomUUID().toString(), title = title, body = aiState.answer2)
            } else {
                askNotice2 = null
            }
        }
    }

    LaunchedEffect(askNotice1?.id) {
        val current = askNotice1?.id ?: return@LaunchedEffect
        delay(10_000)
        if (askNotice1?.id == current) {
            askNotice1 = null
        }
    }

    LaunchedEffect(askNotice2?.id) {
        val current = askNotice2?.id ?: return@LaunchedEffect
        delay(10_000)
        if (askNotice2?.id == current) {
            askNotice2 = null
        }
    }

    LaunchedEffect(aiState.error, aiState.busy) {
        if (aiState.busy) return@LaunchedEffect
        val error = aiState.error ?: return@LaunchedEffect
        if (error == lastAiError) return@LaunchedEffect
        lastAiError = error
        val updater = if (aiState.taskLabel.contains("询问")) {
            { msg: String -> askMessage = msg }
        } else {
            { msg: String -> commandMessage = msg }
        }
        runApiCheckWithMessage(updater)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        LaunchedEffect(Unit) {
            if (offset == Offset.Zero) {
                offset = Offset(
                    with(density) { 18.dp.toPx() },
                    with(density) { 220.dp.toPx() },
                )
            }
        }
        val maxWidth = constraints.maxWidth.toFloat()
        val maxHeight = constraints.maxHeight.toFloat()
        val buttonSizePx = with(density) { 56.dp.toPx() }
        val paddingPx = with(density) { 12.dp.toPx() }

        fun dragOverlay(dragAmount: Offset) {
            offset = Offset(
                (offset.x + dragAmount.x).coerceIn(0f, maxWidth - buttonSizePx),
                (offset.y + dragAmount.y).coerceIn(0f, maxHeight - buttonSizePx),
            )
        }

        fun snapOverlay() {
            val snapX = if (offset.x + buttonSizePx / 2f < maxWidth / 2f) {
                paddingPx
            } else {
                (maxWidth - buttonSizePx - paddingPx).coerceAtLeast(0f)
            }
            offset = Offset(
                snapX,
                offset.y.coerceIn(paddingPx, maxHeight - buttonSizePx - paddingPx),
            )
        }

        val clampedX = offset.x.coerceIn(0f, (maxWidth - buttonSizePx).coerceAtLeast(0f))
        val clampedY = offset.y.coerceIn(0f, (maxHeight - buttonSizePx).coerceAtLeast(0f))
        val snapToRight = clampedX > (maxWidth - buttonSizePx) / 2f
        val cardOffsetX = if (snapToRight && panelWidthPx > 0) {
            (buttonSizePx - panelWidthPx.toFloat()).coerceAtMost(0f).roundToInt()
        } else {
            0
        }
        val longPressThresholdPx = with(density) { 26.dp.toPx() }
        val cancelThresholdPx = with(density) { 34.dp.toPx() }
        val cancelReleaseThresholdPx = cancelThresholdPx * 0.5f
        val panelMaxHeight = with(density) { (maxHeight * 0.68f).toDp() }
        val buttonScale by animateFloatAsState(if (longPressActive) 1.08f else 1f)
        val buttonColor by animateColorAsState(
            when {
                longPressCancelled -> MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                longPressActive && longPressMode == VoiceInputMode.Query -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.92f)
                longPressActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
            },
        )
        val buttonLabel = if (longPressActive) {
            when {
                longPressCancelled -> "取消"
                longPressMode == VoiceInputMode.Query -> "询问"
                longPressMode == VoiceInputMode.Command -> "指令"
                else -> "录音"
            }
        } else {
            "助手"
        }

        Column(
            modifier = Modifier
                .offset { IntOffset(clampedX.roundToInt(), clampedY.roundToInt()) }
                .pointerInput(longPressActive) {
                    if (longPressActive) return@pointerInput
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOverlay(dragAmount)
                        },
                        onDragEnd = {
                            snapOverlay()
                        },
                    )
                },
            horizontalAlignment = if (snapToRight) Alignment.End else Alignment.Start,
        ) {
            if (expanded) {
                GlassCard(
                    modifier = Modifier
                        .widthIn(min = 240.dp, max = 320.dp)
                        .heightIn(max = panelMaxHeight)
                        .offset { IntOffset(cardOffsetX, 0) }
                        .onSizeChanged { panelWidthPx = it.width },
                    blurEnabled = false,
                    elevation = 6.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOverlay(dragAmount)
                                        },
                                        onDragEnd = { snapOverlay() },
                                    )
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("全局助手", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { expanded = false }) {
                                Icon(Icons.Outlined.ExpandLess, contentDescription = "收起")
                            }
                        }

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            label = { Text("指令 / 询问") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 5,
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { handleCommand(inputText) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null)
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("执行")
                            }
                            Button(
                                enabled = !aiState.busy,
                                onClick = { runAskQuestion(inputText) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            ) {
                                if (aiState.busy) {
                                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                                    Text("询问中…")
                                } else {
                                    Text("询问 AI")
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { inputText = "" }, modifier = Modifier.weight(1f)) {
                                Text("清空")
                            }
                            OutlinedButton(
                                enabled = !assistantState.apiCheckBusy,
                                onClick = { runApiCheck() },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("检测 API")
                            }
                        }

                        Text(
                            "长按悬浮按钮录音，上滑=指令，下滑=询问，侧滑取消。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { showVoiceSettings = true }) {
                                Text("语音设置")
                            }
                        }

                        if (!commandMessage.isNullOrBlank()) {
                            GlassCard(elevation = 2.dp) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f))
                                        .padding(8.dp),
                                ) {
                                    Text("指令结果", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        commandMessage!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                }
                            }
                        }
                        if (!commandError.isNullOrBlank()) {
                            Text(commandError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        if (!askMessage.isNullOrBlank()) {
                            GlassCard(elevation = 2.dp) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f))
                                        .padding(8.dp),
                                ) {
                                    Text("询问状态", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        askMessage!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                        }

                        if (recording || transcribeBusy || !recordMessage.isNullOrBlank() || !transcribeMessage.isNullOrBlank()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("语音状态", style = MaterialTheme.typography.bodySmall)
                                if (recording) {
                                    Text("录音：${formatDuration(recordElapsedMs)}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (!recordMessage.isNullOrBlank()) {
                                    Text(recordMessage!!, style = MaterialTheme.typography.bodySmall)
                                }
                                if (transcribeBusy) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                                if (!transcribeMessage.isNullOrBlank()) {
                                    Text(transcribeMessage!!, style = MaterialTheme.typography.bodySmall)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { openVoiceAssistantApp() }) {
                                        Text("打开语音助手 App")
                                    }
                                    TextButton(
                                        enabled = transcribeBusy || pendingRequestId != null,
                                        onClick = { cancelVoiceAssistantTranscribe() },
                                    ) {
                                        Text("取消转写")
                                    }
                                }
                            }
                        }

                        if (!settings.aiUiHidden && (aiState.answer1.isNotBlank() || aiState.answer2.isNotBlank() || aiState.error != null)) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("询问结果", style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(
                                    enabled = aiState.answer1.isNotBlank() || aiState.answer2.isNotBlank() || aiState.error != null,
                                    onClick = { viewModel.clearAssistantReplies() },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("清除回复") }

                                if (!settings.aiAssistantEnabled) {
                                    Text(
                                        "提示：请先在设置中开启 AI 功能。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                    )
                                } else if (!canUseAiNow()) {
                                    Text(
                                        "提示：API 未通过检测或未配置完整，失败后会触发检测。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                    )
                                } else if (settings.aiUseDualApi && !(api1Ok && api2Ok)) {
                                    val only = if (api1Ok) "API1" else "API2"
                                    Text(
                                        "提示：仅 $only 可用，已自动使用单 API。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                    )
                                }

                                if (aiState.error != null) {
                                    Text("错误：${aiState.error}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (aiState.answer1.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = {
                                                fullTextTitle = settings.api1.name.ifBlank { "API 1" }
                                                fullTextBody = aiState.answer1
                                            },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text(aiState.answer1Label.ifBlank { settings.api1.name.ifBlank { "API 1" } })
                                        }
                                    }
                                    if (aiState.answer2.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = {
                                                fullTextTitle = settings.api2.name.ifBlank { "API 2" }
                                                fullTextBody = aiState.answer2
                                            },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text(aiState.answer2Label.ifBlank { settings.api2.name.ifBlank { "API 2" } })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    if (!longPressActive) {
                        expanded = !expanded
                    }
                },
                modifier = Modifier
                    .size(56.dp)
                    .scale(buttonScale)
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                val started = startRecording()
                                if (!started) return@detectDragGesturesAfterLongPress
                                longPressActive = true
                                longPressCancelled = false
                                longPressMode = VoiceInputMode.Command
                                longPressOffset = Offset.Zero
                                recordMessage = "录音中…（指令）"
                                if (settings.hapticsEnabled) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            onDrag = { change, dragAmount ->
                                if (!longPressActive) return@detectDragGesturesAfterLongPress
                                change.consume()
                                longPressOffset = longPressOffset + dragAmount
                                val cancelTriggered = if (snapToRight) {
                                    longPressOffset.x < -cancelThresholdPx
                                } else {
                                    longPressOffset.x > cancelThresholdPx
                                }
                                val cancelCleared = if (snapToRight) {
                                    longPressOffset.x > -cancelReleaseThresholdPx
                                } else {
                                    longPressOffset.x < cancelReleaseThresholdPx
                                }
                                if (!longPressCancelled && cancelTriggered) {
                                    longPressCancelled = true
                                    recordMessage = "录音已取消（松开结束）"
                                    if (settings.hapticsEnabled) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                } else if (longPressCancelled && cancelCleared) {
                                    longPressCancelled = false
                                    val mode = longPressMode ?: VoiceInputMode.Command
                                    recordMessage = if (mode == VoiceInputMode.Query) {
                                        "录音中…（询问）"
                                    } else {
                                        "录音中…（指令）"
                                    }
                                    if (settings.hapticsEnabled) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                                if (longPressCancelled) return@detectDragGesturesAfterLongPress
                                val nextMode = when {
                                    longPressOffset.y < -longPressThresholdPx -> VoiceInputMode.Command
                                    longPressOffset.y > longPressThresholdPx -> VoiceInputMode.Query
                                    else -> longPressMode ?: VoiceInputMode.Command
                                }
                                if (nextMode != longPressMode) {
                                    longPressMode = nextMode
                                    recordMessage = if (nextMode == VoiceInputMode.Query) {
                                        "录音中…（询问）"
                                    } else {
                                        "录音中…（指令）"
                                    }
                                    if (settings.hapticsEnabled) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            },
                            onDragEnd = {
                                val mode = longPressMode ?: VoiceInputMode.Command
                                longPressActive = false
                                longPressMode = null
                                longPressOffset = Offset.Zero
                                if (longPressCancelled) {
                                    longPressCancelled = false
                                    cancelRecording()
                                } else {
                                    stopRecordingAndTranscribe(mode)
                                }
                            },
                            onDragCancel = {
                                val mode = longPressMode ?: VoiceInputMode.Command
                                longPressActive = false
                                longPressMode = null
                                longPressOffset = Offset.Zero
                                if (longPressCancelled) {
                                    longPressCancelled = false
                                    cancelRecording()
                                } else {
                                    stopRecordingAndTranscribe(mode)
                                }
                            },
                        )
                    },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Text(buttonLabel)
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                askNotice1?.let { notice ->
                    AskNoticeCard(
                        title = notice.title,
                        onOpen = {
                            fullTextTitle = notice.title
                            fullTextBody = notice.body
                            askNotice1 = null
                        },
                        onClose = { askNotice1 = null },
                    )
                }
                askNotice2?.let { notice ->
                    AskNoticeCard(
                        title = notice.title,
                        onOpen = {
                            fullTextTitle = notice.title
                            fullTextBody = notice.body
                            askNotice2 = null
                        },
                        onClose = { askNotice2 = null },
                    )
                }
            }
        }
    }

    if (showVoiceSettings) {
        var engineMenuOpen by remember { mutableStateOf(false) }
        var modelMenuOpen by remember { mutableStateOf(false) }
        var sherpaStreamingMenuOpen by remember { mutableStateOf(false) }
        var sherpaOfflineMenuOpen by remember { mutableStateOf(false) }
        var modeMenuOpen by remember { mutableStateOf(false) }
        var threadMenuOpen by remember { mutableStateOf(false) }

        val isWhisper = voiceConfig.engine == "whisper"
        val isSherpa = voiceConfig.engine.startsWith("sherpa")
        val engineOptions = listOf(
            "sherpa_streaming" to "Sherpa 流式",
            "sherpa_offline" to "Sherpa 高精度",
            "whisper" to "Whisper (Q8)",
        )
        val engineLabel = engineOptions.firstOrNull { it.first == voiceConfig.engine }?.second ?: "Whisper (Q8)"
        val modelOptions = listOf("small-q8_0")
        val streamingOptions = listOf(
            "zipformer_zh" to "zipformer（中文）",
            "zipformer_bilingual" to "zipformer bilingual（中英）",
        )
        val offlineOptions = listOf(
            "sense_voice" to "sense-voice（高精度）",
            "paraformer_zh" to "paraformer（中文）",
        )
        val streamingLabel = streamingOptions.firstOrNull { it.first == voiceConfig.sherpaStreamingModel }
            ?.second ?: "zipformer（中文）"
        val offlineLabel = offlineOptions.firstOrNull { it.first == voiceConfig.sherpaOfflineModel }
            ?.second ?: "sense-voice（高精度）"
        val decodeLabel = if (voiceConfig.decodeMode == "accurate") "准确" else "快速"
        val threadOptions = listOf(0, 1, 2, 4, 6, 8)
        val threadLabel = if (voiceConfig.threadCount <= 0) "自动" else "${voiceConfig.threadCount} 线程"

        AlertDialog(
            onDismissRequest = { showVoiceSettings = false },
            confirmButton = {
                TextButton(onClick = { showVoiceSettings = false }) { Text("关闭") }
            },
            title = { Text("语音助手设置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("下发语音设置", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = settings.voiceAssistantOverrideEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.saveSettings(settings.copy(voiceAssistantOverrideEnabled = enabled))
                            },
                        )
                    }
                    Text(
                        if (settings.voiceAssistantOverrideEnabled) {
                            "已使用主 App 设置覆盖语音助手配置。"
                        } else {
                            "已使用语音助手 App 内的设置。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    Text("引擎/模型", style = MaterialTheme.typography.bodySmall)
                    Box {
                        OutlinedButton(onClick = { engineMenuOpen = true }) {
                            Text(engineLabel)
                        }
                        DropdownMenu(expanded = engineMenuOpen, onDismissRequest = { engineMenuOpen = false }) {
                            engineOptions.forEach { (id, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        updateVoiceConfig { it.copy(engine = id) }
                                        engineMenuOpen = false
                                    },
                                )
                            }
                        }
                    }

                    if (isWhisper) {
                        Text("Whisper 模型", style = MaterialTheme.typography.bodySmall)
                        Box {
                            OutlinedButton(onClick = { modelMenuOpen = true }) {
                                Text(voiceConfig.modelId.ifBlank { "small-q8_0" })
                            }
                            DropdownMenu(expanded = modelMenuOpen, onDismissRequest = { modelMenuOpen = false }) {
                                modelOptions.forEach { id ->
                                    DropdownMenuItem(
                                        text = { Text(id) },
                                        onClick = {
                                            updateVoiceConfig { it.copy(modelId = id) }
                                            modelMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (voiceConfig.engine == "sherpa_streaming") {
                        Text("Sherpa 流式模型", style = MaterialTheme.typography.bodySmall)
                        Box {
                            OutlinedButton(onClick = { sherpaStreamingMenuOpen = true }) {
                                Text(streamingLabel)
                            }
                            DropdownMenu(expanded = sherpaStreamingMenuOpen, onDismissRequest = { sherpaStreamingMenuOpen = false }) {
                                streamingOptions.forEach { (id, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            updateVoiceConfig { it.copy(sherpaStreamingModel = id) }
                                            sherpaStreamingMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (voiceConfig.engine == "sherpa_offline") {
                        Text("Sherpa 高精度模型", style = MaterialTheme.typography.bodySmall)
                        Box {
                            OutlinedButton(onClick = { sherpaOfflineMenuOpen = true }) {
                                Text(offlineLabel)
                            }
                            DropdownMenu(expanded = sherpaOfflineMenuOpen, onDismissRequest = { sherpaOfflineMenuOpen = false }) {
                                offlineOptions.forEach { (id, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            updateVoiceConfig { it.copy(sherpaOfflineModel = id) }
                                            sherpaOfflineMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Text("识别模式", style = MaterialTheme.typography.bodySmall)
                    Box {
                        OutlinedButton(onClick = { modeMenuOpen = true }) {
                            Text(decodeLabel)
                        }
                        DropdownMenu(expanded = modeMenuOpen, onDismissRequest = { modeMenuOpen = false }) {
                            DropdownMenuItem(text = { Text("快速") }, onClick = {
                                updateVoiceConfig { it.copy(decodeMode = "fast") }
                                modeMenuOpen = false
                            })
                            DropdownMenuItem(text = { Text("准确") }, onClick = {
                                updateVoiceConfig { it.copy(decodeMode = "accurate") }
                                modeMenuOpen = false
                            })
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("自动策略（Whisper）", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = voiceConfig.autoStrategy,
                            onCheckedChange = { enabled -> updateVoiceConfig { cfg -> cfg.copy(autoStrategy = enabled) } },
                            enabled = isWhisper,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("GPU 加速（Whisper）", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = voiceConfig.useGpu,
                            onCheckedChange = { enabled -> updateVoiceConfig { cfg -> cfg.copy(useGpu = enabled) } },
                            enabled = isWhisper,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("NNAPI 加速（Sherpa）", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = voiceConfig.sherpaProvider == "nnapi",
                            onCheckedChange = { enabled ->
                                updateVoiceConfig { it.copy(sherpaProvider = if (enabled) "nnapi" else "cpu") }
                            },
                            enabled = isSherpa,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("多线程加速", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = voiceConfig.useMultithread,
                            onCheckedChange = { enabled -> updateVoiceConfig { cfg -> cfg.copy(useMultithread = enabled) } },
                        )
                    }

                    Text("线程数", style = MaterialTheme.typography.bodySmall)
                    Box {
                        OutlinedButton(
                            onClick = { threadMenuOpen = true },
                            enabled = voiceConfig.useMultithread,
                        ) {
                            Text(threadLabel)
                        }
                        DropdownMenu(expanded = threadMenuOpen, onDismissRequest = { threadMenuOpen = false }) {
                            threadOptions.forEach { count ->
                                val label = if (count <= 0) "自动" else "${count} 线程"
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        updateVoiceConfig { it.copy(threadCount = count) }
                                        threadMenuOpen = false
                                    },
                                )
                            }
                        }
                    }

                    if (voiceConfig.engine == "sherpa_offline" &&
                        voiceConfig.decodeMode == "accurate" &&
                        voiceConfig.sherpaOfflineModel == "sense_voice"
                    ) {
                        Text(
                            "提示：sense-voice 准确模式会自动降级为稳定模式。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                    }
                }
            },
        )
    }

    if (fullTextBody != null) {
        AlertDialog(
            onDismissRequest = { fullTextBody = null },
            confirmButton = {
                TextButton(onClick = { fullTextBody = null }) { Text("关闭") }
            },
            title = {
                Text(fullTextTitle.ifBlank { "全文" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AiRichText(text = fullTextBody.orEmpty(), style = MaterialTheme.typography.bodySmall)
                }
            },
        )
    }
}
