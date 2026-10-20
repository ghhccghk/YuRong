package top.ghhccghk.yadea.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar as MiuixFloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem as MiuixFloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail as MiuixNavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem as MiuixNavigationRailItem
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior as MiuixScrollBehaviorState
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 模糊顶栏上的动态色着色强度：保留模糊层次，同时让底色明显跟随应用配色。 */
private const val MiuixTopBarBlurTintAlpha = 0.82f

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
    /**
     * Material 3 原生顶栏当前是否真的参与布局。
     *
     * Material 3 只在页面提供页面级操作时才渲染原生 TopAppBar，而它的原生滚动行为只要被挂到
     * 嵌套滚动链上就会把竖直位移累加到内部 heightOffset 上；顶栏缺席时收缩下限仍是初始哨兵值
     * （等于可以无限收缩），于是内容区的拖动会被整体吃掉，表现为“切回 Material 3 后页面拖不动”。
     * 这里记录顶栏是否渲染，供 Material 侧滚动连接决定是否转发事件。
     */
    private var materialTopBarPresent by mutableStateOf(false)

    /** 由 [AppTopBar] 在渲染或移除 Material 原生顶栏时登记；业务页面不需要接触。 */
    internal fun setMaterialTopBarPresent(present: Boolean) {
        materialTopBarPresent = present
    }

    /**
     * Material 3 侧的嵌套滚动连接：没有原生顶栏时退化成空连接，把拖动位移全部让给内容区；
     * 有顶栏时才委托给原生行为，保留“上滑收起、下滑展开”的既有表现。
     */
    private val materialConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (materialTopBarPresent) {
                material.nestedScrollConnection.onPreScroll(available, source)
            } else {
                Offset.Zero
            }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset =
            if (materialTopBarPresent) {
                material.nestedScrollConnection.onPostScroll(consumed, available, source)
            } else {
                Offset.Zero
            }

        override suspend fun onPreFling(available: Velocity): Velocity =
            if (materialTopBarPresent) {
                material.nestedScrollConnection.onPreFling(available)
            } else {
                Velocity.Zero
            }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
            if (materialTopBarPresent) {
                material.nestedScrollConnection.onPostFling(consumed, available)
            } else {
                Velocity.Zero
            }
    }

    /** 当前界面风格对应的嵌套滚动连接。 */
    internal fun nestedScrollConnection(style: UiThemeStyle): NestedScrollConnection =
        if (style == UiThemeStyle.Miuix) miuix.nestedScrollConnection else materialConnection
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
 * @param snackbarHost 轻量提示宿主，通常为 [AppSnackbarHost]；不传时不展示任何提示。
 * @param scrollBehavior 可选的顶栏滚动行为；传入后内容滚动会带动顶栏收起/展开，
 *   为空时保持原有固定顶栏布局（不额外包裹容器）。
 * @param blurEnabled 是否允许顶栏/底栏使用 Miuix 官方 miuix-blur 背景模糊。
 *   只有当前风格为 Miuix、且运行时同时支持背景记录与 RuntimeShader 时才真正生效；
 *   Material 3 风格完全走原生实现，不受该参数影响。
 * @param content 页面内容；参数为需要页面自行消费的窗口内边距。
 */
@Composable
fun AppScaffold(
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    scrollBehavior: AppScrollBehavior? = null,
    blurEnabled: Boolean = false,
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
                // 提示宿主交给原生 Scaffold 定位，自动避开底部导航与系统栏。
                snackbarHost = snackbarHost,
                content = wrappedContent,
            )
        },
        miuix = {
            // 运行时能力检查：Android 上模糊依赖 RuntimeShader（API 33+），背景记录支架依赖
            // RenderEffect（API 32+），Skiko 系（Desktop / iOS / Web）恒为可用。
            // 三者缺一都退回原有的纯色顶栏/底栏，保证 minSdk 24 的单 APK 也能正常运行。
            val blurSupport = rememberAppBlurSupport()
            val blurActive = blurEnabled && blurSupport.isUsable
            if (!blurActive) {
                MiuixScaffold(
                    topBar = topBar,
                    bottomBar = bottomBar,
                    snackbarHost = snackbarHost,
                    content = wrappedContent,
                )
            } else {
                // 背景色需要在组合期取出：AppDesign.colors 是 @Composable 属性，
                // 不能直接在 DrawScope 的 onDraw 回调里读取。
                val backgroundColor = AppDesign.colors.background
                // rememberLayerBackdrop 只捕获自身内容，不包含父级 Scaffold 的背景，
                // 所以先在 onDraw 里铺一层不透明底色，避免透明像素被模糊成色块。
                val backdrop = rememberLayerBackdrop {
                    drawRect(backgroundColor)
                    drawContent()
                }
                // 把捕获层下发给顶栏/底栏：CompositionLocal 里只暴露 miuix-blur 的公共类型，
                // 业务页面依旧只看到 AppTopBar / AppBottomNavigation 这样的语义 API。
                CompositionLocalProvider(LocalAppBlurBackdrop provides backdrop) {
                    MiuixScaffold(
                        topBar = topBar,
                        bottomBar = bottomBar,
                        snackbarHost = snackbarHost,
                        content = { padding ->
                            // 只录制内容区，栏不在里面
                            Box(Modifier.fillMaxSize()
                                .layerBackdrop(backdrop)) {
                                wrappedContent(padding)
                            }
                        },
                    )
                }
            }
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
 *
 * 当 [AppScaffold] 启用模糊时（[LocalAppBlurBackdrop] 非空），Miuix 顶栏会把自身底色置为
 * 透明并叠加 Modifier.textureBlur，让下方内容以模糊形式透出；Material 3 分支始终不变。
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
            // Material 3 只有存在页面级操作时才渲染原生顶栏；这里把“顶栏是否真实渲染”同步给滚动
            // 行为，避免顶栏缺席时原生 enterAlways 行为仍把内容区的拖动全部吃掉（详见 AppScrollBehavior）。
            val materialTopBarPresent = actions != null
            DisposableEffect(scrollBehavior, materialTopBarPresent) {
                scrollBehavior?.setMaterialTopBarPresent(materialTopBarPresent)
                onDispose { scrollBehavior?.setMaterialTopBarPresent(false) }
            }
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
            // 从脚手架下发的捕获层读取模糊能力：为空表示当前未启用模糊或平台不支持，
            // 此时完全保持原生纯色顶栏，不引入任何额外绘制。
            val backdrop = LocalAppBlurBackdrop.current
            // Miuix 的页面底色是 surface：顶栏与页面背景保持一致。
            // 开启 Monet 时 surface 由动态色生成，关闭时保持 HyperOS 原生背景。
            val topBarColor = AppDesign.colors.surface
            val titleColor = AppDesign.colors.onSurface
            if (backdrop == null) {
                MiuixTopAppBar(
                    title = title,
                    modifier = modifier,
                    color = topBarColor,
                    titleColor = titleColor,
                    largeTitleColor = titleColor,
                    actions = { actions?.invoke(this) },
                    scrollBehavior = scrollBehavior?.miuix,
                )
            } else {
                // 顶栏横跨整个窗口，用矩形裁剪即可与原生外观对齐。
                MiuixTopAppBar(
                    title = title,
                    modifier = modifier.textureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        blurRadius = BlurDefaults.BlurRadius,
                    ),
                    // 在模糊层之上叠加动态顶栏色；全透明会让模糊区域完全沿用底层原生 surface。
                    color = topBarColor.copy(alpha = MiuixTopBarBlurTintAlpha),
                    titleColor = titleColor,
                    largeTitleColor = titleColor,
                    actions = { actions?.invoke(this) },
                    scrollBehavior = scrollBehavior?.miuix,
                )
            }
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
 *
 * 当 [AppScaffold] 启用模糊时（[LocalAppBlurBackdrop] 非空），Miuix 底栏会把底色置为透明并
 * 叠加 Modifier.textureBlur：常规底栏使用矩形裁剪，悬浮胶囊底栏使用与自身一致的圆角，
 * 保证模糊区域和胶囊圆角严丝合缝。Material 3 分支始终不变。
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
            val backdrop = LocalAppBlurBackdrop.current
            if (floating) {
                // 悬浮底栏为图标 + 语义标签形式，自带阴影、圆角与系统栏内边距处理。
                // 模糊区域的圆角必须与胶囊圆角一致，否则会在四角露出直角边。
                MiuixFloatingNavigationBar(
                    modifier = backdrop?.let {
                        Modifier.textureBlur(
                            backdrop = it,
                            shape = RoundedCornerShape(FloatingToolbarDefaults.CornerRadius),
                            blurRadius = BlurDefaults.BlurRadius,
                        )
                    } ?: Modifier,
                    // 底色透明，让模糊层完整透出。
                    color = backdrop?.let { Color.Transparent } ?: MiuixTheme.colorScheme.surfaceContainer,
                ) {
                    items.forEachIndexed { index, item ->
                        MiuixFloatingNavigationBarItem(
                            selected = selectedIndex == index,
                            onClick = { onSelected(index) },
                            icon = item.icon,
                            label = item.label,
                        )
                    }
                }
            } else if (backdrop == null) {
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
            } else {
                // 常规底栏整条横跨窗口，矩形裁剪即可；分隔线由 Miuix 自己绘制在模糊层之上。
                MiuixNavigationBar(
                    modifier = Modifier.textureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        blurRadius = BlurDefaults.BlurRadius,
                    ),
                    color = Color.Transparent,
                ) {
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

/** 内容分栏断点：内容区可用宽度达到该值后，页面内容拆成左右两栏。 */
val AppContentTwoColumnBreakpoint: Dp = 840.dp

/** 页面内容分栏模式：窄屏单栏，宽屏左右双栏。 */
enum class AppContentLayoutMode {
    /** 单栏：所有分组自上而下依次排列。 */
    SingleColumn,

    /** 双栏：分组拆到左右两栏并排展示，充分利用宽屏与桌面窗口。 */
    TwoColumn,
}

/**
 * 根据内容区可用宽度推导分栏模式。
 *
 * 与 [appNavigationLayoutMode] 一样抽成纯函数，断点行为可以直接被单元测试覆盖；
 * 这里测量的是扣除侧边导航之后的内容区宽度，因此双栏会比侧边导航晚一档出现。
 *
 * @param availableWidth 内容区可用的最大宽度。
 */
fun appContentLayoutMode(availableWidth: Dp): AppContentLayoutMode =
    if (availableWidth >= AppContentTwoColumnBreakpoint) {
        AppContentLayoutMode.TwoColumn
    } else {
        AppContentLayoutMode.SingleColumn
    }

/**
 * 自适应双栏内容：宽屏把两组内容并排展示，窄屏按 [primary]、[secondary] 的顺序上下堆叠。
 *
 * 只负责分栏，不关心栏内是什么；页面把分组拆成两段传进来即可，既不需要自己判断断点，
 * 也不需要接触任何一方的主题类型。栏内间距交给调用方，避免出现双重间距。
 *
 * @param modifier 额外修饰符，作用于最外层容器。
 * @param columnSpacing 双栏时两栏之间的水平间距。
 * @param rowSpacing 单栏时两段之间的垂直间距，以及双栏时栏内分组的垂直间距。
 * @param primary 第一组内容；双栏时在左，单栏时在上。
 * @param secondary 第二组内容；双栏时在右，单栏时在下。
 */
@Composable
fun AppAdaptiveTwoColumn(
    modifier: Modifier = Modifier,
    columnSpacing: Dp = 0.dp,
    rowSpacing: Dp = 0.dp,
    primary: @Composable () -> Unit,
    secondary: @Composable () -> Unit,
) {
    // 与 AppAdaptiveScaffold 一致，用 BoxWithConstraints 测量自身可用宽度后再按断点分栏。
    BoxWithConstraints(modifier = modifier) {
        when (appContentLayoutMode(maxWidth)) {
            AppContentLayoutMode.TwoColumn -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(columnSpacing),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(rowSpacing),
                    ) { primary() }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(rowSpacing),
                    ) { secondary() }
                }
            }

            AppContentLayoutMode.SingleColumn -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(rowSpacing),
                ) {
                    primary()
                    secondary()
                }
            }
        }
    }
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
 * @param snackbarHost 轻量提示宿主，语义与 [AppScaffold] 一致；两种布局下都会挂载。
 * @param scrollBehavior 可选的顶栏滚动行为，语义与 [AppScaffold] 一致。
 * @param blurEnabled 是否允许顶栏/底栏使用 Miuix 官方 miuix-blur 背景模糊，语义与 [AppScaffold] 一致；
 *   宽屏下会透传给内容区的 [AppScaffold]（侧边导航栏本身不做模糊）。
 * @param content 页面内容；参数为需要页面自行消费的窗口内边距。
 */
@Composable
fun AppAdaptiveScaffold(
    navigationItems: List<AppNavigationItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    topBar: @Composable () -> Unit = {},
    floatingNavigation: Boolean = false,
    snackbarHost: @Composable () -> Unit = {},
    scrollBehavior: AppScrollBehavior? = null,
    blurEnabled: Boolean = false,
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
                            snackbarHost = snackbarHost,
                            scrollBehavior = scrollBehavior,
                            blurEnabled = blurEnabled,
                            content = content,
                        )
                    }
                }
            }

            AppNavigationLayoutMode.BottomNavigation -> {
                AppScaffold(
                    topBar = topBar,
                    snackbarHost = snackbarHost,
                    bottomBar = {
                        AppBottomNavigation(
                            items = navigationItems,
                            selectedIndex = selectedIndex,
                            onSelected = onSelected,
                            floating = floatingNavigation,
                        )
                    },
                    scrollBehavior = scrollBehavior,
                    blurEnabled = blurEnabled,
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
