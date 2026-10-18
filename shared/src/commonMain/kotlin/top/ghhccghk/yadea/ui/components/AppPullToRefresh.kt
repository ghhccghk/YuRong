package top.ghhccghk.yadea.ui

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.PullToRefresh as MiuixPullToRefresh

/**
 * 下拉刷新容器：为可滚动页面提供统一的“下拉重新加载”手势。
 *
 * 业务页面只描述刷新状态、刷新动作与内容，两套风格各自使用原生实现：
 * - Material 3 使用原生 [PullToRefreshBox]，指示器与阈值沿用 M3 默认样式。
 * - Miuix 使用原生 [MiuixPullToRefresh]，保留原生圆环指示器与下拉文案。
 *
 * [content] 需要是真正可滚动的内容（例如 `LazyColumn` 或 `verticalScroll` 容器），
 * 否则两套风格的嵌套滚动都收不到下拉手势。
 *
 * @param isRefreshing 是否正在刷新；由调用方持有，通常直接绑定后台扫描/加载状态。
 * @param onRefresh 用户下拉超过阈值时触发；调用方负责真正发起刷新并更新 [isRefreshing]。
 * @param modifier 应用于最外层可布局节点。
 * @param enabled 是否响应下拉手势；禁用时两套风格都不会回调 [onRefresh]，但内容仍可正常滚动。
 * @param scrollBehavior 可选的顶栏滚动行为；Miuix 用它让下拉手势与顶栏展开/收起协同，
 *   Material 3 的 [PullToRefreshBox] 本身不需要该参数。
 * @param content 可滚动的页面内容。
 */
@Composable
fun AppPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    scrollBehavior: AppScrollBehavior? = null,
    content: @Composable () -> Unit,
) {
    // 两套风格共用同一个回调入口；禁用时统一忽略下拉，保证启用语义一致。
    val triggerRefresh: () -> Unit = { if (enabled) onRefresh() }
    AppStyleContent(
        material = {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = triggerRefresh,
                modifier = modifier,
                enabled = enabled,
            ) {
                content()
            }
        },
        miuix = {
            MiuixPullToRefresh(
                isRefreshing = isRefreshing,
                onRefresh = triggerRefresh,
                modifier = modifier,
                // 复用脚手架创建的同一个滚动行为对象，避免下拉时顶栏状态与滚动不同步。
                topAppBarScrollBehavior = scrollBehavior?.miuix,
                content = content,
            )
        },
    )
}