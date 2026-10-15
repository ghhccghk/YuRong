package top.ghhccghk.yadea.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证搜索框的关键字匹配规则：忽略大小写与首尾空白，空关键字放行全部条目。
 */
class AppSearchBarTest {
    @Test
    fun blankQueryMatchesEverything() {
        // 空关键字与纯空白关键字都应直接放行，调用方无需额外判空。
        assertTrue(matchesSearchQuery("", "yadea-x1", "AA:BB:CC"))
        assertTrue(matchesSearchQuery("   ", "yadea-x1", "AA:BB:CC"))
        assertTrue(matchesSearchQuery("", null))
    }

    @Test
    fun matchesAnyFieldIgnoringCase() {
        assertTrue(matchesSearchQuery("yad", "Yadea-X1", "AA:BB:CC"))
        assertTrue(matchesSearchQuery("YAD", "yadea-x1", null))
    }

    @Test
    fun matchesAddressWhenNameIsMissing() {
        // BLE 设备可能没有名称，此时仍应能按地址搜索。
        assertTrue(matchesSearchQuery("aa:bb", null, "AA:BB:CC:DD"))
        assertFalse(matchesSearchQuery("仪表", null, "AA:BB:CC:DD"))
    }

    @Test
    fun trimsKeywordBeforeMatching() {
        assertTrue(matchesSearchQuery("  cc:dd  ", null, "AA:BB:CC:DD"))
    }

    @Test
    fun returnsFalseWhenNothingMatches() {
        assertFalse(matchesSearchQuery("p2p", "yadea-x1", "AA:BB:CC"))
        assertFalse(matchesSearchQuery("yadea", null, null))
    }
}