package com.plankton.one102.data.api

import com.plankton.one102.data.AppJson
import com.plankton.one102.domain.BiomassCell
import com.plankton.one102.domain.Dataset
import com.plankton.one102.domain.DatasetCalc
import com.plankton.one102.domain.PerPointSpeciesCalc
import com.plankton.one102.domain.PointIndex
import com.plankton.one102.domain.calcFiBySpeciesId
import com.plankton.one102.domain.calcPointTotals
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.max

@Serializable
data class CalcInputPoint(
    val idx: Int,
    val label: String,
    val vConcMl: Double? = null,
    val vOrigL: Double,
)

@Serializable
data class CalcInputSpecies(
    val idx: Int,
    val nameCn: String,
    val wetWeightMg: Double? = null,
)

@Serializable
data class CalcInputCount(
    val speciesIdx: Int,
    val pointIdx: Int,
    val count: Int,
)

@Serializable
data class CalcInput(
    val points: List<CalcInputPoint>,
    val species: List<CalcInputSpecies>,
    val counts: List<CalcInputCount>,
    val notes: List<String> = emptyList(),
)

@Serializable
data class CalcPointInputSpecies(
    val idx: Int,
    val nameCn: String,
    val count: Int,
    val wetWeightMg: Double? = null,
    val fi: Double,
)

@Serializable
data class CalcPointTotals(
    val totalCount: Int,
    val speciesCountS: Int,
)

@Serializable
data class CalcPointInput(
    val point: CalcInputPoint,
    val totals: CalcPointTotals,
    val species: List<CalcPointInputSpecies>,
    val notes: List<String> = emptyList(),
)

@Serializable
data class CalcAuditCellInput(
    val speciesIdx: Int,
    val nameCn: String,
    val count: Int,
    val wetWeightMg: Double? = null,
    val fi: Double,
    val internalDensity: Double? = null,
    val internalBiomassMgL: Double? = null,
    val internalBiomassMissingWetWeight: Boolean = false,
    val internalPLnP: Double? = null,
    val internalY: Double? = null,
)

@Serializable
data class CalcAuditPointInput(
    val point: CalcInputPoint,
    val totals: CalcPointTotals,
    val internalH: Double? = null,
    val internalJ: Double? = null,
    val internalD: Double? = null,
    val cells: List<CalcAuditCellInput>,
    val notes: List<String> = emptyList(),
)

@Serializable
data class CalcAuditBatchInput(
    val points: List<CalcAuditPointInput>,
    val notes: List<String> = emptyList(),
)

@Serializable
data class CalcAuditMismatch(
    val kind: String,
    val speciesIdx: Int? = null,
    val apiValue: Double? = null,
    val apiMissingWetWeight: Boolean? = null,
)

@Serializable
data class CalcAuditOutput(
    val pointIdx: Int,
    val mismatches: List<CalcAuditMismatch> = emptyList(),
    val notes: List<String> = emptyList(),
)

@Serializable
data class CalcAuditBatchOutput(
    val items: List<CalcAuditOutput> = emptyList(),
    val notes: List<String> = emptyList(),
)

@Serializable
data class CalcOutputPointIndex(
    val pointIdx: Int,
    val H: Double? = null,
    val J: Double? = null,
    val D: Double? = null,
)

@Serializable
data class CalcOutputCell(
    val speciesIdx: Int,
    val pointIdx: Int,
    val density: Double? = null,
    val biomassMgL: Double? = null,
    val biomassMissingWetWeight: Boolean = false,
    val pLnP: Double? = null,
    val Y: Double? = null,
)

@Serializable
data class CalcOutput(
    val points: List<CalcOutputPointIndex> = emptyList(),
    val cells: List<CalcOutputCell> = emptyList(),
    val notes: List<String> = emptyList(),
)

data class CalcBuildResult(
    val calc: DatasetCalc,
    val warnings: List<String> = emptyList(),
)

fun buildCalcInput(dataset: Dataset): CalcInput {
    val points = dataset.points.mapIndexed { idx, p ->
        CalcInputPoint(
            idx = idx,
            label = p.label.ifBlank { "未命名" },
            vConcMl = p.vConcMl,
            vOrigL = p.vOrigL,
        )
    }
    val species = dataset.species.mapIndexed { idx, sp ->
        CalcInputSpecies(
            idx = idx,
            nameCn = sp.nameCn.ifBlank { "未命名物种" },
            wetWeightMg = sp.avgWetWeightMg,
        )
    }
    val counts = buildList {
        for ((pi, p) in dataset.points.withIndex()) {
            for ((si, sp) in dataset.species.withIndex()) {
                val n = sp.countsByPointId[p.id] ?: 0
                if (n != 0) add(CalcInputCount(speciesIdx = si, pointIdx = pi, count = n))
            }
        }
    }
    val notes = listOf("未列出的 counts 视为 0。", "请严格遵守输出 JSON 结构。")
    return CalcInput(points = points, species = species, counts = counts, notes = notes)
}

fun buildCalcPointInput(dataset: Dataset, pointIdx: Int): CalcPointInput {
    val point = dataset.points.getOrNull(pointIdx) ?: error("点位索引越界：$pointIdx")
    val fiBySpeciesId = calcFiBySpeciesId(dataset)
    val totals = calcPointTotals(dataset, point)
    val pointInfo = CalcInputPoint(
        idx = pointIdx,
        label = point.label.ifBlank { "未命名" },
        vConcMl = point.vConcMl,
        vOrigL = point.vOrigL,
    )
    val species = dataset.species.mapIndexed { idx, sp ->
        CalcPointInputSpecies(
            idx = idx,
            nameCn = sp.nameCn.ifBlank { "未命名物种" },
            count = sp.countsByPointId[point.id] ?: 0,
            wetWeightMg = sp.avgWetWeightMg,
            fi = fiBySpeciesId[sp.id] ?: 0.0,
        )
    }
    return CalcPointInput(
        point = pointInfo,
        totals = CalcPointTotals(totalCount = totals.first, speciesCountS = totals.second),
        species = species,
        notes = listOf("只输出该点位的计算结果。", "未列出的 species 视为 0。"),
    )
}

fun buildCalcAuditPointInput(dataset: Dataset, internal: DatasetCalc, pointIdx: Int): CalcAuditPointInput {
    val point = dataset.points.getOrNull(pointIdx) ?: error("点位索引越界：$pointIdx")
    val totals = calcPointTotals(dataset, point)
    val fiBySpeciesId = calcFiBySpeciesId(dataset)
    val pointInfo = CalcInputPoint(
        idx = pointIdx,
        label = point.label.ifBlank { "未命名" },
        vConcMl = point.vConcMl,
        vOrigL = point.vOrigL,
    )
    val pointIndex = internal.pointIndexById[point.id]

    val cells = dataset.species.mapIndexed { idx, sp ->
        val count = sp.countsByPointId[point.id] ?: 0
        val per = internal.perSpeciesByPoint[sp.id]?.get(point.id)
        val biomass = per?.biomass
        val (biomassMgL, biomassMissing) = when (biomass) {
            is BiomassCell.Value -> biomass.mgPerL to false
            BiomassCell.MissingWetWeight -> null to true
            else -> null to false
        }
        CalcAuditCellInput(
            speciesIdx = idx,
            nameCn = sp.nameCn.ifBlank { "未命名物种" },
            count = count,
            wetWeightMg = sp.avgWetWeightMg,
            fi = fiBySpeciesId[sp.id] ?: 0.0,
            internalDensity = per?.density,
            internalBiomassMgL = biomassMgL,
            internalBiomassMissingWetWeight = biomassMissing,
            internalPLnP = per?.pLnP,
            internalY = per?.Y,
        )
    }

    return CalcAuditPointInput(
        point = pointInfo,
        totals = CalcPointTotals(totalCount = totals.first, speciesCountS = totals.second),
        internalH = pointIndex?.H,
        internalJ = pointIndex?.J,
        internalD = pointIndex?.D,
        cells = cells,
        notes = listOf("只输出不一致项；若一致则 mismatches 为空。"),
    )
}

fun buildCalcVerificationPrompt(input: CalcInput, decimals: Int = 8, strictJsonOnly: Boolean = false): String {
    val json = AppJson.encodeToString(CalcInput.serializer(), input)
    return """
        你是“浮游动物一体化”应用内的计算核对助手。请严格按以下口径计算，不要编造数据。

        工作流程与物理意义（简要）：
        1) 采样点输入：每个点位有浓缩体积 Vc(mL) 与原水体积 Vo(L)。
        2) 密度（ind/L）：ρ = (C / 1.3) * (Vc / Vo)。
        3) 生物量（mg/L）：B = ρ * 平均湿重(mg/个)。
        4) Shannon-Wiener H'：H' = -Σ(p_i * ln p_i)，p_i = n_i / N。
        5) Pielou J：J = H' / ln(S)。
        6) Margalef D：D = (S - 1) / ln(N)。
        7) 优势度 Y：Y = (n_i / N) * f_i；f_i = 出现点数 / 总点数；Y > 0.02 视为优势种。

        关键边界/约束（必须遵守）：
        - 计数 count <= 0 时：密度=0；生物量=0。
        - 若 Vc 为空或 Vo<=0：密度为 null；生物量为 null（除非 count<=0 时仍为 0）。
        - 湿重缺失且 count>0：生物量标记为“未查到湿重”。
        - H'：总计数 N<=0 则为 null；可为 0。
        - J：S<=1 时为 null。
        - D：S<=1 或 N<=1 时为 null。
        - 未列出的 counts 视为 0。
        - 内置算法不强制四舍五入；请按要求保留小数位输出。
        - 输出数字请保留 ${decimals} 位小数（不要强行四舍五入成整数）。
        - 不要回显输入 JSON。
        - cells 必须覆盖全部点位×物种组合（即使 count=0 也要输出）。

        请输出以下 JSON（只输出最后一行，不要代码块）：
        FINAL_CALC_JSON: {
          "points": [{"pointIdx":0,"H":0.12345678,"J":0.12345678,"D":0.12345678}],
          "cells": [{"speciesIdx":0,"pointIdx":0,"density":0.12345678,"biomassMgL":0.12345678,"biomassMissingWetWeight":false,"pLnP":-0.12345678,"Y":0.12345678}],
          "notes": ["可选备注"]
        }

        计算输入（JSON）：
        $json

        ${if (strictJsonOnly) "只输出 JSON，不要输出其它文字。" else ""}
    """.trimIndent()
}

fun buildCalcAuditPointPrompt(input: CalcAuditPointInput, decimals: Int = 8, strictJsonOnly: Boolean = false): String {
    val json = AppJson.encodeToString(CalcAuditPointInput.serializer(), input)
    return """
        你是“浮游动物一体化”应用内的计算核对助手。请严格按公式重新计算，并与 internal 值对比。

        要求：
        1) 只输出“不一致”的条目；一致则不输出该条目。
        2) 匹配规则：差异 >= 1e-6 或 internal=null 且你算出有效值。
        3) 输出数字请保留 ${decimals} 位小数。
        4) 只输出 JSON，且仅输出一行。
        5) 不要回显输入 JSON。
        6) pointIdx 必须等于 input.point.idx。

        公式口径：
        - 密度：ρ = (C / 1.3) * (Vc / Vo)
        - 生物量：B = ρ * 平均湿重(mg/个)
        - H' = -Σ(p_i * ln p_i), p_i = n_i / N
        - J = H' / ln(S)
        - D = (S - 1) / ln(N)
        - Y = (n_i / N) * f_i

        边界：
        - count<=0：密度=0；生物量=0；p*ln(p)=0
        - Vc 为空或 Vo<=0：密度=null；生物量=null（除非 count<=0）
        - 湿重缺失且 count>0：biomassMissingWetWeight=true
        - H'：N<=0 为 null；可为 0
        - J：S<=1 为 null
        - D：S<=1 或 N<=1 为 null

        输出格式：
        FINAL_DIFF_JSON: {
          "pointIdx":0,
          "mismatches":[
            {"kind":"H","apiValue":0.12345678},
            {"kind":"density","speciesIdx":0,"apiValue":0.12345678},
            {"kind":"biomassMgL","speciesIdx":0,"apiValue":0.12345678},
            {"kind":"biomassMissingWetWeight","speciesIdx":0,"apiMissingWetWeight":true},
            {"kind":"pLnP","speciesIdx":0,"apiValue":-0.12345678},
            {"kind":"Y","speciesIdx":0,"apiValue":0.12345678}
          ],
          "notes":[]
        }

        计算输入（JSON）：
        $json

        ${if (strictJsonOnly) "只输出 JSON，不要输出其它文字。" else ""}
    """.trimIndent()
}

fun buildCalcAuditBatchPrompt(inputs: List<CalcAuditPointInput>, decimals: Int = 8, strictJsonOnly: Boolean = false): String {
    val payload = CalcAuditBatchInput(points = inputs, notes = listOf("只输出不一致项；一致则该点位 mismatches 为空。"))
    val json = AppJson.encodeToString(CalcAuditBatchInput.serializer(), payload)
    return """
        你是“浮游动物一体化”应用内的计算核对助手。请严格按公式重新计算，并与 internal 值对比。

        要求：
        1) 只输出“不一致”的条目；一致则不输出该条目。
        2) 匹配规则：差异 >= 1e-6 或 internal=null 且你算出有效值。
        3) 输出数字请保留 ${decimals} 位小数。
        4) 只输出 JSON，且仅输出一行。
        5) 不要回显输入 JSON。

        公式口径：
        - 密度：ρ = (C / 1.3) * (Vc / Vo)
        - 生物量：B = ρ * 平均湿重(mg/个)
        - H' = -Σ(p_i * ln p_i), p_i = n_i / N
        - J = H' / ln(S)
        - D = (S - 1) / ln(N)
        - Y = (n_i / N) * f_i

        边界：
        - count<=0：密度=0；生物量=0；p*ln(p)=0
        - Vc 为空或 Vo<=0：密度=null；生物量=null（除非 count<=0）
        - 湿重缺失且 count>0：biomassMissingWetWeight=true
        - H'：N<=0 为 null；可为 0
        - J：S<=1 为 null
        - D：S<=1 或 N<=1 为 null

        输出格式：
        FINAL_DIFF_JSON: {
          "items":[
            {
              "pointIdx":0,
              "mismatches":[
                {"kind":"H","apiValue":0.12345678},
                {"kind":"density","speciesIdx":0,"apiValue":0.12345678},
                {"kind":"biomassMgL","speciesIdx":0,"apiValue":0.12345678},
                {"kind":"biomassMissingWetWeight","speciesIdx":0,"apiMissingWetWeight":true},
                {"kind":"pLnP","speciesIdx":0,"apiValue":-0.12345678},
                {"kind":"Y","speciesIdx":0,"apiValue":0.12345678}
              ],
              "notes":[]
            }
          ],
          "notes":[]
        }

        计算输入（JSON）：
        $json

        ${if (strictJsonOnly) "只输出 JSON，不要输出其它文字。" else ""}
    """.trimIndent()
}

fun buildCalcAuditRepairPrompt(raw: String): String {
    return """
        你是 JSON 修复助手。请把下方文本整理成严格 JSON，仅输出 JSON 本身。
        规则：
        - 目标是提取/整理为 FINAL_DIFF_JSON 结构中的 JSON 对象
        - 不要输出解释或多余文字

        待整理文本：
        $raw
    """.trimIndent()
}

fun buildCalcPointVerificationPrompt(input: CalcPointInput, decimals: Int = 8, strictJsonOnly: Boolean = false): String {
    val json = AppJson.encodeToString(CalcPointInput.serializer(), input)
    return """
        你是“浮游动物一体化”应用内的计算核对助手。请严格按以下口径计算，不要编造数据。

        任务：只计算【单个采样点】的结果，不要输出其它点位。
        pointIdx 必须等于输入 point.idx；cells 内的 pointIdx 也必须一致。

        工作流程与物理意义（简要）：
        1) 密度（ind/L）：ρ = (C / 1.3) * (Vc / Vo)。
        2) 生物量（mg/L）：B = ρ * 平均湿重(mg/个)。
        3) Shannon-Wiener H'：H' = -Σ(p_i * ln p_i)，p_i = n_i / N。
        4) Pielou J：J = H' / ln(S)。
        5) Margalef D：D = (S - 1) / ln(N)。
        6) 优势度 Y：Y = (n_i / N) * f_i。

        关键边界/约束（必须遵守）：
        - 计数 count <= 0 时：密度=0；生物量=0；p_i*ln(p_i)=0。
        - 若 Vc 为空或 Vo<=0：密度为 null；生物量为 null（除非 count<=0 时仍为 0）。
        - 湿重缺失且 count>0：生物量标记为“未查到湿重”。
        - H'：总计数 N<=0 则为 null；可为 0。
        - J：S<=1 时为 null。
        - D：S<=1 或 N<=1 时为 null。
        - 不要回显输入 JSON。
        - 输出数字请保留 ${decimals} 位小数。
        - cells 必须覆盖该点位全部 speciesIdx（即使 count=0 也要输出）。

        请输出以下 JSON（只输出最后一行，不要代码块）：
        FINAL_CALC_JSON: {
          "points": [{"pointIdx":0,"H":0.12345678,"J":0.12345678,"D":0.12345678}],
          "cells": [{"speciesIdx":0,"pointIdx":0,"density":0.12345678,"biomassMgL":0.12345678,"biomassMissingWetWeight":false,"pLnP":-0.12345678,"Y":0.12345678}],
          "notes": ["可选备注"]
        }

        计算输入（JSON）：
        $json

        ${if (strictJsonOnly) "只输出 JSON，不要输出其它文字。" else ""}
    """.trimIndent()
}

fun extractFinalCalcJson(text: String): String? {
    return extractBestCalcJson(text)
}

fun extractFinalCalcDiffJson(text: String): String? {
    val marked = extractJsonPayload(text, "FINAL_DIFF_JSON")
        ?: extractJsonPayload(text, "FINAL_CALC_DIFF")
        ?: extractJsonPayload(text, "FINAL_DIFF")
    if (marked != null) return marked
    val trimmed = text.trim()
    if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
        return trimmed
    }
    return null
}

fun parseCalcOutputFromText(text: String): CalcOutput? {
    val json = extractFinalCalcJson(text) ?: return null
    return parseCalcOutput(json)
}

fun parseCalcAuditOutputFromText(text: String): CalcAuditOutput? {
    val json = extractFinalCalcDiffJson(text) ?: return null
    return parseCalcAuditOutput(softFixJson(json))
}

fun parseCalcAuditOutputsFromText(text: String): List<CalcAuditOutput>? {
    val json = extractFinalCalcDiffJson(text)
    if (json != null) {
        val parsed = parseCalcAuditOutputs(softFixJson(json))
        if (parsed != null) return parsed
    }
    val candidates = extractAllJsonPayloads(text)
    for (candidate in candidates) {
        val parsed = parseCalcAuditOutputs(softFixJson(candidate))
        if (parsed != null) return parsed
    }
    return null
}

data class CalcOutputStats(
    val pointCount: Int,
    val cellCount: Int,
)

fun calcOutputStats(output: CalcOutput): CalcOutputStats {
    val pointCount = output.points.distinctBy { it.pointIdx }.size
    val cellCount = output.cells.distinctBy { it.speciesIdx to it.pointIdx }.size
    return CalcOutputStats(pointCount = pointCount, cellCount = cellCount)
}

fun parseCalcOutput(json: String): CalcOutput? {
    val strict = runCatching { AppJson.decodeFromString(CalcOutput.serializer(), json) }.getOrNull()
    if (strict != null) return strict
    return runCatching { parseCalcOutputLenient(json) }.getOrNull()
}

fun parseCalcAuditOutput(json: String): CalcAuditOutput? {
    val strict = runCatching { AppJson.decodeFromString(CalcAuditOutput.serializer(), json) }.getOrNull()
    if (strict != null) return strict
    return runCatching { parseCalcAuditOutputLenient(json) }.getOrNull()
}

fun parseCalcAuditOutputs(json: String): List<CalcAuditOutput>? {
    val root = runCatching { AppJson.parseToJsonElement(json) }.getOrNull() ?: return null
    return when (root) {
        is JsonArray -> root.mapNotNull { parseCalcAuditOutputFromElement(it) }
        is JsonObject -> {
            val listEl = root["items"] ?: root["outputs"] ?: root["list"]
            if (listEl is JsonArray) {
                listEl.mapNotNull { parseCalcAuditOutputFromElement(it) }
            } else {
                val single = parseCalcAuditOutputFromElement(root)
                if (single == null) null else listOf(single)
            }
        }
        else -> null
    }
}

fun applyCalcAuditOutputs(dataset: Dataset, internal: DatasetCalc, outputs: List<CalcAuditOutput>): DatasetCalc {
    val pointByIdx = dataset.points.mapIndexed { idx, p -> idx to p }.toMap()
    val speciesByIdx = dataset.species.mapIndexed { idx, sp -> idx to sp }.toMap()

    val pointIndexById = internal.pointIndexById.mapValues { it.value.copy() }.toMutableMap()
    val perSpeciesByPoint = internal.perSpeciesByPoint.mapValues { it.value.toMutableMap() }.toMutableMap()

    fun updatePoint(pointId: String, updater: (PointIndex) -> PointIndex) {
        val cur = pointIndexById[pointId] ?: return
        pointIndexById[pointId] = updater(cur)
    }

    fun updateCell(pointId: String, speciesId: String, updater: (PerPointSpeciesCalc) -> PerPointSpeciesCalc) {
        val byPoint = perSpeciesByPoint[speciesId] ?: return
        val cur = byPoint[pointId] ?: return
        byPoint[pointId] = updater(cur)
    }

    for (out in outputs) {
        val point = pointByIdx[out.pointIdx] ?: continue
        val pointId = point.id
        for (m in out.mismatches) {
            val kind = m.kind.trim().lowercase()
            when (kind) {
                "h" -> updatePoint(pointId) { it.copy(H = m.apiValue) }
                "j" -> updatePoint(pointId) { it.copy(J = m.apiValue) }
                "d" -> updatePoint(pointId) { it.copy(D = m.apiValue) }
                "density" -> {
                    val sp = m.speciesIdx?.let { speciesByIdx[it] } ?: continue
                    updateCell(pointId, sp.id) { it.copy(density = m.apiValue) }
                }
                "biomassmgl", "biomass" -> {
                    val sp = m.speciesIdx?.let { speciesByIdx[it] } ?: continue
                    val biomass = when {
                        m.apiMissingWetWeight == true -> BiomassCell.MissingWetWeight
                        m.apiValue != null -> BiomassCell.Value(m.apiValue)
                        else -> null
                    }
                    updateCell(pointId, sp.id) { it.copy(biomass = biomass) }
                }
                "biomassmissingwetweight" -> {
                    if (m.apiMissingWetWeight != true) continue
                    val sp = m.speciesIdx?.let { speciesByIdx[it] } ?: continue
                    updateCell(pointId, sp.id) { it.copy(biomass = BiomassCell.MissingWetWeight) }
                }
                "plnp", "p*ln(p)", "p_ln_p" -> {
                    val sp = m.speciesIdx?.let { speciesByIdx[it] } ?: continue
                    updateCell(pointId, sp.id) { it.copy(pLnP = m.apiValue) }
                }
                "y" -> {
                    val sp = m.speciesIdx?.let { speciesByIdx[it] } ?: continue
                    val y = m.apiValue
                    updateCell(pointId, sp.id) { it.copy(Y = y, isDominant = y?.let { v -> v > 0.02 }) }
                }
            }
        }
    }

    return DatasetCalc(
        pointIndexById = pointIndexById,
        fiBySpeciesId = internal.fiBySpeciesId,
        perSpeciesByPoint = perSpeciesByPoint,
    )
}

fun buildDatasetCalcFromOutput(dataset: Dataset, output: CalcOutput): CalcBuildResult {
    val warnings = mutableListOf<String>()
    val pointByIdx = dataset.points.mapIndexed { idx, p -> idx to p }.toMap()
    val speciesByIdx = dataset.species.mapIndexed { idx, sp -> idx to sp }.toMap()

    val outPointMap = output.points.associateBy { it.pointIdx }
    val outCellMap = output.cells.associateBy { it.speciesIdx to it.pointIdx }

    val fiBySpeciesId = calcFiBySpeciesId(dataset)

    val totalsByPointId = dataset.points.associate { p -> p.id to calcPointTotals(dataset, p) }
    val pointIndexById = mutableMapOf<String, PointIndex>()
    for ((idx, p) in pointByIdx) {
        val totals = totalsByPointId[p.id] ?: (0 to 0)
        val out = outPointMap[idx]
        if (out == null) warnings += "API 缺少点位指数：${p.label.ifBlank { p.id }}"
        pointIndexById[p.id] = PointIndex(
            pointId = p.id,
            label = p.label,
            vConcMl = p.vConcMl,
            vOrigL = p.vOrigL,
            totalCount = totals.first,
            speciesCountS = totals.second,
            H = out?.H,
            J = out?.J,
            D = out?.D,
        )
    }

    val perSpeciesByPoint = mutableMapOf<String, MutableMap<String, PerPointSpeciesCalc>>()
    for ((sIdx, sp) in speciesByIdx) {
        val byPoint = mutableMapOf<String, PerPointSpeciesCalc>()
        for ((pIdx, p) in pointByIdx) {
            val count = sp.countsByPointId[p.id] ?: 0
            val totalCount = totalsByPointId[p.id]?.first ?: 0
            val pVal = if (totalCount > 0) count.toDouble() / totalCount.toDouble() else null
            val out = outCellMap[sIdx to pIdx]
            if (out == null) warnings += "API 缺少单元格：点位 ${p.label.ifBlank { p.id }} / 物种 ${sp.nameCn.ifBlank { sp.id }}"

            val biomass = when {
                count <= 0 -> BiomassCell.Value(0.0)
                out?.biomassMissingWetWeight == true -> BiomassCell.MissingWetWeight
                out?.biomassMgL != null -> BiomassCell.Value(out.biomassMgL)
                else -> null
            }

            val y = out?.Y
            byPoint[p.id] = PerPointSpeciesCalc(
                count = count,
                density = out?.density,
                biomass = biomass,
                p = pVal,
                pLnP = out?.pLnP,
                Y = y,
                isDominant = y?.let { it > 0.02 },
            )
        }
        perSpeciesByPoint[sp.id] = byPoint
    }

    val calc = DatasetCalc(
        pointIndexById = pointIndexById,
        fiBySpeciesId = fiBySpeciesId,
        perSpeciesByPoint = perSpeciesByPoint,
    )

    if (warnings.isNotEmpty()) {
        val maxShow = 12
        val shown = warnings.take(maxShow)
        val more = max(0, warnings.size - maxShow)
        val summary = if (more > 0) shown + "… 另有 $more 处缺失" else shown
        return CalcBuildResult(calc = calc, warnings = summary)
    }
    return CalcBuildResult(calc = calc)
}

fun buildCalcOutputFromCalc(dataset: Dataset, calc: DatasetCalc): CalcOutput {
    val pointByIdx = dataset.points.mapIndexed { idx, p -> idx to p }.toMap()
    val speciesByIdx = dataset.species.mapIndexed { idx, sp -> idx to sp }.toMap()

    val points = pointByIdx.mapNotNull { (idx, p) ->
        val pi = calc.pointIndexById[p.id]
        CalcOutputPointIndex(
            pointIdx = idx,
            H = pi?.H,
            J = pi?.J,
            D = pi?.D,
        )
    }

    val cells = buildList {
        for ((sIdx, sp) in speciesByIdx) {
            val perMap = calc.perSpeciesByPoint[sp.id].orEmpty()
            for ((pIdx, p) in pointByIdx) {
                val per = perMap[p.id]
                val biomassMissing = per?.biomass == BiomassCell.MissingWetWeight
                val biomassValue = (per?.biomass as? BiomassCell.Value)?.mgPerL
                add(
                    CalcOutputCell(
                        speciesIdx = sIdx,
                        pointIdx = pIdx,
                        density = per?.density,
                        biomassMgL = biomassValue,
                        biomassMissingWetWeight = biomassMissing,
                        pLnP = per?.pLnP,
                        Y = per?.Y,
                    ),
                )
            }
        }
    }

    return CalcOutput(points = points, cells = cells)
}

private fun parseCalcOutputLenient(json: String): CalcOutput {
    val root = AppJson.parseToJsonElement(json).jsonObject
    val points = root["points"].toArray().mapNotNull { parsePointIndex(it) }
    val cells = root["cells"].toArray().mapNotNull { parseCell(it) }
    val notes = root["notes"].toArray().mapNotNull { it.toStringValue() }
    return CalcOutput(points = points, cells = cells, notes = notes)
}

private fun parseCalcAuditOutputLenient(json: String): CalcAuditOutput {
    val root = AppJson.parseToJsonElement(json).jsonObject
    return parseCalcAuditOutputFromObject(root)
}

private fun parseCalcAuditOutputFromElement(el: JsonElement): CalcAuditOutput? {
    val obj = el as? JsonObject ?: return null
    return parseCalcAuditOutputFromObject(obj)
}

private fun parseCalcAuditOutputFromObject(root: JsonObject): CalcAuditOutput {
    val pointIdx = root["pointIdx"].toIntValue()
        ?: root["pointIndex"].toIntValue()
        ?: root["idx"].toIntValue()
        ?: 0
    val listEl = root["mismatches"] ?: root["diffs"] ?: root["changes"]
    val mismatches = listEl.toArray().mapNotNull { parseAuditMismatch(it) }
    val notes = root["notes"].toArray().mapNotNull { it.toStringValue() }
    return CalcAuditOutput(pointIdx = pointIdx, mismatches = mismatches, notes = notes)
}

private fun parseAuditMismatch(el: JsonElement): CalcAuditMismatch? {
    val obj = el.toObject() ?: return null
    val kind = obj["kind"].toStringValue()
        ?: obj["field"].toStringValue()
        ?: obj["type"].toStringValue()
        ?: return null
    val speciesIdx = obj["speciesIdx"].toIntValue() ?: obj["speciesIndex"].toIntValue()
    val apiValue = obj["apiValue"].toDoubleValue() ?: obj["value"].toDoubleValue() ?: obj["api"].toDoubleValue()
    val apiMissing = obj["apiMissingWetWeight"].toBooleanValue()
        ?: obj["missingWetWeight"].toBooleanValue()
        ?: obj["missing"].toBooleanValue()
    return CalcAuditMismatch(kind = kind, speciesIdx = speciesIdx, apiValue = apiValue, apiMissingWetWeight = apiMissing)
}

private fun extractBestCalcJson(text: String): String? {
    val candidates = extractAllJsonPayloads(text, "FINAL_CALC_JSON")
    if (candidates.isEmpty()) return null
    var best: String? = null
    var bestScore = -1
    for (candidate in candidates) {
        val normalized = normalizeCalcJsonCandidate(candidate) ?: continue
        val output = parseCalcOutput(normalized) ?: parseCalcOutput(softFixJson(normalized)) ?: continue
        val score = calcOutputScore(output)
        if (score > bestScore) {
            bestScore = score
            best = normalized
        }
    }
    val fallback = best ?: normalizeCalcJsonCandidate(candidates.first())
    return fallback?.let { softFixJson(it) }
}

private fun normalizeCalcJsonCandidate(candidate: String): String? {
    val trimmed = candidate.trim()
    if (trimmed.isBlank()) return null
    val el = runCatching { AppJson.parseToJsonElement(trimmed) }.getOrNull()
    val obj = el as? JsonObject ?: return trimmed
    val inner = obj["FINAL_CALC_JSON"]
        ?: obj["final_calc_json"]
        ?: obj["finalCalcJson"]
        ?: obj["final_calc"]
    return inner?.toString() ?: trimmed
}

private fun calcOutputScore(output: CalcOutput): Int {
    val stats = calcOutputStats(output)
    return stats.pointCount * 10 + stats.cellCount
}

private fun softFixJson(input: String): String {
    var text = input.trim()
    if (text.isBlank()) return text
    text = text
        .replace('“', '"')
        .replace('”', '"')
        .replace('：', ':')
        .replace('，', ',')
    text = text.replace("NaN", "null", ignoreCase = true)
        .replace("Infinity", "null", ignoreCase = true)
        .replace("-Infinity", "null", ignoreCase = true)
    text = text.replace(Regex(",\\s*([}\\]])"), "$1")
    if (!text.contains('"') && text.contains('\'')) {
        text = text.replace('\'', '"')
    }
    text = text.replace(Regex("""(?m)(\{|,)\s*([A-Za-z_][A-Za-z0-9_]*)\s*:"""), """$1"$2":""")
    return text
}

private fun parsePointIndex(el: JsonElement): CalcOutputPointIndex? {
    val obj = el.toObject() ?: return null
    val idx = obj["pointIdx"].toIntValue() ?: obj["pointIndex"].toIntValue() ?: return null
    val h = obj["H"].toDoubleValue() ?: obj["h"].toDoubleValue()
    val j = obj["J"].toDoubleValue() ?: obj["j"].toDoubleValue()
    val d = obj["D"].toDoubleValue() ?: obj["d"].toDoubleValue()
    return CalcOutputPointIndex(pointIdx = idx, H = h, J = j, D = d)
}

private fun parseCell(el: JsonElement): CalcOutputCell? {
    val obj = el.toObject() ?: return null
    val sIdx = obj["speciesIdx"].toIntValue() ?: obj["speciesIndex"].toIntValue() ?: return null
    val pIdx = obj["pointIdx"].toIntValue() ?: obj["pointIndex"].toIntValue() ?: return null
    val density = obj["density"].toDoubleValue()
    val biomass = obj["biomassMgL"].toDoubleValue() ?: obj["biomass"].toDoubleValue()
    val missing = obj["biomassMissingWetWeight"].toBooleanValue()
        ?: obj["missingWetWeight"].toBooleanValue()
        ?: obj["missing"].toBooleanValue()
        ?: false
    val pLnP = obj["pLnP"].toDoubleValue() ?: obj["plnp"].toDoubleValue() ?: obj["p_ln_p"].toDoubleValue()
    val y = obj["Y"].toDoubleValue() ?: obj["y"].toDoubleValue()
    return CalcOutputCell(
        speciesIdx = sIdx,
        pointIdx = pIdx,
        density = density,
        biomassMgL = biomass,
        biomassMissingWetWeight = missing,
        pLnP = pLnP,
        Y = y,
    )
}

private fun JsonElement?.toObject(): JsonObject? = this as? JsonObject

private fun JsonElement?.toArray(): List<JsonElement> {
    return (this as? JsonArray)?.toList() ?: emptyList()
}

private fun JsonElement?.toStringValue(): String? {
    val p = this?.jsonPrimitive ?: return null
    val raw = p.content.trim()
    if (raw.isBlank() || raw.equals("null", ignoreCase = true)) return null
    return raw
}

private fun JsonElement?.toIntValue(): Int? {
    val p = this?.jsonPrimitive ?: return null
    val raw = p.content.trim()
    if (raw.isBlank() || raw.equals("null", ignoreCase = true)) return null
    return p.intOrNull ?: raw.toDoubleOrNull()?.toInt()
}

private fun JsonElement?.toDoubleValue(): Double? {
    val p = this?.jsonPrimitive ?: return null
    val raw = p.content.trim()
    if (raw.isBlank() || raw.equals("null", ignoreCase = true)) return null
    return p.doubleOrNull ?: raw.toDoubleOrNull()
}

private fun JsonElement?.toBooleanValue(): Boolean? {
    val p = this?.jsonPrimitive ?: return null
    if (!p.isString) return p.booleanOrNull
    return when (p.content.trim().lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }
}
