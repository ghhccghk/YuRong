package top.ghhccghk.yadea.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

actual fun createHttpTransport(): HttpTransport = AndroidHttpTransport()

private class AndroidHttpTransport : HttpTransport {
    override suspend fun execute(request: HttpRequest): HttpResponse? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
                requestMethod = request.method
                connectTimeout = 8_000
                readTimeout = 12_000
                instanceFollowRedirects = true
                request.headers.forEach { (key, value) -> setRequestProperty(key, value) }
                request.contentType?.let { setRequestProperty("Content-Type", it) }
            }
            if (request.body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(request.body.encodeToByteArray()) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            HttpResponse(status, body)
        }.getOrNull()
    }
}
