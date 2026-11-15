package top.yogiczy.mytv.core.data.repositories.git

import kotlinx.coroutines.CancellationException
import top.yogiczy.mytv.core.data.entities.git.GitRelease
import top.yogiczy.mytv.core.data.network.HttpStatusException
import top.yogiczy.mytv.core.data.network.requestStrict
import top.yogiczy.mytv.core.data.repositories.git.parser.GitReleaseParser
import top.yogiczy.mytv.core.data.utils.Loggable

/**
 * git数据获取
 */
class GitRepository : Loggable("GitRepository") {

    /**
     * 获取最新发行版
     */
    suspend fun latestRelease(url: String): GitRelease {
        log.d("获取最新发行版: $url")

        try {
            val parser = GitReleaseParser.instances.first { it.isSupport(url) }
            return url.requestStrict { body -> parser.parse(body.string()) }
                ?: throw IllegalStateException("发行版接口返回空数据")
        } catch (ex: Exception) {
            log.e("获取最新发行版失败", ex)
            // 取消信号原样抛出去：CancellationException 本身就是 IllegalStateException
            // 的子类，这行守卫必须放在下面那行前面，否则会被包成普通失败
            // （690 轮同款问题：更新检查中途被取消会记成一次检查失败）
            if (ex is CancellationException) throw ex
            // 状态码错是服务端/地址问题，不是网络问题：原样抛，
            // 而不是一律报“请检查网络连接”
            if (ex is HttpStatusException) throw ex
            // 解析失败是数据问题，不是网络问题：把明确原因透传给界面，
            // 而不是一律报“请检查网络连接”
            if (ex is IllegalStateException) throw Exception(ex.message, ex)
            throw Exception("获取最新发行版失败，请检查网络连接", ex)
        }
    }
}