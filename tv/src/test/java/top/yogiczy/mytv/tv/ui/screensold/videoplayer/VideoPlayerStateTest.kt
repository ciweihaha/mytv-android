package top.yogiczy.mytv.tv.ui.screensold.videoplayer

import android.view.SurfaceView
import android.view.TextureView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.ChannelLine
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.VideoPlayer

/**
 * 播放器状态桥的报错映射（天光 1831 轮姊妹检查）：
 * 真报错时 error 写成“名字（错误码）”并通知监听器一次；
 * 空报错（进缓冲时清显示）只清 error、不再通知。
 * 生产代码这两档本来就是对的，这里只把行为钉死，别以后改坏了。
 */
class VideoPlayerStateTest {

    /** 只为测试搭的空播放器：把基类的触发口子公开出来，不碰 Android 框架。 */
    private class FakeVideoPlayer : VideoPlayer(CoroutineScope(Dispatchers.Unconfined)) {
        // 状态层控制方法是不是真转到播放器上，靠这些计数/记录断：
        // 原先全是空实现，调了也看不出来（天光 2181 轮姊妹检查）
        var playCalls = 0
        var pauseCalls = 0
        var stopCalls = 0
        val prepareLines = mutableListOf<ChannelLine>()
        val seekToPositions = mutableListOf<Long>()
        val seekByDeltas = mutableListOf<Long>()
        val selectedVideoTracks = mutableListOf<Metadata.Video?>()
        val selectedAudioTracks = mutableListOf<Metadata.Audio?>()
        val selectedSubtitleTracks = mutableListOf<Metadata.Subtitle?>()
        val setVolumes = mutableListOf<Float>()

        override fun prepare(line: ChannelLine) {
            prepareLines.add(line)
        }
        override fun play() {
            playCalls++
        }
        override fun pause() {
            pauseCalls++
        }
        override fun stop() {
            stopCalls++
        }
        override fun seekTo(position: Long) {
            seekToPositions.add(position)
        }
        override fun seekBy(deltaMs: Long) {
            seekByDeltas.add(deltaMs)
        }
        override fun setVolume(volume: Float) {
            setVolumes.add(volume)
        }
        override fun getVolume(): Float = 1f
        override fun selectVideoTrack(track: Metadata.Video?) {
            selectedVideoTracks.add(track)
        }
        override fun selectAudioTrack(track: Metadata.Audio?) {
            selectedAudioTracks.add(track)
        }
        override fun selectSubtitleTrack(track: Metadata.Subtitle?) {
            selectedSubtitleTracks.add(track)
        }
        override fun setVideoSurfaceView(surfaceView: SurfaceView) {}
        override fun setVideoTextureView(textureView: TextureView) {}

        fun emitError(error: PlaybackException?) = triggerError(error)
        fun emitReady() = triggerReady()
        fun emitBuffering(buffering: Boolean) = triggerBuffering(buffering)
        fun emitMetadata(metadata: Metadata) = triggerMetadata(metadata)
        fun emitResolution(width: Int, height: Int) = triggerResolution(width, height)
        fun emitIsPlaying(isPlaying: Boolean) = triggerIsPlayingChanged(isPlaying)
        fun emitDuration(duration: Long) = triggerDuration(duration)
        fun emitPosition(position: Long, timeoutMs: Long = 1000L) =
            triggerCurrentPosition(position, timeoutMs)
    }

    @Test
    fun `真报错写名字加错误码并通知一次，空报错只清不通知`() {
        val player = FakeVideoPlayer()
        val state = VideoPlayerState(player)
        state.initialize()
        var notifyCount = 0
        state.onError { notifyCount++ }

        player.emitError(VideoPlayer.PlaybackException.UNSUPPORTED_TYPE)
        assertEquals("ERROR_UNSUPPORTED_TYPE(10002)", state.error)
        assertEquals(1, notifyCount)

        // 空报错只是“开始缓冲”的信号：清掉显示就行，别再通知一次
        player.emitError(null)
        assertNull(state.error)
        assertEquals(1, notifyCount)
    }

    @Test
    fun `进缓冲清掉错误显示，不通知报错监听器`() {
        val player = FakeVideoPlayer()
        val state = VideoPlayerState(player)
        state.initialize()
        var notifyCount = 0
        state.onError { notifyCount++ }

        player.emitError(VideoPlayer.PlaybackException.UNSUPPORTED_TYPE)
        assertEquals(1, notifyCount)

        player.emitBuffering(true)
        assertNull(state.error)
        assertEquals(1, notifyCount)
    }

    @Test
    fun `缓冲状态与监听器原样转发（天光 2052 轮姊妹检查）`() {
        val player = FakeVideoPlayer()
        val state = VideoPlayerState(player)
        state.initialize()
        val seen = mutableListOf<Boolean>()
        state.onIsBuffering { seen.add(it) }

        // 初始不在缓冲：底层报缓冲中，状态层与监听器都应跟着变
        assertEquals(false, state.isBuffering)
        player.emitBuffering(true)
        assertEquals(true, state.isBuffering)
        assertEquals(listOf(true), seen)

        // 缓冲结束：状态与监听器都回到 false
        player.emitBuffering(false)
        assertEquals(false, state.isBuffering)
        assertEquals(listOf(true, false), seen)
    }

    @Test
    fun `就绪清掉错误显示，不通知报错监听器`() {
        val player = FakeVideoPlayer()
        val state = VideoPlayerState(player)
        state.initialize()
        var notifyCount = 0
        state.onError { notifyCount++ }

        player.emitError(VideoPlayer.PlaybackException.UNSUPPORTED_TYPE)
        assertEquals(1, notifyCount)

        player.emitReady()
        assertNull(state.error)
        assertEquals(1, notifyCount)
    }

    @Test
    fun `换台重备清掉上一次的错误显示（天光 2051 轮姊妹检查）`() {
        val player = FakeVideoPlayer()
        val state = VideoPlayerState(player)
        state.initialize()

        player.emitError(VideoPlayer.PlaybackException.UNSUPPORTED_TYPE)
        assertEquals("ERROR_UNSUPPORTED_TYPE(10002)", state.error)

        // 切台重备：旧错不能留着，否则新流还没播、错误横幅先挂在那
        state.prepare(ChannelLine(url = "http://example.com/live.m3u8"))
        assertNull(state.error)
    }

    @Test
    fun `释放后旧监听器不再收到报错（天光 2051 轮姊妹检查）`() {
        val player = FakeVideoPlayer()
        val state = VideoPlayerState(player)
        state.initialize()
        var notifyCount = 0
        state.onError { notifyCount++ }

        player.emitError(VideoPlayer.PlaybackException.UNSUPPORTED_TYPE)
        assertEquals(1, notifyCount)

        // 退出播放页释放 state：之后底层再报错也不应再回调旧监听器，
        // 否则已退出的界面还会收到已释放播放器的错误
        state.release()
        player.emitError(VideoPlayer.PlaybackException.LOAD_TIMEOUT)
        assertEquals(1, notifyCount)
        assertEquals("ERROR_UNSUPPORTED_TYPE(10002)", state.error)
    }

    @Test
    fun `就绪原样转给就绪监听器（天光 2052 轮姊妹检查）`() {
        val player = FakeVideoPlayer()
        val state = VideoPlayerState(player)
        state.initialize()
        var readyCount = 0
        state.onReady { readyCount++ }

        // 播稳了：底层 onReady 应原样转给状态层监听器（主界面靠它清
        // 掉“正在加载”与重连提示），这档一次也没断言过
        player.emitReady()
        assertEquals(1, readyCount)
    }

    @Test
    fun `元数据原样存下来供界面读（天光 2052 轮姊妹检查）`() {
        val player = FakeVideoPlayer()
        val state = VideoPlayerState(player)
        state.initialize()

        // 底层报分辨率/编解码器等元数据：状态层应原样存下来，初始
        // 空元数据、收到后才有值
        assertNull(state.metadata.video)
        player.emitMetadata(
            VideoPlayer.Metadata(
                video = VideoPlayer.Metadata.Video(width = 1920, height = 1080),
            ),
        )
        assertEquals(1920, state.metadata.video?.width)
        assertEquals(1080, state.metadata.video?.height)
    }

    @Test
    fun `分辨率改画面比例，零宽高不改`() {
        val player = FakeVideoPlayer()
        val state = VideoPlayerState(player)
        state.initialize()
        assertEquals(16f / 9f, state.aspectRatio, 0.001f)

        // 4:3 的流：比例跟着变，控制条与画面按新比例排
        player.emitResolution(640, 480)
        assertEquals(4f / 3f, state.aspectRatio, 0.001f)

        // 宽或高为 0 是无效分辨率：别把比例打成 0 或无限大
        player.emitResolution(0, 1080)
        assertEquals(4f / 3f, state.aspectRatio, 0.001f)
        player.emitResolution(0, 0)
        assertEquals(4f / 3f, state.aspectRatio, 0.001f)
    }

    @Test
    fun `播放状态时长位置原样转发`() {
        val player = FakeVideoPlayer()
        val state = VideoPlayerState(player)
        state.initialize()

        // 进度条、总时长、播放/暂停图标都靠这三路状态驱动
        player.emitIsPlaying(true)
        assertEquals(true, state.isPlaying)
        player.emitDuration(3600000L)
        assertEquals(3600000L, state.duration)
        player.emitPosition(12345L)
        assertEquals(12345L, state.currentPosition)
        player.emitIsPlaying(false)
        assertEquals(false, state.isPlaying)
    }

    @Test
    fun `位置不动断流计时到期触发一次（天光 2172 轮姊妹检查）`() {
        // 先钉“计时本身会到”：位置冻住不动，超时后中断只报一次——
        // 这是重启那档的前提，不然重启测出来 0 次也分不清是取消了
        // 还是计时根本没跑
        val player = FakeVideoPlayer()
        val interruptCount = java.util.concurrent.atomic.AtomicInteger(0)
        player.onInterrupt { interruptCount.incrementAndGet() }

        player.emitPosition(1000L, timeoutMs = 100L)
        Thread.sleep(1000)
        assertEquals(1, interruptCount.get())
    }

    @Test
    fun `位置变化重启断流计时旧计时不触发（天光 2172 轮姊妹检查）`() {
        val player = FakeVideoPlayer()
        val interruptCount = java.util.concurrent.atomic.AtomicInteger(0)
        player.onInterrupt { interruptCount.incrementAndGet() }

        // 位置一动、断流计时挂起时位置又动了：旧计时必须取消、新计时
        // 从新位置重新开始，否则正常播放中位置每 500ms 都在变、旧计时
        // 会在超时后误报一次断流（主界面收到中断会直接换线路）
        player.emitPosition(1000L, timeoutMs = 200L)
        Thread.sleep(100)
        player.emitPosition(2000L, timeoutMs = 800L)

        // 此时约 500ms：旧计时（200ms）早该到了、新计时（900ms）还没到；
        // 若旧计时没取消，这里已经是 1 次
        Thread.sleep(400)
        assertEquals(0, interruptCount.get())

        // 新计时到期：只报一次
        Thread.sleep(500)
        assertEquals(1, interruptCount.get())
    }

    @Test
    fun `重复上报同一位置不重设计时（天光 2172 轮姊妹检查）`() {
        val player = FakeVideoPlayer()
        val interruptCount = java.util.concurrent.atomic.AtomicInteger(0)
        player.onInterrupt { interruptCount.incrementAndGet() }

        // 位置冻住时轮询每 500ms 都会报同一个位置：重复上报不能重启
        // 计时，否则超时永远等不到、断流永远报不出来
        player.emitPosition(1000L, timeoutMs = 300L)
        repeat(12) {
            Thread.sleep(50)
            player.emitPosition(1000L, timeoutMs = 300L)
        }
        // 此时约 650ms：正确行为已在 300ms 时报过一次断流；
        // 若重复上报会重设计时，计时会被推到 900ms 之后、现在应还是 0 次
        Thread.sleep(50)
        assertEquals(1, interruptCount.get())
    }

    @Test
    fun `中断原样转给状态层中断监听器（断流计时 927–928 轮的下一层）`() {
        // 播放器层断流计时到期调 onInterrupt，状态桥要原样转给自己的
        // 中断监听器——主界面靠它自动换线路；播放器层三档（927–928）
        // 都钉死了，桥这一层一次也没断言过
        val player = FakeVideoPlayer()
        val state = VideoPlayerState(player)
        state.initialize()
        val interruptCount = java.util.concurrent.atomic.AtomicInteger(0)
        state.onInterrupt { interruptCount.incrementAndGet() }

        player.emitPosition(1000L, timeoutMs = 100L)
        Thread.sleep(1000)
        assertEquals(1, interruptCount.get())
    }

    @Test
    fun `释放后状态层中断监听器不再收到中断`() {
        // 退出播放页释放 state：之后底层再断流也不应再回调旧监听器，
        // 否则已退出的界面还会收到已释放播放器的中断、误触换线路。
        // 先证明监听器是活的（断流 1 次），再释放、再断流，仍是 1 次——
        // 光断释放后 0 次不够：监听器压根没注册上也会是 0。
        // 天光 2179 轮姊妹检查回镜：这档天光就是这么补的
        val player = FakeVideoPlayer()
        val state = VideoPlayerState(player)
        state.initialize()
        val interruptCount = java.util.concurrent.atomic.AtomicInteger(0)
        state.onInterrupt { interruptCount.incrementAndGet() }

        player.emitPosition(1000L, timeoutMs = 100L)
        Thread.sleep(1000)
        assertEquals(1, interruptCount.get())

        state.release()
        player.emitPosition(2000L, timeoutMs = 100L)
        Thread.sleep(1000)
        assertEquals(1, interruptCount.get())
    }

    @Test
    fun `重复初始化不重复注册，报错只通知一次`() {
        // initialize() 会先清空播放器的监听器再重注册（init 注释里写了
        // 这么做的原因：首播准备时同步报的错要有人接，又不能重复接）。
        // 要是清空那步丢了，初始化两次就会挂两份桥接闭包、一次报错
        // 通知两次，主界面换线路的逻辑会被触发两次
        val player = FakeVideoPlayer()
        val state = VideoPlayerState(player)
        state.initialize()
        state.initialize()
        var notifyCount = 0
        state.onError { notifyCount++ }

        player.emitError(VideoPlayer.PlaybackException.UNSUPPORTED_TYPE)
        assertEquals("ERROR_UNSUPPORTED_TYPE(10002)", state.error)
        assertEquals(1, notifyCount)
    }

    @Test
    fun `播放暂停停止与准备转发给播放器（天光 2181 轮姊妹检查）`() {
        // 状态层这几个方法就是纯转发：主界面点播放/暂停/停止、切台准备，
        // 都要原样落到播放器上；Fake 原先是空实现，调了也看不出来
        val player = FakeVideoPlayer()
        val state = VideoPlayerState(player)
        state.initialize()

        state.play()
        state.pause()
        state.stop()
        assertEquals(1, player.playCalls)
        assertEquals(1, player.pauseCalls)
        assertEquals(1, player.stopCalls)

        val line = ChannelLine(url = "http://example.com/live.m3u8")
        state.prepare(line)
        assertEquals(1, player.prepareLines.size)
        assertSame(line, player.prepareLines[0])
    }

    @Test
    fun `快进快退转发给播放器并原样传参`() {
        val player = FakeVideoPlayer()
        val state = VideoPlayerState(player)
        state.initialize()

        state.seekTo(12345L)
        state.seekBy(-10000L)
        assertEquals(listOf(12345L), player.seekToPositions)
        assertEquals(listOf(-10000L), player.seekByDeltas)
    }

    @Test
    fun `选轨与音量转发给播放器`() {
        val player = FakeVideoPlayer()
        val state = VideoPlayerState(player)
        state.initialize()

        val video = VideoPlayer.Metadata.Video(width = 1920, height = 1080)
        val audio = VideoPlayer.Metadata.Audio(channels = 6)
        val subtitle = VideoPlayer.Metadata.Subtitle(language = "en")
        state.selectVideoTrack(video)
        state.selectAudioTrack(audio)
        state.selectSubtitleTrack(subtitle)
        assertSame(video, player.selectedVideoTracks.single())
        assertSame(audio, player.selectedAudioTracks.single())
        assertSame(subtitle, player.selectedSubtitleTracks.single())

        // 音量 setter 既存 state 的值、又调 instance.setVolume；
        // 只断 state.volume 是断 backing 字段，得断 Fake 真收到了
        state.volume = 0.5f
        assertEquals(0.5f, state.volume, 0.001f)
        assertEquals(listOf(0.5f), player.setVolumes)
    }

    @Test
    fun `换台重备元数据清空、显示模式回到默认`() {
        val player = FakeVideoPlayer()
        val state = VideoPlayerState(player)
        state.initialize()

        player.emitMetadata(
            VideoPlayer.Metadata(
                video = VideoPlayer.Metadata.Video(width = 1920, height = 1080),
            ),
        )
        state.displayMode = VideoPlayerDisplayMode.FILL
        assertEquals(1920, state.metadata.video?.width)

        // 切台重备：上一路的分辨率/编解码器信息不能留着，用户中途调好
        // 的显示模式也回到设置里的默认值
        state.prepare(ChannelLine(url = "http://example.com/live.m3u8"))
        assertNull(state.metadata.video)
        assertEquals(VideoPlayerDisplayMode.ORIGINAL, state.displayMode)
    }

    @Test
    fun `换台重备显示模式回到设置里的默认值（非原始模式）`() {
        val player = FakeVideoPlayer()
        // 设置里的默认是填充模式：构造时传进来的 provider 决定默认值，
        // 不是写死的原始模式；只用默认构造测的那档分不出来
        val state = VideoPlayerState(player) { VideoPlayerDisplayMode.FILL }
        assertEquals(VideoPlayerDisplayMode.FILL, state.displayMode)

        // 用户中途切到裁剪：换台重备回到设置里的默认值（填充），
        // 而不是写死的原始模式
        state.displayMode = VideoPlayerDisplayMode.CROP
        state.prepare(ChannelLine(url = "http://example.com/live.m3u8"))
        assertEquals(VideoPlayerDisplayMode.FILL, state.displayMode)
    }

    @Test
    fun `换台重备播放状态归零（播放中、位置、时长、缓冲、错误全清）`() {
        // prepare() 只钉死了元数据与显示模式的重置，播放状态那五行
        // （isPlaying、位置、时长、缓冲、错误）一次也没断言过：上一路
        // 播到一半切台，新路没开播前进度条、总时长、缓冲圈、错误横幅
        // 全不能留着上一路的。先报一次错放最后——进缓冲会清错误，
        // 顺序反了就测不到错误那档
        val player = FakeVideoPlayer()
        val state = VideoPlayerState(player)
        state.initialize()

        player.emitIsPlaying(true)
        player.emitDuration(60000L)
        player.emitPosition(30000L, timeoutMs = 60_000L)
        player.emitBuffering(true)
        player.emitError(VideoPlayer.PlaybackException.UNSUPPORTED_TYPE)
        assertEquals(true, state.isPlaying)
        assertEquals(60000L, state.duration)
        assertEquals(30000L, state.currentPosition)
        assertEquals(true, state.isBuffering)
        assertEquals(
            VideoPlayer.PlaybackException.UNSUPPORTED_TYPE,
            state.error,
        )

        state.prepare(ChannelLine(url = "http://example.com/live.m3u8"))
        assertEquals(false, state.isPlaying)
        assertEquals(0L, state.currentPosition)
        assertEquals(0L, state.duration)
        assertEquals(false, state.isBuffering)
        assertNull(state.error)
    }

    @Test
    fun `释放后就绪与缓冲监听器不再收到通知`() {
        // release() 要清掉全部四份监听器：报错与中断那两份 929/930 轮
        // 已钉死，就绪与缓冲这两份一次也没断言过。先证活（各收到一次），
        // 再释放、再触发，次数不变——光断释放后 0 次不够，监听器压根
        // 没注册上也会是 0（930 轮教训）
        val player = FakeVideoPlayer()
        val state = VideoPlayerState(player)
        state.initialize()
        var readyCount = 0
        val bufferingSeen = mutableListOf<Boolean>()
        state.onReady { readyCount++ }
        state.onIsBuffering { bufferingSeen.add(it) }

        player.emitReady()
        assertEquals(1, readyCount)
        player.emitBuffering(true)
        assertEquals(listOf(true), bufferingSeen)

        // 退出播放页释放 state：之后底层再就绪/再进出缓冲也不应再回调
        // 旧监听器，否则已退出的界面还会收到已释放播放器的状态变化
        state.release()
        player.emitReady()
        player.emitBuffering(false)
        assertEquals(1, readyCount)
        assertEquals(listOf(true), bufferingSeen)
    }
}
