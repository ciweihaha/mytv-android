package top.yogiczy.mytv.tv.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.tv.sync.CloudSyncProvider

class ConfigsMigrationTest {
    private val builtinLive = IptvSource(
        name = "默认订阅源",
        url = "https://live.ciweihaha.win/live.m3u",
    )

    @Test
    fun resolveCloudSyncProvider_gistEmpty_forcesCloudflareRegardlessOfStoredProvider() {
        // Gist 账号空着：不管存的是 Gist、Gitee、网络链接还是别的，都改 Cloudflare
        assertEquals(
            CloudSyncProvider.CLOUDFLARE,
            resolveCloudSyncProvider(CloudSyncProvider.GITHUB_GIST, "", ""),
        )
        assertEquals(
            CloudSyncProvider.CLOUDFLARE,
            resolveCloudSyncProvider(CloudSyncProvider.GITEE_GIST, "", ""),
        )
        assertEquals(
            CloudSyncProvider.CLOUDFLARE,
            resolveCloudSyncProvider(CloudSyncProvider.NETWORK_URL, "", ""),
        )
        assertEquals(
            CloudSyncProvider.CLOUDFLARE,
            resolveCloudSyncProvider(CloudSyncProvider.CLOUDFLARE, "", ""),
        )
    }

    @Test
    fun resolveCloudSyncProvider_gistConfigured_keepsStoredProvider() {
        // 配过 Gist（Id 或 Token 任一非空）：存的提供商保持不动
        assertEquals(
            CloudSyncProvider.GITHUB_GIST,
            resolveCloudSyncProvider(CloudSyncProvider.GITHUB_GIST, "12345", ""),
        )
        assertEquals(
            CloudSyncProvider.GITHUB_GIST,
            resolveCloudSyncProvider(CloudSyncProvider.GITHUB_GIST, "", "ghp_xxx"),
        )
        assertEquals(
            CloudSyncProvider.GITEE_GIST,
            resolveCloudSyncProvider(CloudSyncProvider.GITEE_GIST, "12345", "ghp_xxx"),
        )
    }

    @Test
    fun resolveIptvSourceCurrent_legacyGiteeUrls_migratesToBuiltinLive() {
        // 旧版默认源是上游 Gitee 的 iptv-api：result.m3u 已 451，webview
        // 也是旧地址；存的当前源是这两个就迁到内置 live.m3u
        assertEquals(
            builtinLive,
            resolveIptvSourceCurrent(
                IptvSource(
                    name = "默认订阅源 iptv-api",
                    url = "https://gitee.com/mytv-android/iptv-api/raw/master/output/result.m3u",
                ),
                builtinLive,
            ),
        )
        assertEquals(
            builtinLive,
            resolveIptvSourceCurrent(
                IptvSource(
                    name = "WebView订阅源",
                    url = "https://gitee.com/mytv-android/iptv-api/raw/master/output/webview.m3u",
                ),
                builtinLive,
            ),
        )
    }

    @Test
    fun resolveIptvSourceCurrent_blankUrl_migratesToBuiltinLive() {
        assertEquals(
            builtinLive,
            resolveIptvSourceCurrent(IptvSource(name = "空地址", url = ""), builtinLive),
        )
    }

    @Test
    fun resolveIptvSourceCurrent_builtinOrCustomUrl_keepsAsIs() {
        // 已经是内置地址、或用户自己加的源：保持不动
        assertEquals(
            builtinLive,
            resolveIptvSourceCurrent(builtinLive, builtinLive),
        )
        val custom = IptvSource(name = "我的源", url = "https://example.com/tv.m3u")
        assertEquals(custom, resolveIptvSourceCurrent(custom, builtinLive))
    }
}
