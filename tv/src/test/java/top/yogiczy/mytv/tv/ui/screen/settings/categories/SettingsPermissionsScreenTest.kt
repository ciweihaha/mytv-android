package top.yogiczy.mytv.tv.ui.screen.settings.categories

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPermissionsScreenTest {
    @Test
    fun permissionStatusIcon_bothStates() {
        assertEquals(Icons.Outlined.CheckCircle, permissionStatusIcon(true))
        assertEquals(Icons.Outlined.Cancel, permissionStatusIcon(false))
    }
}
