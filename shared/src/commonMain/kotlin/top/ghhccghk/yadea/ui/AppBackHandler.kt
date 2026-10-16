package top.ghhccghk.yadea.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler

/**
 * 系统返回手势的语义封装。
 *
 * 业务页面只描述"返回时该做什么"，不直接依赖平台 API：
 * - Android 在清单里声明 `android:enableOnBackInvokedCallback` 后，返回手势会走
 *   OnBackInvokedCallback，Android 13+ 可以播放预测性返回动画；
 * - Desktop / Web / iOS 由 Compose 宿主提供返回事件调度器，没有系统返回手势时不会触发。
 *
 * [enabled] 为 false 时不注册回调，事件继续交给系统处理（例如在根页面退出应用）。
 *
 * @param enabled 是否拦截本次返回。
 * @param onBack 拦截后的处理逻辑。
 */
@Suppress("DEPRECATION")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}