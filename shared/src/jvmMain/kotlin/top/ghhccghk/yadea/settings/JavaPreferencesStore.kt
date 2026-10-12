package top.ghhccghk.yadea.settings

import java.util.prefs.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class JavaPreferencesStore(
    private val preferences: Preferences = Preferences.userRoot().node("top/ghhccghk/yadea"),
) : PreferenceStore {
    override fun <T : Any> read(key: PreferenceKey<T>): T {
        return try {
            when (key.type) {
                PreferenceType.Boolean -> preferences.getBoolean(key.name, key.defaultValue as Boolean)
                PreferenceType.Int -> preferences.getInt(key.name, key.defaultValue as Int)
                PreferenceType.Long -> preferences.getLong(key.name, key.defaultValue as Long)
                PreferenceType.Float -> preferences.getFloat(key.name, key.defaultValue as Float)
                PreferenceType.String -> preferences.get(key.name, key.defaultValue as String)
                PreferenceType.StringSet -> preferences.get(
                    key.name,
                    encodeSet(key.defaultValue as Set<String>),
                ).decodeSet()
            } as T
        } catch (_: Throwable) {
            key.defaultValue
        }
    }

    override fun <T : Any> observe(key: PreferenceKey<T>): Flow<T> = flow { emit(read(key)) }

    override suspend fun <T : Any> write(key: PreferenceKey<T>, value: T): PreferenceWriteResult {
        return try {
            when (key.type) {
                PreferenceType.Boolean -> preferences.putBoolean(key.name, value as Boolean)
                PreferenceType.Int -> preferences.putInt(key.name, value as Int)
                PreferenceType.Long -> preferences.putLong(key.name, value as Long)
                PreferenceType.Float -> preferences.putFloat(key.name, value as Float)
                PreferenceType.String -> preferences.put(key.name, value as String)
                PreferenceType.StringSet -> preferences.put(key.name, encodeSet(value as Set<String>))
            }
            preferences.flush()
            PreferenceWriteResult.Success
        } catch (cause: Throwable) {
            PreferenceWriteResult.Failure(cause)
        }
    }

    override suspend fun delete(key: PreferenceKey<*>): PreferenceWriteResult =
        runCatching {
            preferences.remove(key.name)
            preferences.flush()
        }.fold(
            onSuccess = { PreferenceWriteResult.Success },
            onFailure = { PreferenceWriteResult.Failure(it) },
        )

    override suspend fun clear(): PreferenceWriteResult =
        runCatching {
            AppPreferenceKeys.all.forEach { preferences.remove(it.name) }
            preferences.flush()
        }.fold(
            onSuccess = { PreferenceWriteResult.Success },
            onFailure = { PreferenceWriteResult.Failure(it) },
        )

    private fun encodeSet(values: Set<String>): String = values.joinToString("\u0000")
    private fun String.decodeSet(): Set<String> =
        if (isEmpty()) emptySet() else split("\u0000").toSet()
}
