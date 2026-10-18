package top.ghhccghk.yadea.auth

import java.net.URLEncoder
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GatewaySignerTest {

    private fun javaSign(normalizedJson: String, timestampMs: Long): String {
        val plaintext = "gatewayTimestamp=$timestampMs&json=$normalizedJson"
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(GatewaySigner.AES_KEY.encodeToByteArray(), "AES"),
        )
        val encrypted = cipher.doFinal(plaintext.encodeToByteArray())
        return URLEncoder.encode(Base64.getEncoder().encodeToString(encrypted), "UTF-8")
    }

    @Test
    fun sign_matchesFixedVector() {
        val (sign, timestamp) = GatewaySigner.sign("""{"a":1,"b":"x"}""", 1_700_000_000_000L)

        assertEquals("1700000000000", timestamp)
        assertEquals(
            "qmdb2ir3RrsMTaaxCXxvsYQzP6y8w98YEWUdS6Vw0Sf%2Fgt32m1aGJwFlOlMXFFoT" +
                "%2F3FMDypp2KQUJyoFW8htuA%3D%3D",
            sign,
        )
    }

    @Test
    fun sign_matchesJdkOracle() {
        val json = """{"account":"13800138000","password":"Abc12345","nested":{"a":1}}"""
        val normalized = GatewaySigner.normalize(json)
        assertEquals(json, normalized)

        val (sign, timestamp) = GatewaySigner.sign(json, 1_700_000_123_456L)
        assertEquals("1700000123456", timestamp)
        assertEquals(javaSign(normalized, 1_700_000_123_456L), sign)
    }

    @Test
    fun normalize_unwrapsObjectString() {
        val input = """{"nested":"{\"a\":1}"}"""
        assertEquals("""{"nested":{"a":1}}""", GatewaySigner.normalize(input))
    }

    @Test
    fun normalize_unwrapsArrayString() {
        val input = """{"list":"[1,2,3]"}"""
        assertEquals("""{"list":[1,2,3]}""", GatewaySigner.normalize(input))
    }

    @Test
    fun normalize_keepsOrdinaryJson() {
        val input = """{"account":"13800138000","password":"Abc12345"}"""
        assertEquals(input, GatewaySigner.normalize(input))
        assertFalse(GatewaySigner.normalize(input).contains("\"[") || GatewaySigner.normalize(input).contains("\"{"))
    }

    @Test
    fun formBody_containsOnlySignatureFields() {
        val body = GatewaySigner.formBody("""{"a":1}""", 42L)
        val parts = body.split("&")
        assertEquals(2, parts.size)
        assertEquals("gatewaySign", parts[0].substringBefore("="))
        assertEquals("gatewayTimestamp=42", parts[1])
    }
}
