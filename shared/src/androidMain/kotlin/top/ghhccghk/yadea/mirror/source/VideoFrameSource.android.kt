package top.ghhccghk.yadea.mirror.source

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import top.ghhccghk.yadea.mirror.FrameCropConfig
import top.ghhccghk.yadea.mirror.FramePreviewDispatcher
import top.ghhccghk.yadea.mirror.FrameSource
import top.ghhccghk.yadea.mirror.FrameSourceDimensions
import top.ghhccghk.yadea.mirror.PlatformBitmap

private const val MAX_CAPTURE_EDGE = 1280
private const val PAUSE_POLL_INTERVAL_MS = 50L
private const val CODEC_TIMEOUT_US = 10_000L
private const val FRAME_WAIT_CHUNK_US = 20_000L
private const val DEFAULT_TARGET_FPS = 30
private const val NO_SEEK_REQUEST = -1L

/**
 * Android 视频/网络流帧源。
 *
 * MediaMetadataRetriever.getFrameAtTime() is designed for random thumbnails, not
 * continuous playback. Repeated closest-frame requests can seek back to the same
 * keyframe for every image, which makes long-GOP videos appear to update only once
 * every few seconds. This source uses the platform decoder as a stream: samples
 * are paced by presentation time and rendered to an ImageReader surface, while
 * FrameBus and MirrorEngine retain only the newest frame at the negotiated rate.
 */
class VideoFrameSource(
    private val context: Context,
    private val uri: Uri,
) : FrameSource {

    private val lifecycleLock = Any()
    private val running = AtomicBoolean(false)
    private val released = AtomicBoolean(false)
    private val generation = AtomicLong(0L)
    private val failureReported = AtomicBoolean(false)
    private val previewDispatcher = FramePreviewDispatcher()

    @Volatile
    private var worker: Thread? = null

    @Volatile
    private var playing = true

    @Volatile
    private var cropConfig: FrameCropConfig = FrameCropConfig.FULL

    @Volatile
    private var failureHandler: ((String) -> Unit)? = null

    @Volatile
    private var targetFps: Int = DEFAULT_TARGET_FPS

    @Volatile
    private var durationUs = 0L

    @Volatile
    private var positionUs = 0L

    @Volatile
    private var clockAnchorContentUs = 0L

    @Volatile
    private var clockAnchorElapsedUs = 0L

    @Volatile
    private var seekRequestUs = NO_SEEK_REQUEST

    @Volatile
    private var frameRotationDegrees = 0

    // ImageReader invokes its listener on one HandlerThread, so these buffers can be reused.
    private var packedRgbaBuffer: ByteBuffer? = null
    private var argbPixelBuffer: IntArray? = null
    private var lastDeliveredElapsedNanos = 0L

    val isPlaying: Boolean
        get() = playing

    val durationMs: Long
        get() = TimeUnit.MICROSECONDS.toMillis(durationUs)

    val positionMs: Long
        get() = TimeUnit.MICROSECONDS.toMillis(currentPositionUs())

    override val isRunning: Boolean
        get() = running.get()

    override fun updateCropConfig(config: FrameCropConfig) {
        if (config.isValid) cropConfig = config
    }

    override fun onFailure(handler: (String) -> Unit) {
        failureHandler = handler
    }

    override fun onSourceDimensions(handler: ((FrameSourceDimensions) -> Unit)?) {
        previewDispatcher.onSourceDimensions(handler)
    }

    override fun onPreviewFrame(handler: ((PlatformBitmap) -> Unit)?) {
        previewDispatcher.onPreviewFrame(handler)
    }

    override fun updateTargetFps(fps: Int) {
        if (fps > 0) targetFps = fps.coerceAtMost(60)
    }

    override suspend fun start(onFrame: (PlatformBitmap) -> Unit) {
        check(!released.get()) { "视频帧源已释放，不能重新启动" }
        if (uri.toString().isBlank()) {
            throw IllegalArgumentException("请先选择视频文件或填写视频地址")
        }

        val startGeneration: Long
        synchronized(lifecycleLock) {
            if (running.get()) return
            startGeneration = generation.incrementAndGet()
            durationUs = 0L
            positionUs = 0L
            clockAnchorContentUs = 0L
            clockAnchorElapsedUs = monotonicUs()
            seekRequestUs = NO_SEEK_REQUEST
            frameRotationDegrees = 0
            playing = true
            failureReported.set(false)
            lastDeliveredElapsedNanos = 0L
            running.set(true)

            worker = Thread(
                { decodeStream(startGeneration, onFrame) },
                "android-video-frame-source",
            ).apply {
                isDaemon = true
                start()
            }
        }
    }

    override fun stop() {
        val thread = synchronized(lifecycleLock) {
            if (!running.getAndSet(false)) return
            generation.incrementAndGet()
            playing = false
            worker.also { worker = null }
        }
        thread?.interrupt()
    }

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        stop()
    }

    fun play() {
        synchronized(lifecycleLock) {
            if (playing || !running.get()) return
            clockAnchorContentUs = positionUs
            clockAnchorElapsedUs = monotonicUs()
            playing = true
        }
    }

    fun pause() {
        synchronized(lifecycleLock) {
            if (!playing) return
            positionUs = currentPositionUsLocked()
            clockAnchorContentUs = positionUs
            clockAnchorElapsedUs = monotonicUs()
            playing = false
        }
    }

    fun seekTo(positionMs: Long) {
        synchronized(lifecycleLock) {
            val requestedUs = TimeUnit.MILLISECONDS.toMicros(positionMs.coerceAtLeast(0L))
            positionUs = if (durationUs > 0L) requestedUs.coerceAtMost(durationUs) else requestedUs
            clockAnchorContentUs = positionUs
            clockAnchorElapsedUs = monotonicUs()
            seekRequestUs = positionUs
        }
    }

    private fun currentPositionUs(): Long =
        synchronized(lifecycleLock) { currentPositionUsLocked() }

    private fun currentPositionUsLocked(): Long {
        if (!playing) return positionUs
        val elapsedUs = (monotonicUs() - clockAnchorElapsedUs).coerceAtLeast(0L)
        return (clockAnchorContentUs + elapsedUs).coerceAtLeast(0L)
    }

    private fun decodeStream(
        expectedGeneration: Long,
        onFrame: (PlatformBitmap) -> Unit,
    ) {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        var reader: ImageReader? = null
        var frameThread: HandlerThread? = null

        try {
            extractor = MediaExtractor().also(::setDataSource)
            val trackIndex = findVideoTrack(extractor)
            if (trackIndex < 0) {
                throw IllegalStateException("视频源中没有可解码的视频轨道")
            }
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalStateException("视频轨道缺少 MIME 类型")
            if (!mime.startsWith("video/")) {
                throw IllegalStateException("不支持的视频轨道: $mime")
            }
            val sourceWidth = format.intValue(MediaFormat.KEY_WIDTH)
            val sourceHeight = format.intValue(MediaFormat.KEY_HEIGHT)
            if (sourceWidth == null || sourceHeight == null ||
                sourceWidth <= 1 || sourceHeight <= 1
            ) {
                throw IllegalStateException("视频轨道尺寸无效: ${sourceWidth}x$sourceHeight")
            }
            val sourceDurationUs = format.longValue(MediaFormat.KEY_DURATION)
                ?.takeIf { it > 0L }
                ?: 0L
            val rotation = format.intValue(MediaFormat.KEY_ROTATION)
                ?.let(::normalizedRotation)
                ?: 0

            val thread = HandlerThread("android-video-frame-reader").apply { start() }
            frameThread = thread
            val frameReader = ImageReader.newInstance(
                sourceWidth,
                sourceHeight,
                PixelFormat.RGBA_8888,
                2,
            )
            reader = frameReader
            frameReader.setOnImageAvailableListener(
                { source -> processImage(expectedGeneration, source, onFrame) },
                Handler(thread.looper),
            )

            val mediaCodec = MediaCodec.createDecoderByType(mime)
            codec = mediaCodec
            synchronized(lifecycleLock) {
                if (!isCurrent(expectedGeneration)) return
                durationUs = sourceDurationUs
                frameRotationDegrees = rotation
                positionUs = 0L
                clockAnchorContentUs = 0L
                clockAnchorElapsedUs = monotonicUs()
            }

            mediaCodec.configure(format, frameReader.surface, null, 0)
            mediaCodec.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            mediaCodec.start()

            extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            decodeSamples(
                expectedGeneration = expectedGeneration,
                extractor = extractor,
                codec = mediaCodec,
            )
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (error: Throwable) {
            if (isCurrent(expectedGeneration)) {
                reportFailure("无法读取视频源: ${error.message ?: error::class.simpleName ?: "未知错误"}")
            }
        } finally {
            reader?.setOnImageAvailableListener(null, null)
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { reader?.close() }
            runCatching { extractor?.release() }
            frameThread?.quitSafely()
            packedRgbaBuffer = null
            argbPixelBuffer = null

            synchronized(lifecycleLock) {
                if (generation.get() == expectedGeneration) {
                    running.set(false)
                    playing = false
                    worker = null
                }
            }
        }
    }

    private fun decodeSamples(
        expectedGeneration: Long,
        extractor: MediaExtractor,
        codec: MediaCodec,
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        var inputEnded = false
        var outputEnded = false

        while (isCurrent(expectedGeneration)) {
            if (drainOutput(codec, bufferInfo) == true) {
                outputEnded = true
            }
            if (outputEnded) {
                if (!isCurrent(expectedGeneration)) break
                restartPlayback(expectedGeneration, extractor, codec)
                inputEnded = false
                outputEnded = false
                continue
            }

            if (!playing) {
                Thread.sleep(PAUSE_POLL_INTERVAL_MS)
                continue
            }

            val requestedSeek = takeSeekRequest()
            if (requestedSeek != NO_SEEK_REQUEST) {
                extractor.seekTo(requestedSeek, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                codec.flush()
                inputEnded = false
                outputEnded = false
                synchronized(lifecycleLock) {
                    if (isCurrent(expectedGeneration)) {
                        clockAnchorContentUs = requestedSeek
                        clockAnchorElapsedUs = monotonicUs()
                    }
                }
            }

            if (!inputEnded) {
                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs < 0L) {
                    val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputEnded = true
                    }
                } else {
                    val waitUs = sampleTimeUs - currentPositionUs()
                    if (waitUs > 0L) {
                        sleepMicros(minOf(waitUs, FRAME_WAIT_CHUNK_US))
                        continue
                    }

                    val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                            ?: throw IllegalStateException("解码器输入缓冲区不可用")
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                sampleTimeUs,
                                extractor.sampleFlags,
                            )
                            extractor.advance()
                        }
                    }
                }
            }

            synchronized(lifecycleLock) {
                if (isCurrent(expectedGeneration)) {
                    positionUs = currentPositionUsLocked()
                }
            }
        }
    }

    /** Returns true when the decoder has emitted its end-of-stream output buffer. */
    private fun drainOutput(
        codec: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
    ): Boolean? {
        while (true) {
            when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> return null
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> continue
                else -> {
                    if (outputIndex < 0) return null
                    val endOfStream =
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    // Surface decoders do not consistently populate BufferInfo.size.
                    // Every non-EOS decoded buffer must still be rendered.
                    val render = !endOfStream || bufferInfo.size > 0
                    codec.releaseOutputBuffer(outputIndex, render)
                    if (endOfStream) {
                        return true
                    }
                }
            }
        }
    }

    private fun restartPlayback(
        expectedGeneration: Long,
        extractor: MediaExtractor,
        codec: MediaCodec,
    ) {
        codec.flush()
        extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        synchronized(lifecycleLock) {
            if (!isCurrent(expectedGeneration)) return
            positionUs = 0L
            clockAnchorContentUs = 0L
            clockAnchorElapsedUs = monotonicUs()
        }
    }

    private fun processImage(
        expectedGeneration: Long,
        reader: ImageReader,
        onFrame: (PlatformBitmap) -> Unit,
    ) {
        val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return
        try {
            if (!isCurrent(expectedGeneration)) return

            val nowNanos = SystemClock.elapsedRealtimeNanos()
            val intervalNanos = TimeUnit.SECONDS.toNanos(1L) / targetFps.coerceAtLeast(1)
            if (lastDeliveredElapsedNanos != 0L &&
                nowNanos - lastDeliveredElapsedNanos < intervalNanos
            ) {
                return
            }
            lastDeliveredElapsedNanos = nowNanos

            val decoded = image.toRgbaBitmap()
            val transformed = rotateAndScale(decoded, frameRotationDegrees)
            previewDispatcher.publishDimensions(transformed.width, transformed.height)
            if (previewDispatcher.shouldPublishFrame()) {
                val preview = transformed.copy(Bitmap.Config.ARGB_8888, false)
                if (preview != null) {
                    previewDispatcher.publishFrame(PlatformBitmap(preview))
                }
            }
            val cropped = applyCrop(transformed)
            var delivered = false
            try {
                if (!isCurrent(expectedGeneration)) return
                onFrame(PlatformBitmap(cropped))
                delivered = true
            } finally {
                if (cropped !== transformed) transformed.recycle()
                if (transformed !== decoded) decoded.recycle()
                if (!delivered) cropped.recycle()
            }
        } catch (error: Throwable) {
            if (isCurrent(expectedGeneration)) {
                reportFailure(
                    "视频帧处理失败: ${error.message ?: error::class.simpleName ?: "未知错误"}",
                )
            }
        } finally {
            image.close()
        }
    }

    private fun rotateAndScale(source: Bitmap, rotationDegrees: Int): Bitmap {
        val maxEdge = maxOf(source.width, source.height)
        val scale = minOf(1f, MAX_CAPTURE_EDGE.toFloat() / maxEdge.coerceAtLeast(1))
        if (rotationDegrees == 0 && scale >= 1f) return source

        val matrix = Matrix().apply {
            if (rotationDegrees != 0) postRotate(rotationDegrees.toFloat())
            if (scale < 1f) postScale(scale, scale)
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun applyCrop(bitmap: Bitmap): Bitmap {
        if (cropConfig == FrameCropConfig.FULL) return bitmap
        val rect = cropConfig.toPixelRect(bitmap.width, bitmap.height)
        if (rect.x == 0 && rect.y == 0 &&
            rect.width == bitmap.width && rect.height == bitmap.height
        ) {
            return bitmap
        }
        return Bitmap.createBitmap(bitmap, rect.x, rect.y, rect.width, rect.height)
    }

    /**
     * Codec output can contain row padding. Reuse packed buffers when possible and
     * avoid allocating a new full-frame array for every delivered image.
     */
    private fun Image.toRgbaBitmap(): Bitmap {
        val plane = planes[0]
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val frameWidth = width
        val frameHeight = height
        val bitmap = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888)
        val source = plane.buffer
        source.rewind()

        val tightRowBytes = frameWidth * pixelStride
        if (pixelStride == 4 && rowStride == tightRowBytes) {
            bitmap.copyPixelsFromBuffer(source)
            return bitmap
        }

        if (pixelStride == 4) {
            val requiredBytes = tightRowBytes * frameHeight
            val packed = packedRgbaBuffer
                ?.takeIf { it.capacity() >= requiredBytes }
                ?: ByteBuffer.allocate(requiredBytes).also { packedRgbaBuffer = it }
            packed.clear()
            for (y in 0 until frameHeight) {
                val rowStart = y * rowStride
                val available = (source.limit() - rowStart).coerceAtLeast(0)
                val bytesToCopy = minOf(tightRowBytes, available)
                if (bytesToCopy <= 0) break
                val row = source.duplicate()
                row.position(rowStart)
                row.limit(rowStart + bytesToCopy)
                packed.put(row)
            }
            packed.flip()
            bitmap.copyPixelsFromBuffer(packed)
            return bitmap
        }

        val requiredPixels = frameWidth * frameHeight
        val pixels = argbPixelBuffer
            ?.takeIf { it.size >= requiredPixels }
            ?: IntArray(requiredPixels).also { argbPixelBuffer = it }
        for (y in 0 until frameHeight) {
            for (x in 0 until frameWidth) {
                val offset = y * rowStride + x * pixelStride
                if (offset + 3 >= source.limit()) continue
                val r = source.get(offset).toInt() and 0xFF
                val g = source.get(offset + 1).toInt() and 0xFF
                val b = source.get(offset + 2).toInt() and 0xFF
                val a = source.get(offset + 3).toInt() and 0xFF
                pixels[y * frameWidth + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        bitmap.setPixels(pixels, 0, frameWidth, 0, 0, frameWidth, frameHeight)
        return bitmap
    }

    private fun setDataSource(extractor: MediaExtractor) {
        when (uri.scheme?.lowercase()) {
            "http", "https", "rtsp" ->
                extractor.setDataSource(uri.toString(), emptyMap())
            "content", "file", "android.resource" ->
                extractor.setDataSource(context, uri, null)
            null ->
                extractor.setDataSource(uri.path ?: uri.toString(), emptyMap())
            else ->
                extractor.setDataSource(context, uri, null)
        }
    }

    private fun findVideoTrack(extractor: MediaExtractor): Int {
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("video/") == true) return index
        }
        return -1
    }

    private fun takeSeekRequest(): Long = synchronized(lifecycleLock) {
        val requested = seekRequestUs
        seekRequestUs = NO_SEEK_REQUEST
        requested
    }

    private fun normalizedRotation(value: Int): Int {
        val normalized = value % 360
        return if (normalized < 0) normalized + 360 else normalized
    }

    private fun MediaFormat.intValue(key: String): Int? =
        if (containsKey(key)) getInteger(key) else null

    private fun MediaFormat.longValue(key: String): Long? =
        if (containsKey(key)) getLong(key) else null

    private fun sleepMicros(delayUs: Long) {
        if (delayUs <= 0L) return
        val millis = delayUs / 1_000L
        val nanos = ((delayUs % 1_000L) * 1_000L).toInt()
        if (millis == 0L && nanos == 0) {
            Thread.yield()
        } else {
            Thread.sleep(millis, nanos)
        }
    }

    private fun reportFailure(message: String) {
        if (failureReported.compareAndSet(false, true)) {
            failureHandler?.invoke(message)
        }
    }

    private fun monotonicUs(): Long = SystemClock.elapsedRealtimeNanos() / 1_000L

    private fun isCurrent(expectedGeneration: Long): Boolean =
        running.get() && !released.get() && generation.get() == expectedGeneration
}
