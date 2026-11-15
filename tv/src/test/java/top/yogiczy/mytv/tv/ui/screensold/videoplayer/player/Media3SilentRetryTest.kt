package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player

import androidx.media3.common.PlaybackException as Media3PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
