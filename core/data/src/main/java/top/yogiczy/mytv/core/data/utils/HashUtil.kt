package top.yogiczy.mytv.core.data.utils

import java.security.MessageDigest

object HashUtil {
    /**
     * SHA-256 十六进制摘要，默认取前 16 位（64 bit）。
     * 用于缓存文件名：32 位 hashCode 碰撞概率不可忽略，两个不同订阅源
     * 共用一个缓存文件时会互相覆盖、读到对方的频道/节目单。
     */
    fun sha256Hex(input: String, length: Int = 16): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(length)
    }
}
