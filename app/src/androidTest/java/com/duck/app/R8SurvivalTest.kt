package com.duck.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duck.app.data.prefs.Feature
import com.duck.app.data.prefs.NormalDataStore
import com.duck.app.data.prefs.NormalPrefs
import com.duck.app.data.prefs.Theme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.util.Date

/**
 * Verifies the library survives R8, which is the one claim the README makes that no JVM test can
 * check. Unit tests never run R8 and Robolectric runs unminified code, so this is the only place
 * the obfuscated behaviour is observable.
 *
 * **Manual only — not in CI, which has no device:**
 *
 * ```
 * ./gradlew :app:connectedAndroidTest -PminifiedTests
 * ```
 *
 * Without `-PminifiedTests` this runs against the debug build and proves nothing about R8; it will
 * still pass, so read the build type before trusting a green result. [testIsActuallyRunningMinified]
 * fails loudly if the app under test was not minified.
 *
 * The classes exercised here (`NormalPrefs`, `NormalDataStore`, `Theme`, `Feature`) live in the
 * app's **main** source set precisely so R8 processes them. A helper declared in this test source
 * set would not be representative.
 */
@RunWith(AndroidJUnit4::class)
class R8SurvivalTest {

	private lateinit var prefs: NormalPrefs
	private lateinit var dataStore: NormalDataStore

	@Before
	fun setUp() {
		val koin = requireNotNull(GlobalContext.getOrNull()) {
			"Koin not started — PrefsHelperApp should have run"
		}
		prefs = koin.get()
		dataStore = koin.get()
		runBlocking {
			prefs.clearPrefs()
			dataStore.clearPrefs()
		}
	}

	/**
	 * Guard against a false pass.
	 *
	 * Asks the runtime directly whether the class was renamed, rather than inferring it from the
	 * build type — if R8 did not actually obfuscate, every other test here is vacuous and would
	 * still go green.
	 */
	@Test
	fun testIsActuallyRunningMinified() {
		val runtimeName = NormalPrefs::class.java.name
		assertTrue(
			"Classes are not obfuscated (NormalPrefs is still '$runtimeName'), so this run proves " +
				"nothing about R8. Re-run with:\n" +
				"  ./gradlew :app:connectedAndroidTest -PminifiedTests",
			runtimeName != "com.duck.app.data.prefs.NormalPrefs",
		)
	}

	/**
	 * The core claim. Enum preferences are stored by [Enum.name] and resolved through
	 * `enumConstants`; if R8 renamed either the constants or the class in a way that broke that,
	 * this round-trip returns the default instead of the stored value.
	 */
	@Test
	fun testEnumPrefSurvivesObfuscation() {
		prefs.theme = Theme.DARK
		assertEquals(Theme.DARK, prefs.theme)

		prefs.theme = Theme.LIGHT
		assertEquals(Theme.LIGHT, prefs.theme)
	}

	/** The name written must be the source-level constant name, not an obfuscated one. */
	@Test
	fun testEnumIsStoredUnderItsSourceName() {
		prefs.theme = Theme.DARK
		assertEquals("DARK", prefs.getString(NormalPrefs.KEY_THEME))
	}

	@Test
	fun testEnumSetPrefSurvivesObfuscation() {
		prefs.enabledFeatures = setOf(Feature.OFFLINE_MODE, Feature.ANALYTICS)
		assertEquals(setOf(Feature.OFFLINE_MODE, Feature.ANALYTICS), prefs.enabledFeatures)

		assertEquals(
			setOf("OFFLINE_MODE", "ANALYTICS"),
			prefs.getStringSet(NormalPrefs.KEY_FEATURES),
		)
	}

	/**
	 * `enumSetPref` on DataStore goes through the inline / `@PublishedApi internal` split that
	 * exists to dodge `IllegalAccessError`. Minification is another way that could break, so it is
	 * worth exercising here and not only on the JVM.
	 */
	@Test
	fun testDataStoreEnumPathsSurviveObfuscation() {
		dataStore.theme = Theme.DARK
		dataStore.enabledFeatures = setOf(Feature.BETA_SEARCH)

		awaitValue(Theme.DARK) { dataStore.theme }
		awaitValue(setOf(Feature.BETA_SEARCH)) { dataStore.enabledFeatures }
	}

	/** Keys are string literals, so obfuscation must not disturb any of the encodings either. */
	@Test
	fun testEncodingsSurviveObfuscation() {
		prefs.setDouble("d", 3.14159265358979)
		assertEquals(3.14159265358979, prefs.getDouble("d"), 0.0)

		prefs.setByteArray("b", byteArrayOf(0, -1, 127, -128))
		assertTrue(byteArrayOf(0, -1, 127, -128).contentEquals(prefs.getByteArray("b")))

		prefs.setStringSet("s", setOf("alpha", "beta"))
		assertEquals(setOf("alpha", "beta"), prefs.getStringSet("s"))

		val date = Date(1_700_000_000_000L)
		prefs.setDate("dt", date)
		assertEquals(date, prefs.getDate("dt"))

		prefs.setDate("dt", null)
		assertNull(prefs.getDate("dt"))
	}

	/** The version stamp and its key must also survive; the key is a literal, not a class name. */
	@Test
	fun testMigrationMachinerySurvivesObfuscation() {
		prefs.setString("something", "x")

		var ranFrom = -1
		val migrated = prefs.migrateIfNeeded(currentVersion = 99) { from -> ranFrom = from }

		assertTrue(migrated)
		assertTrue(ranFrom >= 1)
		// Second call must be a no-op — proves the stamp was written and read back.
		assertTrue(!prefs.migrateIfNeeded(currentVersion = 99) { })
	}

	private fun <T> awaitValue(expected: T, timeoutMs: Long = 5_000, read: () -> T) {
		val deadline = System.currentTimeMillis() + timeoutMs
		var actual = read()
		while (actual != expected && System.currentTimeMillis() < deadline) {
			Thread.sleep(25)
			actual = read()
		}
		assertEquals(expected, actual)
	}

	private companion object {
		init {
			InstrumentationRegistry.getInstrumentation()
		}
	}
}
