package top.ghhccghk.yadea.ui

import androidx.compose.material3.SnackbarDuration as MaterialSnackbarDuration
import androidx.compose.material3.SnackbarResult as MaterialSnackbarResult
import org.junit.Assert.assertEquals
import org.junit.Test
import top.yukonga.miuix.kmp.basic.SnackbarDuration as MiuixSnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarResult as MiuixSnackbarResult

/**
 * 轻量提示桥接的映射测试。
 *
 * 这里只覆盖业务枚举与两套原生类型之间的换算，不涉及 Composable：
 * 业务枚举是页面唯一可见的类型，映射一旦错位，两种风格下提示的时长和返回值都会跑偏。
 */
class AppSnackbarTest {

    @Test
    fun durationMapsToMiuix() {
        assertEquals(MiuixSnackbarDuration.Short, AppSnackbarDuration.Short.toMiuixDuration())
        assertEquals(MiuixSnackbarDuration.Long, AppSnackbarDuration.Long.toMiuixDuration())
        assertEquals(
            MiuixSnackbarDuration.Indefinite,
            AppSnackbarDuration.Indefinite.toMiuixDuration(),
        )
    }

    @Test
    fun durationMapsToMaterial() {
        assertEquals(MaterialSnackbarDuration.Short, AppSnackbarDuration.Short.toMaterialDuration())
        assertEquals(MaterialSnackbarDuration.Long, AppSnackbarDuration.Long.toMaterialDuration())
        assertEquals(
            MaterialSnackbarDuration.Indefinite,
            AppSnackbarDuration.Indefinite.toMaterialDuration(),
        )
    }

    @Test
    fun miuixResultMapsToAppResult() {
        assertEquals(AppSnackbarResult.Dismissed, MiuixSnackbarResult.Dismissed.toAppResult())
        assertEquals(
            AppSnackbarResult.ActionPerformed,
            MiuixSnackbarResult.ActionPerformed.toAppResult(),
        )
    }

    @Test
    fun materialResultMapsToAppResult() {
        assertEquals(AppSnackbarResult.Dismissed, MaterialSnackbarResult.Dismissed.toAppResult())
        assertEquals(
            AppSnackbarResult.ActionPerformed,
            MaterialSnackbarResult.ActionPerformed.toAppResult(),
        )
    }

    @Test
    fun durationEnumsKeepMatchingOrder() {
        // 两个方向枚举值一一对应，任何一边新增档位都应在这里被发现。
        assertEquals(
            AppSnackbarDuration.entries.map { it.toMiuixDuration() },
            listOf(
                MiuixSnackbarDuration.Short,
                MiuixSnackbarDuration.Long,
                MiuixSnackbarDuration.Indefinite,
            ),
        )
        assertEquals(
            AppSnackbarDuration.entries.map { it.toMaterialDuration() },
            listOf(
                MaterialSnackbarDuration.Short,
                MaterialSnackbarDuration.Long,
                MaterialSnackbarDuration.Indefinite,
            ),
        )
    }
}