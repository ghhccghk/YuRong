package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun SettingsScreen(
    selectedStyle: UiThemeStyle,
    onStyleChange: (UiThemeStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val useMiuix = LocalUiThemeStyle.current == UiThemeStyle.Miuix

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(if (useMiuix) 8.dp else 12.dp),
    ) {
        if (useMiuix) {
            item {
                SmallTitle(text = "外观")
            }
            item {
                MiuixCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    SwitchPreference(
                        checked = selectedStyle == UiThemeStyle.Miuix,
                        onCheckedChange = { enabled ->
                            onStyleChange(
                                if (enabled) UiThemeStyle.Miuix else UiThemeStyle.Material3,
                            )
                        },
                        title = "Miuix 界面",
                        summary = "关闭后使用 Material 3",
                    )
                }
            }
            item {
                SmallTitle(text = "当前样式")
            }
            item {
                MiuixCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    StyleDescription(selectedStyle)
                }
            }
        } else {
            item {
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Miuix 界面",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "开启后使用 HyperOS 风格；关闭后使用 Material 3",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = selectedStyle == UiThemeStyle.Miuix,
                            onCheckedChange = { enabled ->
                                onStyleChange(
                                    if (enabled) UiThemeStyle.Miuix else UiThemeStyle.Material3,
                                )
                            },
                        )
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    StyleDescription(selectedStyle)
                }
            }
        }
    }
}

@Composable
private fun StyleDescription(selectedStyle: UiThemeStyle) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = selectedStyle.label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = selectedStyle.description,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
