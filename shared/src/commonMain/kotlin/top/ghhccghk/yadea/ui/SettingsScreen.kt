package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    selectedStyle: UiThemeStyle,
    onStyleChange: (UiThemeStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val useMiuix = LocalUiThemeStyle.current == UiThemeStyle.Miuix
    val dimensions = AppDesign.dimensions
    val pagePadding = PaddingValues(
        horizontal = if (useMiuix) 0.dp else dimensions.pageHorizontalPadding,
        vertical = if (useMiuix) 0.dp else dimensions.pageVerticalPadding,
    )
    val sectionModifier = if (useMiuix) {
        Modifier.fillMaxWidth().padding(horizontal = 12.dp)
    } else {
        Modifier.fillMaxWidth()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = pagePadding,
        verticalArrangement = Arrangement.spacedBy(if (useMiuix) 8.dp else dimensions.sectionSpacing),
    ) {
        item {
            AppPageHeader(
                title = "设置",
                subtitle = "外观与界面风格",
            )
        }

        item {
            AppSection(title = "外观", modifier = sectionModifier) {
                AppSwitchPreference(
                    checked = selectedStyle == UiThemeStyle.Miuix,
                    onCheckedChange = { enabled ->
                        onStyleChange(if (enabled) UiThemeStyle.Miuix else UiThemeStyle.Material3)
                    },
                    title = "Miuix 界面",
                    summary = "关闭后使用 Material 3",
                )
            }
        }

        item {
            AppSection(title = "当前样式", modifier = sectionModifier) {
                AppListRow(
                    title = selectedStyle.label,
                    summary = selectedStyle.description,
                )
            }
        }
    }
}
