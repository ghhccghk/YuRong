package top.ghhccghk.yadea.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [AppSingleChoiceDialog] 内部待选索引归一化的单元测试。
 *
 * 弹窗本体依赖两套平台弹窗宿主，不适合离屏渲染；因此这里覆盖纯逻辑部分：
 * 外部索引越界（选项列表变化、配置里的旧值失效）时弹窗必须表现为「未选中任何项」，
 * 而不是崩溃或错选。
 */
class AppSingleChoiceDialogTest {

    @Test
    fun validIndex_isKeptAsPendingSelection() {
        val options = listOf("浅色", "深色", "跟随系统")

        assertEquals(1, resolvePendingSelection(options, selectedIndex = 1))
    }

    @Test
    fun indexBeyondOptions_fallsBackToNoSelection() {
        val options = listOf("浅色", "深色")

        assertEquals(-1, resolvePendingSelection(options, selectedIndex = 2))
    }

    @Test
    fun negativeIndex_fallsBackToNoSelection() {
        val options = listOf("浅色", "深色")

        assertEquals(-1, resolvePendingSelection(options, selectedIndex = -1))
    }

    @Test
    fun emptyOptions_neverSelectsAnything() {
        assertEquals(-1, resolvePendingSelection(emptyList(), selectedIndex = 0))
    }
}