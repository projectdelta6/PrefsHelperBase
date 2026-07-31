package com.duck.app.di

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duck.app.data.prefs.DevicePrefs
import com.duck.app.data.prefs.NormalDataStore
import com.duck.app.data.prefs.NormalPrefs
import com.duck.app.data.prefs.PrefsHelper
import kotlinx.coroutines.CoroutineScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get

/**
 * The sample's DI graph is reference code people copy, so "it compiles" isn't enough — a missing
 * binding or a wrong qualifier only shows up when the app launches. This resolves the graph for
 * real and pins the property that actually matters.
 */
@RunWith(AndroidJUnit4::class)
class PrefsModuleTest : KoinTest {

	@After
	fun tearDown() {
		stopKoin()
	}

	private fun start() {
		startKoin {
			androidContext(InstrumentationRegistry.getInstrumentation().targetContext)
			modules(prefsModule)
		}
	}

	@Test
	fun testEveryDefinitionResolves() {
		start()

		assertNotNull(get<CoroutineScope>(APP_SCOPE))
		assertNotNull(get<NormalPrefs>())
		assertNotNull(get<DevicePrefs>())
		assertNotNull(get<NormalDataStore>())
		assertNotNull(get<PrefsHelper>())
	}

	/**
	 * The point of registering [NormalDataStore] as a `single`. DataStore allows one live instance
	 * per file per process; a `factory` here would construct a second and throw. Without this the
	 * module could silently regress to a factory and nothing would notice until runtime.
	 */
	@Test
	fun testDataStoreHelperIsASingleton() {
		start()

		assertSame(get<NormalDataStore>(), get<NormalDataStore>())
	}

	/**
	 * The façade must be wired to the container's instances, not fresh ones.
	 *
	 * Asserting `get<PrefsHelper>() === get<PrefsHelper>()` would only prove `PrefsHelper` is itself
	 * a `single` — it says nothing about what got injected into it. This writes through the façade
	 * and reads from the separately-resolved sub-helper, which fails if they are different objects
	 * over different files.
	 */
	@Test
	fun testFacadeIsWiredToTheContainerInstances() {
		start()

		val facade = get<PrefsHelper>()
		val normalPrefs = get<NormalPrefs>()
		val dataStore = get<NormalDataStore>()

		facade.exampleNormalValue = "written-through-facade"
		assertEquals("written-through-facade", normalPrefs.exampleValue)

		normalPrefs.exampleValue = "written-through-sub-helper"
		assertEquals("written-through-sub-helper", facade.exampleNormalValue)

		// The DataStore helper is the one that must not be duplicated at all.
		assertSame(dataStore, get<NormalDataStore>())
		assertSame(facade, get<PrefsHelper>())
	}

	/** The injected scope is what gives fire-and-forget writes somewhere to report failures. */
	@Test
	fun testAppScopeIsSharedAndActive() {
		start()

		val scope = get<CoroutineScope>(APP_SCOPE)
		assertSame(scope, get<CoroutineScope>(APP_SCOPE))
		assertNotNull(scope.coroutineContext[kotlinx.coroutines.CoroutineExceptionHandler])
	}

	/** `androidContext()` must be wired, or every helper needing a Context fails to resolve. */
	@Test
	fun testAndroidContextIsAvailable() {
		start()

		assertNotNull(get<Context>())
	}
}
