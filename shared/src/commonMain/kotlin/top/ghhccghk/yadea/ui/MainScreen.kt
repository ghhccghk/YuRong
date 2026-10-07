package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.ghhccghk.yadea.wifi.WifiConnectionManager
import top.ghhccghk.yadea.wifi.WifiP2pManager
import top.ghhccghk.yadea.ble.BleAdapter

@Composable
fun MainScreen(
    wifiManager: WifiConnectionManager? = null,
    bleAdapter: BleAdapter? = null,
    p2pManager: WifiP2pManager? = null
) {
    var selectedTab by remember { mutableStateOf(0) }
    val viewModel = remember { AppViewModel(wifiManager, bleAdapter, p2pManager) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    icon = { Text("📡", style = MaterialTheme.typography.titleLarge) }, label = { Text("连接") })
                NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    icon = { Text("🖥", style = MaterialTheme.typography.titleLarge) }, label = { Text("投屏") })
                NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                    icon = { Text("⚙", style = MaterialTheme.typography.titleLarge) }, label = { Text("设备") })
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> ConnectionScreen(viewModel)
                1 -> MirrorScreen(viewModel)
                2 -> DeviceScreen()
            }
        }
    }
}

@Composable
fun StatusCard(title: String, status: String, isConnected: Boolean, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(16.dp), Arrangement.SpaceBetween) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
            Text(if (isConnected) "●" else "○",
                style = MaterialTheme.typography.headlineMedium,
                color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun StatusChip(connected: Boolean, text: String) {
    AssistChip(onClick = {}, label = {
        Text(text, color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
    }, leadingIcon = { Text(if (connected) "●" else "○") })
}
