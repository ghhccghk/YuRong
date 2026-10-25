package top.ghhccghk.yadea.instrumentskin

/**
 * 当前平台暂未实现仪表位图管线，返回 null / 抛错以保持编译与语义明确。
 */
actual suspend fun decodeInstrumentImage(source: String): InstrumentRaster? = null
actual suspend fun decodeInstrumentImage(bytes: ByteArray): InstrumentRaster? = null
actual fun createInstrumentCanvas(width: Int, height: Int): InstrumentRaster =
    throw UnsupportedOperationException("仪表画布仅支持 Android/Desktop")
actual fun InstrumentRaster.fillColor(argb: Int) {}
actual fun InstrumentRaster.drawCenterCrop(photo: InstrumentRaster, left: Float, top: Float, right: Float, bottom: Float) {}
actual fun InstrumentRaster.drawCirclePortrait(photo: InstrumentRaster, cx: Float, cy: Float, radius: Float) {}
actual fun InstrumentRaster.drawCircleColor(cx: Float, cy: Float, radius: Float, color: Int) {}
actual fun InstrumentRaster.drawCircleStroke(cx: Float, cy: Float, radius: Float, color: Int, strokeWidth: Float) {}
actual suspend fun encodeInstrumentJpeg(image: InstrumentRaster, quality: Int): ByteArray =
    throw UnsupportedOperationException("仪表 JPEG 编码仅支持 Android/Desktop")
actual fun loadInstrumentSkinReferencePack(): ByteArray? = null
