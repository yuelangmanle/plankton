package com.plankton.one102.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.plankton.one102.PlanktonApplication
import com.plankton.one102.data.repo.BackupExportOptions
import com.plankton.one102.data.repo.BackupImportOptions
import com.plankton.one102.data.repo.BackupSummary
import com.plankton.one102.data.api.CalcAuditOutput
import com.plankton.one102.data.api.ChatCompletionClient
import com.plankton.one102.data.api.applyCalcAuditOutputs
import com.plankton.one102.data.api.buildCalcAuditBatchPrompt
import com.plankton.one102.data.api.buildCalcAuditPointInput
import com.plankton.one102.data.api.buildCalcAuditPointPrompt
import com.plankton.one102.data.api.buildCalcAuditRepairPrompt
import com.plankton.one102.data.api.callAiWithContinuation
import com.plankton.one102.data.api.parseCalcAuditOutputFromText
import com.plankton.one102.data.api.parseCalcAuditOutputsFromText
import com.plankton.one102.domain.ApiConfig
import com.plankton.one102.domain.Dataset
import com.plankton.one102.domain.DatasetCalc
import com.plankton.one102.domain.DatasetSummary
import com.plankton.one102.domain.DEFAULT_WET_WEIGHT_LIBRARY_ID
import com.plankton.one102.domain.Id
import com.plankton.one102.domain.IssueLevel
import com.plankton.one102.domain.calcDataset
import com.plankton.one102.domain.diffCalc
import com.plankton.one102.domain.Settings
import com.plankton.one102.domain.newId
import com.plankton.one102.domain.nowIso
import com.plankton.one102.domain.touchDataset
import com.plankton.one102.voiceassistant.VoiceAssistantResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class PendingSpeciesActionKind { EditTaxonomy, EditWetWeight }

data class PendingSpeciesAction(
    val speciesId: Id,
    val kind: PendingSpeciesActionKind,
)

data class ApiHealthState(
    val ok: Boolean,
    val message: String,
    val baseUrl: String,
    val model: String,
    val latencyMs: Long? = null,
)

data class ApiHealthEntry(
    val at: String,
    val apiName: String,
    val ok: Boolean,
    val message: String,
    val latencyMs: Long?,
)

data class TaskFeedbackState(
    val running: Boolean = false,
    val title: String = "",
    val detail: String = "",
    val level: IssueLevel = IssueLevel.Info,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val application = app as PlanktonApplication
    private val datasetRepo = application.datasetRepository
    private val backupRepo = application.backupRepository
    private val wetWeightRepo = application.wetWeightRepository
    private val prefs = application.preferences
    private val updateMutex = Mutex()
    private var pendingSaveJob: Job? = null
    private var pendingDirtyId: String? = null
    private val assistantClient = ChatCompletionClient()
    private var assistantAiJob: Job? = null
    private var assistantTraceJob: Job? = null
    private var previewCalcJob: Job? = null
    private var previewReportJob: Job? = null
    private val voiceAssistantHub = application.voiceAssistantHub
    private val undoStack = ArrayDeque<Dataset>()
    private val redoStack = ArrayDeque<Dataset>()
    private val maxUndoDepth = 40

    private val _activePointId = MutableStateFlow<Id?>(null)
    val activePointId: StateFlow<Id?> = _activePointId.asStateFlow()

    private val _pendingSpeciesAction = MutableStateFlow<PendingSpeciesAction?>(null)
    val pendingSpeciesAction: StateFlow<PendingSpeciesAction?> = _pendingSpeciesAction.asStateFlow()

    private val _readOnlyEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val readOnlyEvents = _readOnlyEvents.asSharedFlow()

    private val _api1Health = MutableStateFlow<ApiHealthState?>(null)
    val api1Health: StateFlow<ApiHealthState?> = _api1Health.asStateFlow()

    private val _api2Health = MutableStateFlow<ApiHealthState?>(null)
    val api2Health: StateFlow<ApiHealthState?> = _api2Health.asStateFlow()
    private val _apiHealthRecent = MutableStateFlow<List<ApiHealthEntry>>(emptyList())
    val apiHealthRecent: StateFlow<List<ApiHealthEntry>> = _apiHealthRecent.asStateFlow()
    private val _taskFeedback = MutableStateFlow(TaskFeedbackState())
    val taskFeedback: StateFlow<TaskFeedbackState> = _taskFeedback.asStateFlow()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _draftDataset = MutableStateFlow<Dataset?>(null)
    val currentDataset: StateFlow<Dataset?> = _draftDataset.asStateFlow()

    val settings: StateFlow<Settings> = prefs.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = com.plankton.one102.domain.DEFAULT_SETTINGS,
    )

    private val _datasetPageSize = MutableStateFlow(30)

    val datasetSummaries: StateFlow<List<DatasetSummary>> = _datasetPageSize
        .flatMapLatest { datasetRepo.observeSummaries(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val datasetTotalCount: StateFlow<Int> = datasetRepo.observeCount().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0,
    )

    val currentDatasetId: StateFlow<String?> = prefs.currentDatasetId.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    val lastExportUri: StateFlow<String?> = prefs.lastExportUri.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    val lastExportAt: StateFlow<String?> = prefs.lastExportAt.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    private val _assistantState = MutableStateFlow(AssistantUiState())
    val assistantState: StateFlow<AssistantUiState> = _assistantState.asStateFlow()

    private val _previewState = MutableStateFlow(PreviewUiState())
    val previewState: StateFlow<PreviewUiState> = _previewState.asStateFlow()

    private val _previewCommand = MutableStateFlow<PreviewCommand?>(null)
    val previewCommand: StateFlow<PreviewCommand?> = _previewCommand.asStateFlow()

    private val _voiceAssistantPayload = MutableStateFlow<VoiceAssistantResult?>(null)
    val voiceAssistantPayload: StateFlow<VoiceAssistantResult?> = _voiceAssistantPayload.asStateFlow()

    private val _imageImportState = MutableStateFlow(ImageImportUiState())
    val imageImportState: StateFlow<ImageImportUiState> = _imageImportState.asStateFlow()

    init {
        viewModelScope.launch {
            ensureCurrentDataset()
        }
        viewModelScope.launch {
            settings.collect { s ->
                runCatching { wetWeightRepo.ensureDefaultLibrary() }
                val desired = s.activeWetWeightLibraryId.trim().ifBlank { DEFAULT_WET_WEIGHT_LIBRARY_ID }
                val libraries = runCatching { wetWeightRepo.getLibraries() }.getOrElse { emptyList() }
                val resolved = libraries.firstOrNull { it.id == desired }?.id ?: DEFAULT_WET_WEIGHT_LIBRARY_ID
                wetWeightRepo.setActiveLibraryId(resolved)
                if (resolved != s.activeWetWeightLibraryId) {
                    prefs.saveSettings(s.copy(activeWetWeightLibraryId = resolved))
                }
            }
        }
        viewModelScope.launch {
            currentDatasetId
                .flatMapLatest { id ->
                    if (id.isNullOrBlank()) flowOf(null) else datasetRepo.observeById(id)
                }
                .collect { ds ->
                    if (ds == null) {
                        ensureCurrentDataset()
                    } else {
                        val prevId = _draftDataset.value?.id
                        if (prevId != ds.id) {
                            clearUndoRedo()
                        }
                        _draftDataset.value = ds
                        pendingDirtyId = null
                    }
                }
        }
        viewModelScope.launch {
            voiceAssistantHub.incoming.collect { payload ->
                _voiceAssistantPayload.value = payload
            }
        }
    }

    private suspend fun ensureCurrentDataset() {
        val count = datasetRepo.countAll()
        if (count <= 0) {
            val ds = datasetRepo.createNew(settings.value)
            prefs.setCurrentDatasetId(ds.id)
            return
        }

        val curId = currentDatasetId.value
        val exists = curId != null && datasetRepo.exists(curId)
        if (!exists) {
            val latest = datasetRepo.getLatestId()
            prefs.setCurrentDatasetId(latest)
        }
    }

    fun resetAssistantState(datasetId: String?) {
        _assistantState.value = AssistantUiState(datasetId = datasetId)
    }

    fun updateAssistantQuestion(text: String) {
        _assistantState.update { state ->
            state.copy(ai = state.ai.copy(question = text))
        }
    }

    fun setAssistantIncludeTrace(value: Boolean) {
        _assistantState.update { state ->
            state.copy(trace = state.trace.copy(includeTrace = value))
        }
    }

    fun clearAssistantReplies() {
        _assistantState.update { state ->
            state.copy(
                ai = state.ai.copy(
                    answer1 = "",
                    answer2 = "",
                    answer1Label = "",
                    answer2Label = "",
                    error = null,
                    busy = false,
                    taskLabel = "",
                    lastUpdatedAt = null,
                ),
            )
        }
    }

    fun clearAssistantTrace(pointId: String? = null) {
        _assistantState.update { state ->
            val nextMap = if (pointId == null) emptyMap() else state.trace.byPointId - pointId
            state.copy(trace = state.trace.copy(byPointId = nextMap, error = null, busyPointId = null))
        }
    }

    fun updateAssistantFixState(update: (AssistantFixState) -> AssistantFixState) {
        _assistantState.update { state -> state.copy(fix = update(state.fix)) }
    }

    fun updateAssistantTraceState(update: (AssistantTraceState) -> AssistantTraceState) {
        _assistantState.update { state -> state.copy(trace = update(state.trace)) }
    }

    fun updateAssistantApiCheckBusy(busy: Boolean) {
        _assistantState.update { state -> state.copy(apiCheckBusy = busy) }
    }

    fun registerVoiceAssistantRequest(requestId: String) {
        voiceAssistantHub.registerRequest(requestId)
    }

    fun cancelVoiceAssistantRequest(requestId: String) {
        voiceAssistantHub.cancelRequest(requestId)
    }

    fun pushVoiceAssistantPayload(payload: VoiceAssistantResult) {
        _voiceAssistantPayload.value = payload
    }

    fun clearVoiceAssistantPayload() {
        _voiceAssistantPayload.value = null
    }

    fun resetPreviewState(datasetId: String?) {
        previewCalcJob?.cancel()
        previewReportJob?.cancel()
        _previewState.value = PreviewUiState(datasetId = datasetId)
    }

    fun resetImageImportState(datasetId: String?) {
        _imageImportState.value = ImageImportUiState(datasetId = datasetId)
    }

    fun updateImageImportState(update: (ImageImportUiState) -> ImageImportUiState) {
        _imageImportState.update(update)
    }

    fun updatePreviewCalcState(update: (PreviewCalcCheckState) -> PreviewCalcCheckState) {
        _previewState.update { state -> state.copy(calcCheck = update(state.calcCheck)) }
    }

    fun updatePreviewReportState(update: (PreviewReportState) -> PreviewReportState) {
        _previewState.update { state -> state.copy(report = update(state.report)) }
    }

    fun clearPreviewCalcResults() {
        previewCalcJob?.cancel()
        _previewState.update { state ->
            state.copy(
                calcCheck = state.calcCheck.copy(
                    busy = false,
                    message = null,
                    error = null,
                    apiCalc1 = null,
                    apiCalc2 = null,
                    apiWarn1 = emptyList(),
                    apiWarn2 = emptyList(),
                    diffReport1 = null,
                    diffReport2 = null,
                    calcSource = CalcSource.Internal,
                    lastUpdatedAt = null,
                ),
            )
        }
    }

    fun clearPreviewReportResults() {
        previewReportJob?.cancel()
        _previewState.update { state ->
            state.copy(
                report = state.report.copy(
                    busy = false,
                    progress = null,
                    error = null,
                    text1 = null,
                    text2 = null,
                    lastUpdatedAt = null,
                ),
            )
        }
    }

    suspend fun checkApis(settings: Settings): Pair<ApiHealthState, ApiHealthState> = coroutineScope {
        _taskFeedback.value = TaskFeedbackState(running = true, title = "检测 API", detail = "正在检测 API1/API2…")
        val a1 = async { checkOne(settings.api1) }
        val a2 = async { checkOne(settings.api2) }
        val h1 = a1.await()
        val h2 = a2.await()
        setApiHealth(1, h1)
        setApiHealth(2, h2)
        appendApiHealthEntry(settings.api1, h1)
        appendApiHealthEntry(settings.api2, h2)
        val okCount = listOf(h1, h2).count { it.ok }
        _taskFeedback.value = TaskFeedbackState(
            running = false,
            title = "检测 API 完成",
            detail = "可用 $okCount/2 · API1:${h1.message} · API2:${h2.message}",
            level = if (okCount == 0) IssueLevel.Error else IssueLevel.Info,
        )
        h1 to h2
    }

    fun requestPreviewCommand(command: PreviewCommand) {
        _previewCommand.value = command
    }

    fun clearPreviewCommand() {
        _previewCommand.value = null
    }

    fun startPreviewReport(prompt: String, settings: Settings) {
        previewReportJob?.cancel()
        val snapshot = _previewState.value.report
        if (!settings.aiAssistantEnabled) {
            _previewState.update { state -> state.copy(report = snapshot.copy(error = "请先在设置中开启 AI 功能。", busy = false)) }
            return
        }
        if (!snapshot.useApi1 && !snapshot.useApi2) {
            _previewState.update { state -> state.copy(report = snapshot.copy(error = "请至少选择 API1 或 API2。", busy = false)) }
            return
        }
        if (snapshot.useApi1 && !hasApi(settings.api1)) {
            _previewState.update { state -> state.copy(report = snapshot.copy(error = "API1 未配置 Base URL / Model。", busy = false)) }
            return
        }
        if (snapshot.useApi2 && !hasApi(settings.api2)) {
            _previewState.update { state -> state.copy(report = snapshot.copy(error = "API2 未配置 Base URL / Model。", busy = false)) }
            return
        }

        _previewState.update { state ->
            state.copy(
                report = snapshot.copy(
                    busy = true,
                    progress = "生成中…",
                    error = null,
                    text1 = null,
                    text2 = null,
                ),
            )
        }

        previewReportJob = viewModelScope.launch {
            val useApi1 = snapshot.useApi1
            val useApi2 = snapshot.useApi2
            val d1 = if (useApi1) async { runCatching { assistantClient.call(settings.api1, prompt, maxTokens = 1600) } } else null
            val d2 = if (useApi2) async { runCatching { assistantClient.call(settings.api2, prompt, maxTokens = 1600) } } else null

            val r1 = d1?.await()
            val r2 = d2?.await()

            val t1 = r1?.getOrNull()
            val t2 = r2?.getOrNull()
            val err1 = r1?.exceptionOrNull()?.message
            val err2 = r2?.exceptionOrNull()?.message
            val error = listOfNotNull(
                err1?.let { "API1：$it" },
                err2?.let { "API2：$it" },
            ).takeIf { it.isNotEmpty() }?.joinToString("\n")

            _previewState.update { state ->
                state.copy(
                    report = state.report.copy(
                        busy = false,
                        progress = null,
                        text1 = t1,
                        text2 = t2,
                        error = error,
                        lastUpdatedAt = nowIso(),
                    ),
                )
            }
        }
    }

    fun startPreviewCalcCheck(dataset: Dataset, settings: Settings) {
        previewCalcJob?.cancel()
        val snapshot = _previewState.value.calcCheck
        if (!settings.aiAssistantEnabled) {
            _previewState.update { state -> state.copy(calcCheck = snapshot.copy(error = "请先在设置中开启 AI 功能。", busy = false)) }
            return
        }
        if (!snapshot.useApi1 && !snapshot.useApi2) {
            _previewState.update { state -> state.copy(calcCheck = snapshot.copy(error = "请至少选择一个 API。", busy = false)) }
            return
        }
        if (snapshot.useApi1 && !hasApi(settings.api1)) {
            _previewState.update { state -> state.copy(calcCheck = snapshot.copy(error = "API1 未配置 Base URL / Model。", busy = false)) }
            return
        }
        if (snapshot.useApi2 && !hasApi(settings.api2)) {
            _previewState.update { state -> state.copy(calcCheck = snapshot.copy(error = "API2 未配置 Base URL / Model。", busy = false)) }
            return
        }

        _previewState.update { state ->
            state.copy(
                calcCheck = snapshot.copy(
                    busy = true,
                    message = null,
                    error = null,
                    apiCalc1 = null,
                    apiCalc2 = null,
                    apiWarn1 = emptyList(),
                    apiWarn2 = emptyList(),
                    diffReport1 = null,
                    diffReport2 = null,
                ),
            )
        }

        previewCalcJob = viewModelScope.launch {
            val internalCalc = calcDataset(dataset)
            val auditInputs = dataset.points.indices.map { buildCalcAuditPointInput(dataset, internalCalc, it) }
            val totalPoints = auditInputs.size
            val batchSize = calcAuditBatchSize(dataset.species.size, totalPoints)

            suspend fun callOne(api: ApiConfig, label: String): CalcAuditRunResult {
                val warnings = mutableListOf<String>()
                val outputs = mutableListOf<CalcAuditOutput>()
                var callFail = 0
                var parseFail = 0
                var rateLimited = false
                var authFailed = false
                var checkedPoints = 0
                var firstCallFail: String? = null
                var firstParseFail: String? = null
                val pending = ArrayDeque<List<com.plankton.one102.data.api.CalcAuditPointInput>>()
                auditInputs.chunked(batchSize).forEach { pending.add(it) }

                suspend fun retrySingle(input: com.plankton.one102.data.api.CalcAuditPointInput): List<CalcAuditOutput>? {
                    val prompt = buildCalcAuditPointPrompt(input, decimals = 8, strictJsonOnly = true)
                    val raw = try {
                        assistantClient.call(api, prompt, maxTokens = 700)
                    } catch (_: Exception) {
                        return null
                    }
                    var single = parseCalcAuditOutputFromText(raw)
                    if (single == null) {
                        val repaired = try {
                            assistantClient.call(api, buildCalcAuditRepairPrompt(raw), maxTokens = 500)
                        } catch (_: Exception) {
                            null
                        }
                        single = repaired?.let { parseCalcAuditOutputFromText(it) }
                    }
                    return single?.let { listOf(it) }
                }

                while (pending.isNotEmpty()) {
                    if (rateLimited || authFailed) break
                    val batch = pending.removeFirst()
                    val pointLabel = batch.joinToString("、") { it.point.label.ifBlank { (it.point.idx + 1).toString() } }
                    val prompt = buildCalcAuditBatchPrompt(batch, decimals = 8, strictJsonOnly = true)
                    val maxTokens = if (batch.size <= 2) 900 else 1200

                    val raw = try {
                        assistantClient.call(api, prompt, maxTokens = maxTokens)
                    } catch (e: Exception) {
                        val msg = e.message ?: "未知错误"
                        when {
                            isRateLimitError(msg) -> rateLimited = true
                            isAuthError(msg) -> authFailed = true
                            else -> {
                                callFail += 1
                                if (firstCallFail == null) firstCallFail = pointLabel
                            }
                        }
                        continue
                    }

                    var outs = parseCalcAuditOutputsFromText(raw)
                    if (outs == null) {
                        val repaired = try {
                            assistantClient.call(api, buildCalcAuditRepairPrompt(raw), maxTokens = 600)
                        } catch (_: Exception) {
                            null
                        }
                        outs = repaired?.let { parseCalcAuditOutputsFromText(it) }
                    }

                    if (outs == null && batch.size == 1) {
                        outs = retrySingle(batch.first())
                    }

                    if (outs == null) {
                        if (batch.size > 1) {
                            val mid = batch.size / 2
                            val left = batch.subList(0, mid)
                            val right = batch.subList(mid, batch.size)
                            pending.addFirst(right)
                            pending.addFirst(left)
                        } else {
                            parseFail += 1
                            if (firstParseFail == null) firstParseFail = pointLabel
                        }
                        continue
                    }

                    val byIdx = outs.associateBy { it.pointIdx }
                    for (input in batch) {
                        val out = byIdx[input.point.idx] ?: CalcAuditOutput(pointIdx = input.point.idx)
                        outputs += out
                    }
                    checkedPoints += batch.size
                    delay(420)
                }

                if (rateLimited) warnings += "$label 触发限流(429)，已停止后续核对"
                if (authFailed) warnings += "$label 鉴权失败，已停止后续核对"
                if (callFail > 0) warnings += "$label 调用失败 $callFail 次${firstCallFail?.let { "（如：$it）" }.orEmpty()}"
                if (parseFail > 0) warnings += "$label 解析失败 $parseFail 次${firstParseFail?.let { "（如：$it）" }.orEmpty()}"
                if (checkedPoints in 1 until totalPoints) {
                    warnings += "$label 仅核对 $checkedPoints/$totalPoints 点位，其余按内置结果处理"
                }

                val calc = if (outputs.isNotEmpty()) applyCalcAuditOutputs(dataset, internalCalc, outputs) else null
                val notes = outputs.flatMap { it.notes }.mapNotNull { it.trim().ifBlank { null } }
                val warns = warnings + notes
                return CalcAuditRunResult(calc = calc, warnings = warns, checkedPoints = checkedPoints, totalPoints = totalPoints)
            }

            val a1 = if (snapshot.useApi1) async { callOne(settings.api1, "API1") } else null
            val a2 = if (snapshot.useApi2) async { callOne(settings.api2, "API2") } else null

            val r1 = a1?.await()
            val r2 = a2?.await()

            val apiCalc1 = r1?.calc
            val apiCalc2 = r2?.calc
            val diffReport1 = apiCalc1?.let { diffCalc(dataset, internalCalc, it) }
            val diffReport2 = apiCalc2?.let { diffCalc(dataset, internalCalc, it) }

            val hasMismatch = listOf(diffReport1, diffReport2).any { (it?.mismatchCount ?: 0) > 0 }
            val prevSource = _previewState.value.calcCheck.calcSource
            val nextSource = when {
                !hasMismatch -> CalcSource.Internal
                prevSource == CalcSource.Api1 && apiCalc1 != null -> CalcSource.Api1
                prevSource == CalcSource.Api2 && apiCalc2 != null -> CalcSource.Api2
                else -> CalcSource.Internal
            }

            fun summary(label: String, res: CalcAuditRunResult?): String? {
                if (res == null) return null
                return if (res.calc == null) {
                    "$label 未获取到核对结果"
                } else {
                    "$label 核对 ${res.checkedPoints}/${res.totalPoints}"
                }
            }

            val summaries = buildList {
                summary(apiLabel(settings.api1, "API1"), r1)?.let { add(it) }
                summary(apiLabel(settings.api2, "API2"), r2)?.let { add(it) }
            }
            val calcCheckMessage = summaries.joinToString(" · ").ifBlank { "未获取到可用结果" }

            _previewState.update { state ->
                state.copy(
                    calcCheck = state.calcCheck.copy(
                        busy = false,
                        message = calcCheckMessage,
                        error = null,
                        apiCalc1 = apiCalc1,
                        apiCalc2 = apiCalc2,
                        apiWarn1 = r1?.warnings.orEmpty(),
                        apiWarn2 = r2?.warnings.orEmpty(),
                        diffReport1 = diffReport1,
                        diffReport2 = diffReport2,
                        calcSource = nextSource,
                        lastUpdatedAt = nowIso(),
                    ),
                )
            }
        }
    }

    fun launchAssistantTask(block: suspend CoroutineScope.() -> Unit): Job {
        return viewModelScope.launch { block() }
    }

    fun startAssistantAiTask(
        prompt: String,
        api1: ApiConfig,
        api2: ApiConfig,
        useDualApi: Boolean,
        taskLabel: String,
    ) {
        assistantAiJob?.cancel()
        assistantAiJob = viewModelScope.launch {
            _assistantState.update { state ->
                state.copy(
                    ai = state.ai.copy(
                        busy = true,
                        error = null,
                        answer1 = "",
                        answer2 = "",
                        answer1Label = apiLabel(api1, "API1"),
                        answer2Label = if (useDualApi) apiLabel(api2, "API2") else "",
                        taskLabel = taskLabel,
                    ),
                )
            }

            try {
                val a1 = async { callAiWithContinuation(assistantClient, api1, prompt) }
                val a2 = if (useDualApi) async { callAiWithContinuation(assistantClient, api2, prompt) } else null
                val res1 = a1.await()
                val res2 = a2?.await().orEmpty()
                _assistantState.update { state ->
                    state.copy(
                        ai = state.ai.copy(
                            busy = false,
                            answer1 = res1,
                            answer2 = res2,
                            answer1Label = apiLabel(api1, "API1"),
                            answer2Label = if (useDualApi) apiLabel(api2, "API2") else "",
                            lastUpdatedAt = nowIso(),
                        ),
                    )
                }
            } catch (e: Exception) {
                _assistantState.update { state ->
                    state.copy(ai = state.ai.copy(busy = false, error = e.message ?: e.toString()))
                }
            }
        }
    }

    fun startAssistantTrace(dataset: Dataset, pointId: String, builder: suspend (Dataset, String) -> String) {
        if (pointId.isBlank()) return
        assistantTraceJob?.cancel()
        assistantTraceJob = viewModelScope.launch {
            _assistantState.update { state ->
                state.copy(trace = state.trace.copy(busyPointId = pointId, error = null))
            }
            try {
                val result = builder(dataset, pointId)
                _assistantState.update { state ->
                    state.copy(
                        trace = state.trace.copy(
                            busyPointId = null,
                            byPointId = state.trace.byPointId + (pointId to result),
                        ),
                    )
                }
            } catch (e: Exception) {
                _assistantState.update { state ->
                    state.copy(trace = state.trace.copy(busyPointId = null, error = e.message ?: e.toString()))
                }
            }
        }
    }

    fun selectDataset(id: String) {
        viewModelScope.launch {
            flushPendingSave()
            prefs.setCurrentDatasetId(id)
        }
    }

    fun createNewDataset() {
        viewModelScope.launch {
            flushPendingSave()
            val ds = datasetRepo.createNew(settings.value)
            prefs.setCurrentDatasetId(ds.id)
        }
    }

    fun duplicateDataset(id: String) {
        viewModelScope.launch {
            flushPendingSave()
            val copy = datasetRepo.duplicate(id) ?: return@launch
            prefs.setCurrentDatasetId(copy.id)
        }
    }

    fun importNewDataset(dataset: Dataset) {
        viewModelScope.launch {
            flushPendingSave()
            datasetRepo.save(dataset)
            prefs.setCurrentDatasetId(dataset.id)
        }
    }

    fun createTemplateDataset(resetCounts: Boolean = true) {
        viewModelScope.launch {
            flushPendingSave()
            val cur = _draftDataset.value ?: return@launch
            val now = nowIso()
            val nextPoints = cur.points
            val nextSpecies = if (resetCounts) {
                cur.species.map { sp ->
                    sp.copy(countsByPointId = nextPoints.associate { it.id to 0 })
                }
            } else {
                cur.species
            }
            val copy = cur.copy(
                id = newId(),
                createdAt = now,
                updatedAt = now,
                species = nextSpecies,
                lastAutoMatch = null,
                readOnly = false,
                snapshotAt = null,
                snapshotSourceId = null,
            )
            datasetRepo.save(copy)
            prefs.setCurrentDatasetId(copy.id)
        }
    }

    fun deleteDataset(id: String) {
        viewModelScope.launch {
            val wasCurrent = currentDatasetId.value == id
            if (wasCurrent) flushPendingSave()
            datasetRepo.deleteById(id)
            if (wasCurrent) ensureCurrentDataset()
        }
    }

    fun updateCurrentDataset(updater: (Dataset) -> Dataset) {
        viewModelScope.launch {
            updateMutex.withLock {
                val cur = _draftDataset.value ?: return@withLock
                if (cur.readOnly) {
                    _readOnlyEvents.tryEmit("当前数据集为只读快照，请先复制为新数据集再编辑。")
                    return@withLock
                }
                val updated = updater(cur)
                if (updated == cur) return@withLock
                pushUndo(cur)
                val next = touchDataset(updated)
                _draftDataset.value = next
                pendingDirtyId = next.id
                refreshUndoRedoFlags()

                pendingSaveJob?.cancel()
                pendingSaveJob = viewModelScope.launch {
                    delay(350)
                    flushPendingSave()
                }
            }
        }
    }

    fun undoDatasetEdit() {
        viewModelScope.launch {
            updateMutex.withLock {
                val cur = _draftDataset.value ?: return@withLock
                val prev = undoStack.removeLastOrNull() ?: return@withLock
                pushRedo(cur)
                val next = touchDataset(prev.copy(updatedAt = nowIso()))
                _draftDataset.value = next
                pendingDirtyId = next.id
                refreshUndoRedoFlags()
                pendingSaveJob?.cancel()
                pendingSaveJob = viewModelScope.launch {
                    delay(120)
                    flushPendingSave()
                }
            }
        }
    }

    fun redoDatasetEdit() {
        viewModelScope.launch {
            updateMutex.withLock {
                val cur = _draftDataset.value ?: return@withLock
                val nextFromRedo = redoStack.removeLastOrNull() ?: return@withLock
                pushUndo(cur)
                val next = touchDataset(nextFromRedo.copy(updatedAt = nowIso()))
                _draftDataset.value = next
                pendingDirtyId = next.id
                refreshUndoRedoFlags()
                pendingSaveJob?.cancel()
                pendingSaveJob = viewModelScope.launch {
                    delay(120)
                    flushPendingSave()
                }
            }
        }
    }

    fun setActivePointId(id: Id) {
        _activePointId.value = id
    }

    fun clearActivePointId() {
        _activePointId.value = null
    }

    fun requestSpeciesAction(speciesId: Id, kind: PendingSpeciesActionKind) {
        _pendingSpeciesAction.value = PendingSpeciesAction(speciesId = speciesId, kind = kind)
    }

    fun clearPendingSpeciesAction() {
        _pendingSpeciesAction.value = null
    }

    fun setApiHealth(index: Int, state: ApiHealthState?) {
        when (index) {
            1 -> _api1Health.value = state
            2 -> _api2Health.value = state
        }
    }

    private suspend fun flushPendingSave() {
        updateMutex.withLock {
            val dirtyId = pendingDirtyId ?: return@withLock
            val ds = _draftDataset.value ?: return@withLock
            if (ds.id != dirtyId) {
                pendingDirtyId = null
                return@withLock
            }
            pendingDirtyId = null
            datasetRepo.save(ds)
        }
    }

    private fun pushUndo(dataset: Dataset) {
        if (undoStack.size >= maxUndoDepth) undoStack.removeFirst()
        undoStack.addLast(dataset)
        redoStack.clear()
        refreshUndoRedoFlags()
    }

    private fun pushRedo(dataset: Dataset) {
        if (redoStack.size >= maxUndoDepth) redoStack.removeFirst()
        redoStack.addLast(dataset)
        refreshUndoRedoFlags()
    }

    private fun clearUndoRedo() {
        undoStack.clear()
        redoStack.clear()
        refreshUndoRedoFlags()
    }

    private fun refreshUndoRedoFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    fun renameDataset(id: String, titlePrefix: String) {
        viewModelScope.launch {
            val cur = _draftDataset.value
            if (cur != null && cur.id == id) {
                updateCurrentDataset { it.copy(titlePrefix = titlePrefix) }
                return@launch
            }
            val ds = datasetRepo.getById(id) ?: return@launch
            if (ds.readOnly) {
                _readOnlyEvents.tryEmit("当前数据集为只读快照，请先复制为新数据集再重命名。")
                return@launch
            }
            datasetRepo.save(ds.copy(titlePrefix = titlePrefix))
        }
    }

    fun createSnapshotNow(source: Dataset, reason: String) {
        if (source.readOnly) return
        viewModelScope.launch {
            flushPendingSave()
            datasetRepo.createSnapshot(source, reason)
        }
    }

    fun saveSettings(next: Settings) {
        viewModelScope.launch { prefs.saveSettings(next) }
    }

    fun loadMoreDatasets() {
        viewModelScope.launch {
            val total = datasetTotalCount.value
            if (_datasetPageSize.value >= total) return@launch
            _datasetPageSize.value = (_datasetPageSize.value + 30).coerceAtMost(total)
        }
    }

    suspend fun getDatasetById(id: String): Dataset? {
        return datasetRepo.getById(id)
    }

    fun readBackupSummary(contentResolver: ContentResolver, uri: Uri, password: String? = null, onDone: (Result<BackupSummary>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { backupRepo.readBackupSummary(contentResolver, uri, password = password) }
            onDone(result)
        }
    }

    fun exportBackup(
        contentResolver: ContentResolver,
        uri: Uri,
        options: BackupExportOptions = BackupExportOptions(),
        onDone: (Result<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            _taskFeedback.value = TaskFeedbackState(running = true, title = "导出备份", detail = "正在写入备份文件…")
            flushPendingSave()
            val result = runCatching { backupRepo.exportBackup(contentResolver, uri, options = options) }
            _taskFeedback.value = result.fold(
                onSuccess = {
                    TaskFeedbackState(running = false, title = "导出备份完成", detail = "备份文件已写入。", level = IssueLevel.Info)
                },
                onFailure = {
                    TaskFeedbackState(running = false, title = "导出备份失败", detail = it.message ?: "未知错误", level = IssueLevel.Error)
                },
            )
            onDone(result)
        }
    }

    fun importBackup(
        contentResolver: ContentResolver,
        uri: Uri,
        options: BackupImportOptions = BackupImportOptions(),
        onDone: (Result<Int>) -> Unit,
    ) {
        viewModelScope.launch {
            _taskFeedback.value = TaskFeedbackState(running = true, title = "导入备份", detail = "正在导入并校验…")
            flushPendingSave()
            val result = runCatching { backupRepo.importBackup(contentResolver, uri, options = options) }
            _taskFeedback.value = result.fold(
                onSuccess = {
                    TaskFeedbackState(running = false, title = "导入备份完成", detail = "已导入 ${it} 条数据。", level = IssueLevel.Info)
                },
                onFailure = {
                    TaskFeedbackState(running = false, title = "导入备份失败", detail = it.message ?: "未知错误", level = IssueLevel.Error)
                },
            )
            onDone(result)
        }
    }

    fun clearTaskFeedback() {
        _taskFeedback.value = TaskFeedbackState()
    }

    private data class CalcAuditRunResult(
        val calc: DatasetCalc?,
        val warnings: List<String>,
        val checkedPoints: Int,
        val totalPoints: Int,
    )

    private fun hasApi(api: ApiConfig): Boolean = api.baseUrl.isNotBlank() && api.model.isNotBlank()

    private fun apiLabel(api: ApiConfig, fallback: String): String = api.name.trim().ifBlank { fallback }

    private fun isRateLimitError(message: String): Boolean {
        val msg = message.lowercase()
        return msg.contains("429") || msg.contains("too many requests") || msg.contains("rate limit")
    }

    private fun isAuthError(message: String): Boolean {
        val msg = message.lowercase()
        return msg.contains("401") || msg.contains("403") || msg.contains("unauthorized") || msg.contains("forbidden")
    }

    private fun pingPrompt(): String = "请只回复一行：OK"

    private suspend fun checkOne(api: ApiConfig): ApiHealthState {
        if (!hasApi(api)) {
            return ApiHealthState(ok = false, message = "未配置 Base URL / Model", baseUrl = api.baseUrl, model = api.model)
        }
        val start = System.currentTimeMillis()
        val res = assistantClient.check(api, pingPrompt(), maxTokens = 16)
        val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(0L)
        return ApiHealthState(
            ok = res.ok,
            message = res.message,
            baseUrl = api.baseUrl,
            model = api.model,
            latencyMs = elapsed,
        )
    }

    private fun appendApiHealthEntry(api: ApiConfig, result: ApiHealthState) {
        val name = api.name.trim().ifBlank { api.model.trim().ifBlank { "API" } }
        val entry = ApiHealthEntry(
            at = nowIso(),
            apiName = name,
            ok = result.ok,
            message = result.message,
            latencyMs = result.latencyMs,
        )
        _apiHealthRecent.update { old -> (old + entry).takeLast(20) }
    }

    private fun calcAuditBatchSize(speciesCount: Int, pointCount: Int): Int {
        if (pointCount <= 1) return 1
        return when {
            speciesCount >= 140 -> 1
            speciesCount >= 100 -> 2
            speciesCount >= 60 -> 3
            speciesCount >= 30 -> 4
            else -> 6
        }
    }
}
