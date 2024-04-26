package com.duck.prefshelper

import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.*

abstract class BasePrefsHelper {
	protected abstract val sharedPreferences: SharedPreferences

	/**
	 * Clear all preferences
	 */
	open suspend fun clearPrefs(): Unit = withContext(Dispatchers.IO) {
		with(sharedPreferences.edit()) {
			clear()
			commit()
		}
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
		with(sharedPreferences.edit()) {
			putString(key, value)
			apply()
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
		with(sharedPreferences.edit()) {
			putInt(key, value)
			apply()
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
		with(sharedPreferences.edit()) {
			putLong(key, value)
			apply()
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
		with(sharedPreferences.edit()) {
			putLong(key, value?.time ?: -1L)
			apply()
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
		with(sharedPreferences.edit()) {
			putBoolean(key, value)
			apply()
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
		with(sharedPreferences.edit()) {
			putLong(key, value?.toEpochSecond(ZoneOffset.UTC) ?: -1L)
			apply()
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
		with(sharedPreferences.edit()) {
			putLong(key, value?.toEpochDay() ?: -1L)
			apply()
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
		with(sharedPreferences.edit()) {
			putLong(key, value?.toSecondOfDay()?.toLong() ?: -1L)
			apply()
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
	inline fun <reified T : Enum<*>> getEnum(enumClass: Class<T>, key: String): T? {
		val value = getString(key)
		return if (value.isBlank()) null else try {
			enumClass.enumConstants?.firstOrNull { it.name == value }
		} catch (e: Exception) {
			Log.w("BasePrefsHelper", "Could not get Enum of ${enumClass.simpleName} for \"$value\"", e)
			null
		}
	}
}