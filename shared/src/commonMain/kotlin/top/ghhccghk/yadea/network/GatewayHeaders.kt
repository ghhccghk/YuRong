package top.ghhccghk.yadea.network

/**
 * Smali `CommonHeadersInterceptor` 注入的网关应用标识。
 *
 * 缺少该 header 时服务端会返回“appid 参数错误”。
 */
object GatewayHeaders {
    const val KEY_GATEWAY_APP_ID = "gatewayAppId"
    const val GATEWAY_APP_ID = "0e7b1d094c7b4073b7c958edbbbc1115"
}
