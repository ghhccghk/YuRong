package top.ghhccghk.yadea.mirror.source

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt
import top.ghhccghk.yadea.mirror.FrameCropConfig
import top.ghhccghk.yadea.mirror.FramePreviewDispatcher
import top.ghhccghk.yadea.mirror.FrameSource
import top.ghhccghk.yadea.mirror.FrameSourceDimensions
import top.ghhccghk.yadea.mirror.PlatformBitmap
import top.ghhccghk.yadea.ui.logToFile

private const val MAX_CAPTURE_EDGE = 1024
private const val PAUSE_POLL_INTERVAL_MS = 50L
private const val CODEC_TIMEOUT_US = 10_000L
private const val FRAME_WAIT_CHUNK_US = 20_000L
private const val DEFAULT_TARGET_FPS = 30
private const val NO_SEEK_REQUEST = -1L

private data class VideoTrackFormat(
    val mediaFormat: MediaFormat,
    val mime: String,
    val width: Int,
    val height: Int,
    val durationUs: Long,
    val rotationDegrees: Int,
    val frameRate: Float,
    val profile: Int?,
    val level: Int?,
)

private data class VideoDecoderCandidate(
    val name: String,
    val software: Boolean,
)

private data class StartedVideoDecoder(
    val codec: MediaCodec,
    val name: String,
    val software: Boolean,
)

private class VideoDecoderOutputException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Android 视频/网络流帧源。
 *
 * MediaMetadataRetriever.getFrameAtTime() is designed for random thumbnails, not
 * continuous playback. Repeated closest-frame requests can seek back to the same
 * keyframe for every image, which makes long-GOP videos appear to update only once
 * every few seconds. This source uses the platform decoder as a stream: samples
 * are paced by presentation time and decoded to flexible YUV buffers, while
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

    // Decoder output is consumed on the source thread, so this buffer can be reused.
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
        try {
            extractor = MediaExtractor().also(::setDataSource)
            val trackIndex = findVideoTrack(extractor)
            if (trackIndex < 0) {
                throw IllegalStateException("视频源中没有可解码的视频轨道")
            }
            extractor.selectTrack(trackIndex)
            val videoFormat = parseVideoFormat(extractor.getTrackFormat(trackIndex))
            val candidates = decoderCandidates(videoFormat)
            if (candidates.isEmpty()) {
                throw IllegalStateException(
                    "没有支持 ${videoFormat.mime} ${videoFormat.width}x${videoFormat.height} " +
                        "Flexible YUV 输出的解码器",
                )
            }
            var lastDecoderError: Throwable? = null
            var candidateIndex = 0
            while (candidateIndex < candidates.size && isCurrent(expectedGeneration)) {
                val candidate = candidates[candidateIndex]
                val startedDecoder = tryStartDecoder(videoFormat, candidate)
                if (startedDecoder == null) {
                    candidateIndex++
                    continue
                }
                codec = startedDecoder.codec
                synchronized(lifecycleLock) {
                    if (!isCurrent(expectedGeneration)) return
                    durationUs = videoFormat.durationUs
                    frameRotationDegrees = videoFormat.rotationDegrees
                    positionUs = 0L
                    clockAnchorContentUs = 0L
                    clockAnchorElapsedUs = monotonicUs()
                }

                logToFile(
                    "MIRROR VIDEO format=${videoFormat.mime} " +
                        "${videoFormat.width}x${videoFormat.height} " +
                        "fps=${videoFormat.frameRate} profile=${videoFormat.profile} " +
                        "level=${videoFormat.level}",
                )
                logToFile(
                    "MIRROR VIDEO decoder=${startedDecoder.name} " +
                        "software=${startedDecoder.software}",
                )

                try {
                    extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    decodeSamples(
                        expectedGeneration = expectedGeneration,
                        extractor = extractor,
                        codec = startedDecoder.codec,
                        onFrame = onFrame,
                    )
                    return
                } catch (error: VideoDecoderOutputException) {
                    lastDecoderError = error
                    logToFile(
                        "MIRROR VIDEO decoder output failed " +
                            "${startedDecoder.name}: ${error.message}",
                    )
                    runCatching { startedDecoder.codec.stop() }
                    runCatching { startedDecoder.codec.release() }
                    codec = null
                    candidateIndex++
                }
            }
            throw IllegalStateException(
                "硬件和软件解码器均无法输出该视频格式",
                lastDecoderError,
            )
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (error: Throwable) {
            if (isCurrent(expectedGeneration)) {
                reportFailure("无法读取视频源: ${error.message ?: error::class.simpleName ?: "未知错误"}")
            }
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor?.release() }
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
        onFrame: (PlatformBitmap) -> Unit,
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        var inputEnded = false
        var outputEnded = false

        while (isCurrent(expectedGeneration)) {
            if (drainOutput(expectedGeneration, codec, bufferInfo, onFrame) == true) {
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
        expectedGeneration: Long,
        codec: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        onFrame: (PlatformBitmap) -> Unit,
    ): Boolean? {
        while (true) {
            when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> return null
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> continue
                else -> {
                    if (outputIndex < 0) return null
                    val endOfStream =
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    try {
                        if (!endOfStream && bufferInfo.size > 0) {
                            val image = codec.getOutputImage(outputIndex)
                                ?: throw VideoDecoderOutputException(
                                    "视频解码器没有提供 YUV 图像: ${codec.outputFormat}",
                                )
                            try {
                                processImage(expectedGeneration, image, onFrame)
                            } finally {
                                image.close()
                            }
                        }
                    } finally {
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
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
        image: Image,
        onFrame: (PlatformBitmap) -> Unit,
    ) {
        if (!isCurrent(expectedGeneration)) return

        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val intervalNanos = TimeUnit.SECONDS.toNanos(1L) / targetFps.coerceAtLeast(1)
        if (lastDeliveredElapsedNanos != 0L &&
            nowNanos - lastDeliveredElapsedNanos < intervalNanos
        ) {
            return
        }
        lastDeliveredElapsedNanos = nowNanos

        val decoded = image.toArgbBitmap()
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

    private fun scaledSize(width: Int, height: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return width to height
        val scale = minOf(1f, MAX_CAPTURE_EDGE.toFloat() / maxOf(width, height))
        return (width * scale).roundToInt().coerceAtLeast(2) to
            (height * scale).roundToInt().coerceAtLeast(2)
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
     * Convert flexible YUV output explicitly instead of calling
     * Bitmap.copyPixelsFromBuffer on a codec-owned RGBA buffer, which can abort
     * in native code on some devices.
     */
    private fun Image.toArgbBitmap(): Bitmap {
        val imagePlanes = planes
        if (imagePlanes.size < 3) {
            throw IllegalStateException("视频 YUV 解码输出像素平面不足: ${imagePlanes.size}")
        }
        val crop = cropRect
        val cropLeft = crop.left.coerceIn(0, width - 1)
        val cropTop = crop.top.coerceIn(0, height - 1)
        val cropRight = crop.right.coerceIn(cropLeft + 1, width)
        val cropBottom = crop.bottom.coerceIn(cropTop + 1, height)
        val frameWidth = cropRight - cropLeft
        val frameHeight = cropBottom - cropTop
        val yPlane = imagePlanes[0]
        val uPlane = imagePlanes[1]
        val vPlane = imagePlanes[2]
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride
        val chromaLeft = cropLeft / 2
        val chromaTop = cropTop / 2
        // Chroma coordinates are floor(lumaCoordinate / 2). For odd crop edges
        // the visible chroma range can be one sample wider than ceil(width / 2).
        val visibleChromaWidth =
            (cropRight - 1) / 2 - chromaLeft + 1
        val visibleChromaHeight =
            (cropBottom - 1) / 2 - chromaTop + 1
        validateYuvPlane(
            name = "Y",
            limit = yPlane.buffer.limit(),
            baseOffset = yPlane.buffer.position(),
            startX = cropLeft,
            startY = cropTop,
            rowStride = yRowStride,
            pixelStride = yPixelStride,
            width = frameWidth,
            height = frameHeight,
        )
        validateYuvPlane(
            name = "U",
            limit = uPlane.buffer.limit(),
            baseOffset = uPlane.buffer.position(),
            startX = chromaLeft,
            startY = chromaTop,
            rowStride = uRowStride,
            pixelStride = uPixelStride,
            width = visibleChromaWidth,
            height = visibleChromaHeight,
        )
        validateYuvPlane(
            name = "V",
            limit = vPlane.buffer.limit(),
            baseOffset = vPlane.buffer.position(),
            startX = chromaLeft,
            startY = chromaTop,
            rowStride = vRowStride,
            pixelStride = vPixelStride,
            width = visibleChromaWidth,
            height = visibleChromaHeight,
        )

        // Decode buffers can be 4K or larger. Convert directly to a bounded
        // bitmap so the old full-resolution ARGB allocation cannot create the
        // memory pressure seen before the native crash.
        val outputScale = minOf(
            1f,
            MAX_CAPTURE_EDGE.toFloat() / maxOf(frameWidth, frameHeight),
        )
        val outputWidth = (frameWidth * outputScale).roundToInt().coerceAtLeast(2)
        val outputHeight = (frameHeight * outputScale).roundToInt().coerceAtLeast(2)
        val bitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        try {
            val requiredPixels = outputWidth * outputHeight
            val pixels = argbPixelBuffer
                ?.takeIf { it.size >= requiredPixels }
                ?: IntArray(requiredPixels).also { argbPixelBuffer = it }
            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer
            val yBaseOffset = yBuffer.position()
            val uBaseOffset = uBuffer.position()
            val vBaseOffset = vBuffer.position()
            for (y in 0 until outputHeight) {
                val sourceY = cropTop +
                    (y.toLong() * frameHeight / outputHeight).toInt()
                val yRowOffset = yBaseOffset + sourceY * yRowStride
                val uvRowOffset = uBaseOffset + (sourceY / 2) * uRowStride
                val vvRowOffset = vBaseOffset + (sourceY / 2) * vRowStride
                for (x in 0 until outputWidth) {
                    val sourceX = cropLeft +
                        (x.toLong() * frameWidth / outputWidth).toInt()
                    val yValue = yBuffer.get(
                        yRowOffset + sourceX * yPixelStride,
                    ).toInt() and 0xFF
                    val uvX = sourceX / 2
                    val uValue = uBuffer.get(
                        uvRowOffset + uvX * uPixelStride,
                    ).toInt() and 0xFF
                    val vValue = vBuffer.get(
                        vvRowOffset + uvX * vPixelStride,
                    ).toInt() and 0xFF

                    val c = (yValue - 16).coerceAtLeast(0)
                    val d = uValue - 128
                    val e = vValue - 128
                    val r = ((298 * c + 409 * e + 128) shr 8).coerceIn(0, 255)
                    val g = ((298 * c - 100 * d - 208 * e + 128) shr 8).coerceIn(0, 255)
                    val b = ((298 * c + 516 * d + 128) shr 8).coerceIn(0, 255)
                    pixels[y * outputWidth + x] =
                        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            bitmap.setPixels(
                pixels,
                0,
                outputWidth,
                0,
                0,
                outputWidth,
                outputHeight,
            )
            return bitmap
        } catch (error: Throwable) {
            if (!bitmap.isRecycled) bitmap.recycle()
            throw error
        }
    }

    private fun validateYuvPlane(
        name: String,
        limit: Int,
        baseOffset: Int,
        startX: Int,
        startY: Int,
        rowStride: Int,
        pixelStride: Int,
        width: Int,
        height: Int,
    ) {
        if (rowStride <= 0 || pixelStride <= 0) {
            throw IllegalStateException(
                "视频 $name 平面步长无效: row=$rowStride pixel=$pixelStride",
            )
        }
        val lastIndex = baseOffset +
            (startY + height - 1).coerceAtLeast(0) * rowStride +
            (startX + width - 1).coerceAtLeast(0) * pixelStride
        if (lastIndex >= limit) {
            throw IllegalStateException(
                "视频 $name 平面缓冲区不足: last=$lastIndex limit=$limit",
            )
        }
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

    private fun parseVideoFormat(source: MediaFormat): VideoTrackFormat {
        val mime = source.getString(MediaFormat.KEY_MIME)
            ?: throw IllegalStateException("视频轨道缺少 MIME 类型")
        if (!mime.startsWith("video/")) {
            throw IllegalStateException("不支持的视频轨道: $mime")
        }
        val width = source.intValue(MediaFormat.KEY_WIDTH)
        val height = source.intValue(MediaFormat.KEY_HEIGHT)
        if (width == null || height == null || width <= 1 || height <= 1) {
            throw IllegalStateException("视频轨道尺寸无效: ${width}x$height")
        }

        return VideoTrackFormat(
            mediaFormat = source,
            mime = mime,
            width = width,
            height = height,
            durationUs = source.longValue(MediaFormat.KEY_DURATION)?.takeIf { it > 0L } ?: 0L,
            rotationDegrees = source.intValue(MediaFormat.KEY_ROTATION)
                ?.let(::normalizedRotation)
                ?: 0,
            frameRate = source.floatValue(MediaFormat.KEY_FRAME_RATE)
                ?.takeIf { it > 0f }
                ?: 0f,
            profile = source.intValue(MediaFormat.KEY_PROFILE),
            level = source.intValue(MediaFormat.KEY_LEVEL),
        )
    }

    /**
     * Try format-compatible hardware decoders first, then software decoders.
     * configure/start is part of capability detection because vendor
     * MediaCodecInfo declarations can differ from what the runtime accepts.
     */
    private fun tryStartDecoder(
        videoFormat: VideoTrackFormat,
        candidate: VideoDecoderCandidate,
    ): StartedVideoDecoder? {
        videoFormat.mediaFormat.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
        )
        val codec = runCatching { MediaCodec.createByCodecName(candidate.name) }
            .getOrNull()
            ?: return null
        return try {
            codec.configure(videoFormat.mediaFormat, null, null, 0)
            codec.start()
            StartedVideoDecoder(
                codec = codec,
                name = candidate.name,
                software = candidate.software,
            )
        } catch (error: Throwable) {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            null
        }
    }

    private fun decoderCandidates(videoFormat: VideoTrackFormat): List<VideoDecoderCandidate> {
        val probeFormat = MediaFormat.createVideoFormat(
            videoFormat.mime,
            videoFormat.width,
            videoFormat.height,
        ).apply {
            videoFormat.profile?.let { setInteger(MediaFormat.KEY_PROFILE, it) }
            videoFormat.level?.let { setInteger(MediaFormat.KEY_LEVEL, it) }
            if (videoFormat.frameRate > 0f) {
                setFloat(MediaFormat.KEY_FRAME_RATE, videoFormat.frameRate)
            }
        }

        return MediaCodecList(MediaCodecList.REGULAR_CODECS)
            .codecInfos
            .asSequence()
            .filter { info ->
                !info.isEncoder &&
                    !info.name.lowercase().contains("secure") &&
                    info.supportedTypes.any { it.equals(videoFormat.mime, ignoreCase = true) }
            }
            .mapNotNull { info ->
                val capabilities = runCatching {
                    info.getCapabilitiesForType(videoFormat.mime)
                }.getOrNull() ?: return@mapNotNull null
                if (!capabilities.colorFormats.contains(
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
                    )
                ) {
                    return@mapNotNull null
                }
                val videoCapabilities = capabilities.videoCapabilities
                    ?: return@mapNotNull null
                if (!videoCapabilities.isSizeSupported(videoFormat.width, videoFormat.height)) {
                    return@mapNotNull null
                }
                val software = isLikelySoftwareDecoder(info.name)
                val formatSupported = runCatching {
                    capabilities.isFormatSupported(probeFormat)
                }.getOrDefault(true)
                VideoDecoderCandidate(info.name, software) to formatSupported
            }
            .sortedWith(
                compareBy(
                    { if (it.first.software) 1 else 0 },
                    { if (it.second) 0 else 1 },
                    { it.first.name },
                ),
            )
            .map { it.first }
            .toList()
    }

    private fun isLikelySoftwareDecoder(name: String): Boolean {
        val normalized = name.lowercase()
        return normalized.startsWith("c2.android.") ||
            normalized.startsWith("omx.google.") ||
            normalized.contains(".sw.") ||
            normalized.endsWith(".sw")
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

    private fun MediaFormat.floatValue(key: String): Float? {
        if (!containsKey(key)) return null
        return runCatching { getFloat(key) }
            .recoverCatching { getInteger(key).toFloat() }
            .getOrNull()
    }

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
