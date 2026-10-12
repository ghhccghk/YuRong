package top.ghhccghk.yadea.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 权限管理页面组件
 *
 * 显示和管理应用所需权限的状态
 */
@Composable
fun PermissionPanel(
    permissions: List<PermissionStatus>,
    onRequestPermission: (String) -> Unit,
    onRequestAll: () -> Unit
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = AppDesign.dimensions.itemHorizontalPadding, vertical = AppDesign.dimensions.itemVerticalPadding), verticalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppText("权限管理", style = AppDesign.typography.sectionTitle, fontWeight = FontWeight.Bold)
                if (permissions.any { !it.granted }) {
                    AppButton(onClick = onRequestAll) {
                        AppText("全部授权")
                    }
                }
            }

            permissions.forEach { perm ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        AppText(perm.name, style = AppDesign.typography.body)
                        AppText(perm.description, style = AppDesign.typography.summary)
                    }
                    if (perm.granted) {
                        AssistChip(
                            onClick = {},
                            label = { AppText("已授权") },
                            leadingIcon = { AppText("✓") }
                        )
                    } else {
                        AppOutlinedButton(onClick = { onRequestPermission(perm.permission) }) {
                            AppText("授权")
                        }
                    }
                }
            }
        }
    }
}

/**
 * 权限状态数据类
 */
data class PermissionStatus(
    val permission: String,
    val name: String,
    val description: String,
    val granted: Boolean
)

/**
 * 默认权限列表
 */
val defaultPermissions = listOf(
    PermissionStatus(
        "android.permission.BLUETOOTH_SCAN",
        "蓝牙扫描",
        "扫描附近 BLE 设备",
        false
    ),
    PermissionStatus(
        "android.permission.BLUETOOTH_CONNECT",
        "蓝牙连接",
        "连接 BLE 仪表设备",
        false
    ),
    PermissionStatus(
        "android.permission.ACCESS_FINE_LOCATION",
        "精确位置",
        "WiFi 扫描和 BLE 定位",
        false
    ),
    PermissionStatus(
        "android.permission.ACCESS_WIFI_STATE",
        "WiFi 状态",
        "读取 WiFi 连接状态",
        true
    ),
    PermissionStatus(
        "android.permission.CHANGE_WIFI_STATE",
        "WiFi 控制",
        "连接/断开 WiFi",
        true
    ),
    PermissionStatus(
        "android.permission.CAMERA",
        "相机",
        "摄像头投屏",
        false
    ),
    PermissionStatus(
        "android.permission.INTERNET",
        "网络",
        "TCP/UDP 通信",
        true
    ),
    PermissionStatus(
        "android.permission.FOREGROUND_SERVICE",
        "前台服务",
        "后台投屏保活",
        false
    )
)
