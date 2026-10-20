package top.ghhccghk.yadea.settings

/**
 * 敏感配置的安全存储抽象。
 *
 * 普通设置继续走 [PreferenceStore]；只有登录 Token 等不能明文落盘的字段走这里。
 * Android 使用 Keystore 加密，其他平台未接入安全后端时可传 `null`，由 [AppPreferences]
 * 保持原有行为，避免为了单个平台破坏跨平台编译。
 */
interface SecretStore {
    /** 读取并解密；不存在或密文损坏时返回 null。 */
    fun read(name: String): String?

    /** 加密并持久化；失败时抛出异常，由调用方转换为 [PreferenceWriteResult]。 */
    fun write(name: String, value: String)

    /** 删除安全存储中的记录；不存在时视为成功。 */
    fun delete(name: String)
}

object AppSecretKeys {
    const val userToken = "usr_token"
}
