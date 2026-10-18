package top.ghhccghk.yadea.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ghhccghk.yadea.network.GatewayClientInfo
import top.ghhccghk.yadea.network.HttpRequest
import top.ghhccghk.yadea.network.HttpResponse
import top.ghhccghk.yadea.network.HttpTransport
import top.ghhccghk.yadea.network.GatewayHeaders

class YadeaAuthGatewayTest {
    private fun encrypted(body: String): String =
        Crypto.urlEncode(Crypto.aesEcbPkcs5Encrypt(GatewaySigner.AES_KEY, body))

    private fun signedJson(body: String): String {
        val sign = body.split('&')
            .first { it.startsWith("gatewaySign=") }
            .substringAfter('=')
        val plain = Crypto.aesEcbPkcs5Decrypt(GatewaySigner.AES_KEY, Crypto.urlDecode(sign))!!
        return plain.substringAfter("&json=")
    }

    @Test
    fun passwordLogin_postsSignedFormAndParsesUser() = runBlocking {
        val plain = """
            {"success":true,"code":0,"msg":"ok","data":{
              "id":7,"token":"token-7","tokenExpire":1700000000000,
              "userId":"u7","mobile":"13800138000","nickName":"车主"
            }}
        """.trimIndent()
        val transport = object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                assertEquals("POST", request.method)
                assertTrue(request.url.endsWith("user/loginByPassword"))
                assertEquals(
                    GatewayHeaders.GATEWAY_APP_ID,
                    request.headers[GatewayHeaders.KEY_GATEWAY_APP_ID],
                )
                assertEquals("8.2.0", request.headers["app-version"])
                assertEquals("80200", request.headers["app-version-num"])
                assertEquals("Yadea", request.headers["app-market"])
                assertEquals("1", request.headers["app-version-type"])
                assertEquals("4", request.headers["bike-series-code"])
                assertEquals("XIAOMI", request.headers["device-brand"])
                assertEquals("2211133C", request.headers["phone-model"])
                assertTrue(request.headers["req-id"]!!.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
                assertTrue(request.body!!.contains("gatewaySign="))
                assertTrue(request.body.contains("gatewayTimestamp="))
                val payload = signedJson(request.body)
                assertTrue(payload.contains("\"accountType\":\"1\""))
                assertTrue(payload.contains("\"appType\":\"1\""))
                assertTrue(payload.contains("\"oppoPushId\":\"oppo-1\""))
                assertTrue(payload.contains("\"registrationId\":\"push-1\""))
                return HttpResponse(200, encrypted(plain))
            }
        }
        val gateway = YadeaAuthGateway(
            transport = transport,
            timestampProvider = { 1_700_000_000_000L },
            deviceIdProvider = { "device-1" },
            registrationIdProvider = { "push-1" },
            oppoPushIdProvider = { "oppo-1" },
            clientInfoProvider = {
                GatewayClientInfo(
                    appVersion = "8.2.0",
                    appVersionNum = "80200",
                    appMarket = "Yadea",
                    deviceBrand = "XIAOMI",
                    phoneModel = "2211133C",
                    bikeSeriesCode = "4",
                )
            },
        )

        val result = gateway.loginByPassword(
            PasswordLoginRequest("13800138000", "Abc12345", AuthAccountType.Mobile),
        )
        assertTrue(result is AuthResult.Success)
        val user = (result as AuthResult.Success).data
        assertEquals("token-7", user.token)
        assertEquals("车主", user.nickName)
    }

    @Test
    fun failedLogin_returnsServerMessage() = runBlocking {
        val plain = """{"success":false,"code":1001,"msg":"密码错误"}"""
        var requestBody = ""
        val gateway = YadeaAuthGateway(
            transport = object : HttpTransport {
                override suspend fun execute(request: HttpRequest): HttpResponse {
                    requestBody = request.body.orEmpty()
                    return HttpResponse(200, encrypted(plain))
                }
            },
            timestampProvider = { 1L },
        )
        val result = gateway.loginBySms("13800138000", "1234")
        assertTrue(result is AuthResult.Failure)
        assertEquals("密码错误", (result as AuthResult.Failure).message)
        val payload = signedJson(requestBody)
        assertFalse(payload.contains("\"password\""))
        assertTrue(payload.contains("\"loginAppType\":\"1\""))
    }

    @Test
    fun smsCode_successMapsToUnit() = runBlocking {
        val gateway = YadeaAuthGateway(
            transport = object : HttpTransport {
                override suspend fun execute(request: HttpRequest): HttpResponse {
                    assertTrue(request.url.endsWith("user/sendCode"))
                    return HttpResponse(200, encrypted("""{"success":true,"data":null}"""))
                }
            },
            timestampProvider = { 1L },
        )
        assertTrue(gateway.sendSmsCode("13800138000") is AuthResult.Success)
    }

    @Test
    fun tokenLogin_postsOneTapFieldsAndParsesUser() = runBlocking {
        val plain = """
            {"success":true,"data":{
              "id":9,"token":"bearer-9","userId":"u9","nickName":"Token 车主"
            }}
        """.trimIndent()
        var requestBody = ""
        val gateway = YadeaAuthGateway(
            transport = object : HttpTransport {
                override suspend fun execute(request: HttpRequest): HttpResponse {
                    assertTrue(request.url.endsWith("user/loginByToken"))
                    requestBody = request.body.orEmpty()
                    return HttpResponse(200, encrypted(plain))
                }
            },
            timestampProvider = { 1L },
            deviceIdProvider = { "device-1" },
            registrationIdProvider = { "push-1" },
            oppoPushIdProvider = { "oppo-1" },
        )

        val result = gateway.loginByToken(TokenLoginRequest("login-token-1", "gyuid-1"))
        assertTrue(result is AuthResult.Success)
        assertEquals("bearer-9", (result as AuthResult.Success).data.token)

        val payload = signedJson(requestBody)
        assertTrue(payload.contains("\"loginToken\":\"login-token-1\""))
        assertTrue(payload.contains("\"gyuid\":\"gyuid-1\""))
        assertTrue(payload.contains("\"appName\":\"YADEA\""))
        assertTrue(payload.contains("\"verificationType\":\"1\""))
        assertTrue(payload.contains("\"registrationId\":\"push-1\""))
        assertTrue(payload.contains("\"oppoPushId\":\"oppo-1\""))
    }

    @Test
    fun advancedLogin_postsProvidedJsonAndHeaderOverrides() = runBlocking {
        val plain = """
            {"success":true,"data":{"id":10,"token":"bearer-10","nickName":"参数车主"}}
        """.trimIndent()
        var requestBody = ""
        val gateway = YadeaAuthGateway(
            transport = object : HttpTransport {
                override suspend fun execute(request: HttpRequest): HttpResponse {
                    assertTrue(request.url.endsWith("user/loginByPassword"))
                    assertEquals("CUSTOM_BRAND", request.headers["device-brand"])
                    requestBody = request.body.orEmpty()
                    return HttpResponse(200, encrypted(plain))
                }
            },
            timestampProvider = { 1L },
        )

        val result = gateway.loginWithParameters(
            RawLoginRequest(
                path = "user/loginByPassword",
                businessJson = """
                    {"account":"13800138000","password":"Abc12345","accountType":"1","appType":"1"}
                """.trimIndent(),
                headers = mapOf("device-brand" to "CUSTOM_BRAND"),
            ),
        )
        assertTrue(result is AuthResult.Success)
        val payload = signedJson(requestBody)
        assertTrue(payload.contains("\"_gateway\":\"True\""))
        assertTrue(payload.contains("\"account\":\"13800138000\""))
        assertTrue(payload.contains("\"accountType\":\"1\""))
    }
}
