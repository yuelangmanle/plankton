package com.plankton.one102.ui.screens

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plankton.one102.data.log.AppLogger
import com.plankton.one102.domain.LVL1_ORDER
import com.plankton.one102.domain.SpeciesDbItem
import com.plankton.one102.domain.normalizeLvl1Name
import com.plankton.one102.export.writeFileToSafAtomic
import com.plankton.one102.ui.DatabaseViewModel
import com.plankton.one102.ui.components.GlassBackground
import com.plankton.one102.ui.components.LocalGlassPrefs
import com.plankton.one102.ui.theme.GlassWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.Collator
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MIME_PNG = "image/png"
private const val MIME_SVG = "image/svg+xml"
private const val MIN_VIEW_SCALE = 0.08f
private const val MAX_VIEW_SCALE = 6f

private enum class ExportScopeLevel(val label: String) {
    All("全部"),
    Lvl1("大类"),
    Lvl2("纲"),
    Lvl3("目"),
    Lvl4("科"),
    Lvl5("属"),
    Species("种"),
}

private data class MindMapNode(
    val id: String,
    val label: String,
    val depth: Int,
    val groupKey: String,
    val children: MutableList<MindMapNode> = mutableListOf(),
)

private data class NodeBox(
    val id: String,
    val label: String,
    val depth: Int,
    val groupKey: String,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val baselineY: Float,
)

private data class Edge(
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    val groupKey: String,
)

private data class MindMapLayout(
    val nodes: List<NodeBox>,
    val edges: List<Edge>,
    val width: Float,
    val height: Float,
)

private data class MindMapRender(
    val picture: Picture,
    val widthPx: Int,
    val heightPx: Int,
)

private enum class ExportFormat { Png, Svg }

private data class SingleExportRequest(
    val format: ExportFormat,
    val scopeLevel: ExportScopeLevel,
    val scopeValue: String?,
    val pngMaxDim: Int,
)

private data class BatchExportRequest(
    val scopeLevel: ExportScopeLevel,
    val scopeValues: List<String>?,
    val pngMaxDim: Int,
    val exportPng: Boolean,
    val exportSvg: Boolean,
)

private data class MindMapStyle(
    val nodePaddingX: Float,
    val nodePaddingY: Float,
    val nodeCornerRadius: Float,
    val nodeStrokeWidth: Float,
    val edgeStrokeWidth: Float,
    val hGap: Float,
    val vGap: Float,
    val textPaint: Paint,
    val nodeFillPaint: Paint,
    val nodeStrokePaint: Paint,
    val edgePaint: Paint,
)

private val collator: Collator = Collator.getInstance(Locale.CHINA)

private fun safeFileStem(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return "思维导图"
    return trimmed.replace(Regex("[\\\\/:*?\"<>|]"), "_")
}

private fun titleForScope(level: ExportScopeLevel, value: String?): String {
    return when (level) {
        ExportScopeLevel.All -> "浮游动物分类"
        else -> "${level.label}：${value.orEmpty().ifBlank { "（未命名）" }}"
    }
}

private fun scopeValueOf(item: SpeciesDbItem, level: ExportScopeLevel): String? {
    val t = item.taxonomy
    return when (level) {
        ExportScopeLevel.All -> null
        ExportScopeLevel.Lvl1 -> groupLabelFor(item).trim().ifBlank { "（未分类）" }
        ExportScopeLevel.Lvl2 -> t?.lvl2?.trim().takeIf { !it.isNullOrBlank() }
        ExportScopeLevel.Lvl3 -> t?.lvl3?.trim().takeIf { !it.isNullOrBlank() }
        ExportScopeLevel.Lvl4 -> t?.lvl4?.trim().takeIf { !it.isNullOrBlank() }
        ExportScopeLevel.Lvl5 -> t?.lvl5?.trim().takeIf { !it.isNullOrBlank() }
        ExportScopeLevel.Species -> item.nameCn.trim().ifBlank { null }
    }
}

private fun listScopeValues(items: List<SpeciesDbItem>, level: ExportScopeLevel): List<String> {
    if (level == ExportScopeLevel.All) return emptyList()
    val set = LinkedHashSet<String>()
    for (it in items) {
        val v = scopeValueOf(it, level) ?: continue
        set.add(v)
    }
    val raw = set.toList()
    return when (level) {
        ExportScopeLevel.Lvl1 -> {
            val order = LVL1_ORDER + listOf("（未分类）")
            val ordered = order.filter { it in set }
            val rest = raw.filterNot { it in order }.sortedWith(collator)
            ordered + rest
        }

        else -> raw.sortedWith(collator)
    }
}

private fun filterByScope(items: List<SpeciesDbItem>, level: ExportScopeLevel, value: String?): List<SpeciesDbItem> {
    if (level == ExportScopeLevel.All) return items
    val v = value?.trim().orEmpty()
    if (v.isBlank()) return emptyList()
    return items.filter { item ->
        when (level) {
            ExportScopeLevel.All -> true
            ExportScopeLevel.Lvl1 -> groupLabelFor(item) == v
            ExportScopeLevel.Lvl2 -> item.taxonomy?.lvl2?.trim() == v
            ExportScopeLevel.Lvl3 -> item.taxonomy?.lvl3?.trim() == v
            ExportScopeLevel.Lvl4 -> item.taxonomy?.lvl4?.trim() == v
            ExportScopeLevel.Lvl5 -> item.taxonomy?.lvl5?.trim() == v
            ExportScopeLevel.Species -> item.nameCn.trim() == v
        }
    }
}

private fun itemSegments(item: SpeciesDbItem): List<String> {
    val t = item.taxonomy
    val lvl1 = normalizeLvl1Name(t?.lvl1.orEmpty()).ifBlank { "（未分类）" }
    val lvl2 = t?.lvl2?.trim().takeIf { !it.isNullOrBlank() }
    val lvl3 = t?.lvl3?.trim().takeIf { !it.isNullOrBlank() }
    val lvl4 = t?.lvl4?.trim().takeIf { !it.isNullOrBlank() }
    val lvl5 = t?.lvl5?.trim().takeIf { !it.isNullOrBlank() }
    val leaf = item.nameCn.trim().ifBlank { "（未命名物种）" }
    return listOfNotNull(lvl1, lvl2, lvl3, lvl4, lvl5, leaf)
}

private fun groupLabelFor(item: SpeciesDbItem): String {
    val lvl1 = normalizeLvl1Name(item.taxonomy?.lvl1.orEmpty()).ifBlank { "（未分类）" }
    return if (lvl1 in LVL1_ORDER) lvl1 else "（未分类）"
}

private fun colorForGroup(group: String): Int {
    return when (normalizeLvl1Name(group)) {
        "原生动物" -> Color.parseColor("#EF5350")
        "轮虫类" -> Color.parseColor("#42A5F5")
        "枝角类" -> Color.parseColor("#66BB6A")
        "桡足类" -> Color.parseColor("#FFA726")
        else -> Color.parseColor("#90A4AE")
    }
}

private fun buildTree(items: List<SpeciesDbItem>, title: String = "浮游动物分类"): MindMapNode {
    val root = MindMapNode(id = "root", label = title, depth = 0, groupKey = "root")
    val nodeById = LinkedHashMap<String, MindMapNode>().apply { put(root.id, root) }

    fun getOrCreateChild(parent: MindMapNode, label: String, depth: Int, groupKey: String): MindMapNode {
        val id = parent.id + ">" + label
        return nodeById[id] ?: MindMapNode(id = id, label = label, depth = depth, groupKey = groupKey).also { node ->
            nodeById[id] = node
            parent.children.add(node)
        }
    }

    for (it in items) {
        val segs = itemSegments(it)
        if (segs.isEmpty()) continue
        val group = groupLabelFor(it)
        var cur = root
        for ((i, seg) in segs.withIndex()) {
            val depth = i + 1
            val gk = if (depth == 1) seg else group
            cur = getOrCreateChild(cur, seg, depth, gk)
        }
    }

    fun sortChildren(node: MindMapNode) {
        if (node.children.isEmpty()) return
        node.children.sortWith { a, b ->
            if (node.id == "root") {
                val order = (LVL1_ORDER + listOf("（未分类）"))
                val ia = order.indexOf(a.label).let { if (it >= 0) it else Int.MAX_VALUE }
                val ib = order.indexOf(b.label).let { if (it >= 0) it else Int.MAX_VALUE }
                if (ia != ib) return@sortWith ia - ib
            }
            collator.compare(a.label, b.label)
        }
        node.children.forEach(::sortChildren)
    }
    sortChildren(root)
    return root
}

private fun buildLayout(root: MindMapNode, style: MindMapStyle): MindMapLayout {
    val nodes = ArrayList<NodeBox>()
    val edges = ArrayList<Edge>()

    // Collect nodes by depth to compute per-depth x offsets based on max width.
    val allNodes = ArrayList<MindMapNode>()
    fun collect(n: MindMapNode) {
        allNodes.add(n)
        n.children.forEach(::collect)
    }
    collect(root)

    val widthByDepth = LinkedHashMap<Int, Float>()
    val heightByDepth = LinkedHashMap<Int, Float>()
    val metrics = style.textPaint.fontMetrics
    val textHeight = (metrics.descent - metrics.ascent).coerceAtLeast(1f)

    fun boxSize(label: String): Pair<Float, Float> {
        val w = style.textPaint.measureText(label).coerceAtLeast(1f) + style.nodePaddingX * 2
        val h = textHeight + style.nodePaddingY * 2
        return w to h
    }

    for (n in allNodes) {
        val (w, h) = boxSize(n.label)
        val curW = widthByDepth[n.depth] ?: 0f
        widthByDepth[n.depth] = max(curW, w)
        val curH = heightByDepth[n.depth] ?: 0f
        heightByDepth[n.depth] = max(curH, h)
    }

    val depths = widthByDepth.keys.sorted()
    val xByDepth = LinkedHashMap<Int, Float>()
    var xCursor = 0f
    for (d in depths) {
        xByDepth[d] = xCursor
        xCursor += (widthByDepth[d] ?: 0f) + style.hGap
    }

    var yCursor = 0f
    val centerYById = HashMap<String, Float>()
    val boxById = HashMap<String, NodeBox>()

    fun layout(n: MindMapNode) {
        val (w, h) = boxSize(n.label)
        val nodeH = (heightByDepth[n.depth] ?: h)
        if (n.children.isEmpty()) {
            val cy = yCursor + nodeH / 2f
            centerYById[n.id] = cy
            yCursor += nodeH + style.vGap
        } else {
            n.children.forEach(::layout)
            val first = centerYById.getValue(n.children.first().id)
            val last = centerYById.getValue(n.children.last().id)
            centerYById[n.id] = (first + last) / 2f
        }

        val cx = xByDepth[n.depth] ?: 0f
        val cy = centerYById.getValue(n.id)
        val left = cx
        val top = cy - nodeH / 2f
        val textBaseline = top + style.nodePaddingY - metrics.ascent + (nodeH - (textHeight + style.nodePaddingY * 2)) / 2f
        val box = NodeBox(
            id = n.id,
            label = n.label,
            depth = n.depth,
            groupKey = n.groupKey,
            left = left,
            top = top,
            width = w,
            height = nodeH,
            baselineY = textBaseline,
        )
        boxById[n.id] = box
    }
    layout(root)

    // Now build NodeBox list + edges.
    for (n in allNodes) {
        val box = boxById.getValue(n.id)
        nodes.add(box)
        for (c in n.children) {
            val cb = boxById.getValue(c.id)
            val fromX = box.left + box.width
            val fromY = box.top + box.height / 2f
            val toX = cb.left
            val toY = cb.top + cb.height / 2f
            edges.add(Edge(fromX, fromY, toX, toY, groupKey = c.groupKey))
        }
    }

    val maxRight = nodes.maxOfOrNull { it.left + it.width } ?: 0f
    val maxBottom = nodes.maxOfOrNull { it.top + it.height } ?: 0f
    return MindMapLayout(
        nodes = nodes,
        edges = edges,
        width = maxRight,
        height = maxBottom,
    )
}

private fun renderToCanvas(canvas: Canvas, layout: MindMapLayout, style: MindMapStyle, backgroundColor: Int) {
    canvas.drawColor(backgroundColor)

    // Edges first
    for (e in layout.edges) {
        val color = colorForGroup(e.groupKey)
        style.edgePaint.color = color
        style.edgePaint.alpha = 190
        val path = Path()
        val midX = (e.fromX + e.toX) / 2f
        path.moveTo(e.fromX, e.fromY)
        path.cubicTo(midX, e.fromY, midX, e.toY, e.toX, e.toY)
        canvas.drawPath(path, style.edgePaint)
    }

    // Nodes
    for (n in layout.nodes) {
        val isRoot = n.id == "root"
        val strokeColor = if (isRoot) Color.parseColor("#263238") else colorForGroup(n.groupKey)
        val fillColor = if (isRoot) Color.parseColor("#263238") else Color.WHITE
        val textColor = if (isRoot) Color.WHITE else Color.BLACK

        style.nodeFillPaint.color = fillColor
        style.nodeStrokePaint.color = strokeColor
        style.textPaint.color = textColor

        val rect = RectF(n.left, n.top, n.left + n.width, n.top + n.height)
        canvas.drawRoundRect(rect, style.nodeCornerRadius, style.nodeCornerRadius, style.nodeFillPaint)
        canvas.drawRoundRect(rect, style.nodeCornerRadius, style.nodeCornerRadius, style.nodeStrokePaint)

        val textX = n.left + style.nodePaddingX
        canvas.drawText(n.label, textX, n.baselineY, style.textPaint)
    }
}

private fun buildPicture(layout: MindMapLayout, style: MindMapStyle): MindMapRender {
    val w = ceil(layout.width.toDouble()).toInt().coerceAtLeast(1)
    val h = ceil(layout.height.toDouble()).toInt().coerceAtLeast(1)
    val picture = Picture()
    val c = picture.beginRecording(w, h)
    renderToCanvas(c, layout, style, backgroundColor = Color.WHITE)
    picture.endRecording()
    return MindMapRender(picture = picture, widthPx = w, heightPx = h)
}

private fun escapeXml(s: String): String {
    return s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

private fun colorHex(color: Int): String = String.format("#%06X", 0xFFFFFF and color)

private fun toSvg(layout: MindMapLayout, style: MindMapStyle, padding: Float = 24f): ByteArray {
    val w = layout.width + padding * 2
    val h = layout.height + padding * 2
    val fontSize = style.textPaint.textSize
    val rx = style.nodeCornerRadius
    val swNode = style.nodeStrokeWidth
    val swEdge = style.edgeStrokeWidth

    val sb = StringBuilder(64_000)
    sb.append("""<?xml version="1.0" encoding="UTF-8"?>\n""")
    sb.append("""<svg xmlns="http://www.w3.org/2000/svg" width="${w.roundToInt()}" height="${h.roundToInt()}" viewBox="0 0 $w $h">""")
    sb.append("\n")
    sb.append("""  <rect x="0" y="0" width="100%" height="100%" fill="#FFFFFF" />\n""")

    for (e in layout.edges) {
        val color = colorHex(colorForGroup(e.groupKey))
        val midX = (e.fromX + e.toX) / 2f + padding
        val fromX = e.fromX + padding
        val toX = e.toX + padding
        val fromY = e.fromY + padding
        val toY = e.toY + padding
        val d = "M $fromX $fromY C $midX $fromY, $midX $toY, $toX $toY"
        sb.append("""  <path d="$d" fill="none" stroke="$color" stroke-width="$swEdge" stroke-linecap="round" opacity="0.75" />\n""")
    }

    for (n in layout.nodes) {
        val isRoot = n.id == "root"
        val stroke = colorHex(if (isRoot) Color.parseColor("#263238") else colorForGroup(n.groupKey))
        val fill = colorHex(if (isRoot) Color.parseColor("#263238") else Color.WHITE)
        val textColor = colorHex(if (isRoot) Color.WHITE else Color.BLACK)
        val x = n.left + padding
        val y = n.top + padding

        sb.append(
            """  <rect x="$x" y="$y" width="${n.width}" height="${n.height}" rx="$rx" ry="$rx" fill="$fill" stroke="$stroke" stroke-width="$swNode" />\n""",
        )
        val textX = x + style.nodePaddingX
        val textY = n.baselineY + padding
        sb.append(
            """  <text x="$textX" y="$textY" font-size="$fontSize" font-family="sans-serif" fill="$textColor">${escapeXml(n.label)}</text>\n""",
        )
    }

    sb.append("</svg>\n")
    return sb.toString().toByteArray(Charsets.UTF_8)
}

private fun toPngWithMaxDim(layout: MindMapLayout, style: MindMapStyle, maxDim: Int, padding: Float = 24f): ByteArray {
    val baseW = layout.width + padding * 2
    val baseH = layout.height + padding * 2
    val safeMaxDim = maxDim.coerceAtLeast(1)
    val scale = safeMaxDim.toFloat() / max(baseW, baseH).coerceAtLeast(1f)
    val outW = (baseW * scale).roundToInt().coerceAtLeast(1)
    val outH = (baseH * scale).roundToInt().coerceAtLeast(1)

    val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.save()
    canvas.scale(scale, scale)
    canvas.translate(padding, padding)
    renderToCanvas(canvas, layout, style, backgroundColor = Color.WHITE)
    canvas.restore()

    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
    bmp.recycle()
    return out.toByteArray()
}

private fun toPngBestEffort(layout: MindMapLayout, style: MindMapStyle, requestedMaxDim: Int, padding: Float = 24f): ByteArray {
    val presets = listOf(20480, 16384, 12288, 8192, 6144, 4096)
    val candidates = when {
        requestedMaxDim <= 0 -> presets
        else -> presets.filter { it <= requestedMaxDim }.ifEmpty { listOf(requestedMaxDim) }
    }

    var last: Throwable? = null
    for (dim in candidates.distinct()) {
        try {
            return toPngWithMaxDim(layout, style, maxDim = dim, padding = padding)
        } catch (oom: OutOfMemoryError) {
            last = oom
        }
    }
    throw IllegalStateException("导出 PNG 失败：内存不足，请降低分辨率或改导出 SVG", last)
}

private suspend fun writeBytes(context: Context, uri: Uri, bytes: ByteArray) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val tempFile = File.createTempFile("mindmap-", ".tmp", dir)
    try {
        withContext(Dispatchers.IO) {
            tempFile.outputStream().use { out ->
                out.write(bytes)
                out.flush()
            }
        }
        writeFileToSafAtomic(context, uri, tempFile)
    } finally {
        runCatching { tempFile.delete() }
    }
}

private fun treeToDocumentUri(treeUri: Uri): Uri {
    val docId = DocumentsContract.getTreeDocumentId(treeUri)
    return DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
}

private fun withSuffixBeforeExt(fileName: String, suffix: String): String {
    val dot = fileName.lastIndexOf('.')
    if (dot <= 0 || dot == fileName.length - 1) return "$fileName$suffix"
    val stem = fileName.substring(0, dot)
    val ext = fileName.substring(dot)
    return "$stem$suffix$ext"
}

private suspend fun createUniqueDocument(
    contentResolver: ContentResolver,
    parentDocumentUri: Uri,
    mime: String,
    displayName: String,
): Uri = withContext(Dispatchers.IO) {
    var lastErr: Throwable? = null
    for (i in 0..30) {
        val name = if (i == 0) displayName else withSuffixBeforeExt(displayName, "(${i + 1})")
        val uri = runCatching { DocumentsContract.createDocument(contentResolver, parentDocumentUri, mime, name) }.getOrElse {
            lastErr = it
            null
        }
        if (uri != null) return@withContext uri
    }
    throw (lastErr ?: IllegalStateException("无法创建导出文件：$displayName"))
}

private suspend fun exportOne(
    context: Context,
    uri: Uri,
    items: List<SpeciesDbItem>,
    style: MindMapStyle,
    scopeLevel: ExportScopeLevel,
    scopeValue: String?,
    pngMaxDim: Int,
    format: ExportFormat,
) {
    val scoped = filterByScope(items, scopeLevel, scopeValue)
    if (scoped.isEmpty()) error("当前范围下没有可导出的条目")
    val title = titleForScope(scopeLevel, scopeValue)
    val layout = withContext(Dispatchers.Default) { buildLayout(buildTree(scoped, title), style) }
    val bytes = withContext(Dispatchers.Default) {
        when (format) {
            ExportFormat.Png -> toPngBestEffort(layout, style, requestedMaxDim = pngMaxDim)
            ExportFormat.Svg -> toSvg(layout, style)
        }
    }
    writeBytes(context, uri, bytes)
}

private suspend fun exportBatch(
    context: Context,
    contentResolver: ContentResolver,
    treeUri: Uri,
    items: List<SpeciesDbItem>,
    style: MindMapStyle,
    scopeLevel: ExportScopeLevel,
    scopeValues: List<String>?,
    pngMaxDim: Int,
    exportPng: Boolean,
    exportSvg: Boolean,
    onProgress: (String) -> Unit,
): Int {
    if (!exportPng && !exportSvg) return 0

    val parentDoc = treeToDocumentUri(treeUri)
    val values: List<String?> = when {
        scopeLevel == ExportScopeLevel.All -> listOf(null)
        !scopeValues.isNullOrEmpty() -> scopeValues
        else -> listScopeValues(items, scopeLevel)
    }
    val total = values.size.coerceAtLeast(1)
    var done = 0
    var exported = 0

    for (v in values) {
        done += 1
        val label = when {
            scopeLevel == ExportScopeLevel.All -> "全部"
            else -> v.orEmpty()
        }.ifBlank { "（未命名）" }
        onProgress("生成：${scopeLevel.label} $label（$done/$total）")

        val scoped = filterByScope(items, scopeLevel, v)
        if (scoped.isEmpty()) continue

        val title = titleForScope(scopeLevel, v)
        val layout = withContext(Dispatchers.Default) { buildLayout(buildTree(scoped, title), style) }

        val baseName = safeFileStem("思维导图-${scopeLevel.label}-${label}")

        if (exportPng) {
            val uri = createUniqueDocument(contentResolver, parentDoc, MIME_PNG, "$baseName.png")
            val bytes = withContext(Dispatchers.Default) { toPngBestEffort(layout, style, requestedMaxDim = pngMaxDim) }
            writeBytes(context, uri, bytes)
        }
        if (exportSvg) {
            val uri = createUniqueDocument(contentResolver, parentDoc, MIME_SVG, "$baseName.svg")
            val bytes = withContext(Dispatchers.Default) { toSvg(layout, style) }
            writeBytes(context, uri, bytes)
        }
        exported += 1
    }

    return exported
}

@Composable
fun DatabaseMindMapScreen(
    dbViewModel: DatabaseViewModel,
    padding: PaddingValues,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val contentResolver = context.contentResolver
    val items by dbViewModel.items.collectAsStateWithLifecycle()

    val glassPrefs = LocalGlassPrefs.current
    val dialogColor = if (glassPrefs.enabled) GlassWhite else MaterialTheme.colorScheme.surface
    val dialogShape = RoundedCornerShape(24.dp)

    val groups = remember {
        listOf("全部") + LVL1_ORDER + listOf("（未分类）")
    }
    var groupFilter by rememberSaveable { mutableStateOf("全部") }
    if (groupFilter !in groups) groupFilter = "全部"

    val filtered = remember(items, groupFilter) {
        if (groupFilter == "全部") items
        else items.filter { groupLabelFor(it) == groupFilter }
    }

    val density = LocalDensity.current
    val style = remember(density.density, density.fontScale) {
        val textSizePx = with(density) { 14.sp.toPx() }
        val padX = with(density) { 10.dp.toPx() }
        val padY = with(density) { 6.dp.toPx() }
        val corner = with(density) { 12.dp.toPx() }
        val stroke = with(density) { 1.5.dp.toPx() }
        val edgeStroke = with(density) { 3.dp.toPx() }
        val hGap = with(density) { 36.dp.toPx() }
        val vGap = with(density) { 14.dp.toPx() }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizePx
            color = Color.BLACK
        }
        MindMapStyle(
            nodePaddingX = padX,
            nodePaddingY = padY,
            nodeCornerRadius = corner,
            nodeStrokeWidth = stroke,
            edgeStrokeWidth = edgeStroke,
            hGap = hGap,
            vGap = vGap,
            textPaint = textPaint,
            nodeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL },
            nodeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
            },
            edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = edgeStroke
            },
        )
    }

    val layout = remember(filtered, style) {
        val root = buildTree(filtered)
        buildLayout(root, style)
    }

    var busy by remember { mutableStateOf(false) }

    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable { mutableFloatStateOf(0f) }
    var viewportW by remember { mutableFloatStateOf(0f) }
    var viewportH by remember { mutableFloatStateOf(0f) }
    var viewAnimJob by remember { mutableStateOf<Job?>(null) }

    fun clampView() {
        if (viewportW <= 0f || viewportH <= 0f) return

        val margin = with(density) { 32.dp.toPx() }
        val scaledW = layout.width * scale
        val scaledH = layout.height * scale

        offsetX = if (scaledW + margin * 2 <= viewportW) {
            (viewportW - scaledW) / 2f
        } else {
            val minX = viewportW - scaledW - margin
            val maxX = margin
            offsetX.coerceIn(minX, maxX)
        }

        offsetY = if (scaledH + margin * 2 <= viewportH) {
            (viewportH - scaledH) / 2f
        } else {
            val minY = viewportH - scaledH - margin
            val maxY = margin
            offsetY.coerceIn(minY, maxY)
        }
    }

    fun computeFit(): Triple<Float, Float, Float>? {
        if (viewportW <= 0f || viewportH <= 0f) return null
        val s = (min(
            viewportW / (layout.width.coerceAtLeast(1f)),
            viewportH / (layout.height.coerceAtLeast(1f)),
        ) * 0.95f).coerceIn(MIN_VIEW_SCALE, 2.5f)
        val x = (viewportW - layout.width * s) / 2f
        val y = (viewportH - layout.height * s) / 2f
        return Triple(s, x, y)
    }

    fun animateViewTo(target: Triple<Float, Float, Float>) {
        viewAnimJob?.cancel()
        val (targetScale, targetX, targetY) = target
        viewAnimJob = scope.launch {
            val startScale = scale
            val startX = offsetX
            val startY = offsetY
            val spec = tween<Float>(durationMillis = 220, easing = FastOutSlowInEasing)
            coroutineScope {
                launch { animate(startScale, targetScale, animationSpec = spec) { v, _ -> scale = v } }
                launch { animate(startX, targetX, animationSpec = spec) { v, _ -> offsetX = v } }
                launch { animate(startY, targetY, animationSpec = spec) { v, _ -> offsetY = v } }
            }
            clampView()
        }
    }

    fun fitToScreen(animated: Boolean) {
        val target = computeFit() ?: return
        if (animated) animateViewTo(target)
        else {
            viewAnimJob?.cancel()
            viewAnimJob = null
            scale = target.first
            offsetX = target.second
            offsetY = target.third
            clampView()
        }
    }

    LaunchedEffect(layout.width, layout.height, viewportW, viewportH) {
        fitToScreen(animated = false)
    }

    val render = remember(layout, style) { buildPicture(layout, style) }

    var exportDialogOpen by rememberSaveable { mutableStateOf(false) }
    var exportScopeLevelName by rememberSaveable { mutableStateOf(ExportScopeLevel.All.name) }
    var exportScopeValues by rememberSaveable { mutableStateOf(listOf<String>()) }
    var exportValueQuery by rememberSaveable { mutableStateOf("") }
    var exportPngMaxDim by rememberSaveable { mutableIntStateOf(0) }
    var exportProgress by remember { mutableStateOf<String?>(null) }
    val mindMapExportRequest by dbViewModel.mindMapExportRequest.collectAsStateWithLifecycle()

    var pendingSingle by remember { mutableStateOf<SingleExportRequest?>(null) }
    var pendingBatch by remember { mutableStateOf<BatchExportRequest?>(null) }

    LaunchedEffect(mindMapExportRequest) {
        val req = mindMapExportRequest ?: return@LaunchedEffect
        val level = runCatching { ExportScopeLevel.valueOf(req.scopeLevel) }.getOrElse { ExportScopeLevel.All }
        exportScopeLevelName = level.name
        exportScopeValues = req.scopeValue?.let { listOf(it) } ?: emptyList()
        exportValueQuery = req.scopeValue.orEmpty()
        if (level == ExportScopeLevel.Lvl1 && !req.scopeValue.isNullOrBlank()) {
            groupFilter = req.scopeValue
        } else if (level == ExportScopeLevel.All) {
            groupFilter = "全部"
        }
        exportDialogOpen = true
        dbViewModel.clearMindMapExportRequest()
    }

    val exportSinglePngLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_PNG)) { uri ->
        val req = pendingSingle
        pendingSingle = null
        if (uri == null || req == null) return@rememberLauncherForActivityResult
        busy = true
        exportProgress = "导出中…"
        scope.launch {
            val res = runCatching {
                exportOne(
                    context = context,
                    uri = uri,
                    items = items,
                    style = style,
                    scopeLevel = req.scopeLevel,
                    scopeValue = req.scopeValue,
                    pngMaxDim = req.pngMaxDim,
                    format = req.format,
                )
            }
            res.exceptionOrNull()?.let { AppLogger.logError(context, "MindMapExport", "导出 PNG 失败", it) }
            Toast.makeText(context, res.fold(onSuccess = { "已导出 PNG" }, onFailure = { "导出失败：${it.message}" }), Toast.LENGTH_LONG).show()
            exportProgress = null
            busy = false
        }
    }

    val exportSingleSvgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_SVG)) { uri ->
        val req = pendingSingle
        pendingSingle = null
        if (uri == null || req == null) return@rememberLauncherForActivityResult
        busy = true
        exportProgress = "导出中…"
        scope.launch {
            val res = runCatching {
                exportOne(
                    context = context,
                    uri = uri,
                    items = items,
                    style = style,
                    scopeLevel = req.scopeLevel,
                    scopeValue = req.scopeValue,
                    pngMaxDim = req.pngMaxDim,
                    format = req.format,
                )
            }
            res.exceptionOrNull()?.let { AppLogger.logError(context, "MindMapExport", "导出 SVG 失败", it) }
            Toast.makeText(context, res.fold(onSuccess = { "已导出 SVG" }, onFailure = { "导出失败：${it.message}" }), Toast.LENGTH_LONG).show()
            exportProgress = null
            busy = false
        }
    }

    val exportBatchDirLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val req = pendingBatch
        pendingBatch = null
        if (uri == null || req == null) return@rememberLauncherForActivityResult
        busy = true
        exportProgress = "准备导出…"
        scope.launch {
            val res = runCatching {
                exportBatch(
                    context = context,
                    contentResolver = contentResolver,
                    treeUri = uri,
                    items = items,
                    style = style,
                    scopeLevel = req.scopeLevel,
                    scopeValues = req.scopeValues,
                    pngMaxDim = req.pngMaxDim,
                    exportPng = req.exportPng,
                    exportSvg = req.exportSvg,
                    onProgress = { exportProgress = it },
                )
            }
            res.exceptionOrNull()?.let { AppLogger.logError(context, "MindMapExport", "批量导出失败", it) }
            Toast.makeText(
                context,
                res.fold(onSuccess = { "已批量导出：$it 份" }, onFailure = { "批量导出失败：${it.message}" }),
                Toast.LENGTH_LONG,
            ).show()
            exportProgress = null
            busy = false
        }
    }

    fun startSingleExport(format: ExportFormat) {
        val level = runCatching { ExportScopeLevel.valueOf(exportScopeLevelName) }.getOrElse { ExportScopeLevel.All }
        val selectedValues = exportScopeValues.map { it.trim() }.filter { it.isNotBlank() }
        val value = if (level == ExportScopeLevel.All) null else selectedValues.singleOrNull()
        if (level != ExportScopeLevel.All && value.isNullOrBlank()) {
            val msg = if (selectedValues.isEmpty()) {
                "请先选择要导出的${level.label}"
            } else {
                "单张导出仅支持选择 1 个${level.label}"
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            return
        }
        pendingSingle = SingleExportRequest(
            format = format,
            scopeLevel = level,
            scopeValue = value,
            pngMaxDim = exportPngMaxDim,
        )

        val label = if (level == ExportScopeLevel.All) "全部" else value.orEmpty()
        val base = if (level == ExportScopeLevel.All) "数据库-思维导图-全部" else "数据库-思维导图-${level.label}-$label"
        val fileName = safeFileStem(base) + when (format) {
            ExportFormat.Png -> ".png"
            ExportFormat.Svg -> ".svg"
        }
        when (format) {
            ExportFormat.Png -> exportSinglePngLauncher.launch(fileName)
            ExportFormat.Svg -> exportSingleSvgLauncher.launch(fileName)
        }
    }

    fun startBatchExport(exportPng: Boolean, exportSvg: Boolean) {
        val level = runCatching { ExportScopeLevel.valueOf(exportScopeLevelName) }.getOrElse { ExportScopeLevel.All }
        val selectedValues = exportScopeValues.map { it.trim() }.filter { it.isNotBlank() }
        val count = when {
            level == ExportScopeLevel.All -> 1
            selectedValues.isNotEmpty() -> selectedValues.size
            else -> listScopeValues(items, level).size
        }
        if (count <= 0) {
            Toast.makeText(context, "当前维度没有可导出的条目", Toast.LENGTH_SHORT).show()
            return
        }
        pendingBatch = BatchExportRequest(
            scopeLevel = level,
            scopeValues = if (selectedValues.isNotEmpty()) selectedValues else null,
            pngMaxDim = exportPngMaxDim,
            exportPng = exportPng,
            exportSvg = exportSvg,
        )
        exportBatchDirLauncher.launch(null)
    }

    val gestureModifier = remember(layout.width, layout.height, viewportW, viewportH, density.density) {
        Modifier
            .pointerInput(layout.width, layout.height, viewportW, viewportH) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    viewAnimJob?.cancel()
                    viewAnimJob = null
                    if (viewportW <= 0f || viewportH <= 0f) return@detectTransformGestures
                    val oldScale = scale
                    val newScale = (scale * zoom).coerceIn(MIN_VIEW_SCALE, MAX_VIEW_SCALE)
                    val scaleFactor = if (oldScale == 0f) 1f else newScale / oldScale

                    val nextOffsetX = centroid.x + (offsetX - centroid.x) * scaleFactor + pan.x
                    val nextOffsetY = centroid.y + (offsetY - centroid.y) * scaleFactor + pan.y

                    scale = newScale
                    offsetX = nextOffsetX
                    offsetY = nextOffsetY
                    clampView()
                }
            }
            .pointerInput(layout.width, layout.height, viewportW, viewportH) {
                detectTapGestures(
                    onDoubleTap = { fitToScreen(animated = true) },
                )
            }
    }

    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("数据库 · 思维导图", style = MaterialTheme.typography.titleLarge)
            Text(
                "支持双指缩放与拖动。导出可生成 PNG（位图）与 SVG（矢量图）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (g in groups) {
                val selected = g == groupFilter
                OutlinedButton(
                    onClick = { groupFilter = g },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Text(g, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                enabled = !busy,
                onClick = { fitToScreen(animated = true) },
                modifier = Modifier.weight(1f),
            ) { Text("适应屏幕") }
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    fitToScreen(animated = true)
                },
                modifier = Modifier.weight(1f),
            ) { Text("重置") }
            Button(
                enabled = !busy,
                onClick = {
                    val pre = if (groupFilter == "全部") ExportScopeLevel.All else ExportScopeLevel.Lvl1
                    exportScopeLevelName = pre.name
                    exportScopeValues = if (pre == ExportScopeLevel.All) emptyList() else listOf(groupFilter)
                    exportValueQuery = ""
                    exportDialogOpen = true
                },
                modifier = Modifier.weight(1f),
            ) { Text("导出图片") }
        }
        if (exportProgress != null) {
            Text(
                exportProgress!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true),
        ) {
            ComposeCanvas(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .onSizeChanged {
                        viewportW = it.width.toFloat()
                        viewportH = it.height.toFloat()
                    }
                    .then(gestureModifier),
                onDraw = {
                    drawIntoCanvas { c ->
                        val nc = c.nativeCanvas
                        nc.save()
                        nc.translate(offsetX, offsetY)
                        nc.scale(scale, scale)
                        nc.drawPicture(render.picture)
                        nc.restore()
                    }
                },
            )

            FloatingActionButton(
                onClick = {
                    val pre = if (groupFilter == "全部") ExportScopeLevel.All else ExportScopeLevel.Lvl1
                    exportScopeLevelName = pre.name
                    exportScopeValues = if (pre == ExportScopeLevel.All) emptyList() else listOf(groupFilter)
                    exportValueQuery = ""
                    exportDialogOpen = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(56.dp),
            ) {
                Text("导出")
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "当前：${filtered.size} 条物种（可导出整张导图）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
    }

    if (exportDialogOpen) {
        val exportLevel = runCatching { ExportScopeLevel.valueOf(exportScopeLevelName) }.getOrElse { ExportScopeLevel.All }
        val values = remember(items, exportLevel) { listScopeValues(items, exportLevel) }
        val selectedValues = exportScopeValues.map { it.trim() }.filter { it.isNotBlank() }
        val canSingle = exportLevel == ExportScopeLevel.All || selectedValues.size == 1
        val totalForBatch = when {
            exportLevel == ExportScopeLevel.All -> 1
            selectedValues.isNotEmpty() -> selectedValues.size
            else -> values.size
        }

        var levelMenuOpen by remember { mutableStateOf(false) }
        val dims = listOf(0, 4096, 8192, 12288, 16384, 20480)

        AlertDialog(
            onDismissRequest = {
                if (!busy) exportDialogOpen = false
            },
            title = { Text("导出图片") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "支持导出 PNG（高分辨率）与 SVG（矢量）。可按 大类/纲/目/科/属/种 选择范围，或一键批量导出。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )

                    Text("选择范围", style = MaterialTheme.typography.titleSmall)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            enabled = !busy,
                            onClick = { levelMenuOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("维度：${exportLevel.label}") }
                        DropdownMenu(expanded = levelMenuOpen, onDismissRequest = { levelMenuOpen = false }) {
                            for (lv in ExportScopeLevel.values()) {
                                DropdownMenuItem(
                                    text = { Text(lv.label) },
                                    onClick = {
                                        exportScopeLevelName = lv.name
                                        exportScopeValues = emptyList()
                                        exportValueQuery = ""
                                        levelMenuOpen = false
                                    },
                                )
                            }
                        }
                    }

                    if (exportLevel != ExportScopeLevel.All) {
                        val preview = when {
                            selectedValues.isEmpty() -> "（未选择）"
                            selectedValues.size <= 3 -> selectedValues.joinToString("、")
                            else -> selectedValues.take(3).joinToString("、") + "… 等 ${selectedValues.size} 个"
                        }
                        Text(
                            "已选：$preview",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                        OutlinedTextField(
                            value = exportValueQuery,
                            onValueChange = { exportValueQuery = it },
                            enabled = !busy,
                            label = { Text("搜索${exportLevel.label}") },
                            placeholder = { Text("输入关键词后点选下方候选…") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        val q = exportValueQuery.trim()
                        val matches = remember(q, values) {
                            if (q.isBlank()) values.take(10)
                            else values.asSequence().filter { it.contains(q) }.take(20).toList()
                        }
                        if (matches.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (m in matches) {
                                    val selected = selectedValues.contains(m)
                                    OutlinedButton(
                                        enabled = !busy,
                                        onClick = {
                                            exportScopeValues = if (selected) {
                                                exportScopeValues.filterNot { it == m }
                                            } else {
                                                (exportScopeValues + m).distinct()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                        ),
                                    ) { Text(if (selected) "✓ $m" else m, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                }
                            }
                        } else {
                            Text(
                                "没有匹配的${exportLevel.label}，请换个关键词。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            )
                        }
                    }

                    Text("PNG 分辨率（上限边长）", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (d in dims) {
                            val selected = exportPngMaxDim == d
                            OutlinedButton(
                                enabled = !busy,
                                onClick = { exportPngMaxDim = d },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                ),
                            ) { Text(if (d <= 0) "自动" else "${d}px") }
                        }
                    }
                    Text(
                        "提示：SVG 为矢量图不受分辨率影响；PNG 选择越大文件越大。选择“自动”会尽可能导出高分辨率，内存不足时会自动降级。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )

                    Text("单张导出", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            enabled = !busy && canSingle,
                            onClick = { startSingleExport(ExportFormat.Png) },
                            modifier = Modifier.weight(1f),
                        ) { Text("导出 PNG") }
                        Button(
                            enabled = !busy && canSingle,
                            onClick = { startSingleExport(ExportFormat.Svg) },
                            modifier = Modifier.weight(1f),
                        ) { Text("导出 SVG") }
                    }

                    Text("一键全部导出", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (exportLevel == ExportScopeLevel.All) {
                            "导出全部：$totalForBatch 份（会让你选择导出目录）。"
                        } else if (selectedValues.isNotEmpty()) {
                            "导出已选 ${selectedValues.size} 个${exportLevel.label}（会让你选择导出目录）。"
                        } else {
                            "按「${exportLevel.label}」批量导出：$totalForBatch 份（会让你选择导出目录）。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            enabled = !busy && totalForBatch > 0,
                            onClick = { startBatchExport(exportPng = true, exportSvg = false) },
                            modifier = Modifier.weight(1f),
                        ) { Text("全部 PNG") }
                        OutlinedButton(
                            enabled = !busy && totalForBatch > 0,
                            onClick = { startBatchExport(exportPng = false, exportSvg = true) },
                            modifier = Modifier.weight(1f),
                        ) { Text("全部 SVG") }
                    }
                    Button(
                        enabled = !busy && totalForBatch > 0,
                        onClick = { startBatchExport(exportPng = true, exportSvg = true) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("全部 PNG + SVG") }

                    if (exportProgress != null) {
                        Text(
                            exportProgress!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { exportDialogOpen = false },
                ) { Text("关闭") }
            },
            containerColor = dialogColor,
            shape = dialogShape,
        )
    }
}
}
