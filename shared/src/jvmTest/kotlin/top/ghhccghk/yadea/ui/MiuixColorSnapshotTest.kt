package top.ghhccghk.yadea.ui

import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.Color
import java.lang.reflect.Field
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import top.yukonga.miuix.kmp.theme.Colors as MiuixColors
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 主题换色回归防护。
 *
 * Miuix 的 [MiuixColors] 是字段为 mutableStateOf 的可变 @Stable 类：切换配色时 MiuixTheme 只会
 * 就地更新同一个实例的字段，对象引用不变。因此 AppDesign 的 token 缓存必须依赖"颜色快照"而不是
 * 实例引用，否则换色时会出现"只有一部分组件跟着切"。
 *
 * 这里锁定配色快照的两个前提：覆盖全部颜色字段、能反映同一个实例的就地改动。
 */
class MiuixColorSnapshotTest {

    @Test
    fun snapshotReflectsEveryInPlaceFieldUpdate() {
        // 逐个字段就地改写，模拟 MiuixTheme 的配色更新方式；
        // 只要某个字段没进快照，这一轮的 before/after 就会相等，从而暴露"切不干净"的回归。
        val colors = lightColorScheme()
        val fields = colors.colorStateFields()
        assertTrue(fields.size >= 50, "反射取到的 Miuix 颜色字段数量异常：${fields.size}")

        fields.forEach { field ->
            val before = colors.toColorList()
            val current = field.readColor(colors)
            field.writeColor(colors, if (current == ProbeColorA) ProbeColorB else ProbeColorA)
            assertNotEquals(before, colors.toColorList(), "字段 ${field.name} 未参与配色快照")
        }
    }

    @Test
    fun snapshotRoundTripsBackToTheSameColors() {
        // 快照顺序必须与 Miuix Colors 的构造顺序一致，否则过渡动画会把颜色错位重建。
        // 字段 setter 是 internal 的，因此直接用官方亮/暗色板作为样本。
        listOf(lightColorScheme(), darkColorScheme()).forEach { colors ->
            val restored = colors.toColorList().toMiuixColors()
            colors.colorStateFields().forEach { field ->
                assertEquals(
                    field.readColor(colors),
                    field.readColor(restored),
                    "字段 ${field.name} 在快照往返后丢失",
                )
            }
        }
    }
}

private val ProbeColorA = Color(0xFF010203)
private val ProbeColorB = Color(0xFF040506)

/** 取出 [MiuixColors] 背后的全部 mutableStateOf 字段：每个 `var` 属性都会生成一个 `\$delegate` 字段。 */
private fun MiuixColors.colorStateFields(): List<Field> =
    MiuixColors::class.java.declaredFields
        .filter { it.name.endsWith("\$delegate") }
        .onEach { it.isAccessible = true }

@Suppress("UNCHECKED_CAST")
private fun Field.readColor(colors: MiuixColors): Color =
    (get(colors) as MutableState<Color>).value

@Suppress("UNCHECKED_CAST")
private fun Field.writeColor(colors: MiuixColors, color: Color) {
    (get(colors) as MutableState<Color>).value = color
}
