package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ghhccghk.yadea.auth.Crypto
import top.ghhccghk.yadea.auth.GatewaySigner
import top.ghhccghk.yadea.network.HttpRequest
import top.ghhccghk.yadea.network.HttpResponse
import top.ghhccghk.yadea.network.HttpTransport

/**
 * Offline request-shape tests. These tests only inspect fake transport requests;
 * they never connect to the cloud or a vehicle.
 */
class CloudCarSettingsApiSemanticsTest {

    private class FakeTransport : HttpTransport {
        val requests = mutableListOf<HttpRequest>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request
            return HttpResponse(200, """{"success":true,"data":true}""")
        }
    }

    private fun gateway(transport: HttpTransport): YadeaCarSettingsGateway =
        YadeaCarSettingsGateway(
            transport = transport,
            tokenProvider = { "token" },
            timestampProvider = { 1_700_000_000_000L },
            baseUrl = "https://example.invalid/api/app/",
        )

    private fun businessJson(request: HttpRequest): String {
        val body = requireNotNull(request.body)
        val encodedSign = body.substringAfter("gatewaySign=").substringBefore("&")
        val plain = Crypto.aesEcbPkcs5Decrypt(
            GatewaySigner.AES_KEY,
            Crypto.urlDecode(encodedSign),
        ) ?: error("gatewaySign did not decrypt")
        return plain.substringAfter("json=")
    }

    @Test
    fun bikeSettingUsesUpdateBikeSettingAndTypedScalarFields() = runBlocking {
        val transport = FakeTransport()
        val update = requireNotNull(
            CloudCarSettingUpdateBuilder.build(
                configId = "10",
                kind = CarSettingKind.Toggle(true),
                vin = "VIN",
            ),
        )
        assertEquals(CloudCarSettingUpdatePath.BIKE_SETTING, update.path)

        assertTrue(gateway(transport).update("VIN", update))

        val request = transport.requests.single()
        assertEquals("POST", request.method)
        assertEquals(
            "https://example.invalid/api/app/vehiclebase/bike-setting/updateBikeSetting",
            request.url,
        )
        assertEquals("application/x-www-form-urlencoded; charset=utf-8", request.contentType)
        val json = businessJson(request)
        assertTrue(json.contains("\"_gateway\":\"True\""))
        assertTrue(json.contains("\"distanceOn\":\"1\""))
        assertTrue(json.contains("\"vin\":\"VIN\""))
    }

    @Test
    fun parameterSettingUsesLowFreqPathAndValueJson() = runBlocking {
        val transport = FakeTransport()
        val update = requireNotNull(
            CloudCarSettingUpdateBuilder.build(
                configId = "60",
                kind = CarSettingKind.Choice(4, listOf("13Ah", "15Ah", "22Ah", "25Ah", "32Ah", "38Ah")),
                vin = "VIN",
            ),
        )
        assertEquals(CloudCarSettingUpdatePath.PARAM_SETTING, update.path)

        assertTrue(gateway(transport).update("VIN", update))

        val request = transport.requests.single()
        assertEquals("POST", request.method)
        assertEquals(
            "https://example.invalid/api/app/low-freq/common-cmd/param-setting",
            request.url,
        )
        val json = businessJson(request)
        assertTrue(json.contains("\"id\":\"BATT_V_A_CAP_MODEL\""))
        assertTrue(json.contains("\"oper\":\"SET_PARAM\""))
        assertTrue(json.contains("\"value\":32"))
        assertTrue(
            json.contains(
                "\"valueJson\":[{\"byteCount\":1,\"dataType\":1,\"sort\":1,\"value\":null}," +
                    "{\"byteCount\":1,\"dataType\":1,\"sort\":2,\"value\":32}," +
                    "{\"byteCount\":1,\"dataType\":4,\"sort\":3,\"value\":null}," +
                    "{\"byteCount\":2,\"dataType\":4,\"sort\":4,\"value\":null}]",
            ),
        )
    }

    @Test
    fun parameterSwitchUsesConfirmedSingleParamJson() {
        val update = requireNotNull(
            CloudCarSettingUpdateBuilder.build(
                configId = "62",
                kind = CarSettingKind.Toggle(true),
                vin = "VIN",
            ),
        )
        assertEquals(1, update.fields["seatHeating"])
        assertEquals(1, update.fields["value"])
        assertEquals(
            "[{\"byteCount\":1,\"dataType\":1,\"sort\":1,\"value\":1}]",
            update.fields["valueJson"],
        )
    }
}
