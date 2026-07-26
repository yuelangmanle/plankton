package com.plankton.one102.data.update

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
}
