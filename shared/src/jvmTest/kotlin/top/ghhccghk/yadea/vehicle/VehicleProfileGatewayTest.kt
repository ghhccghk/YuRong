package top.ghhccghk.yadea.vehicle

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ghhccghk.yadea.network.HttpRequest
import top.ghhccghk.yadea.network.HttpResponse
import top.ghhccghk.yadea.network.HttpTransport
import top.ghhccghk.yadea.network.GatewayHeaders

class VehicleProfileGatewayTest {
    @Test
    fun gatewayKeepsBackgroundAndBodyImagesSeparate() = runBlocking {
        val body = """
            {"success":true,"data":[{
              "vin":"YD001",
              "modelNo":"LINK-01",
              "seriesCode":"3",
              "seriesName":"冠能",
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
                assertTrue(request.url.contains("userBike/queryBindingBike?"))
                assertEquals(
                    GatewayHeaders.GATEWAY_APP_ID,
                    request.headers[GatewayHeaders.KEY_GATEWAY_APP_ID],
                )
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
        assertEquals("3", profile.seriesCode)
        assertEquals("冠能", profile.seriesName)
        assertEquals("https://cdn/linked.png", profile.backgroundImageUrl)
        assertEquals("https://cdn/normal.png", profile.vehicleImageUrl)
        assertTrue(profile.isLinkedModel)
        assertEquals("小雅", profile.bikeNickName)
        assertTrue(profile.iotService!!.isPermanentlyExpired)
        assertEquals("8986001", profile.iotService!!.iccid)
    }

    @Test
    fun gatewayDoesNotUseBodyImageAsBackground() = runBlocking {
        val body = """{"data":[{"vin":"V","imgUrl":"https://cdn/normal.png","skinPicUrl":""}]}"""
        val gateway = YadeaVehicleProfileGateway(
            transport = object : HttpTransport {
                override suspend fun execute(request: HttpRequest) = HttpResponse(200, body)
            },
            tokenProvider = { "t" },
        )
        val profile = gateway.load()!!
        assertEquals("", profile.backgroundImageUrl)
        assertEquals("https://cdn/normal.png", profile.vehicleImageUrl)
    }

    @Test
    fun gatewayLoadsAllBoundVehicles() = runBlocking {
        val body = """
            {"data":[
              {"vin":"V1","modelName":"X1","imgUrl":"https://cdn/1.png"},
              {"vin":"V2","modelName":"X2","imgUrl":"https://cdn/2.png"}
            ]}
        """.trimIndent()
        val gateway = YadeaVehicleProfileGateway(
            transport = object : HttpTransport {
                override suspend fun execute(request: HttpRequest) = HttpResponse(200, body)
            },
            tokenProvider = { "t" },
        )
        val profiles = gateway.loadAll()
        assertEquals(2, profiles.size)
        assertEquals("V1", profiles[0].vin)
        assertEquals("V2", profiles[1].vin)
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
