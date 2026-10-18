package top.ghhccghk.yadea.network

/**
 * Smali `CommonHeadersInterceptor` 注入的网关应用标识。
 *
 * 缺少该 header 时服务端会返回“appid 参数错误”。
 */
object GatewayHeaders {
    const val KEY_GATEWAY_APP_ID = "gatewayAppId"
    const val GATEWAY_APP_ID = "0e7b1d094c7b4073b7c958edbbbc1115"

    /**
     * 所有亚迪网关请求的最终兜底。
     *
     * 即使某个新接口忘记显式添加 app id，只要请求发往 `yadeaiot.com.cn`，
     * 传输层也会补上正确值，避免服务端在进入业务接口前返回“appid参数错误”。
     */
    fun ensureGatewayAppId(url: String, headers: Map<String, String>): Map<String, String> {
        if (!url.contains("yadeaiot.com.cn", ignoreCase = true)) return headers
        val existing = headers.entries.firstOrNull {
            it.key.equals(KEY_GATEWAY_APP_ID, ignoreCase = true)
        }
        if (existing != null && existing.value.isNotBlank()) return headers
        return headers + (KEY_GATEWAY_APP_ID to GATEWAY_APP_ID)
    }
}
