package top.ghhccghk.yadea.ota

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class JvmWifiOtaFileServer : WifiOtaFileServer {
    private val running = AtomicBoolean(false)
    private val serverSocket = AtomicReference<ServerSocket?>()
    private val currentClient = AtomicReference<Socket?>()

    override suspend fun serve(
        firmware: ByteArray,
        fileName: String,
        port: Int,
        timeoutMs: Long,
        onStarted: () -> Unit,
        onProgress: (sentBytes: Long, totalBytes: Long) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        if (firmware.isEmpty()) return@withContext false
        if (!running.compareAndSet(false, true)) return@withContext false
        val server = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(port))
        }
        serverSocket.set(server)
        server.soTimeout = ACCEPT_POLL_MS
        onStarted()
        val deadline = System.currentTimeMillis() + timeoutMs
        var completed = false
        try {
            while (running.get() && !completed && System.currentTimeMillis() < deadline) {
                val client = try {
                    server.accept()
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (_: Exception) {
                    break
                }
                currentClient.set(client)
                completed = runCatching {
                    client.use { handleClient(it, firmware, onProgress) }
                }.getOrDefault(false)
                currentClient.set(null)
            }
            completed
        } finally {
            running.set(false)
            currentClient.getAndSet(null)?.closeQuietly()
            serverSocket.getAndSet(null)?.closeQuietly()
        }
    }

    override fun stop() {
        running.set(false)
        currentClient.getAndSet(null)?.closeQuietly()
        serverSocket.getAndSet(null)?.closeQuietly()
    }

    private fun handleClient(
        client: Socket,
        firmware: ByteArray,
        onProgress: (Long, Long) -> Unit,
    ): Boolean {
        client.soTimeout = CLIENT_READ_TIMEOUT_MS
        val reader = BufferedReader(
            InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII),
        )
        val requestLine = reader.readLine() ?: return false
        val requestParts = requestLine.split(' ', limit = 3)
        if (requestParts.size < 2) {
            respondText(client, 400, "Bad Request", "Bad Request")
            return false
        }
        if (!requestParts[0].equals("GET", ignoreCase = true)) {
            respondText(client, 405, "Method Not Allowed", "Method Not Allowed", listOf("Allow: GET"))
            return false
        }

        val headers = linkedMapOf<String, String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            headers[line.substring(0, separator).trim().lowercase()] =
                line.substring(separator + 1).trim()
        }

        val target = requestParts[1]
        val query = target.substringAfter('?', "")
        val finish = WifiOtaProtocol.queryContainsFinish(query)
        val total = firmware.size.toLong()
        val rangeHeader = headers["range"]
        if (!rangeHeader.isNullOrBlank()) {
            val range = WifiOtaProtocol.parseRange(rangeHeader, total)
            if (range == null) {
                respondText(
                    client,
                    416,
                    "Range Not Satisfiable",
                    "",
                    listOf("Content-Range: bytes */$total"),
                )
                return false
            }
            val output = client.getOutputStream()
            writeOctetStreamHeaders(
                output = output,
                code = 206,
                reason = "Partial Content",
                contentLength = range.length,
                contentRange = "bytes ${range.start}-${range.endInclusive}/$total",
            )
            sendRange(client, firmware, range, onProgress)
            return finish
        }

        val output = client.getOutputStream()
        writeOctetStreamHeaders(
            output = output,
            code = 200,
            reason = "OK",
            contentLength = total,
            contentRange = null,
        )
        sendRange(client, firmware, WifiByteRange(0L, total - 1L), onProgress)
        return true
    }

    private fun sendRange(
        client: Socket,
        firmware: ByteArray,
        range: WifiByteRange,
        onProgress: (Long, Long) -> Unit,
    ) {
        val output = client.getOutputStream()
        var sent = 0L
        var cursor = range.start.toInt()
        val endExclusive = (range.endInclusive + 1L).toInt()
        while (cursor < endExclusive) {
            val count = minOf(WifiOtaProtocol.CHUNK_SIZE, endExclusive - cursor)
            output.write(firmware, cursor, count)
            cursor += count
            sent += count
            onProgress(sent, range.length)
        }
        output.flush()
        client.shutdownOutput()
    }

    private fun writeOctetStreamHeaders(
        output: java.io.OutputStream,
        code: Int,
        reason: String,
        contentLength: Long,
        contentRange: String?,
    ) {
        val headers = buildString {
            append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
            append("Content-Type: application/octet-stream\r\n")
            append("Content-Length: ").append(contentLength).append("\r\n")
            if (contentRange != null) {
                append("Content-Range: ").append(contentRange).append("\r\n")
            }
            append("Connection: close\r\n\r\n")
        }
        output.write(headers.toByteArray(StandardCharsets.US_ASCII))
        output.flush()
    }

    private fun respondText(
        client: Socket,
        code: Int,
        reason: String,
        body: String,
        extraHeaders: List<String> = emptyList(),
    ) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
            append("Content-Type: text/plain; charset=UTF-8\r\n")
            append("Content-Length: ").append(bytes.size).append("\r\n")
            extraHeaders.forEach { append(it).append("\r\n") }
            append("Connection: close\r\n\r\n")
        }
        val output = client.getOutputStream()
        output.write(headers.toByteArray(StandardCharsets.US_ASCII))
        if (bytes.isNotEmpty()) output.write(bytes)
        output.flush()
    }

    private fun Socket.closeQuietly() {
        runCatching { close() }
    }

    private fun ServerSocket.closeQuietly() {
        runCatching { close() }
    }

    private companion object {
        const val ACCEPT_POLL_MS = 1_000
        const val CLIENT_READ_TIMEOUT_MS = 15_000
    }
}

actual fun createWifiOtaFileServer(): WifiOtaFileServer = JvmWifiOtaFileServer()
