package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 权限管理页面组件
 *
 * 显示和管理应用所需权限的状态
 */
@Composable
fun PermissionPanel(
    permissions: List<PermissionStatus>,
    onRequestPermission: (String) -> Unit,
    onRequestAll: () -> Unit,
) {
    val missingPermissions = permissions.filterNot(PermissionStatus::granted)
    AppSection(
        title = "权限管理",
        modifier = Modifier.fillMaxWidth(),
    ) {
        AppListRow(
            title = "权限状态",
            value = if (missingPermissions.isEmpty()) "全部已授权" else "${missingPermissions.size} 项待授权",
            trailing = {
                if (missingPermissions.isNotEmpty()) {
                    AppButton(onClick = onRequestAll) {
                        AppText("全部授权")
                    }
                }
            },
            showDivider = permissions.isNotEmpty(),
        )
        permissions.forEachIndexed { index, permission ->
            AppListRow(
                title = permission.name,
                summary = permission.description,
                value = if (permission.granted) "已授权" else "未授权",
                showDivider = index < permissions.lastIndex,
                trailing = {
                    if (!permission.granted) {
                        AppOutlinedButton(
                            onClick = { onRequestPermission(permission.permission) },
                        ) {
                            AppText("授权")
                        }
                    }
                },
            )
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
    val granted: Boolean,
)

/**
 * 默认权限列表
 */
val defaultPermissions = listOf(
    PermissionStatus(
        "android.permission.BLUETOOTH_SCAN",
        "蓝牙扫描",
        "扫描附近 BLE 设备",
        false,
    ),
    PermissionStatus(
        "android.permission.BLUETOOTH_CONNECT",
        "蓝牙连接",
        "连接 BLE 仪表设备",
        false,
    ),
    PermissionStatus(
        "android.permission.ACCESS_FINE_LOCATION",
        "精确位置",
        "WiFi 扫描和 BLE 定位",
        false,
    ),
    PermissionStatus(
        "android.permission.ACCESS_WIFI_STATE",
        "WiFi 状态",
        "读取 WiFi 连接状态",
        true,
    ),
    PermissionStatus(
        "android.permission.CHANGE_WIFI_STATE",
        "WiFi 控制",
        "连接/断开 WiFi",
        true,
    ),
    PermissionStatus(
        "android.permission.CAMERA",
        "相机",
        "摄像头投屏",
        false,
    ),
    PermissionStatus(
        "android.permission.INTERNET",
        "网络",
        "TCP/UDP 通信",
        true,
    ),
    PermissionStatus(
        "android.permission.FOREGROUND_SERVICE",
        "前台服务",
        "后台投屏保活",
        false,
    ),
)
