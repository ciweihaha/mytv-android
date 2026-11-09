package top.yogiczy.mytv.core.data.repositories.git.parser

import top.yogiczy.mytv.core.data.entities.git.GitRelease
import top.yogiczy.mytv.core.data.utils.Globals

/**
 * gitee发行版解析
 */
class GiteeGitReleaseParser : GitReleaseParser {
    override fun isSupport(url: String): Boolean {
        return url.contains("gitee.com")
    }

    override suspend fun parse(data: String): GitRelease {
        val json = Globals.json.parseToJsonElement(data).requiredObject()

        return GitRelease(
            version = json.requiredString("tag_name").stripVersionPrefix(),
            downloadUrl = json.requiredArray("assets").downloadUrlAt(0),
            description = json.optionalString("body"),
        )
    }
}
