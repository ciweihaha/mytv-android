package top.yogiczy.mytv.core.data.repositories.git.parser

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import top.yogiczy.mytv.core.data.utils.Constants

/**
 * 发行版解析器测试：附件顺序 / 附件缺失 / 字段缺失 / tag 前缀。
 * 旧实现用写死下标（github [1]、gitee/custom [0]）+ `!!` + substring(1)，
 * 遇到这些输入会直接抛 IndexOutOfBoundsException / NPE，或把版本号截错。
 */
class GitReleaseParserTest {
    private val githubParser = GithubGitReleaseParser()
    private val giteeParser = GiteeGitReleaseParser()
    private val customParser = CustomGitReleaseParser()

    private fun asset(name: String, url: String): String {
        return """{"name": "$name", "browser_download_url": "$url"}"""
    }

    private fun githubPayload(tagName: String, assets: String, body: String = "notes"): String {
        return """[{"tag_name": "$tagName", "body": "$body", "assets": [$assets]}]"""
    }

    private fun singlePayload(tagName: String, assets: String, body: String = "notes"): String {
        return """{"tag_name": "$tagName", "body": "$body", "assets": [$assets]}"""
    }

    @Test
    fun github_picksOriginalAllAbiApk() = runBlocking {
        val data = githubPayload(
            "V2.2.0.219",
            listOf(
                asset("mytv-android-tv-2.2.0.219-all-sdk23-disguised.apk", "https://example.com/disguised.apk"),
                asset("mytv-android-tv-2.2.0.219-all-sdk23-original.apk", "https://example.com/original.apk"),
                asset("mytv-android-tv-2.2.0.219-arm64-v8a-sdk23-original.apk", "https://example.com/arm64.apk"),
            ).joinToString(","),
        )
        val release = githubParser.parse(data)
        assertEquals("2.2.0.219", release.version)
        assertEquals("${Constants.GITHUB_PROXY}https://example.com/original.apk", release.downloadUrl)
    }

    @Test
    fun github_picksOriginalRegardlessOfAssetOrder() = runBlocking {
        val data = githubPayload(
            "V2.2.0.219",
            listOf(
                asset("mytv-android-tv-2.2.0.219-arm64-v8a-sdk23-original.apk", "https://example.com/arm64.apk"),
                asset("mytv-android-tv-2.2.0.219-all-sdk23-original.apk", "https://example.com/original.apk"),
                asset("mytv-android-tv-2.2.0.219-all-sdk23-disguised.apk", "https://example.com/disguised.apk"),
            ).joinToString(","),
        )
        val release = githubParser.parse(data)
        assertEquals("${Constants.GITHUB_PROXY}https://example.com/original.apk", release.downloadUrl)
    }

    @Test
    fun github_singleAssetReleaseParses() = runBlocking {
        // 只有一个附件的发行版：旧代码取 assets[1] 直接越界
        val data = githubPayload(
            "V2.2.0.219",
            asset("mytv-android-tv-2.2.0.219-all-sdk23-original.apk", "https://example.com/original.apk"),
        )
        val release = githubParser.parse(data)
        assertEquals("2.2.0.219", release.version)
        assertEquals("${Constants.GITHUB_PROXY}https://example.com/original.apk", release.downloadUrl)
    }

    @Test
    fun github_fallsBackToOriginalOnlyApk() = runBlocking {
        // 没有同时含 original＋all 的附件：第一级退路落空，退到只含 original 的 APK
        //（598 轮纠正的四级退路之第二级，此前无单测覆盖）
        val data = githubPayload(
            "V2.2.0.219",
            listOf(
                asset("mytv-android-tv-2.2.0.219-all-sdk23-disguised.apk", "https://example.com/disguised.apk"),
                asset("mytv-android-tv-2.2.0.219-arm64-v8a-sdk23-original.apk", "https://example.com/arm64.apk"),
            ).joinToString(","),
        )
        val release = githubParser.parse(data)
        assertEquals("${Constants.GITHUB_PROXY}https://example.com/arm64.apk", release.downloadUrl)
    }

    @Test
    fun github_fallsBackToFirstAttachmentWhenNoApk() = runBlocking {
        // 一个 APK 都没有：前三级退路都落空，退到第一个带下载地址的附件
        //（598 轮纠正的四级退路之第四级，此前无单测覆盖）
        val data = githubPayload(
            "V2.2.0.219",
            listOf(
                asset("release-notes.txt", "https://example.com/notes.txt"),
                asset("mytv-android-tv-2.2.0.219.zip", "https://example.com/app.zip"),
            ).joinToString(","),
        )
        val release = githubParser.parse(data)
        assertEquals("${Constants.GITHUB_PROXY}https://example.com/notes.txt", release.downloadUrl)
    }

    @Test
    fun github_emptyReleaseArrayThrowsClearError() {
        assertThrows(IllegalStateException::class.java) {
            runBlocking { githubParser.parse("[]") }
        }
    }

    @Test
    fun github_noAssetsThrowsClearError() {
        val data = githubPayload("V2.2.0.219", "")
        assertThrows(IllegalStateException::class.java) {
            runBlocking { githubParser.parse(data) }
        }
    }

    @Test
    fun github_tagWithoutPrefixKeptIntact() = runBlocking {
        // tag 没有 v 前缀：旧代码 substring(1) 会把版本号首字符截掉
        val data = githubPayload(
            "2.2.0.219",
            asset("mytv-android-tv-2.2.0.219-all-sdk23-original.apk", "https://example.com/original.apk"),
        )
        val release = githubParser.parse(data)
        assertEquals("2.2.0.219", release.version)
    }

    @Test
    fun github_assetWithoutDownloadUrlThrowsClearError() {
        val data = githubPayload(
            "V2.2.0.219",
            """{"name": "mytv-android-tv-2.2.0.219-all-sdk23-original.apk"}""",
        )
        assertThrows(IllegalStateException::class.java) {
            runBlocking { githubParser.parse(data) }
        }
    }

    @Test
    fun gitee_wellFormedParses() = runBlocking {
        val data = singlePayload(
            "V1.1.1.135",
            asset("mytv.apk", "https://example.com/mytv.apk"),
        )
        val release = giteeParser.parse(data)
        assertEquals("1.1.1.135", release.version)
        assertEquals("https://example.com/mytv.apk", release.downloadUrl)
        assertEquals("notes", release.description)
    }

    @Test
    fun gitee_emptyAssetsThrowsClearError() {
        val data = singlePayload("V1.1.1.135", "")
        assertThrows(IllegalStateException::class.java) {
            runBlocking { giteeParser.parse(data) }
        }
    }

    @Test
    fun gitee_picksOriginalAllAbiApkRegardlessOfOrder() = runBlocking {
        // 和 GitHub 解析保持一致：附件顺序不保证，写死 [0] 会取到
        // arm64 专版或伪装版；全架构原版即使排在后面也要挑出来
        val data = singlePayload(
            "V2.2.0.219",
            listOf(
                asset("mytv-android-tv-2.2.0.219-arm64-v8a-sdk23-original.apk", "https://example.com/arm64.apk"),
                asset("mytv-android-tv-2.2.0.219-all-sdk23-disguised.apk", "https://example.com/disguised.apk"),
                asset("mytv-android-tv-2.2.0.219-all-sdk23-original.apk", "https://example.com/original.apk"),
            ).joinToString(","),
        )
        val release = giteeParser.parse(data)
        assertEquals("https://example.com/original.apk", release.downloadUrl)
    }

    @Test
    fun gitee_missingTagThrowsClearError() {
        val data = """{"body": "notes", "assets": [${asset("mytv.apk", "https://example.com/mytv.apk")}]}"""
        assertThrows(IllegalStateException::class.java) {
            runBlocking { giteeParser.parse(data) }
        }
    }

    @Test
    fun custom_wellFormedParses() = runBlocking {
        val data = singlePayload(
            "v1.1.1.135",
            asset("mytv.apk", "https://example.com/mytv.apk"),
        )
        val release = customParser.parse(data)
        assertEquals("1.1.1.135", release.version)
        assertEquals("${Constants.GITHUB_PROXY}https://example.com/mytv.apk", release.downloadUrl)
    }

    @Test
    fun custom_picksOriginalAllAbiApkRegardlessOfOrder() = runBlocking {
        // 和 GitHub 解析保持一致：附件顺序不保证，写死 [0] 会取到
        // arm64 专版或伪装版；全架构原版即使排在后面也要挑出来
        val data = singlePayload(
            "v2.2.0.219",
            listOf(
                asset("mytv-android-tv-2.2.0.219-arm64-v8a-sdk23-original.apk", "https://example.com/arm64.apk"),
                asset("mytv-android-tv-2.2.0.219-all-sdk23-original.apk", "https://example.com/original.apk"),
            ).joinToString(","),
        )
        val release = customParser.parse(data)
        assertEquals("${Constants.GITHUB_PROXY}https://example.com/original.apk", release.downloadUrl)
    }
}
