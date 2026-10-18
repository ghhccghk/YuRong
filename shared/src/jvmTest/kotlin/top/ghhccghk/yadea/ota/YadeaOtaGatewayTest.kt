package top.ghhccghk.yadea.ota

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ghhccghk.yadea.auth.Crypto
import top.ghhccghk.yadea.auth.GatewaySigner
import top.ghhccghk.yadea.network.HttpRequest
import top.ghhccghk.yadea.network.HttpResponse
import top.ghhccghk.yadea.network.HttpTransport
import top.ghhccghk.yadea.vehicle.VehicleProfile

class YadeaOtaGatewayTest {
    private fun encrypted(body: String): String =
        Crypto.urlEncode(Crypto.aesEcbPkcs5Encrypt(GatewaySigner.AES_KEY, body))

    @Test
    fun checkForUpdate_parsesFirmwareVersionAndDownload() = runBlocking {
        val plain = """
            {"success":true,"data":[{
              "firmwareVersion":1.23,
              "upgradeContent":"修复已知问题",
              "url":"https://cdn/firmware.bin"
            }]}
        """.trimIndent()
        val transport = object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                assertTrue(request.url.endsWith("firmwareVersion/latestVersion"))
                assertTrue(request.body!!.contains("gatewaySign="))
                return HttpResponse(200, encrypted(plain))
            }
        }
        val gateway = YadeaOtaGateway(
            transport = transport,
            profileProvider = { VehicleProfile(vin = "V", modelName = "X1") },
            tokenProvider = { "token" },
            timestampProvider = { 1_700_000_000_000L },
        )

        val packageInfo = gateway.checkForUpdate("1.0")
        assertNotNull(packageInfo)
        assertEquals("1.23", packageInfo!!.version)
        assertEquals("https://cdn/firmware.bin", packageInfo.downloadUrl)
        assertEquals("修复已知问题", packageInfo.releaseNotes)
    }

    @Test
    fun checkForUpdate_returnsNullWithoutToken() = runBlocking {
        val gateway = YadeaOtaGateway(
            transport = object : HttpTransport {
                override suspend fun execute(request: HttpRequest): HttpResponse? = null
            },
            profileProvider = { VehicleProfile(vin = "V", modelName = "X1") },
            tokenProvider = { "" },
            timestampProvider = { 1L },
        )
        assertEquals(null, gateway.checkForUpdate("1.0"))
    }

    @Test
    fun install_doesNotFakeSuccessWithoutTransport() = runBlocking {
        val gateway = YadeaOtaGateway(
            transport = object : HttpTransport {
                override suspend fun execute(request: HttpRequest): HttpResponse? = null
            },
            profileProvider = { null },
            tokenProvider = { "t" },
            timestampProvider = { 1L },
        )
        assertFalse(gateway.install(OtaPackage("1.1")) { _, _ -> })
    }
}
