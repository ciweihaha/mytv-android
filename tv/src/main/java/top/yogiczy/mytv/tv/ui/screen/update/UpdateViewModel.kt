package top.yogiczy.mytv.tv.ui.screen.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import top.yogiczy.mytv.core.data.entities.git.GitRelease
import top.yogiczy.mytv.core.data.repositories.git.GitRepository
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.core.util.utils.Downloader
import top.yogiczy.mytv.core.util.utils.compareVersion
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.material.SnackbarType
import java.io.File

class UpdateViewModel(
    debugLatestRelease: GitRelease? = null,
) : ViewModel() {
    private val log = Logger.create("UpdateViewModel")

    private var _isChecking = false
    /** 正在检查更新：手动点“检测更新”时另一次检查还在跑，别把“没查完”说成“已是最新”（877 轮 P3-2）。 */
    val isChecking get() = _isChecking

    private var _isUpdating by mutableStateOf(false)
    val isUpdating get() = _isUpdating

    private var _isUpdateAvailable by mutableStateOf(false)
    val isUpdateAvailable get() = _isUpdateAvailable

    private var _updateDownloaded by mutableStateOf(false)
    val updateDownloaded get() = _updateDownloaded

    /**
     * 上一次检查是否失败：失败时 isUpdateAvailable 仍是 false，
     * 不靠它区分会把“没查到”说成“已是最新”
     */
    private var _checkFailed by mutableStateOf(false)
    val checkFailed get() = _checkFailed

    /**
     * 消费“已下载待安装”标记：安装尝试后即清除，避免下次打开更新页
     * 时对陈旧的 latest.apk 再次自动弹出安装器。
     */
    fun consumeUpdateDownloaded() {
        _updateDownloaded = false
    }

    private var _latestRelease by mutableStateOf(debugLatestRelease ?: GitRelease())
    val latestRelease get() = _latestRelease


    suspend fun checkUpdate(
        currentVersion: String,
        channel: String,
        releaseTag: String = "",
        // 当前风味（original/disguised）：传给发行版解析器挑包，伪装版必须
        // 下伪装包——下原版包回去装的是另一个包名的 app，自己永远升不了级
        flavor: String = "original",
        fetchLatestRelease: suspend (String) -> GitRelease = {
            GitRepository().latestRelease(it, listOf(flavor, "arm64-v8a"))
        },
    ) {
        if (_isChecking) return
        if (_isUpdateAvailable) return

        try {
            log.i("开始检查更新（${channel}）...")

            _checkFailed = false
            _isChecking = true
            val releaseUrl = Constants.GIT_RELEASE_LATEST_URL[channel] ?: return
            _latestRelease = fetchLatestRelease(releaseUrl)
            // 发版 tag（如 V3.0.0.243）与内部版（1.1.1.N）是两套编号：
            // 发版时拿 tag 和线上最新版比，手动触发/调试时 tag 为空、
            // 拿内部版比；直接比内部版会永远报“有新版”。
            // tag 为空时优先拿发行版附件文件名里的内部版（如 1.1.1.17）和
            // 当前内部版比——只有附件名里也没有内部版时才退回拿 tag 版
            // 和内部版比（旧行为，仍会误报，但已无更好信息）。
            _isUpdateAvailable = when {
                releaseTag.isNotBlank() ->
                    _latestRelease.version.compareVersion(releaseTag) > 0

                _latestRelease.internalVersion.isNotBlank() ->
                    _latestRelease.internalVersion.compareVersion(currentVersion) > 0

                else ->
                    _latestRelease.version.compareVersion(currentVersion) > 0
            }

            log.i("最新版本: ${_latestRelease.version}")
        } catch (ex: Exception) {
            // 检查中途被取消不是检查失败：取消信号原样抛出去，别记成一次失败、
            // 别把取消原因写进版本说明里（690 轮同款问题）
            if (ex is CancellationException) throw ex
            log.e("检查更新失败", ex)
            _checkFailed = true
            _latestRelease = _latestRelease.copy(description = ex.message ?: "检查更新失败")
        } finally {
            _isChecking = false
        }
    }

    suspend fun downloadAndUpdate(
        latestFile: File,
        download: suspend (String, String, ((Int) -> Unit)?) -> Unit =
            { url, path, cb -> Downloader.downloadTo(url, path, cb) },
    ) {
        if (!_isUpdateAvailable) return
        if (_isUpdating) return
        if (latestFile.exists()) {
            latestFile.delete()
        }
        try {
            log.i("开始下载更新...")
            Snackbar.show(
                "开始下载更新",
                leadingLoading = true,
                duration = 10_000,
                id = "downloadProcess",
            )

            _isUpdating = true
            _updateDownloaded = false

            // 先走代理地址；代理失败（超时/不通，电视上 mirror.ghproxy.com
            // 就超时过）时回退 GitHub 直链再试一次。downloadUrl 就是代理前缀
            // + 直链拼出来的，去掉前缀即直链，不用改解析器。
            // 877 轮恢复：这段在 73069d7f 里有，845 轮修编译红时被整段删掉了。
            suspend fun tryDownload(url: String) {
                download(url, latestFile.path) {
                    Snackbar.show(
                        "正在下载更新: $it%",
                        leadingLoading = true,
                        duration = 10_000,
                        id = "downloadProcess"
                    )
                }
            }
            try {
                tryDownload(_latestRelease.downloadUrl)
            } catch (ex: Exception) {
                if (ex is CancellationException) throw ex
                val directUrl = _latestRelease.downloadUrl.removePrefix(Constants.GITHUB_PROXY)
                if (directUrl == _latestRelease.downloadUrl) throw ex
                log.i("代理下载失败，改走 GitHub 直链重试")
                Snackbar.show(
                    "代理下载失败，正在尝试直连下载",
                    leadingLoading = true,
                    duration = 10_000,
                    id = "downloadProcess"
                )
                tryDownload(directUrl)
            }

            _updateDownloaded = true
            Snackbar.show("下载更新成功")
            log.i("下载更新成功")
        } catch (ex: Exception) {
            // 下载中途被取消不是下载失败：取消信号原样抛出去，别弹“下载更新失败”
            // 的假报错（690 轮同款问题；天光 1658 轮提示里单点的就是这两处）
            if (ex is CancellationException) throw ex
            Snackbar.show("下载更新失败", type = SnackbarType.ERROR)
            log.e("下载更新失败", ex)
        } finally {
            _isUpdating = false
        }
    }

    companion object {
        var instance: UpdateViewModel? = null
    }
}

val updateVM: UpdateViewModel
    @Composable get() {
        // 全局单例只在组合提交后登记：直接在组合里写外部状态违反 Compose
        // 规则（组合可能被丢弃重跑）；SideEffect 只在组合成功提交后执行。
        // instance 本身要留着：导航的每个页面是各自的 ViewModel 归属，
        // 单用 viewModel() 会每页各建一个实例，更新状态就不再跨页面共享
        val vm = UpdateViewModel.instance ?: viewModel<UpdateViewModel>()
        SideEffect { UpdateViewModel.instance = vm }
        return vm
    }