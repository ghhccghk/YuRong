package top.ghhccghk.yadea.settings

import kotlinx.coroutines.flow.Flow

private typealias KBoolean = kotlin.Boolean
private typealias KInt = kotlin.Int
private typealias KLong = kotlin.Long
private typealias KFloat = kotlin.Float
private typealias KString = kotlin.String
private typealias KStringSet = Set<kotlin.String>

sealed class PreferenceType<T : Any>(val valueName: KString) {
    data object Boolean : PreferenceType<KBoolean>("Boolean")
    data object Int : PreferenceType<KInt>("Int")
    data object Long : PreferenceType<KLong>("Long")
    data object Float : PreferenceType<KFloat>("Float")
    data object String : PreferenceType<KString>("String")
    data object StringSet : PreferenceType<KStringSet>("StringSet")

    fun accepts(value: Any): KBoolean = when (this) {
        Boolean -> value is KBoolean
        Int -> value is KInt
        Long -> value is KLong
        Float -> value is KFloat
        String -> value is KString
        StringSet -> value is Set<*> && value.all { it is KString }
    }

    @Suppress("UNCHECKED_CAST")
    fun copyValue(value: Any): T {
        require(accepts(value)) {
            "Expected $valueName but received ${value::class.simpleName}."
        }
        return when (this) {
            StringSet -> (value as Set<*>).mapTo(linkedSetOf()) { it as KString } as T
            else -> value as T
        }
    }
}

class PreferenceKey<T : Any> private constructor(
    val name: KString,
    val type: PreferenceType<T>,
    defaultValue: T,
) {
    init {
        require(name.isNotBlank()) { "Preference key name must not be blank." }
    }

    private val storedDefaultValue = type.copyValue(defaultValue)

    val defaultValue: T
        get() = type.copyValue(storedDefaultValue)

    override fun equals(other: Any?): KBoolean =
        other is PreferenceKey<*> && name == other.name && type == other.type

    override fun hashCode(): KInt = 31 * name.hashCode() + type.hashCode()

    companion object {
        operator fun invoke(name: KString, defaultValue: KBoolean): PreferenceKey<KBoolean> =
            PreferenceKey(name, PreferenceType.Boolean, defaultValue)

        operator fun invoke(name: KString, defaultValue: KInt): PreferenceKey<KInt> =
            PreferenceKey(name, PreferenceType.Int, defaultValue)

        operator fun invoke(name: KString, defaultValue: KLong): PreferenceKey<KLong> =
            PreferenceKey(name, PreferenceType.Long, defaultValue)

        operator fun invoke(name: KString, defaultValue: KFloat): PreferenceKey<KFloat> =
            PreferenceKey(name, PreferenceType.Float, defaultValue)

        operator fun invoke(name: KString, defaultValue: KString): PreferenceKey<KString> =
            PreferenceKey(name, PreferenceType.String, defaultValue)

        operator fun invoke(name: KString, defaultValue: KStringSet): PreferenceKey<KStringSet> =
            PreferenceKey(name, PreferenceType.StringSet, defaultValue)

        fun boolean(name: KString, defaultValue: KBoolean = false): PreferenceKey<KBoolean> =
            PreferenceKey(name, defaultValue)

        fun int(name: KString, defaultValue: KInt = 0): PreferenceKey<KInt> =
            PreferenceKey(name, defaultValue)

        fun long(name: KString, defaultValue: KLong = 0L): PreferenceKey<KLong> =
            PreferenceKey(name, defaultValue)

        fun float(name: KString, defaultValue: KFloat = 0f): PreferenceKey<KFloat> =
            PreferenceKey(name, defaultValue)

        fun string(name: KString, defaultValue: KString = ""): PreferenceKey<KString> =
            PreferenceKey(name, defaultValue)

        fun stringSet(
            name: KString,
            defaultValue: KStringSet = emptySet(),
        ): PreferenceKey<KStringSet> = PreferenceKey(name, defaultValue)
    }
}

sealed interface PreferenceWriteResult {
    data object Success : PreferenceWriteResult
    data class Failure(val cause: Throwable? = null) : PreferenceWriteResult
}

interface PreferenceStore {
    fun <T : Any> read(key: PreferenceKey<T>): T
    fun <T : Any> observe(key: PreferenceKey<T>): Flow<T>
    suspend fun <T : Any> write(key: PreferenceKey<T>, value: T): PreferenceWriteResult
    suspend fun delete(key: PreferenceKey<*>): PreferenceWriteResult
    suspend fun clear(): PreferenceWriteResult
}
