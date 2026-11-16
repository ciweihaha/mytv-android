package top.yogiczy.mytv.core.data.repositories.git.parser

import top.yogiczy.mytv.core.data.entities.git.GitRelease

/**
 * git发行版解析接口
 */
interface GitReleaseParser {
    /**
     * 是否支持该格式
     */
    fun isSupport(url: String): Boolean

    /**
     * 解析数据
     */
    suspend fun parse(data: String): GitRelease

    companion object {
        // 顺序不能乱：Custom 必须在 GitHub 前面——"raw.githubusercontent.com"
        // 含 "github.com" 子串，GitHub 在前会把自定义源抢走，Custom 解析器
        // 永远走不到（956 轮修法）；Custom 只认 raw.githubusercontent.com，
        // api.github.com 不含它，GitHub 路不受影响
        val instances = listOf(
            CustomGitReleaseParser(),
            GithubGitReleaseParser(),
            GiteeGitReleaseParser(),
            DefaultGitReleaseParser(),
        )
    }
}