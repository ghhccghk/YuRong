package top.ghhccghk.yadea.mirror

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
}
