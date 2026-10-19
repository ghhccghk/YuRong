package top.ghhccghk.yadea.carsetting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase4CarSettingsTest {

    @Test
    fun phase4CommandsUseConfirmedIdsAndBitfields() {
        assertEquals(
            VehicleSettingCommand(
                "EDGE_BRACE_INDUCTION_OPEN",
                0x131,
                parameter = 1,
            ),
            VehicleSettingCommands.forConfigId("2", CarSettingKind.Toggle(true)),
        )
        assertEquals(
            VehicleSettingCommand(
                "TILT_WARNING_CLOSE",
                0x12D,
                parameter = 0,
            ),
            VehicleSettingCommands.forConfigId("32", CarSettingKind.Toggle(false)),
        )
        assertEquals(
            VehicleSettingCommand(
                "COMMAND_CONST_T_POWER_SETTING",
                0x6A,
                parameter = 1,
            ),
            VehicleSettingCommands.forConfigId("63", CarSettingKind.Toggle(true)),
        )
        assertEquals(
            VehicleSettingCommand(
                "TRACK_MODE",
                0x91,
                parameter = 0,
            ),
            VehicleSettingCommands.forConfigId("90", CarSettingKind.Toggle(false)),
        )
    }

    @Test
    fun phase4FramesCanBeEncoded() {
        listOf(
            VehicleSettingCommand("EDGE_BRACE_INDUCTION_CLOSE", 0x131, 0),
            VehicleSettingCommand("TILT_WARNING_OPEN", 0x12D, 1),
            VehicleSettingCommand("COMMAND_SEAT_POWER_UP", 0x130, 1),
            VehicleSettingCommand("COMMAND_CONST_T_POWER_SETTING", 0x6A, 1),
            VehicleSettingCommand("TRACK_MODE", 0x90, 1),
        ).forEach { command ->
            assertNotNull(command.toString(), VehicleSettingFrameEncoder.encode(command))
        }
    }

    @Test
    fun cloudFieldsCoverFenceDistanceAndSeatHeating() {
        assertEquals(
            mapOf(
                "_gateway" to "True",
                "distanceOn" to "1",
                "vin" to "VIN",
            ),
            CloudCarSettingUpdateBuilder.build(
                configId = "10",
                kind = CarSettingKind.Toggle(true),
                vin = "VIN",
            )?.fields,
        )
        assertEquals(
            mapOf(
                "_gateway" to "True",
                "bleDistance" to "5",
                "vin" to "VIN",
            ),
            CloudCarSettingUpdateBuilder.build(
                configId = "52",
                kind = CarSettingKind.Slider(5f, 1f..12f),
                vin = "VIN",
            )?.fields,
        )
        assertEquals(
            mapOf(
                "_gateway" to "True",
                "seatHeatingLevel" to "3",
                "vin" to "VIN",
            ),
            CloudCarSettingUpdateBuilder.build(
                configId = "62",
                kind = CarSettingKind.Choice(2, listOf("1 档", "2 档", "3 档")),
                vin = "VIN",
            )?.fields,
        )
    }

    @Test
    fun mapperCreatesSecondBatchControls() {
        val cloud = CloudCarSettings(
            configs = listOf(
                CloudBikeConfig(confId = "10", confName = "电子围栏"),
                CloudBikeConfig(confId = "52", confName = "感应解锁"),
                CloudBikeConfig(confId = "62", confName = "座椅加热"),
                CloudBikeConfig(confId = "63", confName = "T 动力"),
                CloudBikeConfig(confId = "90", confName = "赛道模式"),
            ),
            values = mapOf(
                "distanceOn" to "1",
                "distance" to "200",
                "bleUnlockOn" to "1",
                "bleDistance" to "3",
                "seatHeating" to "1",
                "seatHeatingLevel" to "2",
            ),
        )
        val items = CloudCarSettingMapper.toItems(cloud)
        assertTrue(items.first { it.id == "cloud:10" }.kind is CarSettingKind.Toggle)
        assertTrue(items.first { it.id == "cloud:10:distance" }.kind is CarSettingKind.Slider)
        assertTrue(items.first { it.id == "cloud:52" }.kind is CarSettingKind.Toggle)
        assertTrue(items.first { it.id == "cloud:52:distance" }.kind is CarSettingKind.Slider)
        assertTrue(items.first { it.id == "cloud:62" }.kind is CarSettingKind.Toggle)
        assertEquals(
            1,
            (items.first { it.id == "cloud:62:level" }.kind as CarSettingKind.Choice)
                .selectedIndex,
        )
        assertTrue(items.first { it.id == "cloud:63" }.writable)
        assertTrue(items.first { it.id == "cloud:90" }.writable)
    }
}
