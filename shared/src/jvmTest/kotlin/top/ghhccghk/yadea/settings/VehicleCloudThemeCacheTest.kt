package top.ghhccghk.yadea.settings

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import top.ghhccghk.yadea.vehicle.VehicleCloudTheme

class VehicleCloudThemeCacheTest {
    @Test
    fun cacheIsScopedToVinAndRoundTripsCustomFlag() = runBlocking {
        val store = InMemoryPreferenceStore()
        val preferences = AppPreferences(store)
        val theme = VehicleCloudTheme(
            vin = "VIN-1",
            loveCarImageUrl = "https://cdn/love-car.png",
            themeType = 0,
            hasBannerVehicleBgImg = true,
            gameThemeColorType = 3,
        )

        preferences.writeVehicleCloudTheme(theme)

        assertEquals(theme, preferences.readVehicleCloudTheme("VIN-1"))
        assertNull(preferences.readVehicleCloudTheme("VIN-2"))
        assertEquals(AppPreferenceKeys.SCHEMA_VERSION, store.read(AppPreferenceKeys.schemaVersion))
    }

    @Test
    fun clearingThemeDoesNotRemoveVehicleConnectionSettings() = runBlocking {
        val store = InMemoryPreferenceStore()
        val preferences = AppPreferences(store)
        preferences.writeLastVehicle("AA:BB", "车辆", "VIN-1")
        preferences.writeVehicleCloudTheme(VehicleCloudTheme(vin = "VIN-1"))

        preferences.clearVehicleCloudTheme()

        assertNull(preferences.readVehicleCloudTheme("VIN-1"))
        assertEquals("AA:BB", preferences.readVehicleConnection().lastMac)
    }
}
