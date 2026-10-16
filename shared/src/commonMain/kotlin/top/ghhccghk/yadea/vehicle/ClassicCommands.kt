package top.ghhccghk.yadea.vehicle

/**
 * 自行车经典命令。
 *
 * 这 8 条命令是**未经 `5944` 包封**的裸载荷，直接写入车辆特征值。
 * 字节逐条来自 `BleBicycleControlCommands$ClassicCommands` 的静态初始化块。
 */
internal object ClassicCommands {

    /** 开机。 */
    val ON = byteArrayOf(0x07, 0x01, 0x04)

    /** 关机。 */
    val OFF = byteArrayOf(0x07, 0x01, 0x0E)

    /** 锁车。 */
    val LOCK = byteArrayOf(0x07, 0x01, 0x01)

    /** 开锁。 */
    val UNLOCK = byteArrayOf(0x07, 0x01, 0x02)

    /** 寻车（鸣笛）。 */
    val WHISTLE = byteArrayOf(0x07, 0x01, 0x03)

    /** 检测。 */
    val DETECT = byteArrayOf(0x07, 0x01, 0x0D)

    /** ODO 清零。 */
    val ODO_RESET = byteArrayOf(0x07, 0x01, 0x0A)

    /** 坐垫。 */
    val CUSHION = byteArrayOf(0x07, 0x01, 0x09)

    /** 氛围灯命令 ID；载荷需另行构造，不在这 8 条之列。 */
    const val COMMAND_ID_ATMOSPHERE_LAMP = 0x4C

    /**
     * 命令 ID → 裸载荷。对应 `BleBicycleCommandManager` 里的分派表。
     *
     * @return 未知 ID 时返回 `null`
     */
    fun byCommandId(id: Int): ByteArray? = when (id) {
        0x08 -> ON
        0x07 -> OFF
        0x06 -> LOCK
        0x04 -> UNLOCK
        0x03 -> WHISTLE
        0x05 -> DETECT
        0x31 -> ODO_RESET
        0x09 -> CUSHION
        else -> null
    }
}
