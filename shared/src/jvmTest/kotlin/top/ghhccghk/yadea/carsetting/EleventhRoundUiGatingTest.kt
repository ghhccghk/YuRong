package top.ghhccghk.yadea.carsetting

import org.junit.Assert.*
import org.junit.Test
import top.ghhccghk.yadea.ui.adaptivePushUiDisabledReason

/** Pure UI policy with in-memory observations, no transport or account. */
class EleventhRoundUiGatingTest {
    private fun item(configId: String, suffix: String = "") = CloudCarSettingMapper.toItems(
        CloudCarSettings(listOf(CloudBikeConfig(confId = configId)), emptyMap()),
    ).single { it.id == "cloud:$configId$suffix" }

    private fun observed(configId: String, on: Boolean, live: Boolean = true) = CarSettingsState(
        vin = "VIN-A",
        valueStates = mapOf("cloud:$configId" to CarSettingValueState(
            vehicleValue = VehicleSettingValue.Known(CarSettingKind.Toggle(on), "VIN-A", 1, live = live),
            readbackState = SettingReadbackState.VALID,
        )),
    )

    @Test fun bothVariantsRequireConnectionAndKnownPowerForEveryControl() {
        for (configId in listOf("64", "89")) for (suffix in listOf("", ":adaptive", ":level")) {
            val item = item(configId, suffix)
            val state = observed(configId, true)
            for (power in listOf(null, false, true)) {
                assertNotNull(adaptivePushUiDisabledReason(item, state, false, power))
            }
            assertNotNull(adaptivePushUiDisabledReason(item, state, true, null))
            assertNotNull(adaptivePushUiDisabledReason(item, state, true, false))
            assertNull(adaptivePushUiDisabledReason(item, state, true, true))
        }
    }

    @Test fun unknownMainCanBeExplicitlyEditedButCannotUnlockItsChildren() {
        for (configId in listOf("64", "89")) {
            val state = CarSettingsState(vin = "VIN-A")
            assertNull(adaptivePushUiDisabledReason(item(configId), state, true, true))
            for (suffix in listOf(":adaptive", ":level")) {
                assertNotNull(adaptivePushUiDisabledReason(item(configId, suffix), state, true, true))
            }
        }
    }

    @Test fun draftAndSubmittedMainCannotReplaceObservedMain() {
        for (configId in listOf("64", "89")) {
            val state = observed(configId, false)
            val main = state.valueState("cloud:$configId").copy(
                draftValue = CarSettingKind.Toggle(true), submittedValue = CarSettingKind.Toggle(true),
                writeState = SettingWriteState.LOCAL_UPDATED,
            )
            val edited = state.copy(valueStates = state.valueStates + ("cloud:$configId" to main))
            assertNotNull(adaptivePushUiDisabledReason(item(configId, ":adaptive"), edited, true, true))
        }
    }

    @Test fun historicalInvalidAndWrongVinMainCannotUnlockChildren() {
        for (configId in listOf("64", "89")) {
            val state = observed(configId, true)
            val main = state.valueState("cloud:$configId")
            val known = main.vehicleValue as VehicleSettingValue.Known
            for (invalid in listOf(
                main.copy(vehicleValue = known.copy(live = false)),
                main.copy(vehicleValue = known.copy(vin = "VIN-B")),
                main.copy(readbackState = SettingReadbackState.MISSING_OR_INVALID),
            )) {
                val changed = state.copy(valueStates = mapOf("cloud:$configId" to invalid))
                assertNotNull(adaptivePushUiDisabledReason(item(configId, ":level"), changed, true, true))
            }
        }
    }

    @Test fun variant64MainNeverUnlocksVariant89AndViceVersa() {
        assertNotNull(adaptivePushUiDisabledReason(item("89", ":adaptive"), observed("64", true), true, true))
        assertNotNull(adaptivePushUiDisabledReason(item("64", ":level"), observed("89", true), true, true))
    }

    @Test fun parentSubmittingTemporarilyDisablesChildrenWithoutClearingObservation() {
        for (configId in listOf("64", "89")) {
            val state = observed(configId, true).copy(pendingIds = setOf("cloud:$configId"))
            assertNotNull(adaptivePushUiDisabledReason(item(configId, ":level"), state, true, true))
            assertNull(adaptivePushUiDisabledReason(item(configId, ":level"), state.copy(pendingIds = emptySet()), true, true))
            assertEquals(true, ((state.valueState("cloud:$configId").vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Toggle).checked)
        }
    }

    @Test fun disconnectAndPowerLossImmediatelyCloseAnAlreadyOpenEditorPolicy() {
        val item = item("89", ":level")
        val state = observed("89", true)
        assertNull(adaptivePushUiDisabledReason(item, state, true, true))
        assertNotNull(adaptivePushUiDisabledReason(item, state, false, true))
        assertNotNull(adaptivePushUiDisabledReason(item, state, true, false))
    }

    @Test fun alarmVolumeAndOtherProtectedSettingsKeepTheirExistingPolicy() {
        for (configId in listOf("14", "20", "37", "38", "51", "001")) {
            assertNull(adaptivePushUiDisabledReason(item(configId), CarSettingsState(), false, null))
        }
    }
}
