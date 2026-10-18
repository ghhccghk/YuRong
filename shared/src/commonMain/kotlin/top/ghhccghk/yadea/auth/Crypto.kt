package top.ghhccghk.yadea.auth

/**
 * 登录网关用到的纯 Kotlin 密码学原语。
 *
 * 刻意不使用 `expect/actual`：AES 与 MD5 都是确定性字节算法，放在
 * `commonMain` 后 JVM / Android / JS / Wasm / iOS 共享同一份实现，
 * 也便于用固定向量做 100% 单测。
 *
 * **安全提示**：网关密钥是原客户端内置的对称密钥，仅用于兼容既有协议。
 */
object Crypto {

    private const val BLOCK_SIZE = 16

    // ==================== 对外 API ====================

    /** AES-128/ECB/PKCS5Padding，返回标准 Base64（无换行）。 */
    fun aesEcbPkcs5Encrypt(key: String, plaintext: String): String =
        base64Encode(aesEcbPkcs5Encrypt(key.encodeToByteArray(), plaintext.encodeToByteArray()))

    /** AES-128/ECB/PKCS5Padding 解密；Base64 或密文非法时返回 `null`。 */
    fun aesEcbPkcs5Decrypt(key: String, base64: String): String? {
        val cipher = base64Decode(base64) ?: return null
        val plain = aesEcbPkcs5Decrypt(key.encodeToByteArray(), cipher) ?: return null
        return plain.decodeToString()
    }

    /** AES 单块加密（无填充），用于 NIST 向量对拍。 */
    fun aesEncryptBlock(key: ByteArray, block: ByteArray): ByteArray {
        requireAesKey(key)
        require(block.size == BLOCK_SIZE) { "AES block must be 16 bytes" }
        val schedule = expandKey(key)
        val state = IntArray(BLOCK_SIZE) { block[it].toInt() and 0xFF }
        encryptBlock(state, schedule)
        return ByteArray(BLOCK_SIZE) { state[it].toByte() }
    }

    /** AES 单块解密（无填充）。 */
    fun aesDecryptBlock(key: ByteArray, block: ByteArray): ByteArray {
        requireAesKey(key)
        require(block.size == BLOCK_SIZE) { "AES block must be 16 bytes" }
        val schedule = expandKey(key)
        val state = IntArray(BLOCK_SIZE) { block[it].toInt() and 0xFF }
        decryptBlock(state, schedule)
        return ByteArray(BLOCK_SIZE) { state[it].toByte() }
    }

    /** MD5 原始 16 字节摘要。 */
    fun md5(message: ByteArray): ByteArray {
        val bitLength = message.size.toLong() * 8L
        val padLength = ((56 - (message.size + 1) % 64) + 64) % 64
        val padded = ByteArray(message.size + 1 + padLength + 8)
        message.copyInto(padded)
        padded[message.size] = 0x80.toByte()
        for (i in 0 until 8) {
            padded[padded.size - 8 + i] = ((bitLength ushr (8 * i)) and 0xFF).toByte()
        }

        var a0 = 0x67452301
        var b0 = 0xEFCDAB89.toInt()
        var c0 = 0x98BADCFE.toInt()
        var d0 = 0x10325476

        val words = IntArray(16)
        var offset = 0
        while (offset < padded.size) {
            for (i in 0 until 16) {
                val p = offset + i * 4
                words[i] = (padded[p].toInt() and 0xFF) or
                    ((padded[p + 1].toInt() and 0xFF) shl 8) or
                    ((padded[p + 2].toInt() and 0xFF) shl 16) or
                    ((padded[p + 3].toInt() and 0xFF) shl 24)
            }

            var a = a0
            var b = b0
            var c = c0
            var d = d0

            for (i in 0 until 64) {
                val f: Int
                val g: Int
                when (i ushr 4) {
                    0 -> {
                        f = (b and c) or (b.inv() and d)
                        g = i
                    }
                    1 -> {
                        f = (d and b) or (d.inv() and c)
                        g = (5 * i + 1) and 15
                    }
                    2 -> {
                        f = b xor c xor d
                        g = (3 * i + 5) and 15
                    }
                    else -> {
                        f = c xor (b or d.inv())
                        g = (7 * i) and 15
                    }
                }
                val nextD = c
                val nextC = b
                val nextB = b + rotateLeft(a + f + MD5_K[i] + words[g], MD5_SHIFT[i])
                a = d
                b = nextB
                c = nextC
                d = nextD
            }

            a0 += a
            b0 += b
            c0 += c
            d0 += d
            offset += 64
        }

        return ByteArray(16).also { out ->
            writeLittleEndian(out, 0, a0)
            writeLittleEndian(out, 4, b0)
            writeLittleEndian(out, 8, c0)
            writeLittleEndian(out, 12, d0)
        }
    }

    /** MD5 小写 hex。 */
    fun md5Hex(message: ByteArray): String = md5(message).toLowerHex()

    fun md5Hex(text: String): String = md5Hex(text.encodeToByteArray())

    /** 标准 Base64（无换行、带 `=` 填充）。 */
    fun base64Encode(bytes: ByteArray): String {
        val alphabet = BASE64_ALPHABET
        val out = StringBuilder((bytes.size + 2) / 3 * 4)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
            val triple = (b0 shl 16) or (b1 shl 8) or b2
            out.append(alphabet[(triple ushr 18) and 0x3F])
            out.append(alphabet[(triple ushr 12) and 0x3F])
            if (i + 1 < bytes.size) {
                out.append(alphabet[(triple ushr 6) and 0x3F])
            } else {
                out.append('=')
            }
            if (i + 2 < bytes.size) {
                out.append(alphabet[triple and 0x3F])
            } else {
                out.append('=')
            }
            i += 3
        }
        return out.toString()
    }

    /** 标准 Base64 解码；忽略换行与空白，非法输入返回 `null`。 */
    fun base64Decode(text: String): ByteArray? {
        val cleaned = text.filterNot { it == '\n' || it == '\r' || it == ' ' || it == '\t' }
        if (cleaned.length % 4 != 0) return null
        val out = ByteArray(cleaned.length / 4 * 3)
        var outIndex = 0
        var i = 0
        while (i < cleaned.length) {
            val c0 = BASE64_ALPHABET.indexOf(cleaned[i])
            val c1 = BASE64_ALPHABET.indexOf(cleaned[i + 1])
            if (c0 < 0 || c1 < 0) return null
            val p2 = cleaned[i + 2]
            val p3 = cleaned[i + 3]
            val c2 = if (p2 == '=') 0 else BASE64_ALPHABET.indexOf(p2)
            val c3 = if (p3 == '=') 0 else BASE64_ALPHABET.indexOf(p3)
            if (c2 < 0 || c3 < 0) return null
            if (p2 == '=' && p3 != '=') return null
            if ((p2 == '=' || p3 == '=') && i + 4 != cleaned.length) return null

            val triple = (c0 shl 18) or (c1 shl 12) or (c2 shl 6) or c3
            out[outIndex++] = (triple ushr 16).toByte()
            if (p2 != '=') out[outIndex++] = (triple ushr 8).toByte()
            if (p3 != '=') out[outIndex++] = triple.toByte()
            i += 4
        }
        return out.copyOf(outIndex)
    }

    /**
     * Java `URLEncoder.encode(value, "utf-8")` 的等价实现。
     *
     * 保留 `A-Z a-z 0-9 - _ . *`，空格转 `+`，其余 UTF-8 字节转 `%XX`（大写）。
     */
    fun urlEncode(value: String): String {
        val out = StringBuilder()
        for (b in value.encodeToByteArray()) {
            val v = b.toInt() and 0xFF
            when {
                v == ' '.code -> out.append('+')
                v in 'A'.code..'Z'.code ||
                    v in 'a'.code..'z'.code ||
                    v in '0'.code..'9'.code ||
                    v == '-'.code || v == '_'.code || v == '.'.code || v == '*'.code ->
                    out.append(v.toChar())

                else -> {
                    out.append('%')
                    out.append(HEX_UPPER[v ushr 4])
                    out.append(HEX_UPPER[v and 0x0F])
                }
            }
        }
        return out.toString()
    }

    /** Java `URLDecoder.decode(value, "utf-8")` 的等价实现。 */
    fun urlDecode(value: String): String {
        val bytes = ArrayList<Byte>(value.length)
        var i = 0
        while (i < value.length) {
            when (val c = value[i]) {
                '+' -> {
                    bytes.add(' '.code.toByte())
                    i++
                }

                '%' -> {
                    if (i + 2 >= value.length) return value
                    val hi = HEX_UPPER.indexOf(value[i + 1].uppercaseChar())
                    val lo = HEX_UPPER.indexOf(value[i + 2].uppercaseChar())
                    if (hi < 0 || lo < 0) return value
                    bytes.add(((hi shl 4) or lo).toByte())
                    i += 3
                }

                else -> {
                    val encoded = c.toString().encodeToByteArray()
                    for (b in encoded) bytes.add(b)
                    i++
                }
            }
        }
        return bytes.toByteArray().decodeToString()
    }

    // ==================== AES 内部实现 ====================

    private fun aesEcbPkcs5Encrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        requireAesKey(key)
        val padding = BLOCK_SIZE - plaintext.size % BLOCK_SIZE
        val padded = plaintext.copyOf(plaintext.size + padding)
        for (i in plaintext.size until padded.size) padded[i] = padding.toByte()

        val schedule = expandKey(key)
        val out = ByteArray(padded.size)
        val state = IntArray(BLOCK_SIZE)
        var offset = 0
        while (offset < padded.size) {
            for (i in 0 until BLOCK_SIZE) state[i] = padded[offset + i].toInt() and 0xFF
            encryptBlock(state, schedule)
            for (i in 0 until BLOCK_SIZE) out[offset + i] = state[i].toByte()
            offset += BLOCK_SIZE
        }
        return out
    }

    private fun aesEcbPkcs5Decrypt(key: ByteArray, ciphertext: ByteArray): ByteArray? {
        requireAesKey(key)
        if (ciphertext.isEmpty() || ciphertext.size % BLOCK_SIZE != 0) return null
        val schedule = expandKey(key)
        val out = ByteArray(ciphertext.size)
        val state = IntArray(BLOCK_SIZE)
        var offset = 0
        while (offset < ciphertext.size) {
            for (i in 0 until BLOCK_SIZE) state[i] = ciphertext[offset + i].toInt() and 0xFF
            decryptBlock(state, schedule)
            for (i in 0 until BLOCK_SIZE) out[offset + i] = state[i].toByte()
            offset += BLOCK_SIZE
        }

        val padding = out.last().toInt() and 0xFF
        if (padding !in 1..BLOCK_SIZE) return null
        for (i in out.size - padding until out.size) {
            if ((out[i].toInt() and 0xFF) != padding) return null
        }
        return out.copyOf(out.size - padding)
    }

    private fun requireAesKey(key: ByteArray) {
        require(key.size == BLOCK_SIZE) { "AES-128 key must be 16 bytes" }
    }

    /** 返回 11 轮 × 16 字节的轮密钥。 */
    private fun expandKey(key: ByteArray): IntArray {
        val words = IntArray(44)
        for (i in 0 until 4) {
            words[i] = ((key[i * 4].toInt() and 0xFF) shl 24) or
                ((key[i * 4 + 1].toInt() and 0xFF) shl 16) or
                ((key[i * 4 + 2].toInt() and 0xFF) shl 8) or
                (key[i * 4 + 3].toInt() and 0xFF)
        }
        for (i in 4 until 44) {
            var temp = words[i - 1]
            if (i % 4 == 0) {
                temp = subWord(rotateWord(temp)) xor (RCON[i / 4] shl 24)
            }
            words[i] = words[i - 4] xor temp
        }

        return IntArray(176) { i ->
            val word = words[i / 4]
            (word ushr (24 - 8 * (i % 4))) and 0xFF
        }
    }

    private fun encryptBlock(state: IntArray, roundKeys: IntArray) {
        addRoundKey(state, roundKeys, 0)
        for (round in 1 until 10) {
            subBytes(state)
            shiftRows(state)
            mixColumns(state)
            addRoundKey(state, roundKeys, round)
        }
        subBytes(state)
        shiftRows(state)
        addRoundKey(state, roundKeys, 10)
    }

    private fun decryptBlock(state: IntArray, roundKeys: IntArray) {
        addRoundKey(state, roundKeys, 10)
        for (round in 9 downTo 1) {
            invShiftRows(state)
            invSubBytes(state)
            addRoundKey(state, roundKeys, round)
            invMixColumns(state)
        }
        invShiftRows(state)
        invSubBytes(state)
        addRoundKey(state, roundKeys, 0)
    }

    private fun addRoundKey(state: IntArray, roundKeys: IntArray, round: Int) {
        val base = round * BLOCK_SIZE
        for (i in 0 until BLOCK_SIZE) state[i] = state[i] xor roundKeys[base + i]
    }

    private fun subBytes(state: IntArray) {
        for (i in state.indices) state[i] = S_BOX[state[i]]
    }

    private fun invSubBytes(state: IntArray) {
        for (i in state.indices) state[i] = INV_S_BOX[state[i]]
    }

    private fun shiftRows(state: IntArray) {
        val copy = state.copyOf()
        for (row in 0 until 4) {
            for (col in 0 until 4) {
                state[4 * col + row] = copy[4 * ((col + row) and 3) + row]
            }
        }
    }

    private fun invShiftRows(state: IntArray) {
        val copy = state.copyOf()
        for (row in 0 until 4) {
            for (col in 0 until 4) {
                state[4 * col + row] = copy[4 * ((col - row + 4) and 3) + row]
            }
        }
    }

    private fun mixColumns(state: IntArray) {
        for (col in 0 until 4) {
            val i = 4 * col
            val a0 = state[i]
            val a1 = state[i + 1]
            val a2 = state[i + 2]
            val a3 = state[i + 3]
            state[i] = xtime(a0) xor (xtime(a1) xor a1) xor a2 xor a3
            state[i + 1] = a0 xor xtime(a1) xor (xtime(a2) xor a2) xor a3
            state[i + 2] = a0 xor a1 xor xtime(a2) xor (xtime(a3) xor a3)
            state[i + 3] = (xtime(a0) xor a0) xor a1 xor a2 xor xtime(a3)
        }
    }

    private fun invMixColumns(state: IntArray) {
        for (col in 0 until 4) {
            val i = 4 * col
            val a0 = state[i]
            val a1 = state[i + 1]
            val a2 = state[i + 2]
            val a3 = state[i + 3]
            state[i] = gfMul(a0, 0x0E) xor gfMul(a1, 0x0B) xor gfMul(a2, 0x0D) xor gfMul(a3, 0x09)
            state[i + 1] =
                gfMul(a0, 0x09) xor gfMul(a1, 0x0E) xor gfMul(a2, 0x0B) xor gfMul(a3, 0x0D)
            state[i + 2] =
                gfMul(a0, 0x0D) xor gfMul(a1, 0x09) xor gfMul(a2, 0x0E) xor gfMul(a3, 0x0B)
            state[i + 3] =
                gfMul(a0, 0x0B) xor gfMul(a1, 0x0D) xor gfMul(a2, 0x09) xor gfMul(a3, 0x0E)
        }
    }

    private fun xtime(value: Int): Int {
        val shifted = (value shl 1) and 0xFF
        return if (value and 0x80 != 0) shifted xor 0x1B else shifted
    }

    private fun gfMul(a: Int, b: Int): Int {
        var x = a
        var y = b
        var result = 0
        repeat(8) {
            if (y and 1 != 0) result = result xor x
            x = xtime(x)
            y = y ushr 1
        }
        return result
    }

    private fun rotateWord(word: Int): Int =
        (word shl 8) or (word ushr 24)

    private fun subWord(word: Int): Int =
        (S_BOX[(word ushr 24) and 0xFF] shl 24) or
            (S_BOX[(word ushr 16) and 0xFF] shl 16) or
            (S_BOX[(word ushr 8) and 0xFF] shl 8) or
            S_BOX[word and 0xFF]

    // ==================== 工具 ====================

    private fun rotateLeft(value: Int, bits: Int): Int =
        (value shl bits) or (value ushr (32 - bits))

    private fun writeLittleEndian(out: ByteArray, offset: Int, value: Int) {
        for (i in 0 until 4) {
            out[offset + i] = (value ushr (8 * i)).toByte()
        }
    }

    private fun ByteArray.toLowerHex(): String = buildString(size * 2) {
        for (b in this@toLowerHex) {
            val v = b.toInt() and 0xFF
            append(HEX_LOWER[v ushr 4])
            append(HEX_LOWER[v and 0x0F])
        }
    }

    private const val HEX_UPPER = "0123456789ABCDEF"
    private const val HEX_LOWER = "0123456789abcdef"
    private const val BASE64_ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    private val RCON = intArrayOf(
        0x00, 0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x1B, 0x36,
    )

    private val MD5_K = intArrayOf(
        0xD76AA478.toInt(), 0xE8C7B756.toInt(), 0x242070DB, 0xC1BDCEEE.toInt(),
        0xF57C0FAF.toInt(), 0x4787C62A, 0xA8304613.toInt(), 0xFD469501.toInt(),
        0x698098D8, 0x8B44F7AF.toInt(), 0xFFFF5BB1.toInt(), 0x895CD7BE.toInt(),
        0x6B901122, 0xFD987193.toInt(), 0xA679438E.toInt(), 0x49B40821,
        0xF61E2562.toInt(), 0xC040B340.toInt(), 0x265E5A51, 0xE9B6C7AA.toInt(),
        0xD62F105D.toInt(), 0x02441453, 0xD8A1E681.toInt(), 0xE7D3FBC8.toInt(),
        0x21E1CDE6, 0xC33707D6.toInt(), 0xF4D50D87.toInt(), 0x455A14ED,
        0xA9E3E905.toInt(), 0xFCEFA3F8.toInt(), 0x676F02D9, 0x8D2A4C8A.toInt(),
        0xFFFA3942.toInt(), 0x8771F681.toInt(), 0x6D9D6122, 0xFDE5380C.toInt(),
        0xA4BEEA44.toInt(), 0x4BDECFA9, 0xF6BB4B60.toInt(), 0xBEBFBC70.toInt(),
        0x289B7EC6, 0xEAA127FA.toInt(), 0xD4EF3085.toInt(), 0x04881D05,
        0xD9D4D039.toInt(), 0xE6DB99E5.toInt(), 0x1FA27CF8, 0xC4AC5665.toInt(),
        0xF4292244.toInt(), 0x432AFF97, 0xAB9423A7.toInt(), 0xFC93A039.toInt(),
        0x655B59C3, 0x8F0CCC92.toInt(), 0xFFEFF47D.toInt(), 0x85845DD1.toInt(),
        0x6FA87E4F, 0xFE2CE6E0.toInt(), 0xA3014314.toInt(), 0x4E0811A1,
        0xF7537E82.toInt(), 0xBD3AF235.toInt(), 0x2AD7D2BB, 0xEB86D391.toInt(),
    )

    private val MD5_SHIFT = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
    )

    private val S_BOX = intArrayOf(
        0x63, 0x7C, 0x77, 0x7B, 0xF2, 0x6B, 0x6F, 0xC5, 0x30, 0x01, 0x67, 0x2B, 0xFE, 0xD7, 0xAB, 0x76,
        0xCA, 0x82, 0xC9, 0x7D, 0xFA, 0x59, 0x47, 0xF0, 0xAD, 0xD4, 0xA2, 0xAF, 0x9C, 0xA4, 0x72, 0xC0,
        0xB7, 0xFD, 0x93, 0x26, 0x36, 0x3F, 0xF7, 0xCC, 0x34, 0xA5, 0xE5, 0xF1, 0x71, 0xD8, 0x31, 0x15,
        0x04, 0xC7, 0x23, 0xC3, 0x18, 0x96, 0x05, 0x9A, 0x07, 0x12, 0x80, 0xE2, 0xEB, 0x27, 0xB2, 0x75,
        0x09, 0x83, 0x2C, 0x1A, 0x1B, 0x6E, 0x5A, 0xA0, 0x52, 0x3B, 0xD6, 0xB3, 0x29, 0xE3, 0x2F, 0x84,
        0x53, 0xD1, 0x00, 0xED, 0x20, 0xFC, 0xB1, 0x5B, 0x6A, 0xCB, 0xBE, 0x39, 0x4A, 0x4C, 0x58, 0xCF,
        0xD0, 0xEF, 0xAA, 0xFB, 0x43, 0x4D, 0x33, 0x85, 0x45, 0xF9, 0x02, 0x7F, 0x50, 0x3C, 0x9F, 0xA8,
        0x51, 0xA3, 0x40, 0x8F, 0x92, 0x9D, 0x38, 0xF5, 0xBC, 0xB6, 0xDA, 0x21, 0x10, 0xFF, 0xF3, 0xD2,
        0xCD, 0x0C, 0x13, 0xEC, 0x5F, 0x97, 0x44, 0x17, 0xC4, 0xA7, 0x7E, 0x3D, 0x64, 0x5D, 0x19, 0x73,
        0x60, 0x81, 0x4F, 0xDC, 0x22, 0x2A, 0x90, 0x88, 0x46, 0xEE, 0xB8, 0x14, 0xDE, 0x5E, 0x0B, 0xDB,
        0xE0, 0x32, 0x3A, 0x0A, 0x49, 0x06, 0x24, 0x5C, 0xC2, 0xD3, 0xAC, 0x62, 0x91, 0x95, 0xE4, 0x79,
        0xE7, 0xC8, 0x37, 0x6D, 0x8D, 0xD5, 0x4E, 0xA9, 0x6C, 0x56, 0xF4, 0xEA, 0x65, 0x7A, 0xAE, 0x08,
        0xBA, 0x78, 0x25, 0x2E, 0x1C, 0xA6, 0xB4, 0xC6, 0xE8, 0xDD, 0x74, 0x1F, 0x4B, 0xBD, 0x8B, 0x8A,
        0x70, 0x3E, 0xB5, 0x66, 0x48, 0x03, 0xF6, 0x0E, 0x61, 0x35, 0x57, 0xB9, 0x86, 0xC1, 0x1D, 0x9E,
        0xE1, 0xF8, 0x98, 0x11, 0x69, 0xD9, 0x8E, 0x94, 0x9B, 0x1E, 0x87, 0xE9, 0xCE, 0x55, 0x28, 0xDF,
        0x8C, 0xA1, 0x89, 0x0D, 0xBF, 0xE6, 0x42, 0x68, 0x41, 0x99, 0x2D, 0x0F, 0xB0, 0x54, 0xBB, 0x16,
    )

    private val INV_S_BOX = IntArray(256).also { inv ->
        for (i in S_BOX.indices) inv[S_BOX[i]] = i
    }
}
