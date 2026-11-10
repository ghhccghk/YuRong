package top.ghhccghk.yadea.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat
import top.ghhccghk.yadea.carsetting.CarSettingKind

/** Isolated UI only: no application root, accounts, controllers, gateway, BLE or network. */
class AtmosphereSettingsEditorTest {
    @Test
    fun originalDisplayOrderDoesNotBecomeCommandOrder() {
        assertEquals(listOf(0, 2, 1, 3), atmosphereModeOptions.map { it.first })
        assertEquals(listOf("常亮", "闪烁", "呼吸", "流水"), atmosphereModeOptions.map { it.second })
        assertEquals(listOf(0, 1, 4, 5, 2, 3), atmosphereRunOptions.map { it.first })
    }

    @Test
    fun hueTrackHasSixColorSegmentsAndAWhiteEndpoint() {
        listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta).forEachIndexed { index, color ->
            assertEquals(color, AtmosphereSpectrum.colorAt(index * 0.16f))
        }
        assertEquals(Color(254, 254, 254), AtmosphereSpectrum.colorAt(1f))
        assertEquals(1f, AtmosphereSpectrum.positionOf(Color(254, 254, 254)))
        assertEquals(0f, AtmosphereSpectrum.positionOf(Color.Red))
        assertEquals(0.64f, AtmosphereSpectrum.positionOf(Color.Blue))
    }

    @Test
    fun unknownAndKnownDraftsRenderWithoutEditingOrSubmittingInBothThemes() {
        UiThemeStyle.entries.forEach { style ->
            listOf(AppThemeMode.Light, AppThemeMode.Dark).forEach { mode ->
                listOf(320 to 760, 480 to 900, 840 to 540).forEach { (width, height) ->
                    listOf("unknown", "mono", "dual", "pending").forEach { variant ->
                        val selected = variant != "unknown"
                        var edits = 0
                        var writes = 0
                        var bounds: Rect? = null
                        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
                            AppTheme(AppAppearance(uiStyle = style, themeMode = mode, themeAnimationsEnabled = false)) {
                                CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides AppDesign.colors.onSurface) {
                                    Box(Modifier.fillMaxSize().background(AppDesign.colors.surface).padding(16.dp)) {
                                        AtmosphereSettingsEditor(
                                            draft = CarSettingKind.Atmosphere(showMode = 3, singleColor = variant != "dual"),
                                            hasSelection = selected,
                                            enabled = variant != "pending",
                                            pending = variant == "pending",
                                            status = "车辆模式与颜色：UNKNOWN" + if (selected) " · 草稿" else "",
                                            error = null,
                                            onEdit = { edits++ },
                                            onApply = { writes++ },
                                            modifier = Modifier.onGloballyPositioned { bounds = it.boundsInRoot() },
                                            customizationSwitch = {
                                                AppSwitchPreference(title = "氛围灯自定义", checked = true, onCheckedChange = {})
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        try {
                            scene.render(0L)
                            val image = scene.render(1_000_000_000L)
                            val layout = assertNotNull(bounds)
                            assertTrue(layout.left >= 0 && layout.right <= width)
                            assertTrue(layout.top >= 0 && layout.bottom <= height)
                            assertEquals(0, edits, "Rendering must not manufacture a draft")
                            assertEquals(0, writes, "Rendering must not submit")
                            val bytes = assertNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes
                            assertTrue(bytes.size > 1000)
                            val directory = File("build/reports/atmosphere-ui").apply { mkdirs() }
                            File(directory, "${style.name}-${mode.name}-${width}-$variant.png").writeBytes(bytes)
                        } finally {
                            scene.close()
                        }
                    }
                }
            }
        }
    }

    @Test
    fun nativeHueSlidersEditDraftOnlyAfterAnExplicitClickInBothThemes() {
        UiThemeStyle.entries.forEach { style ->
            var color by mutableStateOf<Color?>(null)
            var callbacks = 0
            var bounds: Rect? = null
            val scene = ImageComposeScene(width = 360, height = 100, density = Density(1f)) {
                AppTheme(AppAppearance(uiStyle = style, themeAnimationsEnabled = false)) {
                    AppAtmosphereSpectrumSlider(
                        color = color,
                        onColorChange = { color = it; callbacks++ },
                        enabled = true,
                        label = "颜色A",
                        modifier = Modifier.fillMaxWidth().onGloballyPositioned { bounds = it.boundsInRoot() },
                    )
                }
            }
            try {
                scene.render(0L)
                assertEquals(0, callbacks)
                assertEquals(null, color)
                val slider = assertNotNull(bounds)
                val point = Offset(slider.left + 14f + (slider.width - 28f) * 0.64f, slider.center.y)
                scene.sendPointerEvent(PointerEventType.Press, point)
                scene.sendPointerEvent(PointerEventType.Release, point)
                scene.render(1_000_000_000L)
                val picked = assertNotNull(color, "${style.name} slider did not accept a click")
                assertTrue(picked.blue > 0.9f && picked.red < 0.1f && picked.green < 0.1f)
                assertTrue(callbacks > 0)
                val beforeDrag = callbacks
                scene.sendPointerEvent(PointerEventType.Press, Offset(90f, slider.center.y))
                scene.sendPointerEvent(PointerEventType.Move, Offset(170f, slider.center.y))
                scene.render(1_100_000_000L)
                scene.sendPointerEvent(PointerEventType.Release, Offset(170f, slider.center.y))
                scene.render(1_200_000_000L)
                assertTrue(callbacks > beforeDrag, "${style.name} slider lost native dragging")
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun disabledNativeHueSlidersDoNotEditInEitherTheme() {
        UiThemeStyle.entries.forEach { style ->
            var callbacks = 0
            val scene = ImageComposeScene(width = 360, height = 100, density = Density(1f)) {
                AppTheme(AppAppearance(uiStyle = style, themeAnimationsEnabled = false)) {
                    AppAtmosphereSpectrumSlider(Color.Red, { callbacks++ }, false, "颜色", Modifier.fillMaxWidth())
                }
            }
            try {
                scene.render(0L)
                scene.sendPointerEvent(PointerEventType.Press, Offset(180f, 24f))
                scene.sendPointerEvent(PointerEventType.Release, Offset(180f, 24f))
                scene.render(1_000_000_000L)
                assertEquals(0, callbacks)
            } finally {
                scene.close()
            }
        }
    }
}
