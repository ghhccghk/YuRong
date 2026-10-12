package top.ghhccghk.yadea.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class SharedPreferencesPreferenceStore(
    context: Context,
    preferenceName: String = "touping_settings",
) : PreferenceStore {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)

    override fun <T : Any> read(key: PreferenceKey<T>): T {
        val name = storageName(key)
        return try {
            when (key.type) {
                PreferenceType.Boolean -> preferences.getBoolean(name, key.defaultValue as Boolean)
                PreferenceType.Int -> preferences.getInt(name, key.defaultValue as Int)
                PreferenceType.Long -> preferences.getLong(name, key.defaultValue as Long)
                PreferenceType.Float -> preferences.getFloat(name, key.defaultValue as Float)
                PreferenceType.String -> preferences.getString(name, key.defaultValue as String) ?: key.defaultValue
                PreferenceType.StringSet -> preferences.getStringSet(name, key.defaultValue as Set<String>)?.toSet()
                    ?: key.defaultValue
            } as T
        } catch (_: ClassCastException) {
            key.defaultValue
        }
    }

    override fun <T : Any> observe(key: PreferenceKey<T>): Flow<T> {
        val changes = callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedName ->
                if (changedName == storageName(key)) trySend(Unit)
            }
            preferences.registerOnSharedPreferenceChangeListener(listener)
            awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        return changes.map { read(key) }.distinctUntilChanged()
    }

    override suspend fun <T : Any> write(key: PreferenceKey<T>, value: T): PreferenceWriteResult {
        val editor = preferences.edit()
        val name = storageName(key)
        return try {
            when (key.type) {
                PreferenceType.Boolean -> editor.putBoolean(name, value as Boolean)
                PreferenceType.Int -> editor.putInt(name, value as Int)
                PreferenceType.Long -> editor.putLong(name, value as Long)
                PreferenceType.Float -> editor.putFloat(name, value as Float)
                PreferenceType.String -> editor.putString(name, value as String)
                PreferenceType.StringSet -> editor.putStringSet(name, value as Set<String>)
            }
            if (editor.commit()) PreferenceWriteResult.Success else PreferenceWriteResult.Failure()
        } catch (cause: Throwable) {
            PreferenceWriteResult.Failure(cause)
        }
    }

    override suspend fun delete(key: PreferenceKey<*>): PreferenceWriteResult =
        commit { remove(storageName(key)) }

    override suspend fun clear(): PreferenceWriteResult =
        commit {
            AppPreferenceKeys.all.forEach { remove(storageName(it)) }
        }

    private fun commit(operation: SharedPreferences.Editor.() -> Unit): PreferenceWriteResult {
        return try {
            val editor = preferences.edit()
            editor.operation()
            if (editor.commit()) {
                PreferenceWriteResult.Success
            } else {
                PreferenceWriteResult.Failure()
            }
        } catch (cause: Throwable) {
            PreferenceWriteResult.Failure(cause)
        }
    }

    private fun storageName(key: PreferenceKey<*>): String = "top.ghhccghk.yadea.${key.name}"
}
