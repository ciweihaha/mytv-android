package top.yogiczy.mytv.core.data.entities.epgsource

import kotlinx.serialization.Serializable
import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.core.data.utils.HashUtil
import java.io.File

/**
 * 节目单来源
 */
@Serializable
data class EpgSource(
    /**
     * 名称
     */
    val name: String = "",

    /**
     * 链接
     */
    val url: String = "",
) {

    // 缓存文件名用 url 的 SHA-256（64 bit）：原先用 data class 的 32 位
    // hashCode，两个不同节目单源可能撞名、共用一个缓存文件互相覆盖。
    // 改名后旧缓存文件作废一次，下次拉取时重建。
    fun cacheFileName(ext: String) =
        "${cacheDir.name}/epg_source_${HashUtil.sha256Hex(url)}.$ext"

    companion object {
        val cacheDir by lazy { File(Globals.cacheDir, "epg_source_cache") }

        val EXAMPLE = EpgSource(
            name = "测试节目单1",
            url = "http://1.2.3.4/all.xml",
        )
    }
}