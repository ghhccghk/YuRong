package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals

/**
 * Miuix 动态取色切换的端到端回归测试（无窗口离屏渲染）。
 *
 * 覆盖用户反馈的"换色时只有一部分组件跟着切"：这类组件读取的是 AppDesign token
 * （`LocalAppThemeTokens`），而 Miuix 的 Colors 是可变异实例，若 token 缓存键选错就会停留在旧配色。
 * 这里真实地跑一遍 AppTheme 组合：换种子色后重新渲染一帧，断言 AppDesign.colors.primary 已经变化。
 */
class MiuixDynamicColorSwitchTest {

    @Test
    fun appDesignTokensFollowSeedColorChangeInMiuixStyle() {
        // 关闭颜色过渡动画，让换色在同一帧内立即生效，避免依赖动画时序。
        val appearance = mutableStateOf(
            AppAppearance(
                uiStyle = UiThemeStyle.Miuix,
                themeMode = AppThemeMode.Dark,
                colorSource = AppColorSource.SeedColor,
                seedColorArgb = SeedViolet,
                themeAnimationsEnabled = false,
            ),
        )
        var capturedPrimary: Color? = null

        val scene = ImageComposeScene(width = 64, height = 64, density = Density(1f)) {
            AppTheme(appearance = appearance.value) {
                // 读取的是业务页面使用的语义 token，而不是 MiuixTheme.colorScheme。
                capturedPrimary = AppDesign.colors.primary
                Box(Modifier.size(1.dp))
            }
        }
        try {
            scene.render()
            val before = assertNotNull(capturedPrimary, "首次渲染未取到 AppDesign.colors.primary")

            appearance.value = appearance.value.copy(seedColorArgb = SeedGreen)
            scene.render()
            val after = assertNotNull(capturedPrimary, "换色后未取到 AppDesign.colors.primary")

            assertNotEquals(before, after, "切换种子色后 AppDesign token 没有跟随变化")
        } finally {
            scene.close()
        }
    }
}

/** 用于换色的两个差异明显的种子色，避免调色板推导后仍得到相同主色。 */
private val SeedViolet = 0xFF7B4DFF.toInt()
private val SeedGreen = 0xFF006D39.toInt()
