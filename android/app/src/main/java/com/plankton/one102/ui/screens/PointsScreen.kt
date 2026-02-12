package com.plankton.one102.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plankton.one102.domain.DepthRange
import com.plankton.one102.domain.buildStratifiedLabel
import com.plankton.one102.domain.applyImportedPoints
import com.plankton.one102.domain.Id
import com.plankton.one102.domain.createBlankPoint
import com.plankton.one102.domain.formatDepthForLabel
import com.plankton.one102.domain.newId
import com.plankton.one102.domain.resolveSiteAndDepthForPoint
import com.plankton.one102.importer.importPointsFromExcel
import com.plankton.one102.ui.MainViewModel
import com.plankton.one102.ui.components.GlassBackground
import com.plankton.one102.ui.components.GlassCard
import com.plankton.one102.ui.components.GlassContent
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

private const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

private class DragDropListState(
    val listState: LazyListState,
    private val scope: CoroutineScope,
    private val autoScrollThresholdPx: Float,
    private val autoScrollStepPx: Float,
) {
    var onMove: (Int, Int) -> Unit = { _, _ -> }
    var draggingItemIndex by mutableStateOf<Int?>(null)
    var dragStartIndex by mutableStateOf<Int?>(null)
    var targetIndex by mutableStateOf<Int?>(null)
    var draggingItemSize by mutableStateOf(0)
    var draggingItemOffset by mutableStateOf(0f)
    var lastDragDelta by mutableStateOf(0f)
    var resolveTargetIndex: ((Int) -> Int)? = null
    private var lastAutoScrollAt = 0L

    fun onDragStart(offset: Offset) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            offset.y.toInt() in info.offset..(info.offset + info.size)
        } ?: return
        draggingItemIndex = item.index
        dragStartIndex = item.index
        targetIndex = item.index
        draggingItemSize = item.size
        draggingItemOffset = 0f
    }

    fun onDrag(offset: Offset) {
        val currentIndex = dragStartIndex ?: return
        lastDragDelta = offset.y
        draggingItemOffset += offset.y
        val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentIndex } ?: return
        val currentOffset = itemInfo.offset + draggingItemOffset
        val middle = currentOffset + itemInfo.size / 2
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            middle.toInt() in info.offset..(info.offset + info.size)
        }
        val rawTarget = target?.index ?: currentIndex
        targetIndex = resolveTargetIndex?.invoke(rawTarget) ?: rawTarget
        maybeAutoScroll(middle.toFloat())
    }

    fun onDragEnd() {
        val from = dragStartIndex
        val to = targetIndex
        if (from != null && to != null && from != to) {
            onMove(from, to)
        }
        draggingItemIndex = null
        dragStartIndex = null
        targetIndex = null
        draggingItemSize = 0
        draggingItemOffset = 0f
        lastDragDelta = 0f
    }

    fun onDragCancel() {
        draggingItemIndex = null
        dragStartIndex = null
        targetIndex = null
        draggingItemSize = 0
        draggingItemOffset = 0f
        lastDragDelta = 0f
    }

    fun itemDisplacement(index: Int): Float {
        val from = dragStartIndex ?: return 0f
        val to = targetIndex ?: return 0f
        if (index == from) return draggingItemOffset
        if (from < to && index in (from + 1)..to) return -draggingItemSize.toFloat()
        if (from > to && index in to until from) return draggingItemSize.toFloat()
        return 0f
    }

    private fun maybeAutoScroll(pointerY: Float) {
        if (draggingItemIndex == null) return
        if (autoScrollThresholdPx <= 0f || autoScrollStepPx == 0f) return
        val now = System.currentTimeMillis()
        if (now - lastAutoScrollAt < 40) return
        val layout = listState.layoutInfo
        val viewportStart = layout.viewportStartOffset.toFloat()
        val viewportEnd = layout.viewportEndOffset.toFloat()
        val nearTop = pointerY < viewportStart + autoScrollThresholdPx
        val nearBottom = pointerY > viewportEnd - autoScrollThresholdPx
        val delta = when {
            nearTop && listState.canScrollBackward -> -autoScrollStepPx
            nearBottom && listState.canScrollForward -> autoScrollStepPx
            else -> 0f
        }
        if (delta == 0f) return
        lastAutoScrollAt = now
        scope.launch {
            listState.scrollBy(delta)
            draggingItemOffset += delta
        }
    }
}

@Composable
private fun rememberDragDropListState(
    listState: LazyListState,
    scope: CoroutineScope,
    autoScrollThresholdPx: Float,
    autoScrollStepPx: Float,
    onMove: (Int, Int) -> Unit,
): DragDropListState {
    val state = remember(listState, scope, autoScrollThresholdPx, autoScrollStepPx) {
        DragDropListState(listState, scope, autoScrollThresholdPx, autoScrollStepPx)
    }
    state.onMove = onMove
    return state
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PointsScreen(viewModel: MainViewModel, padding: PaddingValues) {
    val context = LocalContext.current
    val dataset by viewModel.currentDataset.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val globalPointId by viewModel.activePointId.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var importBusy by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var bulkExpanded by rememberSaveable { mutableStateOf(false) }

    val ds = dataset ?: run {
        GlassBackground {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text("加载中…")
            }
        }
        return
    }

    val points = ds.points
    var activePointId by rememberSaveable(ds.id) { mutableStateOf(globalPointId ?: points.firstOrNull()?.id ?: "") }
    val safeIndex = points.indexOfFirst { it.id == activePointId }
        .let { if (it >= 0) it else 0 }
        .coerceIn(0, (points.size - 1).coerceAtLeast(0))
    val bulkPoint = points.getOrNull(safeIndex)

    LaunchedEffect(ds.id, points.size) {
        if (points.isEmpty()) {
            activePointId = ""
            viewModel.clearActivePointId()
            return@LaunchedEffect
        }
        if (activePointId.isBlank() || points.none { it.id == activePointId }) {
            activePointId = points.first().id
        }
    }

    LaunchedEffect(globalPointId, ds.id) {
        val pid = globalPointId ?: return@LaunchedEffect
        if (pid.isNotBlank() && points.any { it.id == pid } && pid != activePointId) {
            activePointId = pid
        }
    }

    LaunchedEffect(activePointId) {
        if (activePointId.isNotBlank()) viewModel.setActivePointId(activePointId)
    }

    fun updatePoint(pointId: Id, updater: (com.plankton.one102.domain.Point) -> com.plankton.one102.domain.Point) {
        viewModel.updateCurrentDataset { cur ->
            cur.copy(points = cur.points.map { p -> if (p.id == pointId) updater(p) else p })
        }
    }

    fun nextDefaultLabel(existing: List<String>): String {
        var n = existing.size + 1
        while (existing.contains(n.toString())) n += 1
        return n.toString()
    }

    fun nextDefaultStratifiedLabel(existingPoints: List<com.plankton.one102.domain.Point>, defaultDepth: Double = 0.0): String {
        val used = existingPoints.mapNotNull { p ->
            val (site, _) = resolveSiteAndDepthForPoint(label = p.label, site = p.site, depthM = p.depthM)
            site?.trim()?.toIntOrNull()
        }.toSet()

        var n = 1
        while (used.contains(n)) n += 1
        return buildStratifiedLabel(site = n.toString(), depthM = defaultDepth)
    }

    fun addPointAndGo() {
        val id = newId()
        viewModel.updateCurrentDataset { cur ->
            val label = if (cur.stratification.enabled) {
                nextDefaultStratifiedLabel(cur.points)
            } else {
                nextDefaultLabel(cur.points.map { it.label })
            }
            val point = createBlankPoint(settings, label, id)
            val nextSpecies = cur.species.map { sp ->
                sp.copy(countsByPointId = sp.countsByPointId + (id to 0))
            }
            cur.copy(points = cur.points + point, species = nextSpecies)
        }
        focusManager.clearFocus()
        activePointId = id
        viewModel.setActivePointId(id)
    }

    fun deleteActivePoint() {
        if (points.isEmpty()) return
        val targetId = activePointId.takeIf { it.isNotBlank() } ?: return
        val curIdx = safeIndex
        val nextPoints = points.filter { it.id != targetId }
        viewModel.updateCurrentDataset { cur ->
            val updatedPoints = cur.points.filter { it.id != targetId }
            val nextSpecies = cur.species.map { sp ->
                sp.copy(countsByPointId = sp.countsByPointId - targetId)
            }
            cur.copy(points = updatedPoints, species = nextSpecies)
        }
        focusManager.clearFocus()
        val nextId = nextPoints.getOrNull((curIdx - 1).coerceAtLeast(0))?.id ?: nextPoints.firstOrNull()?.id
        if (nextId == null) {
            activePointId = ""
            viewModel.clearActivePointId()
        } else {
            activePointId = nextId
            viewModel.setActivePointId(nextId)
        }
    }

    fun goNext(autoCreate: Boolean) {
        focusManager.clearFocus()
        if (points.isEmpty()) return
        activePointId = if (safeIndex < points.size - 1) {
            points[safeIndex + 1].id
        } else {
            points.first().id
        }
    }

    fun goPrev() {
        focusManager.clearFocus()
        if (points.isEmpty()) return
        if (safeIndex > 0) activePointId = points[safeIndex - 1].id
    }

    fun movePoint(fromIndex: Int, toIndex: Int) {
        if (points.isEmpty()) return
        val from = fromIndex.coerceIn(0, points.size - 1)
        val to = toIndex.coerceIn(0, points.size - 1)
        if (from == to) return
        viewModel.updateCurrentDataset { cur ->
            val list = cur.points.toMutableList()
            if (from !in list.indices || to !in list.indices) return@updateCurrentDataset cur
            val p = list.removeAt(from)
            list.add(to, p)
            cur.copy(points = list)
        }
    }

    fun moveActivePoint(delta: Int) {
        if (points.isEmpty()) return
        val from = safeIndex
        val to = (from + delta).coerceIn(0, points.size - 1)
        if (from == to) return
        movePoint(from, to)
        focusManager.clearFocus()
    }

    fun swapActivePointTo(targetIndex: Int) {
        if (points.isEmpty()) return
        val from = safeIndex
        val to = targetIndex.coerceIn(0, points.size - 1)
        if (from == to) return
        viewModel.updateCurrentDataset { cur ->
            val list = cur.points.toMutableList()
            if (from !in list.indices || to !in list.indices) return@updateCurrentDataset cur
            val tmp = list[from]
            list[from] = list[to]
            list[to] = tmp
            cur.copy(points = list)
        }
        focusManager.clearFocus()
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (importBusy) return@rememberLauncherForActivityResult

        importBusy = true
        importMessage = null
        scope.launch {
            val res = runCatching {
                val imported = importPointsFromExcel(context.contentResolver, uri)
                viewModel.updateCurrentDataset { cur ->
                    applyImportedPoints(cur, imported, defaultVOrigL = settings.defaultVOrigL)
                }
                imported.size
            }
            val msg = res.fold(
                onSuccess = { "已导入 $it 个采样点（按名称匹配尽量保留已有计数）" },
                onFailure = { "导入失败：${it.message ?: it.toString()}" },
            )
            importMessage = msg
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            focusManager.clearFocus()
            activePointId = ""
            importBusy = false
        }
    }

    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("采样点", style = MaterialTheme.typography.titleLarge)
            Text(
                "建议：按顺序录入每条采样点的“浓缩体积（mL）”。输入后可点“下一条”（末尾回到第一条）。点位名称可自定义；原水体积默认 20 L，可修改。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                val strat = ds.stratification
                var upperMinText by rememberSaveable(ds.id) { mutableStateOf(strat.upper.minM.toString()) }
                var upperMaxText by rememberSaveable(ds.id) { mutableStateOf(strat.upper.maxM.toString()) }
                var middleMinText by rememberSaveable(ds.id) { mutableStateOf(strat.middle.minM.toString()) }
                var middleMaxText by rememberSaveable(ds.id) { mutableStateOf(strat.middle.maxM.toString()) }
                var lowerMinText by rememberSaveable(ds.id) { mutableStateOf(strat.lower.minM.toString()) }
                var lowerMaxText by rememberSaveable(ds.id) { mutableStateOf(strat.lower.maxM.toString()) }

                LaunchedEffect(ds.id) {
                    val s = ds.stratification
                    upperMinText = s.upper.minM.toString()
                    upperMaxText = s.upper.maxM.toString()
                    middleMinText = s.middle.minM.toString()
                    middleMaxText = s.middle.maxM.toString()
                    lowerMinText = s.lower.minM.toString()
                    lowerMaxText = s.lower.maxM.toString()
                }

                fun updateRange(upper: DepthRange, middle: DepthRange, lower: DepthRange) {
                    viewModel.updateCurrentDataset { cur ->
                        cur.copy(
                            stratification = cur.stratification.copy(
                                upper = upper,
                                middle = middle,
                                lower = lower,
                            ),
                        )
                    }
                }

                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("分层计算（垂向）", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val enabled = strat.enabled
                            if (!enabled) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.updateCurrentDataset { cur ->
                                            cur.copy(stratification = cur.stratification.copy(enabled = false))
                                        }
                                    },
                                ) { Text("不分层") }
                                Button(
                                    onClick = {
                                        viewModel.updateCurrentDataset { cur ->
                                            val nextPoints = cur.points.mapIndexed { idx, p ->
                                                val raw = p.label.trim()
                                                val (parsedSite, parsedDepth) = resolveSiteAndDepthForPoint(label = raw, site = p.site, depthM = p.depthM)

                                                val site = parsedSite?.trim().takeIf { !it.isNullOrBlank() }
                                                    ?: raw.substringBefore("-", missingDelimiterValue = raw).trim().ifBlank { (idx + 1).toString() }
                                                val depth = parsedDepth ?: 0.0
                                                p.copy(
                                                    label = buildStratifiedLabel(site, depth),
                                                    site = site,
                                                    depthM = depth,
                                                )
                                            }
                                            cur.copy(
                                                points = nextPoints,
                                                stratification = cur.stratification.copy(enabled = true),
                                            )
                                        }
                                        Toast.makeText(context, "已开启分层：点位名按“点位-水深(m)”组织，例如 1-0.3", Toast.LENGTH_LONG).show()
                                    },
                                ) { Text("分层") }
                            } else {
                                Button(
                                    onClick = { viewModel.updateCurrentDataset { cur -> cur.copy(stratification = cur.stratification.copy(enabled = false)) } },
                                ) { Text("不分层") }
                                OutlinedButton(onClick = {}) { Text("分层") }
                            }
                        }
                    }

                    Text(
                        "说明：用于同一站位的不同水深样品（例如 1-0.3 表示 1 号点位 0.3m）。开启后可配置上/中/下层水深范围；导出表2会额外生成分层汇总表。多样性指数按“先合并个体数再重算”，不做各样品 H'/D/J 的平均。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )

                    if (strat.enabled) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = upperMinText,
                                onValueChange = { v ->
                                    upperMinText = v
                                    val min = v.trim().toDoubleOrNull() ?: return@OutlinedTextField
                                    val max = upperMaxText.trim().toDoubleOrNull() ?: return@OutlinedTextField
                                    if (!min.isFinite() || !max.isFinite() || max <= min) return@OutlinedTextField
                                    updateRange(
                                        upper = DepthRange(minM = min, maxM = max),
                                        middle = strat.middle,
                                        lower = strat.lower,
                                    )
                                },
                                label = { Text("上层 起(m)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = upperMaxText,
                                onValueChange = { v ->
                                    upperMaxText = v
                                    val min = upperMinText.trim().toDoubleOrNull() ?: return@OutlinedTextField
                                    val max = v.trim().toDoubleOrNull() ?: return@OutlinedTextField
                                    if (!min.isFinite() || !max.isFinite() || max <= min) return@OutlinedTextField
                                    updateRange(
                                        upper = DepthRange(minM = min, maxM = max),
                                        middle = strat.middle,
                                        lower = strat.lower,
                                    )
                                },
                                label = { Text("上层 止(m)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = middleMinText,
                                onValueChange = { v ->
                                    middleMinText = v
                                    val min = v.trim().toDoubleOrNull() ?: return@OutlinedTextField
                                    val max = middleMaxText.trim().toDoubleOrNull() ?: return@OutlinedTextField
                                    if (!min.isFinite() || !max.isFinite() || max <= min) return@OutlinedTextField
                                    updateRange(
                                        upper = strat.upper,
                                        middle = DepthRange(minM = min, maxM = max),
                                        lower = strat.lower,
                                    )
                                },
                                label = { Text("中层 起(m)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = middleMaxText,
                                onValueChange = { v ->
                                    middleMaxText = v
                                    val min = middleMinText.trim().toDoubleOrNull() ?: return@OutlinedTextField
                                    val max = v.trim().toDoubleOrNull() ?: return@OutlinedTextField
                                    if (!min.isFinite() || !max.isFinite() || max <= min) return@OutlinedTextField
                                    updateRange(
                                        upper = strat.upper,
                                        middle = DepthRange(minM = min, maxM = max),
                                        lower = strat.lower,
                                    )
                                },
                                label = { Text("中层 止(m)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = lowerMinText,
                                onValueChange = { v ->
                                    lowerMinText = v
                                    val min = v.trim().toDoubleOrNull() ?: return@OutlinedTextField
                                    val max = lowerMaxText.trim().toDoubleOrNull() ?: return@OutlinedTextField
                                    if (!min.isFinite() || !max.isFinite() || max <= min) return@OutlinedTextField
                                    updateRange(
                                        upper = strat.upper,
                                        middle = strat.middle,
                                        lower = DepthRange(minM = min, maxM = max),
                                    )
                                },
                                label = { Text("下层 起(m)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = lowerMaxText,
                                onValueChange = { v ->
                                    lowerMaxText = v
                                    val min = lowerMinText.trim().toDoubleOrNull() ?: return@OutlinedTextField
                                    val max = v.trim().toDoubleOrNull() ?: return@OutlinedTextField
                                    if (!min.isFinite() || !max.isFinite() || max <= min) return@OutlinedTextField
                                    updateRange(
                                        upper = strat.upper,
                                        middle = strat.middle,
                                        lower = DepthRange(minM = min, maxM = max),
                                    )
                                },
                                label = { Text("下层 止(m)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("一键导入采样点（Excel）", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "读取 .xlsx 第1个工作表：第1列=采样点名称，第2列=浓缩体积(mL)，第3列=原水体积(L，空白则用默认值)。首行表头可有可无。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    Button(
                        enabled = !importBusy,
                        onClick = { importLauncher.launch(arrayOf(MIME_XLSX)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (importBusy) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                            Text("导入中…")
                        } else {
                            Text("选择 Excel 并导入")
                        }
                    }
                    if (importMessage != null) {
                        Text(
                            importMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("批量操作", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { bulkExpanded = !bulkExpanded }) { Text(if (bulkExpanded) "收起" else "展开") }
                    }

                    GlassContent(visible = !bulkExpanded) {
                        Text(
                            "展开后可批量复制/设置原水体积与浓缩体积。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                    }
                    
                    GlassContent(visible = bulkExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                             Text(
                                "提示：批量操作会直接修改当前数据集的采样点参数。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    enabled = bulkPoint != null,
                                    onClick = {
                                        val p = bulkPoint ?: return@OutlinedButton
                                        val vOrig = p.vOrigL
                                        val vConc = p.vConcMl
                                        viewModel.updateCurrentDataset { cur ->
                                            cur.copy(points = cur.points.map { p -> p.copy(vOrigL = vOrig, vConcMl = vConc) })
                                        }
                                        Toast.makeText(context, "已复制当前点参数到全部采样点", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Text("复制当前点到全部") }
                                OutlinedButton(
                                    onClick = {
                                        val v = settings.defaultVOrigL
                                        viewModel.updateCurrentDataset { cur -> cur.copy(points = cur.points.map { p -> p.copy(vOrigL = v) }) }
                                        Toast.makeText(context, "已将默认原水体积应用到全部点位", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Text("应用默认原水") }
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.updateCurrentDataset { cur -> cur.copy(points = cur.points.map { p -> p.copy(vConcMl = null) }) }
                                    Toast.makeText(context, "已清空全部浓缩体积", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("清空全部浓缩体积") }
                        }
                    }
                }
            }

            if (points.isEmpty()) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("当前没有采样点", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "提示：你可以先导入 Excel，或点击“新增一条”开始录入。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                        OutlinedButton(onClick = ::addPointAndGo, modifier = Modifier.fillMaxWidth()) { Text("新增一条") }
                    }
                }
            } else {
                val activePoint = bulkPoint ?: return@Column

                val (resolvedSite, resolvedDepth) = resolveSiteAndDepthForPoint(
                    label = activePoint.label,
                    site = activePoint.site,
                    depthM = activePoint.depthM,
                )

                var labelText by remember(activePoint.id) { mutableStateOf(activePoint.label) }
                var stationText by remember(activePoint.id, ds.stratification.enabled) { mutableStateOf(resolvedSite.orEmpty()) }
                var depthText by remember(activePoint.id, ds.stratification.enabled) { mutableStateOf(resolvedDepth?.let { formatDepthForLabel(it) }.orEmpty()) }
                var vOrigText by remember(activePoint.id) { mutableStateOf(activePoint.vOrigL.toString()) }
                var vConcText by remember(activePoint.id) { mutableStateOf(activePoint.vConcMl?.toString() ?: "") }
                var swapTargetText by rememberSaveable(ds.id) { mutableStateOf("") }
                var quickExpanded by rememberSaveable(ds.id) { mutableStateOf(false) }
                var multiDragEnabled by rememberSaveable(ds.id) { mutableStateOf(false) }
                var selectedPointIds by remember(ds.id) { mutableStateOf(setOf<Id>()) }
                val reorderListState = rememberLazyListState()
                val autoScrollThresholdPx = remember(density) { with(density) { 48.dp.toPx() } }
                val autoScrollStepPx = remember(density) { with(density) { 10.dp.toPx() } }
                val dragDropState = rememberDragDropListState(
                    reorderListState,
                    scope,
                    autoScrollThresholdPx,
                    autoScrollStepPx,
                ) { from, to ->
                    val fromPoint = points.getOrNull(from) ?: return@rememberDragDropListState
                    val selected = selectedPointIds
                    if (!multiDragEnabled || selected.isEmpty() || fromPoint.id !in selected || selected.size <= 1) {
                        movePoint(from, to)
                        return@rememberDragDropListState
                    }

                    val selectedIndices = points.indices.filter { points[it].id in selected }
                    if (to in selectedIndices) return@rememberDragDropListState

                    val remaining = points.filter { it.id !in selected }
                    val moving = selectedIndices.map { points[it] }
                    val beforeCount = selectedIndices.count { it < to }
                    val insertIndex = (to - beforeCount).coerceIn(0, remaining.size)
                    val nextList = remaining.toMutableList().apply { addAll(insertIndex, moving) }
                    viewModel.updateCurrentDataset { cur -> cur.copy(points = nextList) }
                }

                val selectedIndices = remember(points, selectedPointIds) {
                    points.indices.filter { points[it].id in selectedPointIds }
                }
                val groupDragActive = multiDragEnabled &&
                    selectedIndices.size > 1 &&
                    dragDropState.draggingItemIndex?.let { idx ->
                        points.getOrNull(idx)?.id in selectedPointIds
                    } == true
                val groupStartIndex = selectedIndices.firstOrNull() ?: -1
                val groupEndIndex = selectedIndices.lastOrNull() ?: -1

                dragDropState.resolveTargetIndex = { raw ->
                    if (!groupDragActive || selectedIndices.isEmpty()) {
                        raw
                    } else if (raw !in selectedIndices) {
                        raw
                    } else {
                        val directionDown = dragDropState.lastDragDelta >= 0f
                        if (directionDown) {
                            (groupEndIndex + 1..points.lastIndex).firstOrNull { it !in selectedIndices } ?: groupEndIndex
                        } else {
                            (groupStartIndex - 1 downTo 0).firstOrNull { it !in selectedIndices } ?: groupStartIndex
                        }
                    }
                }

                LaunchedEffect(activePoint.id) {
                    labelText = activePoint.label
                    val (s, d) = resolveSiteAndDepthForPoint(label = activePoint.label, site = activePoint.site, depthM = activePoint.depthM)
                    stationText = s.orEmpty()
                    depthText = d?.let { formatDepthForLabel(it) }.orEmpty()
                    vOrigText = activePoint.vOrigL.toString()
                    vConcText = activePoint.vConcMl?.toString() ?: ""
                }

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("第 ${safeIndex + 1} / ${points.size} 条", style = MaterialTheme.typography.titleMedium)
                            OutlinedButton(onClick = ::addPointAndGo) { Text("新增一条") }
                        }

                        if (!ds.stratification.enabled) {
                            OutlinedTextField(
                                value = labelText,
                                onValueChange = { v ->
                                    labelText = v
                                    val (s, d) = resolveSiteAndDepthForPoint(label = v, site = null, depthM = null)
                                    updatePoint(activePoint.id) { it.copy(label = v, site = s, depthM = d) }
                                },
                                label = { Text("名称") },
                                singleLine = true,
                            )
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = stationText,
                                    onValueChange = { v ->
                                        stationText = v
                                        val site = v.trim().ifBlank { "未命名" }
                                        val normalizedDepthText = depthText.trim().ifBlank { "0" }.also { if (depthText.trim().isEmpty()) depthText = it }
                                        val nextLabel = "$site-$normalizedDepthText"
                                        val nextDepth = normalizedDepthText.toDoubleOrNull()?.takeIf { it.isFinite() }
                                        labelText = nextLabel
                                        updatePoint(activePoint.id) { it.copy(label = nextLabel, site = site, depthM = nextDepth) }
                                    },
                                    label = { Text("点位") },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.45f),
                                )
                                OutlinedTextField(
                                    value = depthText,
                                    onValueChange = { v ->
                                        val normalized = v.trim().ifBlank { "0" }
                                        depthText = normalized
                                        val site = stationText.trim().ifBlank { "未命名" }
                                        val nextLabel = "$site-$normalized"
                                        val nextDepth = normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
                                        labelText = nextLabel
                                        updatePoint(activePoint.id) { it.copy(label = nextLabel, site = site, depthM = nextDepth) }
                                    },
                                    label = { Text("水深（m）") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal,
                                        imeAction = ImeAction.Next,
                                    ),
                                    modifier = Modifier.weight(0.55f),
                                )
                            }
                            Text(
                                "提示：分层模式下名称按“点位-水深(m)”组织（例如 1-0.3）。导出分层汇总表会按水深范围自动归类到上/中/下层。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            )
                        }

                        OutlinedTextField(
                            value = vOrigText,
                            onValueChange = { v ->
                                vOrigText = v
                                val n = v.trim().toDoubleOrNull()
                                if (n != null && n.isFinite() && n > 0) {
                                    updatePoint(activePoint.id) { it.copy(vOrigL = n) }
                                }
                            },
                            label = { Text("原水体积（L）") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next,
                            ),
                        )

                        OutlinedTextField(
                            value = vConcText,
                            onValueChange = { v ->
                                vConcText = v
                                val trimmed = v.trim()
                                if (trimmed.isEmpty()) {
                                    updatePoint(activePoint.id) { p -> p.copy(vConcMl = null) }
                                    return@OutlinedTextField
                                }
                                val n = trimmed.toDoubleOrNull()
                                if (n != null && n.isFinite() && n > 0) {
                                    updatePoint(activePoint.id) { p -> p.copy(vConcMl = n) }
                                }
                            },
                            label = { Text("浓缩体积（mL）") },
                            placeholder = { Text("例如：49") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { goNext(false) }),
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(enabled = safeIndex > 0, onClick = { moveActivePoint(-1) }) { Text("上移") }
                            OutlinedButton(enabled = safeIndex < points.size - 1, onClick = { moveActivePoint(1) }) { Text("下移") }
                            Spacer(Modifier.weight(1f))
                            OutlinedButton(onClick = ::deleteActivePoint) { Text("删除这条") }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = swapTargetText,
                                onValueChange = { swapTargetText = it },
                                label = { Text("交换到第几") },
                                placeholder = { Text("1-${points.size}") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(
                                enabled = points.size > 1,
                                onClick = {
                                    val target = swapTargetText.trim().toIntOrNull()
                                    if (target == null) {
                                        Toast.makeText(context, "请输入有效序号", Toast.LENGTH_SHORT).show()
                                        return@OutlinedButton
                                    }
                                    val toIndex = target - 1
                                    if (toIndex !in points.indices) {
                                        Toast.makeText(context, "序号超出范围（1-${points.size}）", Toast.LENGTH_SHORT).show()
                                        return@OutlinedButton
                                    }
                                    swapActivePointTo(toIndex)
                                    Toast.makeText(context, "已交换到第 $target 条", Toast.LENGTH_SHORT).show()
                                    swapTargetText = ""
                                },
                            ) { Text("交换") }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Spacer(Modifier.weight(1f))
                            OutlinedButton(enabled = safeIndex > 0, onClick = ::goPrev) { Text("上一个") }
                            Button(onClick = { goNext(false) }) { Text("下一条") }
                        }
                    }
                }

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("快速切换", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { quickExpanded = !quickExpanded }) {
                                Text(if (quickExpanded) "收起" else "展开")
                            }
                        }
                        if (!quickExpanded) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                for ((i, p) in points.withIndex()) {
                                    val label = p.label.ifBlank { "${i + 1}" }
                                    Surface(
                                        modifier = Modifier.clickable { activePointId = p.id },
                                        shape = MaterialTheme.shapes.large,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        color = MaterialTheme.colorScheme.surface,
                                    ) {
                                        Text(
                                            label,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                "提示：长按拖动点位排序，松手完成调整。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = multiDragEnabled,
                                        onCheckedChange = { checked ->
                                            multiDragEnabled = checked
                                            if (!checked) selectedPointIds = emptySet()
                                        },
                                    )
                                    Text("多选拖动", style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(
                                    enabled = selectedPointIds.isNotEmpty(),
                                    onClick = { selectedPointIds = emptySet() },
                                ) { Text("清空选择") }
                            }
                            LazyColumn(
                                state = reorderListState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 360.dp)
                                    .pointerInput(dragDropState) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { offset -> dragDropState.onDragStart(offset) },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragDropState.onDrag(dragAmount)
                                            },
                                            onDragEnd = { dragDropState.onDragEnd() },
                                            onDragCancel = { dragDropState.onDragCancel() },
                                        )
                                    },
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                itemsIndexed(points, key = { _, p -> p.id }) { index, p ->
                                    val isDragging = index == dragDropState.draggingItemIndex
                                    val isSelected = selectedPointIds.contains(p.id)
                                    val groupCollapsed = groupDragActive && isSelected && !isDragging
                                    val groupSize = selectedIndices.size
                                    val itemSizePx = dragDropState.draggingItemSize.toFloat()
                                    val groupDisplacementTarget = if (groupDragActive && itemSizePx > 0f) {
                                        val from = dragDropState.dragStartIndex ?: index
                                        val to = dragDropState.targetIndex ?: from
                                        when {
                                            isSelected -> dragDropState.draggingItemOffset + (from - index) * itemSizePx
                                            to > groupEndIndex && index in (groupEndIndex + 1)..to -> -itemSizePx * groupSize
                                            to < groupStartIndex && index in to until groupStartIndex -> itemSizePx * groupSize
                                            else -> 0f
                                        }
                                    } else {
                                        dragDropState.itemDisplacement(index)
                                    }
                                    val scale by animateFloatAsState(
                                        targetValue = when {
                                            isDragging -> 1.03f
                                            groupCollapsed -> 0.98f
                                            else -> 1f
                                        },
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        label = "pointDragScale",
                                    )
                                    val itemAlpha by animateFloatAsState(
                                        targetValue = if (groupCollapsed) 0.45f else 1f,
                                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                                        label = "pointDragAlpha",
                                    )
                                    val elevation by animateDpAsState(
                                        targetValue = if (isDragging) 8.dp else 0.dp,
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        label = "pointDragElevation",
                                    )
                                    val displacement by animateFloatAsState(
                                        targetValue = groupDisplacementTarget,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow,
                                        ),
                                        label = "pointDragDisplace",
                                    )
                                    val bg = when {
                                        isDragging -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                        multiDragEnabled && isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                        p.id == activePointId -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else -> MaterialTheme.colorScheme.surface
                                    }
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .graphicsLayer {
                                                translationY = displacement
                                                scaleX = scale
                                                scaleY = scale
                                                alpha = itemAlpha
                                            }
                                            .zIndex(if (isDragging) 1f else 0f)
                                            .clickable { activePointId = p.id },
                                        shape = MaterialTheme.shapes.large,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        color = bg,
                                        shadowElevation = elevation,
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            val label = p.label.ifBlank { "${index + 1}" }
                                            Text(label, style = MaterialTheme.typography.bodyMedium)
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                if (groupDragActive && isDragging) {
                                                    Surface(
                                                        shape = MaterialTheme.shapes.small,
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                    ) {
                                                        Text(
                                                            "已选${groupSize}条",
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.primary,
                                                        )
                                                    }
                                                }
                                                if (multiDragEnabled) {
                                                    Checkbox(
                                                        checked = selectedPointIds.contains(p.id),
                                                        onCheckedChange = { checked ->
                                                            selectedPointIds = if (checked) {
                                                                selectedPointIds + p.id
                                                            } else {
                                                                selectedPointIds - p.id
                                                            }
                                                        },
                                                    )
                                                }
                                                Text(
                                                    "第 ${index + 1} 条",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
