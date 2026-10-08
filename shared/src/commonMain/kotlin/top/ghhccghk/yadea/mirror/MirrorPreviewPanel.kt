package top.ghhccghk.yadea.mirror

import top.ghhccghk.yadea.ui.AppDesign
import top.ghhccghk.yadea.ui.AppOutlinedButton
import top.ghhccghk.yadea.ui.AppText
import top.ghhccghk.yadea.ui.AppSlider

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MirrorPreviewPanel(
    previewWidth: Int = 320,
    previewHeight: Int = 180,
    cropConfig: FrameCropConfig = FrameCropConfig.FULL,
    onCropChange: (FrameCropConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    var crop by remember { mutableStateOf(cropConfig) }
    LaunchedEffect(cropConfig) { crop = cropConfig }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppDesign.colors.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppText(
            "投屏区域预览",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = AppDesign.colors.onSurfaceVariant
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(previewWidth.toFloat() / previewHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val dx = dragAmount.x / size.width
                            val dy = dragAmount.y / size.height
                            val newLeft = (crop.left + dx).coerceIn(0f, crop.right - 0.05f)
                            val newTop = (crop.top + dy).coerceIn(0f, crop.bottom - 0.05f)
                            val newRight = (crop.right + dx).coerceIn(crop.left + 0.05f, 1f)
                            val newBottom = (crop.bottom + dy).coerceIn(crop.top + 0.05f, 1f)
                            val newCrop = FrameCropConfig(newLeft, newTop, newRight, newBottom)
                            crop = newCrop
                            onCropChange(newCrop)
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                drawRect(Color.Black.copy(alpha = 0.5f), Offset.Zero, Size(w, h))
                drawRect(
                    Color.White.copy(alpha = 0.15f),
                    Offset(crop.left * w, crop.top * h),
                    Size(crop.normalizedWidth * w, crop.normalizedHeight * h)
                )
                drawRect(
                    Color.Cyan,
                    Offset(crop.left * w, crop.top * h),
                    Size(crop.normalizedWidth * w, crop.normalizedHeight * h),
                    style = Stroke(width = 3.dp.toPx())
                )
                val cornerLen = 16.dp.toPx()
                val lx = crop.left * w; val ty = crop.top * h
                val rx = crop.right * w; val by = crop.bottom * h
                drawLine(Color.Cyan, Offset(lx, ty), Offset(lx + cornerLen, ty), 4.dp.toPx())
                drawLine(Color.Cyan, Offset(lx, ty), Offset(lx, ty + cornerLen), 4.dp.toPx())
                drawLine(Color.Cyan, Offset(rx, ty), Offset(rx - cornerLen, ty), 4.dp.toPx())
                drawLine(Color.Cyan, Offset(rx, ty), Offset(rx, ty + cornerLen), 4.dp.toPx())
                drawLine(Color.Cyan, Offset(lx, by), Offset(lx + cornerLen, by), 4.dp.toPx())
                drawLine(Color.Cyan, Offset(lx, by), Offset(lx, by - cornerLen), 4.dp.toPx())
                drawLine(Color.Cyan, Offset(rx, by), Offset(rx - cornerLen, by), 4.dp.toPx())
                drawLine(Color.Cyan, Offset(rx, by), Offset(rx, by - cornerLen), 4.dp.toPx())
            }
        }

        AppText("精细调节", fontSize = 13.sp, color = AppDesign.colors.onSurfaceVariant)

        CropSlider("左边界", crop.left, 0f, crop.right - 0.05f) {
            val nc = crop.copy(left = it); crop = nc; onCropChange(nc)
        }
        CropSlider("上边界", crop.top, 0f, crop.bottom - 0.05f) {
            val nc = crop.copy(top = it); crop = nc; onCropChange(nc)
        }
        CropSlider("右边界", crop.right, crop.left + 0.05f, 1f) {
            val nc = crop.copy(right = it); crop = nc; onCropChange(nc)
        }
        CropSlider("下边界", crop.bottom, crop.top + 0.05f, 1f) {
            val nc = crop.copy(bottom = it); crop = nc; onCropChange(nc)
        }

        AppText(
            "区域: L=${fmt(crop.left, 3)}  T=${fmt(crop.top, 3)}  R=${fmt(crop.right, 3)}  B=${fmt(crop.bottom, 3)}",
            fontSize = 11.sp,
            color = AppDesign.colors.onSurfaceVariant.copy(alpha = 0.7f)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppOutlinedButton(onClick = {
                crop = FrameCropConfig.FULL; onCropChange(crop)
            }) { AppText("全幅", fontSize = 12.sp) }
            AppOutlinedButton(onClick = {
                crop = FrameCropConfig.CENTER; onCropChange(crop)
            }) { AppText("居中", fontSize = 12.sp) }
            AppOutlinedButton(onClick = {
                val ratio = previewWidth.toFloat() / previewHeight
                val nc = FrameCropConfig.FULL.withAspectRation(ratio, previewWidth, previewHeight)
                crop = nc; onCropChange(nc)
            }) { AppText("适配", fontSize = 12.sp) }
        }
    }
}

@Composable
private fun CropSlider(
    label: String,
    value: Float,
    rangeStart: Float,
    rangeEnd: Float,
    onChange: (Float) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppText(label, fontSize = 12.sp, modifier = Modifier.width(50.dp))
        AppSlider(
            value = value,
            onValueChange = onChange,
            valueRange = rangeStart..rangeEnd,
            modifier = Modifier.weight(1f)
        )
        AppText(fmt(value, 2), fontSize = 11.sp, modifier = Modifier.width(36.dp))
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
