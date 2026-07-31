package com.duck.app.di

import android.util.Log
import com.duck.app.data.prefs.DevicePrefs
import com.duck.app.data.prefs.NormalDataStore
import com.duck.app.data.prefs.NormalPrefs
import com.duck.app.data.prefs.PrefsHelper
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Qualifier for the application-lifetime [CoroutineScope] that owns fire-and-forget prefs writes.
 */
val APP_SCOPE = named("appScope")

/**
 * Reference wiring for PrefsHelper. Two things here are the point of the example:
 *
 * 1. **[NormalDataStore] is a `single`.** DataStore allows only one live instance per file per
 *    process. Declaring it as a `single` is what enforces that — it replaces the hand-rolled
 *    `getInstance()` double-checked singleton this sample used before 2.0. Register a DataStore
 *    helper as a `factory` and the second instantiation will throw.
 *
 * 2. **The scope is injected, with a handler attached.** `*Async` writes and every delegate setter
 *    launch in the helper's `scope`. The default is a bare per-instance `SupervisorJob`, which has
 *    nowhere to report a failure — an exception from a background write reaches the thread's
 *    default handler and takes the process down. Passing a scope that carries a
 *    [CoroutineExceptionHandler] is the fix, and is the reason 2.0 made the scope injectable.
 *
 * `BasePrefsHelper` subclasses have neither constraint — SharedPreferences is already
 * process-shared and does no background work — so they are `single` only to avoid re-reading the
 * file, not out of necessity.
 */
val prefsModule = module {
	single(APP_SCOPE) {
		CoroutineScope(
			SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
				// A real app would route this to Crashlytics/Sentry rather than logcat.
				Log.e("PrefsHelper", "Fire-and-forget preference write failed", throwable)
			},
		)
	}

	single { NormalPrefs(androidContext()) }
	single { DevicePrefs(androidContext()) }
	single { NormalDataStore(androidContext(), get(APP_SCOPE)) }

	singleOf(::PrefsHelper)
}
