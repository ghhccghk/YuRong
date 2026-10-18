package top.ghhccghk.yadea.auth

/**
 * 网关请求签名。
 *
 * 原实现（`tb0.smali`）的步骤：
 * 1. 业务 JSON 做字符串规范化；
 * 2. 拼接 `gatewayTimestamp=<ms>&json=<json>`；
 * 3. AES-128/ECB/PKCS5 加密；
 * 4. Base64 后做 Java `URLEncoder` 编码。
 *
 * `_gateway=True` 时最终请求体只保留 `gatewaySign` 与 `gatewayTimestamp`，
 * 业务字段已经全部进入签名明文。
 */
object GatewaySigner {

    /** 原客户端内置的 16 字符 AES 密钥。 */
    const val AES_KEY = "cIDJ9f05d1604703"

    /**
     * @param businessJson 业务 JSON；字段顺序应与原实现的 `TreeMap` 字典序一致。
     * @return `(gatewaySign, gatewayTimestamp)`
     */
    fun sign(businessJson: String, timestampMs: Long): Pair<String, String> {
        val normalized = normalize(businessJson)
        val plaintext = "gatewayTimestamp=$timestampMs&json=$normalized"
        val encrypted = Crypto.aesEcbPkcs5Encrypt(AES_KEY, plaintext)
        return Crypto.urlEncode(encrypted) to timestampMs.toString()
    }

    /** 生成可直接作为 form body 的签名串。两个字段均已完成 URL 编码。 */
    fun formBody(businessJson: String, timestampMs: Long): String {
        val (sign, timestamp) = sign(businessJson, timestampMs)
        return "gatewaySign=$sign&gatewayTimestamp=$timestamp"
    }

    /**
     * 复刻原实现的 JSON 字符串规范化。
     *
     * 这段逻辑看起来像"修 JSON"，实际是服务端签名协议的一部分：
     * 嵌套对象/数组在 Gson 序列化后是字符串，原实现会在签名前去掉外层引号。
     *
     * `essay` 分支是商城接口的特殊分段重组；登录接口不涉及，但这里仍按
     * `tb0.smali` 的索引顺序保守复刻。
     */
    internal fun normalize(json: String): String {
        var s = json

        // 5.1：反转义 \" → "
        if (s.contains("\\\"")) {
            s = s.replace("\\\"", "\"")
        }

        // 5.2：essay 特殊分支
        if (s.contains("essay")) {
            s = normalizeEssay(s)
        }

        // 5.3：嵌套对象去引号（仅在非 essay 分支）
        if (!s.contains("essay") && s.contains("{\"")) {
            s = s.replace("\"{", "{").replace("}\"", "}")
        }

        // 5.4：嵌套数组去引号
        if (s.contains("\"[")) {
            s = s.replace("\"[", "[").replace("]\"", "]")
        }
        return s
    }

    /**
     * `essay` 分支：把 essay 字段前后的数组字符串分别去引号，再拼回原段。
     *
     * 原实现用 `indexOf("\"essay\":")`、局部 `indexOf(",\"")` 和
     * `indexOf("\"}")` 计算切点；任一索引异常时回退为只处理数组引号。
     */
    private fun normalizeEssay(json: String): String {
        return try {
            val essayStart = json.indexOf("\"essay\":")
            if (essayStart < 0) return json
            val rest = json.substring(essayStart)
            val commaIndex = rest.indexOf(",\"")
            var essayEnd = json.indexOf("\"}")
            if (commaIndex > 0) {
                essayEnd = essayStart + 8 + commaIndex
            }
            if (essayEnd < essayStart || essayEnd > json.length) return json
            val essaySegment = json.substring(essayStart, essayEnd)
            if (!json.contains("\"[")) return json
            val before = json.substring(0, essayStart).stripArrayQuotes()
            val after = json.substring(essayEnd).stripArrayQuotes()
            before + essaySegment + after
        } catch (_: Exception) {
            if (json.contains("\"[")) json.stripArrayQuotes() else json
        }
    }

    private fun String.stripArrayQuotes(): String =
        replace("\"[", "[").replace("]\"", "]")
}
