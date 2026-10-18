package top.ghhccghk.yadea.ota

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtaFlowTest {
    @Test
    fun flow_coversCheckInstallAndFailure() {
        var state = OtaState(currentVersion = "1.0.0", stage = OtaStage.Idle)
        state = OtaFlow.reduce(state, OtaEvent.CheckStarted)
        assertEquals(OtaStage.Checking, state.stage)
        state = OtaFlow.reduce(state, OtaEvent.UpdateFound(OtaPackage("1.1.0")))
        assertEquals(OtaStage.Available, state.stage)
        state = OtaFlow.reduce(state, OtaEvent.InstallStarted)
        assertEquals(OtaStage.Downloading, state.stage)
        state = OtaFlow.reduce(state, OtaEvent.Progress(0.5f, OtaStage.Installing))
        assertEquals(OtaStage.Installing, state.stage)
        assertEquals(0.5f, state.progress)
        state = OtaFlow.reduce(state, OtaEvent.Failed("boom"))
        assertEquals(OtaStage.Failed, state.stage)
        assertEquals("boom", state.message)
    }

    @Test
    fun controller_checksAndInstallsWithGateway() = runBlocking {
        val gateway = object : OtaGateway {
            override suspend fun checkForUpdate(currentVersion: String) = OtaPackage("2.0.0")
            override suspend fun install(
                packageInfo: OtaPackage,
                onProgress: suspend (Float, OtaStage) -> Unit,
            ): Boolean {
                onProgress(0.4f, OtaStage.Downloading)
                onProgress(0.8f, OtaStage.Installing)
                return true
            }
        }
        val controller = OtaController(gateway, "1.0.0", CoroutineScope(Dispatchers.Unconfined))

        controller.checkForUpdate()
        assertEquals(OtaStage.Available, controller.state.value.stage)
        controller.install()
        assertEquals(OtaStage.Completed, controller.state.value.stage)
        assertEquals(1f, controller.state.value.progress)
        controller.dispose()
    }

    @Test
    fun unavailableGateway_cannotCheckOrInstall() {
        val controller = OtaController(null, "1.0.0", CoroutineScope(Dispatchers.Unconfined))
        assertFalse(controller.state.value.canCheck)
        assertFalse(controller.state.value.canInstall)
        assertEquals(OtaStage.Unavailable, controller.state.value.stage)
        controller.dispose()
    }
}
