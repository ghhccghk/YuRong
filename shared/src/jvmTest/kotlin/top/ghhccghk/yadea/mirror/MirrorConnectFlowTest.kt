package top.ghhccghk.yadea.mirror

import org.junit.Assert.assertEquals
import org.junit.Test

class MirrorConnectFlowTest {

    @Test
    fun flowReachesRunning() {
        var state = MirrorConnectState()
        state = MirrorConnectFlow.reduce(state, MirrorConnectEvent.Start)
        assertEquals(MirrorConnectStage.SendingBle, state.stage)
        state = MirrorConnectFlow.reduce(state, MirrorConnectEvent.BleCommandSent)
        assertEquals(MirrorConnectStage.WaitingWifi, state.stage)
        state = MirrorConnectFlow.reduce(state, MirrorConnectEvent.WifiReady)
        assertEquals(MirrorConnectStage.DiscoveringP2p, state.stage)
        state = MirrorConnectFlow.reduce(
            state,
            MirrorConnectEvent.P2pSelected("YD-TFT", "A4:43:38:12:34:56"),
        )
        assertEquals(MirrorConnectStage.ConnectingP2p, state.stage)
        state = MirrorConnectFlow.reduce(
            state,
            MirrorConnectEvent.TcpConnecting("192.168.49.1"),
        )
        assertEquals(MirrorConnectStage.ConnectingTcp, state.stage)
        state = MirrorConnectFlow.reduce(state, MirrorConnectEvent.TcpConnected)
        assertEquals(MirrorConnectStage.Running, state.stage)
    }

    @Test
    fun bleMacConvertsToP2pMac() {
        assertEquals(
            "A4:43:38:12:34:56",
            toP2pMacAddress("A4:C1:38:12:34:56"),
        )
    }

    @Test
    fun wifiTimeoutMarksFailed() {
        val state = MirrorConnectFlow.reduce(
            MirrorConnectState(stage = MirrorConnectStage.WaitingWifi),
            MirrorConnectEvent.WifiTimeout,
        )
        assertEquals(MirrorConnectStage.Failed, state.stage)
    }
}
