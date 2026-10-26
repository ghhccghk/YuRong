package top.ghhccghk.yadea

import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.ghhccghk.yadea.mirror.MediaSourceInfo
import top.ghhccghk.yadea.mirror.MediaSourcePicker

private const val PREVIEW_MAX_EDGE = 640

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

    override suspend fun inspectVideo(uri: String): MediaSourceInfo? = withContext(Dispatchers.IO) {
        if (uri.isBlank()) return@withContext null
        val parsed = Uri.parse(uri)
        val retriever = MediaMetadataRetriever()
        try {
            when (parsed.scheme?.lowercase()) {
                "http", "https", "rtsp" ->
                    retriever.setDataSource(uri, emptyMap())
                "content", "file", "android.resource" ->
                    retriever.setDataSource(activity, parsed)
                null ->
                    retriever.setDataSource(uri)
                else ->
                    retriever.setDataSource(activity, parsed)
            }

            val rawWidth = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
                ?: 0
            val rawHeight = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
                ?: 0
            if (rawWidth <= 0 || rawHeight <= 0) return@withContext null

            val rotation = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                ?.let { ((it % 360) + 360) % 360 }
                ?: 0
            val orientedWidth = if (rotation % 180 == 90) rawHeight else rawWidth
            val orientedHeight = if (rotation % 180 == 90) rawWidth else rawHeight
            val previewJpeg = extractPreviewJpeg(retriever, rotation)

            MediaSourceInfo(
                width = orientedWidth,
                height = orientedHeight,
                previewJpeg = previewJpeg,
            )
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractPreviewJpeg(
        retriever: MediaMetadataRetriever,
        rotation: Int,
    ): ByteArray? {
        val frame = runCatching {
            retriever.getFrameAtTime(
                0L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            )
        }.getOrNull() ?: return null

        var output: Bitmap? = null
        return try {
            val maxEdge = maxOf(frame.width, frame.height).coerceAtLeast(1)
            val scale = minOf(1f, PREVIEW_MAX_EDGE.toFloat() / maxEdge)
            output = if (rotation == 0 && scale >= 1f) {
                frame
            } else {
                val matrix = Matrix().apply {
                    if (rotation != 0) postRotate(rotation.toFloat())
                    if (scale < 1f) postScale(scale, scale)
                }
                Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, matrix, true)
            }
            ByteArrayOutputStream().use { stream ->
                if (!output.compress(Bitmap.CompressFormat.JPEG, 72, stream)) {
                    null
                } else {
                    stream.toByteArray()
                }
            }
        } finally {
            if (!frame.isRecycled) frame.recycle()
            if (output != null && output !== frame && !output.isRecycled) {
                output.recycle()
            }
        }
    }
}
