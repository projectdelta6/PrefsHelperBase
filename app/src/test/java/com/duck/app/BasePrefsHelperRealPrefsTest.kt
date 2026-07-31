package com.duck.app

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duck.prefshelper.BasePrefsHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * [BasePrefsHelperTest] covers the whole API against a Mockito-mocked [SharedPreferences], which is
 * fast but proves only that the right platform calls are made. The types whose storage involves a
 * real encoding — `Double` (raw IEEE-754 bits in a Long), `ByteArray` (Base64), `Set<String>`
 * (defensive copies) — need a genuine `SharedPreferences` to prove they actually round-trip.
 *
 * Robolectric, so it still runs on the JVM with no device. `android.util.Base64` in particular
 * returns nothing useful without an Android runtime, so the `ByteArray` path cannot be tested at
 * all in the mock-based suite.
 */
@RunWith(AndroidJUnit4::class)
class BasePrefsHelperRealPrefsTest {

	private lateinit var prefs: SharedPreferences
	private lateinit var helper: RealPrefsHelper

	@Before
	fun setUp() {
		val context = InstrumentationRegistry.getInstrumentation().targetContext
		prefs = context.getSharedPreferences("real_prefs_test", Context.MODE_PRIVATE)
		prefs.edit().clear().commit()
		helper = RealPrefsHelper(prefs)
	}

	// region Double — raw IEEE-754 bit encoding

	/**
	 * The values a naive `putFloat`-based encoding would quietly mangle. Each must come back
	 * bit-identical through a real `SharedPreferences`.
	 */
	@Test
	fun testDoubleRoundTripsThroughRealPreferences() {
		val values = listOf(
			0.0,
			-0.0,
			1.0,
			-1.0,
			3.14159265358979,
			1.0 / 3.0,
			Double.MAX_VALUE,
			Double.MIN_VALUE,
			Double.POSITIVE_INFINITY,
			Double.NEGATIVE_INFINITY,
		)
		for (value in values) {
			helper.setDouble("d", value)
			assertEquals("round-trip failed for $value", value, helper.getDouble("d"), 0.0)
		}

		helper.setDouble("d", Double.NaN)
		assertTrue(helper.getDouble("d").isNaN())
	}

	/** -0.0 and 0.0 are equal under `==` but have different bit patterns; the encoding must keep them apart. */
	@Test
	fun testNegativeZeroIsDistinctFromPositiveZero() {
		helper.setDouble("d", -0.0)
		assertEquals(java.lang.Double.doubleToRawLongBits(-0.0), java.lang.Double.doubleToRawLongBits(helper.getDouble("d")))
		assertNotEquals(java.lang.Double.doubleToRawLongBits(0.0), java.lang.Double.doubleToRawLongBits(helper.getDouble("d")))
	}

	@Test
	fun testDoubleReturnsDefaultWhenAbsent() {
		assertEquals(9.5, helper.getDouble("never_written", 9.5), 0.0)
	}

	// endregion

	// region ByteArray — Base64 encoding

	@Test
	fun testByteArrayRoundTripsThroughRealPreferences() {
		val bytes = byteArrayOf(0, -1, 127, -128, 42)
		helper.setByteArray("b", bytes)
		assertArrayEquals(bytes, helper.getByteArray("b"))
	}

	@Test
	fun testEmptyByteArrayRoundTrips() {
		helper.setByteArray("b", ByteArray(0))
		assertArrayEquals(ByteArray(0), helper.getByteArray("b"))
	}

	@Test
	fun testByteArrayNullRemovesKey() {
		helper.setByteArray("b", byteArrayOf(1, 2, 3))
		assertNotNull(helper.getByteArray("b"))

		helper.setByteArray("b", null)
		assertNull(helper.getByteArray("b"))
		assertTrue(!helper.contains("b"))
	}

	@Test
	fun testByteArrayAbsentKeyReturnsNull() {
		assertNull(helper.getByteArray("never_written"))
	}

	/** A key holding a non-Base64 string must return null rather than propagating an exception. */
	@Test
	fun testByteArrayReturnsNullForUndecodableValue() {
		helper.setString("b", "!!! not base64 !!!")
		assertNull(helper.getByteArray("b"))
	}

	/** The delegate must go through the same encoding as the direct accessors. */
	@Test
	fun testByteArrayDelegateRoundTrips() {
		val bytes = byteArrayOf(9, 8, 7)
		helper.byteArrayValue = bytes
		assertArrayEquals(bytes, helper.byteArrayValue)
	}

	// endregion

	// region Set<String> — defensive copies against a real backing store

	@Test
	fun testStringSetRoundTrips() {
		helper.setStringSet("s", setOf("alpha", "beta", "gamma"))
		assertEquals(setOf("alpha", "beta", "gamma"), helper.getStringSet("s"))
	}

	@Test
	fun testEmptyStringSetIsDistinctFromAbsent() {
		helper.setStringSet("s", emptySet())
		assertEquals(emptySet<String>(), helper.getStringSet("s"))
		assertTrue(helper.contains("s"))
		// An absent key falls back to the supplied default; an empty stored set does not.
		assertEquals(setOf("fallback"), helper.getStringSet("never_written", setOf("fallback")))
	}

	/**
	 * Mutating the set we returned must not corrupt what is stored — the real trap
	 * `SharedPreferences.getStringSet` sets for callers.
	 */
	@Test
	fun testGetStringSetCopyIsSafeToMutate() {
		helper.setStringSet("s", setOf("a", "b"))

		@Suppress("UNCHECKED_CAST")
		(helper.getStringSet("s") as MutableSet<String>).add("c")

		assertEquals(setOf("a", "b"), helper.getStringSet("s"))
	}

	/** And mutating the set we were given after writing must not change what was stored. */
	@Test
	fun testSetStringSetCopiesCallerSet() {
		val caller = mutableSetOf("a")
		helper.setStringSet("s", caller)
		caller.add("added-after-write")

		assertEquals(setOf("a"), helper.getStringSet("s"))
	}

	// endregion

	// region Instant and Set<Enum>

	@Test
	fun testInstantRoundTrips() {
		val instant = Instant.ofEpochMilli(1_700_000_000_123L)
		helper.setInstant("i", instant)
		assertEquals(instant, helper.getInstant("i"))
	}

	@Test
	fun testInstantEpochRoundTrips() {
		helper.setInstant("i", Instant.EPOCH)
		assertEquals(Instant.EPOCH, helper.getInstant("i"))
	}

	/**
	 * Before 2.0 the temporal setters wrote `-1L` to mean null, which made these values unstorable.
	 * They round-trip now; this is the regression guard for that fix.
	 *
	 * `LocalDate` is the one that mattered in practice: epoch day -1 is 1969-12-31, an ordinary
	 * date that silently read back as null.
	 */
	@Test
	fun testFormerSentinelValuesNowRoundTrip() {
		helper.setInstant("i", Instant.ofEpochMilli(-1L))
		assertEquals(Instant.ofEpochMilli(-1L), helper.getInstant("i"))

		helper.setDate("d", java.util.Date(-1L))
		assertEquals(java.util.Date(-1L), helper.getDate("d"))

		val lastDayOf1969 = java.time.LocalDate.of(1969, 12, 31)
		assertEquals(-1L, lastDayOf1969.toEpochDay())
		helper.setLocalDate("ld", lastDayOf1969)
		assertEquals(lastDayOf1969, helper.getLocalDate("ld"))

		val secondBeforeEpoch = java.time.LocalDateTime.of(1969, 12, 31, 23, 59, 59)
		helper.setLocalDateTime("ldt", secondBeforeEpoch)
		assertEquals(secondBeforeEpoch, helper.getLocalDateTime("ldt"))
	}

	/** Assigning null removes the key outright now, matching `BaseDataStoreHelper`. */
	@Test
	fun testNullTemporalRemovesKey() {
		helper.setDate("d", java.util.Date(1_000L))
		assertTrue(helper.contains("d"))

		helper.setDate("d", null)
		assertTrue(!helper.contains("d"))
		assertNull(helper.getDate("d"))
	}

	/**
	 * `LocalTime.ofSecondOfDay` throws outside 0..86399, so a stale pre-2.0 `-1L` would crash rather
	 * than misread. Out-of-range values read as null instead.
	 */
	@Test
	fun testOutOfRangeLocalTimeReadsAsNullRatherThanThrowing() {
		helper.setLong("lt", -1L)
		assertNull(helper.getLocalTime("lt"))

		helper.setLong("lt", 999_999L)
		assertNull(helper.getLocalTime("lt"))
	}

	// endregion

	// region Legacy sentinel migration

	@Test
	fun testMigrateLegacyTemporalSentinelsRemovesOnlySentinelKeys() {
		helper.setLong("stale", -1L)
		helper.setLong("real", 1_700_000_000_000L)

		val removed = helper.migrateLegacyTemporalSentinels("stale", "real", "never_written")

		assertEquals(listOf("stale"), removed)
		assertTrue(!helper.contains("stale"))
		assertEquals(1_700_000_000_000L, helper.getLong("real"))
	}

	/** Safe to leave in place: a second run finds nothing and changes nothing. */
	@Test
	fun testMigrateLegacyTemporalSentinelsIsIdempotent() {
		helper.setLong("stale", -1L)
		assertEquals(listOf("stale"), helper.migrateLegacyTemporalSentinels("stale"))
		assertEquals(emptyList<String>(), helper.migrateLegacyTemporalSentinels("stale"))
	}

	/** A key holding something other than a Long must be skipped, not crash the migration. */
	@Test
	fun testMigrateLegacyTemporalSentinelsSkipsNonLongKeys() {
		helper.setString("text", "not a long")
		assertEquals(emptyList<String>(), helper.migrateLegacyTemporalSentinels("text"))
		assertEquals("not a long", helper.getString("text"))
	}

	/** End to end: the upgrade path a 1.x install actually takes. */
	@Test
	fun testLegacyNullDateStillReadsAsNullAfterMigration() {
		// What a pre-2.0 install left on disk for "no date".
		helper.setLong("dob", -1L)
		// Without the sweep this would surface as 1969-12-31.
		assertEquals(java.util.Date(-1L), helper.getDate("dob"))

		helper.migrateLegacyTemporalSentinels("dob")
		assertNull(helper.getDate("dob"))
	}

	// endregion

	// region Version-stamped migrations

	@Test
	fun testMigrateIfNeededSkipsFreshInstall() {
		var ran = false
		val invoked = helper.migrateIfNeeded(currentVersion = 2) { ran = true }

		assertTrue(!invoked)
		assertTrue(!ran)
		// Baseline stamped so the next upgrade has something to compare against.
		assertEquals(2, prefs.getInt(BasePrefsHelper.KEY_HELPER_VERSION, -1))
	}

	@Test
	fun testMigrateIfNeededTreatsUnstampedNonEmptyPrefsAsLegacy() {
		helper.setString("pre_existing", "written by 1.x")

		var seenFrom = -99
		val invoked = helper.migrateIfNeeded(currentVersion = 2) { from -> seenFrom = from }

		assertTrue(invoked)
		assertEquals(BasePrefsHelper.VERSION_LEGACY, seenFrom)
		assertEquals(2, prefs.getInt(BasePrefsHelper.KEY_HELPER_VERSION, -1))
	}

	/** The whole point: a non-idempotent migration must not run twice. */
	@Test
	fun testMigrateIfNeededRunsOnlyOnce() {
		helper.setString("pre_existing", "written by 1.x")

		var runs = 0
		helper.migrateIfNeeded(currentVersion = 2) { runs++ }
		helper.migrateIfNeeded(currentVersion = 2) { runs++ }
		helper.migrateIfNeeded(currentVersion = 2) { runs++ }

		assertEquals(1, runs)
	}

	@Test
	fun testMigrateIfNeededRunsAgainForAHigherVersion() {
		helper.setString("pre_existing", "written by 1.x")

		val seen = mutableListOf<Int>()
		helper.migrateIfNeeded(currentVersion = 2) { seen += it }
		helper.migrateIfNeeded(currentVersion = 3) { seen += it }

		assertEquals(listOf(BasePrefsHelper.VERSION_LEGACY, 2), seen)
		assertEquals(3, prefs.getInt(BasePrefsHelper.KEY_HELPER_VERSION, -1))
	}

    /** An app downgrade must not rewind the stamp, or the migration would re-run on re-upgrade. */
	@Test
	fun testMigrateIfNeededDoesNotRewindOnDowngrade() {
		helper.setString("pre_existing", "written by 1.x")
		helper.migrateIfNeeded(currentVersion = 5) { }
		assertEquals(5, prefs.getInt(BasePrefsHelper.KEY_HELPER_VERSION, -1))

		var ran = false
		val invoked = helper.migrateIfNeeded(currentVersion = 3) { ran = true }

		assertTrue(!invoked)
		assertTrue(!ran)
		assertEquals(5, prefs.getInt(BasePrefsHelper.KEY_HELPER_VERSION, -1))
	}

	@Test(expected = IllegalArgumentException::class)
	fun testMigrateIfNeededRejectsVersionBelowLegacy() {
		helper.migrateIfNeeded(currentVersion = 0) { }
	}

	/**
	 * The version stamp is schema metadata, not user session state, so `clearPrefs()` must not take
	 * it with them.
	 *
	 * Without this, the sample's own shape is a data-corruption path: stamp at v2 → logout calls
	 * clearPrefs → the helper is a DI singleton so `init` never re-runs → the app writes a
	 * preference → the file is now non-empty *and* unstamped → next cold start reads that as
	 * VERSION_LEGACY and re-runs every migration. Fine for the idempotent sentinel sweep, corrupting
	 * for the non-idempotent migrations `migrateIfNeeded` exists to make safe.
	 */
	@Test
	fun testClearPrefsPreservesTheVersionStamp() = runBlocking {
		helper.setString("pre_existing", "written by 1.x")
		helper.migrateIfNeeded(currentVersion = 2) { }
		assertEquals(2, prefs.getInt(BasePrefsHelper.KEY_HELPER_VERSION, -1))

		helper.clearPrefs()

		assertEquals(
			"clearPrefs must not reset the migration high-water mark",
			2,
			prefs.getInt(BasePrefsHelper.KEY_HELPER_VERSION, -1),
		)
		// User data is still gone — only the stamp survives.
		assertNull(helper.getString("pre_existing", "").takeIf { it.isNotEmpty() })
	}

	/** The full logout-then-relaunch sequence, end to end. */
	@Test
	fun testMigrationDoesNotRerunAfterClearPrefs() = runBlocking {
		helper.setString("pre_existing", "written by 1.x")

		var runs = 0
		helper.migrateIfNeeded(currentVersion = 2) { runs++ }
		assertEquals(1, runs)

		helper.clearPrefs()
		helper.setString("written_after_logout", "x")

		// A cold start reconstructs the helper over the same file.
		RealPrefsHelper(prefs).migrateIfNeeded(currentVersion = 2) { runs++ }

		assertEquals("migration must not re-run after clearPrefs", 1, runs)
	}

	@Test
	fun testEnumSetRoundTrips() {
		helper.enumSetValue = setOf(BasePrefsHelperTest.TestEnum.VALUE_A, BasePrefsHelperTest.TestEnum.VALUE_C)
		assertEquals(
			setOf(BasePrefsHelperTest.TestEnum.VALUE_A, BasePrefsHelperTest.TestEnum.VALUE_C),
			helper.enumSetValue,
		)
	}

	/**
	 * A stored empty set must not be confused with an absent key — otherwise an empty selection is
	 * unstorable whenever the default is non-empty, and this helper disagrees with
	 * `BaseDataStoreHelper`, which distinguishes the two.
	 */
	@Test
	fun testEmptyEnumSetIsDistinctFromAbsent() {
		val withDefault = EnumSetDefaultHelper(prefs)

		// Absent -> the default.
		assertEquals(setOf(BasePrefsHelperTest.TestEnum.VALUE_A), withDefault.features)

		// Explicitly stored empty -> empty, NOT the default.
		withDefault.features = emptySet()
		assertEquals(emptySet<BasePrefsHelperTest.TestEnum>(), withDefault.features)

		// Same distinction on the direct accessor.
		assertEquals(
			emptySet<BasePrefsHelperTest.TestEnum>(),
			helper.getEnumSet("features", setOf(BasePrefsHelperTest.TestEnum.VALUE_A)),
		)
		assertEquals(
			setOf(BasePrefsHelperTest.TestEnum.VALUE_A),
			helper.getEnumSet("never_written_enum_set", setOf(BasePrefsHelperTest.TestEnum.VALUE_A)),
		)
	}

	/**
	 * Null must remove the key, not leave an empty string behind. Reads coped either way, but
	 * `contains(key)` did not — it stayed true on this helper and false on `BaseDataStoreHelper`,
	 * contradicting 2.0's "null means the same thing on both" claim.
	 */
	@Test
	fun testNullEnumRemovesKey() {
		helper.setEnum("e", BasePrefsHelperTest.TestEnum.VALUE_B)
		assertTrue(helper.contains("e"))

		helper.setEnum("e", null)
		assertTrue("null enum must remove the key, not store \"\"", !helper.contains("e"))
		assertNull(helper.getEnum<BasePrefsHelperTest.TestEnum>("e"))
	}

	/** Names that no longer map to a constant are dropped, not thrown on. */
	@Test
	fun testEnumSetDropsUnknownStoredNames() {
		helper.setStringSet("enum_set_key", setOf("VALUE_B", "DELETED_CONSTANT"))
		assertEquals(setOf(BasePrefsHelperTest.TestEnum.VALUE_B), helper.enumSetValue)
	}

	// endregion

	private fun assertNotNull(value: Any?) = assertTrue(value != null)

	private class RealPrefsHelper(
		override val sharedPreferences: SharedPreferences,
	) : BasePrefsHelper() {
		var byteArrayValue by byteArrayPref("byte_array_key")
		var enumSetValue by enumSetPref<BasePrefsHelperTest.TestEnum>("enum_set_key")
	}

	/** A delegate with a non-empty default, to expose empty-vs-absent confusion. */
	private class EnumSetDefaultHelper(
		override val sharedPreferences: SharedPreferences,
	) : BasePrefsHelper() {
		var features by enumSetPref("features", defaultValue = setOf(BasePrefsHelperTest.TestEnum.VALUE_A))
	}
}
