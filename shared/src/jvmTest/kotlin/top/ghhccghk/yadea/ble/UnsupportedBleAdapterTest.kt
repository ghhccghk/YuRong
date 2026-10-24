package top.ghhccghk.yadea.ble

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnsupportedBleAdapterTest {
    private val adapter = UnsupportedBleAdapter("test platform")

    @Test
    fun unsupportedAdapter_doesNotClaimAvailabilityOrCapabilities() {
        assertFalse(adapter.isAvailable)
        assertFalse(adapter.isEnabled)
        assertEquals(BlePermissionState.Unknown, adapter.permissionState)
        assertFalse(adapter.capabilities.canScan)
        assertFalse(adapter.capabilities.canRequestMtu)
    }

    @Test
    fun scanIsANoOpAndNeverReportsScanning() {
        adapter.startScan()
        assertFalse(adapter.isScanning.value)
        assertTrue(adapter.discoveredDevices.value.isEmpty())
    }

    @Test
    fun connectFailsExplicitlyAndRemainsDisconnected() {
        runBlocking {
            val error = runCatching { adapter.connect("opaque-id") }.exceptionOrNull()
            assertTrue(error is BleUnsupportedException)
            assertEquals(BleErrorCode.Unsupported, (error as BleUnsupportedException).code)
            assertEquals(BleConnectionState.Disconnected, adapter.connectionState.value)
        }
    }

    @Test
    fun gattOperationsFailExplicitly() {
        runBlocking {
            assertTrue(runCatching { adapter.discoverServices() }.exceptionOrNull() is BleUnsupportedException)
            assertTrue(runCatching { adapter.readCharacteristic("service", "characteristic") }.exceptionOrNull() is BleUnsupportedException)
            assertTrue(runCatching { adapter.writeCharacteristic("service", "characteristic", byteArrayOf(1)) }.exceptionOrNull() is BleUnsupportedException)
            assertTrue(runCatching { adapter.setNotification("service", "characteristic", true) }.exceptionOrNull() is BleUnsupportedException)
            assertTrue(runCatching { adapter.requestMtu(517) }.exceptionOrNull() is BleUnsupportedException)
        }
    }

    @Test
    fun androidCapabilitiesRemainExplicitAndStable() {
        val capabilities = BleCapabilities.Android
        assertTrue(capabilities.canScan)
        assertTrue(capabilities.canRequestMtu)
        assertTrue(capabilities.canProvideRawAdvertisement)
        assertTrue(capabilities.canProvideMacAddress)
        assertFalse(capabilities.supportsStateRestoration)
    }
}
