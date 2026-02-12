package com.plankton.one102.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

sealed interface BatchCountCommand {
    val raw: String

    data class SetCount(
        override val raw: String,
        val pointToken: String?,
        val speciesToken: String,
        val value: Int,
    ) : BatchCountCommand

    data class DeltaCount(
        override val raw: String,
        val pointToken: String?,
        val speciesToken: String,
        val delta: Int,
    ) : BatchCountCommand

    /**
     * 删除物种：
     * - pointToken!=null：把该点位的计数置 0
     * - pointToken==null：从数据集移除该物种（危险操作，建议 UI 二次确认）
     */
    data class DeleteSpecies(
        override val raw: String,
        val pointToken: String?,
        val speciesToken: String,
    ) : BatchCountCommand
}

data class BatchParseResult(
    val commands: List<BatchCountCommand>,
    val errors: List<String> = emptyList(),
)

private fun normalizeText(s: String): String {
    return s.trim()
        .replace('\u3000', ' ')
        .replace(Regex("""[，。；;]+"""), "\n")
        .replace(Regex("""\s+"""), " ")
}

private fun splitClauses(input: String): List<String> {
    val normalized = normalizeText(input)
    return normalized
        .split("\n")
        .map { it.trim() }
        .flatMap { line ->
            line.split(Regex("""(?<=[,，。；;])|(?=然后)|(?=并且)|(?=同时)|(?=另外)"""))
        }
        .map { it.trim().trim(',', '，', '。', ';', '；') }
        .filter { it.isNotBlank() }
}

private fun cleanSpeciesToken(s: String): String {
    return s.trim()
        .removePrefix("物种")
        .removePrefix("一个")
        .removeSuffix("物种")
        .replace("的个数", "")
        .replace("个数", "")
        .replace("数量", "")
        .replace("计数", "")
        .trim()
        .trim('：', ':', '。', '，', ',', '；', ';')
        .trim()
}

fun parseBatchCountCommands(input: String): BatchParseResult {
    val clauses = splitClauses(input)
    val cmds = mutableListOf<BatchCountCommand>()
    val errors = mutableListOf<String>()

    fun addError(raw: String, msg: String) {
        errors += "「$raw」：$msg"
    }

    for (rawClause in clauses) {
        val clause = rawClause.trim()
        if (clause.isBlank()) continue

        var matched = false

        // Patterns: set count
        Regex(
            pattern = """(?:把|将)?(?:(?<point>[^的]{1,20})的)?(?<species>.+?)(?:的)?(?:计数|个数|数量)?(?:改为|设置为|设为|=|为)(?<num>[-+]?[\d一二三四五六七八九十百千两零]+)""",
        ).find(clause)?.let { m ->
            val point = m.groups["point"]?.value?.trim()?.takeIf { it.isNotBlank() }
            val species = cleanSpeciesToken(m.groups["species"]?.value.orEmpty())
            val numRaw = m.groups["num"]?.value.orEmpty()
            val n = parseIntSmart(numRaw)
            if (species.isBlank()) addError(rawClause, "未识别到物种名称") else cmds += BatchCountCommand.SetCount(rawClause, point, species, n)
            matched = true
        }
        if (matched) continue

        // Patterns: delta count (increase/decrease)
        Regex(
            pattern = """(?:(?<point>[^的]{1,20})的)?(?<species>.+?)(?:的)?(?:计数|个数|数量)?(?<op>增加|加上|加|减少|减去|减)(?<num>[\d一二三四五六七八九十百千两零]+)?""",
        ).find(clause)?.let { m ->
            val point = m.groups["point"]?.value?.trim()?.takeIf { it.isNotBlank() }
            val species = cleanSpeciesToken(m.groups["species"]?.value.orEmpty())
            val op = m.groups["op"]?.value.orEmpty()
            val numRaw = m.groups["num"]?.value
            val n = if (numRaw.isNullOrBlank()) 1 else parseIntSmart(numRaw)
            val delta = if (op.contains("减") || op.contains("少")) -abs(n) else abs(n)
            if (species.isBlank()) addError(rawClause, "未识别到物种名称") else cmds += BatchCountCommand.DeltaCount(rawClause, point, species, delta)
            matched = true
        }
        if (matched) continue

        // Patterns: add species at point (treat as delta +1 unless number present)
        Regex(
            pattern = """(?:在)?(?<point>[^，,。；;]{1,20}?)(?:点位|采样点|点)?(?:新增|添加|增加)(?:物种)?(?<species>[^，,。；;]+?)(?<num>[\d一二三四五六七八九十百千两零]+)?(?:个|只|条)?$""",
        ).find(clause)?.let { m ->
            val point = m.groups["point"]?.value?.trim()?.takeIf { it.isNotBlank() }
            val species = cleanSpeciesToken(m.groups["species"]?.value.orEmpty())
            val n = m.groups["num"]?.value?.let { parseIntSmart(it) } ?: 1
            if (point.isNullOrBlank()) addError(rawClause, "未识别到点位") else if (species.isBlank()) addError(rawClause, "未识别到物种名称") else cmds += BatchCountCommand.DeltaCount(rawClause, point, species, abs(n))
            matched = true
        }
        if (matched) continue

        // Patterns: delete species
        Regex(
            pattern = """(?:删除|移除|删掉)(?:(?<point>[^的]{1,20})的)?(?<species>.+)""",
        ).find(clause)?.let { m ->
            val point = m.groups["point"]?.value?.trim()?.takeIf { it.isNotBlank() }
            val speciesRaw = m.groups["species"]?.value.orEmpty()
            val speciesList = speciesRaw
                .split('、', '，', ',', '和', '及')
                .map { cleanSpeciesToken(it) }
                .filter { it.isNotBlank() }
            if (speciesList.isEmpty()) {
                addError(rawClause, "未识别到物种名称")
            } else {
                speciesList.forEach { sp -> cmds += BatchCountCommand.DeleteSpecies(rawClause, point, sp) }
            }
            matched = true
        }
        if (matched) continue

        addError(rawClause, "未识别到可执行指令（可用：改为/设置为、增加/减少、删除）")
    }

    return BatchParseResult(commands = cmds, errors = errors)
}

/**
 * 尽量把“1/一/十/二十三/两百”等解析为 Int。
 * 范围：0~9999（超出会截断）。
 */
fun parseIntSmart(input: String): Int {
    val s = input.trim()
    if (s.isEmpty()) return 0
    s.toIntOrNull()?.let { return it.coerceIn(0, 9999) }

    val map = mapOf(
        '零' to 0,
        '一' to 1,
        '二' to 2,
        '两' to 2,
        '三' to 3,
        '四' to 4,
        '五' to 5,
        '六' to 6,
        '七' to 7,
        '八' to 8,
        '九' to 9,
    )
    fun digit(c: Char): Int? = map[c]

    var result = 0
    var current = 0
    var hasAny = false

    fun flush(unit: Int) {
        hasAny = true
        current = if (current == 0) 1 else current
        result += current * unit
        current = 0
    }

    for (c in s) {
        when (c) {
            '十' -> flush(10)
            '百' -> flush(100)
            '千' -> flush(1000)
            else -> {
                val d = digit(c)
                if (d != null) {
                    hasAny = true
                    current = current * 10 + d
                }
            }
        }
    }
    result += current
    if (!hasAny) return 0
    return result.coerceIn(0, 9999)
}

data class NameCorrection(
    val raw: String,
    val canonical: String,
    val score: Double,
)

private fun normalizeNameToken(name: String): String {
    return name.trim()
        .lowercase()
        .replace(Regex("""[\s·•\-\(\)（）【】\[\]{}，,。.;；:：'"]+"""), "")
}

private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    val n = a.length
    val m = b.length
    val dp = IntArray(m + 1) { it }
    for (i in 1..n) {
        var prev = dp[0]
        dp[0] = i
        for (j in 1..m) {
            val tmp = dp[j]
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            dp[j] = min(
                min(dp[j] + 1, dp[j - 1] + 1),
                prev + cost,
            )
            prev = tmp
        }
    }
    return dp[m]
}

/**
 * 在候选列表中做一个尽量稳健的“纠错匹配”，返回：
 * - canonical：选择的规范名
 * - score：0~1（越大越可信）
 */
fun bestCanonicalName(raw: String, candidates: Collection<String>): NameCorrection? {
    val q = raw.trim()
    if (q.isEmpty()) return null
    if (candidates.contains(q)) return NameCorrection(raw = raw, canonical = q, score = 1.0)

    val qn = normalizeNameToken(q)
    if (qn.isEmpty()) return null

    var best: String? = null
    var bestScore = 0.0
    for (c in candidates) {
        val cn = normalizeNameToken(c)
        if (cn.isEmpty()) continue
        if (cn == qn) {
            return NameCorrection(raw = raw, canonical = c, score = 0.98)
        }
        val maxLen = max(cn.length, qn.length).coerceAtLeast(1)
        val dist = levenshtein(qn, cn)
        val score = 1.0 - (dist.toDouble() / maxLen.toDouble())
        if (score > bestScore) {
            bestScore = score
            best = c
        }
    }
    if (best == null) return null
    return NameCorrection(raw = raw, canonical = best, score = bestScore.coerceIn(0.0, 1.0))
}
