package top.ghhccghk.yadea.vehicle

/**
 * 原 App `BleControlManager.N` 初始化时写入的 BLE/App 版本前缀。
 *
 * 运行时字段为 `bleCommSmartVersion=01`、`bleCommBigVersion=00`、
 * `appSmartVersion=01`、`appBigVersion=01`，命令管理器按
 * `bleCommSmartVersion + bleCommBigVersion + appSmartVersion + appBigVersion`
 * 拼接为 `01 + 00 + 01 + 01`。该值不是 APK 包版本。
 */
internal object VehicleBleProtocolVersion {
    const val ORIGINAL_APP_PREFIX = "01000101"
}
