package com.plankton.one102.ui.screens

import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import androidx.core.content.FileProvider
import com.plankton.one102.PlanktonApplication
import com.plankton.one102.domain.Dataset
import com.plankton.one102.domain.DEFAULT_WET_WEIGHT_LIBRARY_ID
import com.plankton.one102.domain.DEFAULT_WET_WEIGHT_LIBRARY_NAME
import com.plankton.one102.domain.Id
import com.plankton.one102.domain.LVL1_ORDER
import com.plankton.one102.domain.Species
import com.plankton.one102.domain.Taxonomy
import com.plankton.one102.domain.TaxonomyRecord
import com.plankton.one102.domain.AutoMatchEntry
import com.plankton.one102.domain.AutoMatchSession
import com.plankton.one102.domain.ApiConfig
import com.plankton.one102.domain.bestCanonicalName
import com.plankton.one102.domain.nowIso
import com.plankton.one102.domain.WetWeightEntry
import com.plankton.one102.domain.WetWeightTaxonomy
import com.plankton.one102.domain.createBlankSpecies
import com.plankton.one102.domain.newId
import com.plankton.one102.domain.normalizeLvl1Name
import com.plankton.one102.domain.MergeCountsMode
import com.plankton.one102.domain.mergeDuplicateSpeciesByName
import com.plankton.one102.domain.SpeciesDbItem
import com.plankton.one102.domain.resolveSiteAndDepthForPoint
import com.plankton.one102.domain.parseIntSmart
import com.plankton.one102.ui.DatabaseViewModel
import com.plankton.one102.ui.ImageImportMode
import com.plankton.one102.ui.ImageImportPoint
import com.plankton.one102.ui.ImageImportResult
import com.plankton.one102.ui.ImageImportSource
import com.plankton.one102.ui.ImageImportSpecies
import com.plankton.one102.ui.MainViewModel
import com.plankton.one102.ui.NameMatchKind
import com.plankton.one102.ui.HapticKind
import com.plankton.one102.ui.performHaptic
import com.plankton.one102.ui.dialogs.BatchCountDialog
import com.plankton.one102.ui.dialogs.TaxonomyDialog
import com.plankton.one102.ui.dialogs.TaxonomyQueryDialog
import com.plankton.one102.ui.dialogs.WetWeightQueryDialog
import com.plankton.one102.ui.components.GlassBackground
import com.plankton.one102.ui.components.GlassCard
import com.plankton.one102.ui.components.GlassPrefs
import com.plankton.one102.ui.components.LocalGlassPrefs
import com.plankton.one102.data.api.ChatCompletionClient
import com.plankton.one102.data.api.AiImageImport
import com.plankton.one102.data.api.AiSpeciesInfo
import com.plankton.one102.data.api.buildImageImportPrompt
import com.plankton.one102.data.api.buildSpeciesInfoPrompt
import com.plankton.one102.data.api.buildSpeciesTaxonomyAutofillPrompt
import com.plankton.one102.data.api.buildSpeciesWetWeightAutofillPrompt
import com.plankton.one102.data.api.extractFinalImageJson
import com.plankton.one102.data.api.extractFinalSpeciesJson
import com.plankton.one102.data.api.parseAiImageImport
import com.plankton.one102.data.api.parseAiSpeciesInfo
import com.plankton.one102.importer.buildVisionImageUrls
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.catch
import java.util.UUID
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun SpeciesScreen(
    viewModel: MainViewModel,
    databaseViewModel: DatabaseViewModel,
    padding: PaddingValues,
    onOpenFocus: () -> Unit = {},
    onEditSpecies: (Id) -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as PlanktonApplication
    val wetWeightRepo = app.wetWeightRepository
    val taxonomyRepo = app.taxonomyRepository
    val taxonomyOverrideRepo = app.taxonomyOverrideRepository
    val aliasRepo = app.aliasRepository
    val aiCacheRepo = app.aiCacheRepository
    val client = remember { ChatCompletionClient() }

    val dataset by viewModel.currentDataset.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val globalPointId by viewModel.activePointId.collectAsStateWithLifecycle()
    val dbItems by databaseViewModel.items.collectAsStateWithLifecycle()
    val libraries by wetWeightRepo.observeLibraries()
        .catch { emit(emptyList()) }
        .collectAsStateWithLifecycle(emptyList())

    val activeLibraryName = libraries.firstOrNull {
        it.id == settings.activeWetWeightLibraryId.trim().ifBlank { DEFAULT_WET_WEIGHT_LIBRARY_ID }
    }?.name ?: DEFAULT_WET_WEIGHT_LIBRARY_NAME

    val ds = dataset ?: run {
        GlassBackground {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) { Text("加载中…") }
        }
        return
    }

    val scope = rememberCoroutineScope()

    var panelState by rememberSaveable(stateSaver = SpeciesPanelStateSaver) {
        mutableStateOf(SpeciesPanelState())
    }
    val imageImportState by viewModel.imageImportState.collectAsStateWithLifecycle()
    var imageImportCameraUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(ds.id, imageImportState.datasetId) {
        if (imageImportState.datasetId != ds.id) {
            viewModel.resetImageImportState(ds.id)
            imageImportCameraUri = null
        }
    }

    LaunchedEffect(settings.api1.baseUrl, settings.api1.model) {
        viewModel.updateImageImportState { state -> state.copy(api1Unsupported = false) }
    }

    LaunchedEffect(settings.api2.baseUrl, settings.api2.model) {
        viewModel.updateImageImportState { state -> state.copy(api2Unsupported = false) }
    }

    LaunchedEffect(settings.imageApi.baseUrl, settings.imageApi.model) {
        viewModel.updateImageImportState { state -> state.copy(apiImageUnsupported = false) }
    }

    fun createTempImageUri(): Uri {
        val dir = java.io.File(context.cacheDir, "image_import").apply { mkdirs() }
        val file = java.io.File(dir, "import_${System.currentTimeMillis()}.jpg")
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    val pickImagesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        viewModel.updateImageImportState { state ->
            state.copy(images = (state.images + uris).distinct())
        }
    }

    val takePhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok ->
        if (!ok) return@rememberLauncherForActivityResult
        val uri = imageImportCameraUri ?: return@rememberLauncherForActivityResult
        viewModel.updateImageImportState { state ->
            state.copy(images = (state.images + uri).distinct())
        }
        imageImportCameraUri = null
    }

    fun apiConfigured(api: ApiConfig): Boolean = api.baseUrl.isNotBlank() && api.model.isNotBlank()

    fun visionStatus(api: ApiConfig, blocked: Boolean): String? {
        if (!apiConfigured(api)) return "未配置 Base URL / Model"
        if (blocked) return "当前模型不支持图片输入"
        if (!isVisionModel(api.model, api.baseUrl)) return "当前模型不支持图片输入"
        return null
    }

    fun resolveSpeciesName(raw: String, aliasMap: Map<String, String>, candidates: Set<String>): NameMatchResult {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return NameMatchResult(name = trimmed, kind = NameMatchKind.Raw)

        val alias = aliasMap[trimmed]
        if (!alias.isNullOrBlank()) {
            return NameMatchResult(name = alias, kind = NameMatchKind.Alias, score = 0.98)
        }
        if (candidates.contains(trimmed)) {
            return NameMatchResult(name = trimmed, kind = NameMatchKind.Exact, score = 1.0)
        }
        val correction = bestCanonicalName(trimmed, candidates)
        if (correction != null && correction.score >= IMAGE_MATCH_MIN_SCORE) {
            return NameMatchResult(name = correction.canonical, kind = NameMatchKind.Fuzzy, score = correction.score)
        }
        return NameMatchResult(name = trimmed, kind = NameMatchKind.Raw, score = correction?.score)
    }

    fun sanitizeImageImport(
        raw: AiImageImport,
        aliasMap: Map<String, String>,
        candidates: Set<String>,
    ): ImageImportResult {
        val warnings = raw.warnings.toMutableList()
        val notes = raw.notes.toMutableList()
        var dropped = 0

        val pointMap = linkedMapOf<String, MutableList<ImageImportSpecies>>()

        for (p in raw.points) {
            val label = p.label.trim()
            if (label.isBlank()) {
                dropped += 1
                warnings += "忽略空白点位条目"
                continue
            }
            val bucket = pointMap.getOrPut(label) { mutableListOf() }
            for (row in p.species) {
                val (nameRawBase, countFromName) = parseCountFromText(row.name)
                val nameRaw = nameRawBase.trim()
                if (nameRaw.isBlank()) {
                    dropped += 1
                    warnings += "点位 $label 有空白物种名称，已忽略"
                    continue
                }
                if (isLikelyInvalidScribble(nameRaw, row.rawLine)) {
                    dropped += 1
                    warnings += "点位 $label / 物种 $nameRaw 疑似涂改，已忽略"
                    continue
                }
                val count = row.count ?: parseCountExpr(row.countExpr) ?: countFromName
                if (count == null) {
                    dropped += 1
                    warnings += "点位 $label / 物种 $nameRaw 计数无法识别，已忽略"
                    continue
                }
                val match = resolveSpeciesName(nameRaw, aliasMap, candidates)
                if (match.kind == NameMatchKind.Raw && (match.score ?: 0.0) > 0.0) {
                    warnings += "未自动校正：$nameRaw（疑似 ${match.name}，score=${"%.2f".format(match.score)}）"
                }
                if (match.kind == NameMatchKind.Fuzzy) {
                    warnings += "已校正：$nameRaw → ${match.name}（score=${"%.2f".format(match.score)}）"
                }
                if (row.confidence != null && row.confidence < IMAGE_CONFIDENCE_WARN) {
                    warnings += "识别置信度偏低：点位 $label / 物种 $nameRaw（${"%.2f".format(row.confidence)}）"
                }
                bucket += ImageImportSpecies(
                    nameRaw = nameRaw,
                    nameResolved = match.name,
                    count = count,
                    countExpr = row.countExpr,
                    matchKind = match.kind,
                    matchScore = match.score,
                    confidence = row.confidence,
                )
            }
        }

        val points = pointMap.map { (label, rows) ->
            val merged = linkedMapOf<String, ImageImportSpecies>()
            for (row in rows) {
                val key = row.nameResolved
                val existing = merged[key]
                if (existing == null) {
                    merged[key] = row
                } else {
                    merged[key] = existing.copy(count = existing.count + row.count)
                }
            }
            ImageImportPoint(label = label, species = merged.values.toList())
        }

        return ImageImportResult(points = points, warnings = warnings, notes = notes, droppedCount = dropped)
    }

    fun mergeImageImportResults(results: List<ImageImportResult>): ImageImportResult {
        val warnings = mutableListOf<String>()
        val notes = mutableListOf<String>()
        var dropped = 0
        val pointsMap = linkedMapOf<String, MutableMap<String, ImageImportSpecies>>()

        for (res in results) {
            warnings += res.warnings
            notes += res.notes
            dropped += res.droppedCount
            for (p in res.points) {
                val bySpecies = pointsMap.getOrPut(p.label) { mutableMapOf() }
                for (sp in p.species) {
                    val existing = bySpecies[sp.nameResolved]
                    if (existing == null) {
                        bySpecies[sp.nameResolved] = sp
                    } else {
                        val mergedCount = maxOf(existing.count, sp.count)
                        if (existing.count != sp.count) {
                            warnings += "点位 ${p.label} / 物种 ${sp.nameResolved} 跨图片重复（${existing.count} vs ${sp.count}），已取最大值"
                        }
                        bySpecies[sp.nameResolved] = existing.copy(count = mergedCount, countExpr = null)
                    }
                }
            }
        }

        val points = pointsMap.map { (label, map) ->
            ImageImportPoint(label = label, species = map.values.toList())
        }
        return ImageImportResult(points = points, warnings = warnings, notes = notes, droppedCount = dropped)
    }

    fun buildImageRepairPrompt(raw: String): String {
        return """
            你是 JSON 修复助手。请把下方文本整理成严格 JSON，仅输出 JSON 本身。
            规则：
            - 目标是提取/整理为 FINAL_IMAGE_JSON 结构中的 JSON 对象
            - 不要输出解释或多余文字

            待整理文本：
            $raw
        """.trimIndent()
    }

    fun selectedImageImportResult(): ImageImportResult? {
        return when (imageImportState.source) {
            ImageImportSource.Api1 -> imageImportState.api1
            ImageImportSource.Api2 -> imageImportState.api2
            ImageImportSource.ImageApi -> imageImportState.apiImage
        }
    }

    fun runImageImport() {
        val snapshot = imageImportState
        viewModel.updateImageImportState { state ->
            state.copy(error = null, message = null, api1 = null, api2 = null, apiImage = null)
        }

        if (!settings.aiAssistantEnabled) {
            viewModel.updateImageImportState { state -> state.copy(error = "请先在设置中开启 AI 功能。") }
            return
        }
        if (!snapshot.useApi1 && !snapshot.useApi2 && !snapshot.useImageApi) {
            viewModel.updateImageImportState { state -> state.copy(error = "请至少选择一个 API。") }
            return
        }
        val api1Status = if (snapshot.useApi1) visionStatus(settings.api1, snapshot.api1Unsupported) else null
        val api2Status = if (snapshot.useApi2) visionStatus(settings.api2, snapshot.api2Unsupported) else null
        val imgStatus = if (snapshot.useImageApi) visionStatus(settings.imageApi, snapshot.apiImageUnsupported) else null
        if (api1Status != null) {
            viewModel.updateImageImportState { state -> state.copy(error = "API1 $api1Status") }
            return
        }
        if (api2Status != null) {
            viewModel.updateImageImportState { state -> state.copy(error = "API2 $api2Status") }
            return
        }
        if (imgStatus != null) {
            viewModel.updateImageImportState { state -> state.copy(error = "图片 API $imgStatus") }
            return
        }
        if (snapshot.images.isEmpty()) {
            viewModel.updateImageImportState { state -> state.copy(error = "请先选择或拍摄图片。") }
            return
        }

        viewModel.updateImageImportState { state -> state.copy(busy = true, message = "准备图片…") }

        val dsSnapshot = ds
        val dbSnapshot = dbItems
        val settingsSnapshot = settings
        val imagesSnapshot = snapshot.images
        val useApi1 = snapshot.useApi1
        val useApi2 = snapshot.useApi2
        val useImageApi = snapshot.useImageApi

        viewModel.launchAssistantTask {
            val errors = mutableListOf<String>()
            val aliasMap = runCatching { aliasRepo.getAll().associate { it.alias to it.canonicalNameCn } }.getOrElse { emptyMap() }
            val candidates = buildSet {
                addAll(dsSnapshot.species.mapNotNull { it.nameCn.trim().ifBlank { null } })
                addAll(dbSnapshot.mapNotNull { it.nameCn.trim().ifBlank { null } })
                addAll(aliasMap.values.mapNotNull { it.trim().ifBlank { null } })
            }

            val prompt = buildImageImportPrompt()
            val detailPrompt = buildImageImportPrompt(detailMode = true)
            val strictPrompt = buildImageImportPrompt(strictJsonOnly = true)

            suspend fun callOne(api: ApiConfig, label: String, maxTokens: Int): ImageImportResult {
                val results = mutableListOf<ImageImportResult>()
                val warnings = mutableListOf<String>()
                val multi = imagesSnapshot.size > 1
                val baseMaxSize = if (multi) 2000 else 2600
                val baseQuality = if (multi) 84 else 90
                val tileRows = if (multi) 1 else 2
                val tileCols = if (multi) 1 else 2
                val throttle = shouldThrottleVision(api)

                suspend fun buildUrls(uri: Uri, maxSize: Int, quality: Int, rows: Int, cols: Int): List<String> {
                    return buildVisionImageUrls(
                        context.contentResolver,
                        listOf(uri),
                        maxSize = maxSize,
                        jpegQuality = quality,
                        tileRows = rows,
                        tileCols = cols,
                        includeFull = true,
                    )
                }

                fun shrink(maxSize: Int): Int = (maxSize * 0.72f).toInt().coerceAtLeast(1200)

                suspend fun callVisionWithFallback(uri: Uri, promptText: String, maxTokens: Int): String {
                    var maxSize = baseMaxSize
                    var quality = baseQuality
                    var rows = tileRows
                    var cols = tileCols
                    var tokens = maxTokens
                    var triedShrink = false
                    var triedReduceTokens = false
                    var lastError: Throwable? = null

                    repeat(3) { attempt ->
                        if (throttle && attempt > 0) {
                            delay(900L * attempt)
                        }
                        val urls = buildUrls(uri, maxSize, quality, rows, cols)
                        try {
                            return client.callVision(api, promptText, urls, maxTokens = tokens)
                        } catch (err: Throwable) {
                            lastError = err
                            val msg = err.message
                            when {
                                isImagePayloadTooLarge(msg) && !triedShrink -> {
                                    triedShrink = true
                                    maxSize = shrink(maxSize)
                                    quality = (quality - 12).coerceAtLeast(70)
                                    rows = 1
                                    cols = 1
                                }
                                isContextLimitError(msg) && !triedReduceTokens -> {
                                    triedReduceTokens = true
                                    tokens = (tokens * 0.6).toInt().coerceAtLeast(600)
                                    rows = 1
                                    cols = 1
                                }
                                isRateLimitError(msg) -> {
                                    delay(1200L * (attempt + 1))
                                }
                                else -> throw err
                            }
                        }
                    }
                    throw lastError ?: IllegalStateException("识别失败")
                }

                imagesSnapshot.forEachIndexed { idx, uri ->
                    val labelIndex = "$label 第${idx + 1}张"
                    if (throttle && idx > 0) {
                        delay(900L)
                    }
                    val raw = runCatching {
                        callVisionWithFallback(uri, prompt, maxTokens)
                    }.getOrElse { err ->
                        warnings += "$labelIndex 识别失败：${describeVisionError(err.message ?: err.toString())}"
                        return@forEachIndexed
                    }

                    var json = extractFinalImageJson(raw)
                    if (json == null) {
                        val strictRaw = runCatching { callVisionWithFallback(uri, strictPrompt, maxTokens) }
                            .getOrElse { err ->
                                warnings += "$labelIndex 识别失败：${describeVisionError(err.message ?: err.toString())}"
                                return@forEachIndexed
                            }
                        json = extractFinalImageJson(strictRaw)
                    }
                    if (json == null) {
                        json = extractFinalImageJson(client.call(api, buildImageRepairPrompt(raw), maxTokens = 900))
                    }
                    var parsed = json?.let { parseAiImageImport(it) }
                    if (parsed != null && (parsed.points.isEmpty() || parsed.points.all { it.species.isEmpty() })) {
                        val detailRaw = runCatching { callVisionWithFallback(uri, detailPrompt, maxTokens + 400) }
                            .getOrElse { err ->
                                warnings += "$labelIndex 识别失败：${describeVisionError(err.message ?: err.toString())}"
                                null
                            }
                        if (detailRaw != null) {
                            val detailJson = extractFinalImageJson(detailRaw)
                            parsed = detailJson?.let { parseAiImageImport(it) } ?: parsed
                        }
                    }
                    val finalParsed = parsed ?: run {
                        warnings += "$labelIndex JSON 解析失败"
                        return@forEachIndexed
                    }
                    results += sanitizeImageImport(finalParsed, aliasMap, candidates)
                }

                if (results.isEmpty()) {
                    throw IllegalStateException("未获得可用识别结果")
                }
                val merged = mergeImageImportResults(results)
                return if (warnings.isEmpty()) merged else merged.copy(warnings = merged.warnings + warnings)
            }

            val baseTokens = if (imagesSnapshot.size > 1) 2000 else 1600
            val imageTokens = if (imagesSnapshot.size > 1) 2600 else 2000
            val shouldThrottle = listOf(
                useApi1 to settingsSnapshot.api1,
                useApi2 to settingsSnapshot.api2,
                useImageApi to settingsSnapshot.imageApi,
            ).any { (use, api) -> use && shouldThrottleVision(api) }
            val runParallel = imagesSnapshot.size <= 1 && !shouldThrottle

            val r1: Result<ImageImportResult>?
            val r2: Result<ImageImportResult>?
            val rImg: Result<ImageImportResult>?

            if (runParallel) {
                val a1 = if (useApi1) async { runCatching { callOne(settingsSnapshot.api1, "API1", baseTokens) } } else null
                val a2 = if (useApi2) async { runCatching { callOne(settingsSnapshot.api2, "API2", baseTokens) } } else null
                val aImg = if (useImageApi) async { runCatching { callOne(settingsSnapshot.imageApi, "图片 API", imageTokens) } } else null
                r1 = a1?.await()
                r2 = a2?.await()
                rImg = aImg?.await()
            } else {
                r1 = if (useApi1) runCatching { callOne(settingsSnapshot.api1, "API1", baseTokens) } else null
                r2 = if (useApi2) runCatching { callOne(settingsSnapshot.api2, "API2", baseTokens) } else null
                rImg = if (useImageApi) runCatching { callOne(settingsSnapshot.imageApi, "图片 API", imageTokens) } else null
            }

            var nextUseApi1 = useApi1
            var nextUseApi2 = useApi2
            var nextUseImageApi = useImageApi
            var nextApi1Unsupported = snapshot.api1Unsupported
            var nextApi2Unsupported = snapshot.api2Unsupported
            var nextApiImageUnsupported = snapshot.apiImageUnsupported

            val api1Result = r1?.getOrNull()
            val api2Result = r2?.getOrNull()
            val apiImageResult = rImg?.getOrNull()

            if (r1?.isSuccess == true) {
                // handled below
            } else if (r1?.isFailure == true) {
                val rawMsg = r1.exceptionOrNull()?.message
                if (isVisionUnsupportedError(rawMsg)) {
                    nextApi1Unsupported = true
                    nextUseApi1 = false
                    errors += "API1 当前模型不支持图片输入"
                } else {
                    errors += "API1 识别失败：${describeVisionError(rawMsg)}"
                }
            }
            if (r2?.isSuccess == true) {
                // handled below
            } else if (r2?.isFailure == true) {
                val rawMsg = r2.exceptionOrNull()?.message
                if (isVisionUnsupportedError(rawMsg)) {
                    nextApi2Unsupported = true
                    nextUseApi2 = false
                    errors += "API2 当前模型不支持图片输入"
                } else {
                    errors += "API2 识别失败：${describeVisionError(rawMsg)}"
                }
            }

            if (rImg?.isSuccess == true) {
                // handled below
            } else if (rImg?.isFailure == true) {
                val rawMsg = rImg.exceptionOrNull()?.message
                if (isVisionUnsupportedError(rawMsg)) {
                    nextApiImageUnsupported = true
                    nextUseImageApi = false
                    errors += "图片 API 当前模型不支持图片输入"
                } else {
                    errors += "图片 API 识别失败：${describeVisionError(rawMsg)}"
                }
            }

            val nextSource = when {
                apiImageResult != null -> ImageImportSource.ImageApi
                api1Result != null -> ImageImportSource.Api1
                api2Result != null -> ImageImportSource.Api2
                else -> snapshot.source
            }

            val chosen = when (nextSource) {
                ImageImportSource.Api1 -> api1Result
                ImageImportSource.Api2 -> api2Result
                ImageImportSource.ImageApi -> apiImageResult
            }
            val finalMessage = if (chosen == null) {
                "未获得可用识别结果"
            } else {
                "识别完成：点位 ${chosen.points.size} 个，物种条目 ${chosen.points.sumOf { it.species.size }} 条"
            }
            val finalError = if (errors.isEmpty()) null else errors.joinToString("\n")
            viewModel.updateImageImportState { state ->
                state.copy(
                    busy = false,
                    message = finalMessage,
                    error = finalError,
                    api1 = api1Result,
                    api2 = api2Result,
                    apiImage = apiImageResult,
                    source = nextSource,
                    useApi1 = nextUseApi1,
                    useApi2 = nextUseApi2,
                    useImageApi = nextUseImageApi,
                    api1Unsupported = nextApi1Unsupported,
                    api2Unsupported = nextApi2Unsupported,
                    apiImageUnsupported = nextApiImageUnsupported,
                )
            }
        }
    }

    fun applyImageImport(result: ImageImportResult, mode: ImageImportMode, overwriteExisting: Boolean) {
        val dbByName = dbItems.associateBy { it.nameCn }

        if (mode == ImageImportMode.NewDataset) {
            val createdAt = nowIso()
            val pointList = mutableListOf<com.plankton.one102.domain.Point>()
            val pointIdByLabel = linkedMapOf<String, String>()
            for (p in result.points) {
                val id = newId()
                val label = normalizePointLabel(p.label)
                pointIdByLabel[label] = id
                val (site, depth) = resolveSiteAndDepthForPoint(label = label, site = null, depthM = null)
                pointList += com.plankton.one102.domain.Point(
                    id = id,
                    label = label,
                    vConcMl = null,
                    vOrigL = settings.defaultVOrigL,
                    site = site,
                    depthM = depth,
                )
            }

            val speciesMap = linkedMapOf<String, MutableMap<String, Int>>()
            for (p in result.points) {
                val pid = pointIdByLabel[normalizePointLabel(p.label)] ?: continue
                for (row in p.species) {
                    val name = row.nameResolved
                    val map = speciesMap.getOrPut(name) { mutableMapOf() }
                    map[pid] = (map[pid] ?: 0) + row.count
                }
            }

            val speciesList = speciesMap.map { (name, counts) ->
                val entry = dbByName[name]
                val taxonomy = entry?.taxonomy ?: Taxonomy()
                Species(
                    id = newId(),
                    nameCn = name,
                    nameLatin = entry?.nameLatin ?: "",
                    taxonomy = taxonomy,
                    avgWetWeightMg = entry?.wetWeightMg,
                    countsByPointId = pointList.associate { it.id to (counts[it.id] ?: 0) },
                )
            }

            val next = Dataset(
                id = newId(),
                titlePrefix = "图片导入",
                createdAt = createdAt,
                updatedAt = createdAt,
                points = pointList,
                species = speciesList,
            )
            viewModel.importNewDataset(next)
            Toast.makeText(context, "已新建数据集并导入图片识别结果", Toast.LENGTH_LONG).show()
            return
        }

        viewModel.updateCurrentDataset { cur ->
            val pointList = cur.points.toMutableList()
            val pointIdByLabel = pointList.associateBy { normalizePointLabel(it.label) }.mapValues { it.value.id }.toMutableMap()
            var addedPoints = 0
            val addedPointIds = mutableListOf<Id>()
            val pointCandidates = pointIdByLabel.keys.toMutableSet()

            fun resolvePointId(label: String): Id? {
                val key = normalizePointLabel(label)
                pointIdByLabel[key]?.let { return it }
                val best = bestCanonicalName(key, pointCandidates)
                if (best != null && best.score >= 0.9) {
                    return pointIdByLabel[best.canonical]
                }
                return null
            }

            fun ensurePoint(labelRaw: String): Id {
                val label = normalizePointLabel(labelRaw)
                resolvePointId(label)?.let { return it }
                val id = newId()
                val (site, depth) = resolveSiteAndDepthForPoint(label = label, site = null, depthM = null)
                pointList += com.plankton.one102.domain.Point(
                    id = id,
                    label = label,
                    vConcMl = null,
                    vOrigL = settings.defaultVOrigL,
                    site = site,
                    depthM = depth,
                )
                pointIdByLabel[label] = id
                pointCandidates += label
                addedPointIds += id
                addedPoints += 1
                return id
            }

            for (p in result.points) {
                ensurePoint(p.label)
            }

            val speciesList = cur.species.map { sp ->
                if (addedPointIds.isEmpty()) return@map sp
                val nextCounts = sp.countsByPointId.toMutableMap()
                for (pid in addedPointIds) {
                    if (!nextCounts.containsKey(pid)) nextCounts[pid] = 0
                }
                sp.copy(countsByPointId = nextCounts)
            }.toMutableList()
            val speciesByName = speciesList.associateBy { it.nameCn }.toMutableMap()
            var addedSpecies = 0
            val updatedSpecies = speciesList.toMutableList()
            val speciesCandidates = speciesByName.keys.toMutableSet()

            fun resolveSpeciesName(nameRaw: String): String {
                val name = nameRaw.trim()
                if (speciesByName.containsKey(name)) return name
                val best = bestCanonicalName(name, speciesCandidates)
                return if (best != null && best.score >= 0.9) best.canonical else name
            }

            fun ensureSpecies(name: String): Species {
                val resolved = resolveSpeciesName(name)
                val existing = speciesByName[resolved]
                if (existing != null) return existing
                val entry = dbByName[resolved]
                val taxonomy = entry?.taxonomy ?: Taxonomy()
                val counts = pointList.associate { it.id to 0 }
                val sp = Species(
                    id = newId(),
                    nameCn = resolved,
                    nameLatin = entry?.nameLatin ?: "",
                    taxonomy = taxonomy,
                    avgWetWeightMg = entry?.wetWeightMg,
                    countsByPointId = counts,
                )
                speciesList += sp
                updatedSpecies += sp
                speciesByName[resolved] = sp
                speciesCandidates += resolved
                addedSpecies += 1
                return sp
            }

            for (p in result.points) {
                val pid = resolvePointId(p.label) ?: ensurePoint(p.label)
                for (row in p.species) {
                    val sp = ensureSpecies(row.nameResolved)
                    val idx = updatedSpecies.indexOfFirst { it.id == sp.id }
                    if (idx < 0) continue
                    val nextCounts = updatedSpecies[idx].countsByPointId.toMutableMap()
                    val current = nextCounts[pid] ?: 0
                    nextCounts[pid] = if (overwriteExisting) row.count else current + row.count
                    updatedSpecies[idx] = updatedSpecies[idx].copy(countsByPointId = nextCounts)
                }
            }

            fun mergeDuplicatePoints(points: List<com.plankton.one102.domain.Point>, species: List<Species>): Pair<List<com.plankton.one102.domain.Point>, List<Species>> {
                val groups = linkedMapOf<String, MutableList<com.plankton.one102.domain.Point>>()
                for (p in points) {
                    val key = normalizePointLabel(p.label)
                    groups.getOrPut(key) { mutableListOf() }.add(p)
                }
                if (groups.values.none { it.size > 1 }) return points to species

                val keepPoints = mutableListOf<com.plankton.one102.domain.Point>()
                for ((_, list) in groups) {
                    val keep = list.first()
                    keepPoints += keep
                }

                val nextSpecies = species.map { sp ->
                    val nextCounts = mutableMapOf<Id, Int>()
                    for (p in keepPoints) {
                        val ids = groups[normalizePointLabel(p.label)]?.map { it.id } ?: listOf(p.id)
                        val sumCount = ids.sumOf { sp.countsByPointId[it] ?: 0 }
                        nextCounts[p.id] = sumCount
                    }
                    sp.copy(countsByPointId = nextCounts)
                }

                return keepPoints to nextSpecies
            }

            val (mergedPoints, mergedSpecies) = mergeDuplicatePoints(pointList, updatedSpecies)
            val mergedCount = pointList.size - mergedPoints.size
            val tail = if (overwriteExisting) "（计数已覆盖更新）" else "（计数已累加追加）"
            val msg = buildString {
                append("已导入点位 $addedPoints 个，新增物种 $addedSpecies 条")
                if (mergedCount > 0) append("；合并同名点位 $mergedCount 个")
                append(tail)
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            cur.copy(points = mergedPoints, species = mergedSpecies)
        }
    }

    if (ds.points.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("物种与计数", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                "当前数据集没有采样点。请先在底部「采样点」页面新增至少 1 条采样点后再录入计数。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
        return
    }

    var activePointId by remember(ds.id) { mutableStateOf(globalPointId ?: ds.points.firstOrNull()?.id ?: "") }
    if (ds.points.none { it.id == activePointId }) activePointId = ds.points.firstOrNull()?.id ?: ""
    LaunchedEffect(globalPointId, ds.points.size) {
        val pid = globalPointId
        if (pid != null && ds.points.any { it.id == pid } && pid != activePointId) {
            activePointId = pid
        }
    }
    LaunchedEffect(activePointId) {
        if (activePointId.isNotBlank()) viewModel.setActivePointId(activePointId)
    }

    var addDialogState by remember { mutableStateOf(SpeciesAddDialogState()) }
    var libraryUiState by remember { mutableStateOf(SpeciesLibraryUiState()) }
    var inlineEditState by remember { mutableStateOf(SpeciesInlineEditState()) }
    var confirmState by remember { mutableStateOf(SpeciesConfirmState()) }

    var activeSpeciesId by remember(ds.id) { mutableStateOf(ds.species.firstOrNull()?.id ?: "") }
    var autoMatchBusy by remember { mutableStateOf(false) }
    var autoMatchProgress by remember { mutableStateOf("") }
    var autoMatchError by remember { mutableStateOf<String?>(null) }

    val pendingAction by viewModel.pendingSpeciesAction.collectAsStateWithLifecycle()
    val voicePayload by viewModel.voiceAssistantPayload.collectAsStateWithLifecycle()
    LaunchedEffect(voicePayload?.requestId) {
        if (voicePayload?.requestMatched == true) {
            inlineEditState = inlineEditState.copy(batchDialogOpen = true)
        }
    }
    LaunchedEffect(pendingAction, ds.id) {
        val a = pendingAction ?: return@LaunchedEffect
        if (ds.species.none { it.id == a.speciesId }) return@LaunchedEffect
        when (a.kind) {
            com.plankton.one102.ui.PendingSpeciesActionKind.EditTaxonomy -> {
                inlineEditState = inlineEditState.copy(taxonomyEditId = a.speciesId)
            }
            com.plankton.one102.ui.PendingSpeciesActionKind.EditWetWeight -> {
                inlineEditState = inlineEditState.copy(wetWeightQueryId = a.speciesId)
            }
        }
        viewModel.clearPendingSpeciesAction()
    }

    LaunchedEffect(autoMatchBusy) {
        if (autoMatchBusy) panelState = panelState.copy(autoMatchExpanded = true)
    }

    val taxonomyTarget = ds.species.firstOrNull { it.id == inlineEditState.taxonomyEditId }
    val taxonomyQueryTarget = ds.species.firstOrNull { it.id == inlineEditState.taxonomyQueryId }
    val wetWeightTarget = ds.species.firstOrNull { it.id == inlineEditState.wetWeightQueryId }
    val countEditTarget = ds.species.firstOrNull { it.id == inlineEditState.countEditId }

    fun updateSpecies(speciesId: Id, updater: (Species) -> Species) {
        viewModel.updateCurrentDataset { cur ->
            cur.copy(species = cur.species.map { s -> if (s.id == speciesId) updater(s) else s })
        }
    }

    fun deleteSpecies(speciesId: Id) {
        viewModel.updateCurrentDataset { cur -> cur.copy(species = cur.species.filter { it.id != speciesId }) }
    }

    fun addBlankSpecies() {
        viewModel.updateCurrentDataset { cur ->
            cur.copy(species = cur.species + createBlankSpecies(cur.points.map { it.id }))
        }
        performHaptic(context, settings, HapticKind.Success)
        Toast.makeText(context, "已新增空白物种", Toast.LENGTH_SHORT).show()
    }

    fun addFromDatabase(entry: SpeciesDbItem) {
        if (ds.species.any { it.nameCn == entry.nameCn }) {
            Toast.makeText(context, "已存在：${entry.nameCn}", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            val wet = runCatching { wetWeightRepo.findByNameCn(entry.nameCn) }.getOrNull()?.wetWeightMg ?: entry.wetWeightMg
            val id = newId()
            viewModel.updateCurrentDataset { cur ->
                if (cur.species.any { it.nameCn == entry.nameCn }) return@updateCurrentDataset cur
                val counts = cur.points.associate { it.id to 0 }
                val taxonomy = entry.taxonomy ?: Taxonomy()
                val sp = Species(
                    id = id,
                    nameCn = entry.nameCn,
                    nameLatin = entry.nameLatin ?: "",
                    taxonomy = taxonomy,
                    avgWetWeightMg = wet,
                    countsByPointId = counts,
                )
                cur.copy(species = cur.species + sp)
            }
            performHaptic(context, settings, HapticKind.Success)
            activeSpeciesId = id
            Toast.makeText(context, "已添加物种：${entry.nameCn}", Toast.LENGTH_SHORT).show()
            addDialogState = addDialogState.copy(query = "")
        }
    }

    LaunchedEffect(ds.id, ds.species.size) {
        if (activeSpeciesId.isBlank() || ds.species.none { it.id == activeSpeciesId }) {
            activeSpeciesId = ds.species.firstOrNull()?.id ?: ""
        }
    }

    fun autofillFromLibrary(speciesId: Id) {
        val sp = ds.species.firstOrNull { it.id == speciesId } ?: return
        val nameCn = sp.nameCn.trim()
        if (nameCn.isEmpty()) return
        scope.launch {
            val aliasMap = try {
                aliasRepo.getAll().associate { it.alias.trim() to it.canonicalNameCn.trim() }
            } catch (_: Exception) {
                emptyMap()
            }
            val canonical = aliasMap[nameCn].orEmpty()
            val lookupNames = buildList {
                add(nameCn)
                if (canonical.isNotBlank() && canonical != nameCn) add(canonical)
            }

            var entry = wetWeightRepo.findByNameCn(nameCn)
            if (entry == null && canonical.isNotBlank() && canonical != nameCn) {
                entry = wetWeightRepo.findByNameCn(canonical)
            }
            if (entry == null) return@launch
            var customTaxonomy: Taxonomy? = null
            for (n in lookupNames) {
                val candidate = taxonomyOverrideRepo.findCustomByNameCn(n)?.taxonomy
                if (candidate != null && listOf(candidate.lvl1, candidate.lvl2, candidate.lvl3, candidate.lvl4, candidate.lvl5).any { it.isNotBlank() }) {
                    customTaxonomy = candidate
                    break
                }
            }
            val builtinTaxonomy = if (customTaxonomy != null) null else {
                var found: Taxonomy? = null
                for (n in lookupNames) {
                    found = taxonomyRepo.findByNameCn(n)
                    if (found != null) break
                }
                found
            }
            updateSpecies(speciesId) { s ->
                val t1 = s.taxonomy.copy(
                    lvl1 = s.taxonomy.lvl1.ifBlank { entry.taxonomy.group ?: "" },
                    lvl4 = s.taxonomy.lvl4.ifBlank { entry.taxonomy.sub ?: "" },
                )
                val t2 = if (builtinTaxonomy != null) {
                    t1.copy(
                        lvl1 = t1.lvl1.ifBlank { builtinTaxonomy.lvl1 },
                        lvl2 = t1.lvl2.ifBlank { builtinTaxonomy.lvl2 },
                        lvl3 = t1.lvl3.ifBlank { builtinTaxonomy.lvl3 },
                        lvl4 = t1.lvl4.ifBlank { builtinTaxonomy.lvl4 },
                        lvl5 = t1.lvl5.ifBlank { builtinTaxonomy.lvl5 },
                    )
                } else {
                    t1
                }
                s.copy(
                    nameLatin = s.nameLatin.ifBlank { entry.nameLatin ?: "" },
                    avgWetWeightMg = s.avgWetWeightMg ?: entry.wetWeightMg,
                    taxonomy = t2,
                )
            }
        }
    }

    fun autofillTaxonomyFromBuiltin(speciesId: Id) {
        val sp = ds.species.firstOrNull { it.id == speciesId } ?: return
        val nameCn = sp.nameCn.trim()
        if (nameCn.isEmpty()) return
        scope.launch {
            val aliasMap = try {
                aliasRepo.getAll().associate { it.alias.trim() to it.canonicalNameCn.trim() }
            } catch (_: Exception) {
                emptyMap()
            }
            val canonical = aliasMap[nameCn] ?: nameCn
            val candidates = buildSet {
                addAll(taxonomyRepo.getBuiltinEntryMap().keys)
                addAll(taxonomyOverrideRepo.getCustomEntries().mapNotNull { it.nameCn.trim().ifBlank { null } })
            }
            val best = if (!candidates.contains(canonical)) bestCanonicalName(canonical, candidates) else null
            val resolved = when {
                candidates.contains(canonical) -> canonical
                best != null && best.score >= 0.9 -> best.canonical
                else -> canonical
            }

            val custom = taxonomyOverrideRepo.findCustomByNameCn(resolved)?.taxonomy?.takeIf { t ->
                listOf(t.lvl1, t.lvl2, t.lvl3, t.lvl4, t.lvl5).any { it.isNotBlank() }
            }
            val t = custom ?: taxonomyRepo.findByNameCn(resolved)
            if (t == null) {
                val hint = best?.takeIf { it.score >= 0.6 }?.let { "（可能是 ${it.canonical}）" }.orEmpty()
                Toast.makeText(context, "内置分类库未找到该物种分类$hint", Toast.LENGTH_SHORT).show()
                return@launch
            }
            updateSpecies(speciesId) { s ->
                val cur = s.taxonomy
                s.copy(
                    taxonomy = cur.copy(
                        lvl1 = cur.lvl1.ifBlank { t.lvl1 },
                        lvl2 = cur.lvl2.ifBlank { t.lvl2 },
                        lvl3 = cur.lvl3.ifBlank { t.lvl3 },
                        lvl4 = cur.lvl4.ifBlank { t.lvl4 },
                        lvl5 = cur.lvl5.ifBlank { t.lvl5 },
                    ),
                )
            }
        }
    }

    fun clearLastAutoMatch() {
        val session = ds.lastAutoMatch ?: return
        val entriesById = session.entries.associateBy { it.speciesId }
        viewModel.updateCurrentDataset { cur ->
            val nextSpecies = cur.species.map { sp ->
                val e = entriesById[sp.id] ?: return@map sp
                var next = sp
                if (e.nameLatin.isNotBlank() && next.nameLatin == e.nameLatin) {
                    next = next.copy(nameLatin = "")
                }
                if (e.wetWeightMg != null && next.avgWetWeightMg == e.wetWeightMg) {
                    next = next.copy(avgWetWeightMg = null)
                }
                val et = e.taxonomy
                var t = next.taxonomy
                fun clearField(curVal: String, recVal: String): String {
                    return if (recVal.isNotBlank() && curVal == recVal) "" else curVal
                }
                t = t.copy(
                    lvl1 = clearField(t.lvl1, et.lvl1),
                    lvl2 = clearField(t.lvl2, et.lvl2),
                    lvl3 = clearField(t.lvl3, et.lvl3),
                    lvl4 = clearField(t.lvl4, et.lvl4),
                    lvl5 = clearField(t.lvl5, et.lvl5),
                )
                next.copy(taxonomy = t)
            }
            cur.copy(species = nextSpecies, lastAutoMatch = null)
        }
        Toast.makeText(context, "已清空本次匹配的数据", Toast.LENGTH_SHORT).show()
    }

    fun runAutoMatch(kind: AutoMatchKind) {
        if (autoMatchBusy) return

        autoMatchBusy = true
        autoMatchProgress = "准备中…"
        autoMatchError = null

        val speciesSnapshot = ds.species
        val settingsSnapshot = settings
        scope.launch {
            val updates = LinkedHashMap<Id, AutoMatchEntry>()
            val errors = mutableListOf<String>()
            val apiUsedCount = mutableMapOf<String, Int>()
            val writeToDb = settingsSnapshot.autoMatchWriteToDb

            val aliasMap = try {
                aliasRepo.getAll().associate { it.alias to it.canonicalNameCn }
            } catch (_: Exception) {
                emptyMap()
            }

            fun apiDisplayName(api: ApiConfig, fallback: String): String {
                return api.name.trim().ifBlank { fallback }
            }

            data class AiFallbackResult(
                val apiTag: String?,
                val apiName: String?,
                val info: AiSpeciesInfo?,
                val triedApiNames: List<String>,
                val hadAnyResponse: Boolean,
            )

            suspend fun callWithFallbackParsed(progressPrefix: String, nameCn: String, prompt: String): AiFallbackResult {
                suspend fun callOne(api: ApiConfig, apiTag: String): Pair<String?, AiSpeciesInfo?>? {
                    val cached = try {
                        aiCacheRepo.getSpeciesInfo(apiTag, nameCn)
                    } catch (_: Exception) {
                        null
                    }
                    if (cached != null && cached.prompt == prompt && cached.raw.isNotBlank()) {
                        val json = extractFinalSpeciesJson(cached.raw)
                        val info = json?.let { parseAiSpeciesInfo(it) }
                        return cached.raw to info
                    }

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
                    return raw to parsed
                }

                val tried = mutableListOf<String>()
                var hadAnyResponse = false
                var lastResponseApiTag: String? = null
                var lastResponseApiName: String? = null

                val can1 = apiConfigured(settingsSnapshot.api1)
                val can2 = apiConfigured(settingsSnapshot.api2)
                val candidates = buildList {
                    if (can1) add(settingsSnapshot.api1 to "api1")
                    if (can2) add(settingsSnapshot.api2 to "api2")
                }
                if (candidates.isEmpty()) return AiFallbackResult(null, null, null, emptyList(), false)

                for ((api, apiTag) in candidates) {
                    val apiName = when (apiTag) {
                        "api1" -> apiDisplayName(api, "API 1")
                        "api2" -> apiDisplayName(api, "API 2")
                        else -> apiDisplayName(api, apiTag)
                    }
                    tried += apiName
                    autoMatchProgress = "$progressPrefix（AI：$apiName）"
                    val (raw, parsed) = callOne(api, apiTag) ?: continue

                    if (!raw.isNullOrBlank()) {
                        hadAnyResponse = true
                        lastResponseApiTag = apiTag
                        lastResponseApiName = apiName
                    }
                    if (parsed != null) {
                        apiUsedCount[apiTag] = (apiUsedCount[apiTag] ?: 0) + 1
                        return AiFallbackResult(apiTag, apiName, parsed, tried, true)
                    }
                }
                return AiFallbackResult(lastResponseApiTag, lastResponseApiName, null, tried, hadAnyResponse)
            }

            fun mergeSession(existing: AutoMatchSession?, incoming: Collection<AutoMatchEntry>): AutoMatchSession {
                val map = LinkedHashMap<Id, AutoMatchEntry>()
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
                    sessionId = UUID.randomUUID().toString(),
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

            for ((i, sp0) in speciesSnapshot.withIndex()) {
                val nameCn = sp0.nameCn.trim()
                if (nameCn.isEmpty()) continue
                val lookupName = aliasMap[nameCn] ?: nameCn

                val kindLabel = if (kind == AutoMatchKind.Taxonomy) "分类" else "湿重"
                autoMatchProgress = "$kindLabel 补齐 ${i + 1}/${speciesSnapshot.size}：$nameCn"

                val record = AutoMatchEntry(speciesId = sp0.id, nameCn = nameCn)
                var recordLatin = ""
                var recordWet: Double? = null
                var recordTax = Taxonomy()

                val wantLatin = (kind == AutoMatchKind.Taxonomy) && sp0.nameLatin.isBlank()
                var wantWet = (kind == AutoMatchKind.WetWeight) && sp0.avgWetWeightMg == null
                var wantTax = (kind == AutoMatchKind.Taxonomy) && missingTaxonomyFields(sp0.taxonomy, recordTax)

                // Local: wet weight library (custom > builtin)
                if (wantWet || wantLatin || wantTax) {
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
                        if (wantLatin && !entry.nameLatin.isNullOrBlank()) {
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
                if (wantTax) {
                    val tCustom = try {
                        taxonomyOverrideRepo.findCustomByNameCn(lookupName)?.taxonomy
                    } catch (_: Exception) {
                        null
                    }
                    val tBuiltin = if (tCustom == null) {
                        try {
                            taxonomyRepo.findByNameCn(lookupName)
                        } catch (_: Exception) {
                            null
                        }
                    } else {
                        null
                    }
                    val t = tCustom ?: tBuiltin
                    if (t != null && listOf(t.lvl1, t.lvl2, t.lvl3, t.lvl4, t.lvl5).any { it.isNotBlank() }) {
                        recordTax = recordTax.copy(
                            lvl1 = recordTax.lvl1.ifBlank { t.lvl1 },
                            lvl2 = recordTax.lvl2.ifBlank { t.lvl2 },
                            lvl3 = recordTax.lvl3.ifBlank { t.lvl3 },
                            lvl4 = recordTax.lvl4.ifBlank { t.lvl4 },
                            lvl5 = recordTax.lvl5.ifBlank { t.lvl5 },
                        )
                    }
                }

                // API: fill remaining missing parts only
                val wantLatin2 = wantLatin && recordLatin.isBlank()
                val wantTax2 = wantTax && missingTaxonomyFields(sp0.taxonomy, recordTax)
                val wantWet2 = wantWet && recordWet == null
                if (wantWet2 || wantTax2 || wantLatin2) {
                    val prompt = when (kind) {
                        AutoMatchKind.Taxonomy -> buildSpeciesTaxonomyAutofillPrompt(nameCn, sp0.nameLatin.ifBlank { null })
                        AutoMatchKind.WetWeight -> buildSpeciesWetWeightAutofillPrompt(nameCn, sp0.nameLatin.ifBlank { null })
                    }
                    val localHasAny = recordLatin.isNotBlank() ||
                        recordWet != null ||
                        listOf(recordTax.lvl1, recordTax.lvl2, recordTax.lvl3, recordTax.lvl4, recordTax.lvl5).any { it.isNotBlank() }

                    var aiContributedTax = false
                    var aiContributedWet = false
                    val r = try {
                        callWithFallbackParsed("$kindLabel 补齐 ${i + 1}/${speciesSnapshot.size}：$nameCn", nameCn, prompt)
                    } catch (_: Exception) {
                        AiFallbackResult(null, null, null, emptyList(), false)
                    }
                    val ai = r.info

                    if (ai == null) {
                        if (!localHasAny) {
                            val triedText = r.triedApiNames.distinct().joinToString(" / ").ifBlank { "未配置 API" }
                            errors += if (r.hadAnyResponse) {
                                "AI 未解析到结构化结果：$nameCn（已尝试：$triedText）"
                            } else {
                                "AI 调用失败或无响应：$nameCn（已尝试：$triedText）"
                            }
                        }
                    } else {
                        if (wantLatin2 && !ai.nameLatin.isNullOrBlank()) {
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
                            val t = Taxonomy(
                                lvl1 = normalizeLvl1Name(ai.lvl1.orEmpty()),
                                lvl2 = ai.lvl2?.trim().orEmpty(),
                                lvl3 = ai.lvl3?.trim().orEmpty(),
                                lvl4 = ai.lvl4?.trim().orEmpty(),
                                lvl5 = ai.lvl5?.trim().orEmpty(),
                            )
                            if (listOf(t.lvl1, t.lvl2, t.lvl3, t.lvl4, t.lvl5).any { it.isNotBlank() }) {
                                recordTax = recordTax.copy(
                                    lvl1 = recordTax.lvl1.ifBlank { t.lvl1 },
                                    lvl2 = recordTax.lvl2.ifBlank { t.lvl2 },
                                    lvl3 = recordTax.lvl3.ifBlank { t.lvl3 },
                                    lvl4 = recordTax.lvl4.ifBlank { t.lvl4 },
                                    lvl5 = recordTax.lvl5.ifBlank { t.lvl5 },
                                )
                                aiContributedTax = true
                            }
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

                val hasAny = recordLatin.isNotBlank() || recordWet != null || listOf(recordTax.lvl1, recordTax.lvl2, recordTax.lvl3, recordTax.lvl4, recordTax.lvl5).any { it.isNotBlank() }
                if (hasAny) {
                    updates[sp0.id] = record.copy(
                        nameLatin = recordLatin,
                        wetWeightMg = recordWet,
                        taxonomy = recordTax,
                    )
                }

                delay(120)
            }

            if (updates.isEmpty()) {
                autoMatchError = "没有需要匹配/可写入的内容（可能都已填写）。"
                autoMatchBusy = false
                autoMatchProgress = ""
                return@launch
            }

            viewModel.updateCurrentDataset { cur ->
                val byId = updates
                val nextSpecies = cur.species.map { sp ->
                    val u = byId[sp.id] ?: return@map sp
                    var next = sp
                    if (next.nameLatin.isBlank() && u.nameLatin.isNotBlank()) next = next.copy(nameLatin = u.nameLatin)
                    if (kind == AutoMatchKind.WetWeight && next.avgWetWeightMg == null && u.wetWeightMg != null) {
                        next = next.copy(avgWetWeightMg = u.wetWeightMg)
                    }
                    val ut = u.taxonomy
                    if (kind == AutoMatchKind.Taxonomy && listOf(ut.lvl1, ut.lvl2, ut.lvl3, ut.lvl4, ut.lvl5).any { it.isNotBlank() }) {
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

            val api1Name = apiDisplayName(settingsSnapshot.api1, "API 1")
            val api2Name = apiDisplayName(settingsSnapshot.api2, "API 2")
            val api1Used = apiUsedCount["api1"] ?: 0
            val api2Used = apiUsedCount["api2"] ?: 0
            val apiMsg = if (api1Used + api2Used > 0) "；AI：$api1Name×$api1Used，$api2Name×$api2Used" else ""
            val msg = "匹配完成：写入 ${updates.size} 个物种" +
                if (errors.isNotEmpty()) "（${errors.size} 个未成功）" else "" +
                apiMsg
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            autoMatchError = if (errors.isNotEmpty()) errors.take(6).joinToString("\n") else null
            autoMatchBusy = false
            autoMatchProgress = ""
        }
    }

    fun runAutoMatchTaxonomy() = runAutoMatch(AutoMatchKind.Taxonomy)

    fun runAutoMatchWetWeight() = runAutoMatch(AutoMatchKind.WetWeight)

    val listState = rememberLazyListState()
    val canScrollTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val groupedSpecies by remember(ds.species) {
        derivedStateOf {
            ds.species
                .groupBy { normalizeLvl1Name(it.taxonomy.lvl1).ifBlank { "未分类" } }
                .toList()
                .sortedWith(
                    compareBy<Pair<String, List<Species>>>(
                        {
                            val index = LVL1_ORDER.indexOf(it.first)
                            if (index >= 0) index else Int.MAX_VALUE
                        },
                        { it.first },
                    ),
                )
                .map { (label, list) ->
                    label to list.sortedBy { s -> s.nameCn.ifBlank { s.nameLatin } }
                }
        }
    }

    CompositionLocalProvider(
        LocalGlassPrefs provides GlassPrefs(
            enabled = settings.glassEffectEnabled,
            blur = settings.blurEnabled && !listState.isScrollInProgress,
            opacity = settings.glassOpacity,
        ),
    ) {
        GlassBackground {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 108.dp),
            ) {
                item(key = "header") {
                    SpeciesHeaderRow(onOpenFocus = onOpenFocus)
                }

                item(key = "library-row") {
                    val libraryOptions = remember(libraries) { libraries.map { it.id to it.name } }
                    SpeciesWetWeightLibraryRow(
                        activeLibraryName = activeLibraryName,
                        menuOpen = libraryUiState.menuOpen,
                        libraries = libraryOptions,
                        onMenuOpenChange = { open -> libraryUiState = libraryUiState.copy(menuOpen = open) },
                        onSelectLibrary = { libraryId ->
                            viewModel.saveSettings(settings.copy(activeWetWeightLibraryId = libraryId))
                        },
                        onCreateLibrary = {
                            libraryUiState = libraryUiState.copy(createOpen = true, nameDraft = "", error = null)
                        },
                    )
                }

                item(key = "point-selector") {
                    val activeLabel = ds.points.firstOrNull { it.id == activePointId }?.label.orEmpty()
                    val pointOptions = remember(ds.points) {
                        ds.points.map { it.id to it.label.ifBlank { "未命名" } }
                    }
                    val pointStats by remember(ds.species, activePointId) {
                        derivedStateOf {
                            val counts = ds.species.map { it.countsByPointId[activePointId] ?: 0 }
                            val speciesCount = counts.count { it > 0 }
                            val total = counts.sum()
                            speciesCount to total
                        }
                    }

                    SpeciesPointSelectorCard(
                        activeLabel = activeLabel,
                        pointExpanded = panelState.pointExpanded,
                        speciesCount = pointStats.first,
                        totalCount = pointStats.second,
                        points = pointOptions,
                        activePointId = activePointId,
                        onExpandedChange = { expanded -> panelState = panelState.copy(pointExpanded = expanded) },
                        onSelectPoint = { pointId -> activePointId = pointId },
                    )
                }

                if (!settings.aiUiHidden) {
                    item(key = "image-import") {
                        val api1Label = settings.api1.name.trim().ifBlank { "API1" }
                        val api2Label = settings.api2.name.trim().ifBlank { "API2" }
                        val apiImgLabel = settings.imageApi.name.trim().ifBlank { "图片 API" }
                        val api1Status = visionStatus(settings.api1, imageImportState.api1Unsupported)
                        val api2Status = visionStatus(settings.api2, imageImportState.api2Unsupported)
                        val apiImgStatus = visionStatus(settings.imageApi, imageImportState.apiImageUnsupported)
                        val api1Enabled = api1Status == null
                        val api2Enabled = api2Status == null
                        val apiImgEnabled = apiImgStatus == null

                        LaunchedEffect(api1Status, api2Status, apiImgStatus) {
                            if (!api1Enabled && imageImportState.useApi1) {
                                viewModel.updateImageImportState { state -> state.copy(useApi1 = false) }
                            }
                            if (!api2Enabled && imageImportState.useApi2) {
                                viewModel.updateImageImportState { state -> state.copy(useApi2 = false) }
                            }
                            if (!apiImgEnabled && imageImportState.useImageApi) {
                                viewModel.updateImageImportState { state -> state.copy(useImageApi = false) }
                            }
                        }

                        val preview = selectedImageImportResult()

                        SpeciesImageImportCard(
                            expanded = panelState.imageImportExpanded,
                            imageCount = imageImportState.images.size,
                            busy = imageImportState.busy,
                            message = imageImportState.message,
                            error = imageImportState.error,
                            useApi1 = imageImportState.useApi1,
                            useApi2 = imageImportState.useApi2,
                            useImageApi = imageImportState.useImageApi,
                            api1Label = api1Label,
                            api2Label = api2Label,
                            apiImgLabel = apiImgLabel,
                            api1Enabled = api1Enabled,
                            api2Enabled = api2Enabled,
                            apiImgEnabled = apiImgEnabled,
                            api1Status = api1Status,
                            api2Status = api2Status,
                            apiImgStatus = apiImgStatus,
                            mode = imageImportState.mode,
                            overwriteExisting = imageImportState.overwriteExisting,
                            hasApi1Result = imageImportState.api1 != null,
                            hasApi2Result = imageImportState.api2 != null,
                            hasApiImageResult = imageImportState.apiImage != null,
                            selectedSource = imageImportState.source,
                            preview = preview,
                            onExpandedChange = { expanded -> panelState = panelState.copy(imageImportExpanded = expanded) },
                            onToggleUseApi1 = { checked ->
                                viewModel.updateImageImportState { state -> state.copy(useApi1 = checked) }
                            },
                            onToggleUseApi2 = { checked ->
                                viewModel.updateImageImportState { state -> state.copy(useApi2 = checked) }
                            },
                            onToggleUseImageApi = { checked ->
                                viewModel.updateImageImportState { state -> state.copy(useImageApi = checked) }
                            },
                            onPickImages = { pickImagesLauncher.launch("image/*") },
                            onTakePhoto = {
                                val uri = createTempImageUri()
                                imageImportCameraUri = uri
                                takePhotoLauncher.launch(uri)
                            },
                            onClearImages = {
                                viewModel.updateImageImportState { state -> state.copy(images = emptyList()) }
                            },
                            onSelectMode = { mode ->
                                viewModel.updateImageImportState { state -> state.copy(mode = mode) }
                            },
                            onOverwriteExistingChange = { checked ->
                                viewModel.updateImageImportState { state -> state.copy(overwriteExisting = checked) }
                            },
                            onRunImageImport = ::runImageImport,
                            onSelectSource = { source ->
                                viewModel.updateImageImportState { state -> state.copy(source = source) }
                            },
                            onApplyPreview = { result ->
                                applyImageImport(result, imageImportState.mode, imageImportState.overwriteExisting)
                            },
                        )
                    }
                }

                item(key = "auto-match") {
                    SpeciesAutoMatchCard(
                        expanded = panelState.autoMatchExpanded,
                        hasLastAutoMatch = ds.lastAutoMatch != null,
                        autoMatchWriteToDb = settings.autoMatchWriteToDb,
                        speciesEditWriteToDb = settings.speciesEditWriteToDb,
                        autoMatchBusy = autoMatchBusy,
                        autoMatchProgress = autoMatchProgress,
                        autoMatchError = autoMatchError,
                        onExpandedChange = { expanded -> panelState = panelState.copy(autoMatchExpanded = expanded) },
                        onAutoMatchWriteToDbChange = { enabled ->
                            viewModel.saveSettings(settings.copy(autoMatchWriteToDb = enabled))
                        },
                        onSpeciesEditWriteToDbChange = { enabled ->
                            viewModel.saveSettings(settings.copy(speciesEditWriteToDb = enabled))
                        },
                        onRunAutoMatchTaxonomy = ::runAutoMatchTaxonomy,
                        onRunAutoMatchWetWeight = ::runAutoMatchWetWeight,
                        onClearLastAutoMatch = ::clearLastAutoMatch,
                    )
                }

                item(key = "bulk-actions") {
                    SpeciesBulkActionsCard(
                        expanded = panelState.bulkExpanded,
                        onExpandedChange = { expanded -> panelState = panelState.copy(bulkExpanded = expanded) },
                        onClearPoint = { confirmState = confirmState.copy(clearPoint = true) },
                        onClearAll = { confirmState = confirmState.copy(clearAll = true) },
                        onMerge = { confirmState = confirmState.copy(merge = true) },
                        onCreateTemplate = { viewModel.createTemplateDataset(resetCounts = true) },
                        onBatchInput = { inlineEditState = inlineEditState.copy(batchDialogOpen = true) },
                    )
                }

                item(key = "add-buttons") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { addDialogState = addDialogState.copy(visible = true) },
                            modifier = Modifier.weight(1f),
                        ) { Text("添加物种") }
                        OutlinedButton(onClick = ::addBlankSpecies, modifier = Modifier.weight(1f)) { Text("新增空白") }
                    }
                }

                if (ds.species.isEmpty()) {
                    item(key = "empty-species") {
                        Text("还没有物种。", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                    }
                } else {
                    groupedSpeciesListItems(
                        groupedSpecies = groupedSpecies,
                        activePointId = activePointId,
                        activeSpeciesId = activeSpeciesId,
                        aiUiHidden = settings.aiUiHidden,
                        onSelectSpecies = { id -> activeSpeciesId = id },
                        onEditFull = onEditSpecies,
                        onEditCount = { s ->
                            activeSpeciesId = s.id
                            inlineEditState = inlineEditState.copy(
                                countEditId = s.id,
                                countEditPointId = activePointId,
                                countEditText = (s.countsByPointId[activePointId] ?: 0).toString(),
                            )
                        },
                        onEditTaxonomy = { id -> inlineEditState = inlineEditState.copy(taxonomyEditId = id) },
                        onAutofillWetWeight = ::autofillFromLibrary,
                        onAutofillTaxonomy = ::autofillTaxonomyFromBuiltin,
                        onQueryTaxonomy = { id -> inlineEditState = inlineEditState.copy(taxonomyQueryId = id) },
                        onQueryWetWeight = { id -> inlineEditState = inlineEditState.copy(wetWeightQueryId = id) },
                        onSaveWetWeight = { s, mg ->
                            val nameCn = s.nameCn.trim()
                            if (nameCn.isEmpty()) return@groupedSpeciesListItems
                            scope.launch {
                                wetWeightRepo.upsertCustom(
                                    WetWeightEntry(
                                        nameCn = nameCn,
                                        nameLatin = s.nameLatin.trim().ifBlank { null },
                                        wetWeightMg = mg,
                                        taxonomy = WetWeightTaxonomy(
                                            group = s.taxonomy.lvl1.trim().ifBlank { null },
                                            sub = s.taxonomy.lvl4.trim().ifBlank { null },
                                        ),
                                    ),
                                )
                            }
                        },
                        onDeleteSpecies = ::deleteSpecies,
                    )
                }

                item(key = "top-button") {
                    OutlinedButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("返回顶部") }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd),
            ) {
                SpeciesQuickActionsDock(
                    canScrollTop = canScrollTop,
                    onScrollTop = { scope.launch { listState.animateScrollToItem(0) } },
                    onAddSpecies = { addDialogState = addDialogState.copy(visible = true) },
                )
            }
        }
    }
    }

    SpeciesAddFromDatabaseDialog(
        visible = addDialogState.visible,
        query = addDialogState.query,
        dbItems = dbItems,
        onQueryChange = { text -> addDialogState = addDialogState.copy(query = text) },
        onDismissRequest = {
            addDialogState = addDialogState.copy(visible = false, query = "")
        },
        onClose = { addDialogState = addDialogState.copy(visible = false) },
        onSelectEntry = { entry ->
            addFromDatabase(entry)
            addDialogState = addDialogState.copy(visible = false)
        },
    )

    SpeciesTaxonomyWetBatchDialogsHost(
        settings = settings,
        taxonomyTarget = taxonomyTarget,
        taxonomyQueryTarget = taxonomyQueryTarget,
        wetWeightTarget = wetWeightTarget,
        batchDialogOpen = inlineEditState.batchDialogOpen,
        speciesEditWriteToDb = settings.speciesEditWriteToDb,
        viewModel = viewModel,
        activePointId = activePointId,
        incomingPayload = voicePayload,
        onCloseTaxonomyEdit = {
            inlineEditState = inlineEditState.copy(taxonomyEditId = null)
        },
        onApplyTaxonomyEdit = { nameLatin, taxonomy, writeToDb ->
            val target = taxonomyTarget ?: return@SpeciesTaxonomyWetBatchDialogsHost
            updateSpecies(target.id) { it.copy(nameLatin = nameLatin, taxonomy = taxonomy) }
            if (writeToDb && speciesHasTaxonomy(taxonomy)) {
                val nameCn = target.nameCn.trim()
                if (nameCn.isNotBlank()) {
                    scope.launch {
                        taxonomyOverrideRepo.upsertCustom(
                            TaxonomyRecord(
                                nameCn = nameCn,
                                nameLatin = nameLatin.trim().ifBlank { null },
                                taxonomy = taxonomy,
                            ),
                        )
                    }
                }
            }
            inlineEditState = inlineEditState.copy(taxonomyEditId = null)
        },
        onCloseTaxonomyQuery = {
            inlineEditState = inlineEditState.copy(taxonomyQueryId = null)
        },
        onApplyTaxonomyQuery = { taxonomy ->
            val target = taxonomyQueryTarget ?: return@SpeciesTaxonomyWetBatchDialogsHost
            updateSpecies(target.id) { it.copy(taxonomy = taxonomy) }
            if (settings.speciesEditWriteToDb && speciesHasTaxonomy(taxonomy)) {
                val nameCn = target.nameCn.trim()
                if (nameCn.isNotBlank()) {
                    scope.launch {
                        taxonomyOverrideRepo.upsertCustom(
                            TaxonomyRecord(
                                nameCn = nameCn,
                                nameLatin = target.nameLatin.trim().ifBlank { null },
                                taxonomy = taxonomy,
                            ),
                        )
                    }
                }
            }
            inlineEditState = inlineEditState.copy(taxonomyQueryId = null)
        },
        onCloseWetWeightQuery = {
            inlineEditState = inlineEditState.copy(wetWeightQueryId = null)
        },
        onApplyWetWeightQuery = { mg ->
            val target = wetWeightTarget ?: return@SpeciesTaxonomyWetBatchDialogsHost
            updateSpecies(target.id) { it.copy(avgWetWeightMg = mg) }
            if (settings.speciesEditWriteToDb && mg.isFinite() && mg > 0) {
                val nameCn = target.nameCn.trim()
                if (nameCn.isNotBlank()) {
                    scope.launch {
                        wetWeightRepo.upsertCustom(
                            WetWeightEntry(
                                nameCn = nameCn,
                                nameLatin = target.nameLatin.trim().ifBlank { null },
                                wetWeightMg = mg,
                                taxonomy = WetWeightTaxonomy(
                                    group = normalizeLvl1Name(target.taxonomy.lvl1).takeIf { it.isNotBlank() },
                                    sub = target.taxonomy.lvl4.trim().takeIf { it.isNotBlank() },
                                ),
                            ),
                        )
                    }
                }
            }
            inlineEditState = inlineEditState.copy(wetWeightQueryId = null)
        },
        onSaveWetWeightToLibrary = { mg ->
            val target = wetWeightTarget ?: return@SpeciesTaxonomyWetBatchDialogsHost
            val nameCn = target.nameCn.trim()
            if (nameCn.isEmpty()) return@SpeciesTaxonomyWetBatchDialogsHost
            scope.launch {
                wetWeightRepo.upsertCustom(
                    WetWeightEntry(
                        nameCn = nameCn,
                        nameLatin = target.nameLatin.trim().ifBlank { null },
                        wetWeightMg = mg,
                        taxonomy = WetWeightTaxonomy(
                            group = target.taxonomy.lvl1.trim().ifBlank { null },
                            sub = target.taxonomy.lvl4.trim().ifBlank { null },
                        ),
                    ),
                )
            }
        },
        onCloseBatchDialog = {
            inlineEditState = inlineEditState.copy(batchDialogOpen = false)
            if (voicePayload != null) {
                viewModel.clearVoiceAssistantPayload()
            }
        },
    )

    if (countEditTarget != null && inlineEditState.countEditPointId != null) {
        SpeciesCountEditDialog(
            species = countEditTarget,
            countText = inlineEditState.countEditText,
            onCountTextChange = { text -> inlineEditState = inlineEditState.copy(countEditText = text) },
            onConfirm = {
                val spId = countEditTarget.id
                val pid = inlineEditState.countEditPointId ?: return@SpeciesCountEditDialog
                val n = speciesClampNonNegativeInt(inlineEditState.countEditText.trim().toIntOrNull() ?: 0)
                updateSpecies(spId) { sp -> sp.copy(countsByPointId = sp.countsByPointId + (pid to n)) }
                inlineEditState = inlineEditState.copy(countEditId = null, countEditPointId = null)
            },
            onDismiss = {
                inlineEditState = inlineEditState.copy(countEditId = null, countEditPointId = null)
            },
        )
    }

    SpeciesSimpleConfirmDialog(
        visible = confirmState.clearPoint,
        title = "清空当前点计数",
        message = "将把当前采样点「${ds.points.firstOrNull { it.id == activePointId }?.label ?: ""}」的所有物种计数置为 0。",
        confirmLabel = "清空",
        onConfirm = {
            confirmState = confirmState.copy(clearPoint = false)
            viewModel.updateCurrentDataset { cur ->
                val pid = activePointId
                cur.copy(
                    species = cur.species.map { sp ->
                        val next = sp.countsByPointId.toMutableMap()
                        next[pid] = 0
                        sp.copy(countsByPointId = next)
                    },
                )
            }
            Toast.makeText(context, "已清空当前点计数", Toast.LENGTH_SHORT).show()
        },
        onDismiss = { confirmState = confirmState.copy(clearPoint = false) },
    )

    SpeciesSimpleConfirmDialog(
        visible = confirmState.clearAll,
        title = "清空全部计数",
        message = "将把本数据集中所有采样点的所有物种计数置为 0。",
        confirmLabel = "清空",
        onConfirm = {
            confirmState = confirmState.copy(clearAll = false)
            viewModel.updateCurrentDataset { cur ->
                val pids = cur.points.map { it.id }
                cur.copy(
                    species = cur.species.map { sp ->
                        sp.copy(countsByPointId = pids.associateWith { 0 })
                    },
                )
            }
            Toast.makeText(context, "已清空全部计数", Toast.LENGTH_SHORT).show()
        },
        onDismiss = { confirmState = confirmState.copy(clearAll = false) },
    )

    SpeciesSimpleConfirmDialog(
        visible = confirmState.merge,
        title = "合并同名物种",
        message = "将把中文名相同的物种合并为一条（计数求和；分类/拉丁名/湿重尽量保留非空值）。",
        confirmLabel = "合并",
        onConfirm = {
            confirmState = confirmState.copy(merge = false)
            val res = mergeDuplicateSpeciesByName(ds, MergeCountsMode.Sum)
            if (res.mergedCount <= 0) {
                Toast.makeText(context, "未发现需要合并的同名物种", Toast.LENGTH_SHORT).show()
                return@SpeciesSimpleConfirmDialog
            }
            viewModel.updateCurrentDataset { res.dataset }
            Toast.makeText(context, "已合并同名物种：${res.mergedCount} 条", Toast.LENGTH_LONG).show()
        },
        onDismiss = { confirmState = confirmState.copy(merge = false) },
    )

    SpeciesCreateLibraryDialog(
        visible = libraryUiState.createOpen,
        libraryNameDraft = libraryUiState.nameDraft,
        error = libraryUiState.error,
        onNameChange = { text -> libraryUiState = libraryUiState.copy(nameDraft = text) },
        onCreateAndSwitch = {
            val name = libraryUiState.nameDraft.trim()
            if (name.isBlank()) {
                libraryUiState = libraryUiState.copy(error = "库名称不能为空。")
                return@SpeciesCreateLibraryDialog
            }
            scope.launch {
                val created = runCatching { wetWeightRepo.createLibrary(name) }
                created.onSuccess { lib ->
                    viewModel.saveSettings(settings.copy(activeWetWeightLibraryId = lib.id))
                    libraryUiState = libraryUiState.copy(createOpen = false)
                }.onFailure {
                    libraryUiState = libraryUiState.copy(error = it.message ?: "新建失败")
                }
            }
        },
        onDismiss = { libraryUiState = libraryUiState.copy(createOpen = false) },
    )
}
