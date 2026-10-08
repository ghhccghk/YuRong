package top.ghhccghk.yadea.mirror.source

/**
 * JVM desktop runtime information used to choose safe capture behavior.
 * java.awt.Robot is available on Windows, macOS and X11 Linux, but Wayland
 * sessions generally cannot expose a global screen to a plain AWT Robot.
 */
enum class DesktopOperatingSystem {
    Windows,
    MacOS,
    Linux,
    Other,
    ;

    companion object {
        val current: DesktopOperatingSystem by lazy {
            val name = System.getProperty("os.name").orEmpty().lowercase()
            when {
                name.contains("win") -> Windows
                name.contains("mac") || name.contains("darwin") -> MacOS
                name.contains("linux") || name.contains("unix") -> Linux
                else -> Other
            }
        }
    }
}

val isWaylandSession: Boolean
    get() = DesktopOperatingSystem.current == DesktopOperatingSystem.Linux &&
        !System.getenv("WAYLAND_DISPLAY").isNullOrBlank() &&
        System.getenv("DISPLAY").isNullOrBlank()

fun desktopScreenCaptureSupport(): Pair<Boolean, String?> = when {
    java.awt.GraphicsEnvironment.isHeadless() ->
        false to "当前 JVM 处于 headless 模式，无法捕获屏幕"

    isWaylandSession ->
        false to "Wayland 会话不支持 AWT Robot 全局捕获，请使用 X11/XWayland 会话"

    DesktopOperatingSystem.current == DesktopOperatingSystem.MacOS ->
        true to "首次使用请在 macOS 系统设置中授予屏幕录制权限"

    else -> true to null
}

fun desktopCaptureErrorMessage(error: Throwable): String {
    val platform = DesktopOperatingSystem.current
    val hint = when (platform) {
        DesktopOperatingSystem.MacOS -> "请确认已授予屏幕录制权限"
        DesktopOperatingSystem.Linux -> "请确认当前会话为 X11/XWayland"
        else -> "请确认桌面会话允许应用捕获屏幕"
    }
    return "$hint: ${error.message ?: error::class.simpleName}"
}
