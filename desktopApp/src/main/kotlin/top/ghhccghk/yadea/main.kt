package top.ghhccghk.yadea

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import top.ghhccghk.yadea.mirror.source.JvmFrameSourceFactory
import top.ghhccghk.yadea.settings.AppPreferences
import top.ghhccghk.yadea.settings.JavaPreferencesStore
import top.ghhccghk.yadea.ui.MainScreen

fun main() {
    val instrumentImagePicker = DesktopInstrumentImagePicker()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "touping",
        ) {
            MainScreen(
                frameSourceFactory = JvmFrameSourceFactory(),
                instrumentImagePicker = instrumentImagePicker,
                preferences = AppPreferences(JavaPreferencesStore()),
            )
        }
    }
}
