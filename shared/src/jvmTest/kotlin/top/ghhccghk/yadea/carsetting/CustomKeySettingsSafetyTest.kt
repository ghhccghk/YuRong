package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CustomKeySettingsSafetyTest {
    private val options = listOf(
        "ABS 防抱死", "TCS 车身稳定", "雷达功能", "行车记录仪紧急录制", "行车记录仪抓拍",
        "长续航/性能模式切换", "氛围灯功能", "坐垫加热功能", "AI 语音助手唤醒", "无定义",
    )

    private fun item(
        id: String = "cloud:1005:short",
        configId: String = "1005",
        index: Int = 0,
        known: Boolean = true,
    ) = CarSettingItem(
        id = id,
        configId = configId,
        title = "自定义按键",
        summary = "",
        classification = CarSettingClassification.RIDE_CONTROL,
        channel = CarSettingChannel.BLE,
        kind = CarSettingKind.Choice(index, options, valueKnown = known),
    )

    @Test
    fun mapsDefinedChoicesAndKeepsUnknownPlaceholderEditable() {
        for (index in 0..8) {
            val short = VehicleSettingCommands.forItem(item(index = index))
            assertEquals(VehicleSettingCommand("CUSTOMIZE_KEYS_CONFIG_SHORT", 0x131, index), short)
            assertNotNull(VehicleSettingFrameEncoder.encode(requireNotNull(short)))

            val long = VehicleSettingCommands.forItem(item("cloud:1005:long", index = index))
            assertEquals(VehicleSettingCommand("CUSTOMIZE_KEYS_CONFIG_LONG", 0x130, index), long)
        }
        assertEquals(
            VehicleSettingCommand("CUSTOMIZE_KEYS_CONFIG_SHORT", 0x131, 14),
            VehicleSettingCommands.forItem(item(index = 9)),
        )
        assertEquals(
            VehicleSettingCommand("CUSTOMIZE_KEYS_CONFIG_LONG", 0x130, 14),
            VehicleSettingCommands.forItem(item("cloud:1005:long", index = 9)),
        )

        val unknown = item(known = false)
        assertTrue(CarSettingItemAccess.canWrite(unknown))
        assertNull(VehicleSettingCommands.forItem(unknown))
    }

    @Test
    fun malformedItemsAndUnknownValuesNeverReachFakeWriter() = runBlocking {
        val sent = mutableListOf<VehicleSettingCommand>()
        val writer = MappedCarSettingWriter(object : VehicleSettingCommandSink {
            override suspend fun send(command: VehicleSettingCommand): Boolean {
                sent += command
                return true
            }
        })

        for (index in listOf(-1, 10, 15)) {
            val malformed = item(index = index)
            assertNull(VehicleSettingCommands.forItem(malformed))
            assertFalse(writer.write(malformed))
        }
        val unknown = item(known = false)
        assertFalse(writer.write(unknown))
        assertNull(VehicleSettingCommands.forItem(item(configId = "5")))
        assertNull(VehicleSettingCommands.forItem(item(id = "forged:short")))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun directCommandBoundaryEnforcesTypeIdAndSmaliParameterDomain() {
        for (id in listOf(0x130, 0x131)) {
            for (parameter in listOf(9, 10, 11, 12, 13, 15, -1)) {
                assertRejected { VehicleSettingCommand("direct", id, parameter) }
            }
        }
        assertRejected { VehicleSettingCommand("CUSTOMIZE_KEYS_CONFIG_SHORT", 0x130, 0) }
        assertRejected { VehicleSettingCommand("CUSTOMIZE_KEYS_CONFIG_LONG", 0x131, 0) }
        assertRejected { VehicleSettingCommand("CUSTOMIZE_KEYS_CONFIG_SHORT", 0x131, 0, listOf(1)) }

        assertEquals(
            "CUSTOMIZE_KEYS_CONFIG_LONG",
            VehicleSettingCommand("CUSTOMIZE_KEYS_CONFIG_LONG", 0x130, 14).commandType,
        )
        assertEquals(
            "CUSTOMIZE_KEYS_CONFIG_SHORT",
            VehicleSettingCommand("CUSTOMIZE_KEYS_CONFIG_SHORT", 0x131, 8).commandType,
        )
    }

    private fun assertRejected(block: () -> Unit) {
        try {
            block()
            fail("expected invalid custom-key command to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected validation failure.
        }
    }
}
