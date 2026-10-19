package top.ghhccghk.yadea

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

/**
 * 车辆 BLE 连接前台服务。
 *
 * 服务本身不持有 BluetoothGatt；它负责让进程在车辆已连接期间保持
 * connectedDevice 前台服务类型，避免应用切到后台后连接被系统过早回收。
 */
class VehicleConnectionService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        synchronized(lock) {
            running = true
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        synchronized(lock) {
            running = false
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "车辆连接",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "车辆蓝牙连接期间显示后台服务状态"
                },
            )
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("车辆已连接")
            .setContentText("正在保持车辆蓝牙连接")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "vehicle_connection"
        private const val NOTIFICATION_ID = 43
        private val lock = Any()
        private var running = false

        fun start(context: Context) {
            synchronized(lock) {
                if (running) return
            }
            ContextCompat.startForegroundService(
                context,
                Intent(context, VehicleConnectionService::class.java),
            )
        }

        fun stop(context: Context) {
            synchronized(lock) {
                running = false
            }
            context.stopService(Intent(context, VehicleConnectionService::class.java))
        }
    }
}
