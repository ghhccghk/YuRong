package top.ghhccghk.yadea.ui

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [AppHaptics] 的单元测试。
 *
 * 触觉反馈本身在 JVM 上不可观测，因此这里断言的是「业务语义 -> 平台反馈类型」的映射，
 * 保证新增语义时不会出现两个语义落到同一种反馈，或漏掉某个平台的实现。
 */
class AppHapticsTest {

    /** 记录全部触觉调用的假实现，替代各平台的真机实现，使映射可被断言。 */
    private class RecordingHapticFeedback : HapticFeedback {
        val performed = mutableListOf<HapticFeedbackType>()

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            performed += hapticFeedbackType
        }
    }

    @Test
    fun everyKind_mapsToItsOwnPlatformType() {
        val mapped = AppHapticKind.entries.map { it.toPlatformType() }

        // 语义与平台反馈一一对应，避免两种业务语义振动出同一种手感。
        assertEquals(AppHapticKind.entries.size, mapped.toSet().size)
    }

    @Test
    fun perform_forwardsTheMappedTypeInOrder() {
        val recorder = RecordingHapticFeedback()
        val haptics = AppHaptics(recorder)

        AppHapticKind.entries.forEach { haptics.perform(it) }

        assertEquals(AppHapticKind.entries.map { it.toPlatformType() }, recorder.performed)
    }

    @Test
    fun toggle_usesDirectionalTypes() {
        val recorder = RecordingHapticFeedback()
        val haptics = AppHaptics(recorder)

        haptics.toggle(checked = true)
        haptics.toggle(checked = false)

        assertEquals(
            listOf(HapticFeedbackType.ToggleOn, HapticFeedbackType.ToggleOff),
            recorder.performed,
        )
    }

    @Test
    fun convenienceHelpers_matchTheirSemantics() {
        val recorder = RecordingHapticFeedback()
        val haptics = AppHaptics(recorder)

        haptics.contextClick()
        haptics.longPress()
        haptics.confirm()
        haptics.reject()
        haptics.gestureEnd()

        assertEquals(
            listOf(
                HapticFeedbackType.ContextClick,
                HapticFeedbackType.LongPress,
                HapticFeedbackType.Confirm,
                HapticFeedbackType.Reject,
                HapticFeedbackType.GestureEnd,
            ),
            recorder.performed,
        )
    }
}