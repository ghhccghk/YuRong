package top.ghhccghk.yadea.instrumentskin

/**
 * 原 App 1.58 皮肤包格式。
 *
 * 目录项固定 32 字节：
 * CRC16(LE) + 资源 CRC16(LE) + offset(LE32) + size(LE32) +
 * type + 0xFF + last(LE16) + 16B ASCII 名称。
 *
 * 目录和资源校验使用 CRC-CCITT（0x1021，初值 0）；仪表网络报文仍使用
 * [InstrumentCrc.crc16Modbus]，两者不能混用。
 */
object InstrumentSkinPack {

    val IMAGE_NAMES: List<String> = buildList {
        (1..9).forEach { add("FD1_" + it.toString().padStart(2, '0') + ".jpg") }
        add("LEVEL_1.jpg")
        add("LEVEL_2.jpg")
        (1..9).forEach { add("SX1_" + it.toString().padStart(2, '0') + ".jpg") }
    }

    val REQUIRED_NAMES: List<String> = IMAGE_NAMES + "version.ini"

    private const val ENTRY_SIZE = 32
    private const val MAX_BYTES = 8 * 1024 * 1024
    private const val SKIN_TYPE = 3
    private const val RESOURCE_TYPE = 2

    fun versionIni(images: Map<String, ByteArray>): ByteArray {
        require(images.keys == IMAGE_NAMES.toSet()) { "皮肤图片数量不匹配" }
        val sb = StringBuilder()
        sb.append("[config]\r\n")
        sb.append("file=YADEA_5ANIM_EGG.avi\r\n")
        sb.append("version=1.58\r\n")
        sb.append("crc=xxxxxx\r\n\r\n")
        for (name in IMAGE_NAMES) {
            val section = name.replace('.', '_')
            sb.append("[file_$section]\r\n")
            sb.append("file=$name\r\n")
            sb.append("crc=").append(InstrumentCrc.crc32Hex(images.getValue(name))).append("\r\n\r\n")
        }
        return sb.toString().encodeToByteArray()
    }

    /** 复刻原 App 的 TIDX 索引。 */
    fun buildIndex(fileNames: List<String>): ByteArray {
        require(fileNames.isNotEmpty()) { "皮肤索引不能为空" }
        val header = ByteArray(10) { 0xFF.toByte() }
        writeLe32(header, 6, fileNames.size)

        val output = ArrayOutput()
        output.write("TIDX".encodeToByteArray())
        output.write(le16(InstrumentCrc.crc16Ccitt(header)))
        output.write(header)
        fileNames.forEachIndexed { index, name ->
            validateAsciiName(name)
            val withTerminator = name.encodeToByteArray() + byteArrayOf(0)
            val payload = byteArrayOf(
                (withTerminator.size + 4).toByte(),
                (index + 1).toByte(),
            ) + withTerminator
            output.write(le16(InstrumentCrc.crc16Ccitt(payload)))
            output.write(payload)
        }
        return output.toByteArray()
    }

    fun build(files: Map<String, ByteArray>): ByteArray {
        require(files.keys == REQUIRED_NAMES.toSet()) {
            "皮肤必须包含原版 20 张图片和 version.ini"
        }

        val ordered = buildList {
            add("index.idx" to buildIndex(REQUIRED_NAMES))
            REQUIRED_NAMES.forEach { name -> add(name to files.getValue(name)) }
        }
        var dataStart = (ordered.size + 1) * ENTRY_SIZE
        val offsets = IntArray(ordered.size)
        ordered.forEachIndexed { index, (_, data) ->
            require(data.isNotEmpty() && data.size <= MAX_BYTES) { "皮肤资源为空或过大" }
            offsets[index] = dataStart
            dataStart = align16(dataStart + data.size)
        }
        require(dataStart <= MAX_BYTES) { "皮肤包超过 8 MiB" }

        val pack = ByteArray(dataStart) { 0xFF.toByte() }
        ordered.forEachIndexed { index, (name, data) ->
            val entryIndex = index + 1
            val last = if (index == ordered.lastIndex) 1 else 0
            entry(
                dataCrc = InstrumentCrc.crc16Ccitt(data),
                offset = offsets[index],
                size = data.size,
                type = RESOURCE_TYPE,
                last = last,
                name = name,
            ).copyInto(pack, entryIndex * ENTRY_SIZE)
            data.copyInto(pack, offsets[index])
        }

        // 分区头覆盖从第 32 字节开始的整个包，因此最后按最终内容计算。
        entry(
            dataCrc = InstrumentCrc.crc16Ccitt(pack, offset = ENTRY_SIZE),
            offset = ENTRY_SIZE,
            size = pack.size,
            type = SKIN_TYPE,
            last = 0,
            name = "skin",
        ).copyInto(pack, 0)
        return pack
    }

    fun parse(bytes: ByteArray): Map<String, ByteArray> {
        require(bytes.size >= (REQUIRED_NAMES.size + 2) * ENTRY_SIZE) { "皮肤包长度无效" }
        require(bytes.size < MAX_BYTES + 1 && bytes.size % 16 == 0) { "皮肤包长度无效" }

        val names = buildList {
            add("skin")
            add("index.idx")
            addAll(REQUIRED_NAMES)
        }
        val result = LinkedHashMap<String, ByteArray>()
        var expectedOffset = names.size * ENTRY_SIZE

        names.forEachIndexed { index, expectedName ->
            val entryBytes = bytes.copyOfRange(index * ENTRY_SIZE, (index + 1) * ENTRY_SIZE)
            val directoryCrc = readLe16(entryBytes, 0)
            require(directoryCrc == InstrumentCrc.crc16Ccitt(entryBytes, 2, 30)) {
                "皮肤目录 CRC16 错误：$expectedName"
            }
            val dataCrc = readLe16(entryBytes, 2)
            val offset = readLe32(entryBytes, 4)
            val size = readLe32(entryBytes, 8)
            val type = entryBytes[12].toInt() and 0xFF
            require((entryBytes[13].toInt() and 0xFF) == 0xFF) { "皮肤目录标记错误" }
            val last = readLe16(entryBytes, 14)
            require(last == if (index == names.lastIndex) 1 else 0) { "皮肤目录尾标记错误" }
            require(entryBytes[16 + expectedName.length].toInt() == 0) { "皮肤名称缺少终止符" }
            val actualName = entryBytes.copyOfRange(16, 16 + expectedName.length).decodeToString()
            require(actualName == expectedName) { "皮肤目录名称顺序错误：$expectedName" }
            require((entryBytes.copyOfRange(16 + expectedName.length + 1, 32)).all {
                (it.toInt() and 0xFF) == 0xFF
            }) { "皮肤目录名称填充错误：$expectedName" }

            if (index == 0) {
                require(type == SKIN_TYPE && offset == ENTRY_SIZE && size == bytes.size) {
                    "皮肤分区头无效"
                }
                require(dataCrc == InstrumentCrc.crc16Ccitt(bytes, ENTRY_SIZE)) {
                    "皮肤分区内容 CRC16 错误"
                }
            } else {
                require(type == RESOURCE_TYPE && size > 0) { "皮肤资源类型错误：$expectedName" }
                require(offset == expectedOffset && offset % 16 == 0) {
                    "皮肤资源偏移错误：$expectedName"
                }
                require(offset + size <= bytes.size) { "皮肤目录越界：$expectedName" }
                val data = bytes.copyOfRange(offset, offset + size)
                require(dataCrc == InstrumentCrc.crc16Ccitt(data)) {
                    "皮肤资源 CRC16 错误：$expectedName"
                }
                val alignedEnd = align16(offset + size)
                require(alignedEnd <= bytes.size) { "皮肤资源对齐区越界：$expectedName" }
                require((offset + size until alignedEnd).all {
                    (bytes[it].toInt() and 0xFF) == 0xFF
                }) { "皮肤资源填充错误：$expectedName" }
                expectedOffset = alignedEnd
                if (expectedName == "index.idx") {
                    require(data.contentEquals(buildIndex(REQUIRED_NAMES))) { "皮肤索引不匹配" }
                } else {
                    result[expectedName] = data
                }
            }
        }
        require(expectedOffset == bytes.size) { "皮肤包存在未识别尾部数据" }
        verifyVersionIni(result)
        return result
    }

    private fun verifyVersionIni(files: Map<String, ByteArray>) {
        val version = files["version.ini"] ?: error("缺少 version.ini")
        val text = version.decodeToString()
        require(text.contains("version=1.58", ignoreCase = true)) {
            "当前仅支持已验证的 1.58 皮肤结构"
        }
        IMAGE_NAMES.forEach { name ->
            val section = "[file_${name.replace('.', '_')}]\r\n"
            require(text.contains(section, ignoreCase = true)) { "version.ini 缺少 $name" }
            require(text.contains("file=$name\r\n", ignoreCase = true)) {
                "version.ini 文件名错误：$name"
            }
            require(text.contains(
                "crc=${InstrumentCrc.crc32Hex(files.getValue(name))}\r\n",
                ignoreCase = true,
            )) {
                "version.ini 图片 CRC32 错误：$name"
            }
        }
    }

    private fun entry(
        dataCrc: Int,
        offset: Int,
        size: Int,
        type: Int,
        last: Int,
        name: String,
    ): ByteArray {
        validateAsciiName(name)
        val payload = ByteArray(30) { 0xFF.toByte() }
        writeLe16(payload, 0, dataCrc)
        writeLe32(payload, 2, offset)
        writeLe32(payload, 6, size)
        payload[10] = type.toByte()
        payload[11] = 0xFF.toByte()
        writeLe16(payload, 12, last)
        name.encodeToByteArray().copyInto(payload, 14, 0, minOf(14, name.length))
        payload[14 + name.length.coerceAtMost(14)] = 0
        return le16(InstrumentCrc.crc16Ccitt(payload)) + payload
    }

    private fun validateAsciiName(name: String) {
        require(name.length in 1..14) { "无效皮肤文件名" }
        require(name.all { it in ' '..'~' && it != '/' && it != '\\' }) {
            "皮肤文件名必须是 ASCII"
        }
    }

    private fun le16(value: Int): ByteArray = byteArrayOf(
        value.toByte(),
        (value ushr 8).toByte(),
    )

    private fun writeLe16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun writeLe32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
        bytes[offset + 2] = (value ushr 16).toByte()
        bytes[offset + 3] = (value ushr 24).toByte()
    }

    private fun readLe16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun readLe32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun align16(size: Int): Int = (size + 15) / 16 * 16

    private class ArrayOutput {
        private val chunks = mutableListOf<ByteArray>()
        private var size = 0

        fun write(bytes: ByteArray) {
            chunks += bytes
            size += bytes.size
        }

        fun toByteArray(): ByteArray {
            val result = ByteArray(size)
            var offset = 0
            chunks.forEach { chunk ->
                chunk.copyInto(result, offset)
                offset += chunk.size
            }
            return result
        }
    }
}
