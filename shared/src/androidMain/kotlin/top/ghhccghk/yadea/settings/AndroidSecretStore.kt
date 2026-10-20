package top.ghhccghk.yadea.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android 登录 Token 安全存储。
 *
 * 使用 Android Keystore 中的不可导出 AES 密钥做 AES/GCM 加解密，密文写入独立 key。
 * 首次读取时如果发现旧版本留下的明文 `usr_token`，会先加密迁移再删除明文；
 * 迁移失败时宁可让用户重新登录，也不继续保留明文 Token。
 */
class AndroidSecretStore(
    context: Context,
    preferenceName: String = "touping_settings",
) : SecretStore {
    private val preferences = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)

    override fun read(name: String): String? = synchronized(lock) {
        val secureKey = secureStorageName(name)
        val encoded = preferences.getString(secureKey, null)
        if (!encoded.isNullOrBlank()) {
            return@synchronized runCatching { decrypt(encoded) }.getOrElse {
                // 密钥失效或密文损坏时清理该条目，避免每次启动重复失败。
                preferences.edit().remove(secureKey).commit()
                null
            }
        }

        val legacyKey = legacyStorageName(name)
        val legacy = preferences.getString(legacyKey, null)
        if (legacy.isNullOrBlank()) return@synchronized null
        return@synchronized runCatching {
            writeLocked(name, legacy)
            preferences.edit().remove(legacyKey).commit()
            legacy
        }.getOrElse {
            // 无法加密时删除明文，避免“迁移失败但仍继续明文保存”。
            preferences.edit().remove(legacyKey).commit()
            null
        }
    }

    override fun write(name: String, value: String) {
        synchronized(lock) {
            writeLocked(name, value)
            // 安全写入成功后清理同名字段的旧明文。
            preferences.edit().remove(legacyStorageName(name)).commit()
        }
    }

    override fun delete(name: String) = synchronized(lock) {
        preferences.edit()
            .remove(secureStorageName(name))
            .remove(legacyStorageName(name))
            .commit()
        Unit
    }

    private fun writeLocked(name: String, value: String) {
        val encoded = encrypt(value)
        check(preferences.edit().putString(secureStorageName(name), encoded).commit()) {
            "无法写入安全存储"
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(1 + iv.size + encrypted.size)
        payload[0] = iv.size.toByte()
        iv.copyInto(payload, destinationOffset = 1)
        encrypted.copyInto(payload, destinationOffset = 1 + iv.size)
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.isNotEmpty()) { "密文为空" }
        val ivSize = payload[0].toInt() and 0xFF
        require(ivSize in 1..payload.size - 2) { "密文 IV 长度非法" }
        val iv = payload.copyOfRange(1, 1 + ivSize)
        val encrypted = payload.copyOfRange(1 + ivSize, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun secureStorageName(name: String): String = "$PREFERENCE_PREFIX.secure.$name"

    private fun legacyStorageName(name: String): String = "$PREFERENCE_PREFIX.$name"

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "top.ghhccghk.yadea.auth_token.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFERENCE_PREFIX = "top.ghhccghk.yadea"
        val lock = Any()
    }
}
