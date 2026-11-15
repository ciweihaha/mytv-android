package top.yogiczy.mytv.core.data.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class LruMutableCacheTest {
    @Test
    fun getOrPut_miss_computesAndCaches() {
        val cache = LruMutableCache<String, String>(4)
        var computeCount = 0

        val first = cache.getOrPut("cctv1") {
            computeCount++
            "CCTV-1"
        }
        val second = cache.getOrPut("cctv1") {
            computeCount++
            "CCTV-1"
        }

        assertEquals("CCTV-1", first)
        assertEquals("CCTV-1", second)
        assertEquals(1, computeCount)
    }

    @Test
    fun getOrPut_differentKeys_computedSeparately() {
        val cache = LruMutableCache<String, String>(4)
        var computeCount = 0

        assertEquals("CCTV-1", cache.getOrPut("cctv1") { computeCount++; "CCTV-1" })
        assertEquals("CCTV-2", cache.getOrPut("cctv2") { computeCount++; "CCTV-2" })
        assertEquals("CCTV-1", cache.getOrPut("cctv1") { computeCount++; "CCTV-1" })
        assertEquals(2, computeCount)
    }

    @Test
    fun getOrPut_exceedsMaxSize_evictsLeastRecentlyUsed() {
        val cache = LruMutableCache<String, String>(2)
        var computeCount = 0

        cache.getOrPut("a") { computeCount++; "A" }
        cache.getOrPut("b") { computeCount++; "B" }
        cache.getOrPut("c") { computeCount++; "C" }

        // "a" 是最久未用的，已被淘汰，再取要重新算
        assertEquals("A", cache.getOrPut("a") { computeCount++; "A" })
        assertEquals(4, computeCount)
        // 重新放回 "a" 时把当时最久未用的 "b" 挤掉了，"b" 再取也要重新算
        assertEquals("B", cache.getOrPut("b") { computeCount++; "B" })
        assertEquals(5, computeCount)
        // "a" 仍在缓存里，不再算
        assertEquals("A", cache.getOrPut("a") { computeCount++; "A" })
        assertEquals(5, computeCount)
    }

    @Test
    fun getOrPut_hit_refreshesRecency() {
        val cache = LruMutableCache<String, String>(2)
        var computeCount = 0

        cache.getOrPut("a") { computeCount++; "A" }
        cache.getOrPut("b") { computeCount++; "B" }
        // 再取一次 "a"，把它变成最近用的
        assertEquals("A", cache.getOrPut("a") { computeCount++; "A" })
        // 新放 "c"，淘汰的应是 "b" 而不是 "a"
        cache.getOrPut("c") { computeCount++; "C" }

        assertEquals("A", cache.getOrPut("a") { computeCount++; "A" })
        assertEquals("B", cache.getOrPut("b") { computeCount++; "B" })
        assertEquals(4, computeCount)
    }
}
