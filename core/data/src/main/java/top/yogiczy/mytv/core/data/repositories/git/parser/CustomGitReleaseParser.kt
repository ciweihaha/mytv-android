package top.yogiczy.mytv.core.data.repositories.git.parser

import top.yogiczy.mytv.core.data.entities.git.GitRelease
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.data.utils.Globals

/**
 * 自定义发行版解析
 */
class CustomGitReleaseParser : GitReleaseParser {
    override fun isSupport(url: String): Boolean {
        return url.contains("raw.githubusercontent.com")
    }

    override suspend fun parse(data: String): GitRelease {
        val json = Globals.json.parseToJsonElement(data).requiredObject()

        return GitRelease(
            version = json.requiredString("tag_name").stripVersionPrefix(),
            // 和 GitHub 解析保持一致：附件按上传顺序排列，不保证固定位置，
            // 写死 [0] 会取到 arm64 专版或伪装版，全架构原版在后面；
            // 按文件名挑原版全架构 APK，顺序变化也不影响
            downloadUrl = Constants.GITHUB_PROXY +
                json.requiredArray("assets").pickDownloadUrl("original", "all"),
            description = json.optionalString("body"),
        )
    }
}
