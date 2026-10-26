package top.ghhccghk.yadea.mirror

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.decodeToImageBitmap
import top.ghhccghk.yadea.ui.AppDesign
import top.ghhccghk.yadea.ui.AppOutlinedButton
import top.ghhccghk.yadea.ui.AppSlider
import top.ghhccghk.yadea.ui.AppText

private val MAX_PREVIEW_HEIGHT = 360.dp
private const val MIN_CROP_WIDTH_FRACTION = 0.1f

/**
 * 源画面按视频/摄像头自身比例显示，裁切框按车辆协商的投屏像素比例显示。
 * 两者使用不同的宽高比，避免把 16:9 源画面误当成 800x480 投屏画布。
 */
@Composable
fun MirrorPreviewPanel(
    sourceWidth: Int = 1920,
    sourceHeight: Int = 1080,
    projectionWidth: Int = 800,
    projectionHeight: Int = 480,
    previewJpeg: ByteArray? = null,
    cropConfig: FrameCropConfig = FrameCropConfig.FULL,
    onCropChange: (FrameCropConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeSourceWidth = sourceWidth.coerceAtLeast(1)
    val safeSourceHeight = sourceHeight.coerceAtLeast(1)
    val safeProjectionWidth = projectionWidth.coerceAtLeast(1)
    val safeProjectionHeight = projectionHeight.coerceAtLeast(1)
    val sourceAspect = safeSourceWidth.toFloat() / safeSourceHeight
    val projectionAspect = safeProjectionWidth.toFloat() / safeProjectionHeight
    val maxCropWidth = minOf(1f, projectionAspect / sourceAspect)
    val cropState = remember { mutableStateOf(cropConfig) }
    val crop = cropState.value
    val previewImage = remember(previewJpeg) {
        previewJpeg?.let { bytes ->
            runCatching { bytes.decodeToImageBitmap() }.getOrNull()
        }
    }

    fun commitCrop(next: FrameCropConfig) {
        if (!next.isValid || next == cropState.value) return
        cropState.value = next
        onCropChange(next)
    }

    LaunchedEffect(
        cropConfig,
        safeSourceWidth,
        safeSourceHeight,
        safeProjectionWidth,
        safeProjectionHeight,
    ) {
        val fitted = cropConfig.withAspectRatio(
            targetRatio = projectionAspect,
            sourceWidth = safeSourceWidth,
            sourceHeight = safeSourceHeight,
        )
        cropState.value = fitted
        if (fitted != cropConfig) onCropChange(fitted)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppDesign.shapes.section)
            .background(AppDesign.colors.previewBackground)
            .padding(
                horizontal = AppDesign.dimensions.itemHorizontalPadding,
                vertical = AppDesign.dimensions.itemVerticalPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(AppDesign.dimensions.sectionSpacing),
    ) {
        AppText(
            "投屏区域预览",
            style = AppDesign.typography.sectionTitle,
            color = AppDesign.colors.onSurfaceVariant,
        )
        AppText(
            "源 ${safeSourceWidth}×${safeSourceHeight} · " +
                "投屏 ${safeProjectionWidth}×${safeProjectionHeight}",
            style = AppDesign.typography.caption,
            color = AppDesign.colors.onSurfaceVariant,
        )

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val previewHeight = minOf(MAX_PREVIEW_HEIGHT, maxWidth / sourceAspect)
            val previewWidth = previewHeight * sourceAspect

            Box(
                modifier = Modifier
                    .width(previewWidth)
                    .height(previewHeight)
                    .clip(AppDesign.shapes.item)
                    .background(AppDesign.colors.previewBackground),
            ) {
                if (previewImage != null) {
                    Image(
                        bitmap = previewImage,
                        contentDescription = "投屏源画面预览",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppText(
                            "选择视频或开始投屏后显示画面",
                            style = AppDesign.typography.caption,
                            color = AppDesign.colors.onSurfaceVariant,
                        )
                    }
                }

                CropOverlay(
                    crop = crop,
                    sourceWidth = safeSourceWidth,
                    sourceHeight = safeSourceHeight,
                    projectionAspect = projectionAspect,
                    onMove = { deltaX, deltaY ->
                        commitCrop(cropState.value.moveBy(deltaX, deltaY))
                    },
                )
            }
        }

        AppText(
            "拖动画面移动裁切框；裁切框固定为投屏比例",
            style = AppDesign.typography.caption,
            color = AppDesign.colors.onSurfaceVariant,
        )

        CropSlider("水平位置", crop.centerX, 0f, 1f) { value ->
            commitCrop(
                crop.withAspectRatio(
                    projectionAspect,
                    safeSourceWidth,
                    safeSourceHeight,
                    centerX = value,
                    requestedWidth = crop.normalizedWidth,
                ),
            )
        }
        CropSlider("垂直位置", crop.centerY, 0f, 1f) { value ->
            commitCrop(
                crop.withAspectRatio(
                    projectionAspect,
                    safeSourceWidth,
                    safeSourceHeight,
                    centerY = value,
                    requestedWidth = crop.normalizedWidth,
                ),
            )
        }
        CropSlider(
            "裁切范围",
            (crop.normalizedWidth / maxCropWidth).coerceIn(MIN_CROP_WIDTH_FRACTION, 1f),
            MIN_CROP_WIDTH_FRACTION,
            1f,
        ) { value ->
            commitCrop(
                crop.withAspectRatio(
                    projectionAspect,
                    safeSourceWidth,
                    safeSourceHeight,
                    requestedWidth = maxCropWidth * value,
                ),
            )
        }

        AppText(
            "区域: L=${fmt(crop.left, 3)}  T=${fmt(crop.top, 3)}  " +
                "R=${fmt(crop.right, 3)}  B=${fmt(crop.bottom, 3)}",
            style = AppDesign.typography.debug,
            color = AppDesign.colors.onSurfaceVariant.copy(alpha = 0.7f),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing)) {
            AppOutlinedButton(
                onClick = {
                    commitCrop(
                        crop.withAspectRatio(
                            projectionAspect,
                            safeSourceWidth,
                            safeSourceHeight,
                            centerX = 0.5f,
                            centerY = 0.5f,
                        ),
                    )
                },
                modifier = Modifier.weight(1f),
            ) {
                AppText("最大化", style = AppDesign.typography.caption)
            }
            AppOutlinedButton(
                onClick = {
                    commitCrop(
                        crop.withAspectRatio(
                            projectionAspect,
                            safeSourceWidth,
                            safeSourceHeight,
                            centerX = 0.5f,
                            centerY = 0.5f,
                            requestedWidth = maxCropWidth * 0.5f,
                        ),
                    )
                },
                modifier = Modifier.weight(1f),
            ) {
                AppText("居中", style = AppDesign.typography.caption)
            }
        }
    }
}

@Composable
private fun CropOverlay(
    crop: FrameCropConfig,
    sourceWidth: Int,
    sourceHeight: Int,
    projectionAspect: Float,
    onMove: (Float, Float) -> Unit,
) {
    val previewScrim = AppDesign.colors.previewScrim
    val previewSelection = AppDesign.colors.previewSelection
    val previewOutline = AppDesign.colors.previewOutline
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(sourceWidth, sourceHeight, projectionAspect) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onMove(
                        dragAmount.x / size.width,
                        dragAmount.y / size.height,
                    )
                }
            },
    ) {
        val width = size.width
        val height = size.height
        val left = crop.left * width
        val top = crop.top * height
        val right = crop.right * width
        val bottom = crop.bottom * height

        drawRect(previewScrim, Offset.Zero, Size(width, top))
        drawRect(previewScrim, Offset(0f, top), Size(left, bottom - top))
        drawRect(
            previewScrim,
            Offset(right, top),
            Size((width - right).coerceAtLeast(0f), bottom - top),
        )
        drawRect(previewScrim, Offset(0f, bottom), Size(width, height - bottom))
        drawRect(
            previewSelection,
            Offset(left, top),
            Size(right - left, bottom - top),
        )
        drawRect(
            previewOutline,
            Offset(left, top),
            Size(right - left, bottom - top),
            style = Stroke(width = 3.dp.toPx()),
        )

        val cornerLength = 16.dp.toPx()
        val stroke = 4.dp.toPx()
        drawLine(previewOutline, Offset(left, top), Offset(left + cornerLength, top), stroke)
        drawLine(previewOutline, Offset(left, top), Offset(left, top + cornerLength), stroke)
        drawLine(previewOutline, Offset(right, top), Offset(right - cornerLength, top), stroke)
        drawLine(previewOutline, Offset(right, top), Offset(right, top + cornerLength), stroke)
        drawLine(previewOutline, Offset(left, bottom), Offset(left + cornerLength, bottom), stroke)
        drawLine(previewOutline, Offset(left, bottom), Offset(left, bottom - cornerLength), stroke)
        drawLine(previewOutline, Offset(right, bottom), Offset(right - cornerLength, bottom), stroke)
        drawLine(previewOutline, Offset(right, bottom), Offset(right, bottom - cornerLength), stroke)
    }
}

@Composable
private fun CropSlider(
    label: String,
    value: Float,
    rangeStart: Float,
    rangeEnd: Float,
    onChange: (Float) -> Unit,
) {
    val lowerBound = minOf(rangeStart, rangeEnd).coerceIn(0f, 1f)
    val upperBound = maxOf(rangeStart, rangeEnd).coerceIn(0f, 1f)
    val sliderValue = value.coerceIn(lowerBound, upperBound)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing),
    ) {
        AppText(label, style = AppDesign.typography.caption, modifier = Modifier.width(64.dp))
        AppSlider(
            value = sliderValue,
            onValueChange = { onChange(it.coerceIn(lowerBound, upperBound)) },
            valueRange = lowerBound..upperBound,
            modifier = Modifier.weight(1f),
        )
        AppText(
            fmt(sliderValue, 2),
            style = AppDesign.typography.debug,
            modifier = Modifier.width(36.dp),
        )
    }
}

internal fun fmt(v: Float, decimals: Int): String {
    val s = v.toString()
    val dotIndex = s.indexOf('.')
    return if (dotIndex < 0) {
        if (decimals == 0) s else "$s.0"
    } else {
        val after = s.substring(dotIndex + 1)
        if (after.length >= decimals) s.substring(0, dotIndex + 1 + decimals)
        else "$s${"0".repeat(decimals - after.length)}"
    }
}
