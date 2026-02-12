package com.plankton.one102.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.plankton.one102.ui.components.GlassCard

private sealed interface AiBlock {
    data class Heading(val level: Int, val text: String) : AiBlock
    data class Paragraph(val text: String) : AiBlock
    data class Bullet(val items: List<String>) : AiBlock
    data class Ordered(val items: List<String>) : AiBlock
    data class Quote(val text: String) : AiBlock
    data class Code(val text: String) : AiBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : AiBlock
}

@Composable
fun AiRichText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    compact: Boolean = false,
    maxLines: Int? = null,
) {
    val clean = text.trim()
    if (clean.isBlank()) {
        Text("（暂无）", style = style, modifier = modifier)
        return
    }
    if (compact) {
        val annotated = remember(clean) { safeAnnotatedText(clean, style) }
        Text(
            text = annotated,
            style = style,
            maxLines = maxLines ?: Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
        return
    }

    val blocks = remember(clean) {
        runCatching { parseAiBlocks(clean) }.getOrElse { listOf(AiBlock.Paragraph(clean)) }
    }
    SelectionContainer {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (block in blocks) {
                when (block) {
                    is AiBlock.Heading -> {
                        val headingStyle = when {
                            block.level <= 1 -> MaterialTheme.typography.titleLarge
                            block.level == 2 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        }
                        Text(safeAnnotatedText(block.text, headingStyle), style = headingStyle)
                    }
                    is AiBlock.Paragraph -> {
                        val isFormula = looksLikeFormula(block.text)
                        val pStyle = if (isFormula) {
                            style.copy(fontFamily = FontFamily.Serif)
                        } else {
                            style
                        }
                        Text(safeAnnotatedText(block.text, pStyle, isFormula), style = pStyle)
                    }
                    is AiBlock.Bullet -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (item in block.items) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text("•", style = style, modifier = Modifier.padding(end = 6.dp))
                                    val isFormula = looksLikeFormula(item)
                                    val itemStyle = if (isFormula) style.copy(fontFamily = FontFamily.Serif) else style
                                    Text(safeAnnotatedText(item, itemStyle, isFormula), style = itemStyle, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    is AiBlock.Ordered -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for ((idx, item) in block.items.withIndex()) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text("${idx + 1}.", style = style, modifier = Modifier.padding(end = 6.dp))
                                    val isFormula = looksLikeFormula(item)
                                    val itemStyle = if (isFormula) style.copy(fontFamily = FontFamily.Serif) else style
                                    Text(safeAnnotatedText(item, itemStyle, isFormula), style = itemStyle, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    is AiBlock.Quote -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(safeAnnotatedText(block.text, style), style = style, modifier = Modifier.padding(10.dp))
                        }
                    }
                    is AiBlock.Code -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                block.text.trimEnd(),
                                style = style.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                    is AiBlock.Table -> {
                        AiTable(block.headers, block.rows, style)
                    }
                }
            }
        }
    }
}

@Composable
private fun AiTable(headers: List<String>, rows: List<List<String>>, style: TextStyle) {
    val colCount = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0)
    if (colCount == 0) return

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (headers.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (i in 0 until colCount) {
                        val cell = headers.getOrNull(i).orEmpty()
                        Text(
                            safeAnnotatedText(cell, style),
                            style = style.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            for (row in rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (i in 0 until colCount) {
                        val cell = row.getOrNull(i).orEmpty()
                        Text(safeAnnotatedText(cell, style), style = style, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun parseAiBlocks(text: String): List<AiBlock> {
    val lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    val blocks = mutableListOf<AiBlock>()
    val paragraph = mutableListOf<String>()
    val orderedRegex = Regex("""^(\d+)[\.\)、)]\s+(.*)""")

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            val merged = paragraph.joinToString("\n").trim()
            if (merged.isNotBlank()) blocks += AiBlock.Paragraph(merged)
            paragraph.clear()
        }
    }

    fun isTableSeparator(line: String): Boolean {
        val t = line.trim()
        if (!t.contains("-")) return false
        return t.all { it == '|' || it == '-' || it == ':' || it == ' ' || it == '\t' }
    }

    fun splitTableRow(line: String): List<String> {
        return line.trim().trim('|').split('|').map { it.trim() }
    }

    var i = 0
    while (i < lines.size) {
        val raw = lines[i]
        val line = raw.trim()
        if (line.isBlank()) {
            flushParagraph()
            i += 1
            continue
        }
        if (line.startsWith("```")) {
            flushParagraph()
            val buf = mutableListOf<String>()
            i += 1
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                buf += lines[i]
                i += 1
            }
            blocks += AiBlock.Code(buf.joinToString("\n").trimEnd())
            i += 1
            continue
        }
        if (line.startsWith("#")) {
            flushParagraph()
            val level = line.takeWhile { it == '#' }.length
            val title = line.drop(level).trim()
            blocks += AiBlock.Heading(level, title)
            i += 1
            continue
        }
        if (line.startsWith(">")) {
            flushParagraph()
            blocks += AiBlock.Quote(line.removePrefix(">").trim())
            i += 1
            continue
        }
        val orderedMatch = orderedRegex.find(line)
        if (orderedMatch != null) {
            flushParagraph()
            val items = mutableListOf<String>()
            while (i < lines.size) {
                val itemLine = lines[i].trim()
                val match = orderedRegex.find(itemLine) ?: break
                val item = match.groupValues[2].trim()
                if (item.isNotBlank()) items += item
                i += 1
            }
            if (items.isNotEmpty()) blocks += AiBlock.Ordered(items)
            continue
        }
        if (line.startsWith("-") || line.startsWith("*") || line.startsWith("•")) {
            flushParagraph()
            val items = mutableListOf<String>()
            while (i < lines.size) {
                val itemLine = lines[i].trim()
                if (!(itemLine.startsWith("-") || itemLine.startsWith("*") || itemLine.startsWith("•"))) break
                val item = itemLine.removePrefix("-").removePrefix("*").removePrefix("•").trim()
                if (item.isNotBlank()) items += item
                i += 1
            }
            if (items.isNotEmpty()) blocks += AiBlock.Bullet(items)
            continue
        }
        if (line.contains("|") && i + 1 < lines.size && isTableSeparator(lines[i + 1])) {
            flushParagraph()
            val headers = splitTableRow(line)
            i += 2
            val rows = mutableListOf<List<String>>()
            while (i < lines.size) {
                val rowLine = lines[i].trim()
                if (rowLine.isBlank() || !rowLine.contains("|")) break
                rows += splitTableRow(rowLine)
                i += 1
            }
            blocks += AiBlock.Table(headers, rows)
            continue
        }

        paragraph += raw
        i += 1
    }
    flushParagraph()
    return blocks
}

private fun looksLikeFormula(text: String): Boolean {
    val t = text.trim()
    if (t.isBlank()) return false
    if (t.contains("\\frac") || t.contains("\\times") || t.contains("\\cdot") || t.contains("Σ") || t.contains("∑")) return true
    if (!t.contains("=")) return false
    return t.contains("_") || t.contains("^") || t.contains("ln") || t.contains("/") || t.contains("*") || t.contains("×")
}

private fun safeAnnotatedText(text: String, baseStyle: TextStyle, formulaMode: Boolean = false): AnnotatedString {
    return runCatching { buildAnnotatedText(text, baseStyle, formulaMode) }.getOrElse { AnnotatedString(text) }
}

private fun buildAnnotatedText(text: String, baseStyle: TextStyle, formulaMode: Boolean = false): AnnotatedString {
    val normalized = normalizeMathText(text)
    val cleaned = if (formulaMode) normalized.replace(Regex("\\s\\*\\s"), " × ") else normalized
    val builder = AnnotatedString.Builder()
    val baseSize = if (baseStyle.fontSize == TextUnit.Unspecified) 14.sp else baseStyle.fontSize
    var i = 0
    val fractionRegex = Regex("""(-?[A-Za-z0-9_\.]+)\s*/\s*(-?[A-Za-z0-9_\.]+)""")

    fun appendStyled(s: String, style: SpanStyle) {
        if (s.isEmpty()) return
        builder.withStyle(style) { append(s) }
    }

    while (i < cleaned.length) {
        if (formulaMode) {
            val fractionMatch = fractionRegex.find(cleaned, i)
            if (fractionMatch != null && fractionMatch.range.first == i) {
                val numerator = fractionMatch.groupValues[1]
                val denominator = fractionMatch.groupValues[2]
                val fracSize = baseSize * 0.78f
                appendStyled(numerator, SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = fracSize))
                builder.append('⁄')
                appendStyled(denominator, SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = fracSize))
                i = fractionMatch.range.last + 1
                continue
            }
        }
        if (cleaned.startsWith("**", i)) {
            val end = cleaned.indexOf("**", i + 2)
            if (end > i + 2) {
                appendStyled(cleaned.substring(i + 2, end), SpanStyle(fontWeight = FontWeight.SemiBold))
                i = end + 2
                continue
            }
            i += 2
            continue
        }
        val ch = cleaned[i]
        if (ch == '`') {
            val end = cleaned.indexOf('`', i + 1)
            if (end > i + 1) {
                appendStyled(cleaned.substring(i + 1, end), SpanStyle(fontFamily = FontFamily.Monospace))
                i = end + 1
                continue
            }
            i += 1
            continue
        }
        if (ch == '_' || ch == '^') {
            val isSub = ch == '_'
            val next = i + 1
            if (next < cleaned.length) {
                val (token, endIdx) = readScriptToken(cleaned, next)
                if (token.isNotBlank()) {
                    val span = SpanStyle(
                        baselineShift = if (isSub) BaselineShift.Subscript else BaselineShift.Superscript,
                        fontSize = baseSize * 0.75f,
                    )
                    appendStyled(token, span)
                    i = endIdx
                    continue
                }
            }
        }
        builder.append(ch)
        i += 1
    }
    return builder.toAnnotatedString()
}

private fun normalizeMathText(text: String): String {
    var out = text.replace('\u00A0', ' ')
    out = out.replace("\\(", "").replace("\\)", "")
    out = out.replace("\\[", "").replace("\\]", "")
    out = out.replace("\\$", "")
    out = out.replace("$$", "").replace("$", "")
    out = out.replace("\\cdot", "×").replace("\\times", "×").replace("\\pm", "±")
    out = out.replace("\\approx", "≈").replace("\\ge", "≥").replace("\\le", "≤").replace("\\neq", "≠")
    out = out.replace("\\sum", "Σ").replace("\\Sigma", "Σ")
    out = out.replace(Regex("\\\\left|\\\\right"), "")
    out = out.replace(Regex("\\\\(text|mathrm|mathit)\\{([^{}]*)}")) { it.groupValues[2] }
    out = out.replace(Regex("\\\\\\\\s*"), "\n")
    out = out.replace(Regex("\\\\(dfrac|tfrac)\\s*\\{([^{}]+)}\\s*\\{([^{}]+)}")) { match ->
        "${match.groupValues[2]}/${match.groupValues[3]}"
    }
    out = out.replace(Regex("\\\\frac\\s*\\{([^{}]+)}\\s*\\{([^{}]+)}")) { match ->
        "${match.groupValues[1]}/${match.groupValues[2]}"
    }
    out = out.replace("\\_", "_").replace("\\%", "%")
    out = out.replace("\\,", " ").replace("\\;", " ").replace("\\:", " ")
    return out
}

private fun readScriptToken(text: String, start: Int): Pair<String, Int> {
    if (start >= text.length) return "" to start
    if (text[start] == '{') {
        val end = text.indexOf('}', start + 1)
        if (end > start + 1) {
            return text.substring(start + 1, end) to (end + 1)
        }
    }
    var i = start
    while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '.' || text[i] == '%')) {
        i += 1
    }
    if (i == start) return "" to start
    return text.substring(start, i) to i
}
