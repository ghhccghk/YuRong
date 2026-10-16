package top.ghhccghk.yadea.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * 界面风格往返切换后的可交互性回归测试（无窗口离屏渲染）。
 *
 * 用户反馈：在设置页用列表弹窗把风格从 Miuix 切回 Material 3 之后整个界面点不动。
 * 这里复现同样的时序——先展开 Miuix 原生列表弹层，再在弹层保持展开的情况下切换风格，
 * 最后向页面上的按钮发送一次真实点击，断言点击仍然能到达业务回调。
 */
class UiStyleSwitchInteractivityTest {

    @Test
    fun miuixPopupOverlayDoesNotBlockTouchesAfterSwitchingToMaterial3() {
        val appearance = mutableStateOf(
            AppAppearance(
                uiStyle = UiThemeStyle.Miuix,
                themeMode = AppThemeMode.Light,
                themeAnimationsEnabled = false,
            ),
        )
        var rowBounds: Rect? = null
        var targetBounds: Rect? = null
        var targetClicks = 0

        val scene = ImageComposeScene(width = 480, height = 900, density = Density(1f)) {
            AppTheme(appearance.value) {
                AppScaffold(
                    content = { padding ->
                        Column(
                            modifier = Modifier.fillMaxSize().padding(padding),
                        ) {
                            AppPopupPreference(
                                title = "界面风格",
                                options = listOf("Miuix", "Material 3"),
                                selectedIndex = UiThemeStyle.entries.indexOf(appearance.value.uiStyle),
                                onSelectedIndexChange = { index ->
                                    appearance.value = appearance.value.copy(
                                        uiStyle = UiThemeStyle.entries[index],
                                    )
                                },
                                modifier = Modifier.onGloballyPositioned { rowBounds = it.boundsInRoot() },
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .onGloballyPositioned { targetBounds = it.boundsInRoot() }
                                    .clickable { targetClicks++ },
                            )
                        }
                    },
                )
            }
        }

        try {
            var frame = 0L
            fun nextFrame(stepMillis: Long = 16L): Unit {
                frame += stepMillis * 1_000_000L
                scene.render(frame)
            }

            nextFrame()
            // 对照组：弹层没有打开时，点击必须能到达回调，否则说明测试环境本身不成立。
            tapCenter(scene, assertNotNull(targetBounds, "首次布局未取到目标按钮位置"))
            nextFrame()
            assertEquals(1, targetClicks, "对照组失败：Miuix 风格下普通点击没有生效")

            // 展开 Miuix 原生列表弹层（OverlayListPopup 会注册到根 Scaffold 的弹层宿主上）。
            tapCenter(scene, assertNotNull(rowBounds, "首次布局未取到界面风格设置项位置"))
            nextFrame()
            nextFrame()

            // 在弹层仍然展开的时候切换风格，等价于用户在弹层里点选“Material 3”。
            appearance.value = appearance.value.copy(uiStyle = UiThemeStyle.Material3)
            nextFrame()
            // 多跑几帧，给弹层退场动画留出时间。
            nextFrame(500L)
            nextFrame(500L)

            tapCenter(scene, assertNotNull(targetBounds, "切换风格后未取到目标按钮位置"))
            nextFrame()
            assertEquals(2, targetClicks, "从 Miuix 切回 Material 3 后有全屏遮罩拦住了触摸")
        } finally {
            scene.close()
        }
    }
}

/** 向指定矩形中心发送一次鼠标按下/抬起，模拟真实点击。 */
private fun tapCenter(scene: ImageComposeScene, bounds: Rect) {
    val center = bounds.center
    scene.sendPointerEvent(PointerEventType.Press, Offset(center.x, center.y))
    scene.sendPointerEvent(PointerEventType.Release, Offset(center.x, center.y))
}
