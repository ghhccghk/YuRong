package top.ghhccghk.yadea

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import top.ghhccghk.yadea.settings.AppPreferences
import top.ghhccghk.yadea.settings.LocalStoragePreferenceStore

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        WithFontResourcesLoaded {
            App(preferences = AppPreferences(LocalStoragePreferenceStore()))
        }
    }
}
