package top.ghhccghk.yadea.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.CancellationException
import top.ghhccghk.yadea.image.AppImages

/**
 * 跨平台远程图片组件。相同 URL 会共享请求、解码图和平台字节缓存。
 */
@Composable
fun AppRemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    forceRefresh: Boolean = false,
) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url, forceRefresh) {
        bitmap = try {
            AppImages.loadImage(url, forceRefresh)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
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
