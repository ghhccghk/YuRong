package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import top.ghhccghk.yadea.ble.BleAdapter
import top.ghhccghk.yadea.mirror.FrameSourceFactory
import top.ghhccghk.yadea.wifi.WifiConnectionManager
import top.ghhccghk.yadea.wifi.WifiP2pManager
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Phone
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring
import top.yukonga.miuix.kmp.icon.extended.SearchDevice
import top.yukonga.miuix.kmp.icon.extended.Settings

private enum class MainDestination(
    val label: String,
    val miuixIcon: ImageVector,
) {
    Connection("连接", MiuixIcons.SearchDevice),
    Mirror("投屏", MiuixIcons.ScreenMirroring),
    Device("设备", MiuixIcons.Phone),
    Settings("设置", MiuixIcons.Settings),
}

@Composable
fun MainScreen(
    wifiManager: WifiConnectionManager? = null,
    bleAdapter: BleAdapter? = null,
    p2pManager: WifiP2pManager? = null,
    frameSourceFactory: FrameSourceFactory? = null,
    permissionChecker: PermissionChecker? = null,
) {
    val uiThemeState = rememberUiThemeStyle()
    val viewModel = remember {
        AppViewModel(wifiManager, bleAdapter, p2pManager, frameSourceFactory, permissionChecker)
    }

    AppTheme(uiThemeState.value) {
        MainScreenContent(
            viewModel = viewModel,
            selectedStyle = uiThemeState.value,
            onStyleChange = { uiThemeState.value = it },
        )
    }
}

@Composable
private fun MainScreenContent(
    viewModel: AppViewModel,
    selectedStyle: UiThemeStyle,
    onStyleChange: (UiThemeStyle) -> Unit,
) {
    var selectedDestination by remember { mutableStateOf(MainDestination.Connection) }

    if (LocalUiThemeStyle.current == UiThemeStyle.Miuix) {
        MiuixScaffold(
            topBar = {
                MiuixTopAppBar(title = selectedDestination.label)
            },
            bottomBar = {
                MiuixNavigationBar {
                    MainDestination.entries.forEach { destination ->
                        MiuixNavigationBarItem(
                            selected = selectedDestination == destination,
                            onClick = { selectedDestination = destination },
                            icon = destination.miuixIcon,
                            label = destination.label,
                        )
                    }
                }
            },
        ) { padding ->
            MainDestinationContent(
                destination = selectedDestination,
                viewModel = viewModel,
                selectedStyle = selectedStyle,
                onStyleChange = onStyleChange,
                modifier = Modifier.padding(padding),
            )
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    MainDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = selectedDestination == destination,
                            onClick = { selectedDestination = destination },
                            icon = {
                                Text(
                                    text = when (destination) {
                                        MainDestination.Connection -> "📡"
                                        MainDestination.Mirror -> "🖥"
                                        MainDestination.Device -> "📱"
                                        MainDestination.Settings -> "⚙"
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            },
                            label = { Text(destination.label) },
                        )
                    }
                }
            },
        ) { padding ->
            MainDestinationContent(
                destination = selectedDestination,
                viewModel = viewModel,
                selectedStyle = selectedStyle,
                onStyleChange = onStyleChange,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun MainDestinationContent(
    destination: MainDestination,
    viewModel: AppViewModel,
    selectedStyle: UiThemeStyle,
    onStyleChange: (UiThemeStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (destination) {
            MainDestination.Connection -> ConnectionScreen(viewModel)
            MainDestination.Mirror -> MirrorScreen(viewModel)
            MainDestination.Device -> DeviceScreen()
            MainDestination.Settings -> SettingsScreen(
                selectedStyle = selectedStyle,
                onStyleChange = onStyleChange,
            )
        }
    }
}

@Composable
fun StatusCard(title: String, status: String, isConnected: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = if (isConnected) "●" else "○",
                style = MaterialTheme.typography.headlineMedium,
                color = if (isConnected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
fun StatusChip(connected: Boolean, text: String) {
    AssistChip(
        onClick = {},
        label = {
            Text(
                text = text,
                color = if (connected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
            )
        },
        leadingIcon = { Text(if (connected) "●" else "○") },
    )
}

