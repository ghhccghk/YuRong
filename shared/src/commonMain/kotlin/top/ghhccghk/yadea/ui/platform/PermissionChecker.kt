package top.ghhccghk.yadea.ui

/**
 * 权限检查接口 - 由平台实现
 */
interface PermissionChecker {
    /** 检查单个权限是否已授予 */
    fun hasPermission(permission: String): Boolean

    /** 检查多个权限，返回缺失的 */
    fun getMissingPermissions(permissions: List<String>): List<String>

    /** 请求权限 */
    fun requestPermissions(permissions: List<String>)

    companion object {
        /** 摄像头投屏所需权限 */
        val CAMERA_PERMISSIONS = listOf("android.permission.CAMERA")

        /** WiFi/BLE 扫描所需权限 */
        val SCAN_PERMISSIONS = buildList {
            add("android.permission.ACCESS_FINE_LOCATION")
            add("android.permission.ACCESS_COARSE_LOCATION")
            add("android.permission.BLUETOOTH_SCAN")
            add("android.permission.BLUETOOTH_CONNECT")
            add("android.permission.NEARBY_WIFI_DEVICES")
        }

        /** 屏幕录制所需权限 */
        val SCREEN_PERMISSIONS = listOf(
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.POST_NOTIFICATIONS"
        )

        /** 根据投屏源类型获取所需权限 */
        fun permissionsForSource(sourceIndex: Int): List<String> {
            return when (sourceIndex) {
                0 -> CAMERA_PERMISSIONS  // 摄像头
                1 -> SCREEN_PERMISSIONS   // 屏幕
                2 -> emptyList()          // 视频 (SAF 不需要权限)
                else -> emptyList()
            }
        }
    }
}
