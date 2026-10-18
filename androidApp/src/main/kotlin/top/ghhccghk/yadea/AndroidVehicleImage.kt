package top.ghhccghk.yadea

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 车辆图加载器。
 *
 * 原应用把 `skinPicUrl` 用作车辆页背景，把 `imgUrl` 用作车辆本体图。
 * 这里直接使用 Android 自带网络与 BitmapFactory，避免为图片引入图片库。
 */
@Composable
fun AndroidVehicleImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    contentDescription: String = "车辆图片",
) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5_000
                    readTimeout = 8_000
                    instanceFollowRedirects = true
                }
                connection.inputStream.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    val image = bitmap ?: return
    Image(
        bitmap = image,
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
