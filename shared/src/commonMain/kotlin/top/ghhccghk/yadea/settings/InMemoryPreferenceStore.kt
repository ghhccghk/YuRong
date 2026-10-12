package top.ghhccghk.yadea.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryPreferenceStore : PreferenceStore {
    private data class StoredValue(
        val type: PreferenceType<*>,
        val value: Any,
    )

    private val values = MutableStateFlow<Map<String, StoredValue>>(emptyMap())
    private val mutex = Mutex()

    override fun <T : Any> read(key: PreferenceKey<T>): T {
        val stored = values.value[key.name] ?: return key.defaultValue
        return if (stored.type == key.type) {
            key.type.copyValue(stored.value)
        } else {
            key.defaultValue
        }
    }

    override fun <T : Any> observe(key: PreferenceKey<T>): Flow<T> =
        values.map { read(key) }.distinctUntilChanged()

    override suspend fun <T : Any> write(
        key: PreferenceKey<T>,
        value: T,
    ): PreferenceWriteResult = mutex.withLock {
        val normalized = try {
            key.type.copyValue(value)
        } catch (cause: IllegalArgumentException) {
            return@withLock PreferenceWriteResult.Failure(cause)
        }
        values.value = values.value + (
            key.name to StoredValue(key.type, normalized)
            )
        PreferenceWriteResult.Success
    }

    override suspend fun delete(key: PreferenceKey<*>): PreferenceWriteResult =
        mutex.withLock {
            values.value = values.value - key.name
            PreferenceWriteResult.Success
        }

    override suspend fun clear(): PreferenceWriteResult =
        mutex.withLock {
            values.value = emptyMap()
            PreferenceWriteResult.Success
        }
}
