package top.ghhccghk.yadea.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * 应用统一的触觉反馈语义。
 *
 * 这里刻意不暴露 Compose 的 [HapticFeedbackType]：业务页面只描述「这是一次什么性质的操作」，
 * 由 [toPlatformType] 负责落到各平台的具体反馈强度。新增语义时在这里加一项，
 * 业务页面与两套风格都无需改动。
 *
 * 关于「两套原生实现」：触觉反馈在 Android 上会真的振动，Desktop / JS / Wasm / iOS 上
 * 由 Compose 提供的空实现吞掉（不会崩溃、也不会报错），因此 Miuix 与 Material 3 分支
 * 共用同一个 [AppHaptics]，不存在风格差异——这正是「语义 API」该有的样子。
 */
enum class AppHapticKind {
    /** 轻点：选中列表项、点击按钮等常规操作。 */
    ContextClick,

    /** 长按：进入长按操作或选择模式。 */
    LongPress,

    /** 成功：提交完成、连接建立等正向结果。 */
    Confirm,

    /** 失败：校验不通过、连接失败等负向结果。 */
    Reject,

    /** 手势结束：拖动吸附、滑动到顶/底等。 */
    GestureEnd,

    /** 开关被切到开启。 */
    ToggleOn,

    /** 开关被切到关闭。 */
    ToggleOff,

    /** 在离散档位之间切换：滑条、分段控件、刻度盘。 */
    SegmentTick,

    /** 输入框光标 / 文本选择手柄移动。 */
    TextHandleMove,
}

/**
 * 把业务语义映射到 Compose 的跨平台触觉反馈类型。
 *
 * 该映射是 [AppHaptics] 的唯一落点，单元测试直接对着它断言，
 * 保证新增语义时不会漏掉某个平台实现。
 */
internal fun AppHapticKind.toPlatformType(): HapticFeedbackType = when (this) {
    AppHapticKind.ContextClick -> HapticFeedbackType.ContextClick
    AppHapticKind.LongPress -> HapticFeedbackType.LongPress
    AppHapticKind.Confirm -> HapticFeedbackType.Confirm
    AppHapticKind.Reject -> HapticFeedbackType.Reject
    AppHapticKind.GestureEnd -> HapticFeedbackType.GestureEnd
    AppHapticKind.ToggleOn -> HapticFeedbackType.ToggleOn
    AppHapticKind.ToggleOff -> HapticFeedbackType.ToggleOff
    AppHapticKind.SegmentTick -> HapticFeedbackType.SegmentTick
    AppHapticKind.TextHandleMove -> HapticFeedbackType.TextHandleMove
}

/**
 * 应用级触觉反馈入口。
 *
 * 用法：在 Composable 里 `val haptics = rememberAppHaptics()`，然后在真实业务动作里
 * 调用 [perform] 或语义化的便捷方法。调用方不需要关心当前是哪套界面风格。
 *
 * @param hapticFeedback 平台提供的触觉反馈实现；测试可注入假实现以断言调用内容。
 */
@Stable
class AppHaptics internal constructor(
    private val hapticFeedback: HapticFeedback,
) {
    /**
     * 触发一次指定语义的触觉反馈。
     * 在不支持触觉反馈的平台上该调用是空操作，不会抛异常。
     */
    fun perform(kind: AppHapticKind) {
        hapticFeedback.performHapticFeedback(kind.toPlatformType())
    }

    /** 轻点反馈：选中列表项、点击按钮。 */
    fun contextClick() = perform(AppHapticKind.ContextClick)

    /** 长按反馈。 */
    fun longPress() = perform(AppHapticKind.LongPress)

    /** 成功反馈。 */
    fun confirm() = perform(AppHapticKind.Confirm)

    /** 失败反馈。 */
    fun reject() = perform(AppHapticKind.Reject)

    /** 手势结束反馈。 */
    fun gestureEnd() = perform(AppHapticKind.GestureEnd)

    /**
     * 开关切换反馈：根据切换后的状态自动选择 [AppHapticKind.ToggleOn] 或 [AppHapticKind.ToggleOff]。
     *
     * @param checked 切换之后的状态。
     */
    fun toggle(checked: Boolean) = perform(
        if (checked) AppHapticKind.ToggleOn else AppHapticKind.ToggleOff,
    )
}

/**
 * 获取当前组合环境下的 [AppHaptics]。
 *
 * 依赖 Compose 的 `LocalHapticFeedback`，因此 Android / iOS / Desktop / Web 都能拿到
 * 对应实现；没有实现的目标平台会自动回退到空实现。
 */
@Composable
fun rememberAppHaptics(): AppHaptics {
    val hapticFeedback = LocalHapticFeedback.current
    // 平台实现是 CompositionLocal 里的稳定对象，跟随它缓存即可。
    return remember(hapticFeedback) { AppHaptics(hapticFeedback) }
}
