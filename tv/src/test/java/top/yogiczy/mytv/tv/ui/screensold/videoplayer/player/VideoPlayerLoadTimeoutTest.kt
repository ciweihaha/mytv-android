package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player

import android.view.SurfaceView
import android.view.TextureView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.ChannelLine

/**
 * 加载超时（天光 1833 轮姊妹检查）：切台/重试会再次 prepare，
 * 旧那次挂起的加载超时必须取消，否则旧超时会往新流里报一次
 * 多余的 LOAD_TIMEOUT。
 *
 * 超时时间走参数传（默认读设置里的加载超时）：单测里没有
 * Android 环境，读设置会去碰 SP，传 100 毫秒就测得到。
 */
class VideoPlayerLoadTimeoutTest {

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

        fun emitPrepared(timeoutMs: Long) = triggerPrepared(timeoutMs)
        fun emitPosition(position: Long, timeoutMs: Long) =
            triggerCurrentPosition(position, timeoutMs)
        fun emitError(error: PlaybackException?) = triggerError(error)
        fun emitReady() = triggerReady()
    }

    @Test
    fun `单次 prepare 超时只报一次 LOAD_TIMEOUT`() {
        val player = FakeVideoPlayer()
        val errors = mutableListOf<VideoPlayer.PlaybackException>()
        player.onError { it?.let { errors.add(it) } }

        player.emitPrepared(timeoutMs = 100L)

        Thread.sleep(1000)
        assertEquals(
            listOf(VideoPlayer.PlaybackException.LOAD_TIMEOUT),
            errors,
        )
    }

    @Test
    fun `重复 prepare 取消旧加载超时、只报一次 LOAD_TIMEOUT`() {
        val player = FakeVideoPlayer()
        val errors = mutableListOf<VideoPlayer.PlaybackException>()
        player.onError { it?.let { errors.add(it) } }

        // 切台/重试会再次 prepare：旧那次挂起的加载超时必须取消，
        // 否则旧超时会往新流里报一次多余的 LOAD_TIMEOUT
        player.emitPrepared(timeoutMs = 100L)
        Thread.sleep(50)
        player.emitPrepared(timeoutMs = 100L)

        Thread.sleep(1000)
        assertEquals(
            listOf(VideoPlayer.PlaybackException.LOAD_TIMEOUT),
            errors,
        )
    }

    @Test
    fun `换台重备取消挂起的断流计时，旧流的卡住不报中断`() {
        // triggerPrepared 里 interruptJob?.cancel() 那行：上一路流卡住、
        // 断流计时挂起时用户换了台，重备必须把旧计时停掉，否则旧流的
        // 卡住会在超时后往新流里报一次中断、主界面无故换一次线路。
        // 加载超时那半（旧 LOAD_TIMEOUT 取消）上面两个用例已盖，
        // 这里只断中断计时这半
        val player = FakeVideoPlayer()
        val interruptCount = java.util.concurrent.atomic.AtomicInteger(0)
        player.onInterrupt { interruptCount.incrementAndGet() }

        player.emitPosition(1000L, timeoutMs = 200L)
        player.emitPrepared(timeoutMs = 10_000L)

        Thread.sleep(1000)
        assertEquals(0, interruptCount.get())
    }

    @Test
    fun `重备后新位置照常重设断流计时`() {
        // 取消旧计时不等于关掉中断：新流位置一动，计时照常重新开始，
        // 新流卡住还是要报
        val player = FakeVideoPlayer()
        val interruptCount = java.util.concurrent.atomic.AtomicInteger(0)
        player.onInterrupt { interruptCount.incrementAndGet() }

        player.emitPosition(1000L, timeoutMs = 200L)
        player.emitPrepared(timeoutMs = 10_000L)
        player.emitPosition(2000L, timeoutMs = 100L)

        Thread.sleep(1000)
        assertEquals(1, interruptCount.get())
    }

    @Test
    fun `停止取消挂起的加载超时，不再报 LOAD_TIMEOUT`() {
        // stop() 里 loadTimeoutJob?.cancel() 那行：用户手动停掉播放时，
        // 挂起的加载超时必须一起停，否则旧超时会在 100ms 后往已停的流
        // 里报一次多余的 LOAD_TIMEOUT。重复 prepare 取消那档上面已盖，
        // 这里只断停止这条取消路径；计时本身会到由上面第一个用例证活
        val player = FakeVideoPlayer()
        val errors = mutableListOf<VideoPlayer.PlaybackException>()
        player.onError { it?.let { errors.add(it) } }

        player.emitPrepared(timeoutMs = 100L)
        player.stop()

        Thread.sleep(1000)
        assertEquals(emptyList<VideoPlayer.PlaybackException>(), errors)
    }

    @Test
    fun `停止取消挂起的断流计时，不再报中断`() {
        // stop() 里 interruptJob?.cancel() 那行：同上，断流计时那半。
        // 计时本身会到由 VideoPlayerStateTest 里“位置不动断流计时到期
        // 触发一次”证活
        val player = FakeVideoPlayer()
        val interruptCount = java.util.concurrent.atomic.AtomicInteger(0)
        player.onInterrupt { interruptCount.incrementAndGet() }

        player.emitPosition(1000L, timeoutMs = 200L)
        player.stop()

        Thread.sleep(1000)
        assertEquals(0, interruptCount.get())
    }

    @Test
    fun `空错误不取消加载超时，超时照常报`() {
        // 天光 1833 轮姊妹检查：进缓冲时底层报一次空错误清显示——
        // 空错误只是“开始缓冲”的信号，不能取消加载超时，否则卡在
        // 缓冲的流永远等不到超时、永远不自动换线路
        val player = FakeVideoPlayer()
        val errors = mutableListOf<VideoPlayer.PlaybackException>()
        player.onError { it?.let { errors.add(it) } }

        player.emitPrepared(timeoutMs = 100L)
        player.emitError(null)

        Thread.sleep(1000)
        assertEquals(
            listOf(VideoPlayer.PlaybackException.LOAD_TIMEOUT),
            errors,
        )
    }

    @Test
    fun `真报错取消加载超时，不再报 LOAD_TIMEOUT`() {
        // 天光 1833 轮姊妹检查：真报错来了，挂起的加载超时必须停，
        // 否则报错后面还会再跟一次多余的 LOAD_TIMEOUT
        val player = FakeVideoPlayer()
        val errors = mutableListOf<VideoPlayer.PlaybackException>()
        player.onError { it?.let { errors.add(it) } }

        player.emitPrepared(timeoutMs = 100L)
        player.emitError(VideoPlayer.PlaybackException.UNSUPPORTED_TYPE)

        Thread.sleep(1000)
        assertEquals(
            listOf(VideoPlayer.PlaybackException.UNSUPPORTED_TYPE),
            errors,
        )
    }

    @Test
    fun `就绪取消加载超时，不再报 LOAD_TIMEOUT`() {
        // 天光 1833 轮姊妹检查：流播稳了（onReady），挂起的加载超时
        // 必须停，否则播着播着会在超时后误报一次 LOAD_TIMEOUT
        val player = FakeVideoPlayer()
        val errors = mutableListOf<VideoPlayer.PlaybackException>()
        player.onError { it?.let { errors.add(it) } }

        player.emitPrepared(timeoutMs = 100L)
        player.emitReady()

        Thread.sleep(1000)
        assertEquals(emptyList<VideoPlayer.PlaybackException>(), errors)
    }
}
