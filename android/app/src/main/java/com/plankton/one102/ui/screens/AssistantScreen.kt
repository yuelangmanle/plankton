package com.plankton.one102.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plankton.one102.data.api.ChatCompletionClient
import com.plankton.one102.data.api.buildSpeciesInfoPrompt
import com.plankton.one102.data.api.buildSpeciesTaxonomyAutofillPrompt
import com.plankton.one102.data.api.buildSpeciesWetWeightAutofillPrompt
import com.plankton.one102.data.api.extractFinalSpeciesJson
import com.plankton.one102.data.api.parseAiSpeciesInfo
import com.plankton.one102.domain.ApiConfig
import com.plankton.one102.domain.AutoMatchEntry
import com.plankton.one102.domain.AutoMatchSession
import com.plankton.one102.domain.DataIssue
import com.plankton.one102.domain.IssueLevel
import com.plankton.one102.domain.Point
import com.plankton.one102.domain.Settings
import com.plankton.one102.domain.Species
import com.plankton.one102.domain.Taxonomy
import com.plankton.one102.domain.TaxonomyRecord
import com.plankton.one102.domain.WetWeightEntry
import com.plankton.one102.domain.WetWeightTaxonomy
import com.plankton.one102.domain.buildPointTrace
import com.plankton.one102.domain.nowIso
import com.plankton.one102.domain.normalizeLvl1Name
import com.plankton.one102.domain.validateDataset
import com.plankton.one102.ui.ApiHealthState
import com.plankton.one102.ui.MainViewModel
import com.plankton.one102.ui.buildAssistantContextWithDocs
import com.plankton.one102.ui.components.AiRichText
import com.plankton.one102.ui.components.GlassBackground
import com.plankton.one102.ui.components.GlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AutoFixKind { Taxonomy, WetWeight }

private fun ApiHealthState.matches(api: ApiConfig): Boolean = baseUrl == api.baseUrl && model == api.model

private fun anyCountPositive(species: Species): Boolean = species.countsByPointId.values.any { it > 0 }


private fun Context.copyToClipboard(label: String, text: String) {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun clipTrace(text: String, maxChars: Int = 5000): String {
    val clean = text.trimEnd()
    if (clean.length <= maxChars) return clean
    val clipped = clean.take(maxChars).trimEnd()
    val omitted = clean.length - clipped.length
    return "$clipped\n（已省略约 $omitted 字）"
}

@Composable
fun AssistantScreen(
    viewModel: MainViewModel,
    padding: PaddingValues,
    onOpenSpecies: () -> Unit = {},
    onOpenPoints: () -> Unit = {},
    onOpenFocus: () -> Unit = {},
    onOpenSpeciesEdit: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as com.plankton.one102.PlanktonApplication
    val scope = rememberCoroutineScope()
    val client = remember { ChatCompletionClient() }

    val wetWeightRepo = app.wetWeightRepository
    val taxonomyRepo = app.taxonomyRepository
    val taxonomyOverrideRepo = app.taxonomyOverrideRepository
    val aliasRepo = app.aliasRepository
    val aiCacheRepo = app.aiCacheRepository

    val dataset by viewModel.currentDataset.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val api1HealthState by viewModel.api1Health.collectAsStateWithLifecycle()
    val api2HealthState by viewModel.api2Health.collectAsStateWithLifecycle()
    val apiHealthRecent by viewModel.apiHealthRecent.collectAsStateWithLifecycle()

    val ds = dataset ?: run {
        GlassBackground {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) { Text("加载中…") }
        }
        return
    }

    if (ds.points.isEmpty()) {
        GlassBackground {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("AI / 检查", style = MaterialTheme.typography.titleLarge)
                Text(
                    "当前数据集没有采样点。请先在底部「采样点」页面新增至少 1 条采样点后再使用点位相关功能。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
            }
        }
        return
    }

    val allIssues = remember(ds) { validateDataset(ds) }
    val ignoredKeys = remember(ds.ignoredIssueKeys) { ds.ignoredIssueKeys.toSet() }
    val issues = remember(allIssues, ignoredKeys) { allIssues.filter { it.key !in ignoredKeys } }
    val errCount = remember(issues) { issues.count { it.level == IssueLevel.Error } }
    val warnCount = remember(issues) { issues.count { it.level == IssueLevel.Warn } }
    val infoCount = remember(issues) { issues.count { it.level == IssueLevel.Info } }
    val ignoredCount = remember(allIssues, issues) { (allIssues.size - issues.size).coerceAtLeast(0) }

    val assistantState by viewModel.assistantState.collectAsStateWithLifecycle()
    LaunchedEffect(ds.id, assistantState.datasetId) {
        if (assistantState.datasetId != ds.id) {
            viewModel.resetAssistantState(ds.id)
        }
    }

    var activePointId by remember(ds.id) { mutableStateOf(ds.points.firstOrNull()?.id ?: "") }
    if (ds.points.none { it.id == activePointId }) activePointId = ds.points.firstOrNull()?.id ?: ""

    val aiState = assistantState.ai
    val traceState = assistantState.trace
    val fixState = assistantState.fix

    val trace = traceState.byPointId[activePointId].orEmpty()
    val traceBusy = traceState.busyPointId == activePointId
    val traceError = traceState.error
    val includeTrace = traceState.includeTrace

    val api1Health = api1HealthState?.takeIf { it.matches(settings.api1) }
    val api2Health = api2HealthState?.takeIf { it.matches(settings.api2) }
    val api1Ok = api1Health?.ok == true
    val api2Ok = api2Health?.ok == true
    val checkingApi = assistantState.apiCheckBusy

    val fixBusy = fixState.busy
    val fixProgress = fixState.progress
    val fixError = fixState.error
    val fixApiTrace = fixState.apiTrace

    val question = aiState.question
    val busy = aiState.busy
    val answer1 = aiState.answer1
    val answer2 = aiState.answer2
    val lastError = aiState.error
    var fullTextTitle by remember { mutableStateOf("") }
    var fullTextBody by remember { mutableStateOf<String?>(null) }
    var aiNotice by remember { mutableStateOf<String?>(null) }
    var lastAiError by remember { mutableStateOf<String?>(null) }

    fun hasApi(api: ApiConfig): Boolean = api.baseUrl.isNotBlank() && api.model.isNotBlank()

    fun canUseAiNow(): Boolean {
        if (settings.aiUiHidden) return false
        if (!settings.aiAssistantEnabled) return false
        return api1Ok || api2Ok
    }

    LaunchedEffect(lastError, busy) {
        if (busy) return@LaunchedEffect
        val err = lastError ?: return@LaunchedEffect
        if (err == lastAiError) return@LaunchedEffect
        lastAiError = err
        if (!settings.aiAssistantEnabled) return@LaunchedEffect
        if (!hasApi(settings.api1) && !hasApi(settings.api2)) return@LaunchedEffect
        if (assistantState.apiCheckBusy) return@LaunchedEffect
        aiNotice = "API 检测中…"
        viewModel.updateAssistantApiCheckBusy(true)
        val (h1, h2) = viewModel.checkApis(settings)
        viewModel.updateAssistantApiCheckBusy(false)
        aiNotice = "API1：${h1.message}；API2：${h2.message}"
    }

    data class AiApiChoice(
        val primary: ApiConfig,
        val secondary: ApiConfig,
        val useDual: Boolean,
    )

    fun resolveAiChoice(
        h1: ApiHealthState? = api1HealthState,
        h2: ApiHealthState? = api2HealthState,
    ): AiApiChoice {
        val api1Ready = h1?.ok == true && h1.matches(settings.api1)
        val api2Ready = h2?.ok == true && h2.matches(settings.api2)
        val has1 = hasApi(settings.api1)
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

    fun updateSettings(next: Settings) {
        viewModel.saveSettings(next)
    }

    fun ignoreIssues(keys: List<String>) {
        if (keys.isEmpty()) return
        viewModel.updateCurrentDataset { cur ->
            val next = (cur.ignoredIssueKeys + keys).distinct()
            cur.copy(ignoredIssueKeys = next)
        }
    }

    fun clearIgnoredIssues() {
        viewModel.updateCurrentDataset { cur ->
            if (cur.ignoredIssueKeys.isEmpty()) cur else cur.copy(ignoredIssueKeys = emptyList())
        }
    }

    suspend fun callSpeciesInfoApi1Only(nameCn: String, prompt: String): String {
        suspend fun callOne(api: ApiConfig, apiTag: String): String? {
            val cached = try {
                aiCacheRepo.getSpeciesInfo(apiTag, nameCn)
            } catch (_: Exception) {
                null
            }
            if (cached != null && cached.prompt == prompt && cached.raw.isNotBlank()) return cached.raw

            val raw = try {
                client.call(api, prompt, maxTokens = 650)
            } catch (_: Exception) {
                null
            } ?: return null

            val parsed = extractFinalSpeciesJson(raw)?.let { parseAiSpeciesInfo(it) }
            val tax = parsed?.let {
                Taxonomy(
                    lvl1 = normalizeLvl1Name(it.lvl1.orEmpty()),
                    lvl2 = it.lvl2?.trim().orEmpty(),
                    lvl3 = it.lvl3?.trim().orEmpty(),
                    lvl4 = it.lvl4?.trim().orEmpty(),
                    lvl5 = it.lvl5?.trim().orEmpty(),
                )
            }
            try {
                aiCacheRepo.upsertSpeciesInfo(
                    apiTag = apiTag,
                    nameCn = nameCn,
                    nameLatin = parsed?.nameLatin,
                    wetWeightMg = parsed?.wetWeightMg,
                    taxonomy = tax,
                    prompt = prompt,
                    raw = raw,
                )
            } catch (_: Exception) {
                // Ignore cache write failure.
            }
            return raw
        }

        if (!hasApi(settings.api1)) error("未配置可用 API1")
        val raw = callOne(settings.api1, "api1") ?: error("API1 调用失败")
        return "API_USED:api1\n$raw"
    }

    fun runAutoFix(kind: AutoFixKind) {
        if (fixBusy) return

        val targets = when (kind) {
            AutoFixKind.Taxonomy -> ds.species.filter { sp ->
                anyCountPositive(sp) &&
                    sp.nameCn.isNotBlank() &&
                    (
                        sp.nameLatin.isBlank() ||
                            sp.taxonomy.lvl1.isBlank() ||
                            sp.taxonomy.lvl2.isBlank() ||
                            sp.taxonomy.lvl3.isBlank() ||
                            sp.taxonomy.lvl4.isBlank() ||
                            sp.taxonomy.lvl5.isBlank()
                        )
            }
            AutoFixKind.WetWeight -> ds.species.filter { sp -> anyCountPositive(sp) && sp.nameCn.isNotBlank() && sp.avgWetWeightMg == null }
        }
        if (targets.isEmpty()) {
            Toast.makeText(context, "没有需要处理的缺失项", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.updateAssistantFixState { it.copy(busy = true, progress = "准备中…", error = null, apiTrace = "") }

        viewModel.launchAssistantTask block@{
            try {
                val aliasMap = try {
                    aliasRepo.getAll().associate { it.alias to it.canonicalNameCn }
                } catch (_: Exception) {
                    emptyMap()
                }

                val updates = LinkedHashMap<String, AutoMatchEntry>()
                val errors = mutableListOf<String>()
                val apiStats = linkedMapOf("api1" to 0)
                val writeToDb = settings.autoMatchWriteToDb

                fun mergeSession(existing: AutoMatchSession?, incoming: Collection<AutoMatchEntry>): AutoMatchSession {
                    val map = LinkedHashMap<String, AutoMatchEntry>()
                    existing?.entries?.forEach { map[it.speciesId] = it }
                    fun pickText(old: String, new: String): String = if (new.isNotBlank()) new else old
                    fun pickTax(old: Taxonomy, new: Taxonomy): Taxonomy {
                        return Taxonomy(
                            lvl1 = pickText(old.lvl1, new.lvl1),
                            lvl2 = pickText(old.lvl2, new.lvl2),
                            lvl3 = pickText(old.lvl3, new.lvl3),
                            lvl4 = pickText(old.lvl4, new.lvl4),
                            lvl5 = pickText(old.lvl5, new.lvl5),
                        )
                    }
                    for (e in incoming) {
                        val prev = map[e.speciesId]
                        map[e.speciesId] = if (prev == null) {
                            e
                        } else {
                            prev.copy(
                                nameCn = pickText(prev.nameCn, e.nameCn),
                                nameLatin = pickText(prev.nameLatin, e.nameLatin),
                                wetWeightMg = e.wetWeightMg ?: prev.wetWeightMg,
                                taxonomy = pickTax(prev.taxonomy, e.taxonomy),
                            )
                        }
                    }
                    return AutoMatchSession(
                        sessionId = java.util.UUID.randomUUID().toString(),
                        createdAt = nowIso(),
                        entries = map.values.toList(),
                    )
                }

                fun missingTaxonomyFields(cur: Taxonomy, rec: Taxonomy): Boolean {
                    fun missingField(curVal: String, recVal: String): Boolean = curVal.isBlank() && recVal.isBlank()
                    return missingField(cur.lvl1, rec.lvl1) ||
                        missingField(cur.lvl2, rec.lvl2) ||
                        missingField(cur.lvl3, rec.lvl3) ||
                        missingField(cur.lvl4, rec.lvl4) ||
                        missingField(cur.lvl5, rec.lvl5)
                }

                val canUseAi = settings.aiAssistantEnabled && !settings.aiUiHidden && hasApi(settings.api1)

                for ((i, sp0) in targets.withIndex()) {
                    val nameCn = sp0.nameCn.trim()
                    if (nameCn.isEmpty()) continue
                    val lookupName = aliasMap[nameCn] ?: nameCn
                    val kindLabel = if (kind == AutoFixKind.Taxonomy) "分类" else "湿重"
                    viewModel.updateAssistantFixState { it.copy(progress = "$kindLabel 处理 ${i + 1}/${targets.size}：$nameCn") }

                    var recordLatin = ""
                    var recordWet: Double? = null
                    var recordTax = Taxonomy()

                    val wantLatin = (kind == AutoFixKind.Taxonomy) && sp0.nameLatin.isBlank()
                    var wantWet = (kind == AutoFixKind.WetWeight) && sp0.avgWetWeightMg == null
                    val wantTax = (kind == AutoFixKind.Taxonomy) && missingTaxonomyFields(sp0.taxonomy, recordTax)

                    // Local: wet weight library (custom > builtin)
                    if (wantWet || wantTax || wantLatin) {
                        val entry = try {
                            wetWeightRepo.findByNameCn(lookupName)
                        } catch (_: Exception) {
                            null
                        }
                        if (entry != null) {
                            if (wantWet) {
                                recordWet = entry.wetWeightMg
                                wantWet = false
                            }
                            if (wantLatin && recordLatin.isBlank() && !entry.nameLatin.isNullOrBlank()) {
                                recordLatin = entry.nameLatin.orEmpty()
                            }
                            if (wantTax) {
                                val lvl1 = normalizeLvl1Name(entry.taxonomy.group.orEmpty())
                                val lvl4 = entry.taxonomy.sub.orEmpty()
                                if (lvl1.isNotBlank() || lvl4.isNotBlank()) {
                                    recordTax = recordTax.copy(
                                        lvl1 = recordTax.lvl1.ifBlank { lvl1 },
                                        lvl4 = recordTax.lvl4.ifBlank { lvl4 },
                                    )
                                }
                            }
                        }
                    }

                    // Local: taxonomy overrides > table1
                    if (wantTax || wantLatin) {
                        val tCustom = try {
                            taxonomyOverrideRepo.findCustomByNameCn(lookupName)
                        } catch (_: Exception) {
                            null
                        }
                        val tBuiltin = if (tCustom == null) {
                            try {
                                taxonomyRepo.findEntryByNameCn(lookupName)
                            } catch (_: Exception) {
                                null
                            }
                        } else {
                            null
                        }
                        val nameLatin = tCustom?.nameLatin ?: tBuiltin?.nameLatin
                        if (wantLatin && recordLatin.isBlank() && !nameLatin.isNullOrBlank()) {
                            recordLatin = nameLatin.trim()
                        }
                        val t = tCustom?.taxonomy ?: tBuiltin?.taxonomy
                        if (wantTax && t != null && listOf(t.lvl1, t.lvl2, t.lvl3, t.lvl4, t.lvl5).any { it.isNotBlank() }) {
                            recordTax = recordTax.copy(
                                lvl1 = recordTax.lvl1.ifBlank { normalizeLvl1Name(t.lvl1) },
                                lvl2 = recordTax.lvl2.ifBlank { t.lvl2 },
                                lvl3 = recordTax.lvl3.ifBlank { t.lvl3 },
                                lvl4 = recordTax.lvl4.ifBlank { t.lvl4 },
                                lvl5 = recordTax.lvl5.ifBlank { t.lvl5 },
                            )
                        }
                    }

                    // API: fill remaining missing parts only
                    val wantLatin2 = wantLatin && recordLatin.isBlank()
                    val wantWet2 = wantWet && recordWet == null
                    val wantTax2 = wantTax && missingTaxonomyFields(sp0.taxonomy, recordTax)
                    if (canUseAi && (wantWet2 || wantTax2 || wantLatin2)) {
                        val prompt = when (kind) {
                            AutoFixKind.Taxonomy -> buildSpeciesTaxonomyAutofillPrompt(nameCn, sp0.nameLatin.ifBlank { null })
                            AutoFixKind.WetWeight -> buildSpeciesWetWeightAutofillPrompt(nameCn, sp0.nameLatin.ifBlank { null })
                        }
                        val ai = try {
                            val raw = callSpeciesInfoApi1Only(nameCn, prompt)
                            apiStats["api1"] = (apiStats["api1"] ?: 0) + 1
                            val cleaned = raw.removePrefix("API_USED:api1\n")
                            val json = extractFinalSpeciesJson(cleaned)
                            if (json == null) null else parseAiSpeciesInfo(json)
                        } catch (_: Exception) {
                            null
                        }

                        if (ai == null) {
                            errors += "未解析到结构化结果：$nameCn"
                        } else {
                            var aiContributedTax = false
                            var aiContributedWet = false

                            if (wantLatin2 && recordLatin.isBlank() && !ai.nameLatin.isNullOrBlank()) {
                                recordLatin = ai.nameLatin.trim()
                            }
                            if (wantWet2) {
                                val v = ai.wetWeightMg
                                if (v != null && v.isFinite() && v > 0) {
                                    recordWet = v
                                    aiContributedWet = true
                                }
                            }
                            if (wantTax2) {
                                fun shouldFill(curVal: String, recVal: String, aiVal: String): Boolean {
                                    return curVal.isBlank() && recVal.isBlank() && aiVal.isNotBlank()
                                }

                                val aiLvl1 = normalizeLvl1Name(ai.lvl1.orEmpty())
                                val aiLvl2 = ai.lvl2?.trim().orEmpty()
                                val aiLvl3 = ai.lvl3?.trim().orEmpty()
                                val aiLvl4 = ai.lvl4?.trim().orEmpty()
                                val aiLvl5 = ai.lvl5?.trim().orEmpty()

                                if (shouldFill(sp0.taxonomy.lvl1, recordTax.lvl1, aiLvl1)) {
                                    recordTax = recordTax.copy(lvl1 = aiLvl1)
                                    aiContributedTax = true
                                }
                                if (shouldFill(sp0.taxonomy.lvl2, recordTax.lvl2, aiLvl2)) {
                                    recordTax = recordTax.copy(lvl2 = aiLvl2)
                                    aiContributedTax = true
                                }
                                if (shouldFill(sp0.taxonomy.lvl3, recordTax.lvl3, aiLvl3)) {
                                    recordTax = recordTax.copy(lvl3 = aiLvl3)
                                    aiContributedTax = true
                                }
                                if (shouldFill(sp0.taxonomy.lvl4, recordTax.lvl4, aiLvl4)) {
                                    recordTax = recordTax.copy(lvl4 = aiLvl4)
                                    aiContributedTax = true
                                }
                                if (shouldFill(sp0.taxonomy.lvl5, recordTax.lvl5, aiLvl5)) {
                                    recordTax = recordTax.copy(lvl5 = aiLvl5)
                                    aiContributedTax = true
                                }
                            }

                            if (writeToDb && (aiContributedTax || aiContributedWet)) {
                                val keyName = lookupName.trim()
                                if (keyName.isNotBlank()) {
                                    if (aiContributedTax && listOf(recordTax.lvl1, recordTax.lvl2, recordTax.lvl3, recordTax.lvl4, recordTax.lvl5).any { it.isNotBlank() }) {
                                        runCatching {
                                            taxonomyOverrideRepo.upsertCustom(
                                                TaxonomyRecord(
                                                    nameCn = keyName,
                                                    nameLatin = recordLatin.takeIf { it.isNotBlank() },
                                                    taxonomy = recordTax,
                                                ),
                                            )
                                        }
                                    }
                                    val wet = recordWet
                                    if (aiContributedWet && wet != null && wet.isFinite() && wet > 0) {
                                        runCatching {
                                            wetWeightRepo.upsertAutoMatched(
                                                WetWeightEntry(
                                                    nameCn = keyName,
                                                    nameLatin = recordLatin.takeIf { it.isNotBlank() },
                                                    wetWeightMg = wet,
                                                    taxonomy = WetWeightTaxonomy(
                                                        group = sp0.taxonomy.lvl1.trim().ifBlank { null },
                                                        sub = sp0.taxonomy.lvl4.trim().ifBlank { null },
                                                    ),
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val hasAny = recordLatin.isNotBlank() ||
                        recordWet != null ||
                        listOf(recordTax.lvl1, recordTax.lvl2, recordTax.lvl3, recordTax.lvl4, recordTax.lvl5).any { it.isNotBlank() }
                    if (hasAny) {
                        updates[sp0.id] = AutoMatchEntry(
                            speciesId = sp0.id,
                            nameCn = nameCn,
                            nameLatin = recordLatin,
                            wetWeightMg = recordWet,
                            taxonomy = recordTax,
                        )
                    }
                }

                if (updates.isEmpty()) {
                    val apiPart = if (apiStats.values.sum() > 0) "（API1:${apiStats["api1"]}）" else ""
                    val aiPart = if (canUseAi) "AI 未返回有效值$apiPart" else "AI 未启用/未配置"
                    viewModel.updateAssistantFixState {
                        it.copy(error = "没有可写入的结果：本机库未匹配到；$aiPart。建议逐条点“处理/编辑”核对物种名是否准确。")
                    }
                    return@block
                }

                val apiTrace = if (apiStats.values.sum() > 0) {
                    "本次使用：API1 ${apiStats["api1"]} 条。"
                } else {
                    "本次未调用 AI（仅使用本机库）。"
                }
                viewModel.updateAssistantFixState { it.copy(apiTrace = apiTrace) }

                viewModel.updateCurrentDataset { cur ->
                    val nextSpecies = cur.species.map { sp ->
                        val u = updates[sp.id] ?: return@map sp
                        var next = sp
                        if (kind == AutoFixKind.Taxonomy && next.nameLatin.isBlank() && u.nameLatin.isNotBlank()) {
                            next = next.copy(nameLatin = u.nameLatin)
                        }
                        if (kind == AutoFixKind.WetWeight && next.avgWetWeightMg == null && u.wetWeightMg != null) {
                            next = next.copy(avgWetWeightMg = u.wetWeightMg)
                        }
                        val ut = u.taxonomy
                        if (kind == AutoFixKind.Taxonomy && listOf(ut.lvl1, ut.lvl2, ut.lvl3, ut.lvl4, ut.lvl5).any { it.isNotBlank() }) {
                            val t = next.taxonomy
                            next = next.copy(
                                taxonomy = t.copy(
                                    lvl1 = t.lvl1.ifBlank { ut.lvl1 },
                                    lvl2 = t.lvl2.ifBlank { ut.lvl2 },
                                    lvl3 = t.lvl3.ifBlank { ut.lvl3 },
                                    lvl4 = t.lvl4.ifBlank { ut.lvl4 },
                                    lvl5 = t.lvl5.ifBlank { ut.lvl5 },
                                ),
                            )
                        }
                        next
                    }
                    val nextSession = mergeSession(cur.lastAutoMatch, updates.values)
                    cur.copy(species = nextSpecies, lastAutoMatch = nextSession)
                }

                val msg = "已处理：写入 ${updates.size} 个物种" + if (errors.isNotEmpty()) "（${errors.size} 个未成功）" else ""
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                viewModel.updateAssistantFixState { it.copy(error = if (errors.isNotEmpty()) errors.take(6).joinToString("\n") else null) }
            } finally {
                viewModel.updateAssistantFixState { it.copy(busy = false, progress = "") }
            }
        }
    }

    fun runAutoFixTaxonomy() = runAutoFix(AutoFixKind.Taxonomy)

    fun runAutoFixWetWeight() = runAutoFix(AutoFixKind.WetWeight)

    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        Text("AI 监测助手", style = MaterialTheme.typography.titleLarge)
        Text(
            "用途：监督录入→计算→导出全过程；当数据缺失/不一致时提醒。你也可以追溯某点位的计算过程（含核心公式）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )

        if (!settings.aiUiHidden) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("启用 AI 辅助", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "需要可用的 API；不可用时本机仍会做规则检查。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            )
                        }
                        Switch(
                            checked = settings.aiAssistantEnabled,
                            onCheckedChange = { checked ->
                                if (!checked) {
                                    updateSettings(settings.copy(aiAssistantEnabled = false))
                                    return@Switch
                                }
                                // Turn on requires API check.
                                viewModel.updateAssistantApiCheckBusy(true)
                                scope.launch {
                                    val (h1, h2) = viewModel.checkApis(settings)
                                    viewModel.updateAssistantApiCheckBusy(false)
                                    val okAny = h1.ok || h2.ok
                                    val dualOk = h1.ok && h2.ok
                                    if (okAny) {
                                        updateSettings(settings.copy(aiAssistantEnabled = true))
                                        if (settings.aiUseDualApi && !dualOk) {
                                            val only = if (h1.ok) "API1" else "API2"
                                            Toast.makeText(context, "仅 $only 可用，已自动使用单 API。", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        updateSettings(settings.copy(aiAssistantEnabled = false))
                                        Toast.makeText(context, "API 不可用，已保持关闭（可先点“检测 API”查看原因）", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !checkingApi,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = settings.aiUseDualApi,
                            onCheckedChange = { updateSettings(settings.copy(aiUseDualApi = it)) },
                            enabled = !settings.aiAssistantEnabled,
                        )
                        Column {
                            Text("使用双 API（更可靠）", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "启用后优先双 API；仅一项可用时自动单 API（优先 API1）。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            )
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.updateAssistantApiCheckBusy(true)
                            scope.launch {
                                viewModel.checkApis(settings)
                                viewModel.updateAssistantApiCheckBusy(false)
                            }
                        },
                        enabled = !checkingApi,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (checkingApi) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                            Text("检测中…")
                        } else {
                            Text("检测 API")
                        }
                    }

                    Text(
                        "API1：${api1Health?.message ?: "未检测"}；API2：${api2Health?.message ?: "未检测"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    Text(
                        "最近 ${apiHealthRecent.size.coerceAtMost(20)} 次健康度",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (apiHealthRecent.isEmpty()) {
                        Text(
                            "暂无记录。点击“检测 API”后会记录成功率与耗时。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                    } else {
                        val success = apiHealthRecent.count { it.ok }
                        val avgLatency = apiHealthRecent.mapNotNull { it.latencyMs }.average().let {
                            if (it.isNaN()) null else it
                        }
                        Text(
                            "成功率 ${(success * 100f / apiHealthRecent.size).toInt()}% · 平均耗时 ${avgLatency?.toInt() ?: 0}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                        val latest = apiHealthRecent.takeLast(6).reversed()
                        latest.forEach { row ->
                            val icon = if (row.ok) "✓" else "✗"
                            val latency = row.latencyMs?.let { " · ${it}ms" }.orEmpty()
                            Text(
                                "$icon ${row.apiName} · ${row.message}$latency",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                            )
                        }
                    }
                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("本机规则检查（全局监督）", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onOpenFocus) { Text("专注录入") }
                        TextButton(onClick = onOpenSpecies) { Text("去物种页") }
                    }
                }

                val hasWetTargets = ds.species.any { sp -> anyCountPositive(sp) && sp.nameCn.isNotBlank() && sp.avgWetWeightMg == null }
                val hasTaxTargets = ds.species.any { sp ->
                    anyCountPositive(sp) &&
                        sp.nameCn.isNotBlank() &&
                        (
                            sp.nameLatin.isBlank() ||
                                sp.taxonomy.lvl1.isBlank() ||
                                sp.taxonomy.lvl2.isBlank() ||
                                sp.taxonomy.lvl3.isBlank() ||
                                sp.taxonomy.lvl4.isBlank() ||
                                sp.taxonomy.lvl5.isBlank()
                            )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("写入本机库", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = settings.autoMatchWriteToDb,
                        onCheckedChange = { v -> viewModel.saveSettings(settings.copy(autoMatchWriteToDb = v)) },
                    )
                }
                Text(
                    if (settings.autoMatchWriteToDb) "已开启：AI 补齐到的分类/湿重会写入自定义库（不会随“清空本次匹配”回滚）。" else "默认关闭：补齐结果只写入当前数据集（不会自动写入本机库）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        enabled = !fixBusy && hasTaxTargets,
                        onClick = ::runAutoFixTaxonomy,
                        modifier = Modifier.weight(1f),
                    ) { Text("补齐分类") }
                    Button(
                        enabled = !fixBusy && hasWetTargets,
                        onClick = ::runAutoFixWetWeight,
                        modifier = Modifier.weight(1f),
                    ) { Text("补齐湿重") }
                }
                if (fixProgress.isNotBlank()) {
                    Text(fixProgress, style = MaterialTheme.typography.bodySmall)
                }
                if (fixApiTrace.isNotBlank()) {
                    Text(fixApiTrace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                }
                if (fixError != null) {
                    Text("提示：$fixError", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }

                val errIssues = issues.filter { it.level == IssueLevel.Error }
                val warnIssues = issues.filter { it.level == IssueLevel.Warn }
                val infoIssues = issues.filter { it.level == IssueLevel.Info }
                val ignoreCandidates = warnIssues + infoIssues

                Text(
                    "ERROR $errCount · WARN $warnCount · INFO $infoCount · 已忽略 $ignoredCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (errCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        enabled = ignoreCandidates.isNotEmpty(),
                        onClick = { ignoreIssues(ignoreCandidates.map { it.key }) },
                    ) { Text("一键忽略可忽略项") }
                    OutlinedButton(
                        enabled = ignoredCount > 0,
                        onClick = ::clearIgnoredIssues,
                    ) { Text("恢复忽略") }
                }

                @Composable
                fun issueRow(issue: DataIssue, allowIgnore: Boolean) {
                    val color = when (issue.level) {
                        IssueLevel.Info -> MaterialTheme.colorScheme.primary
                        IssueLevel.Warn -> Color(0xFFB45309)
                        IssueLevel.Error -> MaterialTheme.colorScheme.error
                    }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "• ${issue.title}${if (issue.detail.isNotBlank()) "：${issue.detail}" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = color,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            val sid = issue.speciesId
                            val pid = issue.pointId
                            if (sid != null && (issue.title == "缺少平均湿重" || issue.title == "缺少大类分类")) {
                                val kind = if (issue.title == "缺少平均湿重") {
                                    com.plankton.one102.ui.PendingSpeciesActionKind.EditWetWeight
                                } else {
                                    com.plankton.one102.ui.PendingSpeciesActionKind.EditTaxonomy
                                }
                                OutlinedButton(
                                    onClick = {
                                        viewModel.requestSpeciesAction(sid, kind)
                                        onOpenSpecies()
                                    },
                                ) { Text("处理") }
                                TextButton(
                                    onClick = { onOpenSpeciesEdit(sid) },
                                ) { Text("编辑") }
                            } else if (pid != null && (issue.title == "浓缩体积缺失/不合法" || issue.title == "采样点名称为空" || issue.title == "原水体积不合法")) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.setActivePointId(pid)
                                        onOpenPoints()
                                    },
                                ) { Text("去采样点") }
                            }
                            if (allowIgnore) {
                                TextButton(onClick = { ignoreIssues(listOf(issue.key)) }) { Text("忽略") }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("必须修复", style = MaterialTheme.typography.titleSmall)
                    if (errIssues.isEmpty()) {
                        Text("暂无。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                    } else {
                        for (issue in errIssues) {
                            issueRow(issue, allowIgnore = false)
                        }
                    }

                    Text("可忽略", style = MaterialTheme.typography.titleSmall)
                    if (warnIssues.isEmpty()) {
                        Text("暂无。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                    } else {
                        for (issue in warnIssues) {
                            issueRow(issue, allowIgnore = true)
                        }
                    }

                    Text("提示", style = MaterialTheme.typography.titleSmall)
                    if (infoIssues.isEmpty()) {
                        Text("暂无。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                    } else {
                        for (issue in infoIssues) {
                            issueRow(issue, allowIgnore = true)
                        }
                    }
                }
            }
        }

        // Point trace
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("点位追溯（一步一步）", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (p in ds.points) {
                        val selected = p.id == activePointId
                        val label = p.label.ifBlank { "未命名" }
                        if (selected) {
                            Button(onClick = { activePointId = p.id }) { Text(label) }
                        } else {
                            TextButton(onClick = { activePointId = p.id }) { Text(label) }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        enabled = !traceBusy,
                        onClick = {
                            if (traceBusy) return@Button
                            viewModel.updateAssistantTraceState { it.copy(error = null) }
                            val pointId = activePointId
                            if (traceState.byPointId[pointId].isNullOrBlank()) {
                                viewModel.startAssistantTrace(ds, pointId) { dataset, pid ->
                                    withContext(Dispatchers.Default) { buildPointTrace(dataset, pid) }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        if (traceBusy) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                            Text("生成中…")
                        } else {
                            Text("生成追溯文本")
                        }
                    }
                    OutlinedButton(
                        enabled = trace.isNotBlank(),
                        onClick = {
                            context.copyToClipboard("点位追溯", trace)
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("复制") }
                    OutlinedButton(
                        enabled = trace.isNotBlank() || traceError != null,
                        onClick = { viewModel.clearAssistantTrace(activePointId) },
                        modifier = Modifier.weight(1f),
                    ) { Text("清除") }
                }

                if (!settings.aiUiHidden) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = includeTrace, onCheckedChange = { viewModel.setAssistantIncludeTrace(it) })
                        Text("向 AI 提问时附带当前追溯文本", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (trace.isNotBlank()) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            AiRichText(text = trace, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else if (traceError != null) {
                    Text(traceError.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (!settings.aiUiHidden) {
            // AI QA
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("询问 / AI 监测（可选）", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "例如：\"解释 1-0 点位的 H' 是怎么来的？\" 或 \"检查我这份数据哪里可能不合理？\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )

                    OutlinedTextField(
                        value = question,
                        onValueChange = { viewModel.updateAssistantQuestion(it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        label = { Text("你的问题") },
                        placeholder = { Text("输入问题后点击“询问 AI”") },
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            enabled = !busy && question.trim().isNotEmpty(),
                            onClick = {
                                val q = question.trim()
                                viewModel.launchAssistantTask block@{
                                    if (!settings.aiAssistantEnabled) {
                                        aiNotice = "请先在设置中开启 AI 功能。"
                                        return@block
                                    }
                                    if (!hasApi(settings.api1) && !hasApi(settings.api2)) {
                                        aiNotice = "请先配置 API1 或 API2。"
                                        return@block
                                    }
                                    aiNotice = null
                                    val ctx = buildAssistantContextWithDocs(
                                        context = context,
                                        dataset = ds,
                                        issues = issues,
                                        taxonomyRepo = taxonomyRepo,
                                        taxonomyOverrideRepo = taxonomyOverrideRepo,
                                        wetWeightRepo = wetWeightRepo,
                                        aliasRepo = aliasRepo,
                                    )
                                    val traceSnippet = if (includeTrace && trace.isNotBlank()) clipTrace(trace) else ""
                                    val prompt = buildString {
                                        appendLine(ctx)
                                        if (traceSnippet.isNotBlank()) {
                                            appendLine()
                                            appendLine("【点位追溯文本】")
                                            appendLine(traceSnippet)
                                        }
                                        appendLine()
                                        appendLine("【用户问题】$q")
                                    }
                                    val choice = resolveAiChoice(api1HealthState, api2HealthState)
                                    viewModel.startAssistantAiTask(
                                        prompt = prompt,
                                        api1 = choice.primary,
                                        api2 = choice.secondary,
                                        useDualApi = choice.useDual,
                                        taskLabel = "询问 AI",
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            if (busy) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                                Text("询问中…")
                            } else {
                                Text("询问 AI")
                            }
                        }

                        Button(
                            enabled = !busy,
                            onClick = {
                                viewModel.launchAssistantTask block@{
                                    if (!settings.aiAssistantEnabled) {
                                        aiNotice = "请先在设置中开启 AI 功能。"
                                        return@block
                                    }
                                    if (!hasApi(settings.api1) && !hasApi(settings.api2)) {
                                        aiNotice = "请先配置 API1 或 API2。"
                                        return@block
                                    }
                                    aiNotice = null
                                    val ctx = buildAssistantContextWithDocs(
                                        context = context,
                                        dataset = ds,
                                        issues = issues,
                                        taxonomyRepo = taxonomyRepo,
                                        taxonomyOverrideRepo = taxonomyOverrideRepo,
                                        wetWeightRepo = wetWeightRepo,
                                        aliasRepo = aliasRepo,
                                    )
                                    val traceSnippet = if (includeTrace && trace.isNotBlank()) clipTrace(trace, maxChars = 4000) else ""
                                    val prompt = buildString {
                                        appendLine(ctx)
                                        if (traceSnippet.isNotBlank()) {
                                            appendLine()
                                            appendLine("【点位追溯文本】")
                                            appendLine(traceSnippet)
                                        }
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
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("AI 分析当前数据") }
                    }

                    if (aiNotice != null) {
                        Text(aiNotice!!, style = MaterialTheme.typography.bodySmall)
                    }

                    if (busy) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                            if (aiState.taskLabel.isNotBlank()) {
                                Text(aiState.taskLabel, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    OutlinedButton(
                        enabled = answer1.isNotBlank() || answer2.isNotBlank() || lastError != null,
                        onClick = { viewModel.clearAssistantReplies() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("清除回复") }

                    if (!settings.aiAssistantEnabled) {
                        Text("提示：启用 AI 辅助后可使用此功能。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                    } else if (!canUseAiNow()) {
                        Text("提示：API 未通过检测或未配置完整，询问/分析会直接调用，失败后自动检测。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                    } else if (settings.aiUseDualApi && !(api1Ok && api2Ok)) {
                        val only = if (api1Ok) "API1" else "API2"
                        Text("提示：仅 $only 可用，已自动使用单 API。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                    }

                    if (lastError != null) {
                        Text("错误：$lastError", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }

                    if (answer1.isNotBlank()) {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    aiState.answer1Label.ifBlank { settings.api1.name.ifBlank { "API 1" } },
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                AiRichText(
                                    text = answer1,
                                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                    compact = true,
                                    maxLines = 10,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    TextButton(
                                        onClick = {
                                            context.copyToClipboard("AI回答", answer1)
                                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                        },
                                    ) { Text("复制") }
                                    TextButton(
                                        onClick = {
                                            fullTextTitle = settings.api1.name.ifBlank { "API 1" }
                                            fullTextBody = answer1
                                        },
                                    ) { Text("查看全文") }
                                }
                            }
                        }
                    }

                    if (answer2.isNotBlank()) {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    aiState.answer2Label.ifBlank { settings.api2.name.ifBlank { "API 2" } },
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                AiRichText(
                                    text = answer2,
                                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                    compact = true,
                                    maxLines = 10,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    TextButton(
                                        onClick = {
                                            context.copyToClipboard("AI回答", answer2)
                                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                        },
                                    ) { Text("复制") }
                                    TextButton(
                                        onClick = {
                                            fullTextTitle = settings.api2.name.ifBlank { "API 2" }
                                            fullTextBody = answer2
                                        },
                                    ) { Text("查看全文") }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (fullTextBody != null) {
        Dialog(
            onDismissRequest = { fullTextBody = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 2.dp,
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(fullTextTitle.ifBlank { "全文" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    context.copyToClipboard("AI回答", fullTextBody.orEmpty())
                                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                },
                            ) { Text("复制") }
                            TextButton(onClick = { fullTextBody = null }) { Text("关闭") }
                        }
                    }

                    GlassCard(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            AiRichText(
                                text = fullTextBody.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                            )
                        }
                    }
                }
            }
        }
    }
}
}
