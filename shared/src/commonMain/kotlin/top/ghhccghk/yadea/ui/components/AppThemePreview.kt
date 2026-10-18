package top.ghhccghk.yadea.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 预览手机外框宽度：固定尺寸保证预览在任何窗口宽度下都保持同一比例，不会在宽屏上被拉变形。 */
private val PhoneFrameWidth = 148.dp

/** 预览手机外框高度，与宽度共同构成竖屏手机的观感。 */
private val PhoneFrameHeight = 268.dp

/** 手机外框圆角：比普通卡片更圆，用来区分“设备外形”与页面里的卡片。 */
private val PhoneFrameCornerRadius = 22.dp

/** 手机内部内容与边框之间的留白。 */
private val PhoneScreenPadding = 10.dp

/** 预览里顶部高亮卡片的高度，对应应用中的主容器色区块。 */
private val PhoneHeroCardHeight = 32.dp

/** 预览里底部胶囊导航条的高度。 */
private val PhoneNavigationBarHeight = 22.dp

/** 预览里导航指示块的边长。 */
private val PhoneNavigationDotSize = 7.dp

/** 预览默认展示的应用名；调用方可以覆盖成实际产品名。 */
private const val DefaultPreviewAppName = "投屏导航"

/**
 * 主题预览卡片：用一台“手机缩略图”展示当前界面风格与配色的实际效果。
 *
 * 组件不持有任何主题状态，所有颜色都取自 [AppDesign] 语义 token，
 * 因此切换界面风格、明暗模式、颜色来源或 Miuix Monet 开关后都会立即跟随变化。
 * 缩略图里的区块只表达层级关系（标题 / 高亮卡片 / 正文 / 底部导航），不承载任何交互。
 *
 * @param styleLabel 当前界面风格名称，例如 Miuix / Material 3。
 * @param modeLabel 当前明暗模式名称，例如 跟随系统 / 浅色 / 深色。
 * @param appName 缩略图里展示的应用名，默认使用产品名。
 * @param navigationItemCount 缩略图底部导航的入口数量，应与真实底部导航保持一致。
 * @param modifier 应用于最外层可布局节点的修饰符。
 */
@Composable
fun AppThemePreview(
    styleLabel: String,
    modeLabel: String,
    modifier: Modifier = Modifier,
    appName: String = DefaultPreviewAppName,
    navigationItemCount: Int = 3,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing),
    ) {
        AppThemePreviewPhone(
            appName = appName,
            navigationItemCount = navigationItemCount,
        )
        // 缩略图下方补一行说明，明确当前生效的界面风格与明暗模式。
        AppText(
            text = "$styleLabel · $modeLabel",
            style = AppDesign.typography.caption,
            color = AppDesign.colors.onSurfaceVariant,
        )
    }
}

/**
 * 预览里的手机缩略图：外框表达设备外形，内部自上而下依次是应用名、
 * 高亮卡片、正文区域和底部胶囊导航。
 *
 * 所有颜色都来自 [AppDesign] token，因此两套风格、明暗模式与动态色变化都会即时反映。
 */
@Composable
private fun AppThemePreviewPhone(
    appName: String,
    navigationItemCount: Int,
) {
    val colors = AppDesign.colors
    val shapes = AppDesign.shapes
    val compactSpacing = AppDesign.dimensions.compactSpacing
    val itemShape = shapes.item
    val frameShape = RoundedCornerShape(PhoneFrameCornerRadius)

    Box(
        modifier = Modifier
            .width(PhoneFrameWidth)
            .height(PhoneFrameHeight)
            .clip(frameShape)
            .background(colors.previewBackground)
            .border(width = 1.dp, color = colors.outline, shape = frameShape)
            .padding(PhoneScreenPadding),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(compactSpacing),
        ) {
            // 应用名对应真实界面的标题栏文字。
            AppText(
                text = appName,
                style = AppDesign.typography.caption,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 高亮卡片展示主容器色，对应应用里的强调卡片。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PhoneHeroCardHeight)
                    .clip(itemShape)
                    .background(colors.primaryContainer),
            )
            // 正文区域占满剩余高度，用来展示表面容器色与内容底色。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(itemShape)
                    .background(colors.surfaceContainerHighest),
            )
            // 底部胶囊条模拟应用的底部导航：第一项用主色表示“当前选中”。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PhoneNavigationBarHeight)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(colors.surfaceContainer),
                horizontalArrangement = Arrangement.spacedBy(
                    space = compactSpacing,
                    alignment = Alignment.CenterHorizontally,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(navigationItemCount) { index ->
                    Box(
                        modifier = Modifier
                            .size(PhoneNavigationDotSize)
                            .clip(itemShape)
                            .background(
                                if (index == 0) colors.primary else colors.onSurfaceVariant,
                            ),
                    )
                }
            }
        }
    }
}
