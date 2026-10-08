package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
fun SettingsScreen(
    selectedStyle: UiThemeStyle,
    onStyleChange: (UiThemeStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val useMiuix = LocalUiThemeStyle.current == UiThemeStyle.Miuix
    val pagePadding = PaddingValues(
        horizontal = if (useMiuix) 0.dp else 16.dp,
        vertical = if (useMiuix) 0.dp else 16.dp,
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = pagePadding,
        verticalArrangement = Arrangement.spacedBy(if (useMiuix) 8.dp else 12.dp),
    ) {
        if (useMiuix) {
            item { SmallTitle(text = "外观") }
        } else {
            item {
                ScreenTitle("设置")
                AppText(
                    text = "外观",
                    style = AppDesign.typography.sectionTitle,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        item {
            AppCard(
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = if (useMiuix) 12.dp else 0.dp,
                ),
            ) {
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

        if (useMiuix) {
            item { SmallTitle(text = "当前样式") }
        }

        item {
            AppCard(
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = if (useMiuix) 12.dp else 0.dp,
                ),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    AppText(
                        text = selectedStyle.label,
                        style = AppDesign.typography.sectionTitle,
                        fontWeight = FontWeight.Bold,
                    )
                    AppText(
                        text = selectedStyle.description,
                        style = AppDesign.typography.summary,
                    )
                }
            }
        }
    }
}
