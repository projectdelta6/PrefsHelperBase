package com.duck.app

import android.app.Application
import com.duck.app.di.prefsModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

/**
 * Starts Koin for the sample.
 *
 * Note the unit tests do **not** run through this class — `app/src/test/resources/robolectric.properties`
 * points Robolectric at the stock `Application` instead, because Robolectric instantiates the
 * manifest's Application for every test and [startKoin] would fail the second time. None of the
 * tests use this DI graph; they declare their own helper subclasses.
 */
class PrefsHelperApp : Application() {
	override fun onCreate() {
		super.onCreate()
		startKoin {
			androidLogger()
			androidContext(this@PrefsHelperApp)
			modules(prefsModule)
		}
	}
}
