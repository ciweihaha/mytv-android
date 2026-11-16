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
        // 顺序是按匹配范围从具体到兜底：Default 恒 true 必须放最后。
        // 注意别犯 956 轮的错：Custom 与 GitHub 的子串没有包含关系——
        // "raw.githubusercontent.com" 不含 "github.com"（github 后面
        // 是 usercontent，不是 .com），GitHub 解析器从来抢不走自定义
        // 源，Custom 本来就能走到；956 轮把 Custom 提到前面是无用改
        // 动，957 轮已调回原顺序
        val instances = listOf(
            GithubGitReleaseParser(),
            GiteeGitReleaseParser(),
            CustomGitReleaseParser(),
            DefaultGitReleaseParser(),
        )
    }
}