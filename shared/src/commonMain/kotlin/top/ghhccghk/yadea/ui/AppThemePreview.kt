package top.ghhccghk.yadea.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch

/** 预览色卡边长：只用于卡片内部的小色块，不参与整体布局节奏。 */
private val PreviewSwatchSize = 28.dp

/**
 * 主题预览卡片：在设置页顶部展示当前界面风格与配色的实际效果。
 *
 * 组件不持有任何主题状态，所有颜色都取自 [AppDesign] 语义 token，
 * 因此切换界面风格、明暗模式、颜色来源或 Miuix Monet 开关后都会立即跟随变化。
 * 卡片内的示例控件只用于展示外观：开关是只读状态，按钮点击不会修改任何设置。
 *
 * @param styleLabel 当前界面风格名称，例如 Miuix / Material 3。
 * @param modeLabel 当前明暗模式名称，例如 跟随系统 / 亮色 / 暗色。
 * @param modifier 应用于最外层卡片的修饰符。
 */
@Composable
fun AppThemePreview(
    styleLabel: String,
    modeLabel: String,
    modifier: Modifier = Modifier,
) {
    val dimensions = AppDesign.dimensions
    val colors = AppDesign.colors
    AppCard(
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = dimensions.itemHorizontalPadding,
            vertical = dimensions.itemVerticalPadding,
        ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(dimensions.controlSpacing)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    text = "主题预览",
                    style = AppDesign.typography.sectionTitle,
                    color = colors.onSurface,
                )
                AppText(
                    text = "$styleLabel · $modeLabel",
                    style = AppDesign.typography.caption,
                    color = colors.onSurfaceVariant,
                )
            }

            // 色卡顺序与主题 token 一一对应：主色、主容器、次容器、表面容器、错误色。
            Row(horizontalArrangement = Arrangement.spacedBy(dimensions.compactSpacing)) {
                AppThemePreviewSwatch(colors.primary)
                AppThemePreviewSwatch(colors.primaryContainer)
                AppThemePreviewSwatch(colors.secondaryContainer)
                AppThemePreviewSwatch(colors.surfaceContainerHighest)
                AppThemePreviewSwatch(colors.error)
            }

            AppText(
                text = "示例标题",
                style = AppDesign.typography.itemTitle,
                color = colors.onSurface,
            )
            AppText(
                text = "示例正文与摘要文字，用于确认当前主题下的字号、行距和对比度。",
                style = AppDesign.typography.summary,
                color = colors.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 示例按钮只展示外观，点击不触发任何设置变更。
                AppButton(onClick = {}, modifier = Modifier.weight(1f)) { AppText("主要按钮") }
                AppOutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) { AppText("次要按钮") }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = "示例开关",
                        style = AppDesign.typography.itemTitle,
                        color = colors.onSurface,
                    )
                    AppText(
                        text = "只读示例，用于展示开关配色",
                        style = AppDesign.typography.summary,
                        color = colors.onSurfaceVariant,
                    )
                }
                // 两种风格都用原生 Switch，并以只读状态呈现（onCheckedChange 传空）。
                AppStyleContent(
                    material = { Switch(checked = true, onCheckedChange = null) },
                    miuix = { MiuixSwitch(checked = true, onCheckedChange = null) },
                )
            }
        }
    }
}

/** 单个预览色卡：只展示颜色本身，不承载任何交互语义。 */
@Composable
private fun AppThemePreviewSwatch(color: Color) {
    Box(
        modifier = Modifier
            .size(PreviewSwatchSize)
            .clip(AppDesign.shapes.item)
            .background(color),
    )
}
