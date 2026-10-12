package top.ghhccghk.yadea

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import top.ghhccghk.yadea.mirror.source.JvmFrameSourceFactory
import top.ghhccghk.yadea.settings.AppPreferences
import top.ghhccghk.yadea.settings.JavaPreferencesStore
import top.ghhccghk.yadea.ui.MainScreen

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "touping",
    ) {
        MainScreen(
            frameSourceFactory = JvmFrameSourceFactory(),
            preferences = AppPreferences(JavaPreferencesStore()),
        )
    }
}
