package top.ghhccghk.yadea.mirror

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

/**
 * MirrorPreviewPanel - 投屏实时预览 + 裁剪区域调节面板
 *
 * 功能:
 * - 实时预览当前帧（灰度占位/真实帧）
 * - 可拖拽调整裁剪区域
 * - 滑块微调上下左右边界
 * - 显示当前裁剪参数
 */
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
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 标题
        Text(
            "投屏区域预览",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 预览画布 + 裁剪框
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(previewWidth.toFloat() / previewHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
        ) {
            // 裁剪区域可视化
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

                // 裁剪外区域（半透明遮罩）
                drawRect(
                    color = Color.Black.copy(alpha = 0.5f),
                    topLeft = Offset.Zero,
                    size = Size(w, h)
                )

                // 裁剪内区域（清除遮罩效果）
                drawRect(
                    color = Color.White.copy(alpha = 0.15f),
                    topLeft = Offset(crop.left * w, crop.top * h),
                    size = Size(crop.normalizedWidth * w, crop.normalizedHeight * h)
                )

                // 裁剪边框
                drawRect(
                    color = Color.Cyan,
                    topLeft = Offset(crop.left * w, crop.top * h),
                    size = Size(crop.normalizedWidth * w, crop.normalizedHeight * h),
                    style = Stroke(width = 3.dp.toPx())
                )

                // 四角标记
                val cornerLen = 16.dp.toPx()
                val lx = crop.left * w; val ty = crop.top * h
                val rx = crop.right * w; val by = crop.bottom * h

                // 左上
                drawLine(Color.Cyan, Offset(lx, ty), Offset(lx + cornerLen, ty), 4.dp.toPx())
                drawLine(Color.Cyan, Offset(lx, ty), Offset(lx, ty + cornerLen), 4.dp.toPx())
                // 右上
                drawLine(Color.Cyan, Offset(rx, ty), Offset(rx - cornerLen, ty), 4.dp.toPx())
                drawLine(Color.Cyan, Offset(rx, ty), Offset(rx, ty + cornerLen), 4.dp.toPx())
                // 左下
                drawLine(Color.Cyan, Offset(lx, by), Offset(lx + cornerLen, by), 4.dp.toPx())
                drawLine(Color.Cyan, Offset(lx, by), Offset(lx, by - cornerLen), 4.dp.toPx())
                // 右下
                drawLine(Color.Cyan, Offset(rx, by), Offset(rx - cornerLen, by), 4.dp.toPx())
                drawLine(Color.Cyan, Offset(rx, by), Offset(rx, by - cornerLen), 4.dp.toPx())
            }
        }

        // 滑块微调
        Text("精细调节", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

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

        // 当前参数显示
        Text(
            "区域: L=%.3f  T=%.3f  R=%.3f  B=%.3f".format(crop.left, crop.top, crop.right, crop.bottom),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )

        // 快捷按钮
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                crop = FrameCropConfig.FULL; onCropChange(crop)
            }) { Text("全幅", fontSize = 12.sp) }

            OutlinedButton(onClick = {
                crop = FrameCropConfig.CENTER; onCropChange(crop)
            }) { Text("居中", fontSize = 12.sp) }

            OutlinedButton(onClick = {
                // 保持宽高比居中
                val ratio = previewWidth.toFloat() / previewHeight
                val nc = FrameCropConfig.FULL.withAspectRation(ratio, previewWidth, previewHeight)
                crop = nc; onCropChange(nc)
            }) { Text("适配", fontSize = 12.sp) }
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
        Text(label, fontSize = 12.sp, modifier = Modifier.width(50.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = rangeStart..rangeEnd,
            modifier = Modifier.weight(1f)
        )
        Text("%.2f".format(value), fontSize = 11.sp, modifier = Modifier.width(36.dp))
    }
}
