package top.yogiczy.mytv.tv.ui.screen.settings.categories

import org.junit.Assert.assertEquals
import org.junit.Test
import top.yogiczy.mytv.tv.sync.CloudSyncData

class SettingsCloudSyncScreenTest {
    @Test
    fun cloudSyncDataState_fourStates() {
        assertEquals(CloudSyncDataState.LOADING, cloudSyncDataState(null, false))
        assertEquals(CloudSyncDataState.LOADING, cloudSyncDataState(null, true))
        assertEquals(CloudSyncDataState.FAILED, cloudSyncDataState(CloudSyncData.EMPTY, true))
        assertEquals(CloudSyncDataState.EMPTY, cloudSyncDataState(CloudSyncData.EMPTY, false))
        assertEquals(
            CloudSyncDataState.DATA,
            cloudSyncDataState(CloudSyncData(version = "1.2.3"), false),
        )
    }
}
