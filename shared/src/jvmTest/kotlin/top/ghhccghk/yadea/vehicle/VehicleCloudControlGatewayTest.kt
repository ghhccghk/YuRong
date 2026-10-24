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

class VehicleCloudControlGatewayTest {

    @Test
    fun cloudControlUsesFortifyCommandIds() = runBlocking {
        val requests = mutableListOf<HttpRequest>()
        val gateway = YadeaVehicleCloudControlGateway(
            transport = object : HttpTransport {
                override suspend fun execute(request: HttpRequest): HttpResponse {
                    requests += request
                    return HttpResponse(
                        200,
                        Crypto.urlEncode(
                            Crypto.aesEcbPkcs5Encrypt(
                                GatewaySigner.AES_KEY,
                                """{"success":true}""",
                            ),
                        ),
                    )
                }
            },
            tokenProvider = { "token" },
            deviceIdProvider = { "device" },
            timestampProvider = { 1L },
        )

        assertTrue(gateway.send("VIN1", VehicleControlCommand.LOCK).success)
        assertTrue(gateway.send("VIN1", VehicleControlCommand.UNLOCK).success)

        assertEquals(2, requests.size)
        requests.forEach { request ->
            assertEquals("POST", request.method)
            assertTrue(request.url.endsWith(YadeaVehicleCloudControlGateway.PATH))
        }
        val lockJson = signedJson(requests[0].body!!)
        assertTrue(lockJson.contains("\"id\":\"FORTIFY\""))
        assertTrue(lockJson.contains("\"clientId\":\"1\""))
        assertTrue(lockJson.contains("\"controlMode\":\"11\""))
        assertTrue(lockJson.contains("\"vin\":\"VIN1\""))

        val unlockJson = signedJson(requests[1].body!!)
        assertTrue(unlockJson.contains("\"id\":\"RELEASE_FORTIFY\""))
    }

    @Test
    fun unsupportedCloudCommandIsRejectedBeforeNetwork() = runBlocking {
        var called = false
        val gateway = YadeaVehicleCloudControlGateway(
            transport = object : HttpTransport {
                override suspend fun execute(request: HttpRequest): HttpResponse {
                    called = true
                    return HttpResponse(200, "")
                }
            },
            tokenProvider = { "token" },
            timestampProvider = { 1L },
        )

        val result = gateway.send("VIN1", VehicleControlCommand.ODO_RESET)
        assertEquals(false, result.success)
        assertEquals(false, called)
    }

    private fun signedJson(body: String): String {
        val sign = body.split('&')
            .first { it.startsWith("gatewaySign=") }
            .substringAfter('=')
        val plain = Crypto.aesEcbPkcs5Decrypt(
            GatewaySigner.AES_KEY,
            Crypto.urlDecode(sign),
        )!!
        return plain.substringAfter("&json=")
    }
}
