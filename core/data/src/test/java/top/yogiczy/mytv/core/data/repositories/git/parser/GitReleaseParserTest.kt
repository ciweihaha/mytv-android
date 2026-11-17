package top.yogiczy.mytv.core.data.repositories.git.parser

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.data.utils.Globals

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
    fun github_picksOriginalArm64Apk() = runBlocking {
        // 改拿 arm64 专版（16.9MB）而不是全架构包（48.5MB）：用户电视就是
        // arm64，全架构包里其它架构的 .so 纯属浪费流量和下载时间
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
        assertEquals("${Constants.GITHUB_PROXY}https://example.com/arm64.apk", release.downloadUrl)
    }

    @Test
    fun github_picksOriginalArm64RegardlessOfAssetOrder() = runBlocking {
        val data = githubPayload(
            "V2.2.0.219",
            listOf(
                asset("mytv-android-tv-2.2.0.219-arm64-v8a-sdk23-original.apk", "https://example.com/arm64.apk"),
                asset("mytv-android-tv-2.2.0.219-all-sdk23-original.apk", "https://example.com/original.apk"),
                asset("mytv-android-tv-2.2.0.219-all-sdk23-disguised.apk", "https://example.com/disguised.apk"),
            ).joinToString(","),
        )
        val release = githubParser.parse(data)
        assertEquals("${Constants.GITHUB_PROXY}https://example.com/arm64.apk", release.downloadUrl)
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
        // 没有 arm64 专版：第一级退路（original＋arm64-v8a）落空，退到
        // 只含 original 的全架构包（pickDownloadUrl 的第二级退路）
        val data = githubPayload(
            "V2.2.0.219",
            listOf(
                asset("mytv-android-tv-2.2.0.219-all-sdk23-disguised.apk", "https://example.com/disguised.apk"),
                asset("mytv-android-tv-2.2.0.219-all-sdk23-original.apk", "https://example.com/original.apk"),
            ).joinToString(","),
        )
        val release = githubParser.parse(data)
        assertEquals("${Constants.GITHUB_PROXY}https://example.com/original.apk", release.downloadUrl)
    }

    @Test
    fun github_fallsBackToAnyApk() = runBlocking {
        // 没有含 original 的 APK：第一、二级退路都落空，退到任意一个 APK（取第一个）
        //（598 轮纠正的四级退路之第三级；gitee/custom 的 wellFormed 用例只锁了
        // 共用函数，GitHub 这条路还没单测覆盖）
        val data = githubPayload(
            "V2.2.0.219",
            listOf(
                asset("mytv-android-tv-2.2.0.219-all-sdk23-disguised.apk", "https://example.com/disguised.apk"),
                asset("mytv-android-tv-2.2.0.219-arm64-v8a-sdk23.apk", "https://example.com/arm64.apk"),
            ).joinToString(","),
        )
        val release = githubParser.parse(data)
        assertEquals("${Constants.GITHUB_PROXY}https://example.com/disguised.apk", release.downloadUrl)
    }

    @Test
    fun github_anyApkBeatsEarlierNonApkAttachment() = runBlocking {
        // 有 APK 但排在非 APK 附件后面、关键词都不匹配：第三级退路
        // （任意 APK）优先于第四级（第一个附件），不能取到排在前面的
        // release-notes.txt；现有 fallsBackToAnyApk 两个附件都是 APK，
        // 证明不了“非 APK 不会抢先”，第三、四级顺序写反了也测不出来
        val data = githubPayload(
            "V2.2.0.219",
            listOf(
                asset("release-notes.txt", "https://example.com/notes.txt"),
                asset("mytv-android-tv-2.2.0.219-arm64-v8a-sdk23.apk", "https://example.com/arm64.apk"),
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

    @Test
    fun github_firstElementNotObjectThrowsClearError() {
        // 数组首元素不是对象：requiredObject 应报明确原因，而不是 ClassCastException
        assertThrows(IllegalStateException::class.java) {
            runBlocking { githubParser.parse("""["not-an-object"]""") }
        }
    }

    @Test
    fun gitee_topLevelArrayThrowsClearError() {
        // 顶层是数组不是对象：requiredObject 应报明确原因
        assertThrows(IllegalStateException::class.java) {
            runBlocking { giteeParser.parse("[]") }
        }
    }

    @Test
    fun github_missingAssetsKeyThrowsClearError() {
        // 根本没有 assets 字段：requiredArray 应报缺字段，而不是空附件的错
        val data = """[{"tag_name": "V2.2.0.219", "body": "notes"}]"""
        assertThrows(IllegalStateException::class.java) {
            runBlocking { githubParser.parse(data) }
        }
    }

    @Test
    fun github_blankTagThrowsClearError() {
        // tag_name 是空串：requiredString 把空值当缺失处理
        val data = githubPayload(
            "",
            asset("mytv.apk", "https://example.com/mytv.apk"),
        )
        assertThrows(IllegalStateException::class.java) {
            runBlocking { githubParser.parse(data) }
        }
    }

    @Test
    fun gitee_missingBodyDefaultsToEmptyDescription() = runBlocking {
        // 没有 body 字段：optionalString 应回默认空串，说明就是空
        val data = """{"tag_name": "V1.1.1.135", "assets": [${asset("mytv.apk", "https://example.com/mytv.apk")}]}"""
        val release = giteeParser.parse(data)
        assertEquals("", release.description)
    }

    @Test
    fun pickDownloadUrl_noKeywordsFallsBackToAnyApk() {
        // 不传关键词：preferred 退路不存在，直接取第一个 APK
        val assets = Globals.json.parseToJsonElement(
            """[${asset("app-arm64.apk", "https://example.com/arm64.apk")}, ${asset("app-all.apk", "https://example.com/all.apk")}]""",
        ) as JsonArray
        assertEquals("https://example.com/arm64.apk", assets.pickDownloadUrl())
    }

    @Test
    fun pickDownloadUrl_skipsBlankNameAndNonObjectElements() {
        // 名字空白的附件与非对象元素都应跳过，不进候选
        val assets = Globals.json.parseToJsonElement(
            """[{"name": "", "browser_download_url": "https://example.com/blank.apk"}, "junk", ${asset("app.apk", "https://example.com/app.apk")}]""",
        ) as JsonArray
        assertEquals("https://example.com/app.apk", assets.pickDownloadUrl())
    }

    @Test
    fun pickDownloadUrl_caseInsensitiveSuffixAndKeywords() {
        // 后缀与关键词都不区分大小写：上游附件名大小写不保证，大写 .APK
        // 或混写 Original/ALL 也要挑中原版全架构包；这档之前一次也没
        // 断言过，ignoreCase 写漏时会把原版包漏掉、退到 arm64 专版
        val assets = Globals.json.parseToJsonElement(
            """[${asset("mytv-arm64.APK", "https://example.com/arm64.apk")}, ${asset("MyTV-ALL-ORIGINAL.APK", "https://example.com/original.apk")}]""",
        ) as JsonArray
        assertEquals("https://example.com/original.apk", assets.pickDownloadUrl("original", "all"))
        assertEquals("https://example.com/original.apk", assets.pickDownloadUrl("ORIGINAL", "ALL"))
    }

    @Test
    fun github_assetsNotArrayThrowsClearError() {
        // assets 字段存在但不是数组：requiredArray 应报缺字段，而不是
        // 拿字符串当数组用时抛类型转换错
        val data = """[{"tag_name": "V2.2.0.219", "body": "notes", "assets": "not-an-array"}]"""
        assertThrows(IllegalStateException::class.java) {
            runBlocking { githubParser.parse(data) }
        }
    }

    @Test
    fun defaultUpdateUrl_pointsToThisForkAndSelectsGithubParser() {
        // 默认更新源地址是写死在常量里的：三档（stable/beta/dev）全指本 fork
        // 的 releases/latest。写成上游 yaoxieyoulei/mytv-android 时查更新
        // 永远报无更新（上游已归档、最新还是 2025 年的版本）；写错仓库名时
        // GitHub 解析器照样认 "github.com"、照样能解析，错得悄无声息——
        // 地址本身必须钉死，不能只靠"含 github.com 走 GitHub"那档
        val expected = "https://api.github.com/repos/ciweihaha/mytv-android/releases/latest"
        for (channel in listOf("stable", "beta", "dev")) {
            val url = Constants.GIT_RELEASE_LATEST_URL[channel]
            assertEquals(expected, url)
            assertTrue(
                GitReleaseParser.instances.first { it.isSupport(url!!) } is GithubGitReleaseParser
            )
        }
    }

    @Test
    fun dispatch_picksGiteeCustomAndDefaultParsersInOrder() {
        // 分发点只断了 GitHub 一档：Gitee、Custom、Default 三路只在仓库层
        // 端到端走过，没在分发点上断过。顺序本身是契约的一部分（956 轮
        // 教训）：Default 恒 true 必须放最后，挪到前面时这三档立刻红。
        // Custom 认 "raw.githubusercontent.com"、不含 "github.com"，GitHub
        // 解析器抢不走它（957 轮已证实），这档也顺带钉死这一点
        assertTrue(
            GitReleaseParser.instances.first {
                it.isSupport("https://gitee.com/api/v5/repos/ciweihaha/mytv-android/releases/latest")
            } is GiteeGitReleaseParser
        )
        assertTrue(
            GitReleaseParser.instances.first {
                it.isSupport("https://raw.githubusercontent.com/ciweihaha/mytv-android/main/release.json")
            } is CustomGitReleaseParser
        )
        assertTrue(
            GitReleaseParser.instances.first {
                it.isSupport("https://example.com/releases/latest")
            } is DefaultGitReleaseParser
        )
    }
}
