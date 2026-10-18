package top.ghhccghk.yadea.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported

/**
 * Miuix 官方模糊能力的运行时支持情况。
 *
 * 对应官方文档《模糊效果 - 运行时能力检查》：Android 上模糊依赖 RuntimeShader（API 33+），
 * 而背景记录支架依赖 RenderEffect（API 32+）；Skiko 系（Desktop / iOS / macOS / Web）恒为 true。
 * 由于本项目 minSdk 低于 33 仍要发布单 APK，所有模糊相关代码路径都必须先用它门控。
 *
 * @property backdropSupported 背景记录支架是否可用，对应 `isRenderEffectSupported()`。
 * @property blurSupported 模糊/混合/高光是否可用，对应 `isRuntimeShaderSupported()`；是整个库的硬下限。
 */
@Immutable
data class AppBlurSupport(
    val backdropSupported: Boolean,
    val blurSupported: Boolean,
) {
    /** 背景记录与模糊效果同时可用时才允许启用整条模糊链路。 */
    val isUsable: Boolean
        get() = backdropSupported && blurSupported
}

/**
 * 读取当前平台对 Miuix 官方模糊能力的支持情况。
 *
 * 结果只与平台运行时有关，与主题或设置无关，因此只计算一次并缓存。
 */
@Composable
fun rememberAppBlurSupport(): AppBlurSupport = remember {
    AppBlurSupport(
        backdropSupported = isRenderEffectSupported(),
        blurSupported = isRuntimeShaderSupported(),
    )
}

/**
 * 当前 Miuix 脚手架捕获到的背景层。
 *
 * [AppScaffold] 在启用模糊时把 `rememberLayerBackdrop` 创建的捕获层下发给顶栏与底栏，
 * 顶栏/底栏只负责消费它并应用 `Modifier.textureBlur`，不需要自己接触任何 Miuix 私有类型。
 * 未启用模糊（或平台不支持）时为 null，顶栏/底栏保持原有纯色外观。
 */
internal val LocalAppBlurBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
