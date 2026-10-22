package top.ghhccghk.yadea.vehicle

import top.ghhccghk.yadea.auth.Crypto
import top.ghhccghk.yadea.auth.GatewaySigner
import top.ghhccghk.yadea.network.GatewayClientInfo
import top.ghhccghk.yadea.network.GatewayRequestHeaders
import top.ghhccghk.yadea.network.HttpRequest
import top.ghhccghk.yadea.network.HttpTransport
import top.ghhccghk.yadea.network.JsonSupport
import top.ghhccghk.yadea.network.NetworkLog

/**
 * Token 握手的账号角色参数，对应原 App 持久化的 `key_account_list` 与
 * `key_user_no`。
 */
data class VehicleAccountRoles(
    val accountList: IntArray,
    val userNo: Int,
)

interface VehicleAccountRoleProvider {
    suspend fun fetch(bikeId: Long, currentUserId: Long): VehicleAccountRoles?
}

/**
 * `userBike/queryUserListByBikeId` 客户端。
 *
 * 原 `CarConfigRequest` 将每个账号的 `accountOrder` 反向映射到六位权限数组，
 * 并把当前登录用户自己的 `accountOrder` 保存为 `key_user_no`。
 */
class YadeaVehicleAccountRoleGateway(
    private val transport: HttpTransport,
    private val tokenProvider: () -> String,
    private val deviceIdProvider: () -> String = { "" },
    private val clientInfoProvider: () -> GatewayClientInfo = { GatewayClientInfo() },
    private val timestampProvider: () -> Long,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : VehicleAccountRoleProvider {

    private var cachedBikeId = 0L
    private var cachedUserId = 0L
    private var cachedRoles: VehicleAccountRoles? = null

    override suspend fun fetch(bikeId: Long, currentUserId: Long): VehicleAccountRoles? {
        if (bikeId <= 0L || currentUserId <= 0L) return null
        if (
            cachedRoles != null &&
            cachedBikeId == bikeId &&
            cachedUserId == currentUserId
        ) {
            return cachedRoles
        }
        val token = tokenProvider().trim()
        if (token.isBlank()) return null

        val signedQuery = GatewaySigner.formBody(
            """{"_gateway":"True","bikeId":"$bikeId"}""",
            timestampProvider(),
        )
        val url = baseUrl + PATH + "?" + signedQuery
        val headers = GatewayRequestHeaders.build(
            clientInfo = clientInfoProvider(),
            deviceId = deviceIdProvider(),
            token = token,
        )
        NetworkLog.write("ACCOUNT_ROLE -> GET $url headers=${NetworkLog.headers(headers)}")
        val response = transport.execute(
            HttpRequest(
                method = "GET",
                url = url,
                headers = headers,
            ),
        ) ?: run {
            NetworkLog.write("ACCOUNT_ROLE !! no response")
            return null
        }
        NetworkLog.write(
            "ACCOUNT_ROLE <- ${response.status} raw=${NetworkLog.body(response.body)}",
        )
        if (response.status !in 200..299) return null

        val decoded = Crypto.urlDecode(response.body.trim())
        val plain = Crypto.aesEcbPkcs5Decrypt(GatewaySigner.AES_KEY, decoded) ?: decoded
        NetworkLog.write("ACCOUNT_ROLE plain ${NetworkLog.maskSensitive(plain)}")
        if (JsonSupport.boolean(plain, "success") == false) return null

        val users = JsonSupport.arrayObjects(plain, "data")
            .ifEmpty { listOfNotNull(JsonSupport.firstArrayObject(plain, "data")) }
        if (users.isEmpty()) return null

        val accountList = IntArray(6)
        var currentUserNo = -1
        for (objectText in users) {
            val accountOrder = JsonSupport.int(objectText, "accountOrder") ?: continue
            if (accountOrder !in 0..5) continue
            if (JsonSupport.long(objectText, "id") == currentUserId) {
                currentUserNo = accountOrder
            }
            // accountOrder 0..5 对应请求数组的 5..0。
            accountList[5 - accountOrder] = 1
        }
        if (currentUserNo < 0) return null
        return VehicleAccountRoles(accountList = accountList, userNo = currentUserNo).also {
            cachedBikeId = bikeId
            cachedUserId = currentUserId
            cachedRoles = it
        }
    }

    companion object {
        const val PATH = "userBike/queryUserListByBikeId"
        const val DEFAULT_BASE_URL = "https://gw.yadeaiot.com.cn/api/app/"
    }
}
