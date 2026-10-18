package top.ghhccghk.yadea.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectFlowTest {

    private class FakeClock(var now: Long = 0L) : Clock {
        override fun nowMs(): Long = now

        fun advance(ms: Long) {
            now += ms
        }
    }

    private val mac = "AA:BB:CC:DD:EE:FF"

    private fun connectedState(clock: FakeClock): ConnectState {
        val scanned = ConnectFlow.reduce(
            state = ConnectState(),
            event = ConnectEvent.ScanHit(mac),
            nowMs = clock.nowMs(),
        )
        val gatt = ConnectFlow.reduce(
            state = scanned.state,
            event = ConnectEvent.GattConnected(mac, status = 0),
            nowMs = clock.nowMs(),
        )
        val services = ConnectFlow.reduce(
            state = gatt.state,
            event = ConnectEvent.ServicesDiscovered(mac, status = 0),
            nowMs = clock.nowMs(),
        )
        return ConnectFlow.reduce(
            state = services.state,
            event = ConnectEvent.PairingSucceeded(mac),
            nowMs = clock.nowMs(),
        ).state
    }

    @Test
    fun scanHit_entersConnectingAndStartsConnect() {
        val clock = FakeClock(1_000)
        val result = ConnectFlow.reduce(ConnectState(), ConnectEvent.ScanHit(mac), clock.nowMs())

        assertEquals(LinkState.CONNECTING, result.state.link)
        assertEquals(mac, result.state.targetMac)
        assertEquals(PublicState.CONNECTING, result.public)
        assertEquals(listOf(ConnectAction.StartConnect), result.actions)
        assertEquals(1_000L, result.state.connectStartedAtMs)
    }

    @Test
    fun duplicateScanHit_isDroppedWithoutPublicState() {
        val first = ConnectFlow.reduce(ConnectState(), ConnectEvent.ScanHit(mac), 0)
        val second = ConnectFlow.reduce(first.state, ConnectEvent.ScanHit(mac), 10)

        assertEquals(first.state, second.state)
        assertNull(second.public)
        assertTrue(second.actions.isEmpty())
    }

    @Test
    fun gattConnected_delaysFiveHundredThenDiscovers() {
        val state = ConnectState(LinkState.CONNECTING, mac)
        val result = ConnectFlow.reduce(
            state,
            ConnectEvent.GattConnected(mac, status = 0),
            0,
        )

        assertEquals(LinkState.CONNECTING, result.state.link)
        assertNull(result.public)
        assertEquals(listOf(ConnectAction.Delay500ThenDiscover), result.actions)
        assertEquals(500L, VehicleTimeouts.POST_CONNECT_DELAY_MS)
    }

    @Test
    fun gattConnectedError133_refreshesCache() {
        val state = ConnectState(LinkState.CONNECTING, mac)
        val result = ConnectFlow.reduce(
            state,
            ConnectEvent.GattConnected(mac, status = 0x85),
            0,
        )

        assertEquals(LinkState.FAILURE, result.state.link)
        assertEquals(DisconnectType.CONNECT_FAILED, result.disconnectType)
        assertEquals(VehicleException.GATT, result.exception)
        assertEquals(listOf(ConnectAction.RefreshGattCache), result.actions)
    }

    @Test
    fun servicesDiscovered_startsPairingAndOnlyThenReportsConnected() {
        val state = ConnectState(LinkState.CONNECTING, mac)
        val discovered = ConnectFlow.reduce(
            state,
            ConnectEvent.ServicesDiscovered(mac, status = 0),
            0,
        )
        assertEquals(LinkState.CONNECTING, discovered.state.link)
        assertNull(discovered.public)
        assertEquals(listOf(ConnectAction.StartPairing), discovered.actions)

        val paired = ConnectFlow.reduce(
            discovered.state,
            ConnectEvent.PairingSucceeded(mac),
            100,
        )
        assertEquals(LinkState.CONNECTED, paired.state.link)
        assertEquals(PublicState.CONNECTED, paired.public)
        assertEquals(listOf(ConnectAction.RunInitTasks), paired.actions)
    }

    @Test
    fun servicesDiscoveredFailure_doesNotReconnect() {
        val state = ConnectState(LinkState.CONNECTING, mac)
        val result = ConnectFlow.reduce(
            state,
            ConnectEvent.ServicesDiscovered(mac, status = 1),
            0,
        )

        assertEquals(LinkState.FAILURE, result.state.link)
        assertEquals(PublicState.DISCONNECTED, result.public)
        assertTrue(result.actions.isEmpty())
    }

    @Test
    fun connectTimeout_isDrivenByFakeClock() {
        val clock = FakeClock(2_000)
        val state = ConnectFlow.reduce(
            ConnectState(),
            ConnectEvent.ScanHit(mac),
            clock.nowMs(),
        ).state

        clock.advance(VehicleTimeouts.GATT_CONNECT_MS - 1)
        assertNull(ConnectFlow.pollTimeout(state, clock.nowMs()))

        clock.advance(1)
        val result = ConnectFlow.pollTimeout(state, clock.nowMs())!!
        assertEquals(LinkState.FAILURE, result.state.link)
        assertEquals(DisconnectType.TIMEOUT_15S, result.disconnectType)
        assertEquals(VehicleException.TIMEOUT, result.exception)
        assertTrue(result.actions.isEmpty())
    }

    @Test
    fun scanTimeout_stopsScanAndCloses() {
        val clock = FakeClock(0)
        val state = ConnectState(LinkState.CONNECTING, mac)
        clock.advance(VehicleTimeouts.SCAN_MS)
        assertTrue(ConnectFlow.scanTimedOut(0, clock.nowMs()))

        val result = ConnectFlow.reduce(state, ConnectEvent.ScanTimeout, clock.nowMs())
        assertEquals(DisconnectType.TIMEOUT_15S, result.disconnectType)
        assertEquals(listOf(ConnectAction.StopScanAndClose), result.actions)
    }

    @Test
    fun watchdogTimeout_stopsScanAndCloses() {
        val state = ConnectState(LinkState.CONNECTING, mac)
        val result = ConnectFlow.reduce(state, ConnectEvent.WatchdogTimeout, 0)

        assertEquals(LinkState.FAILURE, result.state.link)
        assertEquals(listOf(ConnectAction.StopScanAndClose), result.actions)
    }

    @Test
    fun disconnectWhileConnecting_reconnectsOnceAfterFiveSeconds() {
        val state = ConnectState(LinkState.CONNECTING, mac, reconnectAttempts = 0)
        val first = ConnectFlow.reduce(
            state,
            ConnectEvent.GattDisconnected(mac, status = 8),
            0,
        )
        assertEquals(LinkState.FAILURE, first.state.link)
        assertEquals(1, first.state.reconnectAttempts)
        assertEquals(
            listOf(ConnectAction.ScheduleReconnect(VehicleTimeouts.RECONNECT_DELAY_MS)),
            first.actions,
        )

        val retry = ConnectFlow.reduce(first.state, ConnectEvent.ReconnectRequested, 5_000)
        assertEquals(LinkState.CONNECTING, retry.state.link)
        assertEquals(PublicState.CONNECTING, retry.public)

        val second = ConnectFlow.reduce(
            retry.state,
            ConnectEvent.GattDisconnected(mac, status = 8),
            5_100,
        )
        assertEquals(LinkState.FAILURE, second.state.link)
        assertEquals(1, second.state.reconnectAttempts)
        assertTrue(second.actions.isEmpty())
    }

    @Test
    fun disconnect133WhileConnecting_refreshesCacheBeforeReconnect() {
        val state = ConnectState(LinkState.CONNECTING, mac)
        val result = ConnectFlow.reduce(
            state,
            ConnectEvent.GattDisconnected(mac, status = 0x85),
            0,
        )

        assertEquals(
            listOf(
                ConnectAction.RefreshGattCache,
                ConnectAction.ScheduleReconnect(VehicleTimeouts.RECONNECT_DELAY_MS),
            ),
            result.actions,
        )
    }

    @Test
    fun disconnectAfterConnected_doesNotReconnect() {
        val clock = FakeClock()
        val state = connectedState(clock)
        val result = ConnectFlow.reduce(
            state,
            ConnectEvent.GattDisconnected(mac, status = 8),
            clock.nowMs(),
        )

        assertEquals(LinkState.DISCONNECT, result.state.link)
        assertEquals(PublicState.DISCONNECTED, result.public)
        assertEquals(DisconnectType.TARGET_DISCONNECTED, result.disconnectType)
        assertTrue(result.actions.isEmpty())
    }

    @Test
    fun nonTargetMacEvents_areIgnored() {
        val state = ConnectState(LinkState.CONNECTING, mac)
        val result = ConnectFlow.reduce(
            state,
            ConnectEvent.ServicesDiscovered("11:22:33:44:55:66", status = 0),
            0,
        )

        assertEquals(state, result.state)
        assertNull(result.public)
        assertTrue(result.actions.isEmpty())
    }

    @Test
    fun pairingFailureCarriesDisconnectType() {
        val state = ConnectState(LinkState.CONNECTING, mac)
        val result = ConnectFlow.reduce(
            state,
            ConnectEvent.PairingFailed(mac, DisconnectType.PAIR_ERROR_9),
            0,
        )

        assertEquals(LinkState.FAILURE, result.state.link)
        assertEquals(DisconnectType.PAIR_ERROR_9, result.disconnectType)
        assertEquals(VehicleException.OTHER, result.exception)
    }

    @Test
    fun repeatedDisconnectWhileNotConnecting_isDropped() {
        val state = ConnectState(LinkState.FAILURE, mac, reconnectAttempts = 1)
        val result = ConnectFlow.reduce(
            state,
            ConnectEvent.GattDisconnected(mac, status = 8),
            0,
        )

        assertEquals(state, result.state)
        assertNull(result.public)
        assertTrue(result.actions.isEmpty())
    }
}
