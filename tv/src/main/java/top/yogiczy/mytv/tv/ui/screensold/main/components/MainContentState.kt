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

/**
 * 断流计数：同一条线路短时间内反复断流就放弃重备、走换线/报错。
 * 窗口按加载超时的 6 倍算（壳默认 5 秒，窗口 30 秒）：每个断流间隔
 * 约一个加载超时、至多不到两倍（再长加载超时先报了错，走的不是
 * 断流这条路），窗口外的旧断流先清掉——播稳一阵子后偶尔一次源抖
 * 不该和几小时前的记录叠加计数。顶层函数方便单元测试直接调用。
 * （天光 v2.2.30 同款机制：天光默认 15 秒、窗口 90 秒，壳侧一直
 * 没有——断流等于没处理，见 onInterrupt。）
 */
internal fun nextCutoffStreak(
    previous: List<Long>,
    now: Long,
    windowMs: Long,
    maxCutoffs: Int,
): Pair<List<Long>, Boolean> {
    val kept = previous.filter { now - it <= windowMs } + now
    return kept to (kept.size > maxCutoffs)
}

/**
 * 自动换线时找下一条能播的线：从 fromIdx 往后第一条非网页线。
 * 播失败自动换线（见 onError / onInterrupt）绝不能落到网页线
 * 上——否则切台播着播着会突然跳出央视网页（网页优先模式下网页线
 * 还排在订阅源前面）。没有能播的线就返回 null，调用方按末条线
 * 处理（报错误、不换线）。顶层函数方便单元测试直接调用。
 */
internal fun nextPlayableLineIdx(
    lineList: ChannelLineList,
    fromIdx: Int,
): Int? {
    for (i in fromIdx + 1 until lineList.size) {
        if (lineList[i].hybridType != ChannelLine.HybridType.WebView) return i
    }
    return null
}

/**
 * 切台默认选线时找第一条能播的线：第一条非网页线。
 * 上面两路（播通过的地址、同 host）都没命中时原来回 0，网页优先
 * 模式下 0 号就是网页线、切台直接跳网页。改成第一条能播的线；
 * 全是网页线时回 0（纯网页台只能看网页，保持原行为）。
 * 顶层函数方便单元测试直接调用。
 */
internal fun firstPlayableLineIdx(lineList: ChannelLineList): Int {
    val idx = lineList.indexOfFirst { it.hybridType != ChannelLine.HybridType.WebView }
    return if (idx >= 0) idx else 0
}

/**
 * 首播选台（纯函数）：上次播放的台还在当前列表里就播它，否则播列表
 * 第一个台；列表还没到（空）返回 null——调用方别播、等列表到了再
 * 补一次（见 MainContentState.ensureFirstPlay）。顶层函数方便单元
 * 测试直接调用。
 * （250 回归：首播只在 init 里跑一次，那时列表还没加载完、只能兜
 * 底成空频道，之后再也没人补播，一直黑屏；退出重进就好是因为重进
 * 时列表已在。）
 */
internal fun resolveFirstPlayChannel(
    lastPlay: Channel,
    channelGroupList: ChannelGroupList,
): Channel? {
    if (channelGroupList.channelList.isEmpty()) return null
    if (lastPlay.lineList.isNotEmpty() && lastPlay in channelGroupList.channelList) return lastPlay
    return channelGroupList.channelFirstOrNull()
}

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

    // 断流计数：同一条线路（频道＋线路下标）短时间内反复断流的时间戳；
    // 切到别的台/线就清零重算（见 onInterrupt），别把旧台的记录带到新台上
    private var cutoffTimestamps = emptyList<Long>()
    private var cutoffTarget: Pair<Channel, Int>? = null

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
        // 监听先注册、再首播：首播的准备是同步的，准备里同步报的错
        //（密钥格式不对、类型不支持）发生时监听还没注册就没人接，
        // 不显示也不自动换台、直接黑屏卡住；放前面就接得住
        videoPlayerState.onReady {
            settingsViewModel.iptvChannelLinePlayableUrlList += currentChannelLine.url
            settingsViewModel.iptvChannelLinePlayableHostList += currentChannelLine.url.urlHost()
        }

        videoPlayerState.onError {
            if (_currentPlaybackEpgProgramme != null) return@onError

            settingsViewModel.iptvChannelLinePlayableUrlList -= currentChannelLine.url
            settingsViewModel.iptvChannelLinePlayableHostList -= currentChannelLine.url.urlHost()

            // 自动换线跳过网页线：下一条是网页线就再往后找，没有能播
            // 的线就停在当前线（和原来末条线一样不换线、只报错误），
            // 绝不跳出网页
            nextPlayableLineIdx(_currentChannel.lineList, _currentChannelLineIdx)?.let { nextIdx ->
                changeCurrentChannel(_currentChannel, nextIdx)
            }
        }

        videoPlayerState.onInterrupt {
            // 断流（位置一直不前进）先重备同一条线路；同一条线路短时间
            // 内反复断流（见 nextCutoffStreak），就当这条线路死了，走和
            // 报错一样的换线；末条线路时报 CUTOFF 错误、不再重备——否则
            // 播起来又冻住的源会每隔一个加载超时就闪一下、永远不换线。
            // 原先这里直接调 changeCurrentChannel（同台同线同节目）被
            // 换台守卫原样挡回，断流等于没处理、画面一直冻住，只能手动
            // 换台；天光 v2.2.30 同款机制，壳侧一直没有，这轮补上。
            val now = System.currentTimeMillis()
            val target = _currentChannel to _currentChannelLineIdx
            if (cutoffTarget != target) {
                cutoffTarget = target
                cutoffTimestamps = emptyList()
            }
            val (streak, giveUp) = nextCutoffStreak(
                cutoffTimestamps,
                now,
                6 * Configs.videoPlayerLoadTimeout,
                MAX_CUTOFFS_BEFORE_GIVE_UP,
            )
            cutoffTimestamps = streak
            if (giveUp) {
                cutoffTarget = null
                cutoffTimestamps = emptyList()
                settingsViewModel.iptvChannelLinePlayableUrlList -= currentChannelLine.url
                settingsViewModel.iptvChannelLinePlayableHostList -= currentChannelLine.url.urlHost()
                // 自动换线跳过网页线（同 onError）：没有能播的线就报
                // CUTOFF 错误，别跳出网页
                val nextIdx = nextPlayableLineIdx(_currentChannel.lineList, _currentChannelLineIdx)
                if (_currentPlaybackEpgProgramme == null && nextIdx != null) {
                    changeCurrentChannel(_currentChannel, nextIdx)
                } else {
                    val cutoff = VideoPlayer.PlaybackException.CUTOFF
                    videoPlayerState.error = "${cutoff.errorCodeName}(${cutoff.errorCode})"
                }
            } else {
                changeCurrentChannel(
                    _currentChannel,
                    _currentChannelLineIdx,
                    _currentPlaybackEpgProgramme,
                    force = true,
                )
            }
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

        val channelGroupList = channelGroupListProvider()

        changeCurrentChannel(settingsViewModel.iptvChannelLastPlay.isEmptyOrElse {
            channelGroupList.channelFirstOrNull() ?: Channel.EMPTY
        })
    }

    /**
     * 列表到达后补首播：init 时列表还没加载完会跑空（当前台无线路），
     * 列表加载完后由界面层（MainContent 的 LaunchedEffect）再调一次，
     * 按“上次播放（若在列表里）否则第一个台”选台。当前台已有线路时
     * 什么都不做——正在播的台绝不被打断；列表还没到时也不做，等下一次。
     */
    fun ensureFirstPlay() {
        if (_currentChannel.lineList.isNotEmpty()) return
        val channel = resolveFirstPlayChannel(
            settingsViewModel.iptvChannelLastPlay,
            channelGroupListProvider(),
        ) ?: return
        changeCurrentChannel(channel)
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

        // 切台默认选线也跳过网页线：上面两路都没命中时原来回 0，
        // 网页优先模式下 0 号就是网页线、切台直接跳网页；改成第
        // 一条能播的线（全是网页线时回 0，保持原行为）。手动换线
        // 传了明确下标的不走这里，不受影响
        return if (lineIdx == null && idx < 0) {
            firstPlayableLineIdx(lineList)
        } else {
            max(0, min(idx, lineList.size - 1))
        }
    }

    fun changeCurrentChannel(
        channel: Channel,
        lineIdx: Int? = null,
        playbackEpgProgramme: EpgProgramme? = null,
        // 断流重备用：同台同线同节目再准备一次，跳过下面的换台守卫；
        // 平时切台不传，守卫照常拦着、避免无意义的重复准备
        force: Boolean = false,
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

        if (!force && channel == _currentChannel && realLineIdx == _currentChannelLineIdx && playbackEpgProgramme == _currentPlaybackEpgProgramme) return

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
        // 无线路的频道（如空列表兜底的 Channel.EMPTY）没有可播线路：
        // getLineIdx 在空列表上算出 0，下标取值直接越界；换台守卫
        // 同款问题已在 changeCurrentChannel 里拦过，这里补上
        if (channel.lineList.isEmpty()) return false
        val currentLineIdx = getLineIdx(channel.lineList, lineIdx)
        return channel.lineList[currentLineIdx].playbackType != null ||
        ChannelUtil.urlSupportPlayback(channel.lineList[currentLineIdx].url)
    }

    companion object {
        /**
         * 断流多少次后放弃重备：和播放器里直播位置落后重试上限不同，
         * 断流是位置不动的独立判断，第 21 次断流时放弃（见 nextCutoffStreak）；
         * 断流放弃后不自动重调，只报 CUTOFF 或换线。
         * 20 次不是拍脑袋：凤凰那种每条连接几秒就断、位置几秒不动就
         * 判一次断流的源，30 秒窗口里稳态就有几次；20 次是稳态的几倍
         * 余量，正常抖动永远碰不到，只防几秒内连断这种真死源。真正死
         * 掉的源不用怕上限太高：播不起来根本到不了就绪、位置不动，
         * 加载超时那路会先报；上限只管“播几秒又断流”这种半死不活的线。
         * （天光 v2.2.30 同款：3 次改 20 次，壳侧一直没有这档机制。）
         */
        const val MAX_CUTOFFS_BEFORE_GIVE_UP = 20
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