package com.duck.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.duck.prefshelper.BaseDataStoreHelper
import com.duck.prefshelper.BasePrefsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PrefsHelper(context: Context) {
	private val normalPrefs by lazy { NormalPrefs(context) }
	private val devicePrefs by lazy { DevicePrefs(context) }
	private val normalDataStore by lazy { NormalDataStore.getInstance(context) }

	var exampleNormalValue by normalPrefs::exampleValue

	var exampleDeviceValue by devicePrefs::exampleValue

	var exampleDataStoreValue by normalDataStore::exampleValue
	val exampleDataStoreValueFlow = normalDataStore.exampleValueFlow

	suspend fun clearPrefs() {
		normalPrefs.clearPrefs()
		normalDataStore.clearPrefs()
	}
}

class NormalPrefs(context: Context) : BasePrefsHelper() {
	override val sharedPreferences: SharedPreferences =
		context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

	var exampleValue: String
		get() = getString(KEY_EXAMPLE)
		set(value) = setString(KEY_EXAMPLE, value)

	companion object {
		const val KEY_EXAMPLE = "example_key"
	}
}

class DevicePrefs(context: Context) : BasePrefsHelper() {
	override val sharedPreferences: SharedPreferences =
		context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)

	var exampleValue: String
		get() = getString(KEY_EXAMPLE)
		set(value) = setString(KEY_EXAMPLE, value)

	companion object {
		const val KEY_EXAMPLE = "example_key"
	}
}

class NormalDataStore private constructor(context: Context) : BaseDataStoreHelper(context, "normal_dataStore") {

	var exampleValue: String
		get() = readStringValue(KEY_EXAMPLE) ?: ""
		set(value) {
			CoroutineScope(Dispatchers.IO).launch {
				writeString(KEY_EXAMPLE, value)
			}
		}
	val exampleValueFlow = readString(KEY_EXAMPLE)

	companion object {
		const val KEY_EXAMPLE = "example_key"

		@Volatile
		private var INSTANCE: NormalDataStore? = null
		fun getInstance(context: Context) =
			INSTANCE ?: synchronized(this) {
				INSTANCE ?: NormalDataStore(context).also {
					INSTANCE = it
				}
			}
	}
}