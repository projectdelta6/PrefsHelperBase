package com.duck.prefshelper

import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Date
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext

abstract class BasePrefsHelper(
	protected val coroutineContext: CoroutineContext = Dispatchers.IO + SupervisorJob()
) {
	/**
	 * The [SharedPreferences] instance to use
	 */
	protected abstract val sharedPreferences: SharedPreferences

	/**
	 * Clear all preferences
	 */
	open suspend fun clearPrefs(): Unit = withContext(coroutineContext) {
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
}