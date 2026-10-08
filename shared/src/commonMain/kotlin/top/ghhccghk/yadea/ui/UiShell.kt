package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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

@Composable
fun AppTopBar(title: String) {
    AppStyleContent(
        material = {
            // Material 3 keeps the original page headings; Miuix gets a native top app bar.
        },
        miuix = {
            MiuixTopAppBar(title = title)
        },
    )
}

@Composable
fun AppBottomNavigation(
    items: List<AppNavigationItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
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
        },
    )
}
