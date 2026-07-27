package com.plankton.one102.data.update

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseParserTest {
    @Test
    fun semanticVersionComparisonHandlesDoubleDigitMinorVersions() {
        assertTrue(isReleaseNewer("v6.10", "6.9"))
        assertTrue(isReleaseNewer("6.9.1", "6.9"))
        assertFalse(isReleaseNewer("v6.8", "6.9"))
        assertFalse(isReleaseNewer("preview", "6.9"))
    }

    @Test
    fun parserUsesApkAssetAndKeepsReleaseNotes() {
        val release = parseGitHubRelease(
            """
            {
              "tag_name":"v6.10",
              "name":"浮游动物一体化 6.10",
              "body":"修复设置状态",
              "html_url":"https://github.com/yuelangmanle/plankton/releases/tag/v6.10",
              "published_at":"2026-07-27T00:00:00Z",
              "prerelease":false,
              "assets":[
                {"name":"plankton-v6.10.apk","browser_download_url":"https://example.test/app.apk","size":1234}
              ]
            }
            """.trimIndent(),
        )

        requireNotNull(release)
        assertEquals("v6.10", release.tagName)
        assertEquals("https://example.test/app.apk", release.apkDownloadUrl)
        assertEquals("修复设置状态", release.notes)
    }

    @Test
    fun latestReleaseProducesVisibleStatusMessage() {
        val release = GitHubReleaseInfo(
            tagName = "v7.2",
            name = "浮游动物一体化 v7.2",
            notes = "",
            releasePageUrl = "https://example.test/release",
            publishedAt = null,
            apkDownloadUrl = null,
            apkName = null,
            apkSizeBytes = null,
        )

        assertEquals(
            "当前已是最新版本（7.2）",
            updateCheckStatusMessage(UpdateCheckResult.Found(release, newer = false), "7.2"),
        )
    }

    @Test
    fun checkSkipsVoiceAssistantReleaseAndFindsLatestMainAppRelease() = runBlocking {
        val checker = GitHubUpdateChecker(
            httpClient = jsonClient(
                """
                [
                  {"tag_name":"voice-v3.7","name":"语音助手 v3.7","assets":[{"name":"voice.apk","browser_download_url":"https://example.test/voice.apk","size":1}]},
                  {"tag_name":"v7.3","name":"主 App v7.3","assets":[{"name":"plankton.apk","browser_download_url":"https://example.test/main.apk","size":2}]}
                ]
                """.trimIndent(),
            ),
        )

        val result = checker.check("7.2")

        assertTrue(result is UpdateCheckResult.Found)
        result as UpdateCheckResult.Found
        assertEquals("v7.3", result.release.tagName)
        assertTrue(result.newer)
    }

    private fun jsonClient(body: String): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody())
                .build()
        }
        .build()
}
