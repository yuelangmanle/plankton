package com.plankton.one102.ui

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ProjectDocEntry(val id: String, val title: String)

private data class ProjectDoc(val id: String, val assetPath: String, val title: String)

private val PROJECT_DOCS = listOf(
    ProjectDoc("project", "docs/项目书.enc", "项目书"),
    ProjectDoc("native", "docs/原生安卓项目书.enc", "原生安卓项目书"),
    ProjectDoc("status", "docs/当前程序情况.enc", "当前程序情况"),
    ProjectDoc("voice", "docs/语音助手项目书.enc", "语音助手项目书"),
    ProjectDoc("prompt", "docs/新窗口提示词.enc", "新窗口提示词"),
)

private val HIDDEN_DOC_IDS = setOf("prompt")

private fun isDocHidden(doc: ProjectDoc): Boolean {
    return doc.id in HIDDEN_DOC_IDS || doc.title.contains("新窗口提示词")
}

private fun visibleDocs(): List<ProjectDoc> = PROJECT_DOCS.filterNot(::isDocHidden)

private fun clipTextBlock(text: String, maxChars: Int): String {
    val clean = text.trimEnd()
    if (clean.length <= maxChars) return clean
    val clipped = clean.take(maxChars).trimEnd()
    val omitted = clean.length - clipped.length
    return "$clipped\n（已省略约 $omitted 字）"
}

private fun sanitizeProjectDoc(id: String, raw: String): String {
    if (raw.isBlank()) return raw
    if (id != "project") return raw
    return raw.replace(Regex("AI\\s*/\\s*开发者"), "开发者")
}

private suspend fun readAsset(context: Context, path: String, password: String): String? = withContext(Dispatchers.IO) {
    readEncryptedAsset(context, path, password)
}

fun listProjectDocs(): List<ProjectDocEntry> {
    return visibleDocs().map { ProjectDocEntry(id = it.id, title = it.title) }
}

suspend fun readProjectDoc(context: Context, id: String, maxChars: Int = 120000): String? {
    val doc = PROJECT_DOCS.firstOrNull { it.id == id && !isDocHidden(it) } ?: return null
    val password = DocAccessCache.password ?: return null
    val content = readAsset(context, doc.assetPath, password) ?: return null
    val sanitized = sanitizeProjectDoc(doc.id, content)
    return clipTextBlock(sanitized, maxChars)
}

fun findProjectDocTitle(id: String): String? {
    return visibleDocs().firstOrNull { it.id == id }?.title
}

suspend fun buildProjectDocContext(context: Context, maxChars: Int = 8000): String {
    val password = DocAccessCache.password ?: return ""
    val sb = StringBuilder()
    for (doc in visibleDocs()) {
        val content = sanitizeProjectDoc(doc.id, readAsset(context, doc.assetPath, password)?.trim().orEmpty())
        if (content.isBlank()) continue
        sb.appendLine("【${doc.title}】")
        sb.appendLine(content)
        sb.appendLine()
    }
    val text = sb.toString().trim()
    if (text.isBlank()) return ""
    return clipTextBlock(text, maxChars)
}

fun buildChangelogContext(maxLines: Int = 120): String {
    val lines = AppInfo.changeLog.filter { it.isNotBlank() }.take(maxLines)
    if (lines.isEmpty()) return ""
    val versions = AppInfo.changeLog.map { it.trim() }.filter { it.matches(Regex("^\\d")) }
    val header = if (versions.isEmpty()) {
        "迭代记录：暂无"
    } else {
        "迭代记录：共 ${versions.size} 次；最新版本：${versions.first()}"
    }
    return buildString {
        appendLine(header)
        for (line in lines) {
            appendLine(line)
        }
    }.trimEnd()
}
