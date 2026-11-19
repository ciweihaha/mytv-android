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

    /**
     * 按指定关键词挑附件的解析：preferredAssetKeywords 是附件名里要含的词
     * （如当前风味 original/disguised + 架构 arm64-v8a）。默认实现忽略
     * 关键词、走原有 parse(data)；GitHub 解析器重写它——不按风味挑时，
     * 伪装版（com.chinablue.tv）永远下到原版包，装上去是另一个 app
     * （com.github.mytv.android），自己永远升不了级、还多出一个重复图标。
     */
    suspend fun parse(data: String, preferredAssetKeywords: List<String>): GitRelease =
        parse(data)

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