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
}
