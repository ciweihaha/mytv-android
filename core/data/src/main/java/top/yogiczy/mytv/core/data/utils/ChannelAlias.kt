package top.yogiczy.mytv.core.data.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.core.data.R
import java.io.File
import java.util.Locale
import kotlin.time.measureTimedValue

object ChannelAlias : Loggable("ChannelAlias") {
    val aliasFile by lazy { File(Globals.fileDir, "channel_name_alias.json") }

    private var _aliasMap = mapOf<String, List<String>>()
    val aliasMap get() = _aliasMap

    private val nameCache = LruMutableCache<String, String>(128)

    // 别名查表：旧的 findAliasName 每次缓存未命中都要把两张映射表线性扫四遍
    //（自定义键、自定义别名、默认键、默认别名，共 189 组、2049 个别名），
    // 大源解析时每个不重名频道都要扫一次；现在建成小写查表，一次 O(1)。
    // 自定义映射表 refresh() 会换，查表跟着失效重建。
    @Volatile
    private var aliasLookup: Map<String, String>? = null

    @Volatile
    private var normalizedSuffixesCache: List<String>? = null

    suspend fun refresh() = withContext(Dispatchers.IO) {
        nameCache.evictAll()
        _aliasMap = runCatching {
            Globals.json.decodeFromString<Map<String, List<String>>>(aliasFile.readText())
        }.getOrElse { emptyMap() }
        // 先换表再失效：失效放前面，并发解析可能把旧表建成查表缓存下来
        aliasLookup = null
        normalizedSuffixesCache = null
        log.d("加载自定义频道名映射表完成，共 ${_aliasMap.values.sumOf { it.size }} 个映射")
    }

    fun standardChannelName(name: String): String {
        return nameCache.getOrPut(name) {
            measureTimedValue {
                val normalizedSuffixes = getNormalizedSuffixes()
                val nameWithoutSuffix = stripChannelNameSuffixes(name, normalizedSuffixes)

                findAliasName(nameWithoutSuffix) ?: nameWithoutSuffix
            }.let {
                if (it.value != name) {
                    log.d(
                        "standardChannelName(${nameCache.size()}): $name -> ${it.value}",
                        null,
                        it.duration
                    )
                }
                it.value
            }
        }
    }

    private fun getNormalizedSuffixes(): List<String> {
        return normalizedSuffixesCache
            ?: (_aliasMap.getOrElse("__suffix") { emptyList() } + defaultAlias.getOrElse("__suffix") { emptyList() })
                .also { normalizedSuffixesCache = it }
    }

    /**
     * 反复去掉名字尾部的质量后缀，直到去无可去：后缀表只扫一遍时，
     * `CCTV-1-4K-HD` 先去掉 `-HD` 露出 `-4K`，而 `-4K` 在表里排在
     * 前面已扫过，会残留 `-4K`，标准名错导致 EPG 匹配不上
     */
    internal fun stripChannelNameSuffixes(name: String, suffixes: List<String>): String {
        var result = name
        var changed: Boolean
        do {
            changed = false
            for (suffix in suffixes) {
                val stripped = result.removeSuffix(suffix)
                if (stripped != result) {
                    result = stripped
                    changed = true
                }
            }
        } while (changed)
        return result.trim()
    }

    private fun findAliasName(name: String): String? {
        return getAliasLookup()[name.lowercase(Locale.ROOT)]
    }

    private fun getAliasLookup(): Map<String, String> {
        return aliasLookup ?: buildAliasLookup(_aliasMap, defaultAlias).also { aliasLookup = it }
    }

    /**
     * 把两张映射表建成小写查表（小写名/别名 -> 标准名）。
     * 优先级与旧的线性扫描一致：自定义键、自定义别名、默认键、默认别名，
     * 先到的赢；小写比较与忽略大小写比较在频道名（ASCII + 中文）上等价。
     */
    internal fun buildAliasLookup(
        customMap: Map<String, List<String>>,
        defaultMap: Map<String, List<String>>,
    ): Map<String, String> {
        val lookup = LinkedHashMap<String, String>()
        for (key in customMap.keys) {
            lookup.putIfAbsent(key.lowercase(Locale.ROOT), key)
        }
        for ((key, aliases) in customMap) {
            for (alias in aliases) {
                lookup.putIfAbsent(alias.lowercase(Locale.ROOT), key)
            }
        }
        for (key in defaultMap.keys) {
            lookup.putIfAbsent(key.lowercase(Locale.ROOT), key)
        }
        for ((key, aliases) in defaultMap) {
            for (alias in aliases) {
                lookup.putIfAbsent(alias.lowercase(Locale.ROOT), key)
            }
        }
        return lookup
    }

    private val defaultAlias by lazy {
        Globals.json.decodeFromString<Map<String, List<String>>>(
            Globals.resources.openRawResource(R.raw.channel_name_alias).bufferedReader()
                .use { it.readText() }).also { map ->
            log.d("加载默认频道名映射表完成，共 ${map.values.sumOf { it.size }} 个映射")
        }
    }
}
