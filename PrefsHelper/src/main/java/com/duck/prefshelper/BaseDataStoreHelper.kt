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

/**
 * Base class for DataStore
 *
 * This should be implemented as a Singleton instance.
 */
abstract class BaseDataStoreHelper(
	context: Context,
	preferenceName: String,
	protected val coroutineContext: CoroutineContext = Dispatchers.IO + SupervisorJob()
) {
	//Instance of DataStore
	private val Context.dataStoreInstance: DataStore<Preferences> by preferencesDataStore(name = preferenceName)

	protected val dataStore: DataStore<Preferences> = context.dataStoreInstance

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
}
