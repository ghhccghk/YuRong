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
    fun cloudControlUsesVerifiedCommandIds() = runBlocking {
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

        val expectedIds = listOf(
            VehicleControlCommand.POWER_ON to "START",
            VehicleControlCommand.POWER_OFF to "STOP",
            VehicleControlCommand.FIND to "FIND",
            VehicleControlCommand.LOCK to "FORTIFY",
            VehicleControlCommand.UNLOCK to "RELEASE_FORTIFY",
        )
        expectedIds.forEach { (command, _) ->
            assertTrue(gateway.send("VIN1", command).success)
        }

        assertEquals(expectedIds.size, requests.size)
        requests.forEach { request ->
            assertEquals("POST", request.method)
            assertTrue(request.url.endsWith(YadeaVehicleCloudControlGateway.PATH))
        }
        expectedIds.forEachIndexed { index, (_, expectedId) ->
            val json = signedJson(requests[index].body!!)
            assertTrue(json.contains("\"id\":\"$expectedId\""))
            assertTrue(json.contains("\"clientId\":\"1\""))
            assertTrue(json.contains("\"controlMode\":\"11\""))
            assertTrue(json.contains("\"vin\":\"VIN1\""))
        }
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
