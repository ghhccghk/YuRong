package top.ghhccghk.yadea.protocol

// ---- ByteArray 扩展：大端/小端读写 ----

fun ByteArray.writeUInt16BE(offset: Int, value: Int) {
    this[offset] = (value shr 8).toByte()
    this[offset + 1] = value.toByte()
}

fun ByteArray.writeUInt16LE(offset: Int, value: Int) {
    this[offset] = value.toByte()
    this[offset + 1] = (value shr 8).toByte()
}

fun ByteArray.writeUInt32BE(offset: Int, value: Int) {
    this[offset] = (value shr 24).toByte()
    this[offset + 1] = (value shr 16).toByte()
    this[offset + 2] = (value shr 8).toByte()
    this[offset + 3] = value.toByte()
}

fun ByteArray.writeUInt32LE(offset: Int, value: Int) {
    this[offset] = value.toByte()
    this[offset + 1] = (value shr 8).toByte()
    this[offset + 2] = (value shr 16).toByte()
    this[offset + 3] = (value shr 24).toByte()
}

fun ByteArray.readUInt16BE(offset: Int): Int =
    (this[offset].u() shl 8) or this[offset + 1].u()

fun ByteArray.readUInt16LE(offset: Int): Int =
    this[offset].u() or (this[offset + 1].u() shl 8)

fun ByteArray.readUInt32BE(offset: Int): Long =
    (this[offset].u().toLong() shl 24) or
            (this[offset + 1].u().toLong() shl 16) or
            (this[offset + 2].u().toLong() shl 8) or
            this[offset + 3].u().toLong()

fun ByteArray.readUInt32LE(offset: Int): Long =
    this[offset].u().toLong() or
            (this[offset + 1].u().toLong() shl 8) or
            (this[offset + 2].u().toLong() shl 16) or
            (this[offset + 3].u().toLong() shl 24)

/** 将 Byte 当作无符号 0-255 */
fun Byte.u(): Int = this.toInt() and 0xFF

/** 位操作工具 */
fun setBits(value: Byte, startBit: Int, bitCount: Int, bits: Int): Byte {
    val mask = ((1 shl bitCount) - 1) shl startBit
    val cleared = value.toInt() and mask.inv()
    val set = (bits shl startBit) and mask
    return (cleared or set).toByte()
}
