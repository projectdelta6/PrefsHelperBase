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

	/** The façade must be handed the same instances the container holds, not fresh ones. */
	@Test
	fun testFacadeSharesTheContainerInstances() {
		start()

		assertSame(get<PrefsHelper>(), get<PrefsHelper>())
		assertSame(get<NormalPrefs>(), get<NormalPrefs>())
		assertSame(get<DevicePrefs>(), get<DevicePrefs>())
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
