package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * 底部弹出面板：从屏幕底部滑出，用于承载次级内容而不打断当前页面。
 *
 * Miuix 使用原生 [WindowBottomSheet]（自带标题栏与拖拽手柄），
 * Material 3 使用原生 [ModalBottomSheet]（没有标题槽位，标题在内容顶部渲染）。
 * 两种风格的关闭时机保持一致：下滑、点击遮罩或返回键都会回调 [onDismissRequest]。
 *
 * @param show 是否展示；为 false 时不参与组合。
 * @param onDismissRequest 用户请求关闭时的回调。
 * @param modifier 额外修饰符。
 * @param title 面板标题；为空时不显示标题。
 * @param content 面板内容，在纵向 [Column] 作用域内布局。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppStyleContent(
        material = {
            if (show) {
                ModalBottomSheet(
                    onDismissRequest = onDismissRequest,
                    modifier = modifier,
                    containerColor = AppDesign.colors.surface,
                ) {
                    AppBottomSheetBody(
                        title = title,
                        // Material 3 的面板内容没有内置左右边距，这里补上与页面一致的间距。
                        contentPadding = PaddingValues(
                            start = AppDesign.dimensions.itemHorizontalPadding,
                            end = AppDesign.dimensions.itemHorizontalPadding,
                            bottom = AppDesign.dimensions.itemVerticalPadding,
                        ),
                        content = content,
                    )
                }
            }
        },
        miuix = {
            OverlayBottomSheet(
                show = show,
                modifier = modifier,
                title = title,
                onDismissRequest = onDismissRequest,
            ) {
                // Miuix 面板已自带标题栏与内边距，这里只统一的元素间距。
                AppBottomSheetBody(
                    title = null,
                    contentPadding = PaddingValues(vertical = AppDesign.dimensions.compactSpacing),
                    content = content,
                )
            }
        },
    )
}

/**
 * 底部面板内容容器：统一两种风格的标题与元素间距。
 */
@Composable
private fun AppBottomSheetBody(
    title: String?,
    contentPadding: PaddingValues,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing),
    ) {
        if (!title.isNullOrBlank()) {
            AppText(
                text = title,
                style = AppDesign.typography.title,
                color = AppDesign.colors.onSurface,
            )
        }
        content()
    }
}
