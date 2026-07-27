package com.voiceassistant.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceGitHubUpdateCheckerTest {
    @Test
    fun semanticVersionComparisonHandlesDoubleDigitMinorVersions() {
        assertTrue(isVoiceReleaseNewer("voice-v3.10", "3.9"))
        assertTrue(isVoiceReleaseNewer("voice-v3.7.1", "3.7"))
        assertFalse(isVoiceReleaseNewer("voice-v3.6", "3.7"))
        assertFalse(isVoiceReleaseNewer("v7.2", "3.7"))
    }

    @Test
    fun parserIgnoresMainAppReleasesAndUsesNewestVoiceApk() {
        val release = parseLatestVoiceGitHubRelease(
            """
            [
              {
                "tag_name":"v7.2",
                "name":"浮游动物一体化 v7.2",
                "assets":[{"name":"plankton-v7.2.apk","browser_download_url":"https://example.test/main.apk","size":1}]
              },
              {
                "tag_name":"voice-v3.10",
                "name":"语音助手 v3.10",
                "body":"更新检查",
                "html_url":"https://example.test/voice-v3.10",
                "assets":[{"name":"voice-v3.10.apk","browser_download_url":"https://example.test/voice.apk","size":1234}]
              }
            ]
            """.trimIndent(),
        )

        requireNotNull(release)
        assertEquals("voice-v3.10", release.tagName)
        assertEquals("https://example.test/voice.apk", release.apkDownloadUrl)
        assertEquals("更新检查", release.notes)
    }

    @Test
    fun parserRejectsVoiceReleaseWithoutApkAsset() {
        val release = parseLatestVoiceGitHubRelease(
            """
            [{
              "tag_name":"voice-v3.7",
              "name":"语音助手 v3.7",
              "assets":[{"name":"release-notes.txt","browser_download_url":"https://example.test/notes","size":1}]
            }]
            """.trimIndent(),
        )

        assertNull(release)
    }

    @Test
    fun parserIgnoresNewerPrereleaseAndUsesNewestPublicRelease() {
        val release = parseLatestVoiceGitHubRelease(
            """
            [
              {
                "tag_name":"voice-v3.8",
                "prerelease":true,
                "assets":[{"name":"voice-v3.8.apk","browser_download_url":"https://example.test/preview.apk"}]
              },
              {
                "tag_name":"voice-v3.7",
                "prerelease":false,
                "assets":[{"name":"voice-v3.7.apk","browser_download_url":"https://example.test/release.apk"}]
              }
            ]
            """.trimIndent(),
        )

        requireNotNull(release)
        assertEquals("voice-v3.7", release.tagName)
    }

    @Test
    fun latestVoiceReleaseProducesVisibleStatusMessage() {
        val release = VoiceGitHubReleaseInfo(
            tagName = "voice-v3.7",
            name = "语音助手 v3.7",
            notes = "",
            releasePageUrl = "https://example.test/voice-v3.7",
            publishedAt = null,
            apkDownloadUrl = "https://example.test/voice.apk",
            apkName = "voice-v3.7.apk",
            apkSizeBytes = 1,
        )

        assertEquals(
            "当前已是最新版本（3.7）",
            voiceUpdateCheckStatusMessage(VoiceUpdateCheckResult.Found(release, newer = false), "3.7"),
        )
    }
}
