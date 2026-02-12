package com.plankton.one102.importer

import android.content.ContentResolver
import android.net.Uri
import com.plankton.one102.domain.ImportedPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.FormulaEvaluator
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory

private fun extractFirstDouble(text: String): Double? {
    val regex = Regex("-?\\d+(?:\\.\\d+)?(?:e-?\\d+)?", RegexOption.IGNORE_CASE)
    val m = regex.find(text) ?: return null
    return m.value.toDoubleOrNull()
}

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
        CellType.STRING -> extractFirstDouble(cell.stringCellValue ?: "")
        CellType.FORMULA -> extractFirstDouble(cellText(cell, formatter, evaluator))
        CellType.BLANK -> null
        else -> extractFirstDouble(cellText(cell, formatter, evaluator))
    }
}

private fun looksLikeHeader(row: Row?, formatter: DataFormatter, evaluator: FormulaEvaluator?): Boolean {
    if (row == null) return false
    val a = cellText(row.getCell(0), formatter, evaluator)
    val b = cellText(row.getCell(1), formatter, evaluator)
    val c = cellText(row.getCell(2), formatter, evaluator)
    val aOk = a.contains("采样") || a.contains("点位") || a.contains("名称") || a.lowercase().contains("name")
    val bOk = b.contains("浓缩") || b.lowercase().contains("conc") || b.lowercase().contains("volume")
    val cOk = c.contains("原水") || c.contains("原水体积") || c.lowercase().contains("orig")
    return aOk && bOk && (cOk || c.isNotBlank())
}

/**
 * Excel(.xlsx) 格式：
 * - 第 1 列：采样点名称
 * - 第 2 列：浓缩体积（mL）
 * - 第 3 列：原水体积（L，空白表示使用默认值）
 *
 * 默认读取第 1 个工作表；支持首行表头（会自动识别并跳过）。
 */
suspend fun importPointsFromExcel(
    contentResolver: ContentResolver,
    uri: Uri,
): List<ImportedPoint> = withContext(Dispatchers.IO) {
    contentResolver.openInputStream(uri)?.use { input ->
        val wb = WorkbookFactory.create(input)
        try {
            val sheet = wb.getSheetAt(0) ?: throw IllegalArgumentException("Excel 中没有工作表")
            val formatter = DataFormatter()
            val evaluator = runCatching { wb.creationHelper.createFormulaEvaluator() }.getOrNull()

            val startRow = if (looksLikeHeader(sheet.getRow(0), formatter, evaluator)) 1 else 0
            val out = mutableListOf<ImportedPoint>()

            for (r in startRow..sheet.lastRowNum) {
                val row = sheet.getRow(r) ?: continue
                val label = cellText(row.getCell(0), formatter, evaluator).trim()
                val conc = cellDouble(row.getCell(1), formatter, evaluator)
                val vOrig = cellDouble(row.getCell(2), formatter, evaluator)

                val hasAny = label.isNotBlank() || conc != null || vOrig != null
                if (!hasAny) continue
                if (label.isBlank()) throw IllegalArgumentException("第 ${r + 1} 行：采样点名称为空")

                val concFixed = when {
                    conc == null -> null
                    !conc.isFinite() -> throw IllegalArgumentException("第 ${r + 1} 行：浓缩体积不是有效数值")
                    conc > 0 -> conc
                    conc == 0.0 -> null // 允许用 0 表示空白
                    else -> throw IllegalArgumentException("第 ${r + 1} 行：浓缩体积需为正数")
                }

                val vOrigFixed = when {
                    vOrig == null -> null
                    !vOrig.isFinite() -> throw IllegalArgumentException("第 ${r + 1} 行：原水体积不是有效数值")
                    vOrig > 0 -> vOrig
                    vOrig == 0.0 -> null // 允许用 0 表示空白（走默认值）
                    else -> throw IllegalArgumentException("第 ${r + 1} 行：原水体积需为正数")
                }

                out += ImportedPoint(label = label, vConcMl = concFixed, vOrigL = vOrigFixed)
            }

            if (out.isEmpty()) throw IllegalArgumentException("未读取到任何采样点（请确认第1列为名称，第2列为浓缩体积，第3列为原水体积）")

            val dup = out.groupBy { it.label.trim() }.filterValues { it.size > 1 }.keys.firstOrNull()
            if (dup != null) throw IllegalArgumentException("采样点名称重复：$dup（请确保名称唯一）")

            out
        } finally {
            wb.close()
        }
    } ?: throw IllegalArgumentException("无法读取所选文件")
}

