package com.plankton.one102.data.api

import com.plankton.one102.data.AppJson
import com.plankton.one102.domain.parseIntSmart
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class AiImageSpeciesRow(
    val name: String,
    val countExpr: String? = null,
    val count: Int? = null,
    val confidence: Double? = null,
    val rawLine: String? = null,
)

@Serializable
data class AiImagePoint(
    val label: String,
    val species: List<AiImageSpeciesRow> = emptyList(),
)

@Serializable
data class AiImageImport(
    val points: List<AiImagePoint> = emptyList(),
    val notes: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
)

fun buildImageImportPrompt(strictJsonOnly: Boolean = false, detailMode: Boolean = false): String {
    return """
        你是“浮游动物一体化”应用的图片识别助手（偏 OCR 抄写）。
        目标：从图片中**尽可能完整**地识别采样点、物种名称与计数，尽量不要漏掉点位或物种。

        场景规则（务必遵守）：
        1) 一张纸上可能有多个采样点；一个采样点通常以“点位名称”开头（如 LS1、LS4、J4、1-0.3、LSH3）。
        2) 点位名称下一行开始是物种名称与计数，计数可能写成“1+1”“2+1”等，也可能用竖线分隔。
        3) 只做**抄写**，不要把物种纠错为标准名，不要凭常识替换。
        4) countExpr 保留原始表达（例如 "1+1"），count 为加总后的整数；若无法判断，count 可为空并在 warnings 说明。
        5) 若某行被涂掉/划掉/作废，请判定为无效文字，不要导入到 points；只在 warnings 中说明。
        6) 需要识别**全部点位**；如果某点位不确定，仍然输出该点位并在 warnings 说明。
        7) 输出里可包含 rawLine（该行原始抄写），方便人工核对。
        8) 不要输出多余解释；最终只输出一行 JSON。

        输出格式（最后一行，且只输出这一行）：
        FINAL_IMAGE_JSON: {
          "points":[
            {"label":"LS1","species":[{"name":"某物种","countExpr":"1+1","count":2,"confidence":0.86,"rawLine":"某物种 1+1"}]}
          ],
          "notes":["可选备注"],
          "warnings":["识别不清的条目说明"]
        }
        
        ${if (detailMode) "补充要求：逐行抄写，不要合并或省略；看不清就原样写入 rawLine 并在 warnings 提示。" else ""}
        ${if (strictJsonOnly) "只输出 JSON，不要输出其它文字。" else ""}
    """.trimIndent()
}

fun extractFinalImageJson(text: String): String? {
    val candidates = extractAllJsonPayloads(text, "FINAL_IMAGE_JSON")
    if (candidates.isEmpty()) return null
    return candidates.firstOrNull { hasPointsPayload(it) } ?: candidates.first()
}

private fun hasPointsPayload(json: String): Boolean {
    val root = runCatching { AppJson.parseToJsonElement(json).jsonObject }.getOrNull() ?: return false
    return root.containsKey("points")
}

fun parseAiImageImport(json: String): AiImageImport? {
    val strict = runCatching { AppJson.decodeFromString(AiImageImport.serializer(), json) }.getOrNull()
    if (strict != null) return strict
    return runCatching { parseAiImageImportLenient(json) }.getOrNull()
}

private fun parseAiImageImportLenient(json: String): AiImageImport {
    val root = AppJson.parseToJsonElement(json).jsonObject
    val points = root["points"].toArray().mapNotNull { parsePoint(it) }
    val notes = root["notes"].toArray().mapNotNull { it.toStringValue() }
    val warnings = root["warnings"].toArray().mapNotNull { it.toStringValue() }
    return AiImageImport(points = points, notes = notes, warnings = warnings)
}

private fun parsePoint(el: JsonElement): AiImagePoint? {
    val obj = el.toObject() ?: return null
    val label = obj["label"].toStringValue()
        ?: obj["point"].toStringValue()
        ?: obj["point_name"].toStringValue()
        ?: obj["pointName"].toStringValue()
        ?: obj["name"].toStringValue()
        ?: return null
    val speciesArray = obj["species"]
        ?: obj["items"]
        ?: obj["rows"]
        ?: obj["list"]
    val species = speciesArray.toArray().mapNotNull { parseSpecies(it) }
    return AiImagePoint(label = label, species = species)
}

private fun parseSpecies(el: JsonElement): AiImageSpeciesRow? {
    val obj = el.toObject()
    if (obj == null) {
        val raw = el.toStringValue() ?: return null
        val (name, expr) = splitNameAndCount(raw)
        val count = parseCountExpr(expr) ?: expr?.toIntOrNull()
        return AiImageSpeciesRow(
            name = name,
            countExpr = expr,
            count = count,
            rawLine = raw,
        )
    }
    val name = obj["name"].toStringValue()
        ?: obj["species"].toStringValue()
        ?: obj["species_name"].toStringValue()
        ?: obj["speciesName"].toStringValue()
        ?: obj["nameCn"].toStringValue()
        ?: obj["item"].toStringValue()
        ?: return null
    val countExpr = obj["countExpr"].toStringValue()
        ?: obj["count_expr"].toStringValue()
        ?: obj["expr"].toStringValue()
        ?: obj["countExpression"].toStringValue()
    var count = obj["count"].toIntValue()
        ?: obj["n"].toIntValue()
        ?: obj["num"].toIntValue()
        ?: obj["value"].toIntValue()
    if (count == null && countExpr != null) {
        count = parseCountExpr(countExpr)
    }
    val confidence = obj["confidence"].toDoubleValue()
    val rawLine = obj["rawLine"].toStringValue() ?: obj["raw"].toStringValue()
    return AiImageSpeciesRow(
        name = name,
        countExpr = countExpr,
        count = count,
        confidence = confidence,
        rawLine = rawLine,
    )
}

private fun parseCountExpr(expr: String?): Int? {
    val text = expr?.trim()
        ?.replace("＋", "+")
        ?.replace("加", "+")
        ?.replace(" ", "")
        .orEmpty()
    if (text.isBlank()) return null
    val parts = text.split("+").filter { it.isNotBlank() }
    if (parts.isEmpty()) return null
    var sum = 0
    for (p in parts) {
        val token = p.trim()
        if (token.isEmpty()) return null
        val hasNumeric = token.any { it.isDigit() || "一二三四五六七八九十百千两零".contains(it) }
        if (!hasNumeric) return null
        val n = token.toIntOrNull() ?: parseIntSmart(token)
        if (n < 0) return null
        sum += n
    }
    return sum
}

private fun splitNameAndCount(raw: String): Pair<String, String?> {
    val text = raw.trim()
    if (text.isBlank()) return "" to null
    val match = Regex("""(.+?)[\\s\\|｜]+([0-9一二三四五六七八九十百千两零＋+加]{1,12})\\s*$""").find(text)
    return if (match != null) {
        match.groupValues[1].trim() to match.groupValues[2].trim()
    } else {
        text to null
    }
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
