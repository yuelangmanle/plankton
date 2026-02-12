package com.plankton.one102.export

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.FormulaEvaluator
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.ByteArrayInputStream

data class SheetPreview(
    val name: String,
    val rows: List<List<String>>,
)

data class WorkbookPreview(
    val sheets: List<SheetPreview>,
)

private fun extractMaxDefinedColumn(sheet: Sheet, maxRows: Int): Int {
    var max = 0
    val lastRow = sheet.lastRowNum.coerceAtMost(maxRows - 1)
    for (r in 0..lastRow) {
        val row = sheet.getRow(r) ?: continue
        val last = (row.lastCellNum.toInt()).coerceAtLeast(0)
        max = maxOf(max, last)
    }
    return max
}

private fun formatCell(cell: Cell?, formatter: DataFormatter, evaluator: FormulaEvaluator?): String {
    if (cell == null) return ""
    return try {
        when (cell.cellType) {
            CellType.FORMULA -> formatter.formatCellValue(cell, evaluator)
            else -> formatter.formatCellValue(cell)
        }.trim()
    } catch (_: Exception) {
        // Fallback: show raw, avoid crash.
        runCatching {
            when (cell.cellType) {
                CellType.STRING -> cell.stringCellValue ?: ""
                CellType.NUMERIC -> cell.numericCellValue.toString()
                CellType.BOOLEAN -> cell.booleanCellValue.toString()
                CellType.FORMULA -> cell.cellFormula ?: ""
                else -> ""
            }
        }.getOrElse { "" }.trim()
    }
}

suspend fun buildWorkbookPreview(
    bytes: ByteArray,
    maxRows: Int = 80,
    maxCols: Int = 20,
): WorkbookPreview = withContext(Dispatchers.IO) {
    ByteArrayInputStream(bytes).use { input ->
        val wb = WorkbookFactory.create(input)
        try {
            val formatter = DataFormatter()
            val evaluator = runCatching { wb.creationHelper.createFormulaEvaluator() }.getOrNull()
            val list = buildList {
                for (i in 0 until wb.numberOfSheets) {
                    val sheet = wb.getSheetAt(i)
                    val rowCount = (sheet.lastRowNum + 1).coerceAtMost(maxRows)
                    val maxDefined = extractMaxDefinedColumn(sheet, maxRows = rowCount.coerceAtLeast(1)).coerceAtLeast(1)
                    val visibleCols = buildList {
                        for (c in 0 until maxDefined) {
                            if (!sheet.isColumnHidden(c)) add(c)
                            if (size >= maxCols) break
                        }
                        if (isEmpty()) add(0)
                    }
                    val rows = buildList {
                        for (r in 0 until rowCount) {
                            val row = sheet.getRow(r)
                            add(
                                buildList {
                                    for (c in visibleCols) {
                                        add(formatCell(row?.getCell(c), formatter, evaluator))
                                    }
                                },
                            )
                        }
                    }
                    add(SheetPreview(name = sheet.sheetName, rows = rows))
                }
            }
            WorkbookPreview(sheets = list)
        } finally {
            wb.close()
        }
    }
}
