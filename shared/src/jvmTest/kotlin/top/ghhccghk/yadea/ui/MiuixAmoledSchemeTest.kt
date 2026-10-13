package top.ghhccghk.yadea.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 验证 Miuix 侧的 AMOLED 纯黑变换：只压黑大面积底色 token，文字与描边保持可读。
 */
class MiuixAmoledSchemeTest {
    @Test
    fun disabledReturnsTheSameScheme() {
        val scheme = darkColorScheme()
        assertSame(scheme, scheme.withAmoledBackground(false))
    }

    @Test
    fun enabledBlacksOutBackgroundTokens() {
        // 用亮色色板验证，避免暗色色板本身已经是黑导致的假通过（例如 surface 默认就是纯黑）。
        val scheme = lightColorScheme()
        val amoled = scheme.withAmoledBackground(true)

        assertEquals(Color.Black, amoled.background)
        assertEquals(Color.Black, amoled.surface)
        assertEquals(Color.Black, amoled.surfaceVariant)
        assertEquals(Color.Black, amoled.surfaceContainer)
        assertEquals(Color.Black, amoled.surfaceContainerHigh)
        assertEquals(Color.Black, amoled.surfaceContainerHighest)
    }

    @Test
    fun enabledKeepsTextOutlineAndAccentTokens() {
        val scheme = darkColorScheme()
        val amoled = scheme.withAmoledBackground(true)

        assertEquals(scheme.onBackground, amoled.onBackground)
        assertEquals(scheme.onSurface, amoled.onSurface)
        assertEquals(scheme.onSurfaceContainer, amoled.onSurfaceContainer)
        assertEquals(scheme.outline, amoled.outline)
        assertEquals(scheme.dividerLine, amoled.dividerLine)
        assertEquals(scheme.primary, amoled.primary)
        assertEquals(scheme.windowDimming, amoled.windowDimming)
    }
}
