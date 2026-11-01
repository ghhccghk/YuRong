package top.ghhccghk.yadea.mirror

/**
 * JPEG quality presets exposed by the mirror UI.
 *
 * Resolution is always the display size negotiated with the vehicle. These
 * presets only change JPEG compression quality so source capture and protocol
 * cadence remain independent from the selected preset.
 */
enum class MirrorQualityPreset(
    val label: String,
) {
    Auto("自动"),
    Smooth("流畅"),
    Balanced("均衡"),
    Clear("清晰"),
    Custom("自定义"),
}

data class MirrorQualitySettings(
    val preset: MirrorQualityPreset = MirrorQualityPreset.Auto,
    val customQuality: Int = DEFAULT_CUSTOM_QUALITY,
) {
    fun normalized(): MirrorQualitySettings = copy(
        customQuality = customQuality.coerceIn(
            MIN_CUSTOM_QUALITY,
            MAX_CUSTOM_QUALITY,
        ),
    )

    fun initialQuality(targetWidth: Int, targetHeight: Int): Int = when (preset) {
        MirrorQualityPreset.Auto ->
            if (targetWidth * targetHeight > LARGE_FRAME_PIXELS) 72 else 80

        MirrorQualityPreset.Smooth -> 60
        MirrorQualityPreset.Balanced -> 72
        MirrorQualityPreset.Clear -> 84
        MirrorQualityPreset.Custom -> normalized().customQuality
    }

    fun retryQuality(initialQuality: Int): Int = when (preset) {
        MirrorQualityPreset.Auto -> (initialQuality - RETRY_STEP).coerceAtLeast(
            MIN_AUTO_RETRY_QUALITY,
        )

        MirrorQualityPreset.Smooth -> (initialQuality - RETRY_STEP).coerceAtLeast(
            MIN_PRESET_RETRY_QUALITY,
        )

        MirrorQualityPreset.Balanced -> (initialQuality - RETRY_STEP).coerceAtLeast(
            MIN_PRESET_RETRY_QUALITY,
        )

        MirrorQualityPreset.Clear -> (initialQuality - RETRY_STEP).coerceAtLeast(
            MIN_PRESET_RETRY_QUALITY,
        )

        MirrorQualityPreset.Custom -> (initialQuality - RETRY_STEP).coerceAtLeast(
            MIN_CUSTOM_QUALITY,
        )
    }

    companion object {
        const val MIN_CUSTOM_QUALITY = 40
        const val MAX_CUSTOM_QUALITY = 95
        const val DEFAULT_CUSTOM_QUALITY = 72

        private const val RETRY_STEP = 8
        private const val MIN_AUTO_RETRY_QUALITY = 56
        private const val MIN_PRESET_RETRY_QUALITY = 52
        private const val LARGE_FRAME_PIXELS = 320 * 240
    }
}

data class JpegEncoding(
    val bytes: ByteArray,
    val quality: Int,
    val retried: Boolean,
)
