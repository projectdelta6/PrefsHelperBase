package com.duck.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.duck.prefshelper.BaseDataStoreHelper
import com.duck.prefshelper.BasePrefsHelper
import kotlinx.coroutines.CoroutineScope

/**
 * A façade over several backing preference stores, so the rest of the app injects one type and
 * cross-cutting operations like "clear everything on logout" live in one place.
 *
 * The sub-helpers arrive by injection rather than being constructed here — see
 * `com.duck.app.di.prefsModule`. That is what guarantees there is exactly one [NormalDataStore] in
 * the process, which DataStore requires; before 2.0 this sample hand-rolled a `getInstance()`
 * double-checked singleton to achieve the same thing.
 */
class PrefsHelper(
	private val normalPrefs: NormalPrefs,
	private val devicePrefs: DevicePrefs,
	private val normalDataStore: NormalDataStore,
) {
	var exampleNormalValue by normalPrefs::exampleValue

	var exampleDeviceValue by devicePrefs::exampleValue

	var lastSeen by normalPrefs::lastSeen

	var theme by normalPrefs::theme
	var enabledFeatures by normalPrefs::enabledFeatures

	var exampleDataStoreValue by normalDataStore::exampleValue
	val exampleDataStoreValueFlow = normalDataStore.exampleValueFlow

	var dataStoreTheme by normalDataStore::theme
	var dataStoreFeatures by normalDataStore::enabledFeatures

	suspend fun clearPrefs() {
		normalPrefs.clearPrefs()
		normalDataStore.clearPrefs()
	}
}

/**
 * A [BasePrefsHelper] that also demonstrates [BasePrefsHelper.migrateIfNeeded].
 */
class NormalPrefs(context: Context) : BasePrefsHelper() {
	override val sharedPreferences: SharedPreferences =
		context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

	init {
		// Runs at most once per version. An install written by 1.x may still hold the old -1L
		// sentinel for temporal keys, which 2.0 would otherwise read as 1969-12-31.
		migrateIfNeeded(currentVersion = PREFS_VERSION) { fromVersion ->
			if (fromVersion < 2) {
				migrateLegacyTemporalSentinels(KEY_LAST_SEEN)
			}
		}
	}

	var exampleValue by stringPref(KEY_EXAMPLE, defaultValue = "")
	var lastSeen by datePref(KEY_LAST_SEEN)

	/**
	 * Enum-backed preferences are the library's only reflective path — values are matched by
	 * [Enum.name] against `enumConstants`. Kept in the sample so the R8 instrumented test has
	 * production-shaped, minified code to exercise rather than test-only classes.
	 */
	var theme by enumPref(KEY_THEME, Theme.SYSTEM)
	var enabledFeatures by enumSetPref<Feature>(KEY_FEATURES)

	companion object {
		const val KEY_EXAMPLE = "example_key"
		const val KEY_LAST_SEEN = "last_seen"
		const val KEY_THEME = "theme"
		const val KEY_FEATURES = "enabled_features"

		/** Bump alongside a new branch in the [migrateIfNeeded] block above. */
		private const val PREFS_VERSION = 2
	}
}

enum class Theme { LIGHT, DARK, SYSTEM }

enum class Feature { OFFLINE_MODE, BETA_SEARCH, ANALYTICS }

class DevicePrefs(context: Context) : BasePrefsHelper() {
	override val sharedPreferences: SharedPreferences =
		context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)

	var exampleValue by stringPref(KEY_EXAMPLE, defaultValue = "")

	companion object {
		const val KEY_EXAMPLE = "example_key"
	}
}

/**
 * No `getInstance()` here any more.
 *
 * DataStore permits only one live instance per file per process, and that rule has not gone away —
 * what changed is who enforces it. Registering this as a Koin `single` gives the same guarantee the
 * old double-checked `getInstance()` did, with none of the code. Apps that don't use a DI container
 * still have to enforce it themselves; see the README.
 *
 * [appScope] is the second thing worth noticing: fire-and-forget writes (the `*Async` methods, and
 * every delegate setter) launch in it, so injecting an application-lifetime scope carrying a
 * `CoroutineExceptionHandler` is what stops a failed background write from reaching the default
 * handler and taking the process with it.
 */
class NormalDataStore(
	context: Context,
	appScope: CoroutineScope,
) : BaseDataStoreHelper(context, "normal_dataStore", scope = appScope) {

	var exampleValue by stringPref(KEY_EXAMPLE, defaultValue = "")
	val exampleValueFlow = stringPrefFlow(KEY_EXAMPLE)

	/** Same reflective path as [NormalPrefs.theme], on the DataStore side. */
	var theme by enumPref(KEY_THEME, Theme.SYSTEM)
	var enabledFeatures by enumSetPref<Feature>(KEY_FEATURES)

	companion object {
		const val KEY_EXAMPLE = "example_key"
		const val KEY_THEME = "theme"
		const val KEY_FEATURES = "enabled_features"
	}
}
