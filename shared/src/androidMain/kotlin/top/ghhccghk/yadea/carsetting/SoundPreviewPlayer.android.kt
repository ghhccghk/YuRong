package top.ghhccghk.yadea.carsetting

import android.media.MediaPlayer

actual fun createSoundPreviewPlayer(): SoundPreviewPlayer = AndroidSoundPreviewPlayer()

private class AndroidSoundPreviewPlayer : SoundPreviewPlayer {
    private var player: MediaPlayer? = null

    override val isAvailable: Boolean = true

    override fun play(url: String, onFinished: () -> Unit) {
        stop()
        val next = MediaPlayer()
        player = next
        runCatching {
            next.setDataSource(url)
            next.setOnPreparedListener { prepared ->
                if (player === prepared) prepared.start()
            }
            next.setOnCompletionListener {
                if (player === it) {
                    player = null
                    it.release()
                }
                onFinished()
            }
            next.setOnErrorListener { failed, _, _ ->
                if (player === failed) {
                    player = null
                }
                failed.release()
                onFinished()
                true
            }
            next.prepareAsync()
        }.onFailure {
            if (player === next) {
                player = null
            }
            next.release()
            onFinished()
        }
    }

    override fun stop() {
        val current = player ?: return
        player = null
        runCatching { current.stop() }
        current.release()
    }

    override fun release() {
        stop()
    }
}
