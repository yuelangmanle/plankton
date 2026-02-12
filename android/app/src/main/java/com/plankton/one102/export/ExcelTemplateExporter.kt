package com.plankton.one102.export

import android.content.Context
import com.plankton.one102.data.AppJson
import com.plankton.one102.domain.BiomassCell
import com.plankton.one102.domain.Dataset
import com.plankton.one102.domain.DatasetCalc
import com.plankton.one102.domain.LVL1_ORDER
import com.plankton.one102.domain.LibraryMeta
import com.plankton.one102.domain.PerPointSpeciesCalc
import com.plankton.one102.domain.Point
import com.plankton.one102.domain.Species
import com.plankton.one102.domain.StratifiedKey
import com.plankton.one102.domain.TaxonomiesJson
import com.plankton.one102.domain.Taxonomy
import com.plankton.one102.domain.TaxonomyRecord
import com.plankton.one102.domain.WaterLayer
import com.plankton.one102.domain.buildStratifiedPointGroups
import com.plankton.one102.domain.calcDataset
import com.plankton.one102.domain.calcStratifiedDominance
import com.plankton.one102.domain.calcStratifiedIndices
import com.plankton.one102.domain.normalizeLvl1Name
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

private const val TEMPLATE_TABLE1 = "templates/table1.xlsx"
private const val TEMPLATE_TABLE2 = "templates/table2.xlsx"
private const val BUILTIN_TAXONOMIES = "taxonomies.json"

private const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

data class ExportBytes(
    val bytes: ByteArray,
    val mime: String = MIME_XLSX,
)

data class ExportLatinOptions(
    val lvl1: Boolean = false, // 类（大类）
    val lvl2: Boolean = false, // 纲
    val lvl3: Boolean = false, // 目
    val lvl4: Boolean = false, // 科
    val lvl5: Boolean = false, // 属
    val species: Boolean = false, // 种
)

fun buildTaxonomyLatinOverrides(records: List<TaxonomyRecord>): Map<String, String?> {
    if (records.isEmpty()) return emptyMap()
    val map = LinkedHashMap<String, String?>()
    for (record in records) {
        val key = record.nameCn.trim()
        if (key.isEmpty()) continue
        val latin = record.nameLatin?.trim().takeIf { !it.isNullOrBlank() } ?: continue
        map[key] = latin
    }
    return map
}

class ExcelTemplateExporter(private val context: Context) {
    @Volatile
    private var builtinTaxonomyCache: BuiltinTaxonomy? = null

    private fun getBuiltinTaxonomy(): BuiltinTaxonomy {
        val cached = builtinTaxonomyCache
        if (cached != null) return cached

        return synchronized(this) {
            val cached2 = builtinTaxonomyCache
            if (cached2 != null) return@synchronized cached2

            val loaded = context.assets.open(BUILTIN_TAXONOMIES).use { input ->
                val raw = input.readBytes().toString(Charsets.UTF_8)
                val json = AppJson.decodeFromString(TaxonomiesJson.serializer(), raw)
                val byName = LinkedHashMap<String, Taxonomy>(json.entries.size)
                val latinByName = LinkedHashMap<String, String?>(json.entries.size)
                val order = LinkedHashMap<String, Int>(json.entries.size)
                for ((i, e) in json.entries.withIndex()) {
                    val key = e.nameCn.trim()
                    if (key.isEmpty()) continue
                    val t = e.taxonomy.copy(
                        lvl1 = normalizeLvl1Name(e.taxonomy.lvl1),
                        lvl2 = e.taxonomy.lvl2.trim(),
                        lvl3 = e.taxonomy.lvl3.trim(),
                        lvl4 = e.taxonomy.lvl4.trim(),
                        lvl5 = e.taxonomy.lvl5.trim(),
                    )
                    byName.putIfAbsent(key, t)
                    latinByName.putIfAbsent(key, e.nameLatin?.trim()?.ifBlank { null })
                    order.putIfAbsent(key, i)
                }

                // Keep historical special cases to avoid exporting empty taxonomy.
                val special = Taxonomy(
                    lvl1 = "桡足类",
                    lvl2 = "",
                    lvl3 = "栉足目",
                    lvl4 = "仙达溞科",
                    lvl5 = "秀体溞属",
                )
                for (name in listOf("无节幼体", "无节幼虫")) {
                    byName.putIfAbsent(name, special)
                    latinByName.putIfAbsent(name, null)
                    order.putIfAbsent(name, Int.MAX_VALUE - 1)
                }

                BuiltinTaxonomy(byName = byName, latinByName = latinByName, orderIndex = order)
            }

            builtinTaxonomyCache = loaded
            loaded
        }
    }

    suspend fun exportTable1(
        dataset: Dataset,
        latinOptions: ExportLatinOptions = ExportLatinOptions(),
        calcOverride: DatasetCalc? = null,
        libraryMeta: LibraryMeta? = null,
        latinNameMap: Map<String, String?> = emptyMap(),
    ): ExportBytes = withContext(Dispatchers.IO) {
        context.assets.open(TEMPLATE_TABLE1).use { input ->
            val wb = WorkbookFactory.create(input)
            try {
                fillTable1(wb, dataset, getBuiltinTaxonomy(), latinOptions, calcOverride, latinNameMap)
                applyLibraryMeta(wb, libraryMeta)
                ExportBytes(write(wb))
            } finally {
                wb.close()
            }
        }
    }

    suspend fun exportTable2(
        dataset: Dataset,
        latinOptions: ExportLatinOptions = ExportLatinOptions(),
        calcOverride: DatasetCalc? = null,
        libraryMeta: LibraryMeta? = null,
        latinNameMap: Map<String, String?> = emptyMap(),
    ): ExportBytes = withContext(Dispatchers.IO) {
        context.assets.open(TEMPLATE_TABLE2).use { input ->
            val wb = WorkbookFactory.create(input)
            try {
                fillTable2(wb, dataset, getBuiltinTaxonomy(), latinOptions, calcOverride, latinNameMap)
                applyLibraryMeta(wb, libraryMeta)
                ExportBytes(write(wb))
            } finally {
                wb.close()
            }
        }
    }

    suspend fun exportTable1ToFile(
        dataset: Dataset,
        latinOptions: ExportLatinOptions = ExportLatinOptions(),
        calcOverride: DatasetCalc? = null,
        libraryMeta: LibraryMeta? = null,
        latinNameMap: Map<String, String?> = emptyMap(),
        outputFile: File,
    ) = withContext(Dispatchers.IO) {
        context.assets.open(TEMPLATE_TABLE1).use { input ->
            val wb = WorkbookFactory.create(input)
            try {
                fillTable1(wb, dataset, getBuiltinTaxonomy(), latinOptions, calcOverride, latinNameMap)
                applyLibraryMeta(wb, libraryMeta)
                FileOutputStream(outputFile).use { out -> wb.write(out) }
            } finally {
                wb.close()
            }
        }
    }

    suspend fun exportSimpleCountTable(
        dataset: Dataset,
        libraryMeta: LibraryMeta? = null,
    ): ExportBytes = withContext(Dispatchers.IO) {
        val wb = XSSFWorkbook()
        try {
            fillSimpleWorkbook(wb, dataset)
            applyLibraryMeta(wb, libraryMeta)
            ExportBytes(write(wb))
        } finally {
            wb.close()
        }
    }

    suspend fun exportSimpleCountTableToFile(
        dataset: Dataset,
        libraryMeta: LibraryMeta? = null,
        outputFile: File,
    ) = withContext(Dispatchers.IO) {
        val wb = XSSFWorkbook()
        try {
            fillSimpleWorkbook(wb, dataset)
            applyLibraryMeta(wb, libraryMeta)
            FileOutputStream(outputFile).use { out -> wb.write(out) }
        } finally {
            wb.close()
        }
    }

    suspend fun exportSimpleTable2(
        dataset: Dataset,
        libraryMeta: LibraryMeta? = null,
    ): ExportBytes = withContext(Dispatchers.IO) {
        val wb = XSSFWorkbook()
        try {
            fillSimpleTable2Workbook(wb, dataset)
            applyLibraryMeta(wb, libraryMeta)
            ExportBytes(write(wb))
        } finally {
            wb.close()
        }
    }

    suspend fun exportSimpleTable2ToFile(
        dataset: Dataset,
        libraryMeta: LibraryMeta? = null,
        outputFile: File,
    ) = withContext(Dispatchers.IO) {
        val wb = XSSFWorkbook()
        try {
            fillSimpleTable2Workbook(wb, dataset)
            applyLibraryMeta(wb, libraryMeta)
            FileOutputStream(outputFile).use { out -> wb.write(out) }
        } finally {
            wb.close()
        }
    }

    suspend fun exportTable2ToFile(
        dataset: Dataset,
        latinOptions: ExportLatinOptions = ExportLatinOptions(),
        calcOverride: DatasetCalc? = null,
        libraryMeta: LibraryMeta? = null,
        latinNameMap: Map<String, String?> = emptyMap(),
        outputFile: File,
    ) = withContext(Dispatchers.IO) {
        context.assets.open(TEMPLATE_TABLE2).use { input ->
            val wb = WorkbookFactory.create(input)
            try {
                fillTable2(wb, dataset, getBuiltinTaxonomy(), latinOptions, calcOverride, latinNameMap)
                applyLibraryMeta(wb, libraryMeta)
                FileOutputStream(outputFile).use { out -> wb.write(out) }
            } finally {
                wb.close()
            }
        }
    }

private fun write(wb: Workbook): ByteArray {
    val out = ByteArrayOutputStream()
    wb.write(out)
    return out.toByteArray()
}
}

private fun fillSimpleWorkbook(wb: Workbook, dataset: Dataset) {
    val calc = calcDataset(dataset)
    val prefix = dataset.titlePrefix.trim()
    val styles = createSimpleSheetStyles(wb)
    fillSimpleSheet(
        wb = wb,
        dataset = dataset,
        sheetName = simpleSheetName("浮游动物计数"),
        title = titleWithPrefix(prefix, "浮游动物计数"),
        calc = calc,
        styles = styles,
        addTotalRow = true,
    ) { sp, p, _ ->
        val count = sp.countsByPointId[p.id] ?: 0
        count.takeIf { it > 0 }
    }
    fillSimpleSheet(
        wb = wb,
        dataset = dataset,
        sheetName = simpleSheetName("浮游动物密度（ind_L）"),
        title = titleWithPrefix(prefix, "浮游动物密度（ind/L）"),
        calc = calc,
        styles = styles,
        addTotalRow = true,
    ) { _, p, per ->
        val count = per?.count ?: 0
        val density = per?.density
        when {
            density != null -> density
            count == 0 -> 0.0
            else -> null
        }
    }
    fillSimpleSheet(
        wb = wb,
        dataset = dataset,
        sheetName = simpleSheetName("浮游动物生物量（mg_L）"),
        title = titleWithPrefix(prefix, "浮游动物生物量（mg/L）"),
        calc = calc,
        styles = styles,
        addTotalRow = true,
    ) { _, p, per ->
        val count = per?.count ?: 0
        when (val b = per?.biomass) {
            is BiomassCell.Value -> b.mgPerL
            BiomassCell.MissingWetWeight -> "未查到湿重"
            else -> if (count == 0) 0.0 else null
        }
    }
    fillSimpleSheet(
        wb = wb,
        dataset = dataset,
        sheetName = simpleSheetName("浮游动物多样性指数"),
        title = titleWithPrefix(prefix, "浮游动物多样性指数"),
        calc = calc,
        styles = styles,
        addTotalRow = true,
    ) { _, p, per ->
        val count = per?.count ?: 0
        val part = per?.pLnP?.let { -it }
        when {
            part != null -> part
            count == 0 -> 0.0
            else -> null
        }
    }
}

private fun fillSimpleTable2Workbook(wb: Workbook, dataset: Dataset) {
    val calc = calcDataset(dataset)
    val prefix = dataset.titlePrefix.trim()
    val styles = createSimpleSheetStyles(wb)
    fillSimpleSheet(
        wb = wb,
        dataset = dataset,
        sheetName = simpleSheetName("浮游动物分布图"),
        title = titleWithPrefix(prefix, "浮游动物采样点物种分布图"),
        calc = calc,
        styles = styles,
        addTotalRow = false,
    ) { sp, p, _ ->
        if ((sp.countsByPointId[p.id] ?: 0) > 0) "+" else null
    }
    fillSimpleSheet(
        wb = wb,
        dataset = dataset,
        sheetName = simpleSheetName("浮游动物密度（ind_L）"),
        title = titleWithPrefix(prefix, "浮游动物密度统计表（ind/L）"),
        calc = calc,
        styles = styles,
        addTotalRow = true,
    ) { _, p, per ->
        val count = per?.count ?: 0
        val density = per?.density
        when {
            density != null -> density
            count == 0 -> 0.0
            else -> null
        }
    }
    fillSimpleSheet(
        wb = wb,
        dataset = dataset,
        sheetName = simpleSheetName("浮游动物生物量（mg_L）"),
        title = titleWithPrefix(prefix, "浮游动物生物量统计表（mg/L）"),
        calc = calc,
        styles = styles,
        addTotalRow = true,
    ) { _, p, per ->
        val count = per?.count ?: 0
        when (val b = per?.biomass) {
            is BiomassCell.Value -> b.mgPerL
            BiomassCell.MissingWetWeight -> "未查到湿重"
            else -> if (count == 0) 0.0 else null
        }
    }
    fillSimpleSheet(
        wb = wb,
        dataset = dataset,
        sheetName = simpleSheetName("浮游动物优势度"),
        title = titleWithPrefix(prefix, "浮游动物优势度"),
        calc = calc,
        styles = styles,
        addTotalRow = false,
    ) { _, _, per ->
        per?.Y
    }
    fillSimpleTable2DiversitySheet(
        wb = wb,
        dataset = dataset,
        calc = calc,
        sheetName = simpleSheetName("浮游动物多样性"),
        title = titleWithPrefix(prefix, "浮游动物多样性表"),
        styles = styles,
    )
}

private fun fillSimpleSheet(
    wb: Workbook,
    dataset: Dataset,
    sheetName: String,
    title: String,
    calc: DatasetCalc,
    styles: SimpleSheetStyles,
    addTotalRow: Boolean,
    valueFor: (Species, Point, PerPointSpeciesCalc?) -> Any?,
) {
    val sheet = wb.createSheet(safeSheetName(sheetName))
    applySimpleColumnWidths(sheet, dataset.points.size)

    val titleRow = sheet.createRow(0)
    val titleCell = titleRow.createCell(0)
    titleCell.setCellValue(title)
    titleCell.cellStyle = styles.title
    sheet.createRow(1)
    for (r in 0..1) {
        val row = sheet.getRow(r) ?: sheet.createRow(r)
        for (c in 0..1) {
            val cell = row.getOrCreateCell(c)
            cell.cellStyle = styles.title
        }
    }
    setMergedRegion(sheet, firstRow = 0, lastRow = 1, firstCol = 0, lastCol = 1)

    val headerRow = sheet.createRow(2)
    val nameCell = headerRow.createCell(0)
    nameCell.setCellValue("名称")
    nameCell.cellStyle = styles.header
    val nameCell2 = headerRow.createCell(1)
    nameCell2.cellStyle = styles.header
    setMergedRegion(sheet, firstRow = 2, lastRow = 2, firstCol = 0, lastCol = 1)
    dataset.points.forEachIndexed { idx, p ->
        val cell = headerRow.createCell(2 + idx)
        cell.setCellValue(p.label.ifBlank { (idx + 1).toString() })
        cell.cellStyle = styles.header
    }

    val concRow = sheet.createRow(3)
    val concCell = concRow.createCell(0)
    concCell.setCellValue("浓缩体积（ml）")
    concCell.cellStyle = styles.header
    val concCell2 = concRow.createCell(1)
    concCell2.cellStyle = styles.header
    setMergedRegion(sheet, firstRow = 3, lastRow = 3, firstCol = 0, lastCol = 1)
    dataset.points.forEachIndexed { idx, p ->
        val cell = concRow.createCell(2 + idx)
        val v = p.vConcMl
        if (v != null && v.isFinite() && v > 0) {
            cell.setCellValue(v)
        }
        cell.cellStyle = styles.header
    }

    val orderIndex = LVL1_ORDER.withIndex().associate { it.value to it.index }
    val sorted = dataset.species.sortedWith(
        compareBy<Species> {
            val key = normalizeLvl1Name(it.taxonomy.lvl1).ifBlank { "未分类" }
            orderIndex[key] ?: Int.MAX_VALUE
        }.thenBy {
            normalizeLvl1Name(it.taxonomy.lvl1).ifBlank { "未分类" }
        }.thenBy { it.nameCn },
    )

    var rowIdx = 4
    var currentGroup = ""
    var groupStart = rowIdx
    val groupRanges = mutableListOf<Triple<String, Int, Int>>()
    for (sp in sorted) {
        val group = normalizeLvl1Name(sp.taxonomy.lvl1).ifBlank { "未分类" }
        if (group != currentGroup) {
            if (currentGroup.isNotEmpty()) {
                groupRanges.add(Triple(currentGroup, groupStart, rowIdx - 1))
            }
            currentGroup = group
            groupStart = rowIdx
        }
        val row = sheet.createRow(rowIdx++)
        val groupCell = row.createCell(0)
        if (row.rowNum == groupStart) {
            groupCell.setCellValue(group)
        }
        groupCell.cellStyle = styles.text
        val nameCellRow = row.createCell(1)
        nameCellRow.setCellValue(sp.nameCn)
        nameCellRow.cellStyle = styles.text
        dataset.points.forEachIndexed { idx, p ->
            val cell = row.createCell(2 + idx)
            val per = calc.perSpeciesByPoint[sp.id]?.get(p.id)
            val value = valueFor(sp, p, per)
            when (value) {
                is Number -> {
                    cell.setCellValue(value.toDouble())
                    cell.cellStyle = styles.number
                }
                is String -> {
                    cell.setCellValue(value)
                    cell.cellStyle = styles.text
                }
                else -> {
                    cell.cellStyle = styles.number
                }
            }
        }
    }
    if (currentGroup.isNotEmpty()) {
        groupRanges.add(Triple(currentGroup, groupStart, rowIdx - 1))
    }

    for ((_, start, end) in groupRanges) {
        if (start < end) {
            setMergedRegion(sheet, firstRow = start, lastRow = end, firstCol = 0, lastCol = 0)
        }
    }

    if (addTotalRow) {
        val totalRow = sheet.createRow(rowIdx)
        val totalCell = totalRow.createCell(0)
        totalCell.setCellValue("总计")
        totalCell.cellStyle = styles.text
        val totalCell2 = totalRow.createCell(1)
        totalCell2.cellStyle = styles.text
        setMergedRegion(sheet, firstRow = rowIdx, lastRow = rowIdx, firstCol = 0, lastCol = 1)
        val firstDataRow = 4
        val lastDataRow = (rowIdx - 1).coerceAtLeast(firstDataRow)
        dataset.points.forEachIndexed { idx, _ ->
            val colIndex = 2 + idx
            val cell = totalRow.createCell(colIndex)
            val colLetter = columnName(colIndex)
            val from = firstDataRow + 1
            val to = lastDataRow + 1
            cell.cellFormula = "SUM(${colLetter}${from}:${colLetter}${to})"
            cell.cellStyle = styles.number
        }
    }
}

private fun fillSimpleTable2DiversitySheet(
    wb: Workbook,
    dataset: Dataset,
    calc: DatasetCalc,
    sheetName: String,
    title: String,
    styles: SimpleSheetStyles,
) {
    val sheet = wb.createSheet(safeSheetName(sheetName))
    applySimpleColumnWidths(sheet, dataset.points.size, nameCols = 1)

    val titleRow = sheet.createRow(0)
    val titleCell = titleRow.createCell(0)
    titleCell.setCellValue(title)
    titleCell.cellStyle = styles.title
    sheet.createRow(1)
    for (r in 0..1) {
        val row = sheet.getRow(r) ?: sheet.createRow(r)
        for (c in 0..1) {
            val cell = row.getOrCreateCell(c)
            cell.cellStyle = styles.title
        }
    }
    setMergedRegion(sheet, firstRow = 0, lastRow = 1, firstCol = 0, lastCol = 1)

    val headerRow = sheet.createRow(2)
    val headerCell = headerRow.createCell(0)
    headerCell.setCellValue("指标")
    headerCell.cellStyle = styles.header
    dataset.points.forEachIndexed { idx, p ->
        val cell = headerRow.createCell(1 + idx)
        cell.setCellValue(p.label.ifBlank { (idx + 1).toString() })
        cell.cellStyle = styles.header
    }

    val rows = listOf(
        "H'" to { p: Point -> calc.pointIndexById[p.id]?.H },
        "D" to { p: Point -> calc.pointIndexById[p.id]?.D },
        "J" to { p: Point -> calc.pointIndexById[p.id]?.J },
    )
    rows.forEachIndexed { idx, def ->
        val row = sheet.createRow(3 + idx)
        val labelCell = row.createCell(0)
        labelCell.setCellValue(def.first)
        labelCell.cellStyle = styles.text
        dataset.points.forEachIndexed { pi, p ->
            val cell = row.createCell(1 + pi)
            val value = def.second(p)
            if (value != null) {
                cell.setCellValue(value)
            }
            cell.cellStyle = styles.number
        }
    }
}

private fun simpleSheetName(suffix: String): String {
    return safeSheetName(suffix)
}

private fun safeSheetName(raw: String, fallback: String = "Sheet"): String {
    val trimmed = raw.trim().ifBlank { fallback }
    val cleaned = trimmed.replace(Regex("[\\\\/:*?\\[\\]]"), "_")
    return if (cleaned.length <= 31) cleaned else cleaned.take(31)
}

private data class SimpleSheetStyles(
    val title: org.apache.poi.ss.usermodel.CellStyle,
    val header: org.apache.poi.ss.usermodel.CellStyle,
    val text: org.apache.poi.ss.usermodel.CellStyle,
    val number: org.apache.poi.ss.usermodel.CellStyle,
)

private fun createSimpleSheetStyles(wb: Workbook): SimpleSheetStyles {
    val titleFont = wb.createFont().apply {
        fontName = "宋体"
        fontHeightInPoints = 11
        bold = true
    }
    val headerFont = wb.createFont().apply {
        fontName = "Times New Roman"
        fontHeightInPoints = 11
    }
    val textFont = wb.createFont().apply {
        fontName = "宋体"
        fontHeightInPoints = 11
    }
    val numberFont = wb.createFont().apply {
        fontName = "Times New Roman"
        fontHeightInPoints = 11
    }

    val borderStyle = BorderStyle.THIN
    fun baseStyle(font: org.apache.poi.ss.usermodel.Font): org.apache.poi.ss.usermodel.CellStyle =
        wb.createCellStyle().apply {
            setFont(font)
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            borderTop = borderStyle
            borderBottom = borderStyle
            borderLeft = borderStyle
            borderRight = borderStyle
        }

    val title = wb.createCellStyle().apply {
        setFont(titleFont)
        alignment = HorizontalAlignment.CENTER
        verticalAlignment = VerticalAlignment.CENTER
        fillForegroundColor = org.apache.poi.ss.usermodel.IndexedColors.LIGHT_CORNFLOWER_BLUE.index
        fillPattern = org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND
    }

    val header = baseStyle(headerFont)
    val text = baseStyle(textFont)
    val number = baseStyle(numberFont)
    return SimpleSheetStyles(title = title, header = header, text = text, number = number)
}

private fun applySimpleColumnWidths(sheet: Sheet, pointCount: Int, nameCols: Int = 2) {
    val colAWidth = 13.3
    val colBWidth = 23.6
    sheet.setColumnWidth(0, (colAWidth * 256).toInt())
    if (nameCols >= 2) {
        sheet.setColumnWidth(1, (colBWidth * 256).toInt())
    }
    for (i in 0 until pointCount) {
        sheet.setColumnWidth(nameCols + i, (13.0 * 256).toInt())
    }
}

private fun columnName(index: Int): String {
    var i = index
    val sb = StringBuilder()
    var n = i
    while (n >= 0) {
        val rem = n % 26
        sb.append(('A'.code + rem).toChar())
        n = n / 26 - 1
    }
    return sb.reverse().toString()
}

private fun applyLibraryMeta(wb: Workbook, meta: LibraryMeta?) {
    if (meta == null) return
    val xssf = when (wb) {
        is XSSFWorkbook -> wb
        is SXSSFWorkbook -> wb.xssfWorkbook
        else -> null
    } ?: return
    val props = xssf.properties.customProperties
    props.addProperty("taxonomy_version", meta.taxonomyVersion.toString())
    props.addProperty("taxonomy_custom_count", meta.taxonomyCustomCount.toString())
    props.addProperty("taxonomy_updated_at", meta.taxonomyUpdatedAt.orEmpty())
    props.addProperty("wetweight_version", meta.wetWeightVersion.toString())
    props.addProperty("wetweight_custom_count", meta.wetWeightCustomCount.toString())
    props.addProperty("wetweight_updated_at", meta.wetWeightUpdatedAt.orEmpty())
    props.addProperty("alias_source", meta.aliasSource)
    props.addProperty("alias_count", meta.aliasCount.toString())
    props.addProperty("alias_updated_at", meta.aliasUpdatedAt.orEmpty())
}

private fun toStreamingWorkbook(wb: Workbook, windowSize: Int = 200): Workbook {
    if (wb is XSSFWorkbook) {
        return SXSSFWorkbook(wb, windowSize).apply { setCompressTempFiles(true) }
    }
    return wb
}

private fun closeWorkbook(wb: Workbook, base: Workbook) {
    when (wb) {
        is SXSSFWorkbook -> {
            runCatching { wb.dispose() }
            runCatching { wb.close() }
            if (base !== wb) runCatching { base.close() }
        }
        else -> runCatching { wb.close() }
    }
}

private fun titleWithPrefix(prefix: String, suffix: String): String {
    val p = prefix.trim()
    return if (p.isNotEmpty()) "$p$suffix" else suffix
}

private data class BuiltinTaxonomy(
    val byName: Map<String, Taxonomy>,
    val latinByName: Map<String, String?>,
    val orderIndex: Map<String, Int>,
)

private data class SpeciesSortRow(
    val groupRank: Int,
    val taxonomyOrder: Int,
    val fallbackIndex: Int,
    val species: Species,
)

private fun groupRank(lvl1: String): Int {
    val v = normalizeLvl1Name(lvl1)
    val idx = LVL1_ORDER.indexOf(v)
    return if (idx >= 0) idx else LVL1_ORDER.size
}

private fun fillFromBuiltin(cur: Taxonomy, builtin: Taxonomy?): Taxonomy {
    if (builtin == null) return cur
    fun pick(curVal: String, builtinVal: String): String = if (curVal.isBlank()) builtinVal else curVal
    return cur.copy(
        lvl1 = pick(cur.lvl1, builtin.lvl1),
        lvl2 = pick(cur.lvl2, builtin.lvl2),
        lvl3 = pick(cur.lvl3, builtin.lvl3),
        lvl4 = pick(cur.lvl4, builtin.lvl4),
        lvl5 = pick(cur.lvl5, builtin.lvl5),
    )
}

private fun prepareSpeciesForExport(species: List<Species>, builtin: BuiltinTaxonomy): List<Species> {
    val rows = species.mapIndexed { idx, sp ->
        val key = sp.nameCn.trim()
        val filledTaxonomy = fillFromBuiltin(sp.taxonomy, builtin.byName[key]).let { t ->
            t.copy(
                lvl1 = normalizeLvl1Name(t.lvl1),
                lvl2 = t.lvl2.trim(),
                lvl3 = t.lvl3.trim(),
                lvl4 = t.lvl4.trim(),
                lvl5 = t.lvl5.trim(),
            )
        }
        val latin = sp.nameLatin.trim().ifBlank { builtin.latinByName[key].orEmpty() }
        val fixed = sp.copy(taxonomy = filledTaxonomy, nameLatin = latin)
        val gRank = groupRank(fixed.taxonomy.lvl1)
        val orderIdx = builtin.orderIndex[key] ?: Int.MAX_VALUE
        SpeciesSortRow(groupRank = gRank, taxonomyOrder = orderIdx, fallbackIndex = idx, species = fixed)
    }
    return rows
        .sortedWith(compareBy<SpeciesSortRow>({ it.groupRank }, { it.taxonomyOrder }, { it.fallbackIndex }))
        .map { it.species }
}

private fun fillTable1(
    wb: Workbook,
    dataset: Dataset,
    builtin: BuiltinTaxonomy,
    latinOptions: ExportLatinOptions,
    calcOverride: DatasetCalc?,
    latinNameMap: Map<String, String?>,
) {
    val points = dataset.points
    val calc = calcOverride ?: calcDataset(dataset)
    val speciesRows = prepareSpeciesForExport(dataset.species, builtin)

    val sheets = listOf(
        "浮游动物计数" to "浮游动物计数",
        "浮游动物密度" to "浮游动物密度（ind/L）",
        "浮游动物生物量" to "浮游动物生物量（mg/L）",
        "浮游动物多样性指数H'表" to "浮游动物香农多样性指数H",
    )

    for ((sheetName, titleSuffix) in sheets) {
        val ws = wb.getSheet(sheetName) ?: continue

        // Title in A1
        ws.getOrCreateCell(0, 0).setCellValue(titleWithPrefix(dataset.titlePrefix, titleSuffix))

        // Headers (template layout): row 3 labels, row 4 vConc
        val headerRow = 2
        val concRow = 3
        val firstPointCol = 6 // G (0-based)

        writePointLabels(ws, headerRow, firstPointCol, points)
        writeConcRow(ws, concRow, firstPointCol, points)

        // Data region starts at row 5, total row contains "总计" in col A.
        val startRow = 4
        val totalRow = findRowByCellText(ws, colIndex = 0, text = "总计") ?: 48
        ensureSpeciesCapacity(ws, startRow, totalRow, required = speciesRows.size)

        // Shrink extra template rows if needed, then re-locate total row.
        var totalRow2 = findRowByCellText(ws, colIndex = 0, text = "总计") ?: totalRow
        totalRow2 = shrinkSpeciesCapacity(ws, startRow, totalRow2, required = speciesRows.size)
        totalRow2 = findRowByCellText(ws, colIndex = 0, text = "总计") ?: totalRow2

        // Clear old merges in taxonomy region (A-F) and rebuild.
        removeMergedRegionsIntersecting(ws, startRow, ws.lastRowNum, 0, 5)

        // Fill rows
        for (i in 0 until (totalRow2 - startRow)) {
            val rowIndex = startRow + i
            val row = ws.getOrCreateRow(rowIndex)
            if (i < speciesRows.size) {
                val sp = speciesRows[i]
                writeTaxonomyRow(row, sp, latinOptions, latinNameMap)

                for (pi in points.indices) {
                    val col = firstPointCol + pi
                    val cell = row.getOrCreateCell(col)
                    when (sheetName) {
                        "浮游动物计数" -> cell.setCellValue((sp.countsByPointId[points[pi].id] ?: 0).toDouble())
                        "浮游动物密度" -> {
                            val v = calc.perSpeciesByPoint[sp.id]?.get(points[pi].id)?.density
                            cell.setNullableNumber(v)
                        }

                        "浮游动物生物量" -> {
                            val v = calc.perSpeciesByPoint[sp.id]?.get(points[pi].id)?.biomass
                            cell.setBiomassCell(v)
                        }

                        "浮游动物多样性指数H'表" -> {
                            val v = calc.perSpeciesByPoint[sp.id]?.get(points[pi].id)?.pLnP
                            cell.setNullableNumber(v)
                        }
                    }
                }

                // Clear remaining point columns (keep style)
                clearExtraPointColumns(row, fromCol = firstPointCol + points.size, toCol = row.lastCellNum.toInt().coerceAtLeast(firstPointCol + points.size))
            } else {
                clearRowValues(row, upToCol = firstPointCol + points.size)
            }
        }

        // Merge taxonomy columns A-E similar to web logic.
        mergeTaxonomyColumns(ws, startRow, speciesRows, latinOptions, latinNameMap)

        // Total row
        val total = ws.getOrCreateRow(totalRow2)
        total.getOrCreateCell(0).setCellValue("总计")
        // clear taxonomy columns B-F
        for (c in 1..5) total.getOrCreateCell(c).setBlank()
        setMergedRegion(ws, firstRow = totalRow2, lastRow = totalRow2, firstCol = 0, lastCol = 5)

        for (pi in points.indices) {
            val p = points[pi]
            val col = firstPointCol + pi
            val cell = total.getOrCreateCell(col)
            when (sheetName) {
                "浮游动物计数" -> {
                    val v = calc.pointIndexById[p.id]?.totalCount
                    cell.setNullableNumber(v?.toDouble())
                }

                "浮游动物密度" -> {
                    if (p.vConcMl == null || p.vOrigL <= 0) {
                        cell.setBlank()
                    } else {
                        val sum = dataset.species.map { sp -> calc.perSpeciesByPoint[sp.id]?.get(p.id)?.density }.sumNullable()
                        cell.setNullableNumber(sum)
                    }
                }

                "浮游动物生物量" -> {
                    val cells = dataset.species.map { sp -> calc.perSpeciesByPoint[sp.id]?.get(p.id)?.biomass }
                    cell.setBiomassTotal(cells)
                }

                "浮游动物多样性指数H'表" -> {
                    val v = calc.pointIndexById[p.id]?.H
                    cell.setNullableNumber(v)
                }
            }
        }

        // Hide point columns beyond N to match "列数由点位决定"
        hideExtraPointColumns(ws, firstPointCol, points.size, keepFromTemplate = 80)
        removeRowsAfter(ws, totalRow2)
    }
}

private fun fillTable2(
    wb: Workbook,
    dataset: Dataset,
    builtin: BuiltinTaxonomy,
    latinOptions: ExportLatinOptions,
    calcOverride: DatasetCalc?,
    latinNameMap: Map<String, String?>,
) {
    val points = dataset.points
    val calc = calcOverride ?: calcDataset(dataset)
    val speciesRows = prepareSpeciesForExport(dataset.species, builtin)

    // --- 分布图 ---
    wb.getSheet("浮游动物采样点物种分布图")?.let { ws ->
        val headerPointRow = 1 // row2
        val firstPointCol = 4 // E (0-based)

        ensurePointColumns(ws, headerPointRow, firstPointCol, points.size)

        // Update merged header E1:...
        setMergedRegion(ws, firstRow = 0, lastRow = 0, firstCol = firstPointCol, lastCol = firstPointCol + points.size - 1)

        // Header labels
        for (i in 0 until points.size) {
            ws.getOrCreateCell(headerPointRow, firstPointCol + i).setCellValue(points[i].label)
            ws.setColumnHidden(firstPointCol + i, false)
        }
        // Hide remaining template point columns
        hideExtraPointColumns(ws, firstPointCol, points.size, keepFromTemplate = 200)

        val startRow = 2 // row3
        val existingCapacity = (ws.lastRowNum - startRow + 1).coerceAtLeast(0)
        if (speciesRows.size > existingCapacity) {
            val templateIndex = (startRow + existingCapacity - 1).coerceAtLeast(startRow)
            val templateRow = ws.getOrCreateRow(templateIndex)
            for (i in existingCapacity until speciesRows.size) {
                val newRow = ws.createRow(startRow + i)
                copyRowStyle(templateRow, newRow)
            }
        }

        if (speciesRows.size < existingCapacity) {
            shrinkSpeciesCapacityNoTotal(ws, startRow, required = speciesRows.size, existingCapacity = existingCapacity)
        }
        val dataCapacity = if (speciesRows.size >= existingCapacity) speciesRows.size else maxOf(speciesRows.size, 1)

        for (i in 0 until dataCapacity) {
            val rowIndex = startRow + i
            val row = ws.getOrCreateRow(rowIndex)
            if (i < speciesRows.size) {
                val sp = speciesRows[i]
                val lvl1 = formatLvl1(sp.taxonomy.lvl1, latinOptions.lvl1, latinNameMap)
                val lvl2 = formatTaxValue(
                    sp.taxonomy.lvl2,
                    latinOptions.lvl2,
                    fallbackLatin = lookupLatin(sp.taxonomy.lvl2, latinNameMap),
                )
                val lvl5 = formatTaxValue(
                    sp.taxonomy.lvl5,
                    latinOptions.lvl5,
                    fallbackLatin = lookupLatin(sp.taxonomy.lvl5, latinNameMap),
                )
                val spName = formatSpeciesName(sp, latinOptions.species)

                row.getOrCreateCell(0).setCellValue(lvl1)
                row.getOrCreateCell(1).setCellValue(lvl2)
                row.getOrCreateCell(2).setCellValue(lvl5)
                row.getOrCreateCell(3).setCellValue(spName)

                for (pi in points.indices) {
                    val count = sp.countsByPointId[points[pi].id] ?: 0
                    val cell = row.getOrCreateCell(firstPointCol + pi)
                    if (count > 0) cell.setCellValue("+") else cell.setBlank()
                }

                clearExtraPointColumns(
                    row,
                    fromCol = firstPointCol + points.size,
                    toCol = row.lastCellNum.toInt().coerceAtLeast(firstPointCol + points.size),
                )

            } else {
                clearRowValues(row, upToCol = row.lastCellNum.toInt().coerceAtLeast(0))
            }
        }

        if (speciesRows.isNotEmpty()) {
            removeMergedRegionsIntersecting(ws, startRow, startRow + speciesRows.size - 1, 0, 2)
            val rowsForMerge = speciesRows.map {
                listOf(
                    formatLvl1(it.taxonomy.lvl1, latinOptions.lvl1, latinNameMap),
                    formatTaxValue(
                        it.taxonomy.lvl2,
                        latinOptions.lvl2,
                        fallbackLatin = lookupLatin(it.taxonomy.lvl2, latinNameMap),
                    ),
                    formatTaxValue(
                        it.taxonomy.lvl5,
                        latinOptions.lvl5,
                        fallbackLatin = lookupLatin(it.taxonomy.lvl5, latinNameMap),
                    ),
                )
            }
            mergeHierarchicalColumns(ws, startRow, rowsForMerge, colOffset = 0)
        }
    }

    // --- 密度统计 ---
    wb.getSheet("浮游动物密度统计表")?.let { ws ->
        fillStatSheet(
            ws = ws,
            points = points,
            species = speciesRows,
            totalRowText = "总计",
            valueFor = { sp, p -> calc.perSpeciesByPoint[sp.id]?.get(p.id)?.density },
            totalForPoint = { p ->
                if (p.vConcMl == null || p.vOrigL <= 0) null
                else dataset.species.map { sp -> calc.perSpeciesByPoint[sp.id]?.get(p.id)?.density }.sumNullable()
            },
            latinOptions = latinOptions,
            latinNameMap = latinNameMap,
        )
    }

    // --- 生物量统计 ---
    wb.getSheet("浮游动物生物量统计表")?.let { ws ->
        fillStatSheet(
            ws = ws,
            points = points,
            species = speciesRows,
            totalRowText = "总计",
            valueFor = { sp, p -> calc.perSpeciesByPoint[sp.id]?.get(p.id)?.biomass.toBiomassExcelValue() },
            totalForPoint = { p ->
                val cells = dataset.species.map { sp -> calc.perSpeciesByPoint[sp.id]?.get(p.id)?.biomass }
                biomassTotal(cells)
            },
            latinOptions = latinOptions,
            latinNameMap = latinNameMap,
        )
    }

    // --- 优势度 ---
    wb.getSheet("浮游动物优势度")?.let { ws ->
        fillStatSheet(
            ws = ws,
            points = points,
            species = speciesRows,
            totalRowText = null,
            valueFor = { sp, p -> calc.perSpeciesByPoint[sp.id]?.get(p.id)?.Y },
            totalForPoint = { null },
            latinOptions = latinOptions,
            latinNameMap = latinNameMap,
        )
    }

    // --- 多样性 ---
    wb.getSheet("浮游动物多样性表")?.let { ws ->
        val headerRow = 0
        val firstPointCol = 1 // B
        ensurePointColumns(ws, headerRow, firstPointCol, points.size)
        for (i in points.indices) {
            ws.getOrCreateCell(headerRow, firstPointCol + i).setCellValue(points[i].label)
            ws.setColumnHidden(firstPointCol + i, false)
        }
        hideExtraPointColumns(ws, firstPointCol, points.size, keepFromTemplate = 200)

        val rows = listOf(
            "H'" to { p: Point -> calc.pointIndexById[p.id]?.H },
            "D" to { p: Point -> calc.pointIndexById[p.id]?.D },
            "J" to { p: Point -> calc.pointIndexById[p.id]?.J },
        )

        for ((i, rowDef) in rows.withIndex()) {
            val rowIndex = 1 + i
            val row = ws.getOrCreateRow(rowIndex)
            row.getOrCreateCell(0).setCellValue(rowDef.first)
            for (pi in points.indices) {
                row.getOrCreateCell(firstPointCol + pi).setNullableNumber(rowDef.second(points[pi]))
            }
        }
    }

    if (dataset.stratification.enabled) {
        fillStratifiedSummarySheets(
            wb = wb,
            dataset = dataset,
            calc = calc,
            speciesById = speciesRows.associateBy { it.id },
            latinOptions = latinOptions,
            latinNameMap = latinNameMap,
        )
    }
}

private fun fillStratifiedSummarySheets(
    wb: Workbook,
    dataset: Dataset,
    calc: DatasetCalc,
    speciesById: Map<String, Species>,
    latinOptions: ExportLatinOptions,
    latinNameMap: Map<String, String?>,
) {
    val groups = buildStratifiedPointGroups(dataset)
    if (groups.isEmpty()) return

    val styles = createSimpleTableStyles(wb)
    val indices = calcStratifiedIndices(dataset, groups).associateBy { it.key }

    val sitesByLayer: Map<WaterLayer, List<String>> = groups.keys
        .groupBy({ it.layer }, { it.site })
        .mapValues { (_, list) -> sortSites(list) }

    fillStratifiedDiversitySheet(wb, sitesByLayer, indices, styles)
    fillStratifiedGroupTotalsSheet(
        wb = wb,
        sheetName = "分层密度",
        sitesByLayer = sitesByLayer,
        groups = groups,
        dataset = dataset,
        valueFor = { sp, p -> calc.perSpeciesByPoint[sp.id]?.get(p.id)?.density },
        aggregatePointValues = { values -> values.sumNullable() },
        styles = styles,
        latinOptions = latinOptions,
        latinNameMap = latinNameMap,
    )
    fillStratifiedGroupTotalsSheet(
        wb = wb,
        sheetName = "分层生物量",
        sitesByLayer = sitesByLayer,
        groups = groups,
        dataset = dataset,
        valueFor = { sp, p -> calc.perSpeciesByPoint[sp.id]?.get(p.id)?.biomass },
        aggregatePointValues = { values -> biomassTotal(values) },
        styles = styles,
        latinOptions = latinOptions,
        latinNameMap = latinNameMap,
    )

    val dominant = calcStratifiedDominance(dataset, groups)
        .asSequence()
        .filter { it.isDominant == true && it.count > 0 }
        .sortedWith(compareBy({ layerOrderIndex(it.key.layer) }, { siteSortKey(it.key.site) }, { -(it.Y ?: 0.0) }))
        .toList()

    val wsDom = wb.createFreshSheet("分层优势度")
    val header = listOf("水层", "点位", "大类", "物种", "n", "Y")
    for (c in header.indices) {
        val cell = wsDom.getOrCreateCell(0, c)
        cell.setCellValue(header[c])
        cell.cellStyle = styles.header
    }
    wsDom.setColumnWidth(0, 8 * 256)
    wsDom.setColumnWidth(1, 14 * 256)
    wsDom.setColumnWidth(2, 14 * 256)
    wsDom.setColumnWidth(3, 22 * 256)
    wsDom.setColumnWidth(4, 8 * 256)
    wsDom.setColumnWidth(5, 10 * 256)

    var r = 1
    for (row in dominant) {
        val sp = speciesById[row.speciesId] ?: continue
        wsDom.getOrCreateCell(r, 0).apply {
            setCellValue(row.key.layer.label)
            cellStyle = styles.text
        }
        wsDom.getOrCreateCell(r, 1).apply {
            setCellValue(siteDisplay(row.key.site))
            cellStyle = styles.text
        }
        wsDom.getOrCreateCell(r, 2).apply {
            setCellValue(formatLvl1(sp.taxonomy.lvl1, latinOptions.lvl1, latinNameMap))
            cellStyle = styles.text
        }
        wsDom.getOrCreateCell(r, 3).apply {
            val label = formatSpeciesName(sp, latinOptions.species).ifBlank { sp.id }
            setCellValue(label)
            cellStyle = styles.text
        }
        wsDom.getOrCreateCell(r, 4).apply {
            setCellValue(row.count.toDouble())
            cellStyle = styles.number
        }
        wsDom.getOrCreateCell(r, 5).apply {
            setNullableNumber(row.Y)
            cellStyle = styles.number
        }
        r += 1
    }
}

private fun fillStratifiedDiversitySheet(
    wb: Workbook,
    sitesByLayer: Map<WaterLayer, List<String>>,
    indices: Map<StratifiedKey, com.plankton.one102.domain.StratifiedSiteLayerIndex>,
    styles: SimpleTableStyles,
) {
    val ws = wb.createFreshSheet("分层多样性指数")
    ws.setColumnWidth(0, 8 * 256)
    ws.setColumnWidth(1, 18 * 256)

    val metrics: List<Pair<String, (com.plankton.one102.domain.StratifiedSiteLayerIndex) -> Any?>> = listOf(
        "总数(N)" to { it.totalCount },
        "总类数(S)" to { it.speciesCountS },
        "香农指数(H')" to { it.H },
        "丰富度指数(D)" to { it.D },
        "均匀度(J)" to { it.J },
    )

    var rowStart = 0
    for (layer in listOf(WaterLayer.Upper, WaterLayer.Middle, WaterLayer.Lower)) {
        val sites = sitesByLayer[layer].orEmpty()
        if (sites.isEmpty()) continue

        ws.getOrCreateCell(rowStart, 1).apply {
            setCellValue("点位")
            cellStyle = styles.header
        }
        for (i in sites.indices) {
            ws.setColumnWidth(2 + i, 10 * 256)
            ws.getOrCreateCell(rowStart, 2 + i).apply {
                setCellValue(sites[i])
                cellStyle = styles.header
            }
        }

        val firstMetricRow = rowStart + 1
        val lastMetricRow = firstMetricRow + metrics.size - 1
        ws.getOrCreateCell(firstMetricRow, 0).apply {
            setCellValue(layer.label)
            cellStyle = styles.header
        }
        setMergedRegion(ws, firstRow = firstMetricRow, lastRow = lastMetricRow, firstCol = 0, lastCol = 0)

        for ((mi, metric) in metrics.withIndex()) {
            val rr = firstMetricRow + mi
            ws.getOrCreateCell(rr, 1).apply {
                setCellValue(metric.first)
                cellStyle = styles.header
            }
            for (si in sites.indices) {
                val site = sites[si]
                val idx = indices[StratifiedKey(layer = layer, site = site)] ?: continue
                val v = metric.second(idx)
                val cell = ws.getOrCreateCell(rr, 2 + si)
                when (v) {
                    null -> cell.setBlank()
                    is Int -> cell.setCellValue(v.toDouble())
                    is Double -> cell.setNullableNumber(v)
                    else -> cell.setCellValue(v.toString())
                }
                cell.cellStyle = styles.number
            }
        }

        rowStart = lastMetricRow + 3
    }
}

private fun <T> fillStratifiedGroupTotalsSheet(
    wb: Workbook,
    sheetName: String,
    sitesByLayer: Map<WaterLayer, List<String>>,
    groups: Map<StratifiedKey, List<Point>>,
    dataset: Dataset,
    valueFor: (Species, Point) -> T?,
    aggregatePointValues: (List<T?>) -> Any?,
    styles: SimpleTableStyles,
    latinOptions: ExportLatinOptions,
    latinNameMap: Map<String, String?>,
) {
    val ws = wb.createFreshSheet(sheetName)
    ws.setColumnWidth(0, 8 * 256)
    ws.setColumnWidth(1, 14 * 256)
    ws.getOrCreateCell(0, 0).apply {
        setCellValue("swl")
        cellStyle = styles.header
    }
    ws.getOrCreateCell(0, 1).apply {
        setCellValue("点位")
        cellStyle = styles.header
    }
    for ((i, g) in LVL1_ORDER.withIndex()) {
        ws.setColumnWidth(2 + i, 14 * 256)
        ws.getOrCreateCell(0, 2 + i).apply {
            setCellValue(formatLvl1(g, latinOptions.lvl1, latinNameMap))
            cellStyle = styles.header
        }
    }
    ws.setColumnWidth(2 + LVL1_ORDER.size, 14 * 256)
    ws.getOrCreateCell(0, 2 + LVL1_ORDER.size).apply {
        setCellValue("总计")
        cellStyle = styles.header
    }

    var row = 1
    for (layer in listOf(WaterLayer.Upper, WaterLayer.Middle, WaterLayer.Lower)) {
        val sites = sitesByLayer[layer].orEmpty()
        if (sites.isEmpty()) continue

        val startRow = row
        for (site in sites) {
            val key = StratifiedKey(layer = layer, site = site)
            val pts = groups[key].orEmpty()
            if (pts.isEmpty()) continue

            val rowIndex = row
            ws.getOrCreateCell(rowIndex, 0).apply {
                if (rowIndex == startRow) setCellValue(layer.label) else setBlank()
                cellStyle = styles.header
            }
            ws.getOrCreateCell(rowIndex, 1).apply {
                setCellValue(siteDisplay(site))
                cellStyle = styles.text
            }

            val groupValues = mutableListOf<Any?>()
            for ((gi, groupName) in LVL1_ORDER.withIndex()) {
                val perPoint = pts.mapNotNull { p ->
                    if (sheetName.contains("密度") && (p.vConcMl == null || p.vOrigL <= 0)) return@mapNotNull null
                    val values = dataset.species
                        .asSequence()
                        .filter { normalizeLvl1Name(it.taxonomy.lvl1) == groupName }
                        .map { valueFor(it, p) }
                        .toList()
                    aggregatePointValues(values)
                }
                val v = aggregateAcrossPoints(perPoint)
                groupValues += v

                val cell = ws.getOrCreateCell(rowIndex, 2 + gi)
                when (v) {
                    null -> cell.setBlank()
                    is Double -> cell.setNullableNumber(v)
                    is String -> cell.setCellValue(v)
                    is Int -> cell.setCellValue(v.toDouble())
                    else -> cell.setCellValue(v.toString())
                }
                cell.cellStyle = if (v is String) styles.text else styles.number
            }

            val total = when {
                groupValues.any { it is String } -> (groupValues.firstOrNull { it is String } as? String)
                else -> {
                    val nums = groupValues.filterIsInstance<Double>().filter { it.isFinite() }
                    if (nums.isEmpty()) null else nums.sum()
                }
            }
            val totalCell = ws.getOrCreateCell(rowIndex, 2 + LVL1_ORDER.size)
            when (total) {
                null -> totalCell.setBlank()
                is Double -> totalCell.setCellValue(total)
                is String -> totalCell.setCellValue(total)
                else -> totalCell.setCellValue(total.toString())
            }
            totalCell.cellStyle = if (total is String) styles.text else styles.number

            row += 1
        }
        val endRow = row - 1
        setMergedRegion(ws, firstRow = startRow, lastRow = endRow, firstCol = 0, lastCol = 0)
        row += 1
    }
}

private fun Workbook.createFreshSheet(name: String): Sheet {
    val existing = getSheet(name) ?: return createSheet(name)
    val idx = getSheetIndex(existing)
    removeSheetAt(idx)
    return createSheet(name)
}

private fun siteDisplay(site: String): String {
    val trimmed = site.trim()
    if (trimmed.isEmpty()) return "未命名"
    return if (trimmed.toIntOrNull() != null) "${trimmed}号点位" else trimmed
}

private fun sortSites(sites: List<String>): List<String> {
    return sites.distinct().sortedWith(compareBy({ siteSortKey(it) }, { it }))
}

private fun siteSortKey(site: String): Long {
    val n = site.trim().toLongOrNull()
    return n ?: Long.MAX_VALUE
}

private fun layerOrderIndex(layer: WaterLayer): Int {
    return when (layer) {
        WaterLayer.Upper -> 0
        WaterLayer.Middle -> 1
        WaterLayer.Lower -> 2
    }
}

private fun aggregateAcrossPoints(values: List<Any?>): Any? {
    if (values.isEmpty()) return null
    if (values.any { it is String }) return values.firstOrNull { it is String } as? String
    val nums = values.filterIsInstance<Double>().filter { it.isFinite() }
    if (nums.isEmpty()) return null
    return nums.average()
}

private data class SimpleTableStyles(
    val header: org.apache.poi.ss.usermodel.CellStyle,
    val text: org.apache.poi.ss.usermodel.CellStyle,
    val number: org.apache.poi.ss.usermodel.CellStyle,
)

private fun createSimpleTableStyles(wb: Workbook): SimpleTableStyles {
    val fmt = wb.createDataFormat()

    fun baseStyle(): org.apache.poi.ss.usermodel.CellStyle {
        return wb.createCellStyle().apply {
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
        }
    }

    val headerFont = wb.createFont().apply { bold = true }
    val header = baseStyle().apply { setFont(headerFont) }

    val text = baseStyle().apply { alignment = HorizontalAlignment.LEFT }

    val number = baseStyle().apply {
        alignment = HorizontalAlignment.CENTER
        dataFormat = fmt.getFormat("0.######")
    }

    return SimpleTableStyles(header = header, text = text, number = number)
}

private fun fillStatSheet(
    ws: org.apache.poi.ss.usermodel.Sheet,
    points: List<Point>,
    species: List<Species>,
    totalRowText: String?,
    valueFor: (Species, Point) -> Any?,
    totalForPoint: (Point) -> Any?,
    latinOptions: ExportLatinOptions,
    latinNameMap: Map<String, String?>,
) {
    val headerRow = 0
    val firstPointCol = 2 // C
    ensurePointColumns(ws, headerRow, firstPointCol, points.size)

    // Header labels
    for (i in points.indices) {
        ws.getOrCreateCell(headerRow, firstPointCol + i).setCellValue(points[i].label)
        ws.setColumnHidden(firstPointCol + i, false)
    }
    hideExtraPointColumns(ws, firstPointCol, points.size, keepFromTemplate = 200)

    val startRow = 1 // row2
    val totalRow = totalRowText?.let { findRowByCellText(ws, 0, it) }
    val existingCapacity = (if (totalRow != null) totalRow - startRow else (ws.lastRowNum - startRow + 1)).coerceAtLeast(0)
    if (species.size > existingCapacity) {
        val delta = species.size - existingCapacity
        if (totalRow != null) {
            ws.shiftRows(totalRow, ws.lastRowNum, delta, true, false)
            // Copy style from last species row before total
            val templateRow = ws.getOrCreateRow(totalRow - 1)
            for (i in 0 until delta) {
                val newRow = ws.createRow(totalRow + i)
                copyRowStyle(templateRow, newRow)
            }
        } else {
            val templateIndex = (startRow + existingCapacity - 1).coerceAtLeast(startRow)
            val templateRow = ws.getOrCreateRow(templateIndex)
            for (i in 0 until delta) {
                val newRow = ws.createRow(startRow + existingCapacity + i)
                copyRowStyle(templateRow, newRow)
            }
        }
    }

    if (species.size < existingCapacity) {
        if (totalRow != null) {
            shrinkSpeciesCapacity(ws, startRow, totalRow, required = species.size)
        } else {
            shrinkSpeciesCapacityNoTotal(ws, startRow, required = species.size, existingCapacity = existingCapacity)
        }
    }

    val totalRow2 = totalRowText?.let { findRowByCellText(ws, 0, it) }

    // Remove merges in data region to avoid overlapping merges after shrink.
    val dataCapacity = if (totalRow2 != null) totalRow2 - startRow else maxOf(species.size, 1)
    removeMergedRegionsIntersecting(ws, startRow, ws.lastRowNum, 0, 1)

    for (i in species.indices) {
        val rowIndex = startRow + i
        val row = ws.getOrCreateRow(rowIndex)
        val sp = species[i]
        row.getOrCreateCell(0).setCellValue(formatLvl1(sp.taxonomy.lvl1, latinOptions.lvl1, latinNameMap))
        row.getOrCreateCell(1).setCellValue(formatSpeciesName(sp, latinOptions.species))
        for (pi in points.indices) {
            val col = firstPointCol + pi
            val cell = row.getOrCreateCell(col)
            val v = valueFor(sp, points[pi])
            when (v) {
                null -> cell.setBlank()
                is Double -> cell.setNullableNumber(v)
                is Int -> cell.setCellValue(v.toDouble())
                is String -> cell.setCellValue(v)
                else -> cell.setCellValue(v.toString())
            }
        }

        clearExtraPointColumns(
            row,
            fromCol = firstPointCol + points.size,
            toCol = row.lastCellNum.toInt().coerceAtLeast(firstPointCol + points.size),
        )
    }

    // Clear remaining template rows (keep styles).
    for (i in species.size until dataCapacity) {
        val rowIndex = startRow + i
        val row = ws.getRow(rowIndex) ?: continue
        clearRowValues(row, upToCol = row.lastCellNum.toInt().coerceAtLeast(0))
    }

    // Merge column A for contiguous groups (lvl1)
    if (species.isNotEmpty()) {
        mergeSingleColumn(ws, startRow, startRow + species.size - 1, 0)
    }

    // Total row
    if (totalRow2 != null) {
        val row = ws.getOrCreateRow(totalRow2)
        row.getOrCreateCell(0).setCellValue(totalRowText)
        row.getOrCreateCell(1).setBlank()
        for (pi in points.indices) {
            val col = firstPointCol + pi
            val cell = row.getOrCreateCell(col)
            val v = totalForPoint(points[pi])
            when (v) {
                null -> cell.setBlank()
                is Double -> cell.setNullableNumber(v)
                is Int -> cell.setCellValue(v.toDouble())
                is String -> cell.setCellValue(v)
                else -> cell.setCellValue(v.toString())
            }
        }
        clearExtraPointColumns(
            row,
            fromCol = firstPointCol + points.size,
            toCol = row.lastCellNum.toInt().coerceAtLeast(firstPointCol + points.size),
        )
    }
}

private fun Cell.setNullableNumber(v: Double?) {
    if (v == null || !v.isFinite()) {
        setBlank()
        return
    }
    setCellValue(v)
}

private fun Cell.setBiomassCell(v: BiomassCell?) {
    when (v) {
        null -> setBlank()
        is BiomassCell.Value -> setCellValue(v.mgPerL)
        BiomassCell.MissingWetWeight -> setCellValue("未查到湿重")
    }
}

private fun Any?.toBiomassExcelValue(): Any? {
    return when (this) {
        null -> null
        is BiomassCell.Value -> this.mgPerL
        BiomassCell.MissingWetWeight -> "未查到湿重"
        else -> this
    }
}

private fun biomassTotal(cells: List<BiomassCell?>): Any? {
    if (cells.any { it == BiomassCell.MissingWetWeight }) return "未查到湿重"
    val nums = cells.mapNotNull { (it as? BiomassCell.Value)?.mgPerL }.filter { it.isFinite() }
    if (nums.isEmpty()) return null
    return nums.sum()
}

private fun Cell.setBiomassTotal(cells: List<BiomassCell?>) {
    val v = biomassTotal(cells)
    when (v) {
        null -> setBlank()
        is Double -> setCellValue(v)
        is String -> setCellValue(v)
        else -> setCellValue(v.toString())
    }
}

private val CN_LATIN_RE = Regex("^([^（(]+)[（(]([^）)]+)[）)]$")

private val LVL1_LATIN: Map<String, String> = mapOf(
    "原生动物" to "Protozoa",
    "轮虫类" to "Rotifera",
    "枝角类" to "Cladocera",
    "桡足类" to "Copepoda",
)

private fun splitCnLatin(raw: String): Pair<String, String?> {
    val v = raw.trim()
    if (v.isBlank()) return "" to null
    val m = CN_LATIN_RE.matchEntire(v) ?: return v to null
    val cn = m.groupValues[1].trim()
    val latin = m.groupValues[2].trim().ifBlank { null }
    return cn to latin
}

private fun lookupLatin(raw: String, latinMap: Map<String, String?>): String? {
    val key = raw.trim()
    if (key.isBlank()) return null
    return latinMap[key]
}

private fun formatTaxValue(raw: String, includeLatin: Boolean, fallbackLatin: String? = null): String {
    val v = raw.trim()
    if (v.isBlank()) return ""
    val (cn, latin) = splitCnLatin(v)
    if (!includeLatin) return cn
    val useLatin = latin ?: fallbackLatin
    return if (!useLatin.isNullOrBlank()) "$cn（$useLatin）" else cn
}

private fun formatLvl1(raw: String, includeLatin: Boolean, latinMap: Map<String, String?>): String {
    val v = normalizeLvl1Name(raw).trim()
    val fallback = latinMap[v] ?: LVL1_LATIN[normalizeLvl1Name(v)]
    return formatTaxValue(v, includeLatin, fallbackLatin = fallback)
}

private fun formatSpeciesName(species: Species, includeLatin: Boolean): String {
    val cn = species.nameCn.trim()
    if (cn.isBlank()) return ""
    if (!includeLatin) return cn
    val latin = species.nameLatin.trim().ifBlank { null }
    return if (latin.isNullOrBlank()) cn else "$cn（$latin）"
}

private fun writeTaxonomyRow(
    row: Row,
    species: Species,
    latinOptions: ExportLatinOptions,
    latinNameMap: Map<String, String?>,
) {
    val lvl2Latin = lookupLatin(species.taxonomy.lvl2, latinNameMap)
    val lvl3Latin = lookupLatin(species.taxonomy.lvl3, latinNameMap)
    val lvl4Latin = lookupLatin(species.taxonomy.lvl4, latinNameMap)
    val lvl5Latin = lookupLatin(species.taxonomy.lvl5, latinNameMap)
    val cols = listOf(
        formatLvl1(species.taxonomy.lvl1, latinOptions.lvl1, latinNameMap),
        formatTaxValue(species.taxonomy.lvl2, latinOptions.lvl2, fallbackLatin = lvl2Latin),
        formatTaxValue(species.taxonomy.lvl3, latinOptions.lvl3, fallbackLatin = lvl3Latin),
        formatTaxValue(species.taxonomy.lvl4, latinOptions.lvl4, fallbackLatin = lvl4Latin),
        formatTaxValue(species.taxonomy.lvl5, latinOptions.lvl5, fallbackLatin = lvl5Latin),
        formatSpeciesName(species, latinOptions.species),
    )
    for (i in cols.indices) {
        row.getOrCreateCell(i).setCellValue(cols[i].ifBlank { "" })
    }
}

private fun mergeTaxonomyColumns(
    ws: org.apache.poi.ss.usermodel.Sheet,
    startRow: Int,
    species: List<Species>,
    latinOptions: ExportLatinOptions,
    latinNameMap: Map<String, String?>,
) {
    val rows = species.map {
        val lvl2Latin = lookupLatin(it.taxonomy.lvl2, latinNameMap)
        val lvl3Latin = lookupLatin(it.taxonomy.lvl3, latinNameMap)
        val lvl4Latin = lookupLatin(it.taxonomy.lvl4, latinNameMap)
        val lvl5Latin = lookupLatin(it.taxonomy.lvl5, latinNameMap)
        listOf(
            formatLvl1(it.taxonomy.lvl1, latinOptions.lvl1, latinNameMap),
            formatTaxValue(it.taxonomy.lvl2, latinOptions.lvl2, fallbackLatin = lvl2Latin),
            formatTaxValue(it.taxonomy.lvl3, latinOptions.lvl3, fallbackLatin = lvl3Latin),
            formatTaxValue(it.taxonomy.lvl4, latinOptions.lvl4, fallbackLatin = lvl4Latin),
            formatTaxValue(it.taxonomy.lvl5, latinOptions.lvl5, fallbackLatin = lvl5Latin),
        )
    }
    mergeHierarchicalColumns(ws, startRow, rows, colOffset = 0)
}

private fun mergeHierarchicalColumns(ws: org.apache.poi.ss.usermodel.Sheet, startRow: Int, rows: List<List<String>>, colOffset: Int) {
    if (rows.isEmpty()) return
    val colCount = rows[0].size
    for (col in 0 until colCount) {
        var start = 0
        while (start < rows.size) {
            val value = rows[start][col]
            if (value.isBlank()) {
                start += 1
                continue
            }
            var end = start
            while (end + 1 < rows.size) {
                val next = rows[end + 1]
                if (next[col] != value) break
                var samePrev = true
                for (prev in 0 until col) {
                    if (next[prev] != rows[start][prev]) {
                        samePrev = false
                        break
                    }
                }
                if (!samePrev) break
                end += 1
            }
            setMergedRegion(
                ws,
                firstRow = startRow + start,
                lastRow = startRow + end,
                firstCol = colOffset + col,
                lastCol = colOffset + col,
            )
            start = end + 1
        }
    }
}

private fun mergeSingleColumn(ws: org.apache.poi.ss.usermodel.Sheet, startRow: Int, endRow: Int, col: Int) {
    var r = startRow
    while (r <= endRow) {
        val v = ws.getRow(r)?.getCell(col)?.stringValueOrBlank() ?: ""
        if (v.isBlank()) {
            r += 1
            continue
        }
        var end = r
        while (end + 1 <= endRow) {
            val next = ws.getRow(end + 1)?.getCell(col)?.stringValueOrBlank() ?: ""
            if (next != v) break
            end += 1
        }
        setMergedRegion(ws, firstRow = r, lastRow = end, firstCol = col, lastCol = col)
        r = end + 1
    }
}

private fun ensureSpeciesCapacity(ws: org.apache.poi.ss.usermodel.Sheet, startRow: Int, totalRow: Int, required: Int) {
    val capacity = totalRow - startRow
    if (required <= capacity) return
    val delta = required - capacity

    // Shift total row and below down.
    ws.shiftRows(totalRow, ws.lastRowNum, delta, true, false)

    // Copy style from the last species row (just above total).
    val templateRow = ws.getOrCreateRow(totalRow - 1)
    for (i in 0 until delta) {
        val newRow = ws.createRow(totalRow + i)
        copyRowStyle(templateRow, newRow)
    }
}

private fun shrinkSpeciesCapacity(ws: org.apache.poi.ss.usermodel.Sheet, startRow: Int, totalRow: Int, required: Int): Int {
    val capacity = totalRow - startRow
    if (required >= capacity) return totalRow
    val delta = capacity - required
    // Remove merges in data region before shifting rows to avoid overlap conflicts.
    removeMergedRegionsIntersecting(ws, startRow, ws.lastRowNum, 0, 5)
    ws.shiftRows(totalRow, ws.lastRowNum, -delta, true, false)
    return totalRow - delta
}

private fun shrinkSpeciesCapacityNoTotal(ws: org.apache.poi.ss.usermodel.Sheet, startRow: Int, required: Int, existingCapacity: Int) {
    val keep = if (required <= 0) 1 else required
    if (keep >= existingCapacity) return
    val fromRow = startRow + keep
    removeRowsFrom(ws, fromRow)
}

private fun copyRowStyle(from: Row, to: Row) {
    to.height = from.height
    val last = from.lastCellNum.toInt().coerceAtLeast(0)
    for (c in 0 until last) {
        val src = from.getCell(c) ?: continue
        val dst = to.getOrCreateCell(c)
        dst.cellStyle = src.cellStyle
        dst.setBlank()
    }
}

private fun removeRowsAfter(ws: org.apache.poi.ss.usermodel.Sheet, rowIndex: Int) {
    val fromRow = rowIndex + 1
    removeRowsFrom(ws, fromRow)
}

private fun removeRowsFrom(ws: org.apache.poi.ss.usermodel.Sheet, fromRow: Int) {
    if (fromRow > ws.lastRowNum) return
    val maxCol = (0..ws.lastRowNum).maxOfOrNull { ws.getRow(it)?.lastCellNum?.toInt() ?: 0 } ?: 0
    if (maxCol > 0) {
        removeMergedRegionsIntersecting(ws, fromRow, ws.lastRowNum, 0, maxCol)
    }
    for (r in ws.lastRowNum downTo fromRow) {
        val row = ws.getRow(r) ?: continue
        ws.removeRow(row)
    }
}

private fun writePointLabels(ws: org.apache.poi.ss.usermodel.Sheet, rowIndex: Int, firstPointCol: Int, points: List<Point>) {
    val row = ws.getOrCreateRow(rowIndex)
    for (i in points.indices) {
        row.getOrCreateCell(firstPointCol + i).setCellValue(points[i].label)
        ws.setColumnHidden(firstPointCol + i, false)
    }
}

private fun writeConcRow(ws: org.apache.poi.ss.usermodel.Sheet, rowIndex: Int, firstPointCol: Int, points: List<Point>) {
    val row = ws.getOrCreateRow(rowIndex)
    for (i in points.indices) {
        val cell = row.getOrCreateCell(firstPointCol + i)
        cell.setNullableNumber(points[i].vConcMl)
        ws.setColumnHidden(firstPointCol + i, false)
    }
}

private fun clearExtraPointColumns(row: Row, fromCol: Int, toCol: Int) {
    for (c in fromCol until toCol) {
        val cell = row.getCell(c) ?: continue
        // Remove cell entirely to clear borders/styles in unused columns.
        row.removeCell(cell)
    }
}

private fun clearRowValues(row: Row, upToCol: Int) {
    for (c in 0 until upToCol) {
        row.getCell(c)?.setBlank()
    }
}

private fun clearExtraPointColumnsInSheet(ws: org.apache.poi.ss.usermodel.Sheet, fromCol: Int, toCol: Int) {
    if (toCol <= fromCol) return
    for (r in 0..ws.lastRowNum) {
        val row = ws.getRow(r) ?: continue
        clearExtraPointColumns(row, fromCol, toCol)
    }
}

private fun hideExtraPointColumns(ws: org.apache.poi.ss.usermodel.Sheet, firstPointCol: Int, pointCount: Int, keepFromTemplate: Int) {
    val startHide = firstPointCol + pointCount
    val maxCol = (0..ws.lastRowNum).maxOfOrNull { ws.getRow(it)?.lastCellNum?.toInt() ?: 0 } ?: startHide
    val max = maxOf(startHide, firstPointCol + keepFromTemplate, maxCol)
    if (max <= startHide) return
    clearExtraPointColumnsInSheet(ws, startHide, max)
    for (c in startHide until max) {
        ws.setColumnHidden(c, true)
    }
}

private fun ensurePointColumns(ws: org.apache.poi.ss.usermodel.Sheet, headerRow: Int, firstPointCol: Int, requiredPoints: Int) {
    if (requiredPoints <= 0) return

    val lastNeededCol = firstPointCol + requiredPoints - 1
    val existing = ws.getRow(headerRow)?.lastCellNum?.toInt()?.minus(1) ?: -1
    if (existing >= lastNeededCol) return

    val templateCol = existing.coerceAtLeast(firstPointCol)
    val rowsToCopy = ws.lastRowNum.coerceAtLeast(headerRow)
    for (c in (existing + 1)..lastNeededCol) {
        ws.setColumnWidth(c, ws.getColumnWidth(templateCol))
        for (r in 0..rowsToCopy) {
            val row = ws.getRow(r) ?: continue
            val src = row.getCell(templateCol) ?: continue
            val dst = row.getOrCreateCell(c)
            dst.cellStyle = src.cellStyle
            dst.setBlank()
        }
    }
}

private fun setMergedRegion(ws: org.apache.poi.ss.usermodel.Sheet, firstRow: Int, lastRow: Int, firstCol: Int, lastCol: Int) {
    if (firstRow < 0 || lastRow < 0 || firstCol < 0 || lastCol < 0) return
    val normalizedFirstRow = minOf(firstRow, lastRow)
    val normalizedLastRow = maxOf(firstRow, lastRow)
    val normalizedFirstCol = minOf(firstCol, lastCol)
    val normalizedLastCol = maxOf(firstCol, lastCol)
    if (normalizedFirstRow == normalizedLastRow && normalizedFirstCol == normalizedLastCol) return
    // Remove any merge that intersects the target rectangle to avoid overlap errors.
    removeMergedRegionsIntersecting(
        ws,
        normalizedFirstRow,
        normalizedLastRow,
        normalizedFirstCol,
        normalizedLastCol,
    )
    ws.addMergedRegion(CellRangeAddress(normalizedFirstRow, normalizedLastRow, normalizedFirstCol, normalizedLastCol))
}

private fun removeMergedRegionsIntersecting(
    ws: org.apache.poi.ss.usermodel.Sheet,
    firstRow: Int,
    lastRow: Int,
    firstCol: Int,
    lastCol: Int,
) {
    for (i in ws.numMergedRegions - 1 downTo 0) {
        val r = ws.getMergedRegion(i)
        if (r.firstRow > lastRow || r.lastRow < firstRow) continue
        if (r.firstColumn > lastCol || r.lastColumn < firstCol) continue
        // Keep header merges (often span row 0-1) by only removing inside the data region.
        ws.removeMergedRegion(i)
    }
}

private fun findRowByCellText(ws: org.apache.poi.ss.usermodel.Sheet, colIndex: Int, text: String): Int? {
    for (r in 0..ws.lastRowNum) {
        val cell = ws.getRow(r)?.getCell(colIndex) ?: continue
        if (cell.cellType == CellType.STRING && cell.stringCellValue.trim() == text) return r
        if (cell.cellType == CellType.FORMULA && cell.stringCellValue.trim() == text) return r
    }
    return null
}

private fun org.apache.poi.ss.usermodel.Sheet.getOrCreateRow(rowIndex: Int): Row {
    return getRow(rowIndex) ?: createRow(rowIndex)
}

private fun org.apache.poi.ss.usermodel.Sheet.getOrCreateCell(rowIndex: Int, colIndex: Int): Cell {
    return getOrCreateRow(rowIndex).getOrCreateCell(colIndex)
}

private fun Row.getOrCreateCell(colIndex: Int): Cell {
    return getCell(colIndex) ?: createCell(colIndex)
}

private fun Cell.stringValueOrBlank(): String {
    return when (cellType) {
        CellType.STRING -> stringCellValue ?: ""
        CellType.NUMERIC -> numericCellValue.toString()
        CellType.BOOLEAN -> booleanCellValue.toString()
        CellType.BLANK -> ""
        CellType.FORMULA -> try {
            stringCellValue ?: ""
        } catch (_: Exception) {
            ""
        }

        else -> ""
    }
}

private fun Iterable<Double?>.sumNullable(): Double? {
    var sum = 0.0
    var has = false
    for (v in this) {
        if (v != null && v.isFinite()) {
            sum += v
            has = true
        }
    }
    return if (has) sum else null
}
