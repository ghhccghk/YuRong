package top.ghhccghk.yadea

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import top.ghhccghk.yadea.ui.AppRemoteImage

/**
 * Android 调用点保留原有语义，实际加载统一经过 commonMain 的 AppImages 缓存。
 */
@Composable
fun AndroidVehicleImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    contentDescription: String = "车辆图片",
) {
    AppRemoteImage(
        url = url,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}

/** 车辆本体图，保持完整轮廓。 */
@Composable
fun AndroidVehicleBodyImage(
    url: String,
    modifier: Modifier = Modifier,
) {
    AndroidVehicleImage(
        url = url,
        modifier = modifier.size(132.dp, 88.dp),
        contentScale = ContentScale.Fit,
        contentDescription = "车辆图片",
    )
}

/** 车辆页背景图，铺满卡片并裁剪。 */
@Composable
fun AndroidVehicleBackgroundImage(
    url: String,
    modifier: Modifier = Modifier,
) {
    AndroidVehicleImage(
        url = url,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        contentDescription = "车辆背景图",
    )
}
