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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Base class for DataStore
 *
 * This should be implemented as a Singleton instance.
 */
abstract class BaseDataStoreHelper(
	context: Context,
	preferenceName: String
) {
	//Instance of DataStore
	private val Context.dataStoreInstance: DataStore<Preferences> by preferencesDataStore(name = preferenceName)

	protected val dataStore: DataStore<Preferences> = context.dataStoreInstance

	open suspend fun clearPrefs(): Unit = withContext(Dispatchers.IO) {
		dataStore.edit { preferences ->
			preferences.clear()
		}
	}

	/**
	 * Add string data to data Store
	 */
	protected suspend fun writeString(key: String, value: String) {
		dataStore.edit { pref -> pref[stringPreferencesKey(key)] = value }
	}

	/**
	 * Read string from the data store preferences
	 */
	protected fun readString(key: String): Flow<String> {
		return dataStore.data.map { pref ->
			pref[stringPreferencesKey(key)] ?: ""
		}
	}

	/**
	 * Read string value is a blocking way from the data store preferences
	 */
	protected fun readStringValue(key: String): String? {
		return runBlocking {
			withTimeoutOrNull(2000) {
				dataStore.data.first()[stringPreferencesKey(key)]
			}
		}
	}

	/**
	 * Add Integer to the data store
	 */
	protected suspend fun writeInt(key: String, value: Int) {
		dataStore.edit { pref -> pref[intPreferencesKey(key)] = value }
	}

	/**
	 * Reading the Int value from the data store
	 */
	protected fun readInt(key: String): Flow<Int?> {
		return dataStore.data.map { pref ->
			pref[intPreferencesKey(key)]
		}
	}

	/**
	 * Read Int value is a blocking way from the data store preferences
	 */
	protected fun readIntValue(key: String): Int? {
		return runBlocking {
			withTimeoutOrNull(2000) {
				dataStore.data.first()[intPreferencesKey(key)]
			}
		}
	}

	/**
	 * Adding Double to the data store
	 */
	protected suspend fun writeDouble(key: String, value: Double) {
		dataStore.edit { pref -> pref[doublePreferencesKey(key)] = value }
	}

	/**
	 * Reading the double value from the data store
	 */
	protected fun readDouble(key: String): Flow<Double> {
		return dataStore.data.map { pref ->
			pref[doublePreferencesKey(key)] ?: 0.0
		}
	}

	/**
	 * Read Double value is a blocking way from the data store preferences
	 */
	protected fun readDoubleValue(key: String): Double? {
		return runBlocking {
			withTimeoutOrNull(2000) {
				dataStore.data.first()[doublePreferencesKey(key)]
			}
		}
	}

	/**
	 * Add Long to the data store
	 */
	protected suspend fun writeLong(key: String, value: Long) {
		dataStore.edit { pref -> pref[longPreferencesKey(key)] = value }
	}

	/**
	 * Reading the long from the data store
	 */
	protected fun readLong(key: String): Flow<Long> {
		return dataStore.data.map { pref ->
			pref[longPreferencesKey(key)] ?: 0L
		}
	}

	/**
	 * Read Long value is a blocking way from the data store preferences
	 */
	protected fun readLongValue(key: String): Long? {
		return runBlocking {
			withTimeoutOrNull(2000) {
				dataStore.data.first()[longPreferencesKey(key)]
			}
		}
	}

	/**
	 * Add Boolean to the data store
	 */
	protected suspend fun writeBool(key: String, value: Boolean) {
		dataStore.edit { pref -> pref[booleanPreferencesKey(key)] = value }
	}

	/**
	 * Read Boolean value is a blocking way from the data store preferences
	 */
	protected fun readBooleanValue(key: String): Boolean? {
		return runBlocking {
			withTimeoutOrNull(2000) {
				dataStore.data.first()[booleanPreferencesKey(key)]
			}
		}
	}

	protected fun readBooleanValue(key: String, defaultValue: Boolean): Boolean =
		readBooleanValue(key) ?: defaultValue

	/**
	 * Add Nullable Boolean to the data store
	 */
	protected suspend fun writeBool(key: String, value: Boolean?) {
		dataStore.edit { pref ->
			if (value == null) {
				pref.remove(booleanPreferencesKey(key))
			} else {
				pref[booleanPreferencesKey(key)] = value
			}
		}
	}

	/**
	 * Reading the Boolean from the data store
	 */
	protected fun readNullableBool(key: String): Flow<Boolean?> {
		return dataStore.data.map { pref ->
			pref[booleanPreferencesKey(key)]
		}
	}

	/**
	 * Reading the Boolean from the data store
	 */
	protected fun readBool(key: String): Flow<Boolean> {
		return readNullableBool(key).map { it == true }
	}

	/**
	 * Read Boolean value is a blocking way from the data store preferences
	 */
	suspend fun writeLocalDateTime(key: String, value: LocalDateTime?) {
		dataStore.edit { pref ->
			if (value == null) {
				pref.remove(stringPreferencesKey(key))
			} else {
				pref[stringPreferencesKey(key)] = value.toString()
			}
		}
	}

	/**
	 * Read a [LocalDateTime] preference as a [Flow].
	 *
	 * @param key The key to read the value for
	 * @return A [Flow] emitting the [LocalDateTime] stored under the key, or null if the key does not exist or the value is invalid
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	fun readLocalDateTime(key: String): Flow<LocalDateTime?> {
		return dataStore.data.map { pref ->
			pref[stringPreferencesKey(key)]?.let { LocalDateTime.parse(it) }
		}
	}

	/**
	 * Read a [LocalDateTime] preference.
	 *
	 * @param key The key to read the value for
	 * @return A [LocalDateTime] or null if the key does not exist or the value is invalid
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	fun readLocalDateTimeValue(key: String): LocalDateTime? {
		return runBlocking {
			withTimeoutOrNull(2000) {
				dataStore.data.first()[stringPreferencesKey(key)]?.let { LocalDateTime.parse(it) }
			}
		}
	}

	/**
	 * Add Nullable LocalDate to the data store
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	suspend fun writeLocalDate(key: String, value: LocalDate?) {
		dataStore.edit { pref ->
			if (value == null) {
				pref.remove(stringPreferencesKey(key))
			} else {
				pref[stringPreferencesKey(key)] = value.toString()
			}
		}
	}

	/**
	 * Read a [LocalDate] preference as a [Flow].
	 *
	 * @param key The key to read the value for
	 * @return A [Flow] emitting the [LocalDate] stored under the key, or null if the key does not exist or the value is invalid
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	fun readLocalDate(key: String): Flow<LocalDate?> {
		return dataStore.data.map { pref ->
			pref[stringPreferencesKey(key)]?.let { LocalDate.parse(it) }
		}
	}

	/**
	 * Read a [LocalDate] preference.
	 *
	 * @param key The key to read the value for
	 * @return A [LocalDate] or null if the key does not exist or the value is invalid
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	fun readLocalDateValue(key: String): LocalDate? {
		return runBlocking {
			withTimeoutOrNull(2000) {
				dataStore.data.first()[stringPreferencesKey(key)]?.let { LocalDate.parse(it) }
			}
		}
	}

	/**
	 * Add Nullable LocalTime to the data store
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	suspend fun writeLocalTime(key: String, value: LocalTime?) {
		dataStore.edit { pref ->
			if (value == null) {
				pref.remove(stringPreferencesKey(key))
			} else {
				pref[stringPreferencesKey(key)] = value.toString()
			}
		}
	}

	/**
	 * Read a [LocalTime] preference as a [Flow].
	 *
	 * @param key The key to read the value for
	 * @return A [Flow] emitting the [LocalTime] stored under the key, or null if the key does not exist or the value is invalid
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	fun readLocalTime(key: String): Flow<LocalTime?> {
		return dataStore.data.map { pref ->
			pref[stringPreferencesKey(key)]?.let { LocalTime.parse(it) }
		}
	}

	/**
	 * Read a [LocalTime] preference.
	 *
	 * @param key The key to read the value for
	 * @return A [LocalTime] or null if the key does not exist or the value is invalid
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	fun readLocalTimeValue(key: String): LocalTime? {
		return runBlocking {
			withTimeoutOrNull(2000) {
				dataStore.data.first()[stringPreferencesKey(key)]?.let { LocalTime.parse(it) }
			}
		}
	}

	/**
	 * Add an [Enum] preference
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	suspend fun writeEnum(key: String, value: Enum<*>?) {
		dataStore.edit { pref ->
			pref[stringPreferencesKey(key)] = value?.name ?: ""
		}
	}

	/**
	 * Read an [Enum] preference as a [Flow] with a non-null default.
	 *
	 * @param key The key to read the value for
	 * @param default The default Enum value to return if the key does not exist or the value is invalid
	 * @return A [Flow] emitting the Enum stored under the key, or the default value
	 */
	inline fun <reified T : Enum<*>> readEnum(key: String, default: T): Flow<T> {
		return `access$dataStore`.data.map { pref ->
			pref[stringPreferencesKey(key)]?.let { value ->
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
	}

	/**
	 * Read an [Enum] preference as a [Flow] without a default.
	 *
	 * @param key The key to read the value for
	 * @return A [Flow] emitting the Enum stored under the key, or null if the key does not exist or the value is invalid
	 */
	inline fun <reified T : Enum<*>> readEnum(key: String): Flow<T?> {
		return `access$dataStore`.data.map { pref ->
			pref[stringPreferencesKey(key)]?.let { value ->
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
	}

	/**
	 * Read an [Enum] preference as a blocking call.
	 *
	 * @param key The key to read the value for
	 * @param default The default Enum value to return if the key does not exist or the value is invalid
	 * @return The Enum stored under the key, or the default value
	 */
	@JvmName("readEnumValueNullable")
	inline fun <reified T : Enum<*>> readEnumValue(key: String, default: T? = null): T? {
		return runBlocking {
			withTimeoutOrNull(2000) {
				`access$dataStore`.data.first()[stringPreferencesKey(key)]?.let { value ->
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
			} ?: default
		}
	}

	/**
	 * Read an [Enum] preference as a blocking call.
	 *
	 * @param key The key to read the value for
	 * @param default The default Enum value to return if the key does not exist or the value is invalid
	 * @return The Enum stored under the key, or the default value
	 */
	inline fun <reified T : Enum<*>> readEnumValue(key: String, default: T): T {
		return runBlocking {
			withTimeoutOrNull(2000) {
				`access$dataStore`.data.first()[stringPreferencesKey(key)]?.let { value ->
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
			} ?: default
		}
	}

	@PublishedApi
	internal val `access$dataStore`: DataStore<Preferences>
		get() = dataStore
}