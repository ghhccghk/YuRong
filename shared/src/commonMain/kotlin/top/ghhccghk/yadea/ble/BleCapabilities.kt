package top.ghhccghk.yadea.ble

/** BLE 平台能力声明。未明确支持的能力保持 false。 */
data class BleCapabilities(
    val canScan: Boolean = false,
    val canScanSilently: Boolean = false,
    val requiresUserGestureForScan: Boolean = true,
    val requiresUserGestureForConnect: Boolean = true,
    val canRequestMtu: Boolean = false,
    val canProvideRawAdvertisement: Boolean = false,
    val canProvideMacAddress: Boolean = false,
    val canReconnectSavedDevice: Boolean = false,
    val supportsBackgroundConnection: Boolean = false,
    val supportsStateRestoration: Boolean = false,
    val supportsIndications: Boolean = false,
    val supportsWriteWithoutResponse: Boolean = false,
) {
    companion object {
        val Unsupported = BleCapabilities()

        val Android = BleCapabilities(
            canScan = true,
            canScanSilently = true,
            requiresUserGestureForScan = false,
            requiresUserGestureForConnect = false,
            canRequestMtu = true,
            canProvideRawAdvertisement = true,
            canProvideMacAddress = true,
            canReconnectSavedDevice = true,
            supportsIndications = true,
            supportsWriteWithoutResponse = true,
        )
    }
}

/** 平台蓝牙授权状态。Unknown 不代表已授权。 */
enum class BlePermissionState {
    Unknown,
    NotRequired,
    Granted,
    Denied,
    Restricted,
    PermanentlyDenied,
}