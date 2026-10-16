package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 顶栏滚动行为回归测试（无窗口离屏渲染）。
 *
 * 用户反馈：从 Miuix 切回 Material 3 之后，页面无法上下拖动。
 * 原因是 Material 3 分支只在提供页面级操作时才渲染原生 TopAppBar，而原生 enterAlways 滚动行为
 * 在没有顶栏初始化收缩下限时仍会吃掉内容区的竖直位移。这里分别渲染两种风格、发送一次真实触摸
 * 拖动，断言内容确实滚动了；同时保证有顶栏时收起/展开的既有行为没有被改坏。
 */
class AppTopBarScrollTest {

    @Test
    fun miuixContentScrollsWithoutTopBarActions() {
        val result = dragContent(UiThemeStyle.Miuix, withTopBarActions = false)
        assertTrue(result.scrolledPx > 0, "对照组失败：Miuix 风格下内容区没有滚动，测试环境不成立")
    }

    @Test
    fun material3ContentScrollsWithoutTopBarActions() {
        val result = dragContent(UiThemeStyle.Material3, withTopBarActions = false)
        assertTrue(result.scrolledPx > 0, "Material 3 没有渲染原生顶栏时，顶栏滚动行为不应该吃掉内容滚动")
    }

    @Test
    fun material3TopBarWithActionsStillCollapses() {
        val result = dragContent(UiThemeStyle.Material3, withTopBarActions = true)
        assertTrue(result.topBarHeightOffset < 0f, "Material 3 原生顶栏存在时应当仍然随内容收起")
        assertTrue(result.scrolledPx > 0, "顶栏收起后剩下的位移应当继续交给内容区滚动")
    }
}

/** 一次拖动之后观察到的页面状态。 */
private class DragResult(
    /** 内容区实际滚动的像素数。 */
    val scrolledPx: Int,
    /** Material 3 顶栏的高度偏移；越负表示收起得越多。 */
    val topBarHeightOffset: Float,
)

/**
 * 渲染一个带长列表的页面，向上拖动一次后返回内容滚动量与顶栏偏移量。
 *
 * 页面刻意只提供标题、默认不提供页面级操作，对应设置页等“Material 3 不渲染原生顶栏”的场景。
 */
private fun dragContent(style: UiThemeStyle, withTopBarActions: Boolean): DragResult {
    val appearance = mutableStateOf(
        AppAppearance(
            uiStyle = style,
            themeMode = AppThemeMode.Light,
            themeAnimationsEnabled = false,
        ),
    )
    var listState: LazyListState? = null
    var scrollBehavior: AppScrollBehavior? = null

    val scene = ImageComposeScene(width = 480, height = 900, density = Density(1f)) {
        AppTheme(appearance.value) {
            val behavior = rememberAppScrollBehavior()
            scrollBehavior = behavior
            val state = rememberLazyListState()
            listState = state
            AppScaffold(
                topBar = {
                    AppTopBar(
                        title = "设置",
                        actions = if (withTopBarActions) {
                            { Text("操作") }
                        } else {
                            null
                        },
                        scrollBehavior = behavior,
                    )
                },
                scrollBehavior = behavior,
            ) { padding ->
                LazyColumn(
                    state = state,
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    items(200) { index ->
                        Box(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                            Text("列表项 $index")
                        }
                    }
                }
            }
        }
    }

    try {
        var frame = 0L
        fun nextFrame(stepMillis: Long = 16L) {
            frame += stepMillis * 1_000_000L
            scene.render(frame)
        }

        nextFrame()
        // 从列表中部向上拖动 300 像素，分步发送移动事件以越过触摸阈值。
        val start = Offset(240f, 600f)
        val end = Offset(240f, 300f)
        val pressed = PointerButtons(isPrimaryPressed = true)
        scene.sendPointerEvent(PointerEventType.Press, start, type = PointerType.Touch, buttons = pressed)
        repeat(10) { step ->
            val y = start.y + (end.y - start.y) * (step + 1) / 10f
            scene.sendPointerEvent(
                PointerEventType.Move,
                Offset(start.x, y),
                type = PointerType.Touch,
                buttons = pressed,
            )
            nextFrame()
        }
        scene.sendPointerEvent(PointerEventType.Release, end, type = PointerType.Touch, buttons = PointerButtons())
        nextFrame()
        nextFrame()

        val state = listState ?: return DragResult(scrolledPx = 0, topBarHeightOffset = 0f)
        return DragResult(
            scrolledPx = state.firstVisibleItemIndex * 48 + state.firstVisibleItemScrollOffset,
            // 顶栏高度偏移取真实状态，避免读到组合期缓存的旧值。
            topBarHeightOffset = scrollBehavior?.material?.state?.heightOffset ?: 0f,
        )
    } finally {
        scene.close()
    }
}
