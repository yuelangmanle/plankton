package com.voiceassistant.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

const val VOICE_GITHUB_RELEASE_REPOSITORY = "yuelangmanle/plankton"

data class VoiceGitHubReleaseInfo(
    val tagName: String,
    val name: String,
    val notes: String,
    val releasePageUrl: String,
    val publishedAt: String?,
    val apkDownloadUrl: String?,
    val apkName: String?,
    val apkSizeBytes: Long?,
)

sealed interface VoiceUpdateCheckResult {
    data class Found(val release: VoiceGitHubReleaseInfo, val newer: Boolean) : VoiceUpdateCheckResult
    data class Unavailable(val message: String) : VoiceUpdateCheckResult
}

fun voiceUpdateCheckStatusMessage(result: VoiceUpdateCheckResult, currentVersion: String): String = when (result) {
    is VoiceUpdateCheckResult.Found -> {
        if (result.newer) "发现新版本：${result.release.tagName}"
        else "当前已是最新版本（$currentVersion）"
    }
    is VoiceUpdateCheckResult.Unavailable -> result.message
}

class VoiceGitHubUpdateChecker(
    private val repository: String = VOICE_GITHUB_RELEASE_REPOSITORY,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun check(currentVersion: String): VoiceUpdateCheckResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repository/releases?per_page=100")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Plankton-Voice-Assistant-Update-Check")
            .get()
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                when {
                    response.code == 404 -> VoiceUpdateCheckResult.Unavailable("GitHub 尚未发布语音助手安装包")
                    !response.isSuccessful -> VoiceUpdateCheckResult.Unavailable("检查更新失败：${response.code} ${response.message}")
                    else -> parseLatestVoiceGitHubRelease(raw)?.let { release ->
                        VoiceUpdateCheckResult.Found(release, isVoiceReleaseNewer(release.tagName, currentVersion))
                    } ?: VoiceUpdateCheckResult.Unavailable("GitHub 尚未发布可安装的语音助手 APK")
                }
            }
        }.getOrElse { error ->
            VoiceUpdateCheckResult.Unavailable("检查更新失败：${error.message ?: error.javaClass.simpleName}")
        }
    }
}

fun parseLatestVoiceGitHubRelease(raw: String): VoiceGitHubReleaseInfo? {
    val releases = runCatching { VoiceUpdateJson.parseToJsonElement(raw) as? JsonArray }.getOrNull() ?: return null
    return releases
        .mapNotNull { it as? JsonObject }
        .mapNotNull(::parseVoiceGitHubRelease)
        .maxWithOrNull { first, second ->
            compareVersionParts(
                requireNotNull(voiceVersionParts(first.tagName)),
                requireNotNull(voiceVersionParts(second.tagName)),
            )
        }
}

fun isVoiceReleaseNewer(releaseTag: String, currentVersion: String): Boolean {
    val release = voiceVersionParts(releaseTag) ?: return false
    val current = plainVersionParts(currentVersion) ?: return false
    val max = maxOf(release.size, current.size)
    return (0 until max).firstOrNull { index ->
        release.getOrElse(index) { 0 } != current.getOrElse(index) { 0 }
    }?.let { index -> release.getOrElse(index) { 0 } > current.getOrElse(index) { 0 } } ?: false
}

private fun parseVoiceGitHubRelease(root: JsonObject): VoiceGitHubReleaseInfo? {
    if (root.boolean("prerelease") == true || root.boolean("draft") == true) return null
    val tag = root.string("tag_name") ?: return null
    if (voiceVersionParts(tag) == null) return null
    val assets = root["assets"] as? JsonArray
    val apk = assets
        ?.mapNotNull { it as? JsonObject }
        ?.firstOrNull { it.string("name")?.endsWith(".apk", ignoreCase = true) == true }
        ?: return null

    return VoiceGitHubReleaseInfo(
        tagName = tag,
        name = root.string("name") ?: tag,
        notes = root.string("body").orEmpty(),
        releasePageUrl = root.string("html_url").orEmpty(),
        publishedAt = root.string("published_at"),
        apkDownloadUrl = apk.string("browser_download_url"),
        apkName = apk.string("name"),
        apkSizeBytes = apk.long("size"),
    )
}

private fun voiceVersionParts(raw: String): List<Int>? = parseVersion(raw.trim(), "voice-v")

private fun plainVersionParts(raw: String): List<Int>? = parseVersion(raw.trim(), "")

private fun parseVersion(raw: String, requiredPrefix: String): List<Int>? {
    val text = if (requiredPrefix.isBlank()) raw.removePrefix("v").removePrefix("V") else raw
    if (requiredPrefix.isNotBlank() && !text.startsWith(requiredPrefix, ignoreCase = true)) return null
    val version = if (requiredPrefix.isBlank()) text else text.substring(requiredPrefix.length)
    if (!Regex("\\d+(?:\\.\\d+){1,3}").matches(version)) return null
    return version.split('.').map(String::toIntOrNull).takeIf { values -> values.all { it != null } }?.filterNotNull()
}

private fun compareVersionParts(first: List<Int>, second: List<Int>): Int {
    val max = maxOf(first.size, second.size)
    for (index in 0 until max) {
        val comparison = first.getOrElse(index) { 0 }.compareTo(second.getOrElse(index) { 0 })
        if (comparison != 0) return comparison
    }
    return 0
}

private val VoiceUpdateJson = Json { ignoreUnknownKeys = true }

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonObject.long(name: String): Long? = (this[name] as? JsonPrimitive)?.longOrNull

private fun JsonObject.boolean(name: String): Boolean? = (this[name] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
