package top.ghhccghk.yadea.vehicle

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

class VehicleDiagnosticsTest {

    private fun encrypted(body: String): String =
        Crypto.urlEncode(Crypto.aesEcbPkcs5Encrypt(GatewaySigner.AES_KEY, body))

    @Test
    fun faultGatewayParsesLevelMap() = runBlocking {
        val plain = """
            {"success":true,"data":{
              "vin":"VIN1",
              "faultLevelMap":[
                {"faultLevel":1,"faultName":"normal","faultLevelDesc":"车辆状态正常","fualtShowName":"检测正常"},
                {"faultLevel":3,"faultName":"battery","faultLevelDesc":"电池温度过高","fualtShowName":"电池异常"}
              ]
            }}
        """.trimIndent()
        val gateway = YadeaVehicleFaultGateway(
            transport = object : HttpTransport {
                override suspend fun execute(request: HttpRequest): HttpResponse {
                    assertEquals("POST", request.method)
                    assertTrue(request.url.endsWith(YadeaVehicleFaultGateway.PATH))
                    assertTrue(request.body!!.contains("modelNo"))
                    return HttpResponse(200, encrypted(plain))
                }
            },
            tokenProvider = { "token" },
            timestampProvider = { 1L },
        )

        val report = gateway.load("X1")
        assertNotNull(report)
        assertEquals("VIN1", report!!.vin)
        assertEquals(2, report.levels.size)
        assertEquals("电池异常", report.levels[1].displayName)
        assertTrue(report.hasFaults)
    }

    @Test
    fun faultCodesUseFourDigitHexAndDoNotFakeFaults() {
        assertTrue(VehicleDiagnosticsFormatter.faultCodes(null).isEmpty())
        val info = ttpInfoWithFaults()
        assertEquals(listOf("00AF", "0102"), VehicleDiagnosticsFormatter.faultCodes(info))
    }

    @Test
    fun radarRowsComeFromTtpBitFields() {
        val rows = VehicleDiagnosticsFormatter.radarRows(ttpInfoWithFaults())
        assertTrue(rows.any { it.first == "FCW 工作状态" && it.second == "工作中" })
        assertTrue(rows.any { it.first == "BSD 雷达" && it.second == "开启" })
    }

    @Test
    fun cloudStatusParsesTireAndSafetyFields() = runBlocking {
        val plain = """
            {"success":true,"data":{
              "leftFrontPressure":2.3,
              "leftRearPressure":2.4,
              "leftFrontTireTemp":31,
              "leftRearTireTemp":32,
              "leftFrontPressureWarning":1,
              "leftRearPressureWarning":2,
              "lowBatTelWarning":1,
              "lowBatWarningStartTime":"07:00",
              "lowBatWarningEndTime":"21:00",
              "positioningFunc":1,
              "lossState":1
            }}
        """.trimIndent()
        val gateway = YadeaVehicleCloudStatusGateway(
            transport = object : HttpTransport {
                override suspend fun execute(request: HttpRequest): HttpResponse? {
                    assertTrue(request.url.contains(VehicleCloudStatusGatewayTestPath))
                    return HttpResponse(200, encrypted(plain))
                }
            },
            tokenProvider = { "token" },
            timestampProvider = { 1L },
        )

        val status = gateway.load("VIN")
        assertNotNull(status)
        assertEquals("2.3", status!!.leftFrontPressure)
        assertEquals(1, status.leftFrontPressureWarning)
        assertEquals(32, status.leftRearTireTemp)
        assertEquals(1, status.positioningFunc)
        val rows = VehicleDiagnosticsFormatter.tireRows(status)
        assertTrue(rows.any { it.first == "前轮胎压" && it.second.contains("过高") })
        assertTrue(rows.any { it.first == "后轮胎压" && !it.second.contains("告警") })
        assertFalse(VehicleDiagnosticsFormatter.locationRows(null, status).isEmpty())
    }

    private fun ttpInfoWithFaults(): TtpInfo = TtpInfoParser.parse(faultTestFrame())

    private fun faultTestFrame(): ByteArray {
        val frame = ByteArray(0x110)
        frame[0x59] = 0x02
        frame[0x5A] = 0xF2.toByte()
        frame[0x5B] = 0x00
        frame[0x5C] = 0xAF.toByte()
        frame[0x5D] = 0x01
        frame[0x5E] = 0x02
        frame[0x7F] = 0x10
        frame[0x80] = 0x07
        frame[0x81] = 0x01
        return frame
    }
}

private const val VehicleCloudStatusGatewayTestPath = "bikeRealStatus/getVehRealStatus"
