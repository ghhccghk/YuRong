package top.ghhccghk.yadea.vehicle

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ghhccghk.yadea.network.HttpRequest
import top.ghhccghk.yadea.network.HttpResponse
import top.ghhccghk.yadea.network.HttpTransport

class VehicleProfileGatewayTest {
    @Test
    fun gatewayParsesVehicleImageLinkTagAndIot() = runBlocking {
        val body = """
            {"success":true,"data":[{
              "vin":"YD001",
              "modelNo":"LINK-01",
              "dLinkTag":"1",
              "bikeNickName":"小雅",
              "imgUrl":"https://cdn/normal.png",
              "skinPicUrl":"https://cdn/linked.png",
              "iotExpireDays":-120,
              "iotCardExpireTime":"2025-03-01",
              "iccid":"8986001",
              "cardStatus":2
            }]}
        """.trimIndent()
        val transport = object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                assertEquals("GET", request.method)
                assertTrue(request.url.endsWith("userBike/queryBindingBike"))
                assertEquals("Bearer token", request.headers["Authorization"])
                return HttpResponse(200, body)
            }
        }
        val gateway = YadeaVehicleProfileGateway(
            transport = transport,
            tokenProvider = { "token" },
        )

        val profile = gateway.load()
        assertNotNull(profile)
        assertEquals("YD001", profile!!.vin)
        assertEquals("https://cdn/linked.png", profile.vehicleImageUrl)
        assertTrue(profile.isLinkedModel)
        assertEquals("小雅", profile.bikeNickName)
        assertTrue(profile.iotService!!.isPermanentlyExpired)
        assertEquals("8986001", profile.iotService!!.iccid)
    }

    @Test
    fun gatewayFallsBackToImgUrlWhenSkinIsBlank() = runBlocking {
        val body = """{"data":[{"vin":"V","imgUrl":"https://cdn/normal.png","skinPicUrl":""}]}"""
        val gateway = YadeaVehicleProfileGateway(
            transport = object : HttpTransport {
                override suspend fun execute(request: HttpRequest) = HttpResponse(200, body)
            },
            tokenProvider = { "t" },
        )
        assertEquals("https://cdn/normal.png", gateway.load()!!.vehicleImageUrl)
    }

    @Test
    fun gatewayReturnsNullWithoutToken() = runBlocking {
        val gateway = YadeaVehicleProfileGateway(
            transport = object : HttpTransport {
                override suspend fun execute(request: HttpRequest): HttpResponse? = null
            },
            tokenProvider = { "" },
        )
        assertEquals(null, gateway.load())
    }
}
