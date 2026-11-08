package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ghhccghk.yadea.vehicle.TtpInfo
import top.ghhccghk.yadea.vehicle.VehicleNotify
import top.ghhccghk.yadea.vehicle.VehicleNotifyDispatcher
import top.ghhccghk.yadea.vehicle.hexToBytes

class CarSettingsControllerTest {
    private fun items() = CarSettingsRegistry.defaultItems()

    private class FakeWriter(
        private val result: Boolean,
    ) : CarSettingWriter {
        var last: CarSettingItem? = null
        override suspend fun write(item: CarSettingItem): Boolean {
            last = item
            return result
        }
    }

    private class FakeGateway(
        private val cloud: CloudCarSettings?,
    ) : CarSettingsGateway {
        override suspend fun load(vin: String): CloudCarSettings? = cloud
        override suspend fun update(vin: String, update: CloudCarSettingUpdate): Boolean = false
    }

    private fun statusCloud() = CloudCarSettings(
        configs = listOf(
            CloudBikeConfig(confId = "2", confName = "边撑感应"),
            CloudBikeConfig(confId = "31", confName = "速度单位"),
            CloudBikeConfig(confId = "33", confName = "模拟声浪"),
            CloudBikeConfig(confId = "37", confName = "TCS"),
            CloudBikeConfig(confId = "38", confName = "ABS"),
            CloudBikeConfig(confId = "47", confName = "充电功率"),
            CloudBikeConfig(confId = "62", confName = "座椅加热"),
            CloudBikeConfig(confId = "63", confName = "T 动力"),
        ),
        values = emptyMap(),
    )

    private fun sampleStatus(
        tcsWorkMode: Int? = null,
        seatHeatSwitchStatus: Int? = null,
        templeSettingRaw: Int? = null,
        sleepModeRaw: Int? = null,
        customFunctionShort: Int? = null,
        customFunctionLong: Int? = null,
    ): TtpInfo {
        val bytes = SAMPLE_TTP_HEX.hexToBytes()!!
        tcsWorkMode?.let { bytes[0xAA] = it.toByte() }
        seatHeatSwitchStatus?.let { bytes[0x75] = it.toByte() }
        templeSettingRaw?.let { bytes[0x6F] = it.toByte() }
        sleepModeRaw?.let { bytes[0x88] = it.toByte() }
        customFunctionLong?.let {
            bytes[0x6C] = ((bytes[0x6C].toInt() and 0xF0) or (it and 0x0F)).toByte()
        }
        customFunctionShort?.let {
            bytes[0x6C] = ((bytes[0x6C].toInt() and 0x0F) or ((it and 0x0F) shl 4)).toByte()
        }
        return (VehicleNotifyDispatcher.dispatch(bytes) as VehicleNotify.Ttp).info
    }

    @Test
    fun bleWriteRemainsUnconfirmedUntilNewMatchingTtp() = runBlocking {
        val item = CarSettingItem(
            id = "cloud:37",
            configId = "37",
            title = "TCS",
            summary = "",
            classification = CarSettingClassification.SECURITY,
            channel = CarSettingChannel.BLE,
            kind = CarSettingKind.Toggle(false),
        )
        val writer = object : CarSettingWriter {
            override suspend fun write(target: CarSettingItem): Boolean =
                writeWithResult(target).success

            override suspend fun writeWithResult(item: CarSettingItem): CarSettingWriteResult =
                CarSettingWriteResult(
                    success = true,
                    channel = CarSettingWriteChannel.BLE,
                    confirmationStatus = CarSettingConfirmationStatus.LOCAL_UPDATED,
                )
        }
        val controller = CarSettingsController(
            initialItems = listOf(item),
            writer = writer,
            vinProvider = { "VIN" },
            debounceMs = 0L,
        )

        assertTrue(controller.update("cloud:37") { CarSettingKind.Toggle(true) })
        assertEquals(
            CarSettingConfirmationStatus.LOCAL_UPDATED,
            controller.state.value.confirmationStatuses["cloud:37"],
        )

        controller.updateVehicleStatus("VIN", sampleStatus(tcsWorkMode = 1))
        assertEquals(
            CarSettingConfirmationStatus.LOCAL_UPDATED,
            controller.state.value.confirmationStatuses["cloud:37"],
        )

        controller.updateVehicleStatus("VIN", sampleStatus(tcsWorkMode = 0))
        assertEquals(
            CarSettingConfirmationStatus.VEHICLE_CONFIRMED,
            controller.state.value.confirmationStatuses["cloud:37"],
        )
    }

    @Test
    fun customKeyFunctionsReadBackFromCustomFunctionBits() {
        val options = listOf(
            "ABS 防抱死",
            "TCS 车身稳定",
            "雷达功能",
            "行车记录仪紧急录制",
            "行车记录仪抓拍",
            "长续航/性能模式切换",
            "氛围灯功能",
            "坐垫加热功能",
            "AI 语音助手唤醒",
            "无定义",
        )
        val items = listOf(
            CarSettingItem(
                id = "cloud:1005:short",
                configId = "1005",
                title = "短按功能",
                summary = "",
                classification = CarSettingClassification.RIDE_CONTROL,
                channel = CarSettingChannel.BLE,
                kind = CarSettingKind.Choice(0, options, valueKnown = false),
            ),
            CarSettingItem(
                id = "cloud:1005:long",
                configId = "1005",
                title = "长按功能",
                summary = "",
                classification = CarSettingClassification.RIDE_CONTROL,
                channel = CarSettingChannel.BLE,
                kind = CarSettingKind.Choice(0, options, valueKnown = false),
            ),
        )
        val controller = CarSettingsController(items, writer = null)

        controller.updateVehicleStatus(
            "VIN",
            sampleStatus(customFunctionShort = 5, customFunctionLong = 14),
        )

        val short = controller.state.value.items
            .first { it.id == "cloud:1005:short" }
            .kind as CarSettingKind.Choice
        val long = controller.state.value.items
            .first { it.id == "cloud:1005:long" }
            .kind as CarSettingKind.Choice
        assertEquals(5, short.selectedIndex)
        assertEquals(9, long.selectedIndex)
        assertTrue(short.valueKnown)
        assertTrue(long.valueKnown)
    }
    @Test
    fun writeResultDefaultsDoNotClaimConfirmation() {
        assertEquals(
            CarSettingConfirmationStatus.LOCAL_UPDATED,
            CarSettingWriteResult(
                success = true,
                channel = CarSettingWriteChannel.BLE,
            ).confirmationStatus,
        )
        assertEquals(
            CarSettingConfirmationStatus.CLOUD_ACCEPTED,
            CarSettingWriteResult(
                success = true,
                channel = CarSettingWriteChannel.CLOUD,
            ).confirmationStatus,
        )
        assertEquals(
            CarSettingConfirmationStatus.UNKNOWN,
            CarSettingWriteResult(success = true).confirmationStatus,
        )
        assertEquals(
            CarSettingConfirmationStatus.FAILED,
            CarSettingWriteResult(success = false).confirmationStatus,
        )
    }
    @Test
    fun success_updatesOptimisticallyAndClearsPending() = runBlocking {
        val writer = FakeWriter(true)
        val controller = CarSettingsController(items(), writer)

        val ok = controller.update("security.auto_lock") { kind ->
            (kind as CarSettingKind.Toggle).copy(checked = false)
        }

        assertTrue(ok)
        val item = controller.state.value.items.first { it.id == "security.auto_lock" }
        assertEquals(false, (item.kind as CarSettingKind.Toggle).checked)
        assertTrue(controller.state.value.pendingIds.isEmpty())
        assertNotNull(writer.last)
    }

    @Test
    fun failure_rollsBackAndReportsMessage() = runBlocking {
        val controller = CarSettingsController(items(), FakeWriter(false))
        val before = controller.state.value.items.first { it.id == "ride.cruise" }

        val ok = controller.update("ride.cruise") { kind ->
            (kind as CarSettingKind.Toggle).copy(checked = true)
        }

        assertFalse(ok)
        val after = controller.state.value.items.first { it.id == "ride.cruise" }
        assertEquals(before.kind, after.kind)
        assertNotNull(controller.state.value.message)
        assertTrue(controller.state.value.pendingIds.isEmpty())
    }

    @Test
    fun unavailableWriter_rejectsWithoutChangingValue() = runBlocking {
        val controller = CarSettingsController(items(), writer = null)
        val before = controller.state.value.items.first { it.id == "battery.charge_limit" }

        val ok = controller.update("battery.charge_limit") { kind ->
            (kind as CarSettingKind.Slider).copy(value = 80f)
        }

        assertFalse(ok)
        val after = controller.state.value.items.first { it.id == "battery.charge_limit" }
        assertEquals(before.kind, after.kind)
        assertEquals("车辆设置通道未接入", controller.state.value.message)
    }

    @Test
    fun smaliCommandMapping_usesConfirmedIdsAndParameters() {
        val items = items().associateBy { it.id }
        assertEquals(
            VehicleSettingCommand("LEAVE_THE_CAR_AUTO_LOCK_ON", 0x53),
            VehicleSettingCommands.forItem(items.getValue("security.auto_lock")),
        )
        assertEquals(
            VehicleSettingCommand("WARING_SOUND_CLOSE", 0x63),
            VehicleSettingCommands.forItem(
                items.getValue("light.find_sound").copy(
                    kind = CarSettingKind.Toggle(false),
                ),
            ),
        )
        assertEquals(
            VehicleSettingCommand("BRAKE_RECOVER_LEVEL", 0x3D, parameter = 3),
            VehicleSettingCommands.forItem(
                items.getValue("ride.recover").copy(
                    kind = CarSettingKind.Choice(2, listOf("弱", "中", "强")),
                ),
            ),
        )
        assertEquals(
            VehicleSettingCommand("BRIGHTNESS_ADJUST_VALUE", 0x134, parameter = 80),
            VehicleSettingCommands.forItem(items.getValue("meter.brightness")),
        )
        assertEquals(
            VehicleSettingCommand("BRIGHTNESS_ADJUST_SWITCH", 0x135, parameter = 1),
            VehicleSettingCommands.forItem(items.getValue("meter.auto_brightness")),
        )
    }

    @Test
    fun frameEncoder_buildsVehicleFramesForMappedCommands() {
        val encoded = VehicleSettingFrameEncoder.encode(
            VehicleSettingCommand("LEAVE_THE_CAR_AUTO_LOCK_ON", 0x53),
        )
        assertNotNull(encoded)
        val hex = encoded!!.joinToString("") {
            (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase()
        }
        assertTrue(hex.startsWith("59443E"))
        assertTrue(hex.endsWith("4B4A"))
        assertTrue(
            hex.contains(
                "01000101C8" +
                    "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFDFFFFFFFFFFFFFFF",
            ),
        )

        val brightness = VehicleSettingFrameEncoder.encode(
            VehicleSettingCommand("BRIGHTNESS_ADJUST_VALUE", 0x134, parameter = 80),
        )
        assertNotNull(brightness)
    }

    @Test
    fun latestVehicleStatus_appliesAfterCloudItemsLoad() = runBlocking {
        val controller = CarSettingsController(
            initialItems = emptyList(),
            writer = FakeWriter(true),
            gateway = FakeGateway(statusCloud()),
            vinProvider = { "VIN" },
        )

        controller.updateVehicleStatus(sampleStatus())
        assertTrue(controller.state.value.items.isEmpty())
        assertTrue(controller.refresh())

        val items = controller.state.value.items.associateBy { it.id }
        assertEquals(
            false,
            (items.getValue("cloud:37").kind as CarSettingKind.Toggle).checked,
        )
        assertEquals(
            true,
            (items.getValue("cloud:38").kind as CarSettingKind.Toggle).checked,
        )
        assertEquals(
            true,
            (items.getValue("cloud:2").kind as CarSettingKind.Toggle).checked,
        )
        assertEquals(
            1,
            (items.getValue("cloud:31").kind as CarSettingKind.Choice).selectedIndex,
        )
        assertEquals(
            true,
            (items.getValue("cloud:63").kind as CarSettingKind.Toggle).checked,
        )
        assertEquals(
            false,
            (items.getValue("cloud:62").kind as CarSettingKind.Toggle).checked,
        )
        assertEquals(
            350f,
            (items.getValue("cloud:47").kind as CarSettingKind.Slider).value,
        )
    }

    @Test
    fun tcsStatusUsesZeroIsOnMapping() {
        val controller = CarSettingsController(
            initialItems = listOf(
                CarSettingItem(
                    id = "cloud:37",
                    configId = "37",
                    title = "TCS",
                    summary = "",
                    classification = CarSettingClassification.SECURITY,
                    channel = CarSettingChannel.BLE,
                    kind = CarSettingKind.Toggle(false),
                ),
            ),
            writer = FakeWriter(true),
        )

        controller.updateVehicleStatus(sampleStatus(tcsWorkMode = 1))

        assertEquals(
            false,
            (controller.state.value.items.single().kind as CarSettingKind.Toggle).checked,
        )

        controller.updateVehicleStatus(sampleStatus(tcsWorkMode = 0))

        assertEquals(
            true,
            (controller.state.value.items.single().kind as CarSettingKind.Toggle).checked,
        )
    }

    @Test
    fun invalidStatusDoesNotOverwriteKnownToggleValue() {
        val controller = CarSettingsController(
            initialItems = listOf(
                CarSettingItem(
                    id = "cloud:37",
                    configId = "37",
                    title = "TCS",
                    summary = "",
                    classification = CarSettingClassification.SECURITY,
                    channel = CarSettingChannel.BLE,
                    kind = CarSettingKind.Toggle(true),
                ),
                CarSettingItem(
                    id = "cloud:62",
                    configId = "62",
                    title = "座椅加热",
                    summary = "",
                    classification = CarSettingClassification.BATTERY,
                    channel = CarSettingChannel.BLE,
                    kind = CarSettingKind.Toggle(false),
                ),
            ),
            writer = FakeWriter(true),
        )

        controller.updateVehicleStatus(
            sampleStatus(tcsWorkMode = 0xFF, seatHeatSwitchStatus = 0xFF),
        )

        val items = controller.state.value.items.associateBy { it.id }
        assertEquals(
            true,
            (items.getValue("cloud:37").kind as CarSettingKind.Toggle).checked,
        )
        assertEquals(
            false,
            (items.getValue("cloud:62").kind as CarSettingKind.Toggle).checked,
        )
    }

    @Test
    fun templeAndSleepStatusAreReadBack() {
        val controller = CarSettingsController(
            initialItems = listOf(
                CarSettingItem(
                    id = "cloud:53",
                    configId = "53",
                    title = "边撑开机",
                    summary = "",
                    classification = CarSettingClassification.POWER,
                    channel = CarSettingChannel.BLE,
                    kind = CarSettingKind.Toggle(false),
                ),
                CarSettingItem(
                    id = "cloud:77",
                    configId = "77",
                    title = "休眠模式",
                    summary = "",
                    classification = CarSettingClassification.SECURITY,
                    channel = CarSettingChannel.BLE,
                    kind = CarSettingKind.Toggle(false),
                ),
            ),
            writer = FakeWriter(true),
        )

        controller.updateVehicleStatus(
            sampleStatus(
                // 0x6F bit2-3 = 1
                templeSettingRaw = 0x04,
                // 0x88 bit4-5 = 1
                sleepModeRaw = 0x10,
            ),
        )

        val items = controller.state.value.items.associateBy { it.id }
        assertEquals(
            true,
            (items.getValue("cloud:53").kind as CarSettingKind.Toggle).checked,
        )
        assertEquals(
            true,
            (items.getValue("cloud:77").kind as CarSettingKind.Toggle).checked,
        )
    }

    @Test
    fun oldVinStatusCannotApplyToCurrentVin() = runBlocking {
        val controller = CarSettingsController(
            initialItems = listOf(tcsItem()),
            writer = FakeWriter(true),
            gateway = object : CarSettingsGateway {
                override suspend fun load(vin: String): CloudCarSettings? = CloudCarSettings(
                    configs = listOf(CloudBikeConfig(confId = "37", confName = "TCS")),
                    values = emptyMap(),
                )
            },
            vinProvider = { "VIN-B" },
        )
        assertTrue(controller.refresh())

        controller.updateVehicleStatus("VIN-A", sampleStatus(tcsWorkMode = 0xFF))

        val item = controller.state.value.items.first { it.id == "cloud:37" }
        assertEquals(false, (item.kind as CarSettingKind.Toggle).checked)
    }

    @Test
    fun cachedStatusIsClearedWhenVinChanges() = runBlocking {
        var currentVin = "VIN-A"
        val controller = CarSettingsController(
            initialItems = listOf(tcsItem()),
            writer = FakeWriter(true),
            gateway = object : CarSettingsGateway {
                override suspend fun load(vin: String): CloudCarSettings? = CloudCarSettings(
                    configs = listOf(CloudBikeConfig(confId = "37", confName = "TCS")),
                    values = emptyMap(),
                )
            },
            vinProvider = { currentVin },
        )
        controller.updateVehicleStatus("VIN-A", sampleStatus(tcsWorkMode = 0xFF))
        assertEquals(
            true,
            (controller.state.value.items.first().kind as CarSettingKind.Toggle).checked,
        )

        currentVin = "VIN-B"
        assertTrue(controller.refresh())

        val item = controller.state.value.items.first { it.id == "cloud:37" }
        assertEquals(false, (item.kind as CarSettingKind.Toggle).checked)
    }

    private fun tcsItem() = CarSettingItem(
        id = "cloud:37",
        configId = "37",
        title = "TCS",
        summary = "",
        classification = CarSettingClassification.SECURITY,
        channel = CarSettingChannel.BLE,
        kind = CarSettingKind.Toggle(false),
    )

    @Test
    fun bleFallbackSuccessDoesNotPollCloudStatus() = runBlocking {
        var statusChecks = 0
        val gateway = object : CarSettingsGateway {
            override suspend fun load(vin: String): CloudCarSettings? = null
            override suspend fun update(
                vin: String,
                update: CloudCarSettingUpdate,
            ): Boolean = false

            override suspend fun checkStatus(configId: String): Boolean? {
                statusChecks += 1
                return false
            }
        }
        val writer = object : CarSettingWriter {
            override suspend fun write(item: CarSettingItem): Boolean = true
            override suspend fun writeWithResult(item: CarSettingItem): CarSettingWriteResult =
                CarSettingWriteResult(
                    success = true,
                    channel = CarSettingWriteChannel.BLE,
                )
        }
        val controller = CarSettingsController(
            initialItems = listOf(
                CarSettingItem(
                    id = "cloud:9",
                    configId = "9",
                    title = "信息提醒",
                    summary = "",
                    classification = CarSettingClassification.SECURITY,
                    channel = CarSettingChannel.BLE,
                    kind = CarSettingKind.Toggle(true),
                ),
            ),
            writer = writer,
            gateway = gateway,
            vinProvider = { "VIN" },
            debounceMs = 0,
        )

        assertTrue(
            controller.update("cloud:9") { kind ->
                (kind as CarSettingKind.Toggle).copy(checked = false)
            },
        )
        assertEquals(0, statusChecks)
    }

    private companion object {
        const val SAMPLE_TTP_HEX =
            "59442AAB2F024C06C079FE00FE00B2FEFFFFFFFFFFD5D0FFFFFFCD076BE1FF29" +
                "FFFF003C824DE5F9FFFFFFFFFFFF7FFFFFF7FD57FCFCFFFFFFFFFFFFFFFFFFFF" +
                "FFEF0FFFFF671B00003BFF0000FCEBE3020385F0A19C0177FFFFFFFFFFFFFFFF" +
                "FFFFFFFFFFFFFFFFFFFFFFFF1E7F57F477071E01FFFF5F26FFF8FFFFFFFFFFFC" +
                "FFFFFFFF0BFFFFFFC242DF38FFFFFFFF7FFFFF3830363533FF2E18FFFF4505FF" +
                "FFFFFFFFFFFFFFFFFFFFF8D7EFFFF31B4B4A"
    }
}
