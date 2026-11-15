package top.yogiczy.mytv.core.data.entities.actions

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyDownActionTest {
    @Test
    fun fromValue_allKnownValues_mapCorrectly() {
        // 10 个方向键动作全按 value 对上：SP 里存的整数就是这一张表
        for (action in KeyDownAction.entries) {
            assertEquals(action, KeyDownAction.fromValue(action.value))
        }
    }

    @Test
    fun fromValue_unknownValue_fallsBackToPrev() {
        // 存的值越界（旧版本删掉的动作、SP 被写坏）：兜底成“前一频道”，
        // 不能抛错、也不能悄悄变成别的动作
        assertEquals(
            KeyDownAction.ChangeCurrentChannelToPrev,
            KeyDownAction.fromValue(999),
        )
        assertEquals(
            KeyDownAction.ChangeCurrentChannelToPrev,
            KeyDownAction.fromValue(-1),
        )
        assertEquals(
            KeyDownAction.ChangeCurrentChannelToPrev,
            KeyDownAction.fromValue(10),
        )
    }
}
