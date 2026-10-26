package top.ghhccghk.yadea.mirror

data class MediaSourceInfo(
    val width: Int,
    val height: Int,
    val previewJpeg: ByteArray? = null,
) {
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height else 16f / 9f
}

/**
 * 平台媒体选择器。
 *
 * Android 通过系统文档选择器返回 content URI；桌面端可以只使用 URI 输入框。
 */
interface MediaSourcePicker {
    val canPickVideo: Boolean
        get() = false

    fun pickVideo(onResult: (String?) -> Unit) {
        onResult(null)
    }

    /**
     * Reads oriented dimensions and a small first-frame preview without starting
     * the mirror protocol. Unsupported platforms return null.
     */
    suspend fun inspectVideo(uri: String): MediaSourceInfo? = null
}
