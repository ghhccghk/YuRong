package top.ghhccghk.yadea

import android.content.Context
import top.ghhccghk.yadea.ble.AndroidBleAdapter

/**
 * 应用进程级 BLE 会话。
 *
 * Activity 重建不会销毁 GATT；连接期间由 [VehicleConnectionService] 保持进程
 * 前台，用户真正退出应用时再统一关闭。
 */
object AndroidVehicleBleSession {
    private var adapter: AndroidBleAdapter? = null

    @Synchronized
    fun get(context: Context): AndroidBleAdapter =
        adapter ?: AndroidBleAdapter(context.applicationContext).also { adapter = it }

    @Synchronized
    fun close() {
        adapter?.close()
        adapter = null
    }
}
