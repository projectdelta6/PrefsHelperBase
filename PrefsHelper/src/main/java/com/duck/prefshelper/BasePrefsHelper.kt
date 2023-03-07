package com.duck.prefshelper

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
}