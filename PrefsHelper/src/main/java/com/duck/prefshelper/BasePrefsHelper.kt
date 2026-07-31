package com.duck.prefshelper

import android.content.SharedPreferences
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Date
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * A base class for SharedPreferences helpers
 *
 * Provides common functionality for storing and retrieving preferences of various types
 * including String, Int, Long, Date, Boolean, LocalDateTime, LocalDate, LocalTime, and Enum.
 *
 * Reads and writes are synchronous; only [clearPrefs] does blocking work, and it hops to
 * [dispatcher] to do it. The dispatcher carries no [kotlinx.coroutines.Job], so cancelling the
 * caller correctly cancels the clear.
 *
 * @param dispatcher The [CoroutineDispatcher] `suspend` functions run on, defaults to [Dispatchers.IO]
 */
abstract class BasePrefsHelper(
	protected val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
	/**
	 * The [SharedPreferences] instance to use
	 */
	protected abstract val sharedPreferences: SharedPreferences

	/**
	 * Clear all preferences
	 */
	open suspend fun clearPrefs(): Unit = withContext(dispatcher) {
		sharedPreferences.edit(commit = true) { clear() }
	}

	/**
	 * Check if a preference for the given key exists
	 */
	fun contains(key: String): Boolean {
		return sharedPreferences.contains(key)
	}

	/**
	 * Set a [String] preference
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	fun setString(key: String, value: String) {
		sharedPreferences.edit {
			putString(key, value)
		}
	}

	/**
	 * Get a [String] preference
	 *
	 * @param key The key to get the value for
	 * @param defValue The default value to return if the key does not exist or the value is null, defaults to an empty string
	 */
	fun getString(key: String, defValue: String = ""): String {
		return sharedPreferences.getString(key, defValue) ?: defValue
	}

	/**
	 * Set an [Int] preference
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	fun setInt(key: String, value: Int) {
		sharedPreferences.edit {
			putInt(key, value)
		}
	}

	/**
	 * Get an [Int] preference
	 *
	 * @param key The key to get the value for
	 * @param defValue The default value to return if the key does not exist or the value is null, defaults to 0
	 */
	fun getInt(key: String, defValue: Int = 0): Int {
		return sharedPreferences.getInt(key, defValue)
	}

	/**
	 * Set a [Long] preference
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	fun setLong(key: String, value: Long) {
		sharedPreferences.edit {
			putLong(key, value)
		}
	}

	/**
	 * Get a [Long] preference
	 *
	 * @param key The key to get the value for
	 * @param defValue The default value to return if the key does not exist or the value is null, defaults to 0
	 */
	fun getLong(key: String, defValue: Long = 0): Long {
		return sharedPreferences.getLong(key, defValue)
	}

	/**
	 * Set a [Double] preference
	 *
	 * [SharedPreferences] has no double primitive, so the value is stored as its raw IEEE-754 bit
	 * pattern in a [Long]. Always read it back with [getDouble] — calling [getLong] on the same key
	 * returns the bit pattern, not the number.
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	fun setDouble(key: String, value: Double) {
		sharedPreferences.edit {
			putLong(key, value.toRawBits())
		}
	}

	/**
	 * Get a [Double] preference
	 *
	 * @param key The key to get the value for
	 * @param defValue The default value to return if the key does not exist, defaults to 0.0
	 */
	fun getDouble(key: String, defValue: Double = 0.0): Double {
		if (!sharedPreferences.contains(key)) return defValue
		return Double.fromBits(sharedPreferences.getLong(key, 0L))
	}

	/**
	 * Set a [Float] preference
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	fun setFloat(key: String, value: Float) {
		sharedPreferences.edit {
			putFloat(key, value)
		}
	}

	/**
	 * Get a [Float] preference
	 *
	 * @param key The key to get the value for
	 * @param defValue The default value to return if the key does not exist, defaults to 0f
	 */
	fun getFloat(key: String, defValue: Float = 0f): Float {
		return sharedPreferences.getFloat(key, defValue)
	}

	/**
	 * Set a [Set] of [String] preference
	 *
	 * A copy is stored rather than the caller's instance: [SharedPreferences] keeps a reference to
	 * the set it is handed, and mutating it afterwards corrupts the stored value.
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	fun setStringSet(key: String, value: Set<String>) {
		sharedPreferences.edit {
			putStringSet(key, LinkedHashSet(value))
		}
	}

	/**
	 * Get a [Set] of [String] preference
	 *
	 * Returns a defensive copy. [SharedPreferences.getStringSet] documents its result as one you
	 * must not modify, with undefined behaviour if you do — this returns a set you own instead.
	 *
	 * @param key The key to get the value for
	 * @param defValue The default value to return if the key does not exist, defaults to an empty set
	 */
	fun getStringSet(key: String, defValue: Set<String> = emptySet()): Set<String> {
		val stored = sharedPreferences.getStringSet(key, null) ?: return defValue
		return LinkedHashSet(stored)
	}

	/**
	 * Set a [Date] preference
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	fun setDate(key: String, value: Date?) {
		sharedPreferences.edit {
			putLong(key, value?.time ?: -1L)
		}
	}

	/**
	 * Get a [Date] preference
	 *
	 * @param key The key to get the value for
	 * @return The date stored under the key, or null if the key does not exist or the value is null
	 */
	fun getDate(key: String): Date? {
		val time = sharedPreferences.getLong(key, -1L)
		return if(time == -1L) null else Date(time)
	}

	/**
	 * Set an [Instant] preference
	 *
	 * Stored as epoch milliseconds, using the same -1L sentinel as the other temporal types here.
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	fun setInstant(key: String, value: Instant?) {
		sharedPreferences.edit {
			putLong(key, value?.toEpochMilli() ?: -1L)
		}
	}

	/**
	 * Get an [Instant] preference
	 *
	 * @param key The key to get the value for
	 * @return The Instant stored under the key, or null if the key does not exist or the value is null
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	fun getInstant(key: String): Instant? {
		val millis = sharedPreferences.getLong(key, -1L)
		return if (millis == -1L) null else Instant.ofEpochMilli(millis)
	}

	/**
	 * Set a [ByteArray] preference
	 *
	 * [SharedPreferences] has no binary type, so the value is stored Base64-encoded
	 * ([Base64.NO_WRAP]) in a [String]. Assigning null removes the key.
	 *
	 * @param key The key to store the value under
	 * @param value The value to store, or null to remove the key
	 */
	fun setByteArray(key: String, value: ByteArray?) {
		sharedPreferences.edit {
			if (value == null) remove(key) else putString(key, Base64.encodeToString(value, Base64.NO_WRAP))
		}
	}

	/**
	 * Get a [ByteArray] preference
	 *
	 * @param key The key to get the value for
	 * @return The bytes stored under the key, or null if the key does not exist or the stored value
	 * is not valid Base64
	 */
	fun getByteArray(key: String): ByteArray? {
		val encoded = sharedPreferences.getString(key, null) ?: return null
		return try {
			Base64.decode(encoded, Base64.NO_WRAP)
		} catch (e: IllegalArgumentException) {
			Log.w("BasePrefsHelper", "Could not decode Base64 ByteArray for \"$key\"", e)
			null
		}
	}

	/**
	 * Set a [Boolean] preference
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	fun setBoolean(key: String, value: Boolean) {
		sharedPreferences.edit {
			putBoolean(key, value)
		}
	}

	/**
	 * Get a [Boolean] preference
	 *
	 * @param key The key to get the value for
	 * @param defValue The default value to return if the key does not exist or the value is null, defaults to false
	 */
	fun getBoolean(key: String, defValue: Boolean): Boolean {
		return sharedPreferences.getBoolean(key, defValue)
	}

	/**
	 * Set a [LocalDateTime] preference
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	fun setLocalDateTime(key: String, value: LocalDateTime?) {
		sharedPreferences.edit {
			putLong(key, value?.toEpochSecond(ZoneOffset.UTC) ?: -1L)
		}
	}

	/**
	 * Get a [LocalDateTime] preference
	 *
	 * @param key The key to get the value for
	 * @return The LocalDateTime stored under the key, or null if the key does not exist or the value is null
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	fun getLocalDateTime(key: String): LocalDateTime? {
		val time = sharedPreferences.getLong(key, -1L)
		return if(time == -1L) null else LocalDateTime.ofEpochSecond(time, 0, ZoneOffset.UTC)
	}

	/**
	 * Set a [LocalDate] preference
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	fun setLocalDate(key: String, value: LocalDate?) {
		sharedPreferences.edit {
			putLong(key, value?.toEpochDay() ?: -1L)
		}
	}

	/**
	 * Get a [LocalDate] preference
	 *
	 * @param key The key to get the value for
	 * @return The LocalDate stored under the key, or null if the key does not exist or the value is null
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	fun getLocalDate(key: String): LocalDate? {
		val time = sharedPreferences.getLong(key, -1L)
		return if(time == -1L) null else LocalDate.ofEpochDay(time)
	}

	/**
	 * Set a [LocalTime] preference
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	fun setLocalTime(key: String, value: LocalTime?) {
		sharedPreferences.edit {
			putLong(key, value?.toSecondOfDay()?.toLong() ?: -1L)
		}
	}

	/**
	 * Get a [LocalTime] preference
	 *
	 * @param key The key to get the value for
	 * @return The LocalTime stored under the key, or null if the key does not exist or the value is null
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	fun getLocalTime(key: String): LocalTime? {
		val time = sharedPreferences.getLong(key, -1L)
		return if(time == -1L) null else LocalTime.ofSecondOfDay(time)
	}

	/**
	 * Set an [Enum] preference
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	fun setEnum(key: String, value: Enum<*>?) {
		setString(key, value?.name ?: "")
	}

	/**
	 * Set a [Set] of [Enum] preference
	 *
	 * Stored as a set of [Enum.name] values.
	 *
	 * @param key The key to store the value under
	 * @param value The value to store
	 */
	fun setEnumSet(key: String, value: Set<Enum<*>>) {
		setStringSet(key, value.mapTo(LinkedHashSet()) { it.name })
	}

	/**
	 * Get a [Set] of [Enum] preference
	 *
	 * Stored names that no longer match a constant of [T] are dropped rather than throwing, so
	 * removing an enum constant does not break reads of previously stored data.
	 *
	 * @param key The key to get the value for
	 * @param default The value to return if the key does not exist, defaults to an empty set
	 */
	inline fun <reified T : Enum<*>> getEnumSet(key: String, default: Set<T> = emptySet()): Set<T> {
		val names = getStringSet(key, emptySet())
		if (names.isEmpty()) return default
		return try {
			val constants = T::class.java.enumConstants ?: return default
			names.mapNotNullTo(LinkedHashSet()) { name -> constants.firstOrNull { it.name == name } }
		} catch (e: Exception) {
			Log.w("BasePrefsHelper", "Could not get Enum set of ${T::class.simpleName} for \"$key\"", e)
			default
		}
	}

	/**
	 * Get an [Enum] preference
	 *
	 * @param key The key to get the value for
	 * @return The Enum stored under the key, or null if the key does not exist or the value is null
	 */
	@OptIn(ExperimentalContracts::class)
	inline fun <reified T : Enum<*>> getEnum(key: String, default: T? = null): T? {
		contract {
			returnsNotNull() implies (default != null)
		}
		val value = getString(key)
		return if (value.isBlank()) default else try {
			T::class.java.enumConstants?.firstOrNull { it.name == value } ?: default
		} catch (e: Exception) {
			Log.w("BasePrefsHelper", "Could not get Enum of ${T::class.simpleName} for \"$value\"", e)
			default
		}
	}

	/**
	 * Create a property delegate for a [String] preference.
	 *
	 * @param key The key to read/write the value for
	 * @param defaultValue Value returned if the key is absent
	 */
	protected fun stringPref(key: String, defaultValue: String): ReadWriteProperty<Any?, String> =
		object : ReadWriteProperty<Any?, String> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): String = getString(key, defaultValue)
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) = setString(key, value)
		}

	/**
	 * Create a property delegate for a nullable [String] preference.
	 *
	 * Returns null when the key is absent. Assigning null removes the key.
	 */
	protected fun stringPref(key: String): ReadWriteProperty<Any?, String?> =
		object : ReadWriteProperty<Any?, String?> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): String? =
				if (contains(key)) sharedPreferences.getString(key, null) else null
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: String?) {
				if (value == null) sharedPreferences.edit { remove(key) } else setString(key, value)
			}
		}

	/**
	 * Create a property delegate for an [Int] preference.
	 *
	 * @param key The key to read/write the value for
	 * @param defaultValue Value returned if the key is absent
	 */
	protected fun intPref(key: String, defaultValue: Int): ReadWriteProperty<Any?, Int> =
		object : ReadWriteProperty<Any?, Int> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): Int = getInt(key, defaultValue)
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) = setInt(key, value)
		}

	/**
	 * Create a property delegate for a nullable [Int] preference.
	 *
	 * Returns null when the key is absent. Assigning null removes the key.
	 */
	protected fun intPref(key: String): ReadWriteProperty<Any?, Int?> =
		object : ReadWriteProperty<Any?, Int?> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): Int? =
				if (contains(key)) getInt(key) else null
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int?) {
				if (value == null) sharedPreferences.edit { remove(key) } else setInt(key, value)
			}
		}

	/**
	 * Create a property delegate for a [Long] preference.
	 *
	 * @param key The key to read/write the value for
	 * @param defaultValue Value returned if the key is absent
	 */
	protected fun longPref(key: String, defaultValue: Long): ReadWriteProperty<Any?, Long> =
		object : ReadWriteProperty<Any?, Long> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): Long = getLong(key, defaultValue)
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) = setLong(key, value)
		}

	/**
	 * Create a property delegate for a nullable [Long] preference.
	 *
	 * Returns null when the key is absent. Assigning null removes the key.
	 */
	protected fun longPref(key: String): ReadWriteProperty<Any?, Long?> =
		object : ReadWriteProperty<Any?, Long?> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): Long? =
				if (contains(key)) getLong(key) else null
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: Long?) {
				if (value == null) sharedPreferences.edit { remove(key) } else setLong(key, value)
			}
		}

	/**
	 * Create a property delegate for a [Double] preference.
	 *
	 * @param key The key to read/write the value for
	 * @param defaultValue Value returned if the key is absent
	 */
	protected fun doublePref(key: String, defaultValue: Double): ReadWriteProperty<Any?, Double> =
		object : ReadWriteProperty<Any?, Double> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): Double = getDouble(key, defaultValue)
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: Double) = setDouble(key, value)
		}

	/**
	 * Create a property delegate for a nullable [Double] preference.
	 *
	 * Returns null when the key is absent. Assigning null removes the key.
	 */
	protected fun doublePref(key: String): ReadWriteProperty<Any?, Double?> =
		object : ReadWriteProperty<Any?, Double?> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): Double? =
				if (contains(key)) getDouble(key) else null
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: Double?) {
				if (value == null) sharedPreferences.edit { remove(key) } else setDouble(key, value)
			}
		}

	/**
	 * Create a property delegate for a [Float] preference.
	 *
	 * @param key The key to read/write the value for
	 * @param defaultValue Value returned if the key is absent
	 */
	protected fun floatPref(key: String, defaultValue: Float): ReadWriteProperty<Any?, Float> =
		object : ReadWriteProperty<Any?, Float> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): Float = getFloat(key, defaultValue)
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) = setFloat(key, value)
		}

	/**
	 * Create a property delegate for a nullable [Float] preference.
	 *
	 * Returns null when the key is absent. Assigning null removes the key.
	 */
	protected fun floatPref(key: String): ReadWriteProperty<Any?, Float?> =
		object : ReadWriteProperty<Any?, Float?> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): Float? =
				if (contains(key)) getFloat(key) else null
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float?) {
				if (value == null) sharedPreferences.edit { remove(key) } else setFloat(key, value)
			}
		}

	/**
	 * Create a property delegate for a [Set] of [String] preference.
	 *
	 * @param key The key to read/write the value for
	 * @param defaultValue Value returned if the key is absent
	 */
	protected fun stringSetPref(key: String, defaultValue: Set<String>): ReadWriteProperty<Any?, Set<String>> =
		object : ReadWriteProperty<Any?, Set<String>> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): Set<String> = getStringSet(key, defaultValue)
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: Set<String>) = setStringSet(key, value)
		}

	/**
	 * Create a property delegate for a nullable [Set] of [String] preference.
	 *
	 * Returns null when the key is absent. Assigning null removes the key.
	 */
	protected fun stringSetPref(key: String): ReadWriteProperty<Any?, Set<String>?> =
		object : ReadWriteProperty<Any?, Set<String>?> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): Set<String>? =
				if (contains(key)) getStringSet(key) else null
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: Set<String>?) {
				if (value == null) sharedPreferences.edit { remove(key) } else setStringSet(key, value)
			}
		}

	/**
	 * Create a property delegate for a [ByteArray] preference.
	 *
	 * Returns null when the key is absent. Assigning null removes the key.
	 */
	protected fun byteArrayPref(key: String): ReadWriteProperty<Any?, ByteArray?> =
		object : ReadWriteProperty<Any?, ByteArray?> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): ByteArray? = getByteArray(key)
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: ByteArray?) = setByteArray(key, value)
		}

	/**
	 * Create a property delegate for a [Boolean] preference.
	 *
	 * @param key The key to read/write the value for
	 * @param defaultValue Value returned if the key is absent
	 */
	protected fun booleanPref(key: String, defaultValue: Boolean): ReadWriteProperty<Any?, Boolean> =
		object : ReadWriteProperty<Any?, Boolean> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = getBoolean(key, defaultValue)
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) = setBoolean(key, value)
		}

	/**
	 * Create a property delegate for a nullable [Boolean] preference.
	 *
	 * Returns null when the key is absent. Assigning null removes the key.
	 */
	protected fun booleanPref(key: String): ReadWriteProperty<Any?, Boolean?> =
		object : ReadWriteProperty<Any?, Boolean?> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean? =
				if (contains(key)) getBoolean(key, false) else null
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean?) {
				if (value == null) sharedPreferences.edit { remove(key) } else setBoolean(key, value)
			}
		}

	/**
	 * Create a property delegate for a nullable [Instant] preference.
	 *
	 * Assigning null stores the -1L sentinel (matching [setInstant]/[getInstant]).
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun instantPref(key: String): ReadWriteProperty<Any?, Instant?> =
		object : ReadWriteProperty<Any?, Instant?> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): Instant? = getInstant(key)
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: Instant?) = setInstant(key, value)
		}

	/**
	 * Create a property delegate for a nullable [Date] preference.
	 *
	 * Assigning null stores the -1L sentinel (matching [setDate]/[getDate]).
	 */
	protected fun datePref(key: String): ReadWriteProperty<Any?, Date?> =
		object : ReadWriteProperty<Any?, Date?> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): Date? = getDate(key)
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: Date?) = setDate(key, value)
		}

	/**
	 * Create a property delegate for a nullable [LocalDateTime] preference.
	 *
	 * Assigning null stores the -1L sentinel (matching [setLocalDateTime]/[getLocalDateTime]).
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun localDateTimePref(key: String): ReadWriteProperty<Any?, LocalDateTime?> =
		object : ReadWriteProperty<Any?, LocalDateTime?> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): LocalDateTime? = getLocalDateTime(key)
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: LocalDateTime?) = setLocalDateTime(key, value)
		}

	/**
	 * Create a property delegate for a nullable [LocalDate] preference.
	 *
	 * Assigning null stores the -1L sentinel (matching [setLocalDate]/[getLocalDate]).
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun localDatePref(key: String): ReadWriteProperty<Any?, LocalDate?> =
		object : ReadWriteProperty<Any?, LocalDate?> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): LocalDate? = getLocalDate(key)
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: LocalDate?) = setLocalDate(key, value)
		}

	/**
	 * Create a property delegate for a nullable [LocalTime] preference.
	 *
	 * Assigning null stores the -1L sentinel (matching [setLocalTime]/[getLocalTime]).
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	protected fun localTimePref(key: String): ReadWriteProperty<Any?, LocalTime?> =
		object : ReadWriteProperty<Any?, LocalTime?> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): LocalTime? = getLocalTime(key)
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: LocalTime?) = setLocalTime(key, value)
		}

	/**
	 * Create a property delegate for an [Enum] preference with a non-null default.
	 *
	 * @param key The key to read/write the value for
	 * @param default Value returned if the key is absent or cannot be parsed
	 */
	protected inline fun <reified T : Enum<*>> enumPref(key: String, default: T): ReadWriteProperty<Any?, T> {
		val constants = T::class.java.enumConstants
		return object : ReadWriteProperty<Any?, T> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): T {
				val value = getString(key)
				return if (value.isBlank()) default
				else constants?.firstOrNull { it.name == value } ?: default
			}
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) = setEnum(key, value)
		}
	}

	/**
	 * Create a property delegate for a nullable [Enum] preference.
	 *
	 * Returns null when the key is absent or cannot be parsed. Assigning null clears the stored value.
	 */
	protected inline fun <reified T : Enum<*>> enumPref(key: String): ReadWriteProperty<Any?, T?> {
		val constants = T::class.java.enumConstants
		return object : ReadWriteProperty<Any?, T?> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): T? {
				val value = getString(key)
				return if (value.isBlank()) null
				else constants?.firstOrNull { it.name == value }
			}
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) = setEnum(key, value)
		}
	}

	/**
	 * Create a property delegate for a [Set] of [Enum] preference.
	 *
	 * Stored names that no longer match a constant of [T] are dropped on read.
	 *
	 * @param key The key to read/write the value for
	 * @param defaultValue Value returned if the key is absent
	 */
	protected inline fun <reified T : Enum<*>> enumSetPref(
		key: String,
		defaultValue: Set<T> = emptySet(),
	): ReadWriteProperty<Any?, Set<T>> {
		val constants = T::class.java.enumConstants
		return object : ReadWriteProperty<Any?, Set<T>> {
			override fun getValue(thisRef: Any?, property: KProperty<*>): Set<T> {
				val names = getStringSet(key, emptySet())
				if (names.isEmpty()) return defaultValue
				return names.mapNotNullTo(LinkedHashSet()) { name -> constants?.firstOrNull { it.name == name } }
			}
			override fun setValue(thisRef: Any?, property: KProperty<*>, value: Set<T>) = setEnumSet(key, value)
		}
	}
}