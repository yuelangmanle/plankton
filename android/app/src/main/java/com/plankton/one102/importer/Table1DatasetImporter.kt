package com.plankton.one102.importer

import android.content.ContentResolver
import android.net.Uri
import com.plankton.one102.domain.Dataset
import com.plankton.one102.domain.Point
import com.plankton.one102.domain.Species
import com.plankton.one102.domain.StratificationConfig
import com.plankton.one102.domain.Taxonomy
import com.plankton.one102.domain.newId
import com.plankton.one102.domain.normalizeLvl1Name
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
import kotlin.math.roundToInt

private const val TABLE1_FIRST_POINT_COL = 6 // G (0-based)

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

private fun findRowIndex(sheet: Sheet, maxRows: Int, predicate: (Row?) -> Boolean): Int? {
    val last = minOf(sheet.lastRowNum, maxRows)
    for (r in 0..last) {
        if (predicate(sheet.getRow(r))) return r
    }
    return null
}

private fun findTotalRowIndex(sheet: Sheet, startRow: Int, maxScanRows: Int, formatter: DataFormatter, evaluator: FormulaEvaluator?): Int? {
    val last = minOf(sheet.lastRowNum, startRow + maxScanRows)
    for (r in startRow..last) {
        val v = cellText(sheet.getRow(r)?.getCell(0), formatter, evaluator)
        if (v == "总计") return r
    }
    return null
}

private fun extractTitlePrefix(rawTitle: String): String {
    val t = rawTitle.trim()
    if (t.isBlank()) return ""
    val suffixes = listOf("浮游动物计数", "计数", "Table1", "表1")
    for (s in suffixes) {
        if (t.endsWith(s)) return t.removeSuffix(s).trim()
    }
    return t
}

/**
 * 从“表1.xlsx（浮游动物计数 sheet）”导入一个新数据集：
 * - 读取点位：第3行（名称）从 G 列开始
 * - 读取浓缩体积：第4行（浓缩体积）从 G 列开始
 * - 读取物种与计数：从第5行开始到“总计”上一行
 */
suspend fun importDatasetFromTable1Excel(
    contentResolver: ContentResolver,
    uri: Uri,
    defaultVOrigL: Double,
): Dataset = withContext(Dispatchers.IO) {
    contentResolver.openInputStream(uri)?.use { input ->
        val wb = WorkbookFactory.create(input)
        try {
            val sheet = wb.getSheet("浮游动物计数") ?: wb.getSheetAt(0) ?: error("无法读取工作表")
            val formatter = DataFormatter()
            val evaluator = runCatching { wb.creationHelper.createFormulaEvaluator() }.getOrNull()

            val headerRow = findRowIndex(sheet, maxRows = 30) { row ->
                cellText(row?.getCell(0), formatter, evaluator).contains("名称")
            } ?: throw IllegalArgumentException("未识别到点位标题行（需要包含“名称”）")
            val concRow = headerRow + 1

            val pointLabels = mutableListOf<String>()
            val concValues = mutableListOf<Double?>()
            var c = TABLE1_FIRST_POINT_COL
            while (c <= TABLE1_FIRST_POINT_COL + 200) {
                val label = cellText(sheet.getRow(headerRow)?.getCell(c), formatter, evaluator).trim()
                if (label.isBlank()) break
                pointLabels += label
                concValues += cellDouble(sheet.getRow(concRow)?.getCell(c), formatter, evaluator)
                    ?.takeIf { it > 0 }
                c += 1
            }
            if (pointLabels.isEmpty()) throw IllegalArgumentException("未识别到任何点位列（请确认是表1「浮游动物计数」格式）")

            val points: List<Point> = pointLabels.mapIndexed { idx, label ->
                val (site, depthM) = resolveSiteAndDepthForPoint(label = label, site = null, depthM = null)
                Point(
                    id = newId(),
                    label = label.trim(),
                    vConcMl = concValues.getOrNull(idx),
                    vOrigL = defaultVOrigL,
                    site = site,
                    depthM = depthM,
                )
            }

            val enableStrat = points
                .filter { it.depthM != null && !it.site.isNullOrBlank() }
                .groupBy { it.site!!.trim() }
                .any { (_, list) -> list.size >= 2 }

            val startRow = concRow + 1
            val totalRow = findTotalRowIndex(sheet, startRow, maxScanRows = 5000, formatter, evaluator) ?: (sheet.lastRowNum + 1)

            var lvl1 = ""
            var lvl2 = ""
            var lvl3 = ""
            var lvl4 = ""
            var lvl5 = ""

            val species = mutableListOf<Species>()
            for (r in startRow until totalRow) {
                val row = sheet.getRow(r) ?: continue

                val t1 = cellText(row.getCell(0), formatter, evaluator)
                val t2 = cellText(row.getCell(1), formatter, evaluator)
                val t3 = cellText(row.getCell(2), formatter, evaluator)
                val t4 = cellText(row.getCell(3), formatter, evaluator)
                val t5 = cellText(row.getCell(4), formatter, evaluator)
                val nameCn = cellText(row.getCell(5), formatter, evaluator)

                if (t1.isNotBlank()) {
                    lvl1 = normalizeLvl1Name(t1)
                    lvl2 = ""
                    lvl3 = ""
                    lvl4 = ""
                    lvl5 = ""
                }
                if (t2.isNotBlank()) {
                    lvl2 = t2
                    lvl3 = ""
                    lvl4 = ""
                    lvl5 = ""
                }
                if (t3.isNotBlank()) {
                    lvl3 = t3
                    lvl4 = ""
                    lvl5 = ""
                }
                if (t4.isNotBlank()) {
                    lvl4 = t4
                    lvl5 = ""
                }
                if (t5.isNotBlank()) {
                    lvl5 = t5
                }

                if (nameCn.isBlank()) continue
                if (nameCn.contains("总计") || nameCn.contains("合计")) break

                val counts = buildMap {
                    for (i in points.indices) {
                        val cell = row.getCell(TABLE1_FIRST_POINT_COL + i)
                        put(points[i].id, cellInt(cell, formatter, evaluator))
                    }
                }

                species += Species(
                    id = newId(),
                    nameCn = nameCn,
                    nameLatin = "",
                    taxonomy = Taxonomy(lvl1 = lvl1, lvl2 = lvl2, lvl3 = lvl3, lvl4 = lvl4, lvl5 = lvl5),
                    avgWetWeightMg = null,
                    countsByPointId = counts,
                )
            }

            val now = nowIso()
            val titlePrefix = extractTitlePrefix(cellText(sheet.getRow(0)?.getCell(0), formatter, evaluator))
            Dataset(
                id = newId(),
                titlePrefix = titlePrefix,
                createdAt = now,
                updatedAt = now,
                points = points,
                species = species,
                stratification = StratificationConfig(enabled = enableStrat),
            )
        } finally {
            wb.close()
        }
    } ?: throw IllegalArgumentException("无法读取所选文件")
}

