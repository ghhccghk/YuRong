package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar as MiuixFloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem as MiuixFloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar

data class AppNavigationItem(
    val label: String,
    val icon: ImageVector,
    val fallbackText: String,
)

@Composable
fun AppScaffold(
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    AppStyleContent(
        material = {
            Scaffold(
                topBar = topBar,
                bottomBar = bottomBar,
                content = content,
            )
        },
        miuix = {
            MiuixScaffold(
                topBar = topBar,
                bottomBar = bottomBar,
                content = content,
            )
        },
    )
}

/**
 * 顶栏：承载当前页面的标题，以及可选的页面级操作。
 *
 * 两套风格的职责划分不同：
 * - Miuix 使用原生 [MiuixTopAppBar]，标题与操作都放在原生顶栏里。
 * - Material 3 的页面标题仍由页面内容里的 AppPageHeader 承担；只有提供 [actions] 时
 *   才渲染一个不显示标题的顶栏来承载操作，避免标题重复显示。
 *
 * @param title 页面标题，Miuix 模式下显示在原生顶栏。
 * @param actions 页面级操作，例如图标按钮；为空时不渲染任何操作。
 */
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    AppStyleContent(
        material = {
            if (actions != null) {
                TopAppBar(
                    // 标题交给页面内的 AppPageHeader，这里只承载页面级操作。
                    title = {},
                    modifier = modifier,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AppDesign.colors.background,
                        actionIconContentColor = AppDesign.colors.onBackground,
                    ),
                    actions = { actions() },
                )
            }
        },
        miuix = {
            MiuixTopAppBar(
                title = title,
                modifier = modifier,
                actions = { actions?.invoke(this) },
            )
        },
    )
}

/**
 * 底部导航：在顶层页面之间切换。
 *
 * @param items 导航项；Miuix 使用 [AppNavigationItem.icon]，Material 3 使用文本占位图标。
 * @param selectedIndex 当前选中项索引。
 * @param onSelected 选中回调；重复选中当前项不会触发。
 * @param floating 是否使用 Miuix 悬浮胶囊底栏。Material 3 没有对应的原生样式，
 *   该参数只影响 Miuix 分支，Material 3 始终保持原生 NavigationBar。
 */
@Composable
fun AppBottomNavigation(
    items: List<AppNavigationItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    floating: Boolean = false,
) {
    AppStyleContent(
        material = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = { onSelected(index) },
                        icon = { Text(item.fallbackText, style = AppDesign.typography.title) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
        miuix = {
            if (floating) {
                // 悬浮底栏为图标 + 语义标签形式，自带阴影、圆角与系统栏内边距处理。
                MiuixFloatingNavigationBar {
                    items.forEachIndexed { index, item ->
                        MiuixFloatingNavigationBarItem(
                            selected = selectedIndex == index,
                            onClick = { onSelected(index) },
                            icon = item.icon,
                            label = item.label,
                        )
                    }
                }
            } else {
                MiuixNavigationBar {
                    items.forEachIndexed { index, item ->
                        MiuixNavigationBarItem(
                            selected = selectedIndex == index,
                            onClick = { onSelected(index) },
                            icon = item.icon,
                            label = item.label,
                        )
                    }
                }
            }
        },
    )
}
