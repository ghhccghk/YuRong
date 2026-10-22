package top.ghhccghk.yadea.carsetting

/** 自定义音效试听。当前只有 Android 提供真实播放器。 */
interface SoundPreviewPlayer {
    val isAvailable: Boolean
    fun play(url: String, onFinished: () -> Unit)
    fun stop()
    fun release()
}

expect fun createSoundPreviewPlayer(): SoundPreviewPlayer

internal object UnavailableSoundPreviewPlayer : SoundPreviewPlayer {
    override val isAvailable: Boolean = false
    override fun play(url: String, onFinished: () -> Unit) = Unit
    override fun stop() = Unit
    override fun release() = Unit
}
