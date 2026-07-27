package com.plankton.one102.data.update

import com.plankton.one102.data.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

const val GITHUB_RELEASE_REPOSITORY = "yuelangmanle/plankton"

data class GitHubReleaseInfo(
    val tagName: String,
    val name: String,
    val notes: String,
    val releasePageUrl: String,
    val publishedAt: String?,
    val apkDownloadUrl: String?,
    val apkName: String?,
    val apkSizeBytes: Long?,
)

sealed interface UpdateCheckResult {
    data class Found(val release: GitHubReleaseInfo, val newer: Boolean) : UpdateCheckResult
    data class Unavailable(val message: String) : UpdateCheckResult
}

fun updateCheckStatusMessage(result: UpdateCheckResult, currentVersion: String): String = when (result) {
    is UpdateCheckResult.Found -> {
        if (result.newer) "发现新版本：${result.release.tagName}"
        else "当前已是最新版本（$currentVersion）"
    }
    is UpdateCheckResult.Unavailable -> result.message
}

class GitHubUpdateChecker(
    private val repository: String = GITHUB_RELEASE_REPOSITORY,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun check(currentVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repository/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Plankton-Android-Update-Check")
            .get()
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                when {
                    response.code == 404 -> UpdateCheckResult.Unavailable("GitHub 尚未发布可下载版本")
                    !response.isSuccessful -> UpdateCheckResult.Unavailable("检查更新失败：${response.code} ${response.message}")
                    else -> parseGitHubRelease(raw)?.let { release ->
                        UpdateCheckResult.Found(release, isReleaseNewer(release.tagName, currentVersion))
                    } ?: UpdateCheckResult.Unavailable("GitHub 发布信息格式不完整")
                }
            }
        }.getOrElse { error ->
            UpdateCheckResult.Unavailable("检查更新失败：${error.message ?: error.javaClass.simpleName}")
        }
    }
}

fun parseGitHubRelease(raw: String): GitHubReleaseInfo? {
    val root = runCatching { AppJson.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
    val tag = root.string("tag_name") ?: return null
    val assets = root["assets"] as? JsonArray
    val apk = assets
        ?.mapNotNull { it as? JsonObject }
        ?.firstOrNull { it.string("name")?.endsWith(".apk", ignoreCase = true) == true }

    return GitHubReleaseInfo(
        tagName = tag,
        name = root.string("name") ?: tag,
        notes = root.string("body").orEmpty(),
        releasePageUrl = root.string("html_url").orEmpty(),
        publishedAt = root.string("published_at"),
        apkDownloadUrl = apk?.string("browser_download_url"),
        apkName = apk?.string("name"),
        apkSizeBytes = apk?.long("size"),
    )
}

fun isReleaseNewer(releaseTag: String, currentVersion: String): Boolean {
    val release = parseVersion(releaseTag) ?: return false
    val current = parseVersion(currentVersion) ?: return false
    val max = maxOf(release.size, current.size)
    return (0 until max).firstOrNull { index ->
        release.getOrElse(index) { 0 } != current.getOrElse(index) { 0 }
    }?.let { index -> release.getOrElse(index) { 0 } > current.getOrElse(index) { 0 } } ?: false
}

private fun parseVersion(raw: String): List<Int>? {
    val text = raw.trim().removePrefix("v").removePrefix("V")
    if (!Regex("\\d+(?:\\.\\d+){1,3}").matches(text)) return null
    return text.split('.').map(String::toIntOrNull).takeIf { values -> values.all { it != null } }?.filterNotNull()
}

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonObject.long(name: String): Long? = (this[name] as? JsonPrimitive)?.longOrNull
