package top.ghhccghk.yadea

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import top.ghhccghk.yadea.instrumentskin.InstrumentImagePicker

/**
 * Android 图片选择器：用于仪表壁纸/皮肤图片来源。
 */
class AndroidInstrumentImagePicker(
    private val activity: ComponentActivity,
) : InstrumentImagePicker {

    private var pendingCallback: ((String?) -> Unit)? = null

    private val imagePicker = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
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

    override val canPick: Boolean
        get() = true

    override fun pick(onResult: (String?) -> Unit) {
        if (pendingCallback != null) {
            onResult(null)
            return
        }
        pendingCallback = onResult
        try {
            imagePicker.launch(arrayOf("image/*"))
        } catch (error: Throwable) {
            pendingCallback = null
            onResult(null)
        }
    }
}