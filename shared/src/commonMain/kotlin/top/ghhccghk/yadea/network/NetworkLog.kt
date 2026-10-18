package top.ghhccghk.yadea.network

import top.ghhccghk.yadea.ui.logToFile

/**
 * 网关诊断日志。
 *
 * Android 写入应用外部目录下的 `yadea-logs/mirror-log.txt`，同时输出到
 * logcat（`System.out`）；其他平台输出到控制台。日志不包含明文密码，
 * token / authKey 会在写入前脱敏。
 */
internal object NetworkLog {
    fun write(message: String) {
        val line = "NET $message"
        println(line)
        runCatching { logToFile(line) }
    }

    fun headers(headers: Map<String, String>): String =
        headers.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            val shown = if (key.equals("Authorization", ignoreCase = true)) {
                maskAuthorization(value)
            } else {
                value
            }
            "$key=$shown"
        }

    fun body(value: String?, limit: Int = 1_200): String =
        value?.take(limit)?.replace("\n", "\\n") ?: "<null>"

    fun formSummary(body: String?): String {
        if (body.isNullOrBlank()) return "<empty>"
        val names = body.split('&').mapNotNull { part ->
            part.substringBefore('=', "").takeIf { it.isNotBlank() }
        }
        return "fields=$names length=${body.length}"
    }

    fun maskSensitive(value: String): String = value
        .replace(Regex("\"token\"\\s*:\\s*\"[^\"]*\""), "\"token\":\"***\"")
        .replace(Regex("\"authKey\"\\s*:\\s*\"[^\"]*\""), "\"authKey\":\"***\"")
        .replace(Regex("(Bearer\\s+)[^\\s\"]+"), "$1***")

    private fun maskAuthorization(value: String): String =
        value.replace(Regex("^(Bearer\\s+).+$", RegexOption.IGNORE_CASE), "$1***")
}
