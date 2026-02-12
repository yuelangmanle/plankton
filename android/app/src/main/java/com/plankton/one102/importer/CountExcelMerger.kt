package com.plankton.one102.importer

import android.content.ContentResolver
import android.net.Uri
import com.plankton.one102.domain.Dataset
import com.plankton.one102.domain.Point
import com.plankton.one102.domain.Species
import com.plankton.one102.domain.Taxonomy
import com.plankton.one102.domain.newId
import com.plankton.one102.domain.nowIso
import com.plankton.one102.domain.resolveSiteAndDepthForPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.FormulaEvaluator
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.ByteArrayInputStream
import kotlin.math.roundToInt

data class CountMergeOptions(
    val createMissingPoints: Boolean = false,
    val overwriteCounts: Boolean = true,
)

data class CountMergeSummary(
    val format: String,
    val pointsAdded: Int,
    val speciesAdded: Int,
    val cellsUpdated: Int,
    val ignoredCells: Int,
)

private fun cellText(cell: Cell?, formatter: DataFormatter, evaluator: FormulaEvaluator?): String {
    if (cell == null) return ""
    return runCatching {
        when (cell.cellType) {
            CellType.FORMULA -> formatter.formatCellValue(cell, evaluator)
            else -> formatter.formatCellValue(cell)
        }
    }.getOrElse { "" }.trim()
}

private fun cellDouble(cell: Cell?, formatter: DataFormatter, evaluator: FormulaEvaluator?): Double? {
    if (cell == null) return null
    return when (cell.cellType) {
        CellType.NUMERIC -> cell.numericCellValue
        CellType.STRING -> cell.stringCellValue?.trim()?.toDoubleOrNull()
        CellType.FORMULA -> cellText(cell, formatter, evaluator).trim().toDoubleOrNull()
        else -> cellText(cell, formatter, evaluator).trim().toDoubleOrNull()
    }?.takeIf { it.isFinite() }
}

private fun cellInt(cell: Cell?, formatter: DataFormatter, evaluator: FormulaEvaluator?): Int {
    val d = cellDouble(cell, formatter, evaluator) ?: return 0
    return d.roundToInt().coerceAtLeast(0)
}

private data class SimpleCountRow(
    val pointToken: String,
    val speciesNameCn: String,
    val count: Int,
)

private fun findHeaderRow(sheet: Sheet, maxRows: Int, predicate: (Row?) -> Boolean): Int? {
    val last = minOf(sheet.lastRowNum, maxRows)
    for (r in 0..last) if (predicate(sheet.getRow(r))) return r
    return null
}

private fun parseSimpleCountSheet(bytes: ByteArray): List<SimpleCountRow> {
    ByteArrayInputStream(bytes).use { input ->
        val wb = WorkbookFactory.create(input)
        try {
            val sheet = wb.getSheetAt(0) ?: error("无法读取工作表")
            val formatter = DataFormatter()
            val evaluator = runCatching { wb.creationHelper.createFormulaEvaluator() }.getOrNull()

            val headerRowIdx = findHeaderRow(sheet, maxRows = 20) { row ->
                val cells = (0..10).map { cellText(row?.getCell(it), formatter, evaluator) }
                cells.any { it.contains("点位") || it.contains("采样点") } &&
                    cells.any { it.contains("物种") || it.contains("中文名") || it.contains("名称") } &&
                    cells.any { it.contains("计数") || it.contains("个数") || it.contains("数量") }
            } ?: 0

            val header = sheet.getRow(headerRowIdx)
            fun findCol(pred: (String) -> Boolean): Int? {
                for (c in 0..50) {
                    val v = cellText(header?.getCell(c), formatter, evaluator)
                    if (v.isNotBlank() && pred(v)) return c
                }
                return null
            }

            val colPoint = findCol { it.contains("点位") || it.contains("采样点") } ?: 0
            val colSpecies = findCol { it.contains("物种") || it.contains("中文名") || it.contains("名称") } ?: 1
            val colCount = findCol { it.contains("计数") || it.contains("个数") || it.contains("数量") } ?: 2

            val out = mutableListOf<SimpleCountRow>()
            val last = sheet.lastRowNum
            for (r in (headerRowIdx + 1)..last) {
                val row = sheet.getRow(r) ?: continue
                val p = cellText(row.getCell(colPoint), formatter, evaluator)
                val s = cellText(row.getCell(colSpecies), formatter, evaluator)
                if (p.isBlank() && s.isBlank()) continue
                if (s.isBlank()) continue
                val count = cellInt(row.getCell(colCount), formatter, evaluator)
                out += SimpleCountRow(pointToken = p, speciesNameCn = s, count = count)
            }
            return out
        } finally {
            wb.close()
        }
    }
}

private fun resolvePointIdByToken(dataset: Dataset, token: String): String? {
    val raw = token.trim()
    if (raw.isEmpty()) return null
    dataset.points.firstOrNull { it.label.trim() == raw }?.let { return it.id }

    val asInt = raw.toIntOrNull()
    if (asInt != null) {
        val idx = asInt - 1
        return dataset.points.getOrNull(idx)?.id
    }

    val m = Regex("""(\d+)\s*号""").find(raw)
    val n = m?.groupValues?.getOrNull(1)?.toIntOrNull()
    if (n != null) {
        val idx = n - 1
        return dataset.points.getOrNull(idx)?.id
    }
    return null
}

/**
 * 将 Excel 中的计数数据合并到当前数据集。
 * 支持两种格式：
 * 1) 表1.xlsx（浮游动物计数 sheet）：直接复用表1导入器解析；
 * 2) 简易表（第一行表头含：点位/物种/计数）：按行读取。
 */
suspend fun mergeCountsFromExcelIntoDataset(
    contentResolver: ContentResolver,
    uri: Uri,
    dataset: Dataset,
    defaultVOrigL: Double,
    options: CountMergeOptions,
    resolveSpeciesNameCn: (String) -> String = { it.trim() },
): Pair<Dataset, CountMergeSummary> = withContext(Dispatchers.IO) {
    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalArgumentException("无法读取所选文件")

    fun mergeFromImported(imported: Dataset, format: String): Pair<Dataset, CountMergeSummary> {
        var next = dataset
        val now = nowIso()
        val pointIdByLabel = next.points.associateBy({ it.label.trim() }, { it.id }).toMutableMap()
        var pointsAdded = 0
        var speciesAdded = 0
        var cellsUpdated = 0
        var ignoredCells = 0

        fun ensurePoint(label: String, from: Point): String? {
            val key = label.trim()
            val existing = pointIdByLabel[key]
            if (existing != null) return existing
            if (!options.createMissingPoints) return null

            val (site, depthM) = resolveSiteAndDepthForPoint(label = key, site = null, depthM = null)
            val p = Point(
                id = newId(),
                label = key,
                vConcMl = from.vConcMl,
                vOrigL = defaultVOrigL,
                site = site,
                depthM = depthM,
            )
            next = next.copy(points = next.points + p, updatedAt = now)
            pointIdByLabel[key] = p.id
            pointsAdded += 1
            return p.id
        }

        val pointMap = LinkedHashMap<String, String?>()
        for (p in imported.points) {
            pointMap[p.id] = ensurePoint(p.label, p)
        }

        val speciesByName = next.species.associateBy { it.nameCn.trim() }.toMutableMap()

        fun ensureSpecies(nameCn: String, tax: Taxonomy): Species {
            val key = resolveSpeciesNameCn(nameCn).trim()
            val existing = speciesByName[key]
            if (existing != null) return existing

            val counts = next.points.associate { it.id to 0 }
            val sp = Species(
                id = newId(),
                nameCn = key,
                nameLatin = "",
                taxonomy = tax,
                avgWetWeightMg = null,
                countsByPointId = counts,
            )
            next = next.copy(species = next.species + sp, updatedAt = now)
            speciesByName[key] = sp
            speciesAdded += 1
            return sp
        }

        for (spImp in imported.species) {
            val sp = ensureSpecies(spImp.nameCn, spImp.taxonomy)
            val newCounts = sp.countsByPointId.toMutableMap()
            for ((pidImp, v) in spImp.countsByPointId) {
                val pidCur = pointMap[pidImp]
                if (pidCur == null) {
                    ignoredCells += 1
                    continue
                }
                val prev = newCounts[pidCur] ?: 0
                val nextVal = if (options.overwriteCounts) v else (prev + v)
                if (nextVal != prev) {
                    newCounts[pidCur] = nextVal
                    cellsUpdated += 1
                }
            }
            if (newCounts != sp.countsByPointId) {
                next = next.copy(
                    species = next.species.map { if (it.id == sp.id) sp.copy(countsByPointId = newCounts) else it },
                    updatedAt = now,
                )
            }
        }

        return next to CountMergeSummary(
            format = format,
            pointsAdded = pointsAdded,
            speciesAdded = speciesAdded,
            cellsUpdated = cellsUpdated,
            ignoredCells = ignoredCells,
        )
    }

    val importedTable1 = runCatching {
        importDatasetFromTable1Excel(
            contentResolver = contentResolver,
            uri = uri,
            defaultVOrigL = defaultVOrigL,
        )
    }.getOrNull()

    if (importedTable1 != null) {
        return@withContext mergeFromImported(importedTable1, "表1（浮游动物计数）")
    }

    val importedSimpleTable = runCatching {
        importDatasetFromSimpleTableExcel(
            contentResolver = contentResolver,
            uri = uri,
            defaultVOrigL = defaultVOrigL,
        )
    }.getOrNull()

    if (importedSimpleTable != null) {
        return@withContext mergeFromImported(importedSimpleTable, "简表（四大类+物种）")
    }

    // Fallback: simple sheet (point/species/count)
    val rows = parseSimpleCountSheet(bytes)
    var next = dataset
    val now = nowIso()
    val pointIdByLabel = next.points.associateBy({ it.label.trim() }, { it.id }).toMutableMap()
    var pointsAdded = 0
    var speciesAdded = 0
    var cellsUpdated = 0
    var ignoredCells = 0

    fun ensurePointByToken(token: String): String? {
        val resolved = resolvePointIdByToken(next, token)
        if (resolved != null) return resolved
        val label = token.trim()
        if (label.isEmpty()) return null
        if (!options.createMissingPoints) return null

        val (site, depthM) = resolveSiteAndDepthForPoint(label = label, site = null, depthM = null)
        val p = Point(
            id = newId(),
            label = label,
            vConcMl = null,
            vOrigL = defaultVOrigL,
            site = site,
            depthM = depthM,
        )
        next = next.copy(points = next.points + p, updatedAt = now)
        pointIdByLabel[label] = p.id
        pointsAdded += 1
        return p.id
    }

    val speciesByName = next.species.associateBy { it.nameCn.trim() }.toMutableMap()
    fun ensureSpecies(nameCn: String): Species {
        val key = resolveSpeciesNameCn(nameCn).trim()
        val existing = speciesByName[key]
        if (existing != null) return existing

        val counts = next.points.associate { it.id to 0 }
        val sp = Species(
            id = newId(),
            nameCn = key,
            nameLatin = "",
            taxonomy = Taxonomy(),
            avgWetWeightMg = null,
            countsByPointId = counts,
        )
        next = next.copy(species = next.species + sp, updatedAt = now)
        speciesByName[key] = sp
        speciesAdded += 1
        return sp
    }

    for (r in rows) {
        val pid = ensurePointByToken(r.pointToken)
        if (pid == null) {
            ignoredCells += 1
            continue
        }
        val sp = ensureSpecies(r.speciesNameCn)
        val prev = sp.countsByPointId[pid] ?: 0
        val nextVal = if (options.overwriteCounts) r.count else (prev + r.count)
        if (nextVal == prev) continue
        val newCounts = sp.countsByPointId + (pid to nextVal)
        next = next.copy(
            species = next.species.map { if (it.id == sp.id) sp.copy(countsByPointId = newCounts) else it },
            updatedAt = now,
        )
        cellsUpdated += 1
    }

    next to CountMergeSummary(
        format = "简易表（点位/物种/计数）",
        pointsAdded = pointsAdded,
        speciesAdded = speciesAdded,
        cellsUpdated = cellsUpdated,
        ignoredCells = ignoredCells,
    )
}
