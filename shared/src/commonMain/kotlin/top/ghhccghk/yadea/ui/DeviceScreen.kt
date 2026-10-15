package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 设备信息页面
 *
 * 四组信息都是只读静态数据：窄屏按原有顺序自上而下堆叠，宽屏（含桌面窗口）
 * 拆成左右两栏并排展示，避免宽屏下每行文字被拉得过长。
 * 分栏与断点判断都交给 [AppAdaptiveTwoColumn]，页面本身不接触任何风格或窗口尺寸 API。
 */
@Composable
fun DeviceScreen() {
    val dimensions = AppDesign.dimensions
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = dimensions.pageHorizontalPadding,
            vertical = dimensions.pageVerticalPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
    ) {
        item {
            AppPageHeader(
                title = "设备信息",
                subtitle = "仪表、协议与应用运行信息",
            )
        }

        // 页头之下是一个自适应分栏容器：单栏时两组依次堆叠，双栏时左右各占一半。
        item {
            AppAdaptiveTwoColumn(
                modifier = Modifier.fillMaxWidth(),
                columnSpacing = dimensions.sectionSpacing,
                rowSpacing = dimensions.sectionSpacing,
                primary = {
                    AppSection(title = "仪表配置") {
                        AppListRow("画布宽度", value = "960 px", showDivider = true)
                        AppListRow("画布高度", value = "368 px", showDivider = true)
                        AppListRow("旋转", value = "0°", showDivider = true)
                        AppListRow("分包倍率", value = "1x", showDivider = true)
                        AppListRow("默认包大小", value = "1024 B")
                    }
                    AppSection(title = "BLE 协议") {
                        AppListRow("MTU", value = "517", showDivider = true)
                        AppListRow("包间隔", value = "18 ms", showDivider = true)
                        AppListRow("最大重试", value = "20 次", showDivider = true)
                        AppListRow("写入超时", value = "1500 ms", showDivider = true)
                        AppListRow("重连延迟", value = "1000 ms")
                    }
                },
                secondary = {
                    AppSection(title = "应用信息") {
                        AppListRow("版本", value = "1.0.0", showDivider = true)
                        AppListRow("平台", value = "Kotlin Multiplatform", showDivider = true)
                        AppListRow("目标", value = "Android / iOS / Desktop / Web")
                    }
                    AppSection(title = "协议命令") {
                        AppListRow("0x02", summary = "导航结束", showDivider = true)
                        AppListRow("0x12", summary = "图像数据", showDivider = true)
                        AppListRow("0x14", summary = "导航提示", showDivider = true)
                        AppListRow("0x1F", summary = "心跳", showDivider = true)
                        AppListRow("0x20", summary = "导航开始")
                    }
                },
            )
        }
    }
}
