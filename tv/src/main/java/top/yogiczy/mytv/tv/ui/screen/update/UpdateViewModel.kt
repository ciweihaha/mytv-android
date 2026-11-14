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


    suspend fun checkUpdate(currentVersion: String, channel: String) {
        if (_isChecking) return
        if (_isUpdateAvailable) return

        try {
            log.i("开始检查更新（${channel}）...")

            _checkFailed = false
            _isChecking = true
            val releaseUrl = Constants.GIT_RELEASE_LATEST_URL[channel] ?: return
            _latestRelease = GitRepository().latestRelease(releaseUrl)
            _isUpdateAvailable = _latestRelease.version.compareVersion(currentVersion) > 0

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

    suspend fun downloadAndUpdate(latestFile: File) {
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

            Downloader.downloadTo(_latestRelease.downloadUrl, latestFile.path) {
                Snackbar.show(
                    "正在下载更新: $it%",
                    leadingLoading = true,
                    duration = 10_000,
                    id = "downloadProcess"
                )
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