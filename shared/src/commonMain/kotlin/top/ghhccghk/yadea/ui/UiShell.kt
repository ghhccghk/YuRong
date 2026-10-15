package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail as MaterialNavigationRail
import androidx.compose.material3.NavigationRailItem as MaterialNavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar as MiuixFloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem as MiuixFloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail as MiuixNavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem as MiuixNavigationRailItem
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior as MiuixScrollBehaviorState
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar

data class AppNavigationItem(
    val label: String,
    val icon: ImageVector,
    val fallbackText: String,
)

/**
 * 顶栏滚动行为：把 Material 3 与 Miuix 各自的原生 scrollBehavior 收敛成同一个业务语义对象。
 *
 * 业务页面只在顶层用 [rememberAppScrollBehavior] 创建一次，然后同时交给 [AppScaffold]
 * 挂载嵌套滚动、交给 [AppTopBar] 驱动收起/展开，不需要接触任何一方主题的私有类型。
 */
@Stable
class AppScrollBehavior internal constructor(
    internal val material: TopAppBarScrollBehavior,
    internal val miuix: MiuixScrollBehaviorState,
) {
    /** 当前界面风格对应的嵌套滚动连接。 */
    internal fun nestedScrollConnection(style: UiThemeStyle): NestedScrollConnection =
        if (style == UiThemeStyle.Miuix) miuix.nestedScrollConnection else material.nestedScrollConnection
}

/**
 * 创建顶栏滚动行为。
 *
 * 两种风格的行为对象都会提前创建，但只有当前风格对应的那个会参与布局；
 * Miuix 使用原生 [MiuixScrollBehavior]，Material 3 使用原生 enterAlways 行为。
 */
@Composable
fun rememberAppScrollBehavior(): AppScrollBehavior {
    val material = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val miuix = MiuixScrollBehavior()
    return remember(material, miuix) { AppScrollBehavior(material, miuix) }
}

/**
 * 应用脚手架：统一承载顶栏、底部导航与页面内容。
 *
 * @param topBar 顶栏内容，通常为 [AppTopBar]。
 * @param bottomBar 底部导航，通常为 [AppBottomNavigation]。
 * @param scrollBehavior 可选的顶栏滚动行为；传入后内容滚动会带动顶栏收起/展开，
 *   为空时保持原有固定顶栏布局（不额外包裹容器）。
 * @param content 页面内容；参数为需要页面自行消费的窗口内边距。
 */
@Composable
fun AppScaffold(
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    scrollBehavior: AppScrollBehavior? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val style = LocalUiThemeStyle.current
    // 只有显式传入滚动行为时才额外包一层容器来挂载 nested scroll，
    // 未使用时完全保持原有组件结构，避免影响既有页面。
    val wrappedContent: @Composable (PaddingValues) -> Unit = if (scrollBehavior == null) {
        content
    } else {
        { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection(style)),
            ) {
                content(padding)
            }
        }
    }
    AppStyleContent(
        material = {
            Scaffold(
                topBar = topBar,
                bottomBar = bottomBar,
                content = wrappedContent,
            )
        },
        miuix = {
            MiuixScaffold(
                topBar = topBar,
                bottomBar = bottomBar,
                content = wrappedContent,
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
 * @param scrollBehavior 与 [AppScaffold] 相同的滚动行为；传入后顶栏随内容滚动收起/展开。
 */
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
    scrollBehavior: AppScrollBehavior? = null,
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
                    scrollBehavior = scrollBehavior?.material,
                    actions = { actions() },
                )
            }
        },
        miuix = {
            MiuixTopAppBar(
                title = title,
                modifier = modifier,
                actions = { actions?.invoke(this) },
                scrollBehavior = scrollBehavior?.miuix,
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

/** 自适应导航断点：可用宽度达到该值时改用侧边导航栏，与 Material 3 的 medium 窗口宽度保持一致。 */
val AppNavigationRailBreakpoint: Dp = 600.dp

/** 顶层导航布局模式：窄屏使用底部导航，宽屏使用侧边导航栏。 */
enum class AppNavigationLayoutMode {
    /** 窄屏：底部导航栏。 */
    BottomNavigation,

    /** 宽屏：左侧导航栏。 */
    NavigationRail,
}

/**
 * 根据可用宽度推导导航布局模式。
 *
 * 抽成纯函数是为了让断点行为可以直接被单元测试覆盖，页面只负责把测量到的宽度传进来，
 * 不需要自己关心具体断点数值。
 *
 * @param availableWidth 当前布局可用的最大宽度。
 */
fun appNavigationLayoutMode(availableWidth: Dp): AppNavigationLayoutMode =
    if (availableWidth >= AppNavigationRailBreakpoint) {
        AppNavigationLayoutMode.NavigationRail
    } else {
        AppNavigationLayoutMode.BottomNavigation
    }

/**
 * 自适应脚手架：窄屏沿用底部导航，宽屏把导航换成左侧导航栏。
 *
 * 内容区仍然复用 [AppScaffold]，所以顶栏、嵌套滚动与下拉刷新在两种布局下行为一致，
 * 业务页面不需要知道自己当前运行在哪种布局里。
 *
 * @param navigationItems 顶层导航项，与 [AppBottomNavigation] 共用同一份数据。
 * @param selectedIndex 当前选中项索引。
 * @param onSelected 选中回调；重复选中当前项不会触发。
 * @param topBar 顶栏内容，通常为 [AppTopBar]。
 * @param floatingNavigation 是否使用 Miuix 悬浮胶囊底栏；只影响窄屏下的 Miuix 分支。
 * @param scrollBehavior 可选的顶栏滚动行为，语义与 [AppScaffold] 一致。
 * @param content 页面内容；参数为需要页面自行消费的窗口内边距。
 */
@Composable
fun AppAdaptiveScaffold(
    navigationItems: List<AppNavigationItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    topBar: @Composable () -> Unit = {},
    floatingNavigation: Boolean = false,
    scrollBehavior: AppScrollBehavior? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    // 用 BoxWithConstraints 测量当前窗口宽度，再按断点决定导航形态；
    // 这样业务页面无需接触任何平台窗口尺寸 API。
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        when (appNavigationLayoutMode(maxWidth)) {
            AppNavigationLayoutMode.NavigationRail -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    AppNavigationRail(
                        items = navigationItems,
                        selectedIndex = selectedIndex,
                        onSelected = onSelected,
                    )
                    // 侧边导航只替换底栏，内容区继续走 AppScaffold，保持顶栏与滚动行为不变。
                    Box(modifier = Modifier.weight(1f)) {
                        AppScaffold(
                            topBar = topBar,
                            scrollBehavior = scrollBehavior,
                            content = content,
                        )
                    }
                }
            }

            AppNavigationLayoutMode.BottomNavigation -> {
                AppScaffold(
                    topBar = topBar,
                    bottomBar = {
                        AppBottomNavigation(
                            items = navigationItems,
                            selectedIndex = selectedIndex,
                            onSelected = onSelected,
                            floating = floatingNavigation,
                        )
                    },
                    scrollBehavior = scrollBehavior,
                    content = content,
                )
            }
        }
    }
}

/**
 * 宽屏侧边导航栏：Miuix 使用原生 [MiuixNavigationRail]，Material 3 使用原生 [MaterialNavigationRail]。
 *
 * 两种风格都只接收业务语义参数，业务页面不接触任何一方的私有类型；
 * Miuix 侧栏自带系统栏内边距处理，Material 3 侧栏沿用官方默认配色。
 */
@Composable
private fun AppNavigationRail(
    items: List<AppNavigationItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    AppStyleContent(
        material = {
            MaterialNavigationRail {
                items.forEachIndexed { index, item ->
                    MaterialNavigationRailItem(
                        selected = selectedIndex == index,
                        onClick = { onSelected(index) },
                        // Material 3 侧栏没有对应的矢量图标来源，沿用与底栏一致的文本占位图标。
                        icon = { Text(item.fallbackText, style = AppDesign.typography.title) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
        miuix = {
            MiuixNavigationRail(modifier = Modifier.fillMaxHeight()) {
                items.forEachIndexed { index, item ->
                    MiuixNavigationRailItem(
                        selected = selectedIndex == index,
                        onClick = { onSelected(index) },
                        icon = item.icon,
                        label = item.label,
                    )
                }
            }
        },
    )
}
