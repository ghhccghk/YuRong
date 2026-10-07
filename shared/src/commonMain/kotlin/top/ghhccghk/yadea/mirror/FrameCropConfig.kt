package top.ghhccghk.yadea.mirror

/**
 * 投屏裁剪区域配置
 *
 * 使用归一化坐标 (0.0 ~ 1.0) 定义裁剪矩形，
 * 与源分辨率无关，方便 UI 调节。
 */
data class FrameCropConfig(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    val isValid: Boolean
        get() = left >= 0f && top >= 0f && right <= 1f && bottom <= 1f &&
                left < right && top < bottom

    val normalizedWidth: Float get() = right - left
    val normalizedHeight: Float get() = bottom - top

    /**
     * 将归一化坐标转换为像素矩形
     */
    fun toPixelRect(sourceWidth: Int, sourceHeight: Int): PixelRect {
        val x = (left * sourceWidth).toInt().coerceIn(0, sourceWidth - 1)
        val y = (top * sourceHeight).toInt().coerceIn(0, sourceHeight - 1)
        val w = ((right - left) * sourceWidth).toInt().coerceIn(1, sourceWidth - x)
        val h = ((bottom - top) * sourceHeight).toInt().coerceIn(1, sourceHeight - y)
        return PixelRect(x, y, w, h)
    }

    /**
     * 保持宽高比并居中裁剪
     */
    fun withAspectRation(targetRatio: Float, sourceWidth: Int, sourceHeight: Int): FrameCropConfig {
        val sourceRatio = sourceWidth.toFloat() / sourceHeight
        val centerH = (top + bottom) / 2f
        val centerV = (left + right) / 2f

        return if (sourceRatio > targetRatio) {
            // 源更宽，按高度限制宽度
            val halfW = normalizedHeight * targetRatio / sourceRatio / 2f
            FrameCropConfig(
                left = (centerV - halfW).coerceIn(0f, 1f),
                top = top,
                right = (centerV + halfW).coerceIn(0f, 1f),
                bottom = bottom
            )
        } else {
            // 源更高，按宽度限制高度
            val halfH = normalizedWidth * sourceRatio / targetRatio / 2f
            FrameCropConfig(
                left = left,
                top = (centerH - halfH).coerceIn(0f, 1f),
                right = right,
                bottom = (centerH + halfH).coerceIn(0f, 1f)
            )
        }
    }

    companion object {
        /** 全幅 */
        val FULL = FrameCropConfig()

        /** 居中 50% */
        val CENTER = FrameCropConfig(0.25f, 0.25f, 0.75f, 0.75f)

        /** 上半区域 */
        val TOP_HALF = FrameCropConfig(0f, 0f, 1f, 0.5f)

        /** 下半区域 */
        val BOTTOM_HALF = FrameCropConfig(0f, 0.5f, 1f, 1f)
    }
}

/**
 * 像素矩形
 */
data class PixelRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)
