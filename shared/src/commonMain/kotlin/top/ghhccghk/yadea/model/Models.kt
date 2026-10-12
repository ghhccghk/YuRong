package top.ghhccghk.yadea.model

data class NavDisplayConfig(
    val width: Int = 1024,
    val height: Int = 512,
    val imageFormat: Int = 0,
    val capabilities: Int = 0xF6,
    val maxFps: Int = 10
)
