package top.yogiczy.mytv.tv.ui.screensold.videoplayer.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 播放失败提示的错误码文案：getErrorCodeDesc 是界面第三行的中文说明，
 * 之前一次也没测过——39 个码全靠手写 when 分支，写错一个码、漏一个
 * 分支，构建全过、只有播失败时才看得出来。
 * 注意：未知码回 null 是有意的——界面第二行已经显示过原文了，
 * 未知码再补第三行会把同一串连着显示两遍。
 */
class VideoPlayerErrorTest {

    @Test
    fun `已知错误码带括号后缀返回对应中文说明`() {
        // 播放器报的错常带 (数字) 后缀，先按 ( 截断再查表
        assertEquals(
            "加载超时，请检查网络连接或视频源。",
            getErrorCodeDesc("ERROR_LOAD_TIMEOUT(123)"),
        )
        assertEquals(
            "网络连接异常或不支持IPv6，请检查网络设置。",
            getErrorCodeDesc("MEDIA3_ERROR_IO_NETWORK_CONNECTION_FAILED(2001)"),
        )
        assertEquals(
            "DRM设备被吊销，请检查视频源。",
            getErrorCodeDesc("MEDIA3_ERROR_DRM_DEVICE_REVOKED(6006)"),
        )
    }

    @Test
    fun `已知错误码不带括号也返回对应说明`() {
        assertEquals(
            "不支持的视频类型，请检查视频源。",
            getErrorCodeDesc("ERROR_UNSUPPORTED_TYPE"),
        )
        assertEquals(
            "视频解码格式超出能力范围，请更换设备。",
            getErrorCodeDesc("MEDIA3_ERROR_DECODING_FORMAT_EXCEEDS_CAPABILITIES"),
        )
    }

    @Test
    fun `全部39个已知错误码都返回对应的中文说明`() {
        val expected = mapOf(
            "ERROR_UNSUPPORTED_TYPE" to "不支持的视频类型，请检查视频源。",
            "ERROR_LOAD_TIMEOUT" to "加载超时，请检查网络连接或视频源。",
            "MEDIA3_ERROR_UNSPECIFIED" to "未知错误，请稍后再试。",
            "MEDIA3_ERROR_REMOTE_ERROR" to "远程错误，请稍后再试。",
            "MEDIA3_ERROR_BEHIND_LIVE_WINDOW" to "实时窗口错误，请稍后再试。",
            "MEDIA3_ERROR_TIMEOUT" to "超时错误，请稍后再试。",
            "MEDIA3_ERROR_IO_UNSPECIFIED" to "IO错误，请稍后再试。",
            "MEDIA3_ERROR_IO_NETWORK_CONNECTION_FAILED" to "网络连接异常或不支持IPv6，请检查网络设置。",
            "MEDIA3_ERROR_IO_NETWORK_CONNECTION_TIMEOUT" to "网络连接超时，请检查网络设置。",
            "MEDIA3_ERROR_IO_INVALID_HTTP_CONTENT_TYPE" to "HTTP请求返回错误类型，请检查视频源。",
            "MEDIA3_ERROR_IO_BAD_HTTP_STATUS" to "HTTP请求返回错误状态，请检查视频源。",
            "MEDIA3_ERROR_IO_FILE_NOT_FOUND" to "视频文件未找到，请检查视频源。",
            "MEDIA3_ERROR_IO_NO_PERMISSION" to "没有权限访问视频文件，请检查权限设置。",
            "MEDIA3_ERROR_IO_CLEARTEXT_NOT_PERMITTED" to "不允许使用明文访问视频文件，请检查权限设置。",
            "MEDIA3_ERROR_IO_READ_POSITION_OUT_OF_RANGE" to "视频文件读取位置超出范围，请检查视频源。",
            "MEDIA3_ERROR_PARSING_CONTAINER_MALFORMED" to "视频容器格式错误，请检查视频源。",
            "MEDIA3_ERROR_PARSING_MANIFEST_MALFORMED" to "视频流清单格式错误，请检查视频源。",
            "MEDIA3_ERROR_PARSING_CONTAINER_UNSUPPORTED" to "不支持的视频容器类型，请检查视频源。",
            "MEDIA3_ERROR_PARSING_MANIFEST_UNSUPPORTED" to "不支持的视频流清单类型，请检查视频源。",
            "MEDIA3_ERROR_DECODER_INIT_FAILED" to "视频解码器初始化失败，请检查视频源。",
            "MEDIA3_ERROR_DECODER_QUERY_FAILED" to "视频解码器查询失败，请检查视频源。",
            "MEDIA3_ERROR_DECODING_FAILED" to "视频解码失败，请检查视频源。",
            "MEDIA3_ERROR_DECODING_FORMAT_EXCEEDS_CAPABILITIES" to "视频解码格式超出能力范围，请更换设备。",
            "MEDIA3_ERROR_DECODING_FORMAT_UNSUPPORTED" to "不支持的视频解码格式，请检查视频源。",
            "MEDIA3_ERROR_DECODING_RESOURCES_RECLAIMED" to "视频解码资源被回收，请检查视频源。",
            "MEDIA3_ERROR_AUDIO_TRACK_INIT_FAILED" to "音频轨道初始化失败，请检查视频源。",
            "MEDIA3_ERROR_AUDIO_TRACK_WRITE_FAILED" to "音频轨道写入失败，请检查视频源。",
            "MEDIA3_ERROR_AUDIO_TRACK_OFFLOAD_WRITE_FAILED" to "音频轨道离线写入失败，请检查视频源。",
            "MEDIA3_ERROR_AUDIO_TRACK_OFFLOAD_INIT_FAILED" to "音频轨道离线初始化失败，请检查视频源。",
            "MEDIA3_ERROR_VIDEO_FRAME_PROCESSOR_INIT_FAILED" to "视频帧处理器初始化失败，请检查视频源。",
            "MEDIA3_ERROR_VIDEO_FRAME_PROCESSING_FAILED" to "视频帧处理失败，请检查视频源。",
            "MEDIA3_ERROR_FAILED_RUNTIME_CHECK" to "运行时检查失败，请检查视频源。",
            "MEDIA3_ERROR_DRM_UNSPECIFIED" to "DRM错误，请检查视频源。",
            "MEDIA3_ERROR_DRM_CONTENT_ERROR" to "DRM密钥格式错误，请检查视频源。",
            "MEDIA3_ERROR_DRM_LICENSE_ACQUISITION_FAILED" to "DRM授权失败，请检查视频源。",
            "MEDIA3_ERROR_DRM_DISALLOWED_OPERATION" to "DRM不允许的操作，请检查视频源。",
            "MEDIA3_ERROR_DRM_SYSTEM_ERROR" to "DRM系统错误，请检查视频源。",
            "MEDIA3_ERROR_DRM_DEVICE_REVOKED" to "DRM设备被吊销，请检查视频源。",
            "MEDIA3_ERROR_DRM_LICENSE_EXPIRED" to "DRM授权已过期，请检查视频源。",
        )
        assertEquals(39, expected.size)
        for ((code, desc) in expected) {
            assertEquals(code, desc, getErrorCodeDesc(code))
        }
    }

    @Test
    fun `未知错误码返回空不重复显示原文`() {
        assertNull(getErrorCodeDesc("SOME_UNKNOWN_ERROR"))
        assertNull(getErrorCodeDesc("SOME_UNKNOWN_ERROR(9999)"))
        // 大小写敏感：小写码不在表里，同样回空
        assertNull(getErrorCodeDesc("media3_error_timeout"))
    }

    @Test
    fun `空串返回空`() {
        assertNull(getErrorCodeDesc(""))
    }
}
