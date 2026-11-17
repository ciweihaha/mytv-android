package top.yogiczy.mytv.tv.ui.screen.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.core.data.entities.channel.ChannelFavoriteList
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelLineList
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.core.data.entities.epg.EpgList.Companion.match
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSource
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.network.HttpException
import top.yogiczy.mytv.core.data.network.HttpStatusException
import top.yogiczy.mytv.core.data.repositories.epg.EpgRepository
import top.yogiczy.mytv.core.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.core.data.utils.ChannelAlias
import top.yogiczy.mytv.core.data.utils.ChannelUtil
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.tv.sync.CloudSync
import top.yogiczy.mytv.tv.sync.CloudSyncData
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.material.SnackbarType
import top.yogiczy.mytv.tv.ui.utils.Configs
import java.util.Calendar

class MainViewModel : ViewModel() {
    private val log = Logger.create("MainViewModel")

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var _lastJob: Job? = null

    var needRefresh: () -> Unit = {}

    init {
        viewModelScope.launch {
            pullCloudSyncData()
            init()
            _lastJob?.join()
            refreshOtherIptvSource()
        }
    }

    fun init() {
        _lastJob?.cancel()
        _lastJob = viewModelScope.launch {
            ChannelAlias.refresh()
            refreshChannel()
            refreshEpg()
            mergeEpgMetadata()
        }
    }

    private suspend fun pullCloudSyncData() {
        if (!Configs.cloudSyncAutoPull) return

        _uiState.value = MainUiState.Loading("拉取云端数据")
        runCatching {
            val syncData = CloudSync.pull()

            if (syncData != CloudSyncData.EMPTY) {
                syncData.apply()
                needRefresh()
            }
        }
    }

    private suspend fun refreshChannel() {
        _uiState.value = MainUiState.Loading("加载订阅源")

        flow {
            emit(
                IptvRepository(Configs.iptvSourceCurrent)
                    .getChannelGroupList(Configs.iptvSourceCacheTime)
            )
        }
            .retryWhen { e, attempt ->
                if (!shouldRetryLoadFailure(e, attempt, Configs.networkRetryCount)) {
                    return@retryWhen false
                }

                _uiState.value =
                    MainUiState.Loading("加载订阅源(${attempt + 1}/${Configs.networkRetryCount})...")
                delay(Configs.networkRetryInterval)
                true
            }
            .catch {
                // 取消不是失败：flow 的 catch 会把 CancellationException 也吃掉，
                // 不原样抛出去，被取消的加载会被记成一次 Error（切源时旧任务的
                // Error 可能落在新任务的 Loading 之后，界面闪一下“Job was cancelled”）
                if (it is CancellationException) throw it
                _uiState.value = MainUiState.Error(it.message)
            }
            .map { mergeSimilarChannel(it) }
            .map { hybridChannel(it) }
            .map { groupList ->
                _uiState.value = MainUiState.Ready(
                    channelGroupList = groupList,
                    filteredChannelGroupList = withContext(Dispatchers.Default) {
                        ChannelGroupList(groupList.filter { it.name !in Configs.iptvChannelGroupHiddenList })
                            .withMetadata()
                    }
                )
                groupList
            }
            .map { refreshChannelFavoriteList(Configs.iptvSourceCurrent, it) }
            .collect()
    }

    private suspend fun mergeSimilarChannel(channelGroupList: ChannelGroupList): ChannelGroupList =
        withContext(Dispatchers.Default) {
            if (!Configs.iptvSimilarChannelMerge) return@withContext channelGroupList

            _uiState.value = MainUiState.Loading("合并相似频道")

            return@withContext mergeSimilarChannels(channelGroupList)
        }

    private suspend fun hybridChannel(channelGroupList: ChannelGroupList) =
        withContext(Dispatchers.Default) {
            val mode = Configs.iptvHybridMode
            if (mode != Configs.IptvHybridMode.DISABLE) {
                _uiState.value = MainUiState.Loading("匹配可用的网页源")
            }

            return@withContext hybridChannels(channelGroupList, mode)
        }

    private suspend fun refreshEpg() {
        if (!Configs.epgEnable) return

        if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < Configs.epgRefreshTimeThreshold) {
            val threshold = Configs.epgRefreshTimeThreshold.toString().padStart(2, '0') + ":00"
            log.i("当前时间未到${threshold}，不获取节目单")
            return
        }


        if (_uiState.value is MainUiState.Ready) {
            EpgList.clearCache()
            val channelGroupList = (_uiState.value as MainUiState.Ready).channelGroupList

            flow {
                val epgSource = Configs.epgSourceFollowIptv
                    .takeIf { it }
                    ?.let {
                        val iptvRepository = IptvRepository(Configs.iptvSourceCurrent)
                        iptvRepository.getEpgUrl()?.let { epgUrl ->
                            EpgSource(
                                name = Configs.iptvSourceCurrent.name,
                                url = epgUrl,
                            )
                        }
                    } ?: Configs.epgSourceCurrent

                emit(EpgRepository(epgSource).getEpgList())
            }
                .retryWhen { e, attempt ->
                    if (!shouldRetryLoadFailure(e, attempt, Configs.networkRetryCount)) {
                        return@retryWhen false
                    }

                    delay(Configs.networkRetryInterval)
                    true
                }
                .catch {
                    emit(EpgList())
                    if (it is HttpStatusException) {
                        Snackbar.show("节目单获取失败：${it.message}", type = SnackbarType.ERROR)
                    } else {
                        Snackbar.show("节目单获取失败，请检查网络连接", type = SnackbarType.ERROR)
                    }
                }
                .map { epgList ->
                    withContext(Dispatchers.Default) {
                        val filteredChannels =
                            (channelGroupList.channelList + Configs.iptvChannelFavoriteList.map { it.channel }).map { it.epgName }

                        EpgList(epgList.filter { epg -> epg.channelList.any { it in filteredChannels } })
                    }
                }
                .map { epgList ->
                    _uiState.value = (_uiState.value as MainUiState.Ready).copy(epgList = epgList)
                }
                .collect()
        }
    }

    private suspend fun mergeEpgMetadata() = withContext(Dispatchers.Default) {
        if (_uiState.value is MainUiState.Ready) {
            val channelGroupList = (_uiState.value as MainUiState.Ready).channelGroupList
            val epgList = (_uiState.value as MainUiState.Ready).epgList

            if (epgList.all { epg -> epg.logo == null }) return@withContext

            _uiState.value = (_uiState.value as MainUiState.Ready).copy(
                channelGroupList = ChannelGroupList(channelGroupList.map { group ->
                    group.copy(channelList = ChannelList(group.channelList.map { channel ->
                        channel.copy(
                            logo = epgList.match(channel)?.logo ?: channel.logo
                        )
                    }))
                }),
                epgList = epgList,
            )
        }
    }

    private suspend fun refreshOtherIptvSource() {
        val needRefreshNames = Configs.iptvChannelFavoriteList.map { it.iptvSourceName }.distinct()
            .filter { it != Configs.iptvSourceCurrent.name }

        (Configs.iptvSourceList)
            .filter { it.name in needRefreshNames }
            .filter { it.url.isNotBlank() }
            .forEach { iptvSource ->
                runCatching {
                    val channelGroupList =
                        IptvRepository(iptvSource).getChannelGroupList(Configs.iptvSourceCacheTime)

                    refreshChannelFavoriteList(iptvSource, channelGroupList)
                }
            }
    }

    private suspend fun refreshChannelFavoriteList(
        iptvSource: IptvSource,
        channelGroupList: ChannelGroupList,
    ) = withContext(Dispatchers.Default) {
        Configs.iptvChannelFavoriteList =
            refreshFavoriteChannels(Configs.iptvChannelFavoriteList, iptvSource.name, channelGroupList)
        needRefresh()
    }

    companion object {
        var instance: MainViewModel? = null
    }
}

/**
 * 加载失败要不要重试（纯函数，方便单元测试直接调用）：只重试网络故障、
 * 且还没到设置的重试次数。`HttpException` 是仓库层把拉取失败包出来的
 * 网络错（连接失败、超时、DNS）；`HttpStatusException`（404/500 等状
 * 态错）不是它的子类、从来不重试——地址或服务端本身有问题，重试也
 * 是白等。`attempt` 从 0 数：第一次失败是 0，等于次数上限那次就不重试。
 * 天光 2254 轮的同款函数按状态码分档（500/503/408/429 重试）；壳这边
 * 按异常类型分档，500 同样不重试——两边结论只在 5xx 这一档不同，改不
 * 改是行为取舍，本轮只抽函数钉死现有约定、不改语义。
 */
internal fun shouldRetryLoadFailure(e: Throwable, attempt: Long, retryCount: Long): Boolean =
    attempt < retryCount && e is HttpException

/**
 * 刷新收藏列表里的频道（纯函数，方便单元测试直接调用）：只刷新属于这个源的
 * 收藏；分组或频道在新源里找得到就换成新的频道对象（线路随源更新），找不
 * 到就留旧的、不丢收藏。
 */
internal fun refreshFavoriteChannels(
    favoriteList: ChannelFavoriteList,
    sourceName: String,
    channelGroupList: ChannelGroupList,
): ChannelFavoriteList = ChannelFavoriteList(favoriteList.map { channelFavorite ->
    if (sourceName != channelFavorite.iptvSourceName) return@map channelFavorite

    val newChannel = channelGroupList
        .firstOrNull { group -> group.name == channelFavorite.groupName }?.channelList
        ?.firstOrNull { channel -> channel.name == channelFavorite.channel.name }

    channelFavorite.copy(channel = newChannel ?: channelFavorite.channel)
})

/**
 * 网页混合线路（纯函数，方便单元测试直接调用）：按模式给每个频道拼上网页
 * 线——禁用原样返回；直播优先把网页线放后面、网页优先放前面；没有网页
 * 线的频道原样不动。两种模式只差线路顺序，两层遍历只写一份。
 */
internal fun hybridChannels(
    channelGroupList: ChannelGroupList,
    mode: Configs.IptvHybridMode,
): ChannelGroupList = when (mode) {
    Configs.IptvHybridMode.DISABLE -> channelGroupList
    else -> {
        ChannelGroupList(channelGroupList.map { group ->
            group.copy(channelList = ChannelList(group.channelList.map { channel ->
                val webViewLines = ChannelUtil.getHybridWebViewLines(channel.epgName)
                val lineList = if (mode == Configs.IptvHybridMode.IPTV_FIRST) {
                    channel.lineList + webViewLines
                } else {
                    webViewLines + channel.lineList
                }
                channel.copy(lineList = ChannelLineList(lineList))
            }))
        })
    }
}

/**
 * 合并相似频道（纯函数，方便单元测试直接调用）：按标准名分组，同一标准名
 * 的多个频道合成一个——名字取标准名，线路是各频道线路的并集（线路名取
 * 原频道名）、按地址去重；独苗频道原样不动。
 */
internal fun mergeSimilarChannels(channelGroupList: ChannelGroupList): ChannelGroupList =
    ChannelGroupList(channelGroupList.map { group ->
        group.copy(
            channelList = ChannelList(group.channelList
                .groupBy { channel -> channel.standardName }
                .map { (standardName, similarChannels) ->
                    if (similarChannels.size == 1) {
                        similarChannels.first()
                    } else {
                        val firstChannel = similarChannels.first()
                        val mergedLineList = similarChannels
                            .asSequence()
                            .flatMap { channel ->
                                channel.lineList.asSequence().map { line ->
                                    line.copy(name = channel.name)
                                }
                            }
                            .distinctBy { it.url }
                            .toList()

                        firstChannel.copy(
                            name = standardName,
                            lineList = ChannelLineList(mergedLineList)
                        )
                    }
                }
            )
        )
    })

sealed interface MainUiState {
    data class Loading(val message: String? = null) : MainUiState
    data class Error(val message: String? = null) : MainUiState
    data class Ready(
        val channelGroupList: ChannelGroupList = ChannelGroupList(),
        val filteredChannelGroupList: ChannelGroupList = ChannelGroupList(),
        val epgList: EpgList = EpgList(),
    ) : MainUiState
}

val mainVM: MainViewModel
    @Composable get() {
        // 全局单例只在组合提交后登记：直接在组合里写外部状态违反 Compose
        // 规则（组合可能被丢弃重跑）；SideEffect 只在组合成功提交后执行。
        // instance 本身要留着：导航的每个页面是各自的 ViewModel 归属，
        // 单用 viewModel() 会每页各建一个实例，更新状态就不再跨页面共享
        val vm = MainViewModel.instance ?: viewModel<MainViewModel>()
        SideEffect { MainViewModel.instance = vm }
        return vm
    }