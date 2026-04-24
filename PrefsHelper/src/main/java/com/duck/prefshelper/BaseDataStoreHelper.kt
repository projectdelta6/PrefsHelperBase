package com.duck.prefshelper

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.duck.prefshelper.BaseDataStoreHelper.Companion.supervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.coroutines.CoroutineContext
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Base class for DataStore helpers
 *
 * Uses Preferences DataStore to store key-value pairs asynchronously.
 * Provides generic methods to read and write various data types.
 * Includes methods for blocking reads with timeouts.
 *
 * BaseDataStoreHelper uses a coroutine context for background operations.
 * By default, it uses [Dispatchers.IO] + [supervisorJob]. If you need custom job management,
 * pass a context with your own Job or SupervisorJob. Avoid passing a new Job per instance
 * unless you manage its lifecycle explicitly.
 *
 * @param context Application context
 * @param preferenceName Name of the preferences data store
 * @param coroutineContext Coroutine context for async operations
 */
abstract class BaseDataStoreHelper(
	context: Context,
	preferenceName: String,
	@PublishedApi internal val coroutineContext: CoroutineContext = Dispatchers.IO + supervisorJob
) {
	/**
	 * DataStore instance
	 */
	private val Context.dataStoreInstance: DataStore<Preferences> by preferencesDataStore(name = preferenceName)

	/**
	 * DataStore reference
	 *
	 * Internal visibility allows testing while keeping it hidden from library consumers.
	 * This is accessed by inline functions which require it to be internal or public.
	 */
	@PublishedApi
	internal val dataStore: DataStore<Preferences> = context.dataStoreInstance

	/**
	 * Coroutine scope for async operations
	 */
	protected val scope = CoroutineScope(coroutineContext)

	//Todo: remove Deprecated methods in future release
	/**
	 * Add Boolean to the data store
	 */
	@Deprecated("Use writeBoolean instead", ReplaceWith("writeBoolean(key, value)"))
	protected suspend fun writeBool(key: String, value: Boolean) =
		writeBoolean(key, value)

	/**
	 * Add Nullable Boolean to the data store
	 */
	@Deprecated("Use writeBoolean instead", ReplaceWith("writeBoolean(key, value)"))
	protected suspend fun writeBool(key: String, value: Boolean?) = writeBoolean(key, value)

	/**
	 * Reading the Boolean from the data store
	 */
	@Deprecated("Use readBoolean instead", ReplaceWith("readBoolean(key)"))
	protected fun readNullableBool(key: String): Flow<Boolean?> = readBoolean(key)

	/**
	 * Reading the Boolean from the data store
	 */
	@Deprecated("Use readBoolean(key, default) instead", ReplaceWith("readBoolean(key, true)"))
	protected fun readBool(key: String): Flow<Boolean> = readBoolean(key, true)
	//end Deprecated methods

	/**
	 * Clear all preferences in the data store.
	 *
	 * @return Unit
	 */
	open suspend fun clearPrefs(): Unit = withContext(coroutineContext) {
		dataStore.edit { preferences ->
			preferences.clear()
		}
	}

	/**
	 * Generic method to delete value from the data store.
	 *
	 * @param key The key to delete the value for
	 */
	protected suspend inline fun <reified T> removeKey(key: Preferences.Key<T>) =
		dataStore.edit { pref -> pref.remove(key) }

	/**
	 * Generic method to delete value from the data store asynchronously.
	 *
	 * @param key The key to delete the value for
	 */
	protected inline fun <reified T> removeKeyAsync(key: Preferences.Key<T>) =
		scope.launch { removeKey(key) }

	/**
	 * Generic method to add value to the data store.
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	protected suspend inline fun <reified T> writeValue(key: Preferences.Key<T>, value: T) =
		dataStore.edit { pref -> pref[key] = value }

	/**
	 * Generic method to add value to the data store asynchronously.
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	protected inline fun <reified T> writeValueAsync(key: Preferences.Key<T>, value: T) =
		scope.launch { writeValue(key, value) }

	/**
	 * Generic method to add nullable value to the data store.
	 *
	 * If the value is null, the key will be deleted.
	 *
	 * @param key The key to store the value under
	 * @param value The value to store or null to delete
	 */
	@JvmName("writeNullableValue")
	protected suspend inline fun <reified T> writeValue(key: Preferences.Key<T>, value: T?) = withContext(coroutineContext) {
		if (value == null) removeKey(key) else writeValue(key, value)
	}

	/**
	 * Generic method to add nullable value to the data store.
	 *
	 * If the value is null, the key will be deleted.
	 *
	 * @param key The key to store the value under
	 * @param value The value to store or null to delete
	 */
	@JvmName("writeNullableValueAsync")
	protected inline fun <reified T> writeValueAsync(key: Preferences.Key<T>, value: T?) =
		if (value == null) removeKeyAsync(key) else writeValueAsync(key, value)

	/**
	 * Generic method to read value from the data store
	 *
	 * @param key The key to read the value for
	 * @return Flow of the value or null if not present
	 */
	protected inline fun <reified T> readValue(key: Preferences.Key<T>): Flow<T?> =
		dataStore.data.map { pref -> pref[key] }

	/**
	 * Generic method to read value from the data store with a default value
	 *
	 * @param key The key to read the value for
	 * @param defaultValue The default value to return if the key is not present
	 * @return Flow of the value
	 */
	protected inline fun <reified T> readValue(key: Preferences.Key<T>, defaultValue: T): Flow<T> =
		dataStore.data.map { pref -> pref[key] ?: defaultValue }

	/**
	 * Generic method to read value from the data store with a default value provider
	 *
	 * @param key The key to read the value for
	 * @param defaultValue Lambda to provide the default value if the key is not present
	 * @return Flow of the value
	 */
	protected inline fun <reified T> readValue(key: Preferences.Key<T>, crossinline defaultValue: () -> T): Flow<T> =
		dataStore.data.map { pref -> pref[key] ?: defaultValue() }

	/**
	 * Generic method to read value from the data store in a blocking way
	 *
	 * @param key The key to read the value for
	 * @return The value or null if not present
	 */
	protected inline fun <reified T> readValueBlocking(key: Preferences.Key<T>): T? =
		runBlocking(coroutineContext) {
			withTimeoutOrNull(2000) {
				dataStore.data.first()[key]
			}
		}

	/**
	 * Generic method to read value from the data store in a blocking way with a default value
	 *
	 * @param key The key to read the value for
	 * @param defaultValue The default value to return if the key is not present
	 * @return The value
	 */
	protected inline fun <reified T> readValueBlocking(key: Preferences.Key<T>, defaultValue: T): T =
		readValueBlocking(key) ?: defaultValue

	/**
	 * Generic method to read value from the data store in a blocking way with a default value provider
	 *
	 * @param key The key to read the value for
	 * @param defaultValue Lambda to provide the default value if the key is not present
	 * @return The value
	 */
	protected inline fun <reified T> readValueBlocking(key: Preferences.Key<T>, defaultValue: () -> T): T =
		readValueBlocking(key) ?: defaultValue()

	/**
	 * Add [Int] to the data store
	 *
	 * If value is null, the key will be removed.
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	protected suspend fun writeInt(key: String, value: Int?) =
		writeValue(intPreferencesKey(key), value)

	/**
	 * Reading the [Int] value from the data store
	 *
	 * @param key The key to read the value for
	 * @return Flow of the value or null if not present
	 */
	protected fun readInt(key: String): Flow<Int?> =
		readValue(intPreferencesKey(key))

	/**
	 * Add [Int] to the data store asynchronously
	 *
	 * If value is null, the key will be removed.
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	protected fun writeIntAsync(key: String, value: Int?) =
		writeValueAsync(intPreferencesKey(key), value)

	/**
	 * Reading the [Int] value from the data store
	 *
	 * @param key The key to read the value for
	 * @param default The default value to return if the key is not present
	 * @return Flow of the value
	 */
	protected fun readInt(key: String, default: Int): Flow<Int> =
		readValue(intPreferencesKey(key), default)

	/**
	 * Read [Int] value is a blocking way from the data store preferences
	 *
	 * @param key The key to read the value for
	 * @return The value or null if not present
	 */
	protected fun readIntValue(key: String): Int? =
		readValueBlocking(intPreferencesKey(key))

	/**
	 * Read [Int] value is a blocking way from the data store preferences
	 *
	 * @param key The key to read the value for
	 * @param default The default value to return if the key is not present
	 * @return The value
	 */
	protected fun readIntValue(key: String, default: Int): Int =
		readValueBlocking(intPreferencesKey(key), default)

	/**
	 * Adding [Double] to the data store
	 *
	 * If value is null, the key will be removed.
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	protected suspend fun writeDouble(key: String, value: Double?) =
		writeValue(doublePreferencesKey(key), value)

	/**
	 * Add [Double] to the data store asynchronously
	 *
	 * If value is null, the key will be removed.
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	protected fun writeDoubleAsync(key: String, value: Double?) =
		writeValueAsync(doublePreferencesKey(key), value)

	/**
	 * Reading the [Double] value from the data store
	 *
	 * @param key The key to read the value for
	 * @return Flow of the value or null if not present
	 */
	protected fun readDouble(key: String): Flow<Double?> =
		readValue(doublePreferencesKey(key))

	/**
	 * Reading the [Double] value from the data store
	 *
	 * @param key The key to read the value for
	 * @param default The default value to return if the key is not present
	 * @return Flow of the value
	 */
	protected fun readDouble(key: String, default: Double): Flow<Double> =
		readValue(doublePreferencesKey(key), default)

	/**
	 * Read [Double] value is a blocking way from the data store preferences
	 *
	 * @param key The key to read the value for
	 * @return The value or null if not present
	 */
	protected fun readDoubleValue(key: String): Double? =
		readValueBlocking(doublePreferencesKey(key))

	/**
	 * Read [Double] value is a blocking way from the data store preferences
	 *
	 * @param key The key to read the value for
	 * @param default The default value to return if the key is not present
	 * @return The value
	 */
	protected fun readDoubleValue(key: String, default: Double): Double =
		readValueBlocking(doublePreferencesKey(key), default)

	/**
	 * Add [String] data to data Store
	 *
	 * If value is null, the key will be removed.
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	protected suspend fun writeString(key: String, value: String?) =
		writeValue(stringPreferencesKey(key), value)

	/**
	 * Add [String] data to data Store asynchronously
	 *
	 * If value is null, the key will be removed.
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	protected fun writeStringAsync(key: String, value: String?) =
		writeValueAsync(stringPreferencesKey(key), value)

	/**
	 * Read [String] from the data store preferences
	 *
	 * @param key The key to read the value for
	 * @return Flow of the value or null if not present
	 */
	protected fun readString(key: String): Flow<String?> =
		readValue(stringPreferencesKey(key))

	/**
	 * Read [String] from the data store preferences
	 *
	 * @param key The key to read the value for
	 * @param default The default value to return if the key is not present
	 * @return Flow of the value
	 */
	protected fun readString(key: String, default: String): Flow<String> =
		readValue(stringPreferencesKey(key), default)

	/**
	 * Read [String] value is a blocking way from the data store preferences
	 *
	 * @param key The key to read the value for
	 * @return The value or null if not present
	 */
	protected fun readStringValue(key: String): String? =
		readValueBlocking(stringPreferencesKey(key))

	/**
	 * Read [String] value is a blocking way from the data store preferences
	 *
	 * @param key The key to read the value for
	 * @param default The default value to return if the key is not present
	 * @return The value
	 */
	protected fun readStringValue(key: String, default: String): String =
		readValueBlocking(stringPreferencesKey(key), default)

	/**
	 * Add [Long] to the data store
	 *
	 * If value is null, the key will be removed.
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	protected suspend fun writeLong(key: String, value: Long?) =
		writeValue(longPreferencesKey(key), value)

	/**
	 * Add [Long] to the data store asynchronously
	 *
	 * If value is null, the key will be removed.
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	protected fun writeLongAsync(key: String, value: Long?) =
		writeValueAsync(longPreferencesKey(key), value)

	/**
	 * Reading the [Long] from the data store
	 *
	 * @param key The key to read the value for
	 * @return Flow of the value or null if not present
	 */
	protected fun readLong(key: String): Flow<Long?> =
		readValue(longPreferencesKey(key))

	/**
	 * Reading the [Long] from the data store
	 *
	 * @param key The key to read the value for
	 * @param default The default value to return if the key is not present
	 * @return Flow of the value
	 */
	protected fun readLong(key: String, default: Long): Flow<Long> =
		readValue(longPreferencesKey(key), default)

	/**
	 * Read [Long] value is a blocking way from the data store preferences
	 *
	 * @param key The key to read the value for
	 * @return The value or null if not present
	 */
	protected fun readLongValue(key: String): Long? =
		readValueBlocking(longPreferencesKey(key))

	/**
	 * Read [Long] value is a blocking way from the data store preferences
	 *
	 * @param key The key to read the value for
	 * @param default The default value to return if the key is not present
	 * @return The value
	 */
	protected fun readLongValue(key: String, default: Long): Long =
		readValueBlocking(longPreferencesKey(key), default)

	/**
	 * Add [Boolean] to the data store
	 *
	 * If value is null, the key will be removed.
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	protected suspend fun writeBoolean(key: String, value: Boolean?) =
		writeValue(booleanPreferencesKey(key), value)

	/**
	 * Add [Boolean] to the data store asynchronously
	 *
	 * If value is null, the key will be removed.
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	protected fun writeBooleanAsync(key: String, value: Boolean?) =
		writeValueAsync(booleanPreferencesKey(key), value)

	/**
	 * Reading the [Boolean] from the data store
	 *
	 * @param key The key to read the value for
	 * @return Flow of the value or null if not present
	 */
	protected fun readBoolean(key: String): Flow<Boolean?> =
		readValue(booleanPreferencesKey(key))

	/**
	 * Reading the [Boolean] from the data store
	 *
	 * @param key The key to read the value for
	 * @param default The default value to return if the key is not present
	 */
	protected fun readBoolean(key: String, default: Boolean): Flow<Boolean> =
		readValue(booleanPreferencesKey(key), default)

	/**
	 * Read [Boolean] value is a blocking way from the data store preferences
	 *
	 * @param key The key to read the value for
	 * @return The value or null if not present
	 */
	protected fun readBooleanValue(key: String): Boolean? =
		readValueBlocking(booleanPreferencesKey(key))

	/**
	 * Read [Boolean] value is a blocking way from the data store preferences
	 *
	 * @param key The key to read the value for
	 * @param defaultValue The default value to return if the key is not present
	 * @return The value
	 */
	protected fun readBooleanValue(key: String, defaultValue: Boolean): Boolean =
		readValueBlocking(booleanPreferencesKey(key), defaultValue)

	/**
	 * Add [LocalDateTime] to the data store
	 *
	 * If value is null, the key will be removed.
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	protected suspend fun writeLocalDateTime(key: String, value: LocalDateTime?) =
		writeValue(stringPreferencesKey(key), value?.toString())

	/**
	 * Add [LocalDateTime] to the data store asynchronously
	 *
	 * If value is null, the key will be removed.
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun writeLocalDateTimeAsync(key: String, value: LocalDateTime?) =
		writeValueAsync(stringPreferencesKey(key), value?.toString())

	/**
	 * Read a [LocalDateTime] preference as a [Flow].
	 *
	 * @param key The key to read the value for
	 * @return A [Flow] emitting the [LocalDateTime] stored under the key, or null if the key does not exist or the value is invalid
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun readLocalDateTime(key: String): Flow<LocalDateTime?> =
		readValue(stringPreferencesKey(key)).map { it?.let { LocalDateTime.parse(it) } }

	/**
	 * Read a [LocalDateTime] preference as a [Flow] with a non-null default.
	 *
	 * @param key The key to read the value for
	 * @param default The default LocalDateTime value to return if the key does not exist or the value is invalid
	 * @return A [Flow] emitting the [LocalDateTime] stored under the key, or the default value
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun readLocalDateTime(key: String, default: LocalDateTime): Flow<LocalDateTime> =
		readValue(stringPreferencesKey(key)).map { it?.let { LocalDateTime.parse(it) } ?: default }

	/**
	 * Read a [LocalDateTime] preference.
	 *
	 * @param key The key to read the value for
	 * @return A [LocalDateTime] or null if the key does not exist or the value is invalid
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun readLocalDateTimeValue(key: String): LocalDateTime? =
		readValueBlocking(stringPreferencesKey(key))?.let { LocalDateTime.parse(it) }

	/**
	 * Read a [LocalDateTime] preference with a default value.
	 *
	 * @param key The key to read the value for
	 * @param default The default LocalDateTime value to return if the key does not exist or the value is invalid
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun readLocalDateTimeValue(key: String, default: LocalDateTime): LocalDateTime =
		readValueBlocking(stringPreferencesKey(key))?.let { LocalDateTime.parse(it) } ?: default

	/**
	 * Add Nullable LocalDate to the data store
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected suspend fun writeLocalDate(key: String, value: LocalDate?) =
		writeValue(stringPreferencesKey(key), value?.toString())

	/**
	 * Add [LocalDate] to the data store asynchronously
	 *
	 * If value is null, the key will be removed.
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun writeLocalDateAsync(key: String, value: LocalDate?) =
		writeValueAsync(stringPreferencesKey(key), value?.toString())

	/**
	 * Read a [LocalDate] preference as a [Flow].
	 *
	 * @param key The key to read the value for
	 * @return A [Flow] emitting the [LocalDate] stored under the key, or null if the key does not exist or the value is invalid
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun readLocalDate(key: String): Flow<LocalDate?> =
		readValue(stringPreferencesKey(key)).map { it?.let { LocalDate.parse(it) } }

	/**
	 * Read a [LocalDate] preference.
	 *
	 * @param key The key to read the value for
	 * @return A [LocalDate] or null if the key does not exist or the value is invalid
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun readLocalDateValue(key: String): LocalDate? =
		readValueBlocking(stringPreferencesKey(key))?.let { LocalDate.parse(it) }

	/**
	 * Read a [LocalDate] preference as a [Flow] with a non-null default.
	 *
	 * @param key The key to read the value for
	 * @param default The default LocalDate value to return if the key does not exist or the value is invalid
	 * @return A [Flow] emitting the [LocalDate] stored under the key, or the default value
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun readLocalDate(key: String, default: LocalDate): Flow<LocalDate> =
		readValue(stringPreferencesKey(key)).map { it?.let { LocalDate.parse(it) } ?: default }

	/**
	 * Read a [LocalDate] preference with a default value.
	 *
	 * @param key The key to read the value for
	 * @param default The default LocalDate value to return if the key does not exist or the value is invalid
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun readLocalDateValue(key: String, default: LocalDate): LocalDate =
		readValueBlocking(stringPreferencesKey(key))?.let { LocalDate.parse(it) } ?: default

	/**
	 * Add Nullable LocalTime to the data store
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected suspend fun writeLocalTime(key: String, value: LocalTime?) =
		writeValue(stringPreferencesKey(key), value?.toString())

	/**
	 * Add [LocalTime] to the data store asynchronously
	 *
	 * If value is null, the key will be removed.
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun writeLocalTimeAsync(key: String, value: LocalTime?) =
		writeValueAsync(stringPreferencesKey(key), value?.toString())

	/**
	 * Read a [LocalTime] preference as a [Flow].
	 *
	 * @param key The key to read the value for
	 * @return A [Flow] emitting the [LocalTime] stored under the key, or null if the key does not exist or the value is invalid
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun readLocalTime(key: String): Flow<LocalTime?> =
		readValue(stringPreferencesKey(key)).map { it?.let { LocalTime.parse(it) } }

	/**
	 * Read a [LocalTime] preference.
	 *
	 * @param key The key to read the value for
	 * @return A [LocalTime] or null if the key does not exist or the value is invalid
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun readLocalTimeValue(key: String): LocalTime? =
		readValueBlocking(stringPreferencesKey(key))?.let { LocalTime.parse(it) }

	/**
	 * Read a [LocalTime] preference as a [Flow] with a non-null default.
	 *
	 * @param key The key to read the value for
	 * @param default The default LocalTime value to return if the key does not exist or the value is invalid
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun readLocalTime(key: String, default: LocalTime): Flow<LocalTime> =
		readValue(stringPreferencesKey(key)).map { it?.let { LocalTime.parse(it) } ?: default }

	/**
	 * Read a [LocalTime] preference with a default value.
	 *
	 * @param key The key to read the value for
	 * @param default The default LocalTime value to return if the key does not exist or the value is invalid
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun readLocalTimeValue(key: String, default: LocalTime): LocalTime =
		readValueBlocking(stringPreferencesKey(key))?.let { LocalTime.parse(it) } ?: default

	/**
	 * Add an [Enum] preference
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	protected suspend fun writeEnum(key: String, value: Enum<*>?) =
		writeValue(stringPreferencesKey(key), value?.name)

	/**
	 * Add an [Enum] preference asynchronously
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	protected fun writeEnumAsync(key: String, value: Enum<*>?) =
		writeValueAsync(stringPreferencesKey(key), value?.name)

	/**
	 * Read an [Enum] preference as a [Flow] without a default.
	 *
	 * @param key The key to read the value for
	 * @return A [Flow] emitting the Enum stored under the key, or null if the key does not exist or the value is invalid
	 */
	protected inline fun <reified T : Enum<*>> readEnum(key: String): Flow<T?> =
		readValue(stringPreferencesKey(key)).map {
			it?.let { value ->
				if (value.isBlank()) {
					null
				} else {
					try {
						T::class.java.enumConstants?.firstOrNull { it.name == value }
					} catch (e: Exception) {
						Log.w("BaseDataStoreHelper", "Could not get Enum of ${T::class.simpleName} for \"$value\"", e)
						null
					}
				}
			}
		}

	/**
	 * Read an [Enum] preference as a [Flow] with a non-null default.
	 *
	 * @param key The key to read the value for
	 * @param default The default Enum value to return if the key does not exist or the value is invalid
	 * @return A [Flow] emitting the Enum stored under the key, or the default value
	 */
	protected inline fun <reified T : Enum<*>> readEnum(key: String, default: T): Flow<T> =
		readValue(stringPreferencesKey(key)).map {
			it?.let { value ->
				if (value.isBlank()) {
					default
				} else {
					try {
						T::class.java.enumConstants?.firstOrNull { it.name == value } ?: default
					} catch (e: Exception) {
						Log.w("BaseDataStoreHelper", "Could not get Enum of ${T::class.simpleName} for \"$value\"", e)
						default
					}
				}
			} ?: default
		}

	/**
	 * Read an [Enum] preference as a blocking call.
	 *
	 * @param key The key to read the value for
	 * @return The Enum stored under the key, or the default value
	 */
	@JvmName("readEnumValueNullable")
	protected inline fun <reified T : Enum<*>> readEnumValue(key: String): T? =
		readValueBlocking(stringPreferencesKey(key))?.let { value ->
			if (value.isBlank()) {
				null
			} else {
				try {
					T::class.java.enumConstants?.firstOrNull { it.name == value }
				} catch (e: Exception) {
					Log.w("BaseDataStoreHelper", "Could not get Enum of ${T::class.simpleName} for \"$value\"", e)
					null
				}
			}
		}

	/**
	 * Read an [Enum] preference as a blocking call.
	 *
	 * @param key The key to read the value for
	 * @param default The default Enum value to return if the key does not exist or the value is invalid
	 * @return The Enum stored under the key, or the default value
	 */
	protected inline fun <reified T : Enum<*>> readEnumValue(key: String, default: T): T =
		readValueBlocking(stringPreferencesKey(key))?.let { value ->
			if (value.isBlank()) {
				default
			} else {
				try {
					T::class.java.enumConstants?.firstOrNull { it.name == value } ?: default
				} catch (e: Exception) {
					Log.w("BaseDataStoreHelper", "Could not get Enum of ${T::class.simpleName} for \"$value\"", e)
					default
				}
			}
		} ?: default

	/**
	 * Create a property delegate for a [String] preference.
	 *
	 * Reads block (with a 2s timeout). Writes are dispatched to [scope] via the existing
	 * [writeStringAsync] method — fire-and-forget.
	 *
	 * @param key The key to read/write the value for
	 * @param defaultValue Value returned if the key is absent
	 */
	protected fun stringPref(key: String, defaultValue: String): ReadWriteProperty<Any?, String> =
		object : ReadWriteProperty<Any?, String> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): String = readStringValue(key, defaultValue)
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
				writeStringAsync(key, value)
			}
		}

	/**
	 * Create a property delegate for a nullable [String] preference.
	 *
	 * Assigning null removes the key.
	 */
	protected fun stringPref(key: String): ReadWriteProperty<Any?, String?> =
		object : ReadWriteProperty<Any?, String?> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): String? = readStringValue(key)
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: String?) {
				writeStringAsync(key, value)
			}
		}

	/**
	 * [Flow] accessor for a [String] preference — alias for [readString].
	 */
	protected fun stringPrefFlow(key: String, defaultValue: String): Flow<String> = readString(key, defaultValue)

	/**
	 * [Flow] accessor for a nullable [String] preference — alias for [readString].
	 */
	protected fun stringPrefFlow(key: String): Flow<String?> = readString(key)

	/**
	 * Create a property delegate for an [Int] preference.
	 */
	protected fun intPref(key: String, defaultValue: Int): ReadWriteProperty<Any?, Int> =
		object : ReadWriteProperty<Any?, Int> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): Int = readIntValue(key, defaultValue)
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
				writeIntAsync(key, value)
			}
		}

	/**
	 * Create a property delegate for a nullable [Int] preference.
	 */
	protected fun intPref(key: String): ReadWriteProperty<Any?, Int?> =
		object : ReadWriteProperty<Any?, Int?> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): Int? = readIntValue(key)
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int?) {
				writeIntAsync(key, value)
			}
		}

	/**
	 * [Flow] accessor for an [Int] preference — alias for [readInt].
	 */
	protected fun intPrefFlow(key: String, defaultValue: Int): Flow<Int> = readInt(key, defaultValue)

	/**
	 * [Flow] accessor for a nullable [Int] preference — alias for [readInt].
	 */
	protected fun intPrefFlow(key: String): Flow<Int?> = readInt(key)

	/**
	 * Create a property delegate for a [Long] preference.
	 */
	protected fun longPref(key: String, defaultValue: Long): ReadWriteProperty<Any?, Long> =
		object : ReadWriteProperty<Any?, Long> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): Long = readLongValue(key, defaultValue)
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) {
				writeLongAsync(key, value)
			}
		}

	/**
	 * Create a property delegate for a nullable [Long] preference.
	 */
	protected fun longPref(key: String): ReadWriteProperty<Any?, Long?> =
		object : ReadWriteProperty<Any?, Long?> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): Long? = readLongValue(key)
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: Long?) {
				writeLongAsync(key, value)
			}
		}

	/**
	 * [Flow] accessor for a [Long] preference — alias for [readLong].
	 */
	protected fun longPrefFlow(key: String, defaultValue: Long): Flow<Long> = readLong(key, defaultValue)

	/**
	 * [Flow] accessor for a nullable [Long] preference — alias for [readLong].
	 */
	protected fun longPrefFlow(key: String): Flow<Long?> = readLong(key)

	/**
	 * Create a property delegate for a [Double] preference.
	 */
	protected fun doublePref(key: String, defaultValue: Double): ReadWriteProperty<Any?, Double> =
		object : ReadWriteProperty<Any?, Double> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): Double = readDoubleValue(key, defaultValue)
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: Double) {
				writeDoubleAsync(key, value)
			}
		}

	/**
	 * Create a property delegate for a nullable [Double] preference.
	 */
	protected fun doublePref(key: String): ReadWriteProperty<Any?, Double?> =
		object : ReadWriteProperty<Any?, Double?> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): Double? = readDoubleValue(key)
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: Double?) {
				writeDoubleAsync(key, value)
			}
		}

	/**
	 * [Flow] accessor for a [Double] preference — alias for [readDouble].
	 */
	protected fun doublePrefFlow(key: String, defaultValue: Double): Flow<Double> = readDouble(key, defaultValue)

	/**
	 * [Flow] accessor for a nullable [Double] preference — alias for [readDouble].
	 */
	protected fun doublePrefFlow(key: String): Flow<Double?> = readDouble(key)

	/**
	 * Create a property delegate for a [Boolean] preference.
	 */
	protected fun booleanPref(key: String, defaultValue: Boolean): ReadWriteProperty<Any?, Boolean> =
		object : ReadWriteProperty<Any?, Boolean> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = readBooleanValue(key, defaultValue)
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
				writeBooleanAsync(key, value)
			}
		}

	/**
	 * Create a property delegate for a nullable [Boolean] preference.
	 */
	protected fun booleanPref(key: String): ReadWriteProperty<Any?, Boolean?> =
		object : ReadWriteProperty<Any?, Boolean?> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean? = readBooleanValue(key)
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean?) {
				writeBooleanAsync(key, value)
			}
		}

	/**
	 * [Flow] accessor for a [Boolean] preference — alias for [readBoolean].
	 */
	protected fun booleanPrefFlow(key: String, defaultValue: Boolean): Flow<Boolean> = readBoolean(key, defaultValue)

	/**
	 * [Flow] accessor for a nullable [Boolean] preference — alias for [readBoolean].
	 */
	protected fun booleanPrefFlow(key: String): Flow<Boolean?> = readBoolean(key)

	/**
	 * Create a property delegate for a [LocalDateTime] preference.
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun localDateTimePref(key: String, defaultValue: LocalDateTime): ReadWriteProperty<Any?, LocalDateTime> =
		object : ReadWriteProperty<Any?, LocalDateTime> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): LocalDateTime =
				readLocalDateTimeValue(key, defaultValue)
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: LocalDateTime) {
				writeLocalDateTimeAsync(key, value)
			}
		}

	/**
	 * Create a property delegate for a nullable [LocalDateTime] preference.
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun localDateTimePref(key: String): ReadWriteProperty<Any?, LocalDateTime?> =
		object : ReadWriteProperty<Any?, LocalDateTime?> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): LocalDateTime? = readLocalDateTimeValue(key)
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: LocalDateTime?) {
				writeLocalDateTimeAsync(key, value)
			}
		}

	/**
	 * [Flow] accessor for a [LocalDateTime] preference — alias for [readLocalDateTime].
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun localDateTimePrefFlow(key: String, defaultValue: LocalDateTime): Flow<LocalDateTime> =
		readLocalDateTime(key, defaultValue)

	/**
	 * [Flow] accessor for a nullable [LocalDateTime] preference — alias for [readLocalDateTime].
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun localDateTimePrefFlow(key: String): Flow<LocalDateTime?> = readLocalDateTime(key)

	/**
	 * Create a property delegate for a [LocalDate] preference.
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun localDatePref(key: String, defaultValue: LocalDate): ReadWriteProperty<Any?, LocalDate> =
		object : ReadWriteProperty<Any?, LocalDate> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): LocalDate = readLocalDateValue(key, defaultValue)
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: LocalDate) {
				writeLocalDateAsync(key, value)
			}
		}

	/**
	 * Create a property delegate for a nullable [LocalDate] preference.
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun localDatePref(key: String): ReadWriteProperty<Any?, LocalDate?> =
		object : ReadWriteProperty<Any?, LocalDate?> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): LocalDate? = readLocalDateValue(key)
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: LocalDate?) {
				writeLocalDateAsync(key, value)
			}
		}

	/**
	 * [Flow] accessor for a [LocalDate] preference — alias for [readLocalDate].
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun localDatePrefFlow(key: String, defaultValue: LocalDate): Flow<LocalDate> =
		readLocalDate(key, defaultValue)

	/**
	 * [Flow] accessor for a nullable [LocalDate] preference — alias for [readLocalDate].
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun localDatePrefFlow(key: String): Flow<LocalDate?> = readLocalDate(key)

	/**
	 * Create a property delegate for a [LocalTime] preference.
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun localTimePref(key: String, defaultValue: LocalTime): ReadWriteProperty<Any?, LocalTime> =
		object : ReadWriteProperty<Any?, LocalTime> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): LocalTime = readLocalTimeValue(key, defaultValue)
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: LocalTime) {
				writeLocalTimeAsync(key, value)
			}
		}

	/**
	 * Create a property delegate for a nullable [LocalTime] preference.
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun localTimePref(key: String): ReadWriteProperty<Any?, LocalTime?> =
		object : ReadWriteProperty<Any?, LocalTime?> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): LocalTime? = readLocalTimeValue(key)
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: LocalTime?) {
				writeLocalTimeAsync(key, value)
			}
		}

	/**
	 * [Flow] accessor for a [LocalTime] preference — alias for [readLocalTime].
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun localTimePrefFlow(key: String, defaultValue: LocalTime): Flow<LocalTime> =
		readLocalTime(key, defaultValue)

	/**
	 * [Flow] accessor for a nullable [LocalTime] preference — alias for [readLocalTime].
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun localTimePrefFlow(key: String): Flow<LocalTime?> = readLocalTime(key)

	/**
	 * Create a property delegate for an [Enum] preference with a non-null default.
	 *
	 * @param key The key to read/write the value for
	 * @param default Value returned if the key is absent or cannot be parsed
	 */
	protected inline fun <reified T : Enum<*>> enumPref(key: String, default: T): ReadWriteProperty<Any?, T> =
		enumPrefInternal(key, default, T::class.java)

	/**
	 * Create a property delegate for a nullable [Enum] preference.
	 *
	 * Assigning null removes the key.
	 */
	protected inline fun <reified T : Enum<*>> enumPref(key: String): ReadWriteProperty<Any?, T?> =
		enumPrefInternal(key, T::class.java)

	/**
	 * Non-inline implementation so the anonymous [ReadWriteProperty] is nested in
	 * `BaseDataStoreHelper` itself (giving it synthetic access to the protected
	 * `readStringValue` / `writeEnumAsync` helpers) instead of being emitted inside
	 * the subclass — which would lose protected access at the JVM level.
	 */
	@PublishedApi
	internal fun <T : Enum<*>> enumPrefInternal(
		key: String,
		default: T,
		enumClass: Class<T>
	): ReadWriteProperty<Any?, T> {
		val constants = enumClass.enumConstants
		return object : ReadWriteProperty<Any?, T> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): T {
				val value = readStringValue(key).orEmpty()
				return if (value.isBlank()) default
				else constants?.firstOrNull { it.name == value } ?: default
			}
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
				writeEnumAsync(key, value)
			}
		}
	}

	@PublishedApi
	internal fun <T : Enum<*>> enumPrefInternal(
		key: String,
		enumClass: Class<T>
	): ReadWriteProperty<Any?, T?> {
		val constants = enumClass.enumConstants
		return object : ReadWriteProperty<Any?, T?> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): T? {
				val value = readStringValue(key)?.takeIf { it.isNotBlank() } ?: return null
				return constants?.firstOrNull { it.name == value }
			}
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
				writeEnumAsync(key, value)
			}
		}
	}

	/**
	 * [Flow] accessor for an [Enum] preference — alias for [readEnum].
	 */
	protected inline fun <reified T : Enum<*>> enumPrefFlow(key: String, default: T): Flow<T> = readEnum(key, default)

	/**
	 * [Flow] accessor for a nullable [Enum] preference — alias for [readEnum].
	 */
	protected inline fun <reified T : Enum<*>> enumPrefFlow(key: String): Flow<T?> = readEnum(key)

	companion object {
		val supervisorJob = SupervisorJob()
	}
}
