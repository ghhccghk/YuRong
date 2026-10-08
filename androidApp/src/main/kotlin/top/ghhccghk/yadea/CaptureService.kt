package top.ghhccghk.yadea

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

/**
 * 屏幕录制前台服务。
 *
 * 只能在用户完成系统录屏授权后调用 [start]。服务启动后必须立即进入前台，
 * 以满足 Android 对 mediaProjection 前台服务的启动时限要求。
 */
class CaptureService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground 必须在服务启动后的 5 秒内调用。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        notifyForegroundReady()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        synchronized(foregroundLock) {
            foregroundReady = false
            pendingForegroundCallbacks.clear()
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "屏幕录制",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "屏幕投屏期间显示录制服务状态"
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("屏幕录制")
            .setContentText("投屏服务正在运行")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "screen_capture"
        private const val NOTIFICATION_ID = 42
        private val foregroundLock = Any()
        private val pendingForegroundCallbacks = mutableListOf<() -> Unit>()
        private var foregroundReady = false

        /**
         * 在用户授权录屏之后启动服务。resultData 不在这里缓存或交给
         * MediaProjectionManager；Android 14+ 的授权 Intent 必须由
         * [top.ghhccghk.yadea.mirror.source.AndroidFrameSourceFactory]
         * 一次性传给 getMediaProjection。[onForeground] 会在 startForeground
         * 完成后调用，调用方应在其中创建屏幕帧源。
         */
        fun start(
            context: Context,
            resultCode: Int,
            @Suppress("UNUSED_PARAMETER") resultData: Intent,
            onForeground: () -> Unit = {}
        ) {
            if (resultCode != android.app.Activity.RESULT_OK) {
                throw IllegalStateException(
                    "屏幕录制授权失败：resultCode=$resultCode，必须为 Activity.RESULT_OK"
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                throw SecurityException("启动屏幕录制服务前必须授予 POST_NOTIFICATIONS 权限")
            }

            synchronized(foregroundLock) {
                if (foregroundReady) {
                    onForeground()
                } else {
                    pendingForegroundCallbacks += onForeground
                }
            }

            // resultData 不复制、不保存到 Intent extra；onForeground 内应立即将它一次性交给 getMediaProjection。
            ContextCompat.startForegroundService(context, Intent(context, CaptureService::class.java))
        }

        /** 帧源完全停止并释放 MediaProjection 后调用。 */
        fun stop(context: Context) {
            context.stopService(Intent(context, CaptureService::class.java))
        }

        private fun notifyForegroundReady() {
            val callbacks = synchronized(foregroundLock) {
                foregroundReady = true
                pendingForegroundCallbacks.toList().also { pendingForegroundCallbacks.clear() }
            }
            callbacks.forEach { it() }
        }
    }
}
