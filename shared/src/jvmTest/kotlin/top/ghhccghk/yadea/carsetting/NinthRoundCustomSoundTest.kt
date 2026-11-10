package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

/** Only fake gateways and controlled in-memory responses. No network, storage, account or BLE. */
class NinthRoundCustomSoundTest {
    private val resource = CustomSoundResource("r1", "first")
    private val other = CustomSoundResource("r2", "second")
    private val category = CustomSoundCategory("c1", "boot", "r1", "first")

    private fun cloud(vin: String) = CustomSoundState(
        enabled = vin == "B", resources = listOf(resource, other),
        categories = listOf(category, CustomSoundCategory("c2", "lock", "r2", "second")),
        parentConfId = "zdyyx001", weeklyRemaining = 3, weeklyLimit = 5,
        selectedResourceId = resource.id,
        loadedFields = CustomSoundData.entries.toSet(),
    )

    private inner class FakeGateway : CustomSoundGateway {
        val calls = mutableListOf<Pair<String, String>>()
        var load: suspend (String, Long) -> CustomSoundState? = { vin, _ -> cloud(vin) }
        var write: suspend (String, String) -> Boolean = { _, _ -> true }
        override suspend fun loadCustomSoundState(vin: String, modelId: Long): CustomSoundState? {
            calls += "load:$modelId" to vin
            return load(vin, modelId)
        }
        private suspend fun mutate(kind: String, vin: String): Boolean {
            calls += kind to vin
            return write(kind, vin)
        }
        override suspend fun setCustomSoundEnabled(vin: String, enabled: Boolean) = mutate("enable", vin)
        override suspend fun applyCustomSound(vin: String, state: CustomSoundState, category: CustomSoundCategory, resource: CustomSoundResource) = mutate("apply", vin)
        override suspend fun deleteCustomSound(vin: String, resourceId: String) = mutate("delete", vin)
        override suspend fun renameCustomSound(vin: String, resourceId: String, name: String) = mutate("rename", vin)
        override suspend fun addCustomSound(vin: String, modelId: Long, name: String, resourceUrl: String) = mutate("add:$modelId", vin)
    }

    private inner class Harness(scope: CoroutineScope) {
        var vin = "A"
        var model = 10L
        val gateway = FakeGateway()
        val controller = CustomSoundController(gateway, scope, { vin }, { model })
        val state get() = controller.state.value
        suspend fun load() { controller.refresh()!!.join() }
        fun start(kind: String): Job = when (kind) {
            "enable" -> controller.setEnabled(true)
            "apply" -> controller.apply(category, other)
            "delete" -> controller.delete(resource)
            "rename" -> controller.rename(resource, "new")
            "add" -> controller.addFromUrl("new", "https://example.invalid/sound")
            else -> error(kind)
        } ?: error("Operation rejected: $kind")
    }

    @Test fun refreshDoesNotCrossVinEvenIfGatewayIgnoresCancellation() = runBlocking {
        val h = Harness(this)
        val gate = CompletableDeferred<CustomSoundState>()
        val entered = CompletableDeferred<Unit>()
        h.gateway.load = { vin, _ ->
            if (vin == "A") { entered.complete(Unit); withContext(NonCancellable) { gate.await() } }
            else cloud(vin)
        }
        val old = h.controller.refresh()!!
        entered.await()
        h.vin = "B"
        h.controller.onVehicleChanged()
        h.load()
        val newState = h.state
        gate.complete(cloud("A"))
        old.join()
        assertEquals(newState, h.state)
        assertTrue(old.isCancelled)
    }

    private suspend fun staleWrite(scope: CoroutineScope, kind: String) {
        val h = Harness(scope)
        h.load()
        val gate = CompletableDeferred<Boolean>()
        val entered = CompletableDeferred<Unit>()
        h.gateway.write = { _, _ -> entered.complete(Unit); withContext(NonCancellable) { gate.await() } }
        val old = h.start(kind)
        entered.await()
        h.vin = "B"
        h.controller.onVehicleChanged()
        h.load()
        val newState = h.state
        gate.complete(true)
        old.join()
        assertEquals(newState, h.state)
        assertTrue(old.isCancelled)
        assertNull(h.state.message)
        assertEquals(2, h.gateway.calls.count { it.first.startsWith("load:") })
    }

    @Test fun enabledRequestCannotCrossVin() = runBlocking { staleWrite(this, "enable") }
    @Test fun applyRequestCannotCrossVin() = runBlocking { staleWrite(this, "apply") }
    @Test fun deleteRequestCannotCrossVin() = runBlocking { staleWrite(this, "delete") }
    @Test fun renameRequestCannotCrossVin() = runBlocking { staleWrite(this, "rename") }
    @Test fun addRequestCannotCrossVinOrStartOldFollowupRefresh() = runBlocking { staleWrite(this, "add") }

    @Test fun oldFinallyCannotClearNewVehicleBusyFlag() = runBlocking {
        val h = Harness(this)
        h.load()
        val oldGate = CompletableDeferred<Boolean>()
        val newGate = CompletableDeferred<Boolean>()
        val oldEntered = CompletableDeferred<Unit>()
        val newEntered = CompletableDeferred<Unit>()
        h.gateway.write = { _, vin ->
            if (vin == "A") { oldEntered.complete(Unit); withContext(NonCancellable) { oldGate.await() } }
            else { newEntered.complete(Unit); newGate.await() }
        }
        val old = h.start("enable")
        oldEntered.await()
        h.vin = "B"
        h.controller.onVehicleChanged()
        h.load()
        val new = h.start("rename")
        newEntered.await()
        val pending = h.state
        oldGate.complete(false)
        old.join()
        assertEquals(pending, h.state)
        assertTrue(h.state.busy)
        newGate.complete(true)
        new.join()
        assertFalse(h.state.busy)
    }

    @Test fun refreshLatestRequestWinsWithinSameVin() = runBlocking {
        val h = Harness(this)
        val gate = CompletableDeferred<CustomSoundState>()
        val entered = CompletableDeferred<Unit>()
        h.gateway.load = { _, _ -> entered.complete(Unit); withContext(NonCancellable) { gate.await() } }
        val first = h.controller.refresh()!!
        entered.await()
        h.gateway.load = { _, _ -> cloud("A").copy(weeklyRemaining = 1) }
        h.load()
        val latest = h.state
        gate.complete(cloud("A").copy(weeklyRemaining = 5))
        first.join()
        assertEquals(latest, h.state)
        assertEquals(1, h.state.weeklyRemaining)
    }

    @Test fun refreshDuringWriteIsCoalescedAndDoesNotEraseDraft() = runBlocking {
        val h = Harness(this)
        h.load()
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Boolean>()
        h.gateway.write = { _, _ -> entered.complete(Unit); gate.await() }
        val write = h.start("enable")
        entered.await()
        repeat(3) { assertNull(h.controller.refresh()) }
        assertEquals(1, h.gateway.calls.count { it.first.startsWith("load:") })
        assertFalse(h.state.enabled)
        assertEquals(true, h.state.draftEnabled)
        gate.complete(true)
        write.join()
        yield()
        assertEquals(2, h.gateway.calls.count { it.first.startsWith("load:") })
        assertEquals(CustomSoundOperationStatus.SUCCESS, h.state.operationStatus)
        assertEquals(true, h.state.submittedEnabled)
        assertFalse(h.state.enabled)
        assertNull(h.state.draftEnabled)
    }

    @Test fun mutationInvalidatesOlderRefreshBeforeItCanOverwriteRename() = runBlocking {
        val h = Harness(this)
        h.load()
        val gate = CompletableDeferred<CustomSoundState>()
        val entered = CompletableDeferred<Unit>()
        h.gateway.load = { _, _ -> entered.complete(Unit); withContext(NonCancellable) { gate.await() } }
        val old = h.controller.refresh()!!
        entered.await()
        h.gateway.load = { _, _ -> cloud("A").copy(resources = listOf(resource.copy(name = "new"), other)) }
        h.start("rename").join()
        yield()
        gate.complete(cloud("A"))
        old.join()
        assertEquals("new", h.state.resources.first().name)
    }

    @Test fun mutationsAreReservedSynchronouslyIncludingAdd() = runBlocking {
        val h = Harness(this)
        h.load()
        val first = h.start("rename")
        assertTrue(h.state.busy)
        assertNull(h.controller.delete(resource))
        assertNull(h.controller.addFromUrl("new", "https://example.invalid/sound"))
        first.join()
        assertEquals(1, h.gateway.calls.count { !it.first.startsWith("load:") })
    }

    @Test fun gatewayCancellationPropagatesWithoutBusinessFailure() = runBlocking {
        val h = Harness(this)
        h.load()
        h.gateway.write = { _, _ -> throw CancellationException("fake cancellation") }
        val job = h.start("enable")
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(CustomSoundOperationStatus.CANCELLED, h.state.operationStatus)
        assertFalse(h.state.busy)
        assertNull(h.state.message)
        assertFalse(h.state.enabled)
    }

    @Test fun loadCancellationDoesNotLeavePermanentLoading() = runBlocking {
        val h = Harness(this)
        h.gateway.load = { _, _ -> throw CancellationException("fake cancellation") }
        val job = h.controller.refresh()!!
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(CustomSoundOperationStatus.CANCELLED, h.state.loadStatus)
        assertFalse(h.state.loading)
        assertNull(h.state.message)
    }

    @Test fun cancellationBeforeCoroutineStartsCleansReservation() = runBlocking {
        val h = Harness(this)
        h.load()
        h.start("enable").cancelAndJoin()
        assertFalse(h.state.busy)
        assertEquals(CustomSoundOperationStatus.CANCELLED, h.state.operationStatus)
        assertEquals(0, h.gateway.calls.count { it.first == "enable" })
    }

    @Test fun cancellationRetainsAcceptanceButNeverChangesCloudValue() = runBlocking {
        val h = Harness(this)
        h.load()
        val entered = CompletableDeferred<Unit>()
        h.gateway.write = { _, _ ->
            recordCustomSoundReceipt(accepted = true, status = 1)
            entered.complete(Unit)
            CompletableDeferred<Boolean>().await()
        }
        val job = h.start("apply")
        entered.await()
        h.controller.cancelPending()
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(true, h.state.requestAccepted)
        assertEquals(1, h.state.serverStatus)
        assertEquals(CustomSoundOperationStatus.CANCELLED, h.state.operationStatus)
        assertEquals("r1", h.state.categories.first().selectedResourceId)
        assertNull(h.state.gatewaySucceeded)
    }

    @Test fun booleanSuccessDoesNotFabricateServerResultOrVehicleSelection() = runBlocking {
        val h = Harness(this)
        h.load()
        val before = h.state
        h.start("apply").join()
        assertEquals(before.enabled, h.state.enabled)
        assertEquals(before.categories, h.state.categories)
        assertEquals(before.resources, h.state.resources)
        assertEquals(other, h.state.submittedSelection?.resource)
        assertNull(h.state.serverStatus)
        assertNull(h.state.requestAccepted)
        assertEquals(true, h.state.gatewaySucceeded)
        assertEquals(CustomSoundOperationStatus.SUCCESS, h.state.operationStatus)
    }

    @Test fun failureClearsDraftButKeepsCloudRecordAndAcceptance() = runBlocking {
        val h = Harness(this)
        h.load()
        val before = h.state
        h.gateway.write = { _, _ -> recordCustomSoundReceipt(accepted = true, status = 3); false }
        h.start("enable").join()
        assertEquals(before.enabled, h.state.enabled)
        assertEquals(before.categories, h.state.categories)
        assertEquals(true, h.state.requestAccepted)
        assertEquals(3, h.state.serverStatus)
        assertNull(h.state.submittedEnabled)
        assertNull(h.state.draftEnabled)
        assertNotNull(h.state.message)
        assertEquals(CustomSoundOperationStatus.FAILED, h.state.operationStatus)
    }

    @Test fun successfulAddRefreshesOnceAndAlwaysClearsBusy() = runBlocking {
        val h = Harness(this)
        h.load()
        h.gateway.load = { _, _ -> cloud("A").copy(resources = listOf(resource, other, CustomSoundResource("r3", "new"))) }
        h.start("add").join()
        yield()
        assertFalse(h.state.busy)
        assertFalse(h.state.loading)
        assertEquals(3, h.state.resources.size)
        assertEquals(2, h.gateway.calls.count { it.first.startsWith("load:") })
    }

    @Test fun failedFollowupRefreshDoesNotEraseSuccessfulAddReceipt() = runBlocking {
        val h = Harness(this)
        h.load()
        h.gateway.load = { _, _ -> null }
        h.start("add").join()
        yield()
        assertFalse(h.state.busy)
        assertFalse(h.state.loading)
        assertEquals(CustomSoundOperationStatus.FAILED, h.state.loadStatus)
        assertEquals(CustomSoundOperationStatus.SUCCESS, h.state.operationStatus)
        assertEquals(true, h.state.gatewaySucceeded)
        assertNotNull(h.state.message)
    }

    @Test fun providerChangeAtBoundaryClearsStateWithoutWaitingForUiCollector() = runBlocking {
        val h = Harness(this)
        h.load()
        val gate = CompletableDeferred<Boolean>()
        val entered = CompletableDeferred<Unit>()
        h.gateway.write = { _, _ -> entered.complete(Unit); gate.await() }
        val job = h.start("enable")
        entered.await()
        h.vin = "B"
        gate.complete(true)
        job.join()
        assertEquals("B", h.state.vin)
        assertFalse(h.state.cloudLoaded)
        assertFalse(h.state.busy)
        assertNull(h.state.message)
    }

    @Test fun modelChangeInvalidatesSameVinRequest() = runBlocking {
        val h = Harness(this)
        h.load()
        val gate = CompletableDeferred<Boolean>()
        val entered = CompletableDeferred<Unit>()
        h.gateway.write = { _, _ -> entered.complete(Unit); withContext(NonCancellable) { gate.await() } }
        val old = h.start("enable")
        entered.await()
        h.model = 20
        h.controller.onVehicleChanged()
        assertNull(h.controller.refresh())
        assertTrue(h.state.busy)
        gate.complete(true)
        old.join()
        h.load()
        assertEquals(20L, h.state.modelId)
        assertFalse(h.state.busy)
        assertFalse(h.state.reconciliationRequired)
    }

    @Test fun renameOnlyChangesMatchingResourceAndCategoryNames() = runBlocking {
        val h = Harness(this)
        h.load()
        val before = h.state
        h.start("rename").join()
        assertEquals("new", h.state.resources.first().name)
        assertEquals("new", h.state.categories.first().selectedName)
        assertEquals(before.categories[1], h.state.categories[1])
        assertEquals(before.enabled, h.state.enabled)
        assertEquals(before.weeklyRemaining, h.state.weeklyRemaining)
        h.start("delete").join()
        assertEquals(listOf(other), h.state.resources)
        assertEquals(before.categories[1], h.state.categories[1])
    }

    @Test fun disposedControllerRejectsNewWorkAndCancelsOldRequests() = runBlocking {
        val h = Harness(this)
        h.load()
        val job = h.start("enable")
        h.controller.dispose()
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(h.state.busy)
        assertNull(h.controller.refresh())
        assertNull(h.controller.setEnabled(true))
    }

    @Test fun dismissedNonCooperativeWriteBlocksNewWritesUntilItEndsAndRefreshes() = runBlocking {
        val h = Harness(this)
        h.load()
        val gate = CompletableDeferred<Boolean>()
        val entered = CompletableDeferred<Unit>()
        h.gateway.write = { _, _ -> entered.complete(Unit); withContext(NonCancellable) { gate.await() } }
        val old = h.start("enable")
        entered.await()
        h.controller.cancelPending()
        assertTrue(h.state.busy)
        assertTrue(h.state.reconciliationRequired)
        assertNull(h.controller.rename(resource, "new"))
        assertNull(h.controller.refresh())
        gate.complete(true)
        old.join()
        yield()
        assertFalse(h.state.busy)
        assertFalse(h.state.reconciliationRequired)
        assertEquals(2, h.gateway.calls.count { it.first.startsWith("load:") })
        assertEquals(0, h.gateway.calls.count { it.first == "rename" })
    }

    @Test fun failedReconciliationCannotUnlockFurtherWrites() = runBlocking {
        val h = Harness(this)
        h.load()
        val old = h.start("enable")
        h.controller.cancelPending()
        old.join()
        h.gateway.load = { _, _ -> cloud("A").copy(loadedFields = setOf(CustomSoundData.COUNT)) }
        h.load()
        assertTrue(h.state.reconciliationRequired)
        assertNull(h.controller.setEnabled(false))
        h.gateway.load = { _, _ -> cloud("A") }
        h.load()
        assertFalse(h.state.reconciliationRequired)
        h.controller.setEnabled(false)!!.join()
    }

    @Test fun directlyCancelledWriteAlsoRequiresReconciliation() = runBlocking {
        val h = Harness(this)
        h.load()
        val entered = CompletableDeferred<Unit>()
        h.gateway.write = { _, _ ->
            recordCustomSoundReceipt(accepted = true)
            entered.complete(Unit)
            CompletableDeferred<Boolean>().await()
        }
        val job = h.start("enable")
        entered.await()
        job.cancelAndJoin()
        assertTrue(h.state.reconciliationRequired)
        assertEquals(true, h.state.requestAccepted)
        assertNull(h.controller.setEnabled(false))
        h.load()
        assertFalse(h.state.reconciliationRequired)
    }

    @Test fun partialLoadPreservesValidValuesButMarksMissingFieldsUnknown() = runBlocking {
        val h = Harness(this)
        h.vin = "B"
        h.load()
        val before = h.state
        h.gateway.load = { _, _ -> CustomSoundState(weeklyRemaining = 1, weeklyLimit = 5, loadedFields = setOf(CustomSoundData.COUNT)) }
        h.load()
        assertEquals(before.enabled, h.state.enabled)
        assertEquals(before.categories, h.state.categories)
        assertEquals(before.resources, h.state.resources)
        assertEquals(1, h.state.weeklyRemaining)
        assertEquals(setOf(CustomSoundData.COUNT), h.state.loadedFields)
        assertNull(h.controller.apply(category, other))
        assertNull(h.controller.rename(resource, "new"))
        assertNull(h.controller.delete(resource))
    }

    @Test fun successfulApplyRefreshesCloudRecordWithoutPromotingTargetToReadback() = runBlocking {
        val h = Harness(this)
        h.load()
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<CustomSoundState>()
        h.gateway.load = { _, _ -> entered.complete(Unit); gate.await() }
        h.start("apply").join()
        entered.await()
        assertEquals("r1", h.state.categories.first().selectedResourceId)
        assertEquals("r2", h.state.submittedSelection?.resource?.id)
        gate.complete(cloud("A").copy(categories = listOf(category.copy(selectedResourceId = "r2", selectedName = "second"))))
        yield()
        assertEquals("r2", h.state.categories.first().selectedResourceId)
        assertNull(h.state.serverStatus)
    }

    @Test fun blankVinClearsOldListsAndCannotSubmit() = runBlocking {
        val h = Harness(this)
        h.load()
        h.vin = " "
        assertNull(h.controller.refresh())
        assertEquals("", h.state.vin)
        assertTrue(h.state.resources.isEmpty())
        assertFalse(h.state.cloudLoaded)
        assertNull(h.controller.setEnabled(true))
    }
}
