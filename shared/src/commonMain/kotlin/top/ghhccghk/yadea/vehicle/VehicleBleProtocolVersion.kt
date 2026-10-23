package top.ghhccghk.yadea.vehicle

/**
 * 原 App `BleControlManager.N` 初始化时写入的 BLE/App 版本前缀。
 *
 * 原包版本 `8.8.9` 被拆成 `"08"` 和 `"08"`，命令管理器按
 * `bleCommSmartVersion + bleCommBigVersion + appSmartVersion + appBigVersion`
 * 拼接为 `01 + 00 + 08 + 08`。
 */
internal object VehicleBleProtocolVersion {
    const val ORIGINAL_APP_PREFIX = "01000808"
}
