package top.ghhccghk.yadea.carsetting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import top.ghhccghk.yadea.ui.CarSettingsUiResetKey
import top.ghhccghk.yadea.ui.carSettingsUiResetKey

class CarSettingsUiResetKeyTest {

    @Test
    fun keyIsStableForSameLoadSession() {
        val state = CarSettingsState(
            vin = "VIN-A",
            loadGeneration = 3,
            loadedFromCloud = true,
        )

        assertEquals(carSettingsUiResetKey(state), carSettingsUiResetKey(state.copy()))
    }

    @Test
    fun keyChangesForVinGenerationAndLoadedState() {
        val base = CarSettingsState(
            vin = "VIN-A",
            loadGeneration = 3,
            loadedFromCloud = true,
        )

        assertNotEquals(
            carSettingsUiResetKey(base),
            carSettingsUiResetKey(base.copy(vin = "VIN-B")),
        )
        assertNotEquals(
            carSettingsUiResetKey(base),
            carSettingsUiResetKey(base.copy(loadGeneration = 4)),
        )
        assertNotEquals(
            carSettingsUiResetKey(base),
            carSettingsUiResetKey(base.copy(loadedFromCloud = false)),
        )
    }

    @Test
    fun keyCarriesAllResetDimensions() {
        val key = carSettingsUiResetKey(
            CarSettingsState(
                vin = "VIN-A",
                loadGeneration = 7,
                loadedFromCloud = false,
            ),
        )

        assertEquals(CarSettingsUiResetKey("VIN-A", 7, false), key)
    }
}
