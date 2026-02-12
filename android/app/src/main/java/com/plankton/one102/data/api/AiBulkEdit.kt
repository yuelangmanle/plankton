package com.plankton.one102.data.api

import com.plankton.one102.data.AppJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class AiBulkCommand(
    val actions: List<AiBulkAction> = emptyList(),
    val unparsed: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
)

@Serializable
data class AiBulkAction(
    val type: String = "",
    val point: String? = null,
    val species: String? = null,
    val from: String? = null,
    val to: String? = null,
    val value: Double? = null,
    val delta: Double? = null,
    val vc: Double? = null,
    val vo: Double? = null,
    val source: String? = null,
    val writeToDb: Boolean? = null,
    val note: String? = null,
)

fun extractFinalBulkJson(text: String): String? {
    val marked = extractAllJsonPayloads(text, "FINAL_BULK_JSON")
    val all = if (marked.isNotEmpty()) marked else extractAllJsonPayloads(text, null)
    if (all.isEmpty()) return null
    val keyCandidates = listOf("\"actions\"", "\"commands\"", "\"cmds\"", "\"ops\"", "\"operations\"")
    return all.firstOrNull { json -> keyCandidates.any { json.contains(it) } } ?: all.first()
}

fun parseAiBulkCommand(json: String): AiBulkCommand? {
    val strict = runCatching { AppJson.decodeFromString(AiBulkCommand.serializer(), json) }.getOrNull()
    val lenient = runCatching { parseAiBulkCommandLenient(json) }.getOrNull()
    return when {
        strict == null -> lenient
        lenient == null -> strict
        lenient.estimateQuality() > strict.estimateQuality() -> lenient
        else -> strict
    }
}

internal fun AiBulkCommand.estimateQuality(): Int {
    val actionScore = actions.sumOf { it.estimateQuality() }
    return actionScore * 100 + actions.size * 5 + unparsed.size + warnings.size + notes.size
}

private fun AiBulkAction.estimateQuality(): Int {
    var score = 0
    if (type.isNotBlank()) score += 10
    if (hasPayload()) score += 3
    if (!note.isNullOrBlank()) score += 1
    return score
}

private fun AiBulkAction.hasPayload(): Boolean {
    return !point.isNullOrBlank() ||
        !species.isNullOrBlank() ||
        !from.isNullOrBlank() ||
        !to.isNullOrBlank() ||
        value != null ||
        delta != null ||
        vc != null ||
        vo != null ||
        !source.isNullOrBlank() ||
        writeToDb != null ||
        !note.isNullOrBlank()
}

private fun parseAiBulkCommandLenient(json: String): AiBulkCommand {
    val rootEl = AppJson.parseToJsonElement(json)
    val rootObj = rootEl as? JsonObject
    val actionsEl = when {
        rootObj != null -> rootObj["actions"]
            ?: rootObj["commands"]
            ?: rootObj["cmds"]
            ?: rootObj["ops"]
            ?: rootObj["operations"]
            ?: rootObj["items"]
        rootEl is JsonArray -> rootEl
        else -> null
    }
    val actions = when (actionsEl) {
        is JsonArray -> actionsEl.mapNotNull { parseAction(it) }
        is JsonObject -> actionsEl.entries.flatMap { (key, value) ->
            when (value) {
                is JsonArray -> value.mapNotNull { parseAction(it, forcedType = key) }
                else -> listOfNotNull(parseAction(value, forcedType = key))
            }
        }
        else -> emptyList()
    }
    val unparsed = rootObj?.let {
        parseStringList(it["unparsed"] ?: it["unknown"] ?: it["rest"] ?: it["others"])
    } ?: emptyList()
    val notes = rootObj?.let { parseStringList(it["notes"]) } ?: emptyList()
    val warnings = rootObj?.let { parseStringList(it["warnings"]) } ?: emptyList()
    return AiBulkCommand(actions = actions, unparsed = unparsed, notes = notes, warnings = warnings)
}

private fun parseAction(el: JsonElement, forcedType: String? = null): AiBulkAction? {
    val prim = el as? JsonPrimitive
    if (prim != null) {
        val text = prim.contentOrNull()?.trim().orEmpty()
        return if (text.isBlank()) null else AiBulkAction(type = forcedType ?: text)
    }
    val obj = el as? JsonObject ?: return null
    if (forcedType == null && obj.size == 1) {
        val entry = obj.entries.first()
        return parseAction(entry.value, forcedType = entry.key)
    }
    val type = forcedType ?: obj.stringAny("type", "action", "op", "operation", "cmd", "command") ?: return null
    val payload = obj.objectAny("payload", "params", "args", "data", "fields", "values")
    return AiBulkAction(
        type = type,
        point = payload?.stringAny(
            "point", "label", "pointLabel", "point_name", "pointName", "point_label", "pointId", "point_id",
        ) ?: obj.stringAny("point", "label", "pointLabel", "point_name", "pointName", "point_label"),
        species = payload?.stringAny(
            "species", "name", "speciesName", "species_name", "speciesCn", "sp",
        ) ?: obj.stringAny("species", "name", "speciesName", "species_name", "speciesCn", "sp"),
        from = payload?.stringAny(
            "from", "old", "oldName", "oldLabel", "pointFrom", "speciesFrom", "src",
        ) ?: obj.stringAny("from", "old", "oldName", "oldLabel", "pointFrom", "speciesFrom", "src"),
        to = payload?.stringAny(
            "to", "new", "newName", "newLabel", "pointTo", "speciesTo", "dst",
        ) ?: obj.stringAny("to", "new", "newName", "newLabel", "pointTo", "speciesTo", "dst"),
        value = payload?.doubleAny("value", "count", "num", "n") ?: obj.doubleAny("value", "count", "num", "n"),
        delta = payload?.doubleAny("delta", "change", "inc", "dec") ?: obj.doubleAny("delta", "change", "inc", "dec"),
        vc = payload?.doubleAny("vc", "vConc", "vConcMl", "v_conc", "vconc")
            ?: obj.doubleAny("vc", "vConc", "vConcMl", "v_conc", "vconc"),
        vo = payload?.doubleAny("vo", "vOrig", "vOrigL", "v_orig", "vorig")
            ?: obj.doubleAny("vo", "vOrig", "vOrigL", "v_orig", "vorig"),
        source = payload?.stringAny("source", "origin", "via") ?: obj.stringAny("source", "origin", "via"),
        writeToDb = payload?.boolAny("writeToDb", "write_db", "saveToDb", "saveToDatabase")
            ?: obj.boolAny("writeToDb", "write_db", "saveToDb", "saveToDatabase"),
        note = payload?.stringAny("note", "reason", "memo") ?: obj.stringAny("note", "reason", "memo"),
    )
}

private fun parseStringList(el: JsonElement?): List<String> {
    val prim = el as? JsonPrimitive
    if (prim != null) {
        val text = prim.contentOrNull()?.trim().orEmpty()
        return if (text.isBlank()) emptyList() else listOf(text)
    }
    val arr = el as? JsonArray ?: return emptyList()
    return arr.mapNotNull { it.jsonPrimitive.contentOrNull()?.trim()?.takeIf { s -> s.isNotBlank() } }
}

private fun JsonObject.stringAny(vararg keys: String): String? {
    for (k in keys) {
        val v = this[k] ?: continue
        when (v) {
            is JsonPrimitive -> {
                val text = v.contentOrNull()?.trim()
                if (!text.isNullOrBlank()) return text
            }
            is JsonObject -> {
                val nested = v.stringAny("label", "name", "text", "value")
                if (!nested.isNullOrBlank()) return nested
            }
            is JsonArray -> {
                val first = v.firstOrNull()
                val text = (first as? JsonPrimitive)?.contentOrNull()?.trim()
                if (!text.isNullOrBlank()) return text
            }
        }
    }
    return null
}

private fun JsonObject.doubleAny(vararg keys: String): Double? {
    for (k in keys) {
        val v = this[k] ?: continue
        when (v) {
            is JsonPrimitive -> {
                val num = v.doubleOrNull
                if (num != null) return num
                val text = v.contentOrNull()?.trim()
                val parsed = text?.toDoubleOrNull()
                if (parsed != null) return parsed
            }
            is JsonObject -> {
                val nested = v.doubleAny("value", "count", "num", "n", "delta")
                if (nested != null) return nested
            }
            is JsonArray -> {
                val first = v.firstOrNull() as? JsonPrimitive
                val parsed = first?.doubleOrNull ?: first?.contentOrNull()?.trim()?.toDoubleOrNull()
                if (parsed != null) return parsed
            }
        }
    }
    return null
}

private fun JsonObject.boolAny(vararg keys: String): Boolean? {
    for (k in keys) {
        val v = this[k] ?: continue
        val prim = v as? JsonPrimitive
        if (prim != null) {
            val b = prim.booleanOrNull
            if (b != null) return b
            val text = prim.contentOrNull()?.trim()?.lowercase().orEmpty()
            when (text) {
                "true", "yes", "y", "1", "是", "写入", "保存", "需要", "要" -> return true
                "false", "no", "n", "0", "否", "不写入", "不保存", "不要" -> return false
            }
        }
    }
    return null
}

private fun JsonObject.objectAny(vararg keys: String): JsonObject? {
    for (k in keys) {
        val v = this[k] ?: continue
        if (v is JsonObject) return v
    }
    return null
}

private fun JsonElement.asArray(): JsonArray? = this as? JsonArray

private fun JsonPrimitive.contentOrNull(): String? = runCatching { this.content }.getOrNull()
