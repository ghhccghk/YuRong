package top.ghhccghk.yadea.instrumentskin

import kotlinx.coroutines.CancellationException

/**
 * 仪表皮肤预设，对应原 InstrumentSkinPreset。
 */
enum class InstrumentSkinPreset(val label: String) {
    PortraitOnly("换小圈"),
    BackgroundOnly("换背景"),
    PortraitAndBackground("小圈+背景"),
}

/**
 * 皮肤生成器：以参考包 20 张图为背景，按预设把用户图片绘制到对应区域，
 * 输出与参考包相同的 20 帧 JPEG + version.ini + 皮肤包。
 */
class InstrumentSkinGenerator {

    private companion object {
        const val TARGET_PACK_BYTES = 332_496
        val JPEG_QUALITIES = listOf(88, 75, 60, 45, 30, 20, 10)
    }

    private data class Pose(val scale: Float, val dx: Float, val dy: Float)

    private val shrink = listOf(
        Pose(1.0f, 0.0f, 0.0f),
        Pose(0.91806f, 14.376f, 18.193f),
        Pose(0.7318f, 50.331f, 63.339f),
        Pose(0.63905f, 66.841f, 85.647f),
        Pose(0.57432f, 79.495f, 101.228f),
        Pose(0.53884f, 86.275f, 109.131f),
        Pose(0.522f, 88.899f, 113.921f),
        Pose(0.53362f, 86.338f, 111.261f),
        Pose(0.55f, 84.0f, 108.0f),
    )

    private val enlarge = listOf(
        Pose(0.55f, 84.0f, 108.0f),
        Pose(0.66323f, 63.625f, 79.761f),
        Pose(0.84676f, 27.91f, 35.82f),
        Pose(0.92791f, 12.932f, 17.374f),
        Pose(0.97936f, 4.523f, 4.054f),
        Pose(1.01649f, -3.184f, -4.863f),
        Pose(1.03206f, -7.666f, -8.694f),
        Pose(1.01363f, -1.411f, -4.235f),
        Pose(1.0f, 0.0f, 0.0f),
    )

    private fun pose(frameName: String): Pose = when {
        frameName.startsWith("FD1_") -> shrink[frameName.removePrefix("FD1_").removeSuffix(".jpg").toInt() - 1]
        frameName.startsWith("SX1_") -> enlarge[frameName.removePrefix("SX1_").removeSuffix(".jpg").toInt() - 1]
        frameName == "LEVEL_1.jpg" -> shrink[0]
        frameName == "LEVEL_2.jpg" -> shrink[8]
        else -> Pose(1.0f, 0.0f, 0.0f)
    }

    suspend fun generate(
        referencePack: ByteArray,
        preset: InstrumentSkinPreset,
        portraitSource: String?,
        backgroundSource: String?,
        onStatus: (String) -> Unit = {},
        cancelled: () -> Boolean = { false },
    ): ByteArray {
        if (cancelled()) throw CancellationException("皮肤任务已停止")

        val reference = InstrumentSkinPack.parse(referencePack)
        val portrait = portraitSource?.let { decodeInstrumentImage(it) }
        val background = backgroundSource?.let { decodeInstrumentImage(it) }

        require(preset == InstrumentSkinPreset.BackgroundOnly || portrait != null) {
            "小圈预设必须选择图片"
        }
        require(preset == InstrumentSkinPreset.PortraitOnly || background != null) {
            "背景预设必须选择图片"
        }

        val frames = LinkedHashMap<String, InstrumentRaster>()
        try {
            // 先统一渲染 20 帧，再按原 App 的质量阶梯反复编码直到满足仪表包大小。
            InstrumentSkinPack.IMAGE_NAMES.forEachIndexed { index, frameName ->
                if (cancelled()) throw CancellationException("皮肤任务已停止")
                onStatus("正在生成皮肤帧 ${index + 1}/20")
                val referenceFrame = decodeInstrumentImage(reference.getValue(frameName))
                    ?: error("参考皮肤帧缺失：$frameName")
                val frame = try {
                    renderFrame(
                        reference = referenceFrame,
                        frameName = frameName,
                        preset = preset,
                        portrait = portrait,
                        background = background,
                    )
                } finally {
                    referenceFrame.release()
                }
                frames[frameName] = frame
            }

            if (cancelled()) throw CancellationException("皮肤任务已停止")
            JPEG_QUALITIES.forEachIndexed { qualityIndex, quality ->
                if (cancelled()) throw CancellationException("皮肤任务已停止")
                onStatus("正在压缩皮肤 ${qualityIndex + 1}/${JPEG_QUALITIES.size}，JPEG=$quality")
                val files = LinkedHashMap<String, ByteArray>()
                InstrumentSkinPack.IMAGE_NAMES.forEach { frameName ->
                    if (cancelled()) throw CancellationException("皮肤任务已停止")
                    files[frameName] = encodeInstrumentJpeg(frames.getValue(frameName), quality)
                }
                files["version.ini"] = InstrumentSkinPack.versionIni(files)
                val pack = InstrumentSkinPack.build(files)
                // 生成后自校验，与原实现一致。
                InstrumentSkinPack.parse(pack)
                if (pack.size <= TARGET_PACK_BYTES) return pack
            }
            error("图片细节过多，压缩到最低质量后仍超过仪表皮肤上限")
        } finally {
            frames.values.forEach { it.release() }
            portrait?.release()
            background?.release()
        }
    }

    private suspend fun renderFrame(
        reference: InstrumentRaster,
        frameName: String,
        preset: InstrumentSkinPreset,
        portrait: InstrumentRaster?,
        background: InstrumentRaster?,
    ): InstrumentRaster {
        val canvas = createInstrumentCanvas(800, 480)
        val p = pose(frameName)
        val cx = p.scale * 184f + p.dx
        val cy = p.scale * 240f + p.dy
        val radius = p.scale * 306f

        when (preset) {
            InstrumentSkinPreset.PortraitOnly -> {
                // 保留参考背景，仅替换圆形小圈。
                canvas.drawCenterCrop(reference, 0f, 0f, 800f, 480f)
                canvas.drawCircleColor(cx, cy, radius, 0xFF000000.toInt())
                portrait?.let { canvas.drawCirclePortrait(it, cx, cy, radius) }
                canvas.drawCircleColor(cx, cy, radius, 0x22000000)
            }
            InstrumentSkinPreset.BackgroundOnly -> {
                canvas.fillColor(0xFF000000.toInt())
                background?.let { canvas.drawCenterCrop(it, 0f, 0f, 800f, 480f) }
                canvas.drawCircleStroke(cx, cy, radius - 1f, 0x99333333.toInt(), 2f)
            }
            InstrumentSkinPreset.PortraitAndBackground -> {
                canvas.fillColor(0xFF000000.toInt())
                background?.let { canvas.drawCenterCrop(it, 0f, 0f, 800f, 480f) }
                portrait?.let { canvas.drawCirclePortrait(it, cx, cy, radius) }
            }
        }
        return canvas
    }

}
