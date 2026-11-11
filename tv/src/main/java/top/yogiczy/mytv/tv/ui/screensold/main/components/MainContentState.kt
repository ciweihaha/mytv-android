package top.yogiczy.mytv.tv.ui.screensold.main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelFirstOrNull
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelGroupIdx
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelIdx
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelLastOrNull
import top.yogiczy.mytv.core.data.entities.channel.ChannelLine
import top.yogiczy.mytv.core.data.entities.channel.ChannelLineList
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSourceList
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeReserve
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeReserveList
import top.yogiczy.mytv.core.data.utils.ChannelUtil
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.data.utils.Loggable
import top.yogiczy.mytv.core.util.utils.urlHost
import top.yogiczy.mytv.tv.ui.utils.Configs
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.VideoPlayerState
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.VideoPlayer
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.Media3VideoPlayer
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.rememberVideoPlayerState
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Stable
class MainContentState(
    private val coroutineScope: CoroutineScope,
    private val videoPlayerState: VideoPlayerState,
    private val channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    private val favoriteChannelListProvider: () -> ChannelList = { ChannelList() },
    private val settingsViewModel: SettingsViewModel,
) : Loggable("MainContentState") {
    private var _currentChannel by mutableStateOf(Channel())
    val currentChannel get() = _currentChannel

    private var _currentChannelLineIdx by mutableIntStateOf(0)
    val currentChannelLineIdx get() = _currentChannelLineIdx

    val currentChannelLine get() = _currentChannel.lineList[_currentChannelLineIdx]
    private var _currentPlaybackEpgProgramme by mutableStateOf<EpgProgramme?>(null)
    val currentPlaybackEpgProgramme get() = _currentPlaybackEpgProgramme

    // 拼回放地址用的格式器：切台每次都新建 7 个，提到类里只建一次。
    // 这个类只在主线程用（改的都是界面状态），格式器不是线程安全的也不能多线程共用，
    // 但这里单线程用没问题。
    private val playbackTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
    private val playbackYearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
    private val playbackMonthFormat = SimpleDateFormat("MM", Locale.getDefault())
    private val playbackDayFormat = SimpleDateFormat("dd", Locale.getDefault())
    private val playbackHourFormat = SimpleDateFormat("HH", Locale.getDefault())
    private val playbackMinuteFormat = SimpleDateFormat("mm", Locale.getDefault())
    private val playbackSecondFormat = SimpleDateFormat("ss", Locale.getDefault())
    private val playseekTimeFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())

    private var _tempChannelScreenHideJob: Job? = null

    private var _isTempChannelScreenVisible by mutableStateOf(false)
    var isTempChannelScreenVisible
        get() = _isTempChannelScreenVisible
        set(value) {
            _isTempChannelScreenVisible = value
        }

    private var _isChannelScreenVisible by mutableStateOf(false)
    var isChannelScreenVisible
        get() = _isChannelScreenVisible
        set(value) {
            _isChannelScreenVisible = value
        }

    private var _isVideoPlayerControllerScreenVisible by mutableStateOf(false)
    var isVideoPlayerControllerScreenVisible
        get() = _isVideoPlayerControllerScreenVisible
        set(value) {
            _isVideoPlayerControllerScreenVisible = value
        }

    private var _isIptvSourceScreenVisible by mutableStateOf(false)
    var isIptvSourceScreenVisible
        get() = _isIptvSourceScreenVisible
        set(value) {
            _isIptvSourceScreenVisible = value
        }

    private var _isQuickOpScreenVisible by mutableStateOf(false)
    var isQuickOpScreenVisible
        get() = _isQuickOpScreenVisible
        set(value) {
            _isQuickOpScreenVisible = value
        }
    
    private var _isInPlaybackMOde by mutableStateOf(false)
    var isInPlaybackMode
        get() = _isInPlaybackMOde
        set(value) {
            _isInPlaybackMOde = value
        }

    private var _isEpgScreenVisible by mutableStateOf(false)
    var isEpgScreenVisible
        get() = _isEpgScreenVisible
        set(value) {
            _isEpgScreenVisible = value
        }

    private var _isChannelLineScreenVisible by mutableStateOf(false)
    var isChannelLineScreenVisible
        get() = _isChannelLineScreenVisible
        set(value) {
            _isChannelLineScreenVisible = value
        }

    private var _isVideoPlayerDisplayModeScreenVisible by mutableStateOf(false)
    var isVideoPlayerDisplayModeScreenVisible
        get() = _isVideoPlayerDisplayModeScreenVisible
        set(value) {
            _isVideoPlayerDisplayModeScreenVisible = value
        }

    private var _isVideoTracksScreenVisible by mutableStateOf(false)
    var isVideoTracksScreenVisible
        get() = _isVideoTracksScreenVisible
        set(value) {
            _isVideoTracksScreenVisible = value
        }

    private var _isAudioTracksScreenVisible by mutableStateOf(false)
    var isAudioTracksScreenVisible
        get() = _isAudioTracksScreenVisible
        set(value) {
            _isAudioTracksScreenVisible = value
        }

    private var _isSubtitleTracksScreenVisible by mutableStateOf(false)
    var isSubtitleTracksScreenVisible
        get() = _isSubtitleTracksScreenVisible
        set(value) {
            _isSubtitleTracksScreenVisible = value
        }

    init {
        val channelGroupList = channelGroupListProvider()

        changeCurrentChannel(settingsViewModel.iptvChannelLastPlay.isEmptyOrElse {
            channelGroupList.channelFirstOrNull() ?: Channel.EMPTY
        })

        videoPlayerState.onReady {
            settingsViewModel.iptvChannelLinePlayableUrlList += currentChannelLine.url
            settingsViewModel.iptvChannelLinePlayableHostList += currentChannelLine.url.urlHost()
        }

        videoPlayerState.onError {
            if (_currentPlaybackEpgProgramme != null) return@onError

            settingsViewModel.iptvChannelLinePlayableUrlList -= currentChannelLine.url
            settingsViewModel.iptvChannelLinePlayableHostList -= currentChannelLine.url.urlHost()

            if (_currentChannelLineIdx < _currentChannel.lineList.size - 1) {
                changeCurrentChannel(_currentChannel, _currentChannelLineIdx + 1)
            }
        }

        videoPlayerState.onInterrupt {
            changeCurrentChannel(
                _currentChannel,
                _currentChannelLineIdx,
                _currentPlaybackEpgProgramme
            )
        }

        videoPlayerState.onIsBuffering { isBuffering ->
            if (isBuffering) {
                _isTempChannelScreenVisible = true
            } else {
                _tempChannelScreenHideJob?.cancel()
                _tempChannelScreenHideJob = coroutineScope.launch {
                    val name = _currentChannel.name
                    val lineIdx = _currentChannelLineIdx
                    delay(Constants.UI_TEMP_CHANNEL_SCREEN_SHOW_DURATION)
                    if (name == _currentChannel.name && lineIdx == _currentChannelLineIdx) {
                        _isTempChannelScreenVisible = false
                    }
                }
            }
        }
    }

    private fun getPrevFavoriteChannel(): Channel? {
        if (!settingsViewModel.iptvChannelFavoriteListVisible) return null

        val favoriteChannelList = favoriteChannelListProvider()

        if (_currentChannel !in favoriteChannelList) return null

        val currentIdx = favoriteChannelList.indexOf(_currentChannel)

        return favoriteChannelList.getOrElse(currentIdx - 1) {
            if (settingsViewModel.iptvChannelChangeListLoop) favoriteChannelList.lastOrNull()
            // 循环关着时（默认）到头就停在当前台：原来是跳到整个源的最
            // 后一个台，和“首尾循环”开关的说明、和非收藏路径到头停住
            // 的行为都对不上
            else _currentChannel
        }
    }

    private fun getNextFavoriteChannel(): Channel? {
        if (!settingsViewModel.iptvChannelFavoriteListVisible) return null

        val favoriteChannelList = favoriteChannelListProvider()

        if (_currentChannel !in favoriteChannelList) return null

        val currentIdx = favoriteChannelList.indexOf(_currentChannel)

        return favoriteChannelList.getOrElse(currentIdx + 1) {
            if (settingsViewModel.iptvChannelChangeListLoop) favoriteChannelList.firstOrNull()
            // 同上：循环关着时到尾就停在当前台，不跳到整个源的第一个台
            else _currentChannel
        }
    }

    private fun getPrevChannel(): Channel {
        return getPrevFavoriteChannel() ?: run {
            val channelGroupList = channelGroupListProvider()
            
            if (settingsViewModel.iptvChannelChangeCrossGroup) {
                // 跨分组切换逻辑
                // 频道列表为空（源全部加载失败或已清空）时无频道可切，保持当前频道；
                // 不能兜底成 Channel()：它默认自带一条示例线路（假地址 1.2.3.4），
                // 换台守卫认不出它是假的，会真去播、还会把它记成上次播放
                if (channelGroupList.channelList.isEmpty()) return _currentChannel
                val currentIdx = channelGroupList.channelIdx(_currentChannel)
                val prevIdx = if (currentIdx <= 0) {
                    if (settingsViewModel.iptvChannelChangeListLoop) 
                        channelGroupList.channelList.size - 1 
                    else 
                        0
                } else {
                    currentIdx - 1
                }
                channelGroupList.channelList.getOrElse(prevIdx) {
                    channelGroupList.channelLastOrNull() ?: _currentChannel
                }
            } else {
                // 分组内切换逻辑
                val group = channelGroupList.getOrElse(channelGroupList.channelGroupIdx(_currentChannel)) { channelGroupList.firstOrNull() }
                    ?: return _currentChannel
                // 频道列表为空（源全部加载失败或已清空）时无频道可切，保持当前频道
                if (group.channelList.isEmpty()) return _currentChannel
                val currentIdx = group.channelList.indexOf(_currentChannel)
                if (currentIdx <= 0) {
                    // 当前是分组内第一个
                    if (settingsViewModel.iptvChannelChangeListLoop) {
                        // 循环开启时，返回分组内最后一个
                        group.channelList.last()
                    } else {
                        // 循环关闭时，保持当前频道不变
                        _currentChannel
                    }
                } else {
                    // 返回分组内前一个
                    group.channelList[currentIdx - 1]
                }
            }
        }
    }

    private fun getNextChannel(): Channel {
        return getNextFavoriteChannel() ?: run {
            val channelGroupList = channelGroupListProvider()
            
            if (settingsViewModel.iptvChannelChangeCrossGroup) {
                // 跨分组切换逻辑
                // 同上：列表为空时无频道可切，保持当前频道，不能兜底成 Channel()
                //（默认自带一条示例线路假地址，会真去播并记成上次播放）
                if (channelGroupList.channelList.isEmpty()) return _currentChannel
                val currentIdx = channelGroupList.channelIdx(_currentChannel)
                val nextIdx = if (currentIdx >= channelGroupList.channelList.size - 1) {
                    if (settingsViewModel.iptvChannelChangeListLoop) 
                        0 
                    else 
                        channelGroupList.channelList.size - 1
                } else {
                    currentIdx + 1
                }
                channelGroupList.channelList.getOrElse(nextIdx) {
                    channelGroupList.channelFirstOrNull() ?: _currentChannel
                }
            } else {
                // 分组内切换逻辑
                val group = channelGroupList.getOrElse(channelGroupList.channelGroupIdx(_currentChannel)) { channelGroupList.firstOrNull() }
                    ?: return _currentChannel
                // 频道列表为空（源全部加载失败或已清空）时无频道可切，保持当前频道
                if (group.channelList.isEmpty()) return _currentChannel
                val currentIdx = group.channelList.indexOf(_currentChannel)
                if (currentIdx >= group.channelList.size - 1) {
                    // 当前是分组内最后一个
                    if (settingsViewModel.iptvChannelChangeListLoop) {
                        // 循环开启时，返回分组内第一个
                        group.channelList.first()
                    } else {
                        // 循环关闭时，保持当前频道不变
                        _currentChannel
                    }
                } else {
                    // 返回分组内下一个
                    group.channelList[currentIdx + 1]
                }
            }
        }
    }

    private fun getLineIdx(lineList: ChannelLineList, lineIdx: Int? = null): Int {
        val idx = if (lineIdx == null) {
            val idx = lineList.indexOfFirst { line ->
                settingsViewModel.iptvChannelLinePlayableUrlList.contains(line.url)
            }

            if (idx < 0) {
                lineList.indexOfFirst { line ->
                    settingsViewModel.iptvChannelLinePlayableHostList.contains(line.url.urlHost())
                }
            } else idx
        } else lineList.resolveLineIdx(lineIdx)

        return max(0, min(idx, lineList.size - 1))
    }

    fun changeCurrentChannel(
        channel: Channel,
        lineIdx: Int? = null, 
        playbackEpgProgramme: EpgProgramme? = null,
    ) {
        // 无线路的频道（如空列表兜底的 Channel.EMPTY）无法播放：直接忽略，
        // 避免 currentChannelLine 下标越界，也不把它记为上次播放
        if (channel.lineList.isEmpty()) return
        // 换成本列表里的同一台：从收藏（序号固定 -1）或上次播放（旧源的
        // 序号）拿来的频道对象序号不对，临时频道条、经典选台页右上角、
        // 台标占位那几处用频道自带的序号显示，会显示“00”或旧号；列表
        // 里找得到（同名同线路）就用列表那份（带当前源的正确序号），
        // 找不到才用传进来的
        val channel = channelGroupListProvider().channelList.firstOrNull { it == channel } ?: channel
        settingsViewModel.iptvChannelLastPlay = channel
        val realLineIdx = getLineIdx(channel.lineList, lineIdx)

        if (channel == _currentChannel && realLineIdx == _currentChannelLineIdx && playbackEpgProgramme == _currentPlaybackEpgProgramme) return
        
        if (channel == _currentChannel && realLineIdx != _currentChannelLineIdx) {
            settingsViewModel.iptvChannelLinePlayableUrlList -= currentChannelLine.url
            settingsViewModel.iptvChannelLinePlayableHostList -= currentChannelLine.url.urlHost()
        }

        _isTempChannelScreenVisible = true

        _currentChannel = channel
        _currentChannelLineIdx = realLineIdx
        _currentPlaybackEpgProgramme = playbackEpgProgramme
        currentChannelLine.playbackUrl = null
        isInPlaybackMode = false
        var url = currentChannelLine.url
        _currentPlaybackEpgProgramme?.let { playbackEpg ->
            if(currentChannelLine.playbackType != null){
                var playbackFormat = currentChannelLine.playbackFormat ?: ""
                val timeFormat = playbackTimeFormat
                val tfY = playbackYearFormat
                val tfM = playbackMonthFormat
                val tfD = playbackDayFormat
                val tfH = playbackHourFormat
                val tfm = playbackMinuteFormat
                val tfS = playbackSecondFormat
                val nowTime = System.currentTimeMillis()
                // String.replace 返回新字符串：原先用 apply 包着一串 replace，
                // 返回值全被丢弃，{utc}、{utcend}、${start} 等占位符从未被替换，
                // 用这类格式的回放源 URL 里一直带着字面量占位符、播不了。
                // 改成链式赋值；{utcend} 必须先于 {utc} 替换，否则 "{utcend}"
                // 会被 "{utc}" 的替换截断成 "<时间>end}"。
                _currentPlaybackEpgProgramme?.let { epgProgramme ->
                    playbackFormat = playbackFormat
                        .replace("{utcend}", timeFormat.format(epgProgramme.endAt))
                        .replace("{utc}", timeFormat.format(epgProgramme.startAt))
                        .replace("\${start}", timeFormat.format(epgProgramme.startAt))
                        .replace("{lutc}", timeFormat.format(nowTime))
                        .replace("\${now}", tfY.format(nowTime))
                        .replace("\${timestamp}", timeFormat.format(nowTime))
                        .replace("\${end}", timeFormat.format(epgProgramme.endAt))
                        .replace("{Y}", tfY.format(epgProgramme.startAt))
                        .replace("{m}", tfM.format(epgProgramme.startAt))
                        .replace("{d}", tfD.format(epgProgramme.startAt))
                        .replace("{H}", tfH.format(epgProgramme.startAt))
                        .replace("{M}", tfm.format(epgProgramme.startAt))
                        .replace("{S}", tfS.format(epgProgramme.startAt))
                }
                playbackFormat = ChannelUtil.replacePlaybackFormat(playbackFormat, playbackEpg.startAt, nowTime, playbackEpg.endAt)?:""
                url = when (currentChannelLine.playbackType ?: 10) {
                    0 -> playbackFormat
                    1 -> "$url$playbackFormat"
                    // 2 -> 
                    // 3 -> 
                    // 4 -> 
                    else -> url
                }
            }else{
                val timeFormat = playseekTimeFormat
                val query = listOf(
                    "playseek=",
                    timeFormat.format(playbackEpg.startAt),
                    "-",
                    timeFormat.format(playbackEpg.endAt),
                ).joinToString("")
                // 地址可能含空格、中文等非法字符：java.net.URI 构造直接抛异常，
                // 点节目单回放时会闪退；解析失败时按无查询串处理（拼 ?）
                val hasQuery = runCatching { URI(url).query }.getOrNull()?.isNotBlank() == true
                url = if (hasQuery) "$url&$query" else "$url?$query"
                if (Configs.iptvPLTVToTVOD){
                    url = ChannelUtil.urlToCanPlayback(url)
                }
            }
            currentChannelLine.playbackUrl = url
            isInPlaybackMode = true
        }
        
        val line = currentChannelLine.copy(url = url)

        log.d("播放${_currentChannel.name}（${_currentChannelLineIdx + 1}/${_currentChannel.lineList.size}）: $line")

        if (line.hybridType == ChannelLine.HybridType.WebView) {
            log.i("检测到WebView类型URL: ${line.url}")
            log.i("正在使用WebView打开而不是视频播放器")
            videoPlayerState.metadata = VideoPlayer.Metadata()
            videoPlayerState.stop()
        } else {
            currentChannelLine.playbackUrl = null
            log.i("检测到普通视频URL: ${line.url}")
            log.i("hybridType: ${line.hybridType}, 使用视频播放器播放")
            if(line.url.startsWith("rtsp://") && line.url.contains("smil") && (videoPlayerState.instance is Media3VideoPlayer)){
                settingsViewModel.videoPlayerCore = Configs.VideoPlayerCore.IJK // Media3 1.6.0 不支持rtsp有效负载类型33
            }else{
                videoPlayerState.prepare(line)
            }
            
        }
    }

    fun changeCurrentChannelToPrev() {
        changeCurrentChannel(getPrevChannel())
    }

    fun changeCurrentChannelToNext() {
        changeCurrentChannel(getNextChannel())
    }

    fun reverseEpgProgrammeOrNot(channel: Channel, programme: EpgProgramme) {
        val reverse = settingsViewModel.epgChannelReserveList.firstOrNull {
            it.test(channel, programme)
        }

        if (reverse != null) {
            settingsViewModel.epgChannelReserveList =
                EpgProgrammeReserveList(settingsViewModel.epgChannelReserveList - reverse)
            Snackbar.show("取消预约：${reverse.channel} - ${reverse.programme}")
        } else {
            val newReserve = EpgProgrammeReserve(
                channel = channel.name,
                programme = programme.title,
                startAt = programme.startAt,
                endAt = programme.endAt,
            )

            settingsViewModel.epgChannelReserveList =
                EpgProgrammeReserveList(settingsViewModel.epgChannelReserveList + newReserve)
            Snackbar.show("已预约：${channel.name} - ${programme.title}")
        }
    }

    fun supportPlayback(
        channel: Channel = _currentChannel,
        lineIdx: Int? = _currentChannelLineIdx,
    ): Boolean {
        val currentLineIdx = getLineIdx(channel.lineList, lineIdx)
        return channel.lineList[currentLineIdx].playbackType != null || 
        ChannelUtil.urlSupportPlayback(channel.lineList[currentLineIdx].url)
    }
}

@Composable
fun rememberMainContentState(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    videoPlayerState: VideoPlayerState = rememberVideoPlayerState(),
    channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    favoriteChannelListProvider: () -> ChannelList = { ChannelList() },
    settingsViewModel: SettingsViewModel = settingsVM,
): MainContentState {
    val favoriteChannelListProviderUpdated by rememberUpdatedState(favoriteChannelListProvider)

    return remember(settingsVM.videoPlayerCore) {
        MainContentState(
            coroutineScope = coroutineScope,
            videoPlayerState = videoPlayerState,
            channelGroupListProvider = channelGroupListProvider,
            favoriteChannelListProvider = favoriteChannelListProviderUpdated,
            settingsViewModel = settingsViewModel,
        )
    }
}