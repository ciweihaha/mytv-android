package top.yogiczy.mytv.tv.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket

class HttpServerTest {
    @Test
    fun isPortServing_listeningPort_returnsTrue() {
        // 占一个临时端口不放：isPortServing 能连上就说明“有人在听”
        ServerSocket(0).use { server ->
            assertTrue(HttpServer.isPortServing(server.localPort))
        }
    }

    @Test
    fun isPortServing_closedPort_returnsFalse() {
        // 分配一个端口就关掉：没人监听，连不上就是“服务没在跑”
        val port = ServerSocket(0).use { it.localPort }
        assertFalse(HttpServer.isPortServing(port))
    }
}
