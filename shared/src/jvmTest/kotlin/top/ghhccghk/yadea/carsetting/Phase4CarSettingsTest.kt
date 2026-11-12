package top.ghhccghk.yadea.carsetting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase4CarSettingsTest {

    @Test
    fun phase4CommandsUseConfirmedIdsAndBitfields() {
        assertEquals(
            VehicleSettingCommand(
                "SLIDE_BRACE_ON",
                0x0F,
            ),
            VehicleSettingCommands.forConfigId("2", CarSettingKind.Toggle(true)),
        )
        assertEquals(
            VehicleSettingCommand(
                "TILT_WARNING_CLOSE",
                0xA1,
            ),
            VehicleSettingCommands.forConfigId("32", CarSettingKind.Toggle(false)),
        )
        assertEquals(
            VehicleSettingCommand(
                "COMMAND_CONST_T_POWER_SETTING",
                0xCF,
                parameter = 1,
            ),
            VehicleSettingCommands.forConfigId("63", CarSettingKind.Toggle(true)),
        )
        assertEquals(
            VehicleSettingCommand(
                "TRACK_MODE",
                0x13F,
                parameter = 0,
            ),
            VehicleSettingCommands.forConfigId("90", CarSettingKind.Toggle(false)),
        )
    }

    @Test
    fun commandMappingsMatchOriginalQqBranches() {
        assertEquals(
            VehicleSettingCommand("TILT_WARNING_OPEN", 0xA0),
            VehicleSettingCommands.forConfigId("32", CarSettingKind.Toggle(true)),
        )
        assertEquals(
            VehicleSettingCommand("CONST_SPEED_OPEN", 0xAA),
            VehicleSettingCommands.forConfigId("45", CarSettingKind.Toggle(true)),
        )
        assertEquals(
            VehicleSettingCommand("COMMAND_CONST_T_POWER_SETTING", 0xCF, 1),
            VehicleSettingCommands.forConfigId("63", CarSettingKind.Toggle(true)),
        )
        assertEquals(
            VehicleSettingCommand("TRACK_MODE", 0x13F, 1),
            VehicleSettingCommands.forConfigId("90", CarSettingKind.Toggle(true)),
        )
        assertEquals(
            VehicleSettingCommand("ENERGY_RECOVER_OPEN", 0x13),
            VehicleSettingCommands.forConfigId("17", CarSettingKind.Toggle(true)),
        )
        assertEquals(
            VehicleSettingCommand("COMMAND_CONST_TEMPLE_SETTING", 0xC7, 1),
            VehicleSettingCommands.forConfigId("53", CarSettingKind.Toggle(true)),
        )
        assertEquals(
            VehicleSettingCommand("MAX_SPEED_SETTING", 0x36, 80),
            VehicleSettingCommands.forConfigId(
                "61",
                CarSettingKind.Slider(80f, 0f..100f),
            ),
        )
        assertEquals(
            VehicleSettingCommand("MAX_SPEED_SETTING", 0x36, 0),
            VehicleSettingCommands.forConfigId(
                "61",
                CarSettingKind.Slider(0f, 0f..100f),
            ),
        )
        assertEquals(
            VehicleSettingCommand("SEAT_HEAT_SWITCH", 0xD2, 1),
            VehicleSettingCommands.forConfigId("62", CarSettingKind.Toggle(true)),
        )
        assertEquals(
            VehicleSettingCommand("SEAT_HEAT_LEVEL", 0xD3, 1),
            VehicleSettingCommands.forConfigId(
                "62",
                CarSettingKind.Choice(1, listOf("1 档", "2 档", "3 档")),
            ),
        )
        assertEquals(
            VehicleSettingCommand("LONG_ENDURANCE_SETTING", 0xDD, 0),
            VehicleSettingCommands.forConfigId("72", CarSettingKind.Toggle(true)),
        )
        // Bare BLE omits the original branch's cloud metadata update.
        assertNull(VehicleSettingCommands.forConfigId("77", CarSettingKind.Toggle(true)))
        assertEquals(
            VehicleSettingCommand("COMMAND_OTD_SWITCH", 0x132, 1),
            VehicleSettingCommands.forConfigId("80", CarSettingKind.Toggle(true)),
        )
        // Quiet mode uses an independent order-39 frame, never an ordinary 3E setting.
        assertNull(VehicleSettingCommands.forConfigId("93", CarSettingKind.Toggle(true)))
    }

    @Test
    fun dynamicEnduranceSwitchUsesInlineOperationPolarity() {
        // The KMP directory mirrors bikeConfigVos' inline row (helper.s XORs checked).
        assertEquals(
            VehicleSettingCommand("LONG_ENDURANCE_SETTING", 0xDD, 0),
            VehicleSettingCommands.forConfigId("72", CarSettingKind.Toggle(true)),
        )
        assertEquals(
            VehicleSettingCommand("LONG_ENDURANCE_SETTING", 0xDD, 1),
            VehicleSettingCommands.forConfigId("72", CarSettingKind.Toggle(false)),
        )
    }

    @Test
    fun dynamicOtdSwitchPassesInlineTargetPolarity() {
        assertEquals(
            VehicleSettingCommand("COMMAND_OTD_SWITCH", 0x132, 1),
            VehicleSettingCommands.forConfigId("80", CarSettingKind.Toggle(true)),
        )
        assertEquals(
            VehicleSettingCommand("COMMAND_OTD_SWITCH", 0x132, 0),
            VehicleSettingCommands.forConfigId("80", CarSettingKind.Toggle(false)),
        )
    }

    @Test
    fun phase4FramesCanBeEncoded() {
        listOf(
            VehicleSettingCommand("SLIDE_BRACE_OFF", 0x10),
            VehicleSettingCommand("COMMAND_SEAT_POWER_UP", 0xFB1, 1),
            VehicleSettingCommand("TILT_WARNING_OPEN", 0xA0),
            VehicleSettingCommand("TILT_WARNING_CLOSE", 0xA1),
            VehicleSettingCommand("CONST_SPEED_OPEN", 0xAA),
            VehicleSettingCommand("CONST_SPEED_CLOSE", 0xAB),
            VehicleSettingCommand("COMMAND_CONST_T_POWER_SETTING", 0xCF, 1),
            VehicleSettingCommand("TRACK_MODE", 0x13F, 1),
            VehicleSettingCommand("ENERGY_RECOVER_OPEN", 0x13),
            VehicleSettingCommand("ENERGY_RECOVER_CLOSE", 0x14),
            VehicleSettingCommand("COMMAND_CONST_TEMPLE_SETTING", 0xC7, 1),
            VehicleSettingCommand("SEAT_HEAT_SWITCH", 0xD2, 1),
            VehicleSettingCommand("SEAT_HEAT_LEVEL", 0xD3, 2),
            VehicleSettingCommand("LONG_ENDURANCE_SETTING", 0xDD, 1),
            VehicleSettingCommand("SLEEP_MODE_SWITCH", 0x133, 1),
            VehicleSettingCommand("COMMAND_OTD_SWITCH", 0x132, 0),
        ).forEach { command ->
            assertNotNull(command.toString(), VehicleSettingFrameEncoder.encode(command))
        }
    }

    @Test
    fun cloudFieldsCoverConfirmedRequestShapes() {
        assertEquals(
            mapOf(
                "_gateway" to "True",
                "distanceOn" to "1",
                "distance" to "200",
                "centerCircleLon" to 120.25,
                "centerCircleLat" to 30.5,
                "vin" to "VIN",
            ),
            CloudCarSettingUpdateBuilder.build(
                configId = "10",
                kind = CarSettingKind.Toggle(true),
                vin = "VIN",
                geofence = GeofenceConfiguration("VIN", false, 200, 120.25, 30.5),
            )?.fields,
        )
        assertNull(CloudCarSettingUpdateBuilder.build(
            configId = "52",
            kind = CarSettingKind.Slider(5f, 1f..12f),
            vin = "VIN",
        ))
        assertEquals(
            mapOf(
                "_gateway" to "True",
                "seatHeatingLevel" to 2,
                "oper" to "SET_PARAM",
                "id" to "SEAT_HEATING_LEVEL",
                "value" to 2,
                "valueJson" to "[{\"byteCount\":1,\"dataType\":1,\"sort\":1,\"value\":2}]",
                "vin" to "VIN",
            ),
            CloudCarSettingUpdateBuilder.build(
                configId = "62",
                kind = CarSettingKind.Choice(2, listOf("1 档", "2 档", "3 档")),
                vin = "VIN",
            )?.fields,
        )
        assertEquals(
            mapOf(
                "_gateway" to "True",
                "id" to "CHARGE_WATT_SWITCH",
                "value" to 1,
                "vin" to "VIN",
            ),
            CloudCarSettingUpdateBuilder.build(
                configId = "47",
                kind = CarSettingKind.Toggle(true),
                vin = "VIN",
            )?.fields,
        )
        assertEquals(
            mapOf(
                "_gateway" to "True",
                "id" to "CHARGE_WATT_VAL",
                "value" to 650,
                "vin" to "VIN",
            ),
            CloudCarSettingUpdateBuilder.build(
                configId = "47",
                kind = CarSettingKind.Slider(650f, 200f..1_000f),
                vin = "VIN",
            )?.fields,
        )
        assertEquals(
            mapOf(
                "_gateway" to "True",
                "id" to "CHARGE_UL_VAL",
                "value" to 90,
                "vin" to "VIN",
            ),
            CloudCarSettingUpdateBuilder.build(
                configId = "48",
                kind = CarSettingKind.Slider(90f, 75f..100f),
                vin = "VIN",
            )?.fields,
        )
    }
    @Test
    fun cloudToggleTypesMatchP4() {
        val tamper = requireNotNull(
            CloudCarSettingUpdateBuilder.build(
                configId = "75",
                kind = CarSettingKind.Toggle(true),
                vin = "VIN",
            ),
        )
        assertEquals(1, tamper.fields["tamperProofIotEnable"])

        val lowBattery = requireNotNull(
            CloudCarSettingUpdateBuilder.build(
                configId = "82",
                kind = CarSettingKind.Toggle(true),
                vin = "VIN",
            ),
        )
        assertEquals(1, lowBattery.fields["lowBatTelWarning"])

        val sleep = requireNotNull(
            CloudCarSettingUpdateBuilder.build(
                configId = "77",
                kind = CarSettingKind.Toggle(true),
                vin = "VIN",
            ),
        )
        assertEquals(CloudCarSettingUpdatePath.PARAM_SETTING, sleep.path)
        assertNull(sleep.fields["sleepMode"])
        assertEquals("SLEEP_MODE_SWITCH", sleep.fields["id"])
        assertEquals(1, sleep.fields["value"])
        assertEquals(
            "[{\"byteCount\":1,\"dataType\":1,\"sort\":1,\"value\":1}]",
            sleep.fields["valueJson"],
        )

        val soundOn = requireNotNull(
            CloudCarSettingUpdateBuilder.build(
                configId = "40",
                kind = CarSettingKind.Toggle(true),
                vin = "VIN",
            ),
        )
        assertEquals(CloudCarSettingUpdatePath.BIKE_SETTING, soundOn.path)
        assertEquals("1", soundOn.fields["colorEggSoundEffect"])

        val soundOff = requireNotNull(
            CloudCarSettingUpdateBuilder.build(
                configId = "40",
                kind = CarSettingKind.Toggle(false),
                vin = "VIN",
            ),
        )
        assertEquals(CloudCarSettingUpdatePath.PARAM_SETTING, soundOff.path)
        assertEquals("CLEAR_SOUND_EFFECT", soundOff.fields["id"])
        assertEquals(7, soundOff.fields["value"])
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
        val manualDistance = items.first { it.id == "cloud:52:distance" }
        assertTrue(manualDistance.kind is CarSettingKind.Slider)
        assertFalse(manualDistance.writable)
        assertFalse((manualDistance.kind as CarSettingKind.Slider).valueKnown)
        assertTrue(items.first { it.id == "cloud:62" }.kind is CarSettingKind.Toggle)
        assertEquals(
            2,
            (items.first { it.id == "cloud:62:level" }.kind as CarSettingKind.Choice)
                .selectedIndex,
        )
        assertTrue(items.first { it.id == "cloud:63" }.writable)
        assertTrue(items.first { it.id == "cloud:90" }.writable)
    }
}
