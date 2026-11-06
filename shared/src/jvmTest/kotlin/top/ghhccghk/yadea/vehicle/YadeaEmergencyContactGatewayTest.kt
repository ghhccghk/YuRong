package top.ghhccghk.yadea.vehicle

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ghhccghk.yadea.auth.Crypto
import top.ghhccghk.yadea.auth.GatewaySigner
import top.ghhccghk.yadea.network.HttpRequest
import top.ghhccghk.yadea.network.HttpResponse
import top.ghhccghk.yadea.network.HttpTransport

class YadeaEmergencyContactGatewayTest {

    @Test
    fun savePreservesContactValueWhitespace() = runBlocking {
        var capturedRequest: HttpRequest? = null
        val gateway = YadeaEmergencyContactGateway(
            transport = object : HttpTransport {
                override suspend fun execute(request: HttpRequest): HttpResponse {
                    capturedRequest = request
                    return HttpResponse(200, """{"success":true,"data":null}""")
                }
            },
            tokenProvider = { "token" },
            timestampProvider = { 1_700_000_000_000L },
        )

        val saved = gateway.save(
            vin = "VIN123",
            type = ContactType.Owner,
            name = " 车主 ",
            mobile = " 13800138000 ",
        )

        assertTrue(saved)
        val payload = signedJson(capturedRequest?.body.orEmpty())
        assertEquals(
            """{"_gateway":"True","mobile":" 13800138000 ","name":" 车主 ","type":"1","vin":"VIN123"}""",
            payload,
        )
    }

    private fun signedJson(body: String): String {
        val sign = body.split('&')
            .first { it.startsWith("gatewaySign=") }
            .substringAfter('=')
        val plain = Crypto.aesEcbPkcs5Decrypt(
            GatewaySigner.AES_KEY,
            Crypto.urlDecode(sign),
        )
        return plain.orEmpty().substringAfter("&json=")
    }
}
