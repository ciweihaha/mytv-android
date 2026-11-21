package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player

import androidx.media3.common.PlaybackException as Media3PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yogiczy.mytv.core.data.utils.Constants

/**
 * 无声重试的错误码分类：2004/2001/2002 是瞬时网络错，同一条线路
 * 重试 3 次；超限才上报原始错误走换线。数值断言是特意写死的——
 * 之前天光云影的测试就把连接失败/超时写成了 2007/2008，实际是
 * 2001/2002，错了要立刻被测出来。
 */
class Media3SilentRetryTest {
    @Test
    fun `2004 是瞬时网络错`() {
        assertEquals(2004, Media3PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
        assertTrue(isSilentRetryError(Media3PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
    }

    @Test
    fun `2001 和 2002 是瞬时网络错`() {
        assertEquals(2001, Media3PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        assertEquals(2002, Media3PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
        assertTrue(isSilentRetryError(Media3PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertTrue(isSilentRetryError(Media3PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
    }

    @Test
    fun `3001 容器损坏是无声重试错误`() {
        // 天光 v2.2.40 同款判断：分片 HTTP 200、长度完整、但 PES 层损坏，
        // 播放器一个轨道都解析不出来、报 3001；坏窗口是分钟级的，重拉拿新
        // 节目单后新分片大概率已干净。壳侧没有天光的“耗尽后自动重调”，走
        // 已有的无声重试这条路。码值锁死 3001：就是 logcat 里那个
        // ERROR_CODE_PARSING_CONTAINER_MALFORMED(3001)。
        assertEquals(3001, Media3PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED)
        assertTrue(isSilentRetryError(Media3PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED))
    }

    @Test
    fun `原有三类错误仍无声重试`() {
        assertTrue(isSilentRetryError(Media3PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW))
        assertTrue(isSilentRetryError(Media3PlaybackException.ERROR_CODE_DECODING_FAILED))
        assertTrue(isSilentRetryError(Media3PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
    }

    @Test
    fun `其他错误不无声重试`() {
        assertFalse(isSilentRetryError(Media3PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED))
        assertFalse(isSilentRetryError(Media3PlaybackException.ERROR_CODE_DECODER_INIT_FAILED))
        assertFalse(isSilentRetryError(Media3PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED))
    }

    @Test
    fun `第 1 次重试等 1 秒、第 2 次 2 秒、第 3 次 3 秒`() {
        assertEquals(1000L, silentRetryDelayMs(1))
        assertEquals(2000L, silentRetryDelayMs(2))
        assertEquals(3000L, silentRetryDelayMs(3))
    }

    @Test
    fun `还没重试时不等待`() {
        assertEquals(0L, silentRetryDelayMs(0))
    }

    @Test
    fun `2004 最多无声重试 3 次`() {
        assertTrue(shouldSilentRetry(Media3PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 0))
        assertTrue(shouldSilentRetry(Media3PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 1))
        assertTrue(shouldSilentRetry(Media3PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 2))
        assertFalse(shouldSilentRetry(Media3PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 3))
    }

    @Test
    fun `3001 最多无声重试 3 次`() {
        // 坏窗口是分钟级的、3 次退避（1/2/3 秒）未必能等过去：3 次都还坏
        // 就上报原始错误走换线，不循环；只有一条线路的台才会在此挂住，
        // 和其他无声重试错误同一口径
        assertTrue(shouldSilentRetry(Media3PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, 0))
        assertTrue(shouldSilentRetry(Media3PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, 1))
        assertTrue(shouldSilentRetry(Media3PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, 2))
        assertFalse(shouldSilentRetry(Media3PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, 3))
    }

    @Test
    fun `非无声重试错误一次也不重试`() {
        assertFalse(shouldSilentRetry(Media3PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, 0))
        assertFalse(shouldSilentRetry(Media3PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, 2))
    }

    @Test
    fun `BEHIND_LIVE_WINDOW 连报三次都重试、第四次上报`() {
        val errorCode = Media3PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
        var retries = 0
        repeat(3) {
            assertTrue(shouldSilentRetry(errorCode, retries))
            retries++
        }
        assertFalse(shouldSilentRetry(errorCode, retries))
    }

    @Test
    fun `加载超时默认 5 秒`() {
        // 分支修法把加载超时默认从 15 秒改成 5 秒：改回去会让
        // LOAD_TIMEOUT 的触发点悄悄变了，重试节奏跟着变
        assertEquals(5000L, Constants.VIDEO_PLAYER_LOAD_TIMEOUT)
    }

    @Test
    fun `单次退避最长 3 秒、短于 5 秒加载超时`() {
        // 退避期间旧的 watchdog 还在跑（重试只在 prepare() 后才调
        // triggerPrepared 重启它）：单次退避必须短于加载超时，否则报错
        // 一来就撞上旧 watchdog、连第 1 次退避都等不完。注意这只保“报错
        // 刚发生时”那档——报错来得晚、旧 watchdog 剩下的时间比本次退避
        // 短，照样先报假 LOAD_TIMEOUT（1036 轮）。
        assertTrue(silentRetryDelayMs(1) < Constants.VIDEO_PLAYER_LOAD_TIMEOUT)
        assertTrue(silentRetryDelayMs(2) < Constants.VIDEO_PLAYER_LOAD_TIMEOUT)
        assertTrue(silentRetryDelayMs(3) < Constants.VIDEO_PLAYER_LOAD_TIMEOUT)
    }
}
