package top.ghhccghk.yadea.network

import kotlin.random.Random

/**
 * 原客户端公共请求头参数。
 *
 * 取值来源：
 * - `app-version` / `app-version-num` / `app-market`：`PackageUtils`；
 * - `device-brand` / `phone-model`：`Build.BRAND` / `Build.MODEL`；
 * - `bike-series-code`：原实现无车辆系列时回退 `CarType.SERIES_4.key`，即 `"4"`；
 * - `app-version-type`：原生产构建的 `BuildConfig` 开关为 false，因此是 `"1"`；
 * - 其余固定值来自 `CommonHeadInterceptor`。
 */
data class GatewayClientInfo(
    val appVersion: String = "",
    val appVersionNum: String = "",
    val appMarket: String = "Yadea",
    val appVersionType: String = "1",
    val deviceBrand: String = "",
    val phoneModel: String = "",
    val bikeSeriesCode: String = "4",
    val appName: String = "YADEA",
    val language: String = "zh_CN",
    val pushOperator: String = "2",
    val os: String = "android",
)

/** 生成与原客户端一致的公共请求头。 */
object GatewayRequestHeaders {
    fun build(
        clientInfo: GatewayClientInfo,
        deviceId: String,
        token: String = "",
        requestId: String = newRequestId(),
    ): Map<String, String> = buildMap {
        put(GatewayHeaders.KEY_GATEWAY_APP_ID, GatewayHeaders.GATEWAY_APP_ID)
        val normalizedToken = token.trim()
        if (normalizedToken.isNotBlank()) {
            put(
                "Authorization",
                if (normalizedToken.startsWith("Bearer ")) {
                    normalizedToken
                } else {
                    "Bearer $normalizedToken"
                },
            )
        }
        put("req-id", requestId)
        put("request-agent", "2")
        put("deviceId", deviceId)
        put("os", clientInfo.os)
        put("app-version", clientInfo.appVersion)
        put("bike-series-code", clientInfo.bikeSeriesCode)
        put("app-name", clientInfo.appName)
        put("app-version-num", clientInfo.appVersionNum)
        put("app-market", clientInfo.appMarket)
        put("app-version-type", clientInfo.appVersionType)
        put("device-brand", clientInfo.deviceBrand)
        put("phone-model", clientInfo.phoneModel)
        put("language", clientInfo.language)
        put("push-operator", clientInfo.pushOperator)
    }

    /** 原实现使用 `UUID.randomUUID()`；这里用同样的 8-4-4-4-12 形式。 */
    fun newRequestId(): String {
        val bytes = ByteArray(16).also { Random.nextBytes(it) }
        val hex = bytes.joinToString("") {
            (it.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-" +
            "${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}
