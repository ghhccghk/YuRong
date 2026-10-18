package top.ghhccghk.yadea.auth

import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CryptoTest {

    private val key = "cIDJ9f05d1604703"

    private fun hex(text: String): ByteArray =
        ByteArray(text.length / 2) { i ->
            text.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private fun javaEncrypt(plaintext: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.encodeToByteArray(), "AES"))
        val encrypted = cipher.doFinal(plaintext.encodeToByteArray())
        return Base64.getEncoder().encodeToString(encrypted)
    }

    @Test
    fun md5_knownVectors() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", Crypto.md5Hex(""))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", Crypto.md5Hex("abc"))
        assertEquals(
            "c3fcd3d76192e4007dfb496cca67e13b",
            Crypto.md5Hex("abcdefghijklmnopqrstuvwxyz"),
        )
    }

    @Test
    fun md5_matchesJdkForBinaryInput() {
        val data = ByteArray(256) { it.toByte() }
        assertArrayEquals(MessageDigest.getInstance("MD5").digest(data), Crypto.md5(data))
    }

    @Test
    fun aes_nistBlockVector() {
        val keyBytes = hex("000102030405060708090a0b0c0d0e0f")
        val plain = hex("00112233445566778899aabbccddeeff")
        val expected = hex("69c4e0d86a7b0430d8cdb78070b4c55a")

        val encrypted = Crypto.aesEncryptBlock(keyBytes, plain)
        assertArrayEquals(expected, encrypted)
        assertArrayEquals(plain, Crypto.aesDecryptBlock(keyBytes, encrypted))
    }

    @Test
    fun aes_ecbPkcs5_matchesJdk() {
        val cases = listOf(
            "",
            "a",
            "0123456789abcde",
            "0123456789abcdef",
            "0123456789abcdefg",
            "网关签名 gatewayTimestamp=1700000000000",
            "x".repeat(200),
        )
        for (plain in cases) {
            assertEquals("case=$plain", javaEncrypt(plain), Crypto.aesEcbPkcs5Encrypt(key, plain))
        }
    }

    @Test
    fun aes_ecbPkcs5_roundTripsUtf8() {
        val plain = "车辆状态：电量 87%，温度 25.5°C"
        val encrypted = Crypto.aesEcbPkcs5Encrypt(key, plain)
        assertEquals(plain, Crypto.aesEcbPkcs5Decrypt(key, encrypted))
    }

    @Test
    fun aes_rejectsBadKeyAndCiphertext() {
        val badKey = runCatching { Crypto.aesEcbPkcs5Encrypt("short", "x") }
        assertEquals(IllegalArgumentException::class.java, badKey.exceptionOrNull()?.javaClass)
        assertEquals(null, Crypto.aesEcbPkcs5Decrypt(key, "not-base64"))
        assertEquals(null, Crypto.aesEcbPkcs5Decrypt(key, Base64.getEncoder().encodeToString(ByteArray(15))))
    }

    @Test
    fun base64_matchesJdk() {
        val cases = listOf(
            ByteArray(0),
            byteArrayOf(0),
            byteArrayOf(1, 2),
            byteArrayOf(1, 2, 3),
            ByteArray(100) { it.toByte() },
        )
        for (bytes in cases) {
            val expected = Base64.getEncoder().encodeToString(bytes)
            assertEquals(expected, Crypto.base64Encode(bytes))
            assertArrayEquals(bytes, Crypto.base64Decode(expected))
        }
    }

    @Test
    fun urlEncode_matchesJavaFormEncoder() {
        val values = listOf(
            "abcXYZ019-_.*",
            "a b+c/d=",
            "中文 参数",
            "qmdb2ir3RrsMTaaxCXxvsYQzP6y8w98YEWUdS6Vw0Sf/gt32m1aGJwFlOlMXFFoT/3FMDypp2KQUJyoFW8htuA==",
        )
        for (value in values) {
            assertEquals(URLEncoder.encode(value, "UTF-8"), Crypto.urlEncode(value))
        }
    }

    @Test
    fun urlDecode_roundTrips() {
        val value = "中文 + / = % 值"
        assertEquals(value, Crypto.urlDecode(Crypto.urlEncode(value)))
    }
}
