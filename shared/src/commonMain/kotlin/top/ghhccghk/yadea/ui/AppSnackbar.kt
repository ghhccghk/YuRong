package top.ghhccghk.yadea.ui

import androidx.compose.material3.SnackbarDuration as MaterialSnackbarDuration
import androidx.compose.material3.SnackbarHost as MaterialSnackbarHost
import androidx.compose.material3.SnackbarHostState as MaterialSnackbarHostState
import androidx.compose.material3.SnackbarResult as MaterialSnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.SnackbarDuration as MiuixSnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost as MiuixSnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState as MiuixSnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult as MiuixSnackbarResult

/**
 * 轻量提示（Snackbar）的展示时长。
 *
 * 只保留两套原生实现都支持的三个档位，业务页面不需要感知某一方的私有类型：
 * Material 3 对应原生枚举 `SnackbarDuration`，Miuix 对应原生密封接口 `SnackbarDuration`。
 */
enum class AppSnackbarDuration {
    /** 短时展示，适合操作完成后的即时确认。 */
    Short,

    /** 长时展示，适合需要读完的说明文本。 */
    Long,

    /** 一直展示，直到用户点击操作按钮或主动关闭。 */
    Indefinite,
}

/**
 * 轻量提示的结束原因，两种风格语义一致，业务页面据此决定后续动作。
 */
enum class AppSnackbarResult {
    /** 超时自动消失，或被用户主动关闭。 */
    Dismissed,

    /** 用户点击了操作按钮。 */
    ActionPerformed,
}

/** 业务时长映射到 Miuix 原生时长。 */
internal fun AppSnackbarDuration.toMiuixDuration(): MiuixSnackbarDuration = when (this) {
    AppSnackbarDuration.Short -> MiuixSnackbarDuration.Short
    AppSnackbarDuration.Long -> MiuixSnackbarDuration.Long
    AppSnackbarDuration.Indefinite -> MiuixSnackbarDuration.Indefinite
}

/** 业务时长映射到 Material 3 原生时长。 */
internal fun AppSnackbarDuration.toMaterialDuration(): MaterialSnackbarDuration = when (this) {
    AppSnackbarDuration.Short -> MaterialSnackbarDuration.Short
    AppSnackbarDuration.Long -> MaterialSnackbarDuration.Long
    AppSnackbarDuration.Indefinite -> MaterialSnackbarDuration.Indefinite
}

/** Miuix 原生结果映射回业务结果。 */
internal fun MiuixSnackbarResult.toAppResult(): AppSnackbarResult = when (this) {
    MiuixSnackbarResult.Dismissed -> AppSnackbarResult.Dismissed
    MiuixSnackbarResult.ActionPerformed -> AppSnackbarResult.ActionPerformed
}

/** Material 3 原生结果映射回业务结果。 */
internal fun MaterialSnackbarResult.toAppResult(): AppSnackbarResult = when (this) {
    MaterialSnackbarResult.Dismissed -> AppSnackbarResult.Dismissed
    MaterialSnackbarResult.ActionPerformed -> AppSnackbarResult.ActionPerformed
}

/**
 * 轻量提示状态：一份业务状态同时持有两套原生实现。
 *
 * 与 [AppScrollBehavior] 一样，两种风格的原生状态都提前创建，只有当前风格对应的那个参与布局；
 * [showSnackbar] 依据最近一次组合同步的风格，把调用转发给对应的原生状态。
 * 因此业务页面只需要持有同一个状态对象，不需要在页面里判断当前是哪种风格。
 *
 * 注意：状态需要配合 [AppSnackbarHost] 使用；没有宿主参与布局时 [showSnackbar] 会一直挂起，
 * 这与两套原生 `SnackbarHostState` 的行为一致。
 */
@Stable
class AppSnackbarHostState internal constructor(
    internal val materialState: MaterialSnackbarHostState,
    internal val miuixState: MiuixSnackbarHostState,
) {
    /** 当前参与布局的风格，由 [AppSnackbarHost] 在组合期间同步。 */
    internal var activeStyle: UiThemeStyle = UiThemeStyle.Miuix

    /**
     * 展示一条轻量提示，并挂起直到提示消失或用户点击了操作按钮。
     *
     * @param message 提示文本。
     * @param actionLabel 操作按钮文本；为空时不展示操作。
     * @param withDismissAction 是否展示关闭按钮；[AppSnackbarDuration.Indefinite] 时建议开启。
     * @param duration 展示时长；默认与 Material 3 一致：无操作时短时展示，有操作时一直展示。
     */
    suspend fun showSnackbar(
        message: String,
        actionLabel: String? = null,
        withDismissAction: Boolean = false,
        duration: AppSnackbarDuration =
            if (actionLabel == null) AppSnackbarDuration.Short else AppSnackbarDuration.Indefinite,
    ): AppSnackbarResult = when (activeStyle) {
        UiThemeStyle.Miuix -> miuixState
            .showSnackbar(message, actionLabel, withDismissAction, duration.toMiuixDuration())
            .toAppResult()

        UiThemeStyle.Material3 -> materialState
            .showSnackbar(message, actionLabel, withDismissAction, duration.toMaterialDuration())
            .toAppResult()
    }
}

/** 创建轻量提示状态；状态生命周期与调用它的页面一致。 */
@Composable
fun rememberAppSnackbarHostState(): AppSnackbarHostState {
    val state = remember {
        AppSnackbarHostState(MaterialSnackbarHostState(), MiuixSnackbarHostState())
    }
    // 组合期间同步当前风格：showSnackbar 不是组合函数，需要一个明确的宿主归属。
    state.activeStyle = LocalUiThemeStyle.current
    return state
}

/**
 * 轻量提示宿主：放进 [AppScaffold] 的 `snackbarHost` 槽位即可。
 *
 * Miuix 使用原生 [MiuixSnackbarHost]（自带滑动手势关闭），Material 3 使用原生 [MaterialSnackbarHost]；
 * 两者的定位与底部导航避让都由各自的原生 Scaffold 负责，这里不再额外补边距。
 *
 * @param state 与页面共用的提示状态。
 * @param modifier 额外修饰符，作用于提示宿主本身。
 */
@Composable
fun AppSnackbarHost(
    state: AppSnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val style = LocalUiThemeStyle.current
    // 同步当前风格，保证后续 showSnackbar 打到正在参与布局的原生宿主上。
    state.activeStyle = style
    // 风格切换后，另一种风格的宿主已经离开组合，不再为它的提示计时；
    // 这里主动清掉它上面仍在展示的提示，避免对应的 showSnackbar 协程永远等不到结果。
    LaunchedEffect(style) {
        when (style) {
            UiThemeStyle.Miuix -> state.materialState.currentSnackbarData?.dismiss()
            UiThemeStyle.Material3 -> state.miuixState.newestSnackbarData()?.dismiss()
        }
    }
    AppStyleContent(
        material = {
            MaterialSnackbarHost(hostState = state.materialState, modifier = modifier)
        },
        miuix = {
            MiuixSnackbarHost(state = state.miuixState, modifier = modifier)
        },
    )
}