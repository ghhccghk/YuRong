package top.ghhccghk.yadea.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ghhccghk.yadea.network.HttpRequest
import top.ghhccghk.yadea.network.HttpResponse
import top.ghhccghk.yadea.network.HttpTransport
import top.ghhccghk.yadea.network.GatewayHeaders

class YadeaAuthGatewayTest {
    private fun encrypted(body: String): String =
        Crypto.urlEncode(Crypto.aesEcbPkcs5Encrypt(GatewaySigner.AES_KEY, body))

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
                assertTrue(request.body!!.contains("gatewaySign="))
                assertTrue(request.body.contains("gatewayTimestamp="))
                return HttpResponse(200, encrypted(plain))
            }
        }
        val gateway = YadeaAuthGateway(
            transport = transport,
            timestampProvider = { 1_700_000_000_000L },
            deviceIdProvider = { "device-1" },
            registrationIdProvider = { "push-1" },
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
        val gateway = YadeaAuthGateway(
            transport = object : HttpTransport {
                override suspend fun execute(request: HttpRequest) =
                    HttpResponse(200, encrypted(plain))
            },
            timestampProvider = { 1L },
        )
        val result = gateway.loginBySms("13800138000", "1234")
        assertTrue(result is AuthResult.Failure)
        assertEquals("密码错误", (result as AuthResult.Failure).message)
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
}
