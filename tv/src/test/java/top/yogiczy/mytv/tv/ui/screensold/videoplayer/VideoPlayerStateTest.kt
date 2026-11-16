package top.yogiczy.mytv.tv.ui.screensold.videoplayer

import android.view.SurfaceView
import android.view.TextureView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        override fun prepare(line: ChannelLine) {}
        override fun play() {}
        override fun pause() {}
        override fun seekTo(position: Long) {}
        override fun seekBy(deltaMs: Long) {}
        override fun setVolume(volume: Float) {}
        override fun getVolume(): Float = 1f
        override fun selectVideoTrack(track: Metadata.Video?) {}
        override fun selectAudioTrack(track: Metadata.Audio?) {}
        override fun selectSubtitleTrack(track: Metadata.Subtitle?) {}
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
        // 否则已退出的界面还会收到已释放播放器的中断、误触换线路
        val player = FakeVideoPlayer()
        val state = VideoPlayerState(player)
        state.initialize()
        val interruptCount = java.util.concurrent.atomic.AtomicInteger(0)
        state.onInterrupt { interruptCount.incrementAndGet() }

        state.release()
        player.emitPosition(1000L, timeoutMs = 100L)
        Thread.sleep(1000)
        assertEquals(0, interruptCount.get())
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
}
