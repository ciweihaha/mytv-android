package top.yogiczy.mytv.core.data.entities.iptvsource

import kotlinx.serialization.Serializable
import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.core.data.utils.HashUtil
import java.io.File

/**
 *  直播源
 */
@Serializable
data class IptvSource(
    /**
     * 名称
     */
    val name: String = "",

    /**
     * 链接
     */
    val url: String = "",

    /**
     * 是否本地
     */
    val isLocal: Boolean = false,

    /**
     * 转换js
     */
    val transformJs: String? = null,
) {
    // 缓存文件名用 url + transformJs 的 SHA-256（64 bit）：原先用 data class 的
    // 32 位 hashCode，两个不同订阅源可能撞名、共用一个缓存文件互相覆盖。
    // 注意解析后缓存（json）是 transformJs 作用后的结果，同 url 不同 transformJs
    // 也必须分开；改名后旧缓存文件作废一次，下次拉取时重建。
    fun cacheFileName(ext: String) =
        "${cacheDir.name}/iptv_source_${HashUtil.sha256Hex("$url\u0000${transformJs ?: ""}")}.$ext"

    companion object {
        val cacheDir by lazy { File(Globals.cacheDir, "iptv_source_cache") }

        val EXAMPLE = IptvSource(
            name = "测试直播源1",
            url = "http://1.2.3.4/tv.txt",
            transformJs = "",
        )

        fun IptvSource.needExternalStoragePermission(): Boolean {
            return this.isLocal && !this.url.startsWith(Globals.fileDir.path)
        }
    }
}