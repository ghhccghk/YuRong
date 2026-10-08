package top.ghhccghk.yadea.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

enum class UiThemeStyle(
    val storageName: String,
    val label: String,
    val description: String,
) {
    Miuix(
        storageName = "miuix",
        label = "Miuix",
        description = "HyperOS 风格组件与交互",
    ),
    Material3(
        storageName = "material3",
        label = "Material 3",
        description = "保留原有 Material Design 3 界面",
    );

    companion object {
        fun fromStorageName(value: String?): UiThemeStyle =
            entries.firstOrNull { it.storageName == value } ?: Miuix
    }
}

val LocalUiThemeStyle = staticCompositionLocalOf { UiThemeStyle.Miuix }

@Composable
fun rememberUiThemeStyle(): MutableState<UiThemeStyle> =
    rememberSaveable(saver = UiThemeStyleSaver) {
        mutableStateOf(UiThemeStyle.Miuix)
    }

@Composable
fun AppTheme(
    uiThemeStyle: UiThemeStyle,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalUiThemeStyle provides uiThemeStyle) {
        when (uiThemeStyle) {
            UiThemeStyle.Miuix -> {
                val controller = remember {
                    ThemeController(ColorSchemeMode.System)
                }
                MiuixTheme(controller = controller) {
                    MaterialTheme(content = content)
                }
            }

            UiThemeStyle.Material3 -> MaterialTheme(content = content)
        }
    }
}

private val UiThemeStyleSaver = androidx.compose.runtime.saveable.Saver<MutableState<UiThemeStyle>, String>(
    save = { it.value.storageName },
    restore = { value ->
        mutableStateOf(UiThemeStyle.fromStorageName(value))
    },
)


