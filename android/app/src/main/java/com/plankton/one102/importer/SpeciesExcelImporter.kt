package com.plankton.one102.importer

import android.content.ContentResolver
import android.net.Uri
import com.plankton.one102.domain.ImportedSpecies
import com.plankton.one102.domain.ImportTemplatePreset
import com.plankton.one102.domain.ImportTemplateType
import com.plankton.one102.domain.Taxonomy
import com.plankton.one102.domain.LVL1_ORDER
import com.plankton.one102.domain.normalizeLvl1Name
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.FormulaEvaluator
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory

private fun extractFirstDouble(text: String): Double? {
    val regex = Regex("-?\\d+(?:\\.\\d+)?(?:e-?\\d+)?", RegexOption.IGNORE_CASE)
    val m = regex.find(text) ?: return null
    return m.value.toDoubleOrNull()
}

private fun parseAliases(raw: String): List<String> {
    val cleaned = raw.trim()
    if (cleaned.isBlank()) return emptyList()
    return cleaned
        .split(Regex("[,，;；、/|\\n\\r\\t]+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

private fun columnRefToIndex(raw: String): Int? {
    val token = raw.trim().uppercase()
    if (token.isBlank()) return null
    if (token.all { it.isDigit() }) {
        val number = token.toIntOrNull() ?: return null
        return if (number > 0) number - 1 else null
    }
    if (!token.all { it in 'A'..'Z' }) return null
    var out = 0
    for (ch in token) {
        out = out * 26 + (ch - 'A' + 1)
    }
    return (out - 1).takeIf { it >= 0 }
}

private fun parseColumnRefs(raw: String): List<Int> {
    return raw.split(Regex("[,，;；\\s]+"))
        .mapNotNull { columnRefToIndex(it) }
        .distinct()
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

private fun normalizeHeader(s: String): String {
    return s.lowercase()
        .replace("（", "(")
        .replace("）", ")")
        .replace(" ", "")
}

private fun findHeaderIndex(headers: List<String>, vararg keys: String): Int? {
    val set = keys.map { normalizeHeader(it) }.toSet()
    for ((i, raw) in headers.withIndex()) {
        val h = normalizeHeader(raw)
        if (h.isBlank()) continue
        if (h in set) return i
        if (set.any { k -> h.contains(k) }) return i
    }
    return null
}

private fun findHeaderIndices(headers: List<String>, vararg keys: String): List<Int> {
    val set = keys.map { normalizeHeader(it) }.toSet()
    val out = mutableListOf<Int>()
    for ((i, raw) in headers.withIndex()) {
        val h = normalizeHeader(raw)
        if (h.isBlank()) continue
        if (h in set || set.any { k -> h.contains(k) }) {
            out += i
        }
    }
    return out.distinct()
}

private fun looksLikeHeader(row: Row?, formatter: DataFormatter, evaluator: FormulaEvaluator?): Boolean {
    if (row == null) return false
    val h0 = cellText(row.getCell(0), formatter, evaluator)
    val h1 = cellText(row.getCell(1), formatter, evaluator)
    val h2 = cellText(row.getCell(2), formatter, evaluator)
    val okName = h0.contains("物种") || h0.contains("中文") || h0.contains("名称") || h0.lowercase().contains("name")
    val okWet = h2.contains("湿重") || h2.lowercase().contains("wet") || h2.lowercase().contains("weight")
    val okLatin = h1.contains("拉丁") || h1.contains("学名") || h1.lowercase().contains("latin")
    return okName || okWet || okLatin
}

private fun looksLikeTable3Header(row: Row?, formatter: DataFormatter, evaluator: FormulaEvaluator?): Boolean {
    if (row == null) return false
    val a = normalizeHeader(cellText(row.getCell(0), formatter, evaluator))
    val b = normalizeHeader(cellText(row.getCell(1), formatter, evaluator))
    val c = normalizeHeader(cellText(row.getCell(2), formatter, evaluator))
    val aOk = a.contains("种类") || a.contains("种") || a.contains("类别")
    val bOk = b.contains("拉丁") || b.contains("latin")
    val cOk = c.contains("平均湿重") || c.contains("湿重") || c.contains("weight")
    return aOk && bOk && cOk
}

private fun findTable3HeaderRow(sheet: Sheet, formatter: DataFormatter, evaluator: FormulaEvaluator?): Int? {
    val max = minOf(sheet.lastRowNum, 30)
    for (r in 0..max) {
        if (looksLikeTable3Header(sheet.getRow(r), formatter, evaluator)) return r
    }
    return null
}

private fun importFromTable3Sheet(sheet: Sheet, headerRow: Int, formatter: DataFormatter, evaluator: FormulaEvaluator?): List<ImportedSpecies> {
    var currentLvl1 = ""
    var currentSub = ""
    val out = mutableListOf<ImportedSpecies>()

    for (r in (headerRow + 1)..sheet.lastRowNum) {
        val row = sheet.getRow(r) ?: continue
        val name = cellText(row.getCell(0), formatter, evaluator).trim()
        val latin = cellText(row.getCell(1), formatter, evaluator).trim()
        val wet = cellDouble(row.getCell(2), formatter, evaluator)

        val hasAny = name.isNotBlank() || latin.isNotBlank() || wet != null
        if (!hasAny) continue
        if (name.contains("总计") || name.contains("合计")) break

        val wetFixed = when {
            wet == null -> null
            !wet.isFinite() -> throw IllegalArgumentException("第 ${r + 1} 行：平均湿重不是有效数值")
            wet > 0 -> wet
            wet == 0.0 -> null
            else -> throw IllegalArgumentException("第 ${r + 1} 行：平均湿重需为正数")
        }

        if (wetFixed != null) {
            if (name.isBlank()) throw IllegalArgumentException("第 ${r + 1} 行：物种名称为空")
            val lvl1 = normalizeLvl1Name(currentLvl1)
            val lvl4 = currentSub.trim()
            val taxonomy = if (lvl1.isNotBlank() || lvl4.isNotBlank()) Taxonomy(lvl1 = lvl1, lvl4 = lvl4) else null
            out += ImportedSpecies(
                nameCn = name,
                nameLatin = latin.ifBlank { null },
                wetWeightMg = wetFixed,
                taxonomy = taxonomy,
            )
            continue
        }

        // Heading rows: update current group/family.
        val normalized = normalizeLvl1Name(name)
        if (normalized in LVL1_ORDER) {
            currentLvl1 = normalized
            currentSub = ""
            continue
        }
        if (name.endsWith("科")) {
            currentSub = name
            continue
        }
    }

    return out
}

private fun mergeTaxonomy(base: Taxonomy?, incoming: Taxonomy?): Taxonomy? {
    if (base == null) return incoming
    if (incoming == null) return base
    return Taxonomy(
        lvl1 = incoming.lvl1.ifBlank { base.lvl1 },
        lvl2 = incoming.lvl2.ifBlank { base.lvl2 },
        lvl3 = incoming.lvl3.ifBlank { base.lvl3 },
        lvl4 = incoming.lvl4.ifBlank { base.lvl4 },
        lvl5 = incoming.lvl5.ifBlank { base.lvl5 },
    )
}

private fun mergeImported(base: ImportedSpecies, incoming: ImportedSpecies): ImportedSpecies {
    val latin = incoming.nameLatin?.takeIf { !it.isNullOrBlank() } ?: base.nameLatin
    val wet = incoming.wetWeightMg ?: base.wetWeightMg
    val taxonomy = mergeTaxonomy(base.taxonomy, incoming.taxonomy)
    val aliases = (base.aliases + incoming.aliases).map { it.trim() }.filter { it.isNotBlank() }.distinct()
    return base.copy(
        nameLatin = latin,
        wetWeightMg = wet,
        taxonomy = taxonomy,
        aliases = aliases,
    )
}

private fun dedupeImported(rows: List<ImportedSpecies>): List<ImportedSpecies> {
    val map = LinkedHashMap<String, ImportedSpecies>()
    for (row in rows) {
        val key = row.nameCn.trim()
        if (key.isBlank()) continue
        val existing = map[key]
        map[key] = if (existing == null) row else mergeImported(existing, row)
    }
    return map.values.toList()
}

private fun importFromTemplateSheet(
    sheet: Sheet,
    template: ImportTemplatePreset,
    formatter: DataFormatter,
    evaluator: FormulaEvaluator?,
): List<ImportedSpecies> {
    val idxNameCn = columnRefToIndex(template.nameCnColumn)
        ?: throw IllegalArgumentException("导入模板缺少“中文名列”映射")
    val idxLatin = columnRefToIndex(template.latinColumn)
    val idxWet = columnRefToIndex(template.wetWeightColumn)
    val idx1 = columnRefToIndex(template.lvl1Column)
    val idx2 = columnRefToIndex(template.lvl2Column)
    val idx3 = columnRefToIndex(template.lvl3Column)
    val idx4 = columnRefToIndex(template.lvl4Column)
    val idx5 = columnRefToIndex(template.lvl5Column)
    val idxAliases = parseColumnRefs(template.aliasColumns)

    val headerRow = (template.headerRow - 1).coerceAtLeast(0)
    var startRow = (template.startRow - 1).coerceAtLeast(0)
    if (template.hasHeader) startRow = maxOf(startRow, headerRow + 1)

    val out = mutableListOf<ImportedSpecies>()
    for (r in startRow..sheet.lastRowNum) {
        val row = sheet.getRow(r) ?: continue

        val nameCn = cellText(row.getCell(idxNameCn), formatter, evaluator).trim()
        val latin = idxLatin?.let { cellText(row.getCell(it), formatter, evaluator).trim() }.orEmpty()
        val wet = idxWet?.let { cellDouble(row.getCell(it), formatter, evaluator) }
        val lvl1 = idx1?.let { cellText(row.getCell(it), formatter, evaluator).trim() }.orEmpty()
        val lvl2 = idx2?.let { cellText(row.getCell(it), formatter, evaluator).trim() }.orEmpty()
        val lvl3 = idx3?.let { cellText(row.getCell(it), formatter, evaluator).trim() }.orEmpty()
        val lvl4 = idx4?.let { cellText(row.getCell(it), formatter, evaluator).trim() }.orEmpty()
        val lvl5 = idx5?.let { cellText(row.getCell(it), formatter, evaluator).trim() }.orEmpty()
        val aliases = idxAliases
            .flatMap { idx -> parseAliases(cellText(row.getCell(idx), formatter, evaluator)) }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val hasAny = nameCn.isNotBlank() ||
            latin.isNotBlank() ||
            wet != null ||
            lvl1.isNotBlank() || lvl2.isNotBlank() || lvl3.isNotBlank() || lvl4.isNotBlank() || lvl5.isNotBlank() ||
            aliases.isNotEmpty()
        if (!hasAny) continue
        if (nameCn.isBlank()) throw IllegalArgumentException("第${r + 1}行：物种名称为空（模板映射后）")

        val wetFixed = when {
            wet == null -> null
            !wet.isFinite() -> throw IllegalArgumentException("第${r + 1}行：平均湿重不是有效数值")
            wet > 0 -> wet
            wet == 0.0 -> null
            else -> throw IllegalArgumentException("第${r + 1}行：平均湿重需为正数")
        }

        val taxonomy = if (lvl1.isNotBlank() || lvl2.isNotBlank() || lvl3.isNotBlank() || lvl4.isNotBlank() || lvl5.isNotBlank()) {
            Taxonomy(lvl1 = lvl1, lvl2 = lvl2, lvl3 = lvl3, lvl4 = lvl4, lvl5 = lvl5)
        } else {
            null
        }

        out += ImportedSpecies(
            nameCn = nameCn,
            nameLatin = latin.ifBlank { null },
            wetWeightMg = wetFixed,
            taxonomy = taxonomy,
            aliases = aliases,
        )
    }
    return out
}

/**
 * Excel(.xlsx) 导入（第1个工作表）推荐表头：
 * - 物种名称（或 中文名/名称）
 * - 拉丁名（可空）
 * - 平均湿重（mg/个，可空）
 * - 大类 / 纲 / 目 / 科 / 属（可空；也支持 分类1-分类5 或 lvl1-lvl5）
 * - 别名（可空；可用“/、,，；换行”等分隔多个别名；也支持多个“别名”列）
 *
 * 无表头时默认列序：
 * 1=中文名，2=拉丁名，3=平均湿重，4-8=大类/纲/目/科/属，9=别名
 */
suspend fun importSpeciesFromExcel(
    contentResolver: ContentResolver,
    uri: Uri,
    template: ImportTemplatePreset? = null,
): List<ImportedSpecies> = withContext(Dispatchers.IO) {
    contentResolver.openInputStream(uri)?.use { input ->
        val wb = WorkbookFactory.create(input)
        try {
            val formatter = DataFormatter()
            val evaluator = runCatching { wb.creationHelper.createFormulaEvaluator() }.getOrNull()

            if (template != null && template.type == ImportTemplateType.SpeciesLibrary) {
                val sheetIndex = template.sheetIndex.coerceAtLeast(0)
                if (sheetIndex >= wb.numberOfSheets) {
                    throw IllegalArgumentException("导入模板指定工作表索引超出范围：$sheetIndex")
                }
                val mappedSheet = wb.getSheetAt(sheetIndex)
                    ?: throw IllegalArgumentException("导入模板指定工作表不存在")
                val mapped = importFromTemplateSheet(mappedSheet, template, formatter, evaluator)
                if (mapped.isEmpty()) {
                    throw IllegalArgumentException("未读取到任何物种（请检查模板映射与起始行）")
                }
                return@withContext dedupeImported(mapped)
            }

            val sheet = wb.getSheetAt(0) ?: throw IllegalArgumentException("Excel 中没有工作表")

            // Special: 表三（平均湿重）格式（3 列，存在层级标题：大类/科/物种）。
            val table3Header = findTable3HeaderRow(sheet, formatter, evaluator)
            if (table3Header != null) {
                val out = importFromTable3Sheet(sheet, table3Header, formatter, evaluator)
                if (out.isEmpty()) throw IllegalArgumentException("未识别到任何物种（表三格式）")
                return@withContext dedupeImported(out)
            }

            val headerRow = sheet.getRow(0)
            val hasHeader = looksLikeHeader(headerRow, formatter, evaluator)

            val idxNameCn: Int
            val idxLatin: Int?
            val idxWet: Int?
            val idx1: Int?
            val idx2: Int?
            val idx3: Int?
            val idx4: Int?
            val idx5: Int?
            val idxAliases: List<Int>

            if (hasHeader) {
                val headers = buildList {
                    val row = headerRow
                    val last = (row?.lastCellNum?.toInt() ?: 0).coerceAtLeast(0)
                    for (c in 0 until last) add(cellText(row?.getCell(c), formatter, evaluator))
                }

                idxNameCn = findHeaderIndex(headers, "物种名称", "中文名", "名称", "name", "species") ?: 0
                idxLatin = findHeaderIndex(headers, "拉丁名", "学名", "latin", "scientificname")
                idxWet = findHeaderIndex(headers, "平均湿重(mg/个)", "平均湿重", "湿重", "wetweight", "weight")
                idx1 = findHeaderIndex(headers, "大类", "分类1", "lvl1")
                idx2 = findHeaderIndex(headers, "纲", "分类2", "lvl2")
                idx3 = findHeaderIndex(headers, "目", "分类3", "lvl3")
                idx4 = findHeaderIndex(headers, "科", "分类4", "lvl4")
                idx5 = findHeaderIndex(headers, "属", "分类5", "lvl5")
                idxAliases = findHeaderIndices(headers, "别名", "别称", "简称", "旧名", "曾用名", "alias", "aliases")
            } else {
                idxNameCn = 0
                idxLatin = 1
                idxWet = 2
                idx1 = 3
                idx2 = 4
                idx3 = 5
                idx4 = 6
                idx5 = 7
                idxAliases = listOf(8)
            }

            val startRow = if (hasHeader) 1 else 0
            val out = mutableListOf<ImportedSpecies>()

            for (r in startRow..sheet.lastRowNum) {
                val row = sheet.getRow(r) ?: continue

                val nameCn = cellText(row.getCell(idxNameCn), formatter, evaluator).trim()
                val latin = idxLatin?.let { cellText(row.getCell(it), formatter, evaluator).trim() }.orEmpty()
                val wet = idxWet?.let { cellDouble(row.getCell(it), formatter, evaluator) }

                val lvl1 = idx1?.let { cellText(row.getCell(it), formatter, evaluator).trim() }.orEmpty()
                val lvl2 = idx2?.let { cellText(row.getCell(it), formatter, evaluator).trim() }.orEmpty()
                val lvl3 = idx3?.let { cellText(row.getCell(it), formatter, evaluator).trim() }.orEmpty()
                val lvl4 = idx4?.let { cellText(row.getCell(it), formatter, evaluator).trim() }.orEmpty()
                val lvl5 = idx5?.let { cellText(row.getCell(it), formatter, evaluator).trim() }.orEmpty()
                val aliases = idxAliases.flatMap { idx ->
                    parseAliases(cellText(row.getCell(idx), formatter, evaluator))
                }.map { it.trim() }.filter { it.isNotBlank() }.distinct()

                val hasAny = nameCn.isNotBlank() ||
                    latin.isNotBlank() ||
                    wet != null ||
                    lvl1.isNotBlank() || lvl2.isNotBlank() || lvl3.isNotBlank() || lvl4.isNotBlank() || lvl5.isNotBlank() ||
                    aliases.isNotEmpty()
                if (!hasAny) continue
                if (nameCn.isBlank()) throw IllegalArgumentException("第 ${r + 1} 行：物种名称为空")

                val wetFixed = when {
                    wet == null -> null
                    !wet.isFinite() -> throw IllegalArgumentException("第 ${r + 1} 行：平均湿重不是有效数值")
                    wet > 0 -> wet
                    wet == 0.0 -> null // 允许用 0 表示空白
                    else -> throw IllegalArgumentException("第 ${r + 1} 行：平均湿重需为正数")
                }

                val taxonomy = if (lvl1.isNotBlank() || lvl2.isNotBlank() || lvl3.isNotBlank() || lvl4.isNotBlank() || lvl5.isNotBlank()) {
                    Taxonomy(lvl1 = lvl1, lvl2 = lvl2, lvl3 = lvl3, lvl4 = lvl4, lvl5 = lvl5)
                } else {
                    null
                }

                out += ImportedSpecies(
                    nameCn = nameCn,
                    nameLatin = latin.ifBlank { null },
                    wetWeightMg = wetFixed,
                    taxonomy = taxonomy,
                    aliases = aliases,
                )
            }

            if (out.isEmpty()) {
                throw IllegalArgumentException("未读取到任何物种（请确认第1个工作表含物种名称列，或按规范表头填写）")
            }

            dedupeImported(out)
        } finally {
            wb.close()
        }
    } ?: throw IllegalArgumentException("无法读取所选文件")
}

/**
 * 给 AI 识别用：提取 Excel 的一个“可读预览表”，用于让模型理解列含义。
 */
suspend fun buildExcelPreviewForAi(
    contentResolver: ContentResolver,
    uri: Uri,
    maxRows: Int = 25,
    maxCols: Int = 8,
): String = withContext(Dispatchers.IO) {
    contentResolver.openInputStream(uri)?.use { input ->
        val wb = WorkbookFactory.create(input)
        try {
            val sheet = wb.getSheetAt(0) ?: return@withContext ""
            val formatter = DataFormatter()
            val evaluator = runCatching { wb.creationHelper.createFormulaEvaluator() }.getOrNull()

            val out = StringBuilder()
            val rows = (0..sheet.lastRowNum).take(maxRows)
            for (r in rows) {
                val row = sheet.getRow(r)
                val cells = (0 until maxCols).map { c -> cellText(row?.getCell(c), formatter, evaluator) }
                if (cells.all { it.isBlank() }) continue
                out.append(cells.joinToString("\t"))
                out.appendLine()
            }
            out.toString().trimEnd()
        } finally {
            wb.close()
        }
    } ?: ""
}
