package top.yogiczy.mytv.tv.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.tv.sync.CloudSyncProvider

/**
 * 云端同步脱敏：上传/拉取的配置里，本机才有的项要清掉、同步凭证与
 * 普通配置要留着。desensitized() 是决定什么东西离机进云端 gist 的
 * 一道关，之前一次也没测过——改漏一项，旧单测全过、发现不了。
 * 注意：同步凭证（gist token、WebDAV 密码等）是特意留着的——
 * 生产代码里这几行是注释掉不脱敏的，新设备靠它们才能去拉同步数据；
 * 这是现有取舍，不是漏网，改它要另作决定。
 */
class ConfigsPartialTest {

    @Test
    fun `脱敏清掉本机项、留住同步凭证与普通配置`() {
        val partial = Configs.Partial(
            appBootLaunch = true,
            appLastLatestVersion = "1.1.1.100",
            iptvChannelLastPlay = Channel(name = "央视一套"),
            iptvChannelLinePlayableHostList = setOf("example.com"),
            iptvChannelLinePlayableUrlList = setOf("https://example.com/live.m3u8"),
            uiFocusOptimize = true,
            videoPlayerCore = Configs.VideoPlayerCore.MEDIA3,
            webViewCore = Configs.WebViewCore.SYSTEM,
            videoPlayerForceSoftDecode = true,
            cloudSyncAutoPull = true,
            cloudSyncProvider = CloudSyncProvider.GITHUB_GIST,
            cloudSyncGithubGistId = "gist-id",
            cloudSyncGithubGistToken = "token-123",
            cloudSyncGiteeGistId = "gitee-id",
            cloudSyncGiteeGistToken = "gitee-token",
            cloudSyncNetworkUrl = "https://example.com/sync.json",
            cloudSyncLocalFilePath = "/sdcard/sync.json",
            cloudSyncWebDavUrl = "https://dav.example.com/",
            cloudSyncWebDavUsername = "user",
            cloudSyncWebDavPassword = "pw-123",
            cloudSyncCloudflareUrl = "https://mytv-sync.ciweihaha.win/sync",
            networkRetryCount = 3L,
        )

        val result = partial.desensitized()

        // 本机才有的项：清掉，不跟去别的设备
        assertNull(result.iptvChannelLastPlay)
        assertNull(result.iptvChannelLinePlayableHostList)
        assertNull(result.iptvChannelLinePlayableUrlList)
        assertNull(result.uiFocusOptimize)
        assertNull(result.videoPlayerCore)
        assertNull(result.webViewCore)
        assertNull(result.videoPlayerForceSoftDecode)
        assertNull(result.cloudSyncAutoPull)
        assertNull(result.cloudSyncProvider)

        // 同步凭证：留着——新设备靠它们才能去拉同步数据
        assertEquals("gist-id", result.cloudSyncGithubGistId)
        assertEquals("token-123", result.cloudSyncGithubGistToken)
        assertEquals("gitee-id", result.cloudSyncGiteeGistId)
        assertEquals("gitee-token", result.cloudSyncGiteeGistToken)
        assertEquals("https://example.com/sync.json", result.cloudSyncNetworkUrl)
        assertEquals("/sdcard/sync.json", result.cloudSyncLocalFilePath)
        assertEquals("https://dav.example.com/", result.cloudSyncWebDavUrl)
        assertEquals("user", result.cloudSyncWebDavUsername)
        assertEquals("pw-123", result.cloudSyncWebDavPassword)
        assertEquals("https://mytv-sync.ciweihaha.win/sync", result.cloudSyncCloudflareUrl)

        // 普通配置：原样保留
        assertEquals(true, result.appBootLaunch)
        assertEquals("1.1.1.100", result.appLastLatestVersion)
        assertEquals(3L, result.networkRetryCount)
    }
}
