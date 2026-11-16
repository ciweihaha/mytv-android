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
        fun emitPosition(position: Long) = triggerCurrentPosition(position)
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
