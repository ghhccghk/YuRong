package top.ghhccghk.yadea

private const val HEX_DIGITS = "0123456789ABCDEF"

internal fun hexByte(value: Int): String {
    val v = value and 0xFF
    return charArrayOf(HEX_DIGITS[v shr 4], HEX_DIGITS[v and 0x0F]).concatToString()
}