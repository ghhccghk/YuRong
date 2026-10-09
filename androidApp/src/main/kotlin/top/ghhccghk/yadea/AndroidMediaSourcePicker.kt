package top.ghhccghk.yadea

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import top.ghhccghk.yadea.mirror.MediaSourcePicker

/**
 * 使用 Android Storage Access Framework 选择视频文件。
 */
class AndroidMediaSourcePicker(
    private val activity: ComponentActivity,
) : MediaSourcePicker {

    private var pendingCallback: ((String?) -> Unit)? = null

    private val videoPicker = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val callback = pendingCallback
        pendingCallback = null
        val result = uri?.toString()
        if (uri != null) {
            try {
                activity.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
                // 某些提供器不支持持久化授权，但当前进程仍可读取。
            }
        }
        callback?.invoke(result)
    }

    override val canPickVideo: Boolean
        get() = true

    override fun pickVideo(onResult: (String?) -> Unit) {
        if (pendingCallback != null) {
            onResult(null)
            return
        }
        pendingCallback = onResult
        try {
            videoPicker.launch(arrayOf("video/*"))
        } catch (error: Throwable) {
            pendingCallback = null
            onResult(null)
        }
    }
}
