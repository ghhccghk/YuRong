package top.ghhccghk.yadea.network

data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val contentType: String? = null,
)

data class HttpResponse(
    val status: Int,
    val body: String,
)

interface HttpTransport {
    suspend fun execute(request: HttpRequest): HttpResponse?
}

/** 未接入网络实现的平台返回 `null`。 */
object UnavailableHttpTransport : HttpTransport {
    override suspend fun execute(request: HttpRequest): HttpResponse? = null
}

expect fun createHttpTransport(): HttpTransport
