package top.ghhccghk.yadea.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import platform.Foundation.NSUserDefaults

class NSUserDefaultsPreferenceStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : PreferenceStore {
    override fun <T : Any> read(key: PreferenceKey<T>): T {
        val name = storageName(key)
        return try {
            when (key.type) {
                PreferenceType.Boolean -> defaults.boolForKey(name)
                PreferenceType.Int -> defaults.integerForKey(name).toInt()
                PreferenceType.Long -> defaults.integerForKey(name)
                PreferenceType.Float -> defaults.doubleForKey(name).toFloat()
                PreferenceType.String -> defaults.stringForKey(name) ?: key.defaultValue
                PreferenceType.StringSet -> defaults.stringArrayForKey(name)?.toSet() ?: key.defaultValue
            } as T
        } catch (_: Throwable) {
            key.defaultValue
        }
    }

    override fun <T : Any> observe(key: PreferenceKey<T>): Flow<T> = flow { emit(read(key)) }

    override suspend fun <T : Any> write(key: PreferenceKey<T>, value: T): PreferenceWriteResult {
        val name = storageName(key)
        return try {
            when (key.type) {
                PreferenceType.Boolean -> defaults.setBool(value as Boolean, forKey = name)
                PreferenceType.Int -> defaults.setInteger((value as Int).toLong(), forKey = name)
                PreferenceType.Long -> defaults.setInteger(value as Long, forKey = name)
                PreferenceType.Float -> defaults.setDouble((value as Float).toDouble(), forKey = name)
                PreferenceType.String -> defaults.setObject(value as String, forKey = name)
                PreferenceType.StringSet -> defaults.setObject((value as Set<String>).toList(), forKey = name)
            }
            PreferenceWriteResult.Success
        } catch (cause: Throwable) {
            PreferenceWriteResult.Failure(cause)
        }
    }

    override suspend fun delete(key: PreferenceKey<*>): PreferenceWriteResult {
        return try {
            defaults.removeObjectForKey(storageName(key))
            PreferenceWriteResult.Success
        } catch (cause: Throwable) {
            PreferenceWriteResult.Failure(cause)
        }
    }

    override suspend fun clear(): PreferenceWriteResult {
        return try {
            AppPreferenceKeys.all.forEach { defaults.removeObjectForKey(storageName(it)) }
            PreferenceWriteResult.Success
        } catch (cause: Throwable) {
            PreferenceWriteResult.Failure(cause)
        }
    }

    private fun storageName(key: PreferenceKey<*>): String = "top.ghhccghk.yadea.${key.name}"
}
