package top.yogiczy.mytv.tv.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudSyncProviderTest {
    @Test
    fun fromValue_allKnownValues_mapCorrectly() {
        // 6 个云同步方式全按 value 对上：SP 里存的整数就是这一张表
        for (provider in CloudSyncProvider.entries) {
            assertEquals(provider, CloudSyncProvider.fromValue(provider.value))
        }
    }

    @Test
    fun fromValue_unknownValue_fallsBackToGithubGist() {
        // 存的值越界（旧版本删掉的方式、SP 被写坏）：兜底成 GitHub Gist，
        // 不能抛错、也不能悄悄变成别的同步方式
        assertEquals(
            CloudSyncProvider.GITHUB_GIST,
            CloudSyncProvider.fromValue(999),
        )
        assertEquals(
            CloudSyncProvider.GITHUB_GIST,
            CloudSyncProvider.fromValue(-1),
        )
        assertEquals(
            CloudSyncProvider.GITHUB_GIST,
            CloudSyncProvider.fromValue(6),
        )
    }
}
