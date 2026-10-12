package top.ghhccghk.yadea.settings

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class LocalStoragePreferenceStore : PreferenceStore {
    override fun <T : Any> read(key: PreferenceKey<T>): T {
        val raw = localStorage.getItem(storageName(key)) ?: return key.defaultValue
        return try {
            when (key.type) {
                PreferenceType.Boolean -> raw.toBoolean()
                PreferenceType.Int -> raw.toInt()
                PreferenceType.Long -> raw.toLong()
                PreferenceType.Float -> raw.toFloat()
                PreferenceType.String -> raw
                PreferenceType.StringSet -> if (raw.isEmpty()) emptySet() else raw.split("\u0000").toSet()
            } as T
        } catch (_: Throwable) {
            key.defaultValue
        }
    }

    override fun <T : Any> observe(key: PreferenceKey<T>): Flow<T> = flow { emit(read(key)) }

    override suspend fun <T : Any> write(key: PreferenceKey<T>, value: T): PreferenceWriteResult {
        return try {
            val encoded = when (key.type) {
                PreferenceType.Boolean -> (value as Boolean).toString()
                PreferenceType.Int -> (value as Int).toString()
                PreferenceType.Long -> (value as Long).toString()
                PreferenceType.Float -> (value as Float).toString()
                PreferenceType.String -> value as String
                PreferenceType.StringSet -> (value as Set<String>).joinToString("\u0000")
            }
            localStorage.setItem(storageName(key), encoded)
            PreferenceWriteResult.Success
        } catch (cause: Throwable) {
            PreferenceWriteResult.Failure(cause)
        }
    }

    override suspend fun delete(key: PreferenceKey<*>): PreferenceWriteResult {
        return try {
            localStorage.removeItem(storageName(key))
            PreferenceWriteResult.Success
        } catch (cause: Throwable) {
            PreferenceWriteResult.Failure(cause)
        }
    }

    override suspend fun clear(): PreferenceWriteResult {
        return try {
            AppPreferenceKeys.all.forEach { localStorage.removeItem(storageName(it)) }
            PreferenceWriteResult.Success
        } catch (cause: Throwable) {
            PreferenceWriteResult.Failure(cause)
        }
    }

    private fun storageName(key: PreferenceKey<*>): String = "top.ghhccghk.yadea.${key.name}"
}
