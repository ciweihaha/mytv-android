package top.yogiczy.mytv.core.data.repositories.git.parser

import kotlinx.serialization.json.JsonArray
import top.yogiczy.mytv.core.data.entities.git.GitRelease
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.data.utils.Globals

/**
 * github发行版解析
 */
class GithubGitReleaseParser : GitReleaseParser {
    override fun isSupport(url: String): Boolean {
        return url.contains("github.com")
    }

    override suspend fun parse(data: String): GitRelease =
        parse(data, listOf("original", "arm64-v8a"))

    override suspend fun parse(data: String, preferredAssetKeywords: List<String>): GitRelease {
        val jsonElement = Globals.json.parseToJsonElement(data)
        // 判断 JSON 是数组还是对象
        val json = if (jsonElement is JsonArray) {
            if (jsonElement.isEmpty()) {
                throw IllegalStateException("发行版数据不完整：没有找到任何发行版")
            }
            jsonElement[0].requiredObject()
        } else {
            // 如果是对象，直接使用
            jsonElement.requiredObject()
        }
        // 附件按上传顺序排列，不保证固定位置：按文件名挑当前风味的 arm64 专版 APK，
        // 而不是全架构包（全架构 48.5MB，arm64 专版 16.9MB；用户电视就是
        // arm64，全架构包里其它架构的 .so 纯属浪费流量和下载时间）。
        // 没 arm64 专版时 pickDownloadUrl 的退路会落到只含风味关键词的包。
        // 关键词不传时默认原版（老行为不变）；伪装版传 disguised，下的才是
        // 伪装包——下原版包回去装不上自己（包名不同），更新永远失败。
        val keywords = preferredAssetKeywords.ifEmpty { listOf("original", "arm64-v8a") }
        val (assetName, assetUrl) = json.requiredArray("assets").pickDownloadAsset(*keywords.toTypedArray())
        val downloadUrl = Constants.GITHUB_PROXY + assetUrl
        return GitRelease(
            version = json.requiredString("tag_name").stripVersionPrefix(),
            downloadUrl = downloadUrl,
            description = "伪装版本请到Github项目主页手动更新\n" + json.optionalString("body"),
            internalVersion = assetName.extractInternalVersion(),
        )
    }
}
