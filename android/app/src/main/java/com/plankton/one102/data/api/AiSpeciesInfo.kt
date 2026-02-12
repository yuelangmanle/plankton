package com.plankton.one102.data.api

import com.plankton.one102.data.AppJson
import kotlinx.serialization.Serializable

@Serializable
data class AiSpeciesInfo(
    val nameLatin: String? = null,
    val wetWeightMg: Double? = null,
    val lvl1: String? = null,
    val lvl2: String? = null,
    val lvl3: String? = null,
    val lvl4: String? = null,
    val lvl5: String? = null,
)

fun buildSpeciesInfoPrompt(nameCn: String, latin: String?): String {
    val latinFixed = latin?.trim().takeIf { !it.isNullOrBlank() } ?: "（未提供）"
    return """
        请为以下浮游动物补齐“分类（内置分类库）”与“平均湿重（mg/个）”。

        物种中文名：$nameCn
        拉丁名：$latinFixed

        分类字段（对应分类库 A-E）：
        - lvl1：四大类之一（必须从：原生动物 / 轮虫类 / 枝角类 / 桡足类 中选择；不确定请留空）
        - lvl2：纲（可空）
        - lvl3：目（可空）
        - lvl4：科（可空）
        - lvl5：属（可空）

        平均湿重：wetWeightMg（单位 mg/个；不确定请留空）

        输出要求（必须满足）：
        1) 只要最终结构化结果即可，不要输出多余解释。
        2) 最后一行严格输出（只输出一行，不要代码块）：
           FINAL_SPECIES_JSON: <{"nameLatin":"...","wetWeightMg":0.0005,"lvl1":"...","lvl2":"...","lvl3":"...","lvl4":"...","lvl5":"..."} 或 UNKNOWN>
    """.trimIndent()
}

fun buildSpeciesTaxonomyAutofillPrompt(nameCn: String, latin: String?): String {
    val latinFixed = latin?.trim().takeIf { !it.isNullOrBlank() } ?: "（未提供）"
    return """
        请为以下浮游动物补齐“分类（内置分类库）”。

        物种中文名：$nameCn
        拉丁名：$latinFixed

        分类字段（对应分类库 A-E）：
        - lvl1：四大类之一（必须从：原生动物 / 轮虫类 / 枝角类 / 桡足类 中选择；不确定请留空）
        - lvl2：纲（可空）
        - lvl3：目（可空）
        - lvl4：科（可空）
        - lvl5：属（可空）

        输出要求（必须满足）：
        1) 只要最终结构化结果即可，不要输出多余解释。
        2) 最后一行严格输出（只输出一行，不要代码块）：
           FINAL_SPECIES_JSON: <{"nameLatin":"...","wetWeightMg":null,"lvl1":"...","lvl2":"...","lvl3":"...","lvl4":"...","lvl5":"..."} 或 UNKNOWN>
    """.trimIndent()
}

fun buildSpeciesWetWeightAutofillPrompt(nameCn: String, latin: String?): String {
    val latinFixed = latin?.trim().takeIf { !it.isNullOrBlank() } ?: "（未提供）"
    return """
        请为以下浮游动物补齐“平均湿重（mg/个）”。

        物种中文名：$nameCn
        拉丁名：$latinFixed

        输出要求（必须满足）：
        1) 只要最终结构化结果即可，不要输出多余解释。
        2) wetWeightMg 只输出正数或 null（不确定就留空）。
        3) 最后一行严格输出（只输出一行，不要代码块）：
           FINAL_SPECIES_JSON: <{"nameLatin":"...","wetWeightMg":0.0005,"lvl1":null,"lvl2":null,"lvl3":null,"lvl4":null,"lvl5":null} 或 UNKNOWN>
    """.trimIndent()
}

fun extractFinalSpeciesJson(text: String): String? {
    val line = text.lineSequence().lastOrNull { it.trim().startsWith("FINAL_SPECIES_JSON:", ignoreCase = true) } ?: return null
    var raw = line.substringAfter(":", "").trim()
    if (raw.equals("UNKNOWN", ignoreCase = true)) return null
    raw = raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    return raw.takeIf { it.isNotBlank() }
}

fun parseAiSpeciesInfo(json: String): AiSpeciesInfo? {
    return runCatching { AppJson.decodeFromString(AiSpeciesInfo.serializer(), json) }.getOrNull()
}
