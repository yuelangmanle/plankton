package com.plankton.one102.export

import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.ByteArrayOutputStream

object DocxReportExporter {
    data class Section(
        val title: String,
        val body: String,
    )

    fun export(
        title: String,
        subtitleLines: List<String> = emptyList(),
        sections: List<Section>,
    ): ByteArray {
        val doc = XWPFDocument()

        fun addParagraph(text: String, bold: Boolean = false, fontSize: Int? = null, center: Boolean = false) {
            val p = doc.createParagraph()
            p.alignment = if (center) ParagraphAlignment.CENTER else ParagraphAlignment.LEFT
            val r = p.createRun()
            r.isBold = bold
            if (fontSize != null) r.fontSize = fontSize
            r.setText(text)
        }

        fun addMultiline(text: String) {
            val normalized = sanitizePlainText(text)
            for (line in normalized.split("\n")) {
                if (line.isBlank()) {
                    doc.createParagraph()
                } else {
                    addParagraph(line)
                }
            }
        }

        addParagraph(sanitizePlainText(title), bold = true, fontSize = 20, center = true)
        if (subtitleLines.isNotEmpty()) {
            for (line in subtitleLines) {
                val t = sanitizePlainText(line).trim()
                if (t.isNotBlank()) addParagraph(t, fontSize = 11, center = true)
            }
            doc.createParagraph()
        }

        for (s in sections) {
            val heading = sanitizePlainText(s.title).trim()
            if (heading.isNotBlank()) addParagraph(heading, bold = true, fontSize = 14)
            addMultiline(s.body)
            doc.createParagraph()
        }

        val out = ByteArrayOutputStream()
        doc.use { it.write(out) }
        return out.toByteArray()
    }
}

private fun sanitizePlainText(input: String): String {
    if (input.isBlank()) return ""
    val sb = StringBuilder(input.length)
    for (ch in input.replace("\r\n", "\n").replace('\r', '\n')) {
        val code = ch.code
        val keep = when {
            ch == '\n' || ch == '\t' -> true
            code in 0x20..0x10FFFF -> true
            else -> false
        }
        if (keep) sb.append(ch) else sb.append(' ')
    }
    return sb.toString()
        .replace("```", "")
        .trim()
}

