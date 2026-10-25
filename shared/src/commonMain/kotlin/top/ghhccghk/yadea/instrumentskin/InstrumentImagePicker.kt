package top.ghhccghk.yadea.instrumentskin

/**
 * 平台图片选择器（仪表壁纸/皮肤图片来源）。
 */
interface InstrumentImagePicker {
    val canPick: Boolean
        get() = false

    fun pick(onResult: (String?) -> Unit) {
        onResult(null)
    }
}

/** 生成壁纸图片。格式：0=NV21 JPEG、1=PNG、2=JPEG、3=Jieli RLE。 */
expect suspend fun prepareInstrumentWallpaper(
    source: String,
    width: Int,
    height: Int,
    format: Int,
): ByteArray?