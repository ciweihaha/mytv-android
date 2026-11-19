package top.yogiczy.mytv.core.data.entities.git

/**
 * git版本信息
 */
data class GitRelease(
    val version: String = "0.0.0",
    val downloadUrl: String = "",
    val description: String = "",
    /**
     * 发版附件文件名里带的内部版（如 mytv-android-tv-1.1.1.17-...-original.apk
     * -> 1.1.1.17）：RELEASE_TAG 为空（非 tag 构建）时，拿它和当前内部版比，
     * 否则只能拿 tag 的 3.0.0.N 和内部版 1.1.1.N 比、永远误报“有新版”。
     * 解析器从附件名里取不到时为空串。
     */
    val internalVersion: String = "",
)