package top.yogiczy.mytv.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sentry DSN 校验：fork 的 CI 没配 DSN 时 BuildConfig 里是字面量
 * "null"，Sentry 解析它会抛异常杀死启动；没配就不初始化。
 */
class MyTVApplicationTest {
    @Test
    fun `正常 DSN 能初始化`() {
        assertTrue(isValidSentryDsn("https://abc@o0.ingest.sentry.io/123"))
    }

    @Test
    fun `空串与空白算没配`() {
        assertFalse(isValidSentryDsn(""))
        assertFalse(isValidSentryDsn("   "))
    }

    @Test
    fun `字面量 null 算没配`() {
        assertFalse(isValidSentryDsn("null"))
    }
}
