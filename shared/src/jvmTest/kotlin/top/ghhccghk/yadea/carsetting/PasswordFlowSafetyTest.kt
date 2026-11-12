package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import top.ghhccghk.yadea.vehicle.*

/** In-memory gateways, command sinks and a manually advanced monotonic clock only. */
class PasswordFlowSafetyTest {
    private class FakeGateway : VehiclePasswordGateway {
        val writes = mutableListOf<String>()
        var setResult: suspend () -> PasswordRequestResult = { PasswordRequestResult(true) }
        var verifyResult = PasswordVerifyResult(true)
        var smsResult = PasswordSmsVerifyResult(true, "credential")
        var read: suspend () -> PasswordEnableState = { PasswordEnableState(1, true) }
        var enableResult = PasswordRequestResult(true)
        override suspend fun getEnableState(vin: String) = read()
        override suspend fun exists(vin: String) = true
        override suspend fun set(vin: String, password: String): PasswordRequestResult {
            writes += vin
            return setResult()
        }
        override suspend fun reset(vin: String, password: String, validCode: String): PasswordRequestResult {
            writes += "$vin:reset"
            return setResult()
        }
        override suspend fun verify(vin: String, password: String) = verifyResult
        override suspend fun sendSmsCode(vin: String, mobile: String) = PasswordRequestResult(true)
        override suspend fun verifySmsCode(vin: String, mobile: String, smsCode: String) = smsResult
        override suspend fun updateEnable(vin: String, enabled: Boolean) = enableResult
    }

    private class Harness : AutoCloseable {
        var vin = "TEST-A"
        var now = 0L
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gateway = FakeGateway()
        val commands = mutableListOf<VehicleSettingCommand>()
        val timers = mutableListOf<CompletableDeferred<Unit>>()
        val timerDelays = mutableListOf<Long>()
        var send: suspend () -> Boolean = { true }
        val controller = VehiclePasswordController(
            gateway, { vin }, { "fake-mobile" },
            object : VehicleSettingCommandSink {
                override suspend fun send(command: VehicleSettingCommand): Boolean {
                    commands += command
                    return send()
                }
            }, scope, { now }, { delay -> timerDelays += delay; timers += CompletableDeferred<Unit>(); timers.last().await() },
        )
        val state get() = controller.state.value
        init { controller.onVinChanged(vin) }
        fun observation(sequence: Long, origin: VehicleStatusOrigin = VehicleStatusOrigin.LIVE_TTP, result: Int = 0, observedVin: String = vin) =
            VehicleStatusObservation(observedVin, TtpInfoParser.parse(ByteArray(256) { -1 }).let {
                it.copy(extended = it.extended.copy(passwordUnlockSettingResult = result))
            }, origin, sequence)
        fun submit() {
            controller.updateVehicleObservation(observation(10))
            controller.begin(PasswordFlow.Set)
            controller.submitPassword("1357")
        }
        override fun close() { scope.cancel() }
    }

    @Test fun freshResultWithinWindowConfirmsButAcceptanceDoesNot() = Harness().use { h ->
        h.submit()
        assertTrue(h.state.cloudAccepted)
        assertTrue(h.state.bleSent)
        assertFalse(h.state.success)
        assertEquals(0x82, h.commands.single().commandId)
        assertEquals(1357, h.commands.single().parameter)
        h.now = 1_999
        h.controller.updateVehicleObservation(h.observation(11))
        assertEquals(PasswordWriteState.VEHICLE_CONFIRMED, h.state.writeState)
        assertTrue(h.state.success)
        assertFalse(h.state.confirmationTimedOut)
    }

    @Test fun readbackWindowStartsAfterBleSendReturns() = Harness().use { h ->
        h.send = { h.now = 5_000; true }
        h.submit()
        assertEquals(listOf(2_000L), h.timerDelays)
        h.now = 6_999
        h.controller.updateVehicleObservation(h.observation(11))
        assertEquals(PasswordWriteState.VEHICLE_CONFIRMED, h.state.writeState)
    }

    @Test fun exactDeadlineRejectsLateSuccessEvenBeforeTimerResumes() = Harness().use { h ->
        h.submit()
        h.now = 2_000
        h.controller.updateVehicleObservation(h.observation(11))
        assertEquals(PasswordWriteState.LOCAL_UPDATED, h.state.writeState)
        assertTrue(h.state.confirmationTimedOut)
        assertTrue(h.state.cloudAccepted && h.state.bleSent)
        assertFalse(h.state.success)
        h.controller.updateVehicleObservation(h.observation(12))
        assertNotEquals(PasswordWriteState.VEHICLE_CONFIRMED, h.state.writeState)
    }

    @Test fun timeoutWithoutPacketRetainsAcceptanceFacts() = Harness().use { h ->
        h.submit()
        h.now = 2_000
        h.timers.single().complete(Unit)
        assertTrue(h.state.confirmationTimedOut)
        assertTrue(h.state.cloudAccepted && h.state.bleSent)
        assertFalse(h.state.success)
        assertFalse(h.state.busy)
    }

    @Test fun cacheWrongVinOldAndInvalidResultsCannotConfirm() = Harness().use { h ->
        h.submit()
        h.controller.updateVehicleObservation(h.observation(10))
        h.controller.updateVehicleObservation(h.observation(11, VehicleStatusOrigin.CACHED_TTP))
        h.controller.updateVehicleObservation(h.observation(12, observedVin = "TEST-B"))
        h.controller.updateVehicleObservation(h.observation(12, result = -1))
        assertEquals(PasswordWriteState.LOCAL_UPDATED, h.state.writeState)
        h.controller.updateVehicleObservation(h.observation(13))
        assertEquals(PasswordWriteState.VEHICLE_CONFIRMED, h.state.writeState)
    }

    @Test fun cachedPreWriteBaselineCannotEstablishConfirmation() = Harness().use { h ->
        h.controller.updateVehicleObservation(h.observation(10, VehicleStatusOrigin.CACHED_TTP))
        h.controller.begin(PasswordFlow.Set)
        h.controller.submitPassword("1357")
        h.controller.updateVehicleObservation(h.observation(11))
        assertEquals(PasswordWriteState.LOCAL_UPDATED, h.state.writeState)
    }

    @Test fun oldTimerCannotExpireNextAttempt() = Harness().use { h ->
        h.submit()
        val oldTimer = h.timers.single()
        h.now = 500
        h.controller.begin(PasswordFlow.Set)
        h.controller.submitPassword("1357")
        oldTimer.complete(Unit)
        assertFalse(h.state.confirmationTimedOut)
        h.controller.updateVehicleObservation(h.observation(11))
        assertEquals(PasswordWriteState.VEHICLE_CONFIRMED, h.state.writeState)
    }

    @Test fun providerVinChangeRevokesOldFlowEvenWithoutUiNotification() = Harness().use { h ->
        h.submit()
        h.vin = "TEST-B"
        h.controller.updateVehicleObservation(h.observation(11, observedVin = "TEST-A"))
        assertEquals(PasswordWriteState.UNKNOWN, h.state.writeState)
        h.controller.submitPassword("1357")
        assertEquals(listOf("TEST-A"), h.gateway.writes)
        assertEquals(1, h.commands.size)
    }

    @Test fun abaVinSwitchRejectsNonCooperativeOldWriteResult() = Harness().use { h ->
        val release = CompletableDeferred<Unit>()
        h.gateway.setResult = { withContext(NonCancellable) { release.await(); PasswordRequestResult(true) } }
        h.submit()
        h.vin = "TEST-B"
        h.controller.onVinChanged(h.vin)
        h.vin = "TEST-A"
        h.controller.onVinChanged(h.vin)
        h.controller.begin(PasswordFlow.Change)
        release.complete(Unit)
        assertTrue(h.commands.isEmpty())
        assertFalse(h.state.cloudAccepted)
        assertEquals(PasswordFlow.Change, h.state.flow)
        assertEquals(PasswordWriteState.UNKNOWN, h.state.writeState)
    }

    @Test fun refreshAfterVinChangeClearsOldVerification() = Harness().use { h ->
        h.controller.begin(PasswordFlow.Forget)
        h.controller.verifySmsCode("fake-code")
        assertTrue(h.state.smsVerified)
        h.vin = "TEST-B"
        h.controller.refresh()
        assertNull(h.state.flow)
        assertFalse(h.state.smsVerified)
        h.controller.submitPassword("1357")
        assertTrue(h.gateway.writes.isEmpty())
    }

    @Test fun changeRequiresOriginalPasswordAndCannotUseSmsVerification() = Harness().use { h ->
        h.controller.begin(PasswordFlow.Change)
        assertFalse(h.state.smsVerified)
        h.controller.verifySmsCode("fake-code")
        h.controller.submitPassword("1357")
        assertTrue(h.gateway.writes.isEmpty())
        h.gateway.verifyResult = PasswordVerifyResult(false)
        h.controller.verifyCurrentPassword("2468")
        h.controller.submitPassword("1357")
        assertTrue(h.gateway.writes.isEmpty())
        h.gateway.verifyResult = PasswordVerifyResult(true)
        h.controller.verifyCurrentPassword("2468")
        h.controller.submitPassword("1357")
        assertEquals(listOf("TEST-A"), h.gateway.writes)
    }

    @Test fun forgetRequiresSuccessfulCredentialAndCannotUseOriginalPassword() = Harness().use { h ->
        h.controller.begin(PasswordFlow.Forget)
        h.controller.verifyCurrentPassword("2468")
        h.controller.submitPassword("1357")
        assertTrue(h.gateway.writes.isEmpty())
        h.gateway.smsResult = PasswordSmsVerifyResult(false, "should-not-be-used")
        h.controller.verifySmsCode("fake-code")
        h.controller.submitPassword("1357")
        assertTrue(h.gateway.writes.isEmpty())
        h.gateway.smsResult = PasswordSmsVerifyResult(true, "credential")
        h.controller.verifySmsCode("fake-code")
        h.controller.submitPassword("1357")
        assertEquals(listOf("TEST-A:reset"), h.gateway.writes)
    }

    @Test fun explicitCloudFailureNeverSendsBleOrReportsSuccess() = Harness().use { h ->
        h.gateway.setResult = { PasswordRequestResult(false, "fake rejection") }
        h.submit()
        assertEquals(PasswordWriteState.FAILED, h.state.writeState)
        assertFalse(h.state.cloudAccepted || h.state.bleSent || h.state.success || h.state.busy)
        assertTrue(h.commands.isEmpty())
    }

    @Test fun bleFailureRetainsCloudAcceptanceWithoutConfirmation() = Harness().use { h ->
        h.send = { false }
        h.submit()
        h.controller.updateVehicleObservation(h.observation(11))
        assertEquals(PasswordWriteState.CLOUD_ACCEPTED, h.state.writeState)
        assertTrue(h.state.cloudAccepted)
        assertFalse(h.state.bleSent || h.state.success)
        assertTrue(h.timers.isEmpty())
    }

    @Test fun enableFailureToneDoesNotRewritePasswordAcceptanceFacts() = Harness().use { h ->
        h.submit()
        assertTrue(h.state.cloudAccepted)
        assertTrue(h.state.bleSent)
        h.gateway.enableResult = PasswordRequestResult(false, "toggle failed")
        h.controller.setEnabled(true)
        assertEquals(PasswordMessageTone.ERROR, h.state.messageTone)
        assertTrue(h.state.cloudAccepted)
        assertTrue(h.state.bleSent)
        assertFalse(h.state.success)
    }

    @Test fun cancellationPropagatesAndKeepsCloudAcceptance() = Harness().use { h ->
        val gate = CompletableDeferred<Boolean>()
        val cancelled = CancellationException("fake sink cancellation")
        h.send = { gate.await() }
        h.submit()
        var completion: Throwable? = null
        h.scope.coroutineContext[Job]!!.children.single().invokeOnCompletion { completion = it }
        gate.completeExceptionally(cancelled)
        assertTrue(completion is CancellationException)
        assertEquals(cancelled.message, completion?.message)
        assertFalse(h.state.busy || h.state.success || h.state.bleSent)
        assertTrue(h.state.cloudAccepted)
        assertEquals(PasswordWriteState.CLOUD_ACCEPTED, h.state.writeState)
    }

    @Test fun beginDuringPendingRequestDoesNotSwitchFlow() = Harness().use { h ->
        val release = CompletableDeferred<PasswordRequestResult>()
        h.gateway.setResult = { release.await() }
        h.submit()
        h.controller.begin(PasswordFlow.Forget)
        assertEquals(PasswordFlow.Set, h.state.flow)
        release.complete(PasswordRequestResult(false))
        assertFalse(h.state.busy)
    }
}
