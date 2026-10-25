package top.ghhccghk.yadea.instrumentskin

/**
 * 跨平台位图抽象，仅暴露仪表换肤/壁纸需要的操作。
 * 各平台用原生位图实现（Android Bitmap / JVM BufferedImage）。
 */
interface InstrumentRaster {
    val width: Int
    val height: Int

    /** 释放平台位图资源；JVM 等托管实现可以保持默认空操作。 */
    fun release() {}
}

/** 从平台提供的数据源（Android content URI / 本地路径 / URL）解码图片。 */
expect suspend fun decodeInstrumentImage(source: String): InstrumentRaster?

/** 从已编码图片字节（JPEG/PNG）解码。 */
expect suspend fun decodeInstrumentImage(bytes: ByteArray): InstrumentRaster?

/** 创建画布。 */
expect fun createInstrumentCanvas(width: Int, height: Int): InstrumentRaster

/** 填充 ARGB 色。 */
expect fun InstrumentRaster.fillColor(argb: Int)

/** 以 center-crop 方式把 [photo] 绘制进目标矩形。 */
expect fun InstrumentRaster.drawCenterCrop(
    photo: InstrumentRaster,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
)

/** 把 [photo] 按圆形区域绘制，圆心 (cx, cy)，半径 radius。 */
expect fun InstrumentRaster.drawCirclePortrait(
    photo: InstrumentRaster,
    cx: Float,
    cy: Float,
    radius: Float,
)

/** 在圆形区域内填充颜色，用于复刻原 App 的小圈底色与暗色蒙层。 */
expect fun InstrumentRaster.drawCircleColor(
    cx: Float,
    cy: Float,
    radius: Float,
    color: Int,
)

/** 绘制描边圆。 */
expect fun InstrumentRaster.drawCircleStroke(
    cx: Float,
    cy: Float,
    radius: Float,
    color: Int,
    strokeWidth: Float,
)

/** 编码为 JPEG。 */
expect suspend fun encodeInstrumentJpeg(image: InstrumentRaster, quality: Int): ByteArray

/** 加载皮肤生成所需的参考包（captured_800x480.bin）。 */
expect fun loadInstrumentSkinReferencePack(): ByteArray?
