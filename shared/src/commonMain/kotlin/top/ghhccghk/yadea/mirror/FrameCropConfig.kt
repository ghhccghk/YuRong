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
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

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
     * 生成像素宽高比严格等于 [targetRatio] 的裁剪区域。
     *
     * 归一化坐标同时受源宽高影响，不能直接令 normalizedWidth /
     * normalizedHeight 等于投屏比例。这里先换算到源像素比例，再以当前中心
     * 和期望宽度生成裁剪框。
     */
    fun withAspectRatio(
        targetRatio: Float,
        sourceWidth: Int,
        sourceHeight: Int,
        centerX: Float = this.centerX,
        centerY: Float = this.centerY,
        requestedWidth: Float = normalizedWidth,
    ): FrameCropConfig {
        if (targetRatio <= 0f || sourceWidth <= 0 || sourceHeight <= 0) return this

        val sourceRatio = sourceWidth.toFloat() / sourceHeight
        val normalizedHeightPerWidth = sourceRatio / targetRatio
        val maxWidth = minOf(1f, 1f / normalizedHeightPerWidth)
        val width = requestedWidth
            .coerceAtLeast(0.01f)
            .coerceAtMost(maxWidth)
        val height = (width * normalizedHeightPerWidth).coerceAtMost(1f)
        val halfWidth = width / 2f
        val halfHeight = height / 2f
        val safeCenterX = centerX.coerceIn(halfWidth, 1f - halfWidth)
        val safeCenterY = centerY.coerceIn(halfHeight, 1f - halfHeight)

        return FrameCropConfig(
            left = safeCenterX - halfWidth,
            top = safeCenterY - halfHeight,
            right = safeCenterX + halfWidth,
            bottom = safeCenterY + halfHeight,
        )
    }

    /** 保留旧方法名，避免已发布调用点失效。 */
    fun withAspectRation(targetRatio: Float, sourceWidth: Int, sourceHeight: Int): FrameCropConfig =
        withAspectRatio(targetRatio, sourceWidth, sourceHeight)

    /** 平移裁剪框，同时保持在源画面边界内。 */
    fun moveBy(deltaX: Float, deltaY: Float): FrameCropConfig {
        val width = normalizedWidth
        val height = normalizedHeight
        val safeLeft = (left + deltaX).coerceIn(0f, 1f - width)
        val safeTop = (top + deltaY).coerceIn(0f, 1f - height)
        return FrameCropConfig(
            left = safeLeft,
            top = safeTop,
            right = safeLeft + width,
            bottom = safeTop + height,
        )
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
