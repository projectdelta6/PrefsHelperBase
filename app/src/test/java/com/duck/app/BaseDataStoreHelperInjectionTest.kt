package com.duck.app

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duck.prefshelper.BaseDataStoreHelper
import com.duck.prefshelper.BasePrefsHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/**
 * Proves the coroutine-API redesign delivers injectable [DataStore], [CoroutineDispatcher], and
 * [CoroutineScope], plus caller-cancellable suspend work — without the sleeps/polling the
 * convenience-constructor suite still needs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class BaseDataStoreHelperInjectionTest {

	@get:Rule
	val tempFolder = TemporaryFolder()

	/** Scopes that own active DataStore file locks — cancelled in [tearDown]. */
	private val storeScopes = mutableListOf<CoroutineScope>()

	private var fileCounter = 0

	@After
	fun tearDown() {
		storeScopes.forEach { it.cancel() }
		storeScopes.clear()
	}

	/**
	 * Fresh preferences file per call — DataStore throws if two active instances share a file.
	 * Returns a path that does not yet exist ([TemporaryFolder.newFile] would pre-create it).
	 */
	private fun newPreferencesFile(): File =
		File(tempFolder.root, "test_${fileCounter++}.preferences_pb")

	/**
	 * Builds a [DataStore] on an [UnconfinedTestDispatcher] so edits complete eagerly inside
	 * [runTest] with no polling.
	 */
	private fun newDataStore(testScheduler: TestCoroutineScheduler): DataStore<Preferences> {
		val storeScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
		storeScopes += storeScope
		// Resolve the path up front and close over it: produceFile is contractually required to
		// yield the same file every time. Calling newPreferencesFile() inside the lambda would
		// hand DataStore a different path on any second invocation.
		val file = newPreferencesFile()
		return PreferenceDataStoreFactory.create(
			scope = storeScope,
			produceFile = { file },
		)
	}

	// region 1 — Injected DataStore: deterministic write/read

	/**
	 * Injected [DataStore] + unconfined scopes make `write` then `read`/`first()` complete
	 * deterministically — no [kotlinx.coroutines.delay] or polling required.
	 */
	@Test
	fun testInjectedDataStoreWriteThenReadIsDeterministic() = runTest {
		val dispatcher = UnconfinedTestDispatcher(testScheduler)
		val helper = InjectableDataStoreHelper(
			dataStore = newDataStore(testScheduler),
			dispatcher = dispatcher,
			scope = CoroutineScope(dispatcher + SupervisorJob()),
		)

		helper.testWriteString("deterministic_key", "deterministic_value")

		assertEquals("deterministic_value", helper.testReadStringFlow("deterministic_key").first())
		assertEquals("deterministic_value", helper.testReadStringValue("deterministic_key"))
	}

	// endregion

	// region 2 — Injected helper scope owns *Async launches

	/**
	 * `*Async` writes launch in the injected [BaseDataStoreHelper] [scope], not an internal one.
	 * With a paused [StandardTestDispatcher], the write only lands after [advanceUntilIdle], and
	 * the returned [Job] can be [Job.join]ed.
	 */
	@Test
	fun testAsyncWriteLaunchesInInjectedHelperScope() = runTest {
		val storeDispatcher = UnconfinedTestDispatcher(testScheduler)
		val helperDispatcher = UnconfinedTestDispatcher(testScheduler)
		// Paused dispatcher: launched async work does not run until the scheduler is advanced.
		val helperScopeDispatcher = StandardTestDispatcher(testScheduler)
		val helperScope = CoroutineScope(helperScopeDispatcher + Job())

		val helper = InjectableDataStoreHelper(
			dataStore = newDataStore(testScheduler),
			dispatcher = helperDispatcher,
			scope = helperScope,
		)

		val job = helper.testWriteStringAsync("async_key", "async_value")
		assertTrue("Returned Job should still be active before the helper scope is advanced", job.isActive)
		assertNull(
			"Write must not land before the injected scope's dispatcher is advanced",
			helper.testReadStringFlow("async_key").first(),
		)

		advanceUntilIdle()
		job.join()

		assertTrue("Returned Job should be complete after join()", job.isCompleted)
		assertEquals("async_value", helper.testReadStringFlow("async_key").first())
	}

	// endregion

	// region 3 — Default scope is per-instance (regression for deleted companion SupervisorJob)

	/**
	 * Regression guard for the deleted shared companion `supervisorJob`: two helpers built with
	 * the default [scope] argument must not share a Job. Cancelling instance A's scope must not
	 * prevent instance B's `*Async` writes from completing.
	 */
	@Test
	fun testDefaultScopeIsPerInstanceNotShared() = runTest {
		// Default scope uses Dispatchers.IO + SupervisorJob per instance — real threads, so join()
		// is the await (no delay/polling). DataStore still uses an unconfined test scope for
		// deterministic file I/O under runTest.
		val helperA = InjectableDataStoreHelper(dataStore = newDataStore(testScheduler))
		val helperB = InjectableDataStoreHelper(dataStore = newDataStore(testScheduler))

		assertFalse(
			"Default scopes must be independent instances",
			helperA.exposedScope === helperB.exposedScope,
		)
		assertFalse(
			"Default scopes must not share a Job (regression for companion supervisorJob)",
			helperA.exposedScope.coroutineContext.job === helperB.exposedScope.coroutineContext.job,
		)

		helperA.exposedScope.cancel()
		assertFalse(helperA.exposedScope.isActive)

		// B must still complete async writes after A was cancelled.
		helperB.testWriteStringAsync("independent_key", "still_works").join()
		assertEquals("still_works", helperB.testReadStringFlow("independent_key").first())
		assertTrue(helperB.exposedScope.isActive)
	}

	// endregion

	// region 4 — clearPrefs() is caller-cancellable (DataStore)

	/**
	 * Headline behaviour change: [BaseDataStoreHelper.clearPrefs] uses `withContext(dispatcher)`
	 * (no Job on the dispatcher), so cancelling the caller cancels the clear. Under the old
	 * foreign-Job `withContext(coroutineContext)` the clear would have completed regardless.
	 *
	 * [CoroutineStart.UNDISPATCHED] runs until the first suspension (`withContext`) without
	 * advancing the test scheduler — then we cancel before [advanceUntilIdle].
	 */
	@Test
	fun testClearPrefsIsCallerCancellable() = runTest {
		val clearDispatcher = StandardTestDispatcher(testScheduler)
		val helper = InjectableDataStoreHelper(
			dataStore = newDataStore(testScheduler),
			dispatcher = clearDispatcher,
			scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
		)

		// Seed via writeString (withContext on the paused dispatcher), then advance so it lands.
		val seedJob = launch(start = CoroutineStart.UNDISPATCHED) {
			helper.testWriteString("keep_key", "keep_me")
		}
		advanceUntilIdle()
		seedJob.join()
		assertEquals("keep_me", helper.testReadStringFlow("keep_key").first())

		val clearJob = launch(start = CoroutineStart.UNDISPATCHED) {
			helper.clearPrefs()
		}
		// Suspended inside withContext(clearDispatcher) — body not yet run.
		assertTrue(clearJob.isActive)
		clearJob.cancel()
		assertTrue(clearJob.isCancelled)

		advanceUntilIdle()

		assertEquals(
			"Cancelled clear must not wipe prefs",
			"keep_me",
			helper.testReadStringFlow("keep_key").first(),
		)
	}

	/**
	 * Positive path companion to [testClearPrefsIsCallerCancellable]: when the clear job is not
	 * cancelled, [advanceUntilIdle] must actually clear — so the cancel test cannot pass vacuously.
	 */
	@Test
	fun testClearPrefsRunsWhenNotCancelled() = runTest {
		val clearDispatcher = StandardTestDispatcher(testScheduler)
		val helper = InjectableDataStoreHelper(
			dataStore = newDataStore(testScheduler),
			dispatcher = clearDispatcher,
			scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
		)

		val seedJob = launch(start = CoroutineStart.UNDISPATCHED) {
			helper.testWriteString("clear_key", "will_go")
		}
		advanceUntilIdle()
		seedJob.join()
		assertEquals("will_go", helper.testReadStringFlow("clear_key").first())

		val clearJob = launch(start = CoroutineStart.UNDISPATCHED) {
			helper.clearPrefs()
		}
		advanceUntilIdle()
		clearJob.join()

		assertNull(helper.testReadStringFlow("clear_key").first())
	}

	// endregion

	// region 4b — Suspending writes are caller-cancellable

	/**
	 * The redesign's claim is that `clearPrefs` *and the suspending writes* became caller-cancellable,
	 * so the writes need their own guard rather than riding on the clearPrefs tests.
	 *
	 * The nullable `writeValue` overload is the one that routes through `withContext(dispatcher)`,
	 * so it's the path that changed. Same shape as the clearPrefs pair: cancel before advancing and
	 * the write must not land.
	 */
	@Test
	fun testSuspendingWriteIsCallerCancellable() = runTest {
		val writeDispatcher = StandardTestDispatcher(testScheduler)
		val helper = InjectableDataStoreHelper(
			dataStore = newDataStore(testScheduler),
			dispatcher = writeDispatcher,
			scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
		)

		val writeJob = launch(start = CoroutineStart.UNDISPATCHED) {
			helper.testWriteString("cancelled_write", "should_not_land")
		}
		// Suspended inside withContext(writeDispatcher) — the edit has not run.
		assertTrue(writeJob.isActive)
		writeJob.cancel()
		assertTrue(writeJob.isCancelled)

		advanceUntilIdle()

		assertNull(
			"Cancelled write must not reach the store",
			helper.testReadStringFlow("cancelled_write").first(),
		)
	}

	/**
	 * Positive path companion to [testSuspendingWriteIsCallerCancellable], so that test cannot pass
	 * merely because the write never worked.
	 */
	@Test
	fun testSuspendingWriteLandsWhenNotCancelled() = runTest {
		val writeDispatcher = StandardTestDispatcher(testScheduler)
		val helper = InjectableDataStoreHelper(
			dataStore = newDataStore(testScheduler),
			dispatcher = writeDispatcher,
			scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
		)

		val writeJob = launch(start = CoroutineStart.UNDISPATCHED) {
			helper.testWriteString("landed_write", "should_land")
		}
		advanceUntilIdle()
		writeJob.join()

		assertEquals("should_land", helper.testReadStringFlow("landed_write").first())
	}

	// endregion

	// region 5 — Same cancellation guard for BasePrefsHelper

	/**
	 * [BasePrefsHelper.clearPrefs] is also `withContext(dispatcher)` — caller cancellation must
	 * prevent the clear. Real Robolectric [SharedPreferences], not mocks, so we assert on stored
	 * state rather than verify() interactions.
	 */
	@Test
	fun testPrefsHelperClearPrefsIsCallerCancellable() = runTest {
		val clearDispatcher = StandardTestDispatcher(testScheduler)
		val prefs = newSharedPreferences("prefs_cancel_${fileCounter++}")
		val helper = InjectablePrefsHelper(prefs, clearDispatcher)

		helper.setString("keep_key", "keep_me")
		assertEquals("keep_me", helper.getString("keep_key"))
		assertTrue(helper.contains("keep_key"))

		val clearJob = launch(start = CoroutineStart.UNDISPATCHED) {
			helper.clearPrefs()
		}
		assertTrue(clearJob.isActive)
		clearJob.cancel()
		assertTrue(clearJob.isCancelled)

		advanceUntilIdle()

		assertTrue("Cancelled clear must leave the key present", helper.contains("keep_key"))
		assertEquals("keep_me", helper.getString("keep_key"))
	}

	/**
	 * Positive path companion to [testPrefsHelperClearPrefsIsCallerCancellable].
	 */
	@Test
	fun testPrefsHelperClearPrefsRunsWhenNotCancelled() = runTest {
		val clearDispatcher = StandardTestDispatcher(testScheduler)
		val prefs = newSharedPreferences("prefs_clear_${fileCounter++}")
		val helper = InjectablePrefsHelper(prefs, clearDispatcher)

		helper.setString("clear_key", "will_go")
		assertTrue(helper.contains("clear_key"))

		val clearJob = launch(start = CoroutineStart.UNDISPATCHED) {
			helper.clearPrefs()
		}
		advanceUntilIdle()
		clearJob.join()

		assertFalse(helper.contains("clear_key"))
	}

	// endregion

	private fun newSharedPreferences(name: String): SharedPreferences {
		val context = InstrumentationRegistry.getInstrumentation().targetContext
		return context.getSharedPreferences(name, Context.MODE_PRIVATE).also { it.edit().clear().commit() }
	}

	// region Test subclasses

	/**
	 * Thin test surface over [BaseDataStoreHelper] for the primary (injected-[DataStore]) constructor.
	 * Exposes [exposedScope] so tests can cancel the default per-instance scope (#3).
	 */
	private class InjectableDataStoreHelper(
		dataStore: DataStore<Preferences>,
		dispatcher: CoroutineDispatcher = Dispatchers.IO,
		scope: CoroutineScope = CoroutineScope(dispatcher + SupervisorJob()),
	) : BaseDataStoreHelper(dataStore, dispatcher, scope) {

		val exposedScope: CoroutineScope get() = scope

		suspend fun testWriteString(key: String, value: String?) = writeString(key, value)
		fun testWriteStringAsync(key: String, value: String?) = writeStringAsync(key, value)
		fun testReadStringValue(key: String) = readStringValue(key)
		fun testReadStringFlow(key: String) = readString(key)
	}

	/**
	 * [BasePrefsHelper] over a real Robolectric [SharedPreferences] with an injectable dispatcher.
	 */
	private class InjectablePrefsHelper(
		override val sharedPreferences: SharedPreferences,
		dispatcher: CoroutineDispatcher,
	) : BasePrefsHelper(dispatcher)

	// endregion
}
