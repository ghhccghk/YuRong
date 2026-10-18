package top.ghhccghk.yadea.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleInfoFormatterTest {
    private fun packet(size: Int = 0xC0, fill: Byte = 0x00) = ByteArray(size) { fill }

    @Test
    fun emptyStatus_hasDashesAndConnectionHint() {
        val model = VehicleInfoFormatter.format(null, connected = true)
        assertEquals("已连接，等待车辆上报", model.connectionLabel)
        assertEquals("—", model.speedLabel)
        assertTrue(model.sensorLines.isEmpty())
    }

    @Test
    fun formattedStatus_usesTtpValues() {
        val d = packet()
        d[0x08] = 0x01 // 未锁车
        d[0x4E] = 0x01 // 已开机
        d[0x4B] = 0xFA.toByte()
        d[0x4C] = 0x00 // 25.0 km/h
        d[0x49] = 87 // SOC
        d[0x43] = 0x7B // 12.3 km
        d[0x45] = 0xE8.toByte()
        d[0x46] = 0x03
        d[0x47] = 0x00
        d[0x48] = 0x00 // 100.0 km
        d[0x50] = 0xD0.toByte()
        d[0x51] = 0x01 // 46.4 V
        d[0x52] = 0xE0.toByte()
        d[0x53] = 0x2E // 200.0 A
        d[0x54] = 0x10 // chargingStatus = 1

        val info = TtpInfoParser.parse(d)
        val model = VehicleInfoFormatter.format(info, connected = true)
        assertEquals("车辆在线", model.connectionLabel)
        assertEquals("未锁车", model.lockLabel)
        assertEquals("已开机", model.powerLabel)
        assertEquals("25.0 km/h", model.speedLabel)
        assertEquals("87 %", model.batteryLabel)
        assertEquals("12.3 km", model.rangeLabel)
        assertEquals("100.0 km", model.odoLabel)
        assertEquals("46.4 V", model.voltageLabel)
        assertEquals("200.0 A", model.currentLabel)
        assertEquals("充电中", model.chargeLabel)
    }

    @Test
    fun invalidSentinels_renderAsDash() {
        val info = TtpInfoParser.parse(packet(fill = 0xFF.toByte()))
        val model = VehicleInfoFormatter.format(info, connected = false)
        assertEquals("—", model.speedLabel)
        assertEquals("—", model.batteryLabel)
        assertEquals("—", model.lockLabel)
        assertEquals("—", model.powerLabel)
    }
}
