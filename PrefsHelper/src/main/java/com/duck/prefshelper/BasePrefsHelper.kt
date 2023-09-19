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

	open suspend fun clearPrefs(): Unit = withContext(Dispatchers.IO) {
		with(sharedPreferences.edit()) {
			clear()
			commit()
		}
	}

	fun contains(key: String): Boolean {
		return sharedPreferences.contains(key)
	}

	fun setString(key: String, value: String) {
		with(sharedPreferences.edit()) {
			putString(key, value)
			apply()
		}
	}

	fun getString(key: String, defValue: String = ""): String {
		return sharedPreferences.getString(key, defValue) ?: defValue
	}

	fun setInt(key: String, value: Int) {
		with(sharedPreferences.edit()) {
			putInt(key, value)
			apply()
		}
	}

	fun getInt(key: String, defValue: Int = 0): Int {
		return sharedPreferences.getInt(key, defValue)
	}

	fun setLong(key: String, value: Long) {
		with(sharedPreferences.edit()) {
			putLong(key, value)
			apply()
		}
	}

	fun getLong(key: String, defValue: Long = 0): Long {
		return sharedPreferences.getLong(key, defValue)
	}

	fun setDate(key: String, value: Date?) {
		with(sharedPreferences.edit()) {
			putLong(key, value?.time ?: -1L)
			apply()
		}
	}

	fun getDate(key: String): Date? {
		val time = sharedPreferences.getLong(key, -1L)
		return if(time == -1L) null else Date(time)
	}

	fun setBoolean(key: String, value: Boolean) {
		with(sharedPreferences.edit()) {
			putBoolean(key, value)
			apply()
		}
	}

	fun getBoolean(key: String, defValue: Boolean): Boolean {
		return sharedPreferences.getBoolean(key, defValue)
	}

	@RequiresApi(Build.VERSION_CODES.O)
	fun setLocalDateTime(key: String, value: LocalDateTime?) {
		with(sharedPreferences.edit()) {
			putLong(key, value?.toEpochSecond(ZoneOffset.UTC) ?: -1L)
			apply()
		}
	}

	@RequiresApi(Build.VERSION_CODES.O)
	fun getLocalDateTime(key: String): LocalDateTime? {
		val time = sharedPreferences.getLong(key, -1L)
		return if(time == -1L) null else LocalDateTime.ofEpochSecond(time, 0, ZoneOffset.UTC)
	}

	@RequiresApi(Build.VERSION_CODES.O)
	fun setLocalDate(key: String, value: LocalDate?) {
		with(sharedPreferences.edit()) {
			putLong(key, value?.toEpochDay() ?: -1L)
			apply()
		}
	}

	@RequiresApi(Build.VERSION_CODES.O)
	fun getLocalDate(key: String): LocalDate? {
		val time = sharedPreferences.getLong(key, -1L)
		return if(time == -1L) null else LocalDate.ofEpochDay(time)
	}

	@RequiresApi(Build.VERSION_CODES.O)
	fun setLocalTime(key: String, value: LocalTime?) {
		with(sharedPreferences.edit()) {
			putLong(key, value?.toSecondOfDay()?.toLong() ?: -1L)
			apply()
		}
	}

	@RequiresApi(Build.VERSION_CODES.O)
	fun getLocalTime(key: String): LocalTime? {
		val time = sharedPreferences.getLong(key, -1L)
		return if(time == -1L) null else LocalTime.ofSecondOfDay(time)
	}

	fun setEnum(key: String, value: Enum<*>?) {
		setString(key, value?.name ?: "")
	}

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