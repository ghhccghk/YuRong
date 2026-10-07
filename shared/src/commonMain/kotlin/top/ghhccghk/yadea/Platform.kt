package top.ghhccghk.yadea

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform