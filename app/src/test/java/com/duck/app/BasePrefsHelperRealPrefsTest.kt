package com.duck.app

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duck.prefshelper.BasePrefsHelper
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
	 * `BasePrefsHelper` uses -1L as its "absent" sentinel for temporal types, so an Instant at
	 * exactly -1ms is indistinguishable from null. Documented in the README; pinned here so the
	 * limitation is a decision rather than a surprise.
	 */
	@Test
	fun testInstantAtSentinelMillisReadsBackAsNull() {
		helper.setInstant("i", Instant.ofEpochMilli(-1L))
		assertNull(helper.getInstant("i"))
	}

	@Test
	fun testEnumSetRoundTrips() {
		helper.enumSetValue = setOf(BasePrefsHelperTest.TestEnum.VALUE_A, BasePrefsHelperTest.TestEnum.VALUE_C)
		assertEquals(
			setOf(BasePrefsHelperTest.TestEnum.VALUE_A, BasePrefsHelperTest.TestEnum.VALUE_C),
			helper.enumSetValue,
		)
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
}
